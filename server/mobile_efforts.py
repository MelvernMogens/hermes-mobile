"""Per-model reasoning-effort menu for the phone.

Hermes' effort ladder (minimal … max, ultra) is wider than any single model's API: a level the
model doesn't have is clamped at request time to the nearest weaker one, never escalated.
``ultra`` is Hermes' top tier and no API accepts it verbatim; it runs as whatever Hermes maps it
to for that model (usually the strongest level — but not always, see ``runs_as``). The desktop
lists the whole ladder and lets the clamp happen silently; the phone shows each model's REAL
levels instead.

No hand-made provider tables (Hermes itself warns that hand maps drift): the WIRE ROUTE is picked
per model the way the runtime does (``agent/agent_init._resolve_api_mode`` + the per-provider
``*_model_api_mode`` helpers), and for every ladder level we ask Hermes' own request builders
what would go on the wire. ``can_off`` is derived the same way: Off is offered only when the
builder puts an explicit disable on the wire.

No outbound network on the request path; some provider builders may read local catalog caches
(and an OpenRouter cache may warm itself in the background, as it does in the agent). Runs inside
the hermes-agent venv (the proxy does). Fails open: if Hermes can't say, every level is offered
(``known: false``) — exactly what the desktop offers — and that answer is never cached.
"""

from __future__ import annotations

import threading
from types import SimpleNamespace
from typing import Any, Callable, Optional

LADDER: tuple[str, ...] = ("minimal", "low", "medium", "high", "xhigh", "max", "ultra")
_RANK = {lv: i for i, lv in enumerate(("none",) + LADDER)}

# (provider, model, base_url) -> menu. Only DEFINITE answers are cached (known=True).
_CACHE: dict[tuple[str, str, str], dict] = {}
_CACHE_MAX = 256
_LOCK = threading.Lock()
_PROBE_SESSION = "mobile-effort-probe"

Wire = Callable[[dict], Any]   # reasoning_config -> wire payload (anything we can search)


def warm() -> None:
    """Load Hermes' provider registry once (call at proxy startup, off the event loop).
    Provider discovery is not thread-safe: a lookup during a half-finished discovery returns None."""
    with _LOCK:
        try:
            from providers import list_providers
            list_providers()
        except Exception:
            pass


# ── reading the wire ───────────────────────────────────────────────────────────────────────

_EFFORT_KEYS = ("reasoning_effort", "verbosity", "thinkingLevel", "thinking_level")


def _walk(obj: Any):
    if isinstance(obj, dict):
        yield obj
        for v in obj.values():
            yield from _walk(v)
    elif isinstance(obj, (list, tuple)):
        for v in obj:
            yield from _walk(v)


def wire_effort(payload: Any) -> Optional[str]:
    """The effort word a request payload carries (Hermes-shaped: top-level reasoning_effort,
    OpenRouter's verbosity, reasoning.effort, Gemini thinkingLevel / thinking_level, Claude's
    output_config.effort, or a Claude thinking budget)."""
    for d in _walk(payload):
        for k in _EFFORT_KEYS:
            v = d.get(k)
            if isinstance(v, str) and v and v != "none":
                return v.lower()
        r = d.get("reasoning")
        if isinstance(r, dict) and isinstance(r.get("effort"), str) and r["effort"] not in ("", "none"):
            return r["effort"].lower()
        oc = d.get("output_config")
        if isinstance(oc, dict) and isinstance(oc.get("effort"), str) and oc["effort"]:
            return oc["effort"].lower()
    for d in _walk(payload):
        t = d.get("thinking")
        if isinstance(t, dict) and t.get("budget_tokens"):
            return f"budget:{t['budget_tokens']}"
    return None


def wire_disables(payload: Any) -> bool:
    """True when a payload carries an EXPLICIT thinking disable (omission is not a disable:
    most thinking models think by default)."""
    for d in _walk(payload):
        t = d.get("thinking")
        if isinstance(t, dict) and str(t.get("type", "")).lower() == "disabled":
            return True
        r = d.get("reasoning")
        if isinstance(r, dict) and (r.get("enabled") is False or r.get("effort") == "none"):
            return True
        if d.get("reasoning_effort") == "none" or d.get("think") is False:
            return True
        if d.get("enable_thinking") is False:
            return True
        for k in ("thinkingBudget", "thinking_budget"):
            if k in d and d.get(k) == 0:
                return True
    return False


