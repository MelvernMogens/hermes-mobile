#!/usr/bin/env python3
"""M6: proxy multi-surface — HP Android → gateway desktop (kalau hidup) / mobile-serve 8788.

Kenapa proxy (bukan HP langsung ke port desktop):
- Gateway desktop bind 127.0.0.1 dengan port dynamic (--port 0) — gak bisa di-hardcode.
- Mode loopback: WS /api/ws hanya terima peer loopback + ?token= per-spawn (token desktop),
  sedangkan mobile app punya kredensial mobile-serve (password → cookie → ws-ticket).
  Proxy lokal inject token desktop server-side → HP gak pernah melihat token desktop.

Routing (semua path, port proxy 8790):
- GET /api/desktop-port  → JSON {port,pid,surface} gateway aktif (discovery, public)
- /api/ws               → desktop WS (token di-inject) kalau hidup; else 8788 verbatim (ticket)
- /auth/*, /api/auth/*  → 8788 selalu (password-login + ws-ticket mobile)
- /api/media            → 8788 selalu (cookie auth mobile)
- /api/* lain           → desktop kalau hidup (header X-Hermes-Session-Token); else 8788
- WS relay: raw TCP setelah 101 — frame JSON-RPC diteruskan apa adanya.

Discovery READ-ONLY terhadap ~/.hermes/runtime/active_sessions.json (larangan brief M6).
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

import aiohttp
from aiohttp import web
import yarl

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mobile_insights  # noqa: E402  (sibling module, read-only state.db queries)
import mobile_limits  # noqa: E402  (plan limits via hermes-agent + Mac RAM)

LISTEN_HOST = "127.0.0.1"
LISTEN_PORT = int(os.environ.get("HERMES_PROXY_PORT", "8790"))
DESKTOP_HINT_PORT = int(os.environ.get("HERMES_DESKTOP_PORT_HINT", "0") or 0)
MOBILE_PORT = int(os.environ.get("HERMES_MOBILE_PORT", "8788"))
REGISTRY = Path.home() / ".hermes" / "runtime" / "active_sessions.json"

log = logging.getLogger("hermes-proxy")

_TOKEN_RE = re.compile(r'window\.__HERMES_SESSION_TOKEN__\s*=\s*"([^"]+)"')
_token_cache: dict[str, float | str | None] = {"token": None, "at": 0.0}
_TOKEN_TTL = 30.0

_desktop_state: dict[str, float | int] = {"port": 0, "pid": 0, "checked": 0.0}
_STATE_TTL = 2.0


def _loopback_url(port: int) -> yarl.URL:
    return yarl.URL(f"http://127.0.0.1:{port}")


def _desktop_alive() -> tuple[int, int]:
    """(port, pid) gateway desktop yang hidup — resolve + lsof verify; 0 kalau mati."""
    now = time.monotonic()
    if now - float(_desktop_state["checked"]) < _STATE_TTL:
        return int(_desktop_state["port"]), int(_desktop_state["pid"])
    port, pid = 0, 0
    try:
        entries = json.loads(REGISTRY.read_text())["entries"]
        desktop = [e for e in entries if str(e.get("surface")) == "desktop" and e.get("pid")]
        if desktop:
            cand_pid = int(desktop[0]["pid"])
            out = subprocess.run(
                ["/usr/sbin/lsof", "-nP", "-a", "-p", str(cand_pid), "-iTCP", "-sTCP:LISTEN"],
                capture_output=True, text=True, timeout=3)
            for line in out.stdout.splitlines()[1:]:
                parts = line.split()
                if len(parts) >= 9 and "TCP" in parts[7] and "(LISTEN)" in parts:
                    addr = parts[8]
                    if addr.startswith("127.0.0.1:"):
                        port = int(addr.rsplit(":", 1)[1])
                        pid = cand_pid
                        break
    except Exception:
        port, pid = 0, 0
    _desktop_state.update(port=port, pid=pid, checked=now)
    return port, pid


async def _desktop_token(port: int, session: aiohttp.ClientSession) -> str | None:
    """Scrape window.__HERMES_SESSION_TOKEN__ dari index HTML backend desktop (public)."""
    now = time.monotonic()
    tok = _token_cache.get("token")
    if isinstance(tok, str) and now - float(_token_cache["at"]) < _TOKEN_TTL:
        return tok
    try:
        async with session.get(_loopback_url(port) / "", params=None,
                               headers={"Host": f"127.0.0.1:{port}"}) as resp:
            if resp.status == 200:
                m = _TOKEN_RE.search(await resp.text())
                if m:
                    _token_cache.update(token=m.group(1), at=now)
                    return m.group(1)
    except Exception:
        pass
    return None


HOP_HEADERS = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
               "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"}

# ── Auth gate (security fix 28 Sep) ──────────────────────────────────────
# Desktop-bound traffic carries the desktop's own loopback token, so the proxy
# MUST prove the caller is a logged-in mobile user first. Proof = mobile-serve
# (8788) accepts it: HTTP → cookie valid on /api/auth/me; WS → single-use
# ticket accepted by a WS handshake on 8788 (then closed; ticket is consumed).
_AUTH_TTL = 60.0
_cookie_ok: dict[str, float] = {}


async def _cookie_authed(request: web.Request, session: aiohttp.ClientSession) -> bool:
    cookie = request.headers.get("Cookie", "")
    if not cookie:
        return False
    now = time.monotonic()
    if now - _cookie_ok.get(cookie, -1e9) < _AUTH_TTL:
        return True
    try:
        async with session.get(_loopback_url(MOBILE_PORT) / "api" / "auth" / "me",
                               headers={"Cookie": cookie, "Host": f"127.0.0.1:{MOBILE_PORT}"}) as r:
            ok = r.status == 200
    except Exception:
        ok = False
    if ok:
        if len(_cookie_ok) > 256:
            _cookie_ok.clear()
        _cookie_ok[cookie] = now
    return ok


async def _ticket_valid(ticket: str, subproto: str, session: aiohttp.ClientSession) -> bool:
    if not ticket:
        return False
    url = _loopback_url(MOBILE_PORT).with_path("/api/ws").with_query({"ticket": ticket})
    try:
        ws = await session.ws_connect(url, headers={"Host": f"127.0.0.1:{MOBILE_PORT}"},
                                      protocols=[subproto] if subproto else None,
                                      autoping=False, heartbeat=None)
    except Exception:
        return False
    await ws.close()
    return True



_IMG_MIME = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp",
             ".gif": "image/gif", ".bmp": "image/bmp",
             # M9: video dari agent (media3 ExoPlayer di app; suffix list diperluas,
             # auth + path-in-chat + size cap TIDAK diubah)
             ".mp4": "video/mp4", ".m4v": "video/mp4", ".webm": "video/webm",
             ".mov": "video/quicktime", ".mkv": "video/x-matroska", ".avi": "video/x-msvideo",
             # M15.2: dokumen/artifact file — masih path-referenced-in-chat guarded, cuma
             # perluas tipe supaya artifact .md/.pdf/.zip/... bisa di-save dari HP.
             ".pdf": "application/pdf", ".zip": "application/zip", ".txt": "text/plain",
             ".md": "text/markdown", ".json": "application/json", ".csv": "text/csv",
             ".apk": "application/vnd.android.package-archive", ".yaml": "application/yaml",
             ".yml": "application/yaml", ".py": "text/x-python", ".kt": "text/x-kotlin",
             ".sh": "text/x-shellscript", ".html": "text/html", ".ts": "text/x-typescript",
             ".tsx": "text/plain", ".js": "text/javascript", ".java": "text/x-java", ".xml": "text/xml",
             ".log": "text/plain", ".toml": "text/plain", ".sql": "text/plain",
             ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
             ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
             ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
             ".mp3": "audio/mpeg", ".wav": "audio/wav", ".m4a": "audio/mp4"}
_MEDIA_MAX = 20 * 1024 * 1024


def _path_in_transcripts(path: str) -> bool:
    """True if some chat message literally mentions this path (any profile's state.db).
    That is the guard: the phone may only fetch files the agent actually put in a chat."""
    import sqlite3
    hermes = Path.home() / ".hermes"
    dbs = [hermes / "state.db", *sorted((hermes / "profiles").glob("*/state.db"))]
    for db in dbs:
        if not db.exists():
            continue
        try:
            con = sqlite3.connect(f"file:{db}?mode=ro", uri=True, timeout=3)
            try:
                hit = con.execute("SELECT 1 FROM messages WHERE instr(content, ?) > 0 LIMIT 1", (path,)).fetchone()
            finally:
                con.close()
            if hit:
                return True
        except sqlite3.Error:
            continue
    return False


async def handle_mobile_media(request: web.Request) -> web.Response:
    """GET /api/mobile-media?path=<abs> → {"data_url": ...} (same shape as /api/media).
    For images the agent sent via MEDIA:/path that live outside Hermes' media roots.
    Requires a logged-in mobile cookie, an image suffix, a file under $HOME, a size cap,
    and the path appearing in a stored chat message."""
    session: aiohttp.ClientSession = request.app["client"]
    if not await _cookie_authed(request, session):
        return web.json_response({"error": "unauthenticated"}, status=401)
    raw = request.query.get("path", "")
    if not raw.startswith("/"):
        return web.json_response({"detail": "absolute path required"}, status=400)
    try:
        target = Path(raw).resolve(strict=True)
    except (OSError, RuntimeError):
        return web.json_response({"detail": "not found"}, status=404)
    mime = _IMG_MIME.get(target.suffix.lower())
    home = Path.home().resolve()
    if mime is None or not target.is_file() or home not in target.parents:
        return web.json_response({"detail": "not an allowed image"}, status=403)
    if target.stat().st_size > _MEDIA_MAX:
        return web.json_response({"detail": "too large"}, status=413)
    loop = asyncio.get_running_loop()
    if not await loop.run_in_executor(None, _path_in_transcripts, raw) and \
            not await loop.run_in_executor(None, _path_in_transcripts, str(target)):
        return web.json_response({"detail": "path not referenced in any chat"}, status=403)
    import base64
    data = await loop.run_in_executor(None, target.read_bytes)
    return web.json_response({"data_url": f"data:{mime};base64," + base64.b64encode(data).decode()})


async def handle_mobile_last(request: web.Request) -> web.Response:
    """GET /api/mobile-last?profile=<p>&ids=a,b,c → {"last": {id: {role,text,at}}}.
    Last human-visible message per session (session.list preview = first prompt)."""
    session: aiohttp.ClientSession = request.app["client"]
    if not await _cookie_authed(request, session):
        return web.json_response({"error": "unauthenticated"}, status=401)
    db = mobile_insights.state_db(request.query.get("profile"))
    if db is None:
        return web.json_response({"detail": "unknown profile"}, status=404)
    ids = [i for i in request.query.get("ids", "").split(",") if i]
    last = await asyncio.get_running_loop().run_in_executor(None, mobile_insights.last_messages, db, ids)
    return web.json_response({"last": last})


async def handle_mobile_user_tail(request: web.Request) -> web.Response:
    """GET /api/mobile-user-tail?profile=<p>&id=<sid>&after=<epoch> → {"messages": [...]}.
    Prompts typed on another surface (desktop) — the gateway emits no event for them."""
    session: aiohttp.ClientSession = request.app["client"]
    if not await _cookie_authed(request, session):
        return web.json_response({"error": "unauthenticated"}, status=401)
    db = mobile_insights.state_db(request.query.get("profile"))
    if db is None:
        return web.json_response({"messages": []})
    try:
        after = float(request.query.get("after", "0") or 0)
    except ValueError:
        after = 0.0
    msgs = await asyncio.get_running_loop().run_in_executor(
        None, mobile_insights.user_messages_after, db, request.query.get("id", ""), after)
    return web.json_response({"messages": msgs})


async def handle_mobile_usage(request: web.Request) -> web.Response:
    """GET /api/mobile-usage?profile=<p> → token usage by provider (last 30 days)."""
    session: aiohttp.ClientSession = request.app["client"]
    if not await _cookie_authed(request, session):
        return web.json_response({"error": "unauthenticated"}, status=401)
    db = mobile_insights.state_db(request.query.get("profile"))
    if db is None:
        return web.json_response({"available": False})
    data = await asyncio.get_running_loop().run_in_executor(None, mobile_insights.usage_summary, db)
    return web.json_response(data)


async def handle_mobile_limits(request: web.Request) -> web.Response:
    """GET /api/mobile-limits → {plans:[{label, windows:[{label,used_percent,resets_at}]}], ram:{...}}."""
    session: aiohttp.ClientSession = request.app["client"]
    if not await _cookie_authed(request, session):
        return web.json_response({"error": "unauthenticated"}, status=401)
    data = await asyncio.get_running_loop().run_in_executor(None, mobile_limits.limits_snapshot)
    return web.json_response(data)


async def handle_desktop_port(request: web.Request) -> web.Response:
    port, pid = await asyncio.get_running_loop().run_in_executor(None, _desktop_alive)
    if port == 0 and DESKTOP_HINT_PORT:
        port, pid = DESKTOP_HINT_PORT, 0
    surface = "desktop" if port else "mobile-serve"
    return web.json_response({"port": port or MOBILE_PORT, "pid": pid, "surface": surface})


def _pick_target(path: str) -> tuple[str, int]:
    """(target, port): 'desktop' hanya kalau hidup DAN path bukan auth/media."""
    port, pid = _desktop_alive()
    if port and not (path.startswith("/auth/") or path == "/auth"
                     or path.startswith("/api/auth/") or path.startswith("/api/media")):
        return "desktop", port
    return "mobile", MOBILE_PORT


async def proxy_http(request: web.Request) -> web.StreamResponse:
    path = request.path
    target, port = _pick_target(path)
    headers = {k: v for k, v in request.headers.items() if k.lower() not in HOP_HEADERS}
    headers["Host"] = f"127.0.0.1:{port}"
    session: aiohttp.ClientSession = request.app["client"]
    if target == "desktop" and not await _cookie_authed(request, session):
        return web.json_response({"error": "unauthenticated", "reason": "proxy_auth"}, status=401)
    if target == "desktop":
        tok = await _desktop_token(port, session)
        if not tok:
            target, port = "mobile", MOBILE_PORT
        else:
            headers.setdefault("X-Hermes-Session-Token", tok)
            headers.pop("Cookie", None)
    url = _loopback_url(port).with_path(path).with_query(request.query)
    body = await request.read()
    try:
        upstream = await session.request(
            request.method, url, headers=headers, data=body or None,
            allow_redirects=False)
    except Exception as exc:
        log.warning("upstream %s %s gagal: %s", request.method, url, exc)
        return web.json_response({"detail": f"upstream unreachable: {exc}"}, status=502)
    resp = web.StreamResponse(status=upstream.status, reason=upstream.reason)
    for k, v in upstream.headers.items():
        lk = k.lower()
        if lk in HOP_HEADERS or lk == "content-encoding":
            continue
        if lk == "set-cookie":
            # multi set-cookie: StreamResponse.headers[...] assignment is last-wins;
            # use raw addpath so at/rt session cookies survive the proxy hop.
            resp.headers.add(k, v)
        else:
            resp.headers[k] = v
    await resp.prepare(request)
    async for chunk in upstream.content.iter_any():
        await resp.write(chunk)
    await resp.write_eof()
    return resp


async def proxy_ws(request: web.Request) -> web.WebSocketResponse:
    """WS: desktop kalau hidup (token query di-inject, subprotocol desktop),
    else mobile-serve verbatim (tiket dari app). Relay raw dua arah setelah 101."""
    port, _pid = _desktop_alive()
    session: aiohttp.ClientSession = request.app["client"]
    target = "mobile"
    tok = None
    if port:
        tok = await _desktop_token(port, session)
        if tok:
            target = "desktop"
    # credential: desktop → ?token= ; mobile → ?ticket= dari app (udah ada di query)
    if target == "desktop":
        if not await _ticket_valid(request.query.get("ticket", ""),
                                   request.headers.get("Sec-WebSocket-Protocol", "hermes-gateway-v1"), session):
            return web.json_response({"error": "unauthenticated", "reason": "proxy_ticket"}, status=401)
        q = dict(request.query)
        q.pop("ticket", None)
        q["token"] = tok
        url = _loopback_url(port).with_path("/api/ws").with_query(q)
        subproto = request.headers.get("Sec-WebSocket-Protocol", "hermes-gateway-v1")
        hdrs = {"Host": f"127.0.0.1:{port}"}
    else:
        url = _loopback_url(MOBILE_PORT).with_path("/api/ws").with_query(request.query)
        subproto = request.headers.get("Sec-WebSocket-Protocol", "hermes-gateway-v1")
        hdrs = {"Host": f"127.0.0.1:{MOBILE_PORT}"}
    try:
        upstream = await session.ws_connect(
            url, headers=hdrs, protocols=[subproto] if subproto else None,
            autoclose=True, autoping=False, max_msg_size=0, heartbeat=None)
    except Exception as exc:
        log.warning("ws upstream %s gagal: %s", url, exc)
        wsr = web.WebSocketResponse()
        await wsr.prepare(request)
        await wsr.close(code=1014, message=b"upstream ws unavailable")
        return wsr
    downstream = web.WebSocketResponse(protocols=[subproto] if subproto else None, autoping=False,
                                       max_msg_size=0)  # aiohttp default 4 MB would kill file.attach >3 MB
    await downstream.prepare(request)

    async def pump(src, dst, *, close_dst: bool) -> None:
        try:
            async for msg in src:
                if msg.type == aiohttp.WSMsgType.TEXT:
                    await dst.send_str(msg.data)
                elif msg.type == aiohttp.WSMsgType.BINARY:
                    await dst.send_bytes(msg.data)
                elif msg.type == aiohttp.WSMsgType.PING:
                    await dst.ping(msg.data)
                elif msg.type == aiohttp.WSMsgType.PONG:
                    await dst.pong(msg.data)
                elif msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSING, aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.ERROR):
                    break
        except Exception:
            pass
        finally:
            if close_dst:
                await dst.close()

    await asyncio.gather(
        pump(upstream, downstream, close_dst=True),
        pump(downstream, upstream, close_dst=True),
    )
    return downstream


async def on_startup(app: web.Application) -> None:
    app["client"] = aiohttp.ClientSession(
        timeout=aiohttp.ClientTimeout(total=None, connect=5))

async def on_cleanup(app: web.Application) -> None:
    await app["client"].close()


def main() -> None:
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s %(name)s %(message)s")
    app = web.Application()
    app.router.add_get("/api/desktop-port", handle_desktop_port)
    app.router.add_get("/api/mobile-media", handle_mobile_media)
    app.router.add_get("/api/mobile-last", handle_mobile_last)
    app.router.add_get("/api/mobile-usage", handle_mobile_usage)
    app.router.add_get("/api/mobile-user-tail", handle_mobile_user_tail)
    app.router.add_get("/api/mobile-limits", handle_mobile_limits)
    app.router.add_get("/api/ws", proxy_ws)
    app.router.add_route("*", "/{tail:.*}", proxy_http)
    app.on_startup.append(on_startup)
    app.on_cleanup.append(on_cleanup)

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    def _reload(signum, frame):
        # force re-resolve pada sinyal (debug)
        _desktop_state["checked"] = 0.0
        _token_cache["at"] = 0.0

    signal.signal(signal.SIGUSR1, _reload)
    web.run_app(app, host=LISTEN_HOST, port=LISTEN_PORT, loop=loop,
                access_log=None, print=None)


if __name__ == "__main__":
    main()
