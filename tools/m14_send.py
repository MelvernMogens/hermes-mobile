#!/usr/bin/env python3
"""Kirim prompt ke session via proxy (satu shot), tunggu message.complete."""
import asyncio, aiohttp, json, sys

STORED = sys.argv[1] if len(sys.argv) > 1 else "20261002_175753_2fc48e"
TEXT = sys.argv[2] if len(sys.argv) > 2 else "jawab satu kata: siap"

async def t():
    async with aiohttp.ClientSession(cookie_jar=aiohttp.CookieJar(unsafe=True)) as s:
        async with s.post("http://127.0.0.1:8790/auth/password-login", json={"provider":"basic","username":"melvern","password":"ted"}) as r:
            assert r.status == 200
        async with s.post("http://127.0.0.1:8790/api/auth/ws-ticket") as r:
            ticket = (await r.json())["ticket"]
        async with s.ws_connect(f"http://127.0.0.1:8790/api/ws?ticket={ticket}", protocols=["hermes-gateway-v1"]) as ws:
            await ws.send_str(json.dumps({"jsonrpc":"2.0","id":1,"method":"session.resume","params":{"session_id":STORED}}))
            sid = None
            async for msg in ws:
                obj = json.loads(msg.data)
                if obj.get("id") == 1:
                    sid = obj.get("result",{}).get("session_id"); break
            print("runtime sid:", sid, flush=True)
            await ws.send_str(json.dumps({"jsonrpc":"2.0","id":2,"method":"prompt.submit","params":{"session_id":sid,"text":TEXT}}))
            import time; t0 = time.time()
            while time.time()-t0 < 170:
                msg = await ws.receive(timeout=170)
                if msg.type != aiohttp.WSMsgType.TEXT: break
                obj = json.loads(msg.data)
                if obj.get("method") == "event" and obj.get("params",{}).get("type") == "message.complete":
                    print("complete:", obj["params"].get("payload",{}).get("text","")[:60], flush=True)
                    return
asyncio.run(t())
