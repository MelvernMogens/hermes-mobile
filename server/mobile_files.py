"""v28 item C: galeri semua file yang dikirim agent (baris `MEDIA:/abs/path` di jawaban asisten).

Scan ~400 session terbaru lintas default + profile bot, read-only. Hanya path di bawah $HOME
dengan suffix yang juga dilayani /api/mobile-media; file yang sudah hilang dilewati.
"""
from __future__ import annotations

import os
import re
import sqlite3
import threading
import time
from pathlib import Path

import mobile_insights
import mobile_tasks

SESSION_SCAN = 400
# Biasanya baris sendiri; data nyata juga punya "**Label:** MEDIA:/x.png" dan "> MEDIA:/x" (kutipan).
# Contoh dalam backtick (`MEDIA:/path/ke/file.pdf`) sengaja tidak cocok.
_MEDIA_LINE = re.compile(r"(?:^|(?<=[\s>]))MEDIA:\s*(/[^\s`'\"<>]+)", re.M)
_IMAGE = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"}
_VIDEO = {".mp4", ".m4v", ".webm", ".mov", ".mkv", ".avi"}
_AUDIO = {".mp3", ".wav", ".m4a"}
KINDS = ("all", "image", "video", "audio", "doc")
CACHE_TTL = 30.0
_cache: dict[str, tuple[float, list[dict]]] = {}
_lock = threading.Lock()


def kind_of(path: str, allowed: set[str] | frozenset[str]) -> str | None:
    """image/video/audio/doc dari suffix; None kalau suffix tidak diizinkan (allowlist mobile-media)."""
    suf = os.path.splitext(path)[1].lower()
    if suf not in allowed:
        return None
    if suf in _IMAGE:
        return "image"
    if suf in _VIDEO:
        return "video"
    if suf in _AUDIO:
        return "audio"
    return "doc"


def media_paths(content: str) -> list[str]:
    return [m.group(1).strip().strip("`'\"") for m in _MEDIA_LINE.finditer(content or "")]


def _scan_db(profile: str, db: Path) -> list[tuple[float, str, str, str, str]]:
    """[(at, path, profile, session_id, session_title)] dari SESSION_SCAN session terbaru."""
    con = mobile_insights._connect(db)
    try:
        active = mobile_insights._active_sql(con)
        sessions = con.execute(
            "SELECT id, COALESCE(title,'') FROM sessions ORDER BY started_at DESC LIMIT ?",
            (SESSION_SCAN,)).fetchall()
        out = []
        for sid, title in sessions:
            for content, ts in con.execute(
                    "SELECT content, timestamp FROM messages WHERE session_id=? AND role='assistant' "
                    "AND instr(content, 'MEDIA:') > 0 " + active, (sid,)):
                for p in media_paths(content):
                    out.append((ts or 0.0, p, profile, sid, title))
        return out
    finally:
        con.close()


def collect(allowed: set[str] | frozenset[str], home: Path | None = None) -> list[dict]:
    """Semua file (newest first, dedupe per path resolved). Tanpa filter kind/limit."""
    home_dir = (home or Path.home()).resolve()
    hits: list[tuple[float, str, str, str, str]] = []
    for profile, db in mobile_tasks.all_dbs(home):
        try:
            hits += _scan_db(profile, db)
        except sqlite3.Error:
            continue
    hits.sort(key=lambda h: h[0], reverse=True)
    seen: set[str] = set()
    out = []
    for at, raw, profile, sid, title in hits:
        try:
            real = Path(raw).resolve(strict=True)
        except (OSError, RuntimeError):
            continue  # file sudah hilang
        key = str(real)
        if key in seen or home_dir not in real.parents or not real.is_file():
            continue
        kind = kind_of(key, allowed)
        if kind is None:
            continue
        seen.add(key)
        out.append({"path": raw, "name": real.name, "kind": kind, "size": real.stat().st_size, "at": at,
                    "profile": profile, "session_id": sid,
                    "session_title": mobile_tasks.clean_title(title)})
    return out


def list_files(allowed: set[str] | frozenset[str], kind: str = "all", limit: int = 120,
               home: Path | None = None, now: float | None = None) -> list[dict]:
    """Dicache 30 dtk (scan penuh lintas DB), filter kind + limit di atas cache."""
    now = time.monotonic() if now is None else now
    ck = str(home or "")
    with _lock:
        hit = _cache.get(ck)
        if hit is None or now - hit[0] > CACHE_TTL:
            hit = (now, collect(allowed, home))
            _cache[ck] = hit
    rows = hit[1] if kind == "all" else [r for r in hit[1] if r["kind"] == kind]
    return rows[:max(1, min(int(limit), 500))]
