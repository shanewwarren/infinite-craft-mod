"""Tests for report.py. Run with: python3 -m unittest discover -s tools/playtest -p 'test_*.py'"""

import gzip
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import report  # noqa: E402

LOG = HERE / "testdata" / "session.log"
EXPECTED = (HERE / "testdata" / "session.expected.md").read_text(encoding="utf-8")


def setUpModule():
    # the report shows clock times in local time; the golden file is in UTC
    os.environ["TZ"] = "UTC"
    time.tzset()


class ReportTest(unittest.TestCase):

    def test_golden(self):
        with open(LOG, encoding="utf-8") as lines:
            self.assertEqual(EXPECTED, report.build_report(lines))

    def test_odd_names(self):
        line = ('[14:00:00] [Server thread/INFO]: [InfiniteCraft/playtest] {"event":"craft","t":1792936800000,'
                '"id":"ffff0006","player":"Sam","inputs":[{"item":"minecraft:stick","count":1,"name":"A|B\\nC"}],'
                '"name":"A|B\\nC","base":"infinite_craft:custom_crafted_item","components":[],"signature":"launch",'
                '"verdict":"clean","latencyMs":1,"costUsd":0.0,"count":1}')
        out = report.build_report([line])
        self.assertIn('| "A\\|B C" (stick) | A\\|B C |', out)

    def test_gz_and_multiple_files(self):
        lines = LOG.read_text(encoding="utf-8").splitlines(keepends=True)
        with tempfile.TemporaryDirectory() as tmp:
            first = Path(tmp) / "2026-10-25-1.log.gz"
            second = Path(tmp) / "latest.log"
            with gzip.open(first, "wt", encoding="utf-8") as f:
                f.writelines(lines[:9])
            second.write_text("".join(lines[9:]), encoding="utf-8")
            out = subprocess.run([sys.executable, str(HERE / "report.py"), str(first), str(second)],
                                 capture_output=True, text=True, encoding="utf-8", env={**os.environ, "TZ": "UTC"}, check=True)
        self.assertEqual(EXPECTED, out.stdout)

    def test_ignores_garbage(self):
        with open(LOG, encoding="utf-8") as f:
            lines = list(f)
        lines.insert(5, "[14:00:20] [Server thread/INFO]: [InfiniteCraft/playtest] {not json\n")
        self.assertEqual(EXPECTED, report.build_report(lines))

    def test_empty_log(self):
        out = report.build_report(["[14:00:00] [main/INFO]: Loading Minecraft\n"])
        self.assertIn("no crafts in this log", out.lower())


if __name__ == "__main__":
    unittest.main()
