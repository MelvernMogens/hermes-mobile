"""v28 item E: /api/mobile-tail — bentuk sama dengan session.resume messages; temp state.db."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import mobile_tail as mt  # noqa: E402
from statedb_fixture import make_db, msg, session  # noqa: E402


class Tail(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = make_db(Path(self.tmp.name))
        session(self.db, "s1", source="cli", title="Big", started=1)

    def tearDown(self):
        self.tmp.cleanup()

    def test_shape_matches_resume(self):
        u = msg(self.db, "s1", "user", "build it", 10)
        msg(self.db, "s1", "user", "[System: model switched]", 10.5)
        calls = json.dumps([{"id": "c1", "function": {"name": "terminal", "arguments": json.dumps({"command": "ls -la"})}}])
        msg(self.db, "s1", "assistant", "", 11, tool_calls=calls)                 # tool-call only → hilang
        msg(self.db, "s1", "tool", "out", 12, tool_call_id="c1")
        msg(self.db, "s1", "assistant", "old", 13, active=0)                      # rewound
        a = msg(self.db, "s1", "assistant", "done", 14, reasoning="thought")
        got = mt.tail(self.db, "s1", 120)
        self.assertEqual(got["messages"], [
            {"role": "user", "text": "build it", "timestamp": 10.0, "row_id": u},
            {"role": "tool", "name": "terminal", "context": "ls -la", "args": {"command": "ls -la"}},
            {"role": "assistant", "text": "done", "timestamp": 14.0, "row_id": a, "reasoning": "thought"},
        ])
        self.assertEqual(got["total_active"], 5)
        self.assertFalse(got["has_more"])

    def test_limit_returns_last_n_across_batches(self):
        for i in range(mt.BATCH + 50):
            msg(self.db, "s1", "assistant" if i % 2 else "user", f"m{i}", i + 1)
            msg(self.db, "s1", "assistant", "", i + 1.5, tool_calls="[]")  # baris tak tampil di sela
        got = mt.tail(self.db, "s1", 120)
        self.assertEqual(len(got["messages"]), 120)
        self.assertEqual(got["messages"][-1]["text"], f"m{mt.BATCH + 49}")
        self.assertEqual(got["messages"][0]["text"], f"m{mt.BATCH + 50 - 120}")
        self.assertTrue(got["has_more"])

    def test_bad_session_id(self):
        self.assertEqual(mt.tail(self.db, "../x", 10)["messages"], [])


if __name__ == "__main__":
    unittest.main()
