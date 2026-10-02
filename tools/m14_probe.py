#!/usr/bin/env python3
"""M14 probe: kirim prompt ke session via gateway WS (desktop token loopback
via proxy 8790) — mensimulasikan agent menjawab di session yang TIDAK dibuka
di app Android. Notifikasi harus muncul di emulator.

Pakai:
  python3 m14_probe.py prompt "tulis satu kalimat tentang kopi"
  python3 m14_probe.py list          # session.list
  python3 m14_probe.py submit <stored_id> "prompt"
"""
import asyncio, json, sys, re
import aiohttp

PROXY = "http://127.0.0.1:8790"
IDC = 0


def next_id():
    global IDC
    IDC += 1
    return IDC


async def desktop_token(session):
    # proxy inject token sendiri utk /api/*, tapi WS perlu kita minta lewat proxy:
    # proxy /api/ws → desktop (inject token) — cukup konek TANPA tiket.
    return None


async def rpc(ws, method, params=None, timeout=30):
    rid = next_id()
    frame = {"jsonrpc": "2.0", "id": rid, "method": method}
    if params is not None:
        frame["params"] = params
    await ws.send_str(json.dumps(frame))
    async for msg in ws:
        if msg.type == aiohttp.WSMsgType.TEXT:
            obj = json.loads(msg.data)
            if obj.get("id") == rid:
                if "error" in obj:
                    raise RuntimeError(f"{method}: {obj['error']}")
                return obj.get("result", {})
        elif msg.type in (aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.ERROR):
            raise RuntimeError("ws closed")


async def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "list"
    async with aiohttp.ClientSession(cookie_jar=aiohttp.CookieJar(unsafe=True)) as s:
        # login password → cookie → ws-ticket single-use (via 8788; proxy
        # memvalidasi tiket ini sebelum inject token desktop).
        async with s.post(f"{PROXY}/auth/password-login", json={
            "provider": "basic", "username": "melvern", "password": "ted"}) as r:
            assert r.status == 200, f"login {r.status}"
        async with s.post(f"{PROXY}/api/auth/ws-ticket") as r:
            assert r.status == 200, f"ticket {r.status}: {await r.text()}"
            ticket = (await r.json())["ticket"]
        async with s.ws_connect(f"{PROXY}/api/ws?ticket={ticket}",
                                protocols=["hermes-gateway-v1"]) as ws:
            if action == "list":
                res = await rpc(ws, "session.list", {"limit": 15})
                for sess in res.get("sessions", []):
                    print(f"{sess.get('session_key','?'):42} {sess.get('id','?'):40} {(sess.get('title') or '')[:40]}")
                return
            if action == "events":
                # mode monitor: print semua event ring 60s (debug router notif)
                secs = int(sys.argv[2]) if len(sys.argv) > 2 else 60
                rid = next_id()
                await ws.send_str(json.dumps({"jsonrpc": "2.0", "id": rid, "method": "session.list", "params": {"limit": 1}}))
                import time as _t
                t0 = _t.time()
                while _t.time() - t0 < secs:
                    msg = await ws.receive(timeout=secs)
                    if msg.type != aiohttp.WSMsgType.TEXT:
                        break
                    obj = json.loads(msg.data)
                    if obj.get("method") == "event":
                        p = obj.get("params", {})
                        print(f"{_t.time()-t0:6.1f}s {p.get('type','?'):24} sid={p.get('session_id','')[:16]} {(json.dumps(p.get('payload',{}))[:120])}")
                return
            if action == "prompt":
                text = sys.argv[2] if len(sys.argv) > 2 else "Balas satu kalimat pendek."
                res = await rpc(ws, "session.list", {"limit": 1})
                sessions = res.get("sessions", [])
                if not sessions:
                    print("tidak ada session"); return
                target = sessions[0]
                print(f"target: {target.get('session_key')} ({target.get('title')})")
                # resume dulu (ambil runtime id) lalu submit
                r2 = await rpc(ws, "session.resume", {"session_id": target["session_key"]})
                rt = r2.get("id") or target["id"]
                print(f"runtime: {rt}")
                out = await rpc(ws, "prompt.submit", {"session_id": rt, "text": text}, timeout=180)
                print("submit ok:", json.dumps(out)[:200])
                return
            if action == "submit":
                stored = sys.argv[2]
                text = sys.argv[3] if len(sys.argv) > 3 else "Balas satu kalimat."
                r2 = await rpc(ws, "session.resume", {"session_id": stored})
                rt = r2.get("id")
                print(f"runtime: {rt}")
                out = await rpc(ws, "prompt.submit", {"session_id": rt, "text": text}, timeout=180)
                print("submit ok:", json.dumps(out)[:200])
                return
            print(__doc__)

asyncio.run(main())
