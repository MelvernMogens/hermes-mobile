"""v28 item E: `/api/mobile-tail` — N pesan AKTIF terakhir sebuah session, bentuk sama dengan
`session.resume.messages` (tui_gateway/session_history._history_to_messages):
  user/assistant/system → {role, text, timestamp?, row_id, reasoning?, display_kind?}
  tool                  → {role:"tool", name, context, args?}
Supaya chat raksasa (3000+ pesan) tampil instan; resume penuh menyusul dan MENGGANTI daftar ini.
Read-only sqlite3 (mode=ro); scan mundur via idx_messages_session_id per batch.
"""
from __future__ import annotations

import json
from pathlib import Path

import mobile_insights

BATCH = 400
MAX_SCAN = 6000
_ROLES = ("user", "assistant", "tool", "system")
_DETAIL = ("reasoning", "reasoning_content")


def _text(content) -> str:
    """content bisa string biasa atau JSON list parts ([{type:text,text:..}])."""
    if not isinstance(content, str):
        return ""
    s = content
    if s.startswith("[{") and s.endswith("}]"):
        try:
            parts = json.loads(s)
            return "".join(p.get("text", "") if isinstance(p, dict) else str(p) for p in parts)
        except (ValueError, TypeError):
            return s
    return s


def _ctx(args: dict) -> str:
    """Preview argumen ≤80 char (pengganti agent.display.build_tool_preview — app memakai `args` dulu)."""
    for v in args.values():
        if isinstance(v, str) and v.strip():
            return " ".join(v.split())[:80]
    return ""


def project(rows: list[dict]) -> list[dict]:
    """Pure: baris DB (kronologis) → pesan tampilan; aturan sama dengan _history_to_messages."""
    out: list[dict] = []
    calls: dict[str, tuple[str, dict]] = {}
    for m in rows:
        role = m.get("role")
        if role not in _ROLES or m.get("display_kind") == "hidden":
            continue
        text = _text(m.get("content"))
        if role == "user" and text.lstrip().startswith("[System:"):
            continue
        if role == "assistant" and m.get("tool_calls"):
            try:
                tcs = json.loads(m["tool_calls"]) if isinstance(m["tool_calls"], str) else m["tool_calls"]
            except (ValueError, TypeError):
                tcs = []
            for tc in tcs or []:
                fn = (tc or {}).get("function") or {}
                if tc.get("id") and fn.get("name"):
                    try:
                        a = json.loads(fn.get("arguments") or "{}")
                    except (ValueError, TypeError):
                        a = {}
                    calls[tc["id"]] = (fn["name"], a if isinstance(a, dict) else {})
        if role == "tool":
            name, args = calls.get(m.get("tool_call_id") or "", (None, None))
            name = name or m.get("tool_name") or "tool"
            args = args or {}
            msg: dict = {"role": "tool", "name": name, "context": _ctx(args)}
            if args:
                msg["args"] = args
            out.append(msg)
            continue
        detail = role == "assistant" and any(m.get(k) for k in _DETAIL)
        if not text.strip() and not detail:
            continue
        msg: dict = {"role": role, "text": text}
        ts = m.get("timestamp")
        if isinstance(ts, (int, float)) and ts > 0:
            msg["timestamp"] = float(ts)
        msg["row_id"] = m["id"]
        if role == "assistant":
            r = m.get("reasoning") or m.get("reasoning_content")
            if r:
                msg["reasoning"] = r
        if m.get("display_kind"):
            msg["display_kind"] = m["display_kind"]
        if m.get("display_metadata"):
            try:
                msg["display_metadata"] = json.loads(m["display_metadata"])
            except (ValueError, TypeError):
                pass
        out.append(msg)
    return out


def tail(db: Path, session_id: str, limit: int = 120) -> dict:
    """{messages, has_more, total_active} — `messages` = ≤limit pesan tampilan terakhir (kronologis)."""
    limit = max(1, min(int(limit), 500))
    if not mobile_insights._SESSION_RE.match(session_id or "") or not db.exists():
        return {"messages": [], "has_more": False, "total_active": 0}
    con = mobile_insights._connect(db)
    try:
        cols = {r[1] for r in con.execute("PRAGMA table_info(messages)").fetchall()}
        want = ["id", "role", "content", "timestamp", "tool_call_id", "tool_calls", "tool_name",
                "reasoning", "reasoning_content", "display_kind", "display_metadata"]
        sel = ", ".join(c if c in cols else f"NULL AS {c}" for c in want)
        active = mobile_insights._active_sql(con)
        total = con.execute("SELECT COUNT(*) FROM messages WHERE session_id=? " + active, (session_id,)).fetchone()[0]
        rows: list[dict] = []
        before = None
        projected: list[dict] = []
        while len(rows) < MAX_SCAN:
            q = f"SELECT {sel} FROM messages WHERE session_id=? " + active + \
                ("AND id < ? " if before is not None else "") + "ORDER BY id DESC LIMIT ?"
            args = (session_id, before, BATCH) if before is not None else (session_id, BATCH)
            batch = [dict(zip(want, r)) for r in con.execute(q, args).fetchall()]
            if not batch:
                break
            rows = list(reversed(batch)) + rows
            before = batch[-1]["id"]
            projected = project(rows)
            # > limit: jendela cukup (baris tool di ujung awal jendela bisa kehilangan nama — dibuang slice)
            if len(projected) > limit or len(batch) < BATCH:
                break
    finally:
        con.close()
    msgs = projected[-limit:]
    return {"messages": msgs, "has_more": len(projected) > limit or len(rows) < total, "total_active": total}
