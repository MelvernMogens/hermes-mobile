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


_OOB_RE = re.compile(r"^\s*\[OUT-OF-BAND USER MESSAGE[^\]]*\]\s*(.*?)\s*\[/OUT-OF-BAND USER MESSAGE\]\s*$", re.S)


def _flatten(text: str) -> str:
    """One display line: collapse whitespace, strip markdown noise, MEDIA/@file lines and
    the server-inlined "--- Attached Context ---" block (model-only, desktop hides it too)."""
    text = re.split(r"(?:^|\n)--- Attached Context ---\s*\n", text, maxsplit=1)[0]
    # steer/redirect rows are stored wrapped in an internal marker — show only the user's words
    m = _OOB_RE.match(text)
    if m:
        text = m.group(1)
    lines = [ln for ln in text.splitlines() if not ln.strip().startswith(("MEDIA:", "@file:", "@image:"))]
    flat = " ".join(" ".join(lines).split())
    if not flat and "MEDIA:" in text:
        flat = "Attachment"
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
        act = _active_sql(con)
        for sid in clean:
            row = con.execute(
                "SELECT role, content, timestamp FROM messages "
                "WHERE session_id = ? AND role IN ('user','assistant') "
                "AND content IS NOT NULL AND TRIM(content) != '' " + act +
                "ORDER BY id DESC LIMIT 1",
                (sid,),
            ).fetchone()
            if row:
                text = _flatten(row[1])
                if text:
                    out[sid] = {"role": row[0], "text": text, "at": row[2]}
                    out[sid].update(_turn_info(con, sid, act))
    finally:
        con.close()
    return out


def _has_col(con, col: str) -> bool:
    return col in {r[1] for r in con.execute("PRAGMA table_info(messages)").fetchall()}


def _turn_info(con, sid: str, act: str) -> dict:
    """When the CURRENT turn started + what it is doing right now (v28 "on air" monitors).

    A turn starts with the first row after the newest user prompt or the newest FINAL
    assistant answer (finish_reason='stop'), whichever is later — not simply at the newest
    user row: goal/continuation turns and system notes ("[System: model changed…]") leave a
    days-old user row in front of a turn that began minutes ago. ``tool`` = the newest tool
    the agent ran in this turn (rows after the last prose answer), for "Running terminal".
    """
    info: dict = {}
    has_fin = _has_col(con, "finish_reason")
    stop = "OR (role = 'assistant' AND finish_reason = 'stop') " if has_fin else ""
    anchor = con.execute(
        "SELECT id, role, timestamp FROM messages WHERE session_id = ? AND (role = 'user' " + stop + ") " + act +
        "ORDER BY id DESC LIMIT 1",
        (sid,),
    ).fetchone()
    if anchor:
        nxt = con.execute(
            "SELECT timestamp FROM messages WHERE session_id = ? AND id > ? " + act + "ORDER BY id LIMIT 1",
            (sid, anchor[0]),
        ).fetchone()
        if nxt:
            info["turn_at"] = nxt[0]
        elif anchor[1] == "user":
            info["turn_at"] = anchor[2]
        if _has_col(con, "tool_name"):
            t = con.execute(
                "SELECT tool_name FROM messages WHERE session_id = ? AND id > ? AND role = 'tool' "
                "AND tool_name IS NOT NULL AND tool_name != '' " + act + "ORDER BY id DESC LIMIT 1",
                (sid, anchor[0]),
            ).fetchone()
            if t:
                info["tool"] = t[0]
    return info


def _active_sql(con) -> str:
    """Rows rewound by edit/regenerate stay in the table with active=0 — skip them (older DBs lack the column)."""
    cols = {r[1] for r in con.execute("PRAGMA table_info(messages)").fetchall()}
    return "AND COALESCE(active, 1) = 1 " if "active" in cols else ""


def user_messages_after(db: Path, session_id: str, after: float, limit: int = 20) -> list[dict]:
    """User prompts in ``session_id`` newer than ``after`` (epoch s), oldest first.

    The gateway emits no event for a prompt typed on ANOTHER surface (desktop):
    a phone watching the session only sees ``message.start`` + the reply. The
    prompt row is persisted before the turn streams, so the phone pulls it here.
    """
    if not _SESSION_RE.match(session_id or "") or not db.exists():
        return []
    con = _connect(db)
    try:
        _ACTIVE_SQL = _active_sql(con)
        rows = con.execute(
            "SELECT id, content, timestamp FROM messages "
            "WHERE session_id = ? AND role = 'user' AND timestamp > ? "
            "AND content IS NOT NULL AND TRIM(content) != '' " + _ACTIVE_SQL +
            "ORDER BY id DESC LIMIT ?",
            (session_id, float(after), max(1, min(int(limit), 50))),
        ).fetchall()
    finally:
        con.close()
    return [{"row_id": r[0], "text": r[1], "at": r[2]} for r in reversed(rows)]


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
