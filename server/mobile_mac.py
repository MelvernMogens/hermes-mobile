"""Mobile v24 endpoints — Mac control panel, code-change diff, web preview.

Everything here is behind the proxy's cookie auth. Mac actions are a fixed allow-list
(no arbitrary shell): status, lock screen, keep-awake toggle, sleep display, restart a
Hermes service, kill bot Chrome tabs. Diffs are read-only `git` on repos under $HOME.
Web preview only proxies http://127.0.0.1:<port> (dev servers the agent started).
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import time
from pathlib import Path

HOME = Path.home().resolve()
UID = os.getuid()

# ── Mac status ────────────────────────────────────────────────────────────────

def _run(cmd: list[str], timeout: float = 5) -> str:
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout).stdout
    except Exception:
        return ""


def parse_pmset_batt(text: str) -> dict | None:
    m = re.search(r"(\d+)%;\s*([a-zA-Z ]+?);", text)
    if not m:
        return None
    return {"percent": int(m.group(1)), "state": m.group(2).strip(),
            "ac": "AC Power" in text}


def parse_load(text: str) -> list[float]:
    m = re.search(r"load averages?:\s*([\d.]+)[, ]+([\d.]+)[, ]+([\d.]+)", text)
    return [float(x) for x in m.groups()] if m else []


def parse_uptime_days(text: str) -> float | None:
    m = re.search(r"up\s+(\d+)\s+days?", text)
    if m:
        return float(m.group(1))
    return 0.0 if "up" in text else None


_caffeinate_pid_file = HOME / ".hermes" / "cache" / "mobile-caffeinate.pid"


def _caffeinate_pid() -> int | None:
    try:
        pid = int(_caffeinate_pid_file.read_text().strip())
        os.kill(pid, 0)
        return pid
    except Exception:
        return None


def _services() -> list[dict]:
    out = _run(["launchctl", "list"])
    rows = []
    for label, name in (("com.hermes.mobile-serve", "Mobile server"),
                        ("com.hermes.desktop-gateway-proxy", "Mobile proxy"),
                        ("com.hermes.cft-browser", "Bot browser")):
        line = next((l for l in out.splitlines() if l.endswith("\t" + label)), None)
        pid = None
        if line:
            first = line.split("\t")[0]
            pid = int(first) if first.isdigit() else None
        rows.append({"label": label, "name": name, "running": pid is not None, "loaded": line is not None})
    return rows


def _bot_tabs() -> int | None:
    try:
        import urllib.request
        with urllib.request.urlopen("http://127.0.0.1:9333/json", timeout=2) as r:
            return sum(1 for t in json.loads(r.read()) if t.get("type") == "page")
    except Exception:
        return None


def mac_status() -> dict:
    disk = shutil.disk_usage(str(HOME))
    return {
        "battery": parse_pmset_batt(_run(["pmset", "-g", "batt"])),
        "load": parse_load(_run(["uptime"])),
        "uptime_days": parse_uptime_days(_run(["uptime"])),
        "cpu_count": os.cpu_count(),
        "disk": {"free_bytes": disk.free, "total_bytes": disk.total},
        "keep_awake": _caffeinate_pid() is not None,
        "services": _services(),
        "bot_tabs": _bot_tabs(),
        "host": _run(["scutil", "--get", "ComputerName"]).strip() or os.uname().nodename,
        "fetched_at": time.time(),
    }


# ── Mac actions (allow-list) ─────────────────────────────────────────────────

_RESTARTABLE = {"com.hermes.mobile-serve", "com.hermes.cft-browser"}


def mac_action(action: str, arg: str = "") -> dict:
    if action == "lock":
        # same as Ctrl+Cmd+Q
        r = subprocess.run(["osascript", "-e",
                            'tell application "System Events" to keystroke "q" using {control down, command down}'],
                           capture_output=True, text=True, timeout=10)
        return {"ok": r.returncode == 0, "message": "Screen locked" if r.returncode == 0 else (r.stderr.strip() or "lock failed")}
    if action == "display_sleep":
        r = subprocess.run(["pmset", "displaysleepnow"], capture_output=True, text=True, timeout=10)
        return {"ok": r.returncode == 0, "message": "Display off"}
    if action == "keep_awake":
        on = arg != "off"
        pid = _caffeinate_pid()
        if on and pid is None:
            p = subprocess.Popen(["caffeinate", "-dimsu", "-t", str(8 * 3600)],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
            _caffeinate_pid_file.parent.mkdir(parents=True, exist_ok=True)
            _caffeinate_pid_file.write_text(str(p.pid))
            return {"ok": True, "message": "Mac stays awake for 8 hours"}
        if not on and pid is not None:
            try:
                os.kill(pid, 15)
            except Exception:
                pass
            _caffeinate_pid_file.unlink(missing_ok=True)
            return {"ok": True, "message": "Normal sleep restored"}
        return {"ok": True, "message": "Already " + ("on" if on else "off")}
    if action == "restart_service":
        if arg not in _RESTARTABLE:
            return {"ok": False, "message": "service not allowed"}
        r = subprocess.run(["launchctl", "kickstart", "-k", f"gui/{UID}/{arg}"], capture_output=True, text=True, timeout=20)
        return {"ok": r.returncode == 0, "message": "Restarted" if r.returncode == 0 else (r.stderr.strip() or "restart failed")}
    if action == "close_bot_tabs":
        try:
            import urllib.request
            with urllib.request.urlopen("http://127.0.0.1:9333/json", timeout=3) as r:
                tabs = [t for t in json.loads(r.read()) if t.get("type") == "page"]
            closed = 0
            for t in tabs[1:]:  # keep one tab so the browser stays alive
                try:
                    urllib.request.urlopen(f"http://127.0.0.1:9333/json/close/{t['id']}", timeout=2).read()
                    closed += 1
                except Exception:
                    pass
            return {"ok": True, "message": f"Closed {closed} tabs"}
        except Exception:
            return {"ok": False, "message": "Bot browser not running"}
    return {"ok": False, "message": "unknown action"}


# ── Code-change diff ─────────────────────────────────────────────────────────

def _git(cwd: Path, *args: str, timeout: float = 10) -> str:
    try:
        # Repo bisa berisi config jahat (diff.external / textconv / fsmonitor / hooks) — matikan semua
        # eksekusi yang dikendalikan repo. Hanya baca.
        safe = ["-c", "core.fsmonitor=false", "-c", "core.hooksPath=/dev/null", "-c", "diff.external=",
                "-c", "core.pager=cat", "-c", "protocol.allow=never"]
        if args and args[0] == "diff":
            args = ("diff", "--no-ext-diff", "--no-textconv", *args[1:])
        env = {**os.environ, "GIT_CONFIG_NOSYSTEM": "1", "GIT_TERMINAL_PROMPT": "0", "GIT_OPTIONAL_LOCKS": "0"}
        return subprocess.run(["git", *safe, "-C", str(cwd), *args], capture_output=True, text=True,
                              timeout=timeout, env=env).stdout
    except Exception:
        return ""


def repo_root(path: str) -> Path | None:
    try:
        p = Path(path).expanduser().resolve()
    except Exception:
        return None
    if p != HOME and HOME not in p.parents:
        return None
    if p.is_file():
        p = p.parent
    top = _git(p, "rev-parse", "--show-toplevel").strip()
    if not top:
        return None
    root = Path(top).resolve()
    return root if (root == HOME or HOME in root.parents) else None


def parse_numstat(text: str) -> list[dict]:
    files = []
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        a, d, name = parts
        files.append({"path": name, "added": int(a) if a.isdigit() else 0,
                      "deleted": int(d) if d.isdigit() else 0, "binary": a == "-"})
    return files


MAX_PATCH = 200_000


def diff(path: str, since: float | None = None) -> dict:
    """Working-tree changes (+ commits since `since`, epoch secs) for the repo containing `path`."""
    root = repo_root(path)
    if root is None:
        return {"error": "not a git repo under your home folder"}
    base = "HEAD"
    commits = []
    if since:
        log = _git(root, "log", f"--since=@{int(since)}", "--format=%H%x09%s%x09%ct")
        for l in log.splitlines():
            h, _, rest = l.partition("\t")
            subj, _, ct = rest.rpartition("\t")
            commits.append({"hash": h[:9], "subject": subj, "at": int(ct) if ct.isdigit() else None})
        if commits:
            oldest = log.splitlines()[-1].split("\t")[0]
            parent = _git(root, "rev-parse", "--verify", "-q", oldest + "^").strip()
            base = parent or _git(root, "hash-object", "-t", "tree", "/dev/null").strip()
    numstat = parse_numstat(_git(root, "diff", "--numstat", base))
    untracked = [l for l in _git(root, "ls-files", "--others", "--exclude-standard").splitlines() if l][:50]
    for u in untracked:
        numstat.append({"path": u, "added": 0, "deleted": 0, "binary": False, "untracked": True})
    patch = _git(root, "diff", "--no-color", "-U3", base, timeout=20)
    for u in untracked[:20]:
        f = root / u
        try:
            if f.is_file() and f.stat().st_size < 60_000:
                body = f.read_text(errors="replace")
                patch += f"\ndiff --git a/{u} b/{u}\nnew file\n--- /dev/null\n+++ b/{u}\n@@ -0,0 +1,{body.count(chr(10)) + 1} @@\n" + \
                         "".join("+" + l + "\n" for l in body.splitlines())
        except Exception:
            pass
    return {
        "repo": str(root), "name": root.name,
        "branch": _git(root, "rev-parse", "--abbrev-ref", "HEAD").strip(),
        "base": base if base == "HEAD" else base[:9],
        "commits": commits, "files": numstat,
        "patch": patch[:MAX_PATCH], "truncated": len(patch) > MAX_PATCH,
    }


# ── Web preview ──────────────────────────────────────────────────────────────

def listening_ports() -> list[dict]:
    """Local dev servers (127.0.0.1 / * listeners on 1024–65535), excluding Hermes' own ports."""
    out = _run(["lsof", "-nP", "-iTCP", "-sTCP:LISTEN"], timeout=8)
    skip = {8788, 8790, 8791, 9333, 9222, 5037}
    dev_procs = {"node", "python", "python3", "python3.1", "ruby", "deno", "bun", "php", "java", "go", "vite",
                 "next-serv", "uvicorn", "gunicorn", "flask", "hugo", "caddy", "nginx", "http-serv", "esbuild"}
    seen: dict[int, dict] = {}
    for line in out.splitlines()[1:]:
        cols = line.split()
        if len(cols) < 9:
            continue
        m = re.search(r":(\d+)$", cols[8])
        if not m:
            continue
        port = int(m.group(1))
        if port < 1024 or port in skip or port in seen:
            continue
        if not (cols[8].startswith("127.0.0.1") or cols[8].startswith("*") or cols[8].startswith("[::1]") or cols[8].startswith("localhost")):
            continue
        proc = cols[0].replace("\\x20", " ")
        if not any(proc.lower().startswith(d) for d in dev_procs):
            continue  # ControlCenter, Figma, MCP helpers, etc. — bukan web dev server
        seen[port] = {"port": port, "process": proc}
    rows = sorted(seen.values(), key=lambda r: r["port"])
    # Probe cepat: hanya yang menjawab HTTP (buang port RPC/socket mentah).
    import urllib.request
    alive = []
    for r in rows[:20]:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{r['port']}/", timeout=1.2) as resp:
                ctype = resp.headers.get("Content-Type", "")
                r["title"] = _title(resp.read(20000).decode("utf-8", "replace")) if "html" in ctype else ""
                r["kind"] = "html" if "html" in ctype else "other"
            alive.append(r)
        except urllib.error.HTTPError as e:
            if e.code < 500:
                r["title"], r["kind"] = "", "other"
                alive.append(r)
        except Exception:
            pass
    return alive


def _title(html: str) -> str:
    m = re.search(r"<title[^>]*>(.*?)</title>", html, re.I | re.S)
    return re.sub(r"\s+", " ", m.group(1)).strip()[:80] if m else ""
