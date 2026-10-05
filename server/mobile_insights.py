"""Read-only insights over Hermes state.db for the mobile app.

Two things the gateway RPC surface does not give a phone:
- the LAST human-visible message per session (``session.list`` preview = first
  prompt, by design of the compressed tip row), and
- token usage per provider (``usage.bars`` is Nous-portal only; key-based
  accounts have no dollar data, but state.db records every token).

Pure stdlib (sqlite3), opened ``mode=ro`` — never writes, never touches leases.
Kept out of desktop_gateway_proxy.py so it can be unit-tested without aiohttp.
"""
from __future__ import annotations

import re
import sqlite3
import time
from pathlib import Path

_PROFILE_RE = re.compile(r"^[A-Za-z0-9_-]{1,40}$")
_SESSION_RE = re.compile(r"^[A-Za-z0-9_.-]{1,80}$")
PREVIEW_MAX = 140
MAX_IDS = 120


def state_db(profile: str | None, home: Path | None = None) -> Path | None:
    """state.db for a profile; None for an unknown/invalid name (no path traversal)."""
    root = (home or Path.home()) / ".hermes"
    if not profile or profile == "default":
        return root / "state.db"
    if not _PROFILE_RE.match(profile):
        return None
    p = root / "profiles" / profile / "state.db"
    return p if p.exists() else None


def _connect(db: Path) -> sqlite3.Connection:
    return sqlite3.connect(f"file:{db}?mode=ro", uri=True, timeout=3)


def _flatten(text: str) -> str:
    """One display line: collapse whitespace, strip markdown noise and MEDIA lines."""
    lines = [ln for ln in text.splitlines() if not ln.strip().startswith("MEDIA:")]
    flat = " ".join(" ".join(lines).split())
    flat = re.sub(r"!?\[([^\]]*)\]\([^)\s]*\)?", r"\1", flat)  # [label](url) -> label
    flat = re.sub(r"[*_`#>|]+", "", flat).strip()
    return flat[:PREVIEW_MAX]


def last_messages(db: Path, ids: list[str]) -> dict[str, dict]:
    """{session_id: {role, text, at}} for each id that has a user/assistant message.

    Uses ``idx_messages_session_id`` (session_id, id) — one indexed probe per id.
    Tool-only assistant rows (empty content) are skipped so the preview is prose.
    """
    out: dict[str, dict] = {}
    clean = [i for i in ids[:MAX_IDS] if _SESSION_RE.match(i)]
    if not clean or not db.exists():
        return out
    con = _connect(db)
    try:
        for sid in clean:
            row = con.execute(
                "SELECT role, content, timestamp FROM messages "
                "WHERE session_id = ? AND role IN ('user','assistant') "
                "AND content IS NOT NULL AND TRIM(content) != '' "
                "ORDER BY id DESC LIMIT 1",
                (sid,),
            ).fetchone()
            if row:
                text = _flatten(row[1])
                if text:
                    out[sid] = {"role": row[0], "text": text, "at": row[2]}
    finally:
        con.close()
    return out


def usage_summary(db: Path, days: int = 30, now: float | None = None) -> dict:
    """Token usage in the last ``days`` (+ today), grouped by provider.

    ``tokens`` = input + output (cache reads are reported separately: on
    subscription plans they dwarf real traffic and would make every bar 100%).
    """
    now = now or time.time()
    since = now - days * 86400
    lt = time.localtime(now)
    today_start = time.mktime((lt.tm_year, lt.tm_mon, lt.tm_mday, 0, 0, 0, 0, 0, -1))
    if not db.exists():
        return {"available": False}
    con = _connect(db)
    try:
        rows = con.execute(
            "SELECT COALESCE(NULLIF(billing_provider,''),'other') AS p, COUNT(*), "
            "COALESCE(SUM(input_tokens),0), COALESCE(SUM(output_tokens),0), "
            "COALESCE(SUM(cache_read_tokens),0), COALESCE(SUM(estimated_cost_usd),0) "
            "FROM sessions WHERE started_at >= ? AND source NOT IN ('tool','kanban') "
            "GROUP BY p ORDER BY COALESCE(SUM(input_tokens),0)+COALESCE(SUM(output_tokens),0) DESC",
            (since,),
        ).fetchall()
        today = con.execute(
            "SELECT COUNT(*), COALESCE(SUM(input_tokens),0)+COALESCE(SUM(output_tokens),0) "
            "FROM sessions WHERE started_at >= ? AND source NOT IN ('tool','kanban')",
            (today_start,),
        ).fetchone()
    finally:
        con.close()
    providers = [
        {"provider": p, "sessions": n, "input": i, "output": o, "cache_read": c,
         "tokens": i + o, "cost_usd": round(cost, 4)}
        for p, n, i, o, c, cost in rows if (i + o) > 0
    ]
    return {
        "available": True,
        "days": days,
        "total_tokens": sum(p["tokens"] for p in providers),
        "total_sessions": sum(p["sessions"] for p in providers),
        "cost_usd": round(sum(p["cost_usd"] for p in providers), 4),
        "today_tokens": int(today[1] or 0),
        "today_sessions": int(today[0] or 0),
        "providers": providers,
    }
