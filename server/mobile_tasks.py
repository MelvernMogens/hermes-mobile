"""v28: riwayat tugas bot (session source='tool') lintas semua profile bot — read-only.

Tugas dari `bot-run` / Give task = proses CLI `hermes -p <bot> chat --source tool`; tidak
terlihat di session.active_list gateway. Status diturunkan dari proses hidup + baris terakhir
transcript. sqlite3 stdlib, `mode=ro` — tidak pernah menulis.
"""
from __future__ import annotations

import re
import sqlite3
import subprocess
from pathlib import Path

import mobile_insights
import mobile_mac

RESULT_MAX = 160
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


def running_profiles() -> set[str]:
    """Profile dengan proses bot-run / `chat --source tool` hidup (logika mobile_mac.bot_work)."""
    try:
        ps = subprocess.run(["ps", "-axo", "etime=,command="], capture_output=True, text=True, timeout=5).stdout
    except Exception:
        return set()
    return set(mobile_mac.parse_bot_procs(ps))


def clean_title(title: str) -> str:
    t = re.sub(r"^#\s*", "", title or "").strip()
    t = re.sub(r"^BRIEF\s*@?\w+\s*[—-]\s*", "", t).strip()
    return " ".join(t.split())[:120]


def prose(text: str, limit: int = RESULT_MAX) -> str:
    """Satu baris prosa: tanpa markdown, tanpa baris MEDIA:, whitespace diringkas."""
    return mobile_insights._flatten(text or "", limit)


def task_status(last_role: str | None, last_content: str | None, end_reason: str | None,
                live: bool) -> str:
    """Pure: running (proses hidup + session terbaru) > done (baris aktif terakhir = jawaban
    asisten) > failed (end_reason error) > stopped."""
    if live:
        return "running"
    if last_role == "assistant" and (last_content or "").strip():
        return "done"
    if end_reason and _FAILED_END.search(end_reason):
        return "failed"
    return "stopped"


def _profile_tasks(profile: str, db: Path, limit: int, live_profile: bool) -> list[dict]:
    con = mobile_insights._connect(db)
    try:
        active = mobile_insights._active_sql(con)
        rows = con.execute(
            "SELECT id, COALESCE(title,''), started_at, ended_at, COALESCE(message_count,0), "
            "COALESCE(end_reason,'') FROM sessions WHERE source='tool' "
            "ORDER BY started_at DESC LIMIT ?", (limit,)).fetchall()
        out = []
        for i, (sid, title, started, ended, count, end_reason) in enumerate(rows):
            last = con.execute(
                "SELECT role, content, timestamp FROM messages WHERE session_id=? " + active +
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
            status = task_status(last[0] if last else None, last[1] if last else None, end_reason,
                                 live=live_profile and i == 0)
            out.append({
                "profile": profile, "id": sid, "title": clean_title(title) or "Task",
                "started_at": started, "ended_at": ended, "message_count": count,
                "last_activity": last_at or started, "status": status,
                "result": prose(ans[0]) if ans else "",
            })
        return out
    finally:
        con.close()


def list_tasks(limit: int = 60, home: Path | None = None, running: set[str] | None = None) -> list[dict]:
    """Tugas bot terbaru lintas profile, newest first."""
    limit = max(1, min(int(limit), 200))
    live = running_profiles() if running is None else running
    out: list[dict] = []
    for profile, db in bot_dbs(home):
        try:
            out += _profile_tasks(profile, db, limit, profile in live)
        except sqlite3.Error:
            continue
    out.sort(key=lambda t: t["started_at"] or 0, reverse=True)
    return out[:limit]
