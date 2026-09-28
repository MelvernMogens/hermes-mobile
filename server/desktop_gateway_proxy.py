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
        if k.lower() in HOP_HEADERS or k.lower() == "content-encoding":
            continue
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
    downstream = web.WebSocketResponse(protocols=[subproto] if subproto else None, autoping=False)
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
