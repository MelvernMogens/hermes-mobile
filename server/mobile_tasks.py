"""v28: riwayat tugas bot (session source='tool') lintas semua profile bot — read-only.

Tugas dari `bot-run` / Give task = proses CLI `hermes -p <bot> chat --source tool`; tidak
terlihat di session.active_list gateway. Status diturunkan dari proses hidup + baris terakhir
transcript. sqlite3 stdlib, `mode=ro` — tidak pernah menulis.
"""
from __future__ import annotations

import re
import sqlite3
import subprocess
import time
from pathlib import Path

import mobile_insights
import mobile_mac

RESULT_MAX = 160
# review fix: session tanpa ended_at yang masih aktif < 10 menit di profile yang prosesnya hidup = running
# (bukan cuma session terbaru — dua bot-run paralel di satu profile).
RECENT_SECS = 600
_FAILED_END = re.compile(r"error|fail|crash|exception", re.I)


def bot_dbs(home: Path | None = None) -> list[tuple[str, Path]]:
    """[(profile, state.db)] untuk tiap profile bot yang PUNYA state.db (sisanya dilewati)."""
    root = (home or Path.home()) / ".hermes" / "profiles"
    out = []
    for d in sorted(root.glob("*")) if root.is_dir() else ():
        db = d / "state.db"
        if d.is_dir() and mobile_insights._PROFILE_RE.match(d.name) and db.is_file():
            out.append((d.name, db))
    return out


def all_dbs(home: Path | None = None) -> list[tuple[str, Path]]:
    """default + semua bot (yang punya state.db)."""
    default = (home or Path.home()) / ".hermes" / "state.db"
    return ([("default", default)] if default.is_file() else []) + bot_dbs(home)


def running_profiles() -> set[str] | None:
    """Profile dengan proses bot-run / `chat --source tool` hidup (logika mobile_mac.bot_work).
    None = `ps` gagal (tidak diketahui) — JANGAN diperlakukan sebagai \"tidak ada yang jalan\"."""
    try:
        r = subprocess.run(["ps", "-axo", "etime=,command="], capture_output=True, text=True, timeout=5)
    except Exception:
        return None
    if r.returncode != 0:
        return None
    return set(mobile_mac.parse_bot_procs(r.stdout))


def clean_title(title: str) -> str:
    t = re.sub(r"^#\s*", "", title or "").strip()
    t = re.sub(r"^BRIEF\s*@?\w+\s*[—-]\s*", "", t).strip()
    return " ".join(t.split())[:120]


def prose(text: str, limit: int = RESULT_MAX) -> str:
    """Satu baris prosa: tanpa markdown, tanpa baris MEDIA:, whitespace diringkas."""
    return mobile_insights._flatten(text or "", limit)


def task_status(last_role: str | None, last_content: str | None, end_reason: str | None,
                live: bool, last_tool_calls: str | None = None) -> str:
    """Pure: running (proses hidup) > done (baris aktif terakhir = jawaban asisten FINAL — bukan
    prosa yang menyertai tool_calls) > failed (end_reason error) > stopped."""
    if live:
        return "running"
    final = not (last_tool_calls or "").strip() or last_tool_calls.strip() in ("[]", "null")
    if last_role == "assistant" and (last_content or "").strip() and final:
        return "done"
    if end_reason and _FAILED_END.search(end_reason):
        return "failed"
    return "stopped"


def _profile_tasks(profile: str, db: Path, limit: int, live_profile: bool | None,
                   now: float | None = None) -> list[dict]:
    """live_profile: True = proses hidup, False = tidak, None = tidak diketahui (ps gagal)."""
    now = time.time() if now is None else now
    con = mobile_insights._connect(db)
    try:
        active = mobile_insights._active_sql(con)
        has_tc = "tool_calls" in {r[1] for r in con.execute("PRAGMA table_info(messages)").fetchall()}
        rows = con.execute(
            "SELECT id, COALESCE(title,''), started_at, ended_at, COALESCE(message_count,0), "
            "COALESCE(end_reason,'') FROM sessions WHERE source='tool' "
            "ORDER BY started_at DESC LIMIT ?", (limit,)).fetchall()
        out = []
        for i, (sid, title, started, ended, count, end_reason) in enumerate(rows):
            last = con.execute(
                "SELECT role, content, " + ("tool_calls" if has_tc else "NULL") +
                " FROM messages WHERE session_id=? " + active +
                "ORDER BY id DESC LIMIT 1", (sid,)).fetchone()
            last_at = con.execute(
                "SELECT MAX(timestamp) FROM messages WHERE session_id=? " + active, (sid,)).fetchone()[0]
            ans = con.execute(
                "SELECT content FROM messages WHERE session_id=? AND role='assistant' "
                "AND content IS NOT NULL AND TRIM(content)!='' " + active +
                "ORDER BY id DESC LIMIT 1", (sid,)).fetchone()
            if not title:
                first = con.execute(
                    "SELECT content FROM messages WHERE session_id=? AND role='user' " + active +
                    "ORDER BY id LIMIT 1", (sid,)).fetchone()
                title = prose(first[0], 120) if first else ""
            recent_open = ended is None and (last_at or started or 0) > now - RECENT_SECS
            live = (live_profile is True and (i == 0 or recent_open)) or (live_profile is None and recent_open)
            status = task_status(last[0] if last else None, last[1] if last else None, end_reason,
                                 live=live, last_tool_calls=last[2] if last else None)
            out.append({
                "profile": profile, "id": sid, "title": clean_title(title) or "Task",
                "started_at": started, "ended_at": ended, "message_count": count,
                "last_activity": last_at or started, "status": status,
                "result": prose(ans[0]) if ans else "",
            })
        return out
    finally:
        con.close()


_UNSET = object()


def list_tasks(limit: int = 60, home: Path | None = None, running=_UNSET, now: float | None = None) -> list[dict]:
    """Tugas bot terbaru lintas profile, newest first. running: set profile hidup / None = tidak diketahui."""
    limit = max(1, min(int(limit), 200))
    live = running_profiles() if running is _UNSET else running
    out: list[dict] = []
    for profile, db in bot_dbs(home):
        try:
            out += _profile_tasks(profile, db, limit, None if live is None else profile in live, now)
        except sqlite3.Error:
            continue
    out.sort(key=lambda t: t["started_at"] or 0, reverse=True)
    return out[:limit]
