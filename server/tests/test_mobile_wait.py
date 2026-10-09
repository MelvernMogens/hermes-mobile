"""v28 item D: /api/mobile-wait cursor logic (pure) + event extraction on temp state.db."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import mobile_wait as mw  # noqa: E402
from statedb_fixture import make_db, msg, session  # noqa: E402


class Cursor(unittest.TestCase):
    def test_roundtrip(self):
        c = mw.format_cursor({"qa": 82, "default": 31660}, {"coder"})
        self.assertEqual(c, "default:31660,qa:82;run=coder")
        self.assertEqual(mw.parse_cursor(c), ({"default": 31660, "qa": 82}, {"coder"}))
        self.assertEqual(mw.parse_cursor("default:5;run="), ({"default": 5}, set()))

    def test_garbage_is_none(self):
        for bad in (None, "", "default:abc", "../x:1", "a:1,b"):
            self.assertIsNone(mw.parse_cursor(bad), bad)

    def test_diff(self):
        grown, changed = mw.diff({"default": 10, "qa": 5}, {"coder"},
                                 {"default": 12, "qa": 5, "video": 99}, {"qa"})
        self.assertEqual(grown, {"default": 10})       # video baru = baseline, bukan event
        self.assertEqual(changed, {"coder", "qa"})
        self.assertEqual(mw.diff({"default": 10}, set(), {"default": 3}, set()), ({}, set()))  # id mundur


class Check(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = Path(self.tmp.name)
        self.default = make_db(self.home)
        self.coder = make_db(self.home, "coder")
        session(self.default, "d1", source="cli", title="Chat A", started=1)
        msg(self.default, "d1", "user", "hi", 1)
        session(self.coder, "t1", source="tool", title="# build", started=2)

    def tearDown(self):
        self.tmp.cleanup()

    def test_baseline_then_events(self):
        first = mw.check(None, home=self.home, running={"coder"})
        self.assertTrue(first["baseline"])
        self.assertEqual(first["events"], [])
        c = first["cursor"]
        # tidak ada perubahan
        self.assertEqual(mw.check(c, home=self.home, running={"coder"})["events"], [])
        # tool row / user row / assistant kosong / rewound → bukan event; assistant prosa → event
        msg(self.default, "d1", "tool", "x", 2)
        msg(self.default, "d1", "assistant", "  ", 3)
        msg(self.default, "d1", "assistant", "rewound", 4, active=0)
        self.assertEqual(mw.check(c, home=self.home, running={"coder"})["events"], [])
        msg(self.default, "d1", "assistant", "answer", 5)
        res = mw.check(c, home=self.home, running=set())  # coder juga berhenti
        kinds = {(e["kind"], e["profile"], e["session_id"]) for e in res["events"]}
        self.assertEqual(kinds, {("message", "default", "d1"), ("task", "coder", "t1")})
        # review fix: prosa bertool_calls & jawaban di session tugas (source=tool) tidak membangunkan HP
        c2 = res["cursor"]
        msg(self.default, "d1", "assistant", "Now running tests", 6, tool_calls='[{"id":"x"}]')
        msg(self.coder, "t1", "assistant", "step done", 7)
        self.assertEqual(mw.check(c2, home=self.home, running=set())["events"], [])
        task = next(e for e in res["events"] if e["kind"] == "task")
        self.assertEqual(task["title"], "build")
        self.assertNotEqual(res["cursor"], c)


if __name__ == "__main__":
    unittest.main()