def _has_content(payload: Any) -> bool:
    """Anything actually on the wire (not just empty containers)."""
    if isinstance(payload, dict):
        return any(_has_content(v) for v in payload.values())
    if isinstance(payload, (list, tuple)):
        return any(_has_content(v) for v in payload)
    return payload is not None and payload != ""


# ── route per model (mirrors agent_init._resolve_api_mode) ─────────────────────────────────

def _route(slug: str, model: str, base_url: Optional[str]) -> tuple[str, Any]:
    """(api_mode, provider profile or None) the agent would use for this provider/model."""
    from providers import get_provider_profile

    profile = get_provider_profile(slug) if slug else None
    if profile is None and slug.startswith("custom"):
        profile = get_provider_profile("custom")    # named custom endpoints share the custom profile
    if slug in ("openai-codex", "xai", "xai-oauth"):
        return "codex_responses", profile
    if slug == "anthropic":
        return "anthropic_messages", profile
    try:
        if slug == "copilot":
            from hermes_cli.models import copilot_model_api_mode
            return copilot_model_api_mode(model), profile          # id pattern only, no catalog fetch
        if slug.startswith("opencode"):
            from hermes_cli.models import opencode_model_api_mode
            return opencode_model_api_mode(slug, model), profile
        if slug in ("azure-foundry", "azure"):
            from hermes_cli.models import azure_foundry_model_api_mode
            mode = azure_foundry_model_api_mode(model)
            if mode:
                return mode, profile
        if slug in ("nous", "nous-portal", "nousresearch"):
            from hermes_cli.providers import nous_api_mode
            return nous_api_mode(model), profile
    except Exception:
        pass
    url = (base_url or getattr(profile, "base_url", "") or "").lower().rstrip("/")
    if url.endswith("/anthropic"):
        return "anthropic_messages", profile
    return (getattr(profile, "api_mode", "") or "chat_completions"), profile


# ── per-route builders ─────────────────────────────────────────────────────────────────────

def _anthropic_wire(model: str) -> Wire:
    from agent.anthropic_adapter import _thinking_kwargs

    return lambda cfg: _thinking_kwargs(cfg, model, 4096)


def _legacy_claude_off(model: str) -> bool:
    """Manual-thinking Claude (budget_tokens) is OFF when no budget is sent — no disable needed."""
    try:
        from agent.anthropic_adapter import _is_claude_model, _supports_adaptive_thinking
        return bool(_is_claude_model(model) and not _supports_adaptive_thinking(model))
    except Exception:
        return False


def _github_responses_wire(model: str) -> Wire:
    from agent.reasoning_params import ReasoningParamsMixin

    def wire(cfg: dict):
        stand_in = SimpleNamespace(model=model, reasoning_config=cfg)
        extra = ReasoningParamsMixin._github_models_reasoning_extra_body(stand_in)
        return {"reasoning": extra} if extra else {}

    return wire


def _responses_wire(slug: str, model: str, base_url: Optional[str]) -> Wire:
    from agent.transports.codex import _reasoning_fields, _resolve_reasoning

    is_xai = slug in ("xai", "xai-oauth")

    def wire(cfg: dict):
        params = {
            "reasoning_config": cfg, "provider": "xai" if is_xai else slug, "base_url": base_url,
            "is_codex_backend": slug == "openai-codex", "is_xai_responses": is_xai,
        }
        effort, enabled = _resolve_reasoning(model, params)
        return _reasoning_fields(model, params, effort=effort, enabled=enabled,
                                 replay_encrypted_reasoning=False, is_xai_responses=is_xai,
                                 is_github_responses=False)

    return wire


def _supports_reasoning(slug: str, model: str) -> bool:
    """The agent's gate for sending reasoning on chat routes (cache-only catalog lookups)."""
    if slug != "openrouter":
        return True
    try:
        from hermes_cli.models_reasoning_caps import openrouter_model_reasoning_capabilities
        caps = openrouter_model_reasoning_capabilities(model)
    except Exception:
        caps = None
    if caps is not None:
        return bool(caps.get("supports_reasoning"))
    from agent.reasoning_params import _OPENROUTER_REASONING_PREFIXES
    return (model or "").lower().startswith(_OPENROUTER_REASONING_PREFIXES)


