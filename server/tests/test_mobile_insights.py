"""Invariant tests for server/mobile_insights.py (stdlib unittest, temp state.db)."""
import sqlite3
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import mobile_insights as mi  # noqa: E402


def _make_db(path: Path) -> None:
    con = sqlite3.connect(path)
    con.executescript(
        """
        CREATE TABLE sessions (id TEXT PRIMARY KEY, source TEXT, started_at REAL,
            billing_provider TEXT, input_tokens INTEGER, output_tokens INTEGER,
            cache_read_tokens INTEGER, estimated_cost_usd REAL);
        CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT,
            role TEXT, content TEXT, timestamp REAL);
        CREATE INDEX idx_messages_session_id ON messages(session_id, id);
        """
    )
    con.commit()
    con.close()


class LastMessages(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = Path(self.tmp.name) / "state.db"
        _make_db(self.db)

    def tearDown(self):
        self.tmp.cleanup()

    def _msg(self, sid, role, content, ts):
        con = sqlite3.connect(self.db)
        con.execute("INSERT INTO messages(session_id, role, content, timestamp) VALUES (?,?,?,?)", (sid, role, content, ts))
        con.commit()
        con.close()

    def test_newest_prose_wins_over_first_prompt_and_tool_rows(self):
        self._msg("s1", "user", "first prompt", 1)
        self._msg("s1", "assistant", "the **real** latest answer", 2)
        self._msg("s1", "tool", "tool output", 3)
        self._msg("s1", "assistant", "", 4)
        got = mi.last_messages(self.db, ["s1"])
        self.assertEqual(got["s1"]["text"], "the real latest answer")
        self.assertEqual(got["s1"]["role"], "assistant")

    def test_media_lines_and_bad_ids_are_dropped(self):
        self._msg("s2", "assistant", "here you go\nMEDIA:/Users/x/a.png", 1)
        got = mi.last_messages(self.db, ["s2", "../etc", "s2;drop"])
        self.assertEqual(set(got), {"s2"})
        self.assertNotIn("MEDIA", got["s2"]["text"])


class Usage(unittest.TestCase):
    def test_tokens_group_by_provider_and_exclude_worker_sources(self):
        with tempfile.TemporaryDirectory() as d:
            db = Path(d) / "state.db"
            _make_db(db)
            now = time.time()
            con = sqlite3.connect(db)
            con.executemany(
                "INSERT INTO sessions VALUES (?,?,?,?,?,?,?,?)",
                [
                    ("a", "desktop", now - 60, "zai", 100, 50, 9999, 0),
                    ("b", "desktop", now - 120, "anthropic", 10, 5, 0, 0.25),
                    ("c", "tool", now - 60, "zai", 1000, 1000, 0, 0),
                    ("d", "desktop", now - 90 * 86400, "zai", 1000, 1000, 0, 0),
                ],
            )
            con.commit()
            con.close()
            u = mi.usage_summary(db, days=30, now=now)
        by = {p["provider"]: p for p in u["providers"]}
        self.assertEqual(by["zai"]["tokens"], 150)
        self.assertEqual(u["total_tokens"], sum(p["tokens"] for p in u["providers"]))
        self.assertEqual(u["providers"][0]["provider"], "zai")


class ProfilePaths(unittest.TestCase):
    def test_traversal_names_resolve_to_none(self):
        self.assertIsNone(mi.state_db("../../etc"))
        self.assertIsNone(mi.state_db("a/b"))


if __name__ == "__main__":
    unittest.main()
