"""Per-model reasoning-effort menu for the phone (read-only, no network).

Hermes' effort ladder (minimal … max, ultra) is wider than any single model's API: a level the
model doesn't have is clamped at request time to the nearest weaker one, never escalated.
``ultra`` is Hermes' top tier and no API accepts it verbatim — it always runs as the model's
strongest level. The desktop lists the whole ladder and lets the clamp happen silently; the
phone shows each model's REAL levels instead, plus Ultra with what it runs as.

No hand-made provider tables here (Hermes itself warns that hand maps drift): for every ladder
level we ask Hermes' own request builders what would go on the wire for this provider/model.

Runs inside the hermes-agent venv (the proxy does). Fails open: if Hermes can't be imported or
a builder raises, every level is returned as available (``known: false``) — exactly what the
desktop offers.
"""

from __future__ import annotations

from typing import Callable, Optional

LADDER: tuple[str, ...] = ("minimal", "low", "medium", "high", "xhigh", "max", "ultra")

# (provider, model) -> menu. Effort mapping is pure code, so cache for the process lifetime
# (bounded: a phone only ever asks about the handful of models it shows).
_CACHE: dict[tuple[str, str], dict] = {}
_CACHE_MAX = 256
_PROBE_SESSION = "mobile-effort-probe"


def _anthropic_wire(model: str, level: str) -> Optional[str]:
    from agent.anthropic_adapter import THINKING_BUDGET, _thinking_kwargs

    kw = _thinking_kwargs({"enabled": True, "effort": level}, model, 4096)
    oc = kw.get("output_config") or {}
    if oc.get("effort"):
        return str(oc["effort"])
    budget = (kw.get("thinking") or {}).get("budget_tokens")
    if not budget:
        return None
    # older Claude thinks with a token budget: name the level that budget belongs to
    # (unknown levels fall back to the medium budget in Hermes).
    by_budget = {v: k for k, v in THINKING_BUDGET.items()}
    return by_budget.get(budget, f"budget:{budget}")


def _anthropic_can_off(model: str) -> bool:
    from agent.anthropic_adapter import _accepts_thinking_disable, _supports_adaptive_thinking

    # adaptive Claude needs an explicit disable (mandatory-thinking families reject it);
    # legacy manual-thinking Claude is already off when no budget is sent.
    return bool(_accepts_thinking_disable(model) or not _supports_adaptive_thinking(model))


def _codex_wire(provider: str, model: str, level: str, base_url: Optional[str]) -> Optional[str]:
    from agent.transports.codex import _resolve_reasoning

    effort, enabled = _resolve_reasoning(model, {
        "reasoning_config": {"enabled": True, "effort": level},
        "provider": provider,
        "base_url": base_url,
        "is_codex_backend": provider == "openai-codex",
        "is_xai_responses": provider == "xai",
    })
    return str(effort) if enabled and effort else None


def _chat_wire(profile, model: str, level: str, base_url: Optional[str]) -> Optional[str]:
    from agent.transports.chat_completions import _reasoning_config_for_model

    cfg = _reasoning_config_for_model(model, {"enabled": True, "effort": level})
    extra, top = profile.build_api_kwargs_extras(
        reasoning_config=cfg, supports_reasoning=True, model=model, base_url=base_url,
        qwen_session_metadata=None, ollama_num_ctx=None, session_id=_PROBE_SESSION,
    )
    try:
        body = profile.build_extra_body(
            session_id=_PROBE_SESSION, provider_preferences=None, model=model,
            base_url=base_url, reasoning_config=cfg, openrouter_min_coding_score=None,
        ) or {}
    except Exception:
        body = {}
    for key in ("reasoning_effort", "verbosity"):  # verbosity = OpenRouter's Claude effort lever
        if top.get(key):
            return str(top[key])
    for part in (extra, body):
        r = part.get("reasoning") if isinstance(part, dict) else None
        if isinstance(r, dict) and r.get("effort"):
            return str(r["effort"])
    return None


def hermes_menu_fns(provider: str, model: str, base_url: Optional[str] = None
                    ) -> tuple[Optional[Callable[[str], Optional[str]]], bool]:
    """(``level -> wire value`` from Hermes' real builders or None, can turn thinking off)."""
    try:
        from providers import get_provider_profile
    except Exception:
        return None, True
    slug = (provider or "").strip().lower()
    profile = get_provider_profile(slug) if slug else None
    if profile is None and slug.startswith("custom"):
        profile = get_provider_profile("custom")  # named custom endpoints share the custom profile
    api_mode = getattr(profile, "api_mode", "") if profile is not None else ""
    if slug == "anthropic" or api_mode == "anthropic_messages":
        return (lambda level: _anthropic_wire(model, level)), _anthropic_can_off(model)
    if api_mode == "codex_responses" or slug in ("openai-codex", "xai"):
        return (lambda level: _codex_wire(slug, model, level, base_url)), True
    if profile is not None:
        return (lambda level: _chat_wire(profile, model, level, base_url)), True
    return None, True


def build_menu(model: str, wire: Optional[Callable[[str], Optional[str]]], can_off: bool = True) -> dict:
    """Pure: turn a ``level -> wire`` function into the phone's menu.

    ``levels`` = every ladder level with ``wire`` (what Hermes sends; None = no effort field),
    ``native`` (the model has this level itself) and ``runs_as`` (the model's own level a
    clamped one lands on). ``dial`` is False when no level puts anything on the wire.
    """
    if wire is None:
        return {"model": model, "known": False, "dial": True, "can_off": True,
                "levels": [{"level": lv, "wire": None, "native": True, "runs_as": lv} for lv in LADDER]}
    wires: dict[str, Optional[str]] = {}
    for lv in LADDER:
        try:
            wires[lv] = wire(lv)
        except Exception:
            return build_menu(model, None)
    if not any(wires.values()):
        return {"model": model, "known": True, "dial": False, "can_off": can_off, "levels": []}
    first_for_wire: dict[str, str] = {}
    for lv in LADDER:
        w = wires[lv]
        if w and w not in first_for_wire:
            first_for_wire[w] = lv
    levels = []
    for lv in LADDER:
        w = wires[lv]
        if not w:
            levels.append({"level": lv, "wire": None, "native": False, "runs_as": None})
            continue
        runs_as = w if w in LADDER else first_for_wire[w]
        levels.append({"level": lv, "wire": w, "native": runs_as == lv, "runs_as": runs_as})
    return {"model": model, "known": True, "dial": True, "can_off": can_off, "levels": levels}


def effort_menu(provider: str, model: str, base_url: Optional[str] = None) -> dict:
    key = ((provider or "").strip().lower()[:120], (model or "").strip()[:200])
    hit = _CACHE.get(key)
    if hit is not None:
        return hit
    try:
        fn, can_off = hermes_menu_fns(key[0], key[1], base_url)
    except Exception:
        fn, can_off = None, True
    menu = {"provider": key[0], **build_menu(key[1], fn, can_off)}
    if len(_CACHE) >= _CACHE_MAX:
        _CACHE.pop(next(iter(_CACHE)))
    _CACHE[key] = menu
    return menu