def _chat_wire(profile, slug: str, model: str, base_url: Optional[str]) -> Wire:
    from agent.transports.chat_completions import _reasoning_config_for_model

    supports = _supports_reasoning(slug, model)

    def wire(cfg: dict):
        cfg = _reasoning_config_for_model(model, cfg)
        extra, top = profile.build_api_kwargs_extras(
            reasoning_config=cfg, supports_reasoning=supports, model=model, base_url=base_url,
            qwen_session_metadata=None, ollama_num_ctx=None, session_id=_PROBE_SESSION,
        )
        try:
            body = profile.build_extra_body(
                session_id=_PROBE_SESSION, provider_preferences=None, model=model,
                base_url=base_url, reasoning_config=cfg, openrouter_min_coding_score=None,
            ) or {}
        except Exception:
            body = {}
        return {"top": top, "extra": extra, "body": body}

    return wire


def hermes_fns(provider: str, model: str, base_url: Optional[str] = None
               ) -> tuple[Optional[Callable[[str], Optional[str]]], bool, bool]:
    """(``level -> wire effort`` from Hermes' real builders or None,
    Off really turns thinking off, On/Off is a real toggle for a model with no levels)."""
    slug = (provider or "").strip().lower()
    mode, profile = _route(slug, model, base_url)
    if mode == "anthropic_messages":
        wire = _anthropic_wire(model)
    elif mode == "codex_responses":
        wire = _github_responses_wire(model) if slug == "copilot" else _responses_wire(slug, model, base_url)
    elif profile is not None:
        wire = _chat_wire(profile, slug, model, base_url)
    else:
        return None, True, True
    raw_off = wire({"enabled": False})
    raw_on = wire({"enabled": True, "effort": "medium"})
    # Off = an explicit disable on the wire (omitting reasoning is NOT off: most models think by
    # default — Responses routes, mandatory-thinking Claude, …), or legacy budget Claude where
    # sending no budget IS off.
    off = (wire_disables(raw_off) or (mode == "anthropic_messages" and _legacy_claude_off(model))) \
        and raw_off != raw_on
    toggle = off and _has_content(raw_on)
    return (lambda level: wire_effort(wire({"enabled": True, "effort": level}))), off, toggle


# ── menu ───────────────────────────────────────────────────────────────────────────────────

def _budget_name(w: Optional[str]) -> Optional[str]:
    """Claude budget-style wires → the level that budget belongs to in Hermes."""
    if not w or not w.startswith("budget:"):
        return w
    try:
        from agent.anthropic_adapter import THINKING_BUDGET
        by_budget = {str(v): k for k, v in THINKING_BUDGET.items()}
        return by_budget.get(w.split(":", 1)[1], w)
    except Exception:
        return w


def build_menu(model: str, wire: Optional[Callable[[str], Optional[str]]], can_off: bool = True,
               toggle: bool = True) -> dict:
    """Pure: turn a ``level -> wire`` function into the phone's menu.

    ``levels`` = every ladder level with ``wire`` (what Hermes sends; None = no effort field),
    ``native`` (the model has this level itself) and ``runs_as`` (the model's own level a
    clamped one lands on). ``dial`` is False when no level puts an effort on the wire.
    """
    if wire is None:
        return {"model": model, "known": False, "dial": True, "can_off": True,
                "levels": [{"level": lv, "wire": None, "native": True, "runs_as": lv} for lv in LADDER]}
    wires: dict[str, Optional[str]] = {}
    for lv in LADDER:
        try:
            wires[lv] = _budget_name(wire(lv))
        except Exception:
            return build_menu(model, None)
    if not any(wires.values()):
        # no levels: Off/On is offered only when both really change the request
        return {"model": model, "known": True, "dial": False, "can_off": can_off and toggle, "levels": []}
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
    key = ((provider or "").strip().lower()[:120], (model or "").strip()[:200], (base_url or "").strip().lower()[:300])
    with _LOCK:
        hit = _CACHE.get(key)
        if hit is not None:
            return hit
        try:
            fn, can_off, toggle = hermes_fns(key[0], key[1], base_url or None)
        except Exception:
            fn, can_off, toggle = None, True, True
        menu = {"provider": key[0], **build_menu(key[1], fn, can_off, toggle)}
        if menu["known"]:                       # never pin an "I don't know" answer
            while len(_CACHE) >= _CACHE_MAX:
                _CACHE.pop(next(iter(_CACHE)))
            _CACHE[key] = menu
        return menu
