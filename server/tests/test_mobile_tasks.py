"""v28 item A: /api/mobile-tasks — server/mobile_tasks.py dengan temp state.db per profile."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import mobile_tasks as mt  # noqa: E402
from statedb_fixture import make_db, msg, session  # noqa: E402


class Tasks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = Path(self.tmp.name)
        self.coder = make_db(self.home, "coder")
        self.qa = make_db(self.home, "qa")
        (self.home / ".hermes" / "profiles" / "nodb").mkdir(parents=True)  # profile tanpa state.db

        # coder: tugas lama selesai + tugas terbaru masih jalan
        session(self.coder, "c_old", title="# BRIEF @coder — fix login", started=100)
        msg(self.coder, "c_old", "user", "fix login", 100)
        msg(self.coder, "c_old", "assistant", "Done: **login** fixed.\nMEDIA:/Users/x/shot.png", 160)
        session(self.coder, "c_new", title="build v28", started=300)
        msg(self.coder, "c_new", "user", "build v28", 300)
        msg(self.coder, "c_new", "tool", "ok", 310)
        # qa: berhenti di tool row; jawaban asisten terakhir di-rewind (active=0) harus diabaikan
        session(self.qa, "q1", title="", started=200)
        msg(self.qa, "q1", "user", "run the smoke tests", 200)
        msg(self.qa, "q1", "assistant", "early prose", 205)
        msg(self.qa, "q1", "tool", "boom", 210)
        msg(self.qa, "q1", "assistant", "rewound answer", 220, active=0)
        # session non-tool tidak boleh muncul
        session(self.qa, "q_cli", source="cli", title="chat", started=400)

    def tearDown(self):
        self.tmp.cleanup()

    def _tasks(self, running=frozenset()):
        return {t["id"]: t for t in mt.list_tasks(60, home=self.home, running=set(running))}

    def test_running_done_stopped_and_order(self):
        rows = mt.list_tasks(60, home=self.home, running={"coder"})
        self.assertEqual([r["id"] for r in rows], ["c_new", "q1", "c_old"])
        t = {r["id"]: r for r in rows}
        self.assertEqual(t["c_new"]["status"], "running")  # proses hidup + session terbaru profile itu
        self.assertEqual(t["c_old"]["status"], "done")     # proses hidup tapi bukan session terbaru
        self.assertEqual(t["c_old"]["result"], "Done: login fixed.")
        self.assertEqual(t["c_old"]["title"], "fix login")
        self.assertEqual(t["c_old"]["last_activity"], 160)

    def test_no_process_tool_last_is_stopped_and_active0_ignored(self):
        t = self._tasks()
        self.assertEqual(t["c_new"]["status"], "stopped")
        self.assertEqual(t["q1"]["status"], "stopped")
        self.assertEqual(t["q1"]["result"], "early prose")       # bukan \"rewound answer\"
        self.assertEqual(t["q1"]["last_activity"], 210)
        self.assertEqual(t["q1"]["title"], "run the smoke tests")  # judul kosong → prompt pertama
        self.assertNotIn("q_cli", t)

    def test_profile_without_db_skipped(self):
        self.assertEqual([p for p, _ in mt.bot_dbs(self.home)], ["coder", "qa"])

    def test_failed_end_reason(self):
        self.assertEqual(mt.task_status("tool", "x", "error: provider timeout", live=False), "failed")
        self.assertEqual(mt.task_status("user", "x", "cli_close", live=False), "stopped")


if __name__ == "__main__":
    unittest.main()
