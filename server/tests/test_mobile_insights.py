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

    def test_turn_at_is_first_row_of_current_turn(self):
        self._msg("s3", "user", "old prompt", 10)
        self._msg("s3", "assistant", "old answer", 11)
        self._msg("s3", "user", "current prompt", 20)
        self._msg("s3", "assistant", "on it", 22)
        self._msg("s3", "tool", "running...", 23)
        got = mi.last_messages(self.db, ["s3"])
        self.assertEqual(got["s3"]["turn_at"], 22)

    def test_turn_after_stale_user_row_starts_at_first_new_row(self):
        # a days-old system note as the newest user row must not make the turn look 3 days long
        con = sqlite3.connect(self.db)
        con.execute("ALTER TABLE messages ADD COLUMN finish_reason TEXT")
        con.execute("ALTER TABLE messages ADD COLUMN tool_name TEXT")
        con.executemany(
            "INSERT INTO messages(session_id, role, content, timestamp, finish_reason, tool_name) VALUES (?,?,?,?,?,?)",
            [("s4", "assistant", "final answer", 100, "stop", None),
             ("s4", "user", "[System: model changed]", 200, None, None),
             ("s4", "assistant", "Gas, starting", 90_000, "tool_calls", None),
             ("s4", "tool", "{}", 90_010, None, "terminal")],
        )
        con.commit(); con.close()
        got = mi.last_messages(self.db, ["s4"])
        self.assertEqual(got["s4"]["turn_at"], 90_000)
        self.assertEqual(got["s4"]["tool"], "terminal")

    def test_old_db_without_finish_reason_never_reports_stale_prompt(self):
        # continuation turn on an old DB: last user row is days old, agent spoke 30s ago
        self._msg("s6", "user", "ancient prompt", 100)
        self._msg("s6", "assistant", "answered days ago", 110)
        self._msg("s6", "assistant", "continuing the goal", 500_000)
        self._msg("s6", "tool", "{}", 500_010)
        got = mi.last_messages(self.db, ["s6"])
        self.assertGreaterEqual(got["s6"]["turn_at"], 500_000)

    def test_just_submitted_turn_uses_user_row(self):
        self._msg("s5", "assistant", "previous", 5)
        self._msg("s5", "user", "new prompt", 50)
        got = mi.last_messages(self.db, ["s5"])
        self.assertEqual(got["s5"]["turn_at"], 50)

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


class UserTail(unittest.TestCase):
    def test_only_user_prompts_after_watermark_oldest_first(self):
        with tempfile.TemporaryDirectory() as d:
            db = Path(d) / "state.db"
            _make_db(db)
            con = sqlite3.connect(db)
            con.executemany(
                "INSERT INTO messages(session_id, role, content, timestamp) VALUES (?,?,?,?)",
                [("s", "user", "old", 10), ("s", "assistant", "a", 11),
                 ("s", "user", "from desktop 1", 20), ("s", "tool", "t", 21),
                 ("s", "user", "from desktop 2", 22), ("other", "user", "x", 30)],
            )
            con.commit()
            con.close()
            got = mi.user_messages_after(db, "s", after=15)
        self.assertEqual([m["text"] for m in got], ["from desktop 1", "from desktop 2"])
        self.assertEqual(mi.user_messages_after(db, "s;drop", after=0), [])


if __name__ == "__main__":
    unittest.main()
