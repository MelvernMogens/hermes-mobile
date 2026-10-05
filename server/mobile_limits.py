"""Live 'limits' panel data for the phone: plan limits per provider + Mac RAM.

Plan limits reuse hermes-agent's own fetcher (the same numbers `/usage` prints),
so the phone shows exactly what the desktop/CLI would. Provider calls are slow
(network) and rate-limited upstream → cached for CACHE_S.
"""
from __future__ import annotations

import re
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

CACHE_S = 60.0
# providers whose account usage hermes-agent knows how to fetch, in display order
PROVIDERS = (("anthropic", "Claude"), ("openai-codex", "ChatGPT"), ("openrouter", "OpenRouter"), ("nous", "Nous"))
_cache: dict[str, tuple[float, object]] = {}


def _iso(dt: datetime | None) -> str | None:
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).isoformat()


def _plan_limits() -> list[dict]:
    try:
        from agent.account_usage import fetch_account_usage  # hermes-agent venv
    except Exception:
        return []

    def one(item):
        slug, label = item
        try:
            snap = fetch_account_usage(slug)
        except Exception:
            return None
        if snap is None or not snap.windows:
            return None
        return {
            "provider": slug,
            "label": label,
            "plan": snap.plan,
            "windows": [
                {"label": w.label, "used_percent": w.used_percent, "resets_at": _iso(w.reset_at)}
                for w in snap.windows if w.used_percent is not None
            ],
            "note": next((d for d in snap.details if "reset" in d.lower() or "extra" in d.lower()), None),
        }

    with ThreadPoolExecutor(max_workers=len(PROVIDERS)) as ex:
        return [r for r in ex.map(one, PROVIDERS) if r and r["windows"]]


def parse_vm_stat(text: str, total_bytes: int) -> dict:
    """Activity-Monitor style 'Memory Used' = app (active+? approximated) + wired + compressed.

    vm_stat gives page counts. Used = total - (free + inactive + speculative + purgeable)
    mirrors what macOS reports as available memory, without needing root.
    """
    page = int(m.group(1)) if (m := re.search(r"page size of (\d+) bytes", text)) else 16384

    def pages(name: str) -> int:
        m = re.search(rf"^{re.escape(name)}:\s+(\d+)\.", text, re.M)
        return int(m.group(1)) if m else 0

    available = (pages("Pages free") + pages("Pages inactive") + pages("Pages speculative")
                 + pages("Pages purgeable")) * page
    used = max(0, total_bytes - available)
    return {"used_bytes": used, "total_bytes": total_bytes,
            "used_percent": round(used * 100.0 / total_bytes, 1) if total_bytes else None}


def _ram() -> dict | None:
    try:
        total = int(subprocess.run(["sysctl", "-n", "hw.memsize"], capture_output=True, text=True, timeout=5).stdout.strip())
        vm = subprocess.run(["vm_stat"], capture_output=True, text=True, timeout=5).stdout
        out = parse_vm_stat(vm, total)
        sw = subprocess.run(["sysctl", "-n", "vm.swapusage"], capture_output=True, text=True, timeout=5).stdout
        if m := re.search(r"used = ([\d.]+)M", sw):
            out["swap_used_bytes"] = int(float(m.group(1)) * 1024 * 1024)
        return out
    except Exception:
        return None


def limits_snapshot() -> dict:
    now = time.time()
    hit = _cache.get("plan")
    if hit and now - hit[0] < CACHE_S:
        plans = hit[1]
    else:
        plans = _plan_limits()
        _cache["plan"] = (now, plans)
    return {"plans": plans, "ram": _ram(), "fetched_at": datetime.now(timezone.utc).isoformat()}
