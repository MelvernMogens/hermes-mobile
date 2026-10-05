import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import mobile_limits as ml  # noqa: E402

VM = """Mach Virtual Memory Statistics: (page size of 16384 bytes)
Pages free:                                   100000.
Pages active:                                 300000.
Pages inactive:                               200000.
Pages speculative:                             50000.
Pages wired down:                             150000.
Pages purgeable:                                 10000.
"""


class Ram(unittest.TestCase):
    def test_used_is_total_minus_reclaimable(self):
        total = 16 * 1024 ** 3
        r = ml.parse_vm_stat(VM, total)
        reclaimable = (100000 + 200000 + 50000 + 10000) * 16384
        self.assertEqual(r["used_bytes"], total - reclaimable)
        self.assertTrue(0 < r["used_percent"] < 100)

    def test_snapshot_shape_without_network(self):
        ml._cache["plan"] = (9e18, [])  # pretend fresh cache → no provider calls
        snap = ml.limits_snapshot()
        self.assertEqual(snap["plans"], [])
        self.assertIn("ram", snap)


if __name__ == "__main__":
    unittest.main()
