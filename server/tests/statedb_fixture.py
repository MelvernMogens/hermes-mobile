"""Temp HOME dengan ~/.hermes/state.db + profiles/<bot>/state.db (skema minimal yang dipakai v28)."""
from __future__ import annotations

import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE sessions (id TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT, started_at REAL NOT NULL,
    ended_at REAL, end_reason TEXT, message_count INTEGER DEFAULT 0, model TEXT);
CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, role TEXT NOT NULL,
    content TEXT, tool_call_id TEXT, tool_calls TEXT, tool_name TEXT, timestamp REAL NOT NULL,
    reasoning TEXT, active INTEGER NOT NULL DEFAULT 1, display_kind TEXT);
CREATE INDEX idx_messages_session_id ON messages(session_id, id);
"""


def make_db(home: Path, profile: str = "default") -> Path:
    db = home / ".hermes" / ("state.db" if profile == "default" else f"profiles/{profile}/state.db")
    db.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(db)
    con.executescript(SCHEMA)
    con.commit()
    con.close()
    return db


def session(db: Path, sid: str, source: str = "tool", title: str = "", started: float = 1.0,
            ended: float | None = None, end_reason: str | None = None, count: int = 0) -> None:
    con = sqlite3.connect(db)
    con.execute("INSERT INTO sessions(id, source, title, started_at, ended_at, end_reason, message_count) "
                "VALUES (?,?,?,?,?,?,?)", (sid, source, title, started, ended, end_reason, count))
    con.commit()
    con.close()


def msg(db: Path, sid: str, role: str, content: str, ts: float, active: int = 1, **extra) -> int:
    con = sqlite3.connect(db)
    cols = ["session_id", "role", "content", "timestamp", "active", *extra]
    cur = con.execute(f"INSERT INTO messages({','.join(cols)}) VALUES ({','.join('?' * len(cols))})",
                      (sid, role, content, ts, active, *extra.values()))
    con.commit()
    rid = cur.lastrowid
    con.close()
    return rid
