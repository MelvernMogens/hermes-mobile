import subprocess
from pathlib import Path

import mobile_mac as mm


def test_parse_pmset():
    t = "Now drawing from 'AC Power'\n -InternalBattery-0 (id=1)\t100%; charged; 0:00 remaining present: true"
    assert mm.parse_pmset_batt(t) == {"percent": 100, "state": "charged", "ac": True}
    t2 = "Now drawing from 'Battery Power'\n -InternalBattery-0 (id=1)\t57%; discharging; 4:10 remaining"
    assert mm.parse_pmset_batt(t2)["ac"] is False
    assert mm.parse_pmset_batt("no battery") is None


def test_parse_load_and_uptime():
    t = "11:47  up 20 days, 18:09, 2 users, load averages: 3.34 2.99 2.51"
    assert mm.parse_load(t) == [3.34, 2.99, 2.51]
    assert mm.parse_uptime_days(t) == 20.0


def test_numstat():
    assert mm.parse_numstat("3\t1\tsrc/a.kt\n-\t-\timg.png\n") == [
        {"path": "src/a.kt", "added": 3, "deleted": 1, "binary": False},
        {"path": "img.png", "added": 0, "deleted": 0, "binary": True},
    ]


def test_unknown_action_and_service_allowlist():
    assert mm.mac_action("rm -rf")["ok"] is False
    assert mm.mac_action("restart_service", "com.apple.Finder")["ok"] is False


def test_diff_outside_home_rejected():
    assert "error" in mm.diff("/etc")


def test_diff_working_tree(tmp_path, monkeypatch):
    monkeypatch.setattr(mm, "HOME", tmp_path.resolve())
    repo = tmp_path / "proj"
    repo.mkdir()
    g = lambda *a: subprocess.run(["git", "-C", str(repo), *a], check=True, capture_output=True)
    g("init", "-q")
    g("config", "user.email", "t@t"); g("config", "user.name", "t")
    (repo / "a.txt").write_text("one\n")
    g("add", "."); g("commit", "-qm", "init")
    (repo / "a.txt").write_text("one\ntwo\n")
    (repo / "new.txt").write_text("hello\n")
    out = mm.diff(str(repo))
    paths = {f["path"]: f for f in out["files"]}
    assert paths["a.txt"]["added"] == 1
    assert paths["new.txt"].get("untracked")
    assert "+two" in out["patch"] and "+hello" in out["patch"]


def test_diff_never_runs_repo_controlled_commands(tmp_path, monkeypatch):
    monkeypatch.setattr(mm, "HOME", tmp_path.resolve())
    repo = tmp_path / "evil"
    repo.mkdir()
    g = lambda *a: subprocess.run(["git", "-C", str(repo), *a], check=True, capture_output=True)
    g("init", "-q"); g("config", "user.email", "t@t"); g("config", "user.name", "t")
    (repo / "a.txt").write_text("x\n"); g("add", "."); g("commit", "-qm", "i")
    flag = tmp_path / "PWNED"
    g("config", "diff.evil.textconv", f"touch {flag}")
    g("config", "diff.external", f"touch {flag}")
    g("config", "core.fsmonitor", f"touch {flag}")
    (repo / ".gitattributes").write_text("a.txt diff=evil\n")
    (repo / "a.txt").write_text("x\ny\n")
    out = mm.diff(str(repo))
    assert not flag.exists()
    assert "+y" in out["patch"]


def test_hermes_internal_servers_not_listed():
    assert mm._is_hermes_internal("/x/.hermes/hermes-agent/venv/bin/python -m hermes_cli.main --profile default serve --port 0")
    assert mm._is_hermes_internal("/x/venv/bin/python /Users/m/Code/hermes-webui/server.py")
    assert not mm._is_hermes_internal("node /Users/m/Code/reel-studio/node_modules/.bin/vite --port 4545")
