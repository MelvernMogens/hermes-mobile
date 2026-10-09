"""v28 item D: long-poll `/api/mobile-wait` — bangunkan poller notifikasi HP tanpa polling boros.

Cursor = string opak "default:31660,qa:82,...;run=coder+qa" = MAX(messages.id) per profile + set
profile bot yang punya proses bot-run hidup. Handler mengecek tiap 1.5 dtk (query MAX(id) = lookup
rowid, murah) dan pulang begitu ada pesan asisten baru / status tugas bot berubah, atau saat timeout.
HP tetap memakai poller lama sebagai sumber kebenaran; endpoint ini hanya MEMBANGUNKAN.
"""
from __future__ import annotations

import re
import sqlite3
from pathlib import Path

import mobile_insights
import mobile_tasks

_PART = re.compile(r"^([A-Za-z0-9_-]{1,40}):(\d{1,15})$")
MAX_EVENTS = 50


def parse_cursor(cursor: str | None) -> tuple[dict[str, int], set[str]] | None:
    """None = tidak ada / rusak (→ jawab langsung dengan cursor baru, tanpa event)."""
    if not cursor:
        return None
    ids_part, _, run_part = cursor.partition(";")
    ids: dict[str, int] = {}
    for p in filter(None, ids_part.split(",")):
        m = _PART.match(p)
        if not m:
            return None
        ids[m.group(1)] = int(m.group(2))
    run = set()
    if run_part.startswith("run="):
        run = {r for r in run_part[4:].split("+") if mobile_insights._PROFILE_RE.match(r)}
    return ids, run


def format_cursor(ids: dict[str, int], running: set[str]) -> str:
    return ",".join(f"{k}:{v}" for k, v in sorted(ids.items())) + ";run=" + "+".join(sorted(running))


def diff(prev_ids: dict[str, int], prev_run: set[str], cur_ids: dict[str, int],
         cur_run: set[str]) -> tuple[dict[str, int], set[str]]:
    """Pure. → ({profile: id_terakhir_terlihat} yang tumbuh, profile yang status run-nya berubah).
    Profile baru di cursor (tidak ada di prev) = baseline, bukan perubahan; id mundur (DB diganti) juga."""
    grown = {p: prev_ids[p] for p, v in cur_ids.items() if p in prev_ids and v > prev_ids[p]}
    return grown, prev_run ^ cur_run


def snapshot(home: Path | None = None) -> dict[str, int]:
    out = {}
    for profile, db in mobile_tasks.all_dbs(home):
        try:
            con = mobile_insights._connect(db)
            try:
                out[profile] = int(con.execute("SELECT COALESCE(MAX(id),0) FROM messages").fetchone()[0])
            finally:
                con.close()
        except sqlite3.Error:
            continue
    return out


def _db(profile: str, home: Path | None) -> Path | None:
    return mobile_insights.state_db(profile, home)


def message_events(grown: dict[str, int], home: Path | None = None) -> list[dict]:
    """Event "message" per session untuk baris asisten baru (active=1, isi tidak kosong)."""
    events = []
    for profile, after in grown.items():
        db = _db(profile, home)
        if db is None or not db.exists():
            continue
        try:
            con = mobile_insights._connect(db)
            try:
                cols = {r[1] for r in con.execute("PRAGMA table_info(messages)").fetchall()}
                # review fix (baterai): hanya jawaban FINAL (tanpa tool_calls) di session non-tool —
                # prosa di tengah turn & session tugas bot (diliput event "task") tidak membangunkan HP.
                final = "AND (m.tool_calls IS NULL OR TRIM(m.tool_calls) IN ('', '[]', 'null')) " \
                    if "tool_calls" in cols else ""
                rows = con.execute(
                    "SELECT m.session_id, MAX(m.timestamp), COALESCE(s.title,'') FROM messages m "
                    "LEFT JOIN sessions s ON s.id = m.session_id WHERE m.id > ? AND m.role='assistant' "
                    "AND m.content IS NOT NULL AND TRIM(m.content) != '' AND COALESCE(s.source,'') != 'tool' " +
                    final + mobile_insights._active_sql(con).replace("active", "m.active") +
                    "GROUP BY m.session_id ORDER BY MAX(m.id) DESC LIMIT ?", (after, MAX_EVENTS)).fetchall()
            finally:
                con.close()
        except sqlite3.Error:
            continue
        events += [{"kind": "message", "profile": profile, "session_id": sid, "title": title, "at": at}
                   for sid, at, title in rows]
    return events


def task_events(changed: set[str], home: Path | None = None) -> list[dict]:
    """Event "task" untuk profile bot yang status run-nya berubah (sesi tool terbaru profile itu)."""
    events = []
    for profile in sorted(changed):
        db = _db(profile, home)
        if profile == "default" or db is None or not db.exists():
            continue
        try:
            con = mobile_insights._connect(db)
            try:
                row = con.execute("SELECT id, COALESCE(title,''), started_at FROM sessions WHERE source='tool' "
                                  "ORDER BY started_at DESC LIMIT 1").fetchone()
            finally:
                con.close()
        except sqlite3.Error:
            continue
        if row:
            events.append({"kind": "task", "profile": profile, "session_id": row[0],
                           "title": mobile_tasks.clean_title(row[1]), "at": row[2]})
    return events


_run_cache: list = [0.0, set()]
RUN_TTL = 5.0


def _running_cached() -> set[str]:
    """`ps` maks tiap 5 dtk — satu long-poll 25 dtk tidak boleh spawn ps 17x.
    ps gagal → pakai hasil terakhir (bukan set kosong: itu akan terbaca sebagai \"semua bot berhenti\")."""
    import time
    now = time.monotonic()
    if now - _run_cache[0] > RUN_TTL:
        got = mobile_tasks.running_profiles()
        _run_cache[0] = now
        if got is not None:
            _run_cache[1] = got
    return set(_run_cache[1])


def check(cursor: str | None, home: Path | None = None, running: set[str] | None = None) -> dict:
    """Satu langkah (dipanggil berulang oleh handler). → {cursor, events}; cursor selalu terbaru."""
    cur_ids = snapshot(home)
    cur_run = _running_cached() if running is None else running
    cur_run = {p for p in cur_run if p != "default"}
    out_cursor = format_cursor(cur_ids, cur_run)
    prev = parse_cursor(cursor)
    if prev is None:
        return {"cursor": out_cursor, "events": [], "baseline": True}
    grown, changed = diff(prev[0], prev[1], cur_ids, cur_run)
    events = (message_events(grown, home) if grown else []) + (task_events(changed, home) if changed else [])
    return {"cursor": out_cursor, "events": events}
