"""v28 item C: /api/mobile-files — server/mobile_files.py dengan temp HOME + state.db."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import mobile_files as mf  # noqa: E402
from statedb_fixture import make_db, msg, session  # noqa: E402

ALLOWED = frozenset({".png", ".mp4", ".pdf", ".md"})


class Files(unittest.TestCase):
    def setUp(self):
        mf._cache.clear()
        self.tmp = tempfile.TemporaryDirectory()
        self.home = Path(self.tmp.name).resolve()
        out = self.home / "out"
        out.mkdir()
        (out / "a.png").write_bytes(b"x" * 10)
        (out / "b.mp4").write_bytes(b"y" * 20)
        (out / "c.pdf").write_bytes(b"z")
        self.a, self.b, self.c = (str(out / n) for n in ("a.png", "b.mp4", "c.pdf"))
        default = make_db(self.home)
        coder = make_db(self.home, "coder")
        session(default, "d1", source="cli", title="Logo work", started=10)
        msg(default, "d1", "assistant", f"here\nMEDIA:{self.a}\nMEDIA:{self.home}/out/missing.png", 11)
        session(coder, "c1", source="tool", title="# BRIEF @coder — render", started=20)
        msg(coder, "c1", "assistant", f"done\nMEDIA:{self.b}", 21)
        msg(coder, "c1", "assistant", f"again\nMEDIA:{self.a}", 25)        # duplikat path (lebih baru)
        msg(coder, "c1", "assistant", f"rewound\nMEDIA:{self.c}", 30, active=0)
        msg(coder, "c1", "user", f"MEDIA:{self.c}", 31)                    # bukan assistant
        msg(coder, "c1", "assistant", "MEDIA:/etc/hosts.md", 32)            # di luar $HOME

    def tearDown(self):
        self.tmp.cleanup()

    def test_newest_first_dedup_missing_skipped(self):
        rows = mf.list_files(ALLOWED, home=self.home)
        self.assertEqual([r["path"] for r in rows], [self.a, self.b])
        a = rows[0]
        self.assertEqual((a["kind"], a["size"], a["at"], a["profile"], a["session_id"]), ("image", 10, 25, "coder", "c1"))
        self.assertEqual(a["session_title"], "render")
        self.assertEqual(rows[1]["kind"], "video")

    def test_kind_filter_and_cache(self):
        self.assertEqual([r["name"] for r in mf.list_files(ALLOWED, "video", home=self.home)], ["b.mp4"])
        (self.home / "out" / "b.mp4").unlink()
        # masih dari cache (30 dtk)
        self.assertEqual(len(mf.list_files(ALLOWED, "video", home=self.home)), 1)
        self.assertEqual(len(mf.list_files(ALLOWED, "video", home=self.home, now=1e12)), 0)

    def test_kind_of(self):
        self.assertEqual(mf.kind_of("/x/a.PNG", ALLOWED), "image")
        self.assertEqual(mf.kind_of("/x/a.md", ALLOWED), "doc")
        self.assertIsNone(mf.kind_of("/x/a.exe", ALLOWED))

    def test_media_paths_inline_and_quoted_not_backticked(self):
        text = ("**Optimus:** MEDIA:/Users/x/o.png\n> MEDIA:/Users/x/q.md\nMEDIA:/Users/x/own.pdf  \n"
                "write `MEDIA:/path/ke/file.pdf` in the reply\nfooMEDIA:/Users/x/no.png")
        self.assertEqual(mf.media_paths(text), ["/Users/x/o.png", "/Users/x/q.md", "/Users/x/own.pdf"])


if __name__ == "__main__":
    unittest.main()
