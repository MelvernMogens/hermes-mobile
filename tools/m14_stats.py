#!/usr/bin/env python3
"""Cek session.events.since tanpa last_seen utk stored id (probe ring visibility)."""
import asyncio, aiohttp, json

async def t():
    async with aiohttp.ClientSession(cookie_jar=aiohttp.CookieJar(unsafe=True)) as s:
        async with s.post("http://127.0.0.1:8790/auth/password-login", json={"provider":"basic","username":"melvern","password":"ted"}) as r:
            assert r.status == 200
        async with s.post("http://127.0.0.1:8790/api/auth/ws-ticket") as r:
            ticket = (await r.json())["ticket"]
        async with s.ws_connect(f"http://127.0.0.1:8790/api/ws?ticket={ticket}", protocols=["hermes-gateway-v1"]) as ws:
            await ws.send_str(json.dumps({"jsonrpc":"2.0","id":1,"method":"session.events.since","params":{"session_id":"20261002_175753_2fc48e"}}))
            async for msg in ws:
                obj = json.loads(msg.data)
                if obj.get("id") == 1:
                    r = obj.get("result", obj)
                    print("count:", r.get("count"), "latest_seq:", r.get("latest_seq"), "epoch:", r.get("epoch"))
                    for e in r.get("events", [])[-3:]:
                        print(" ev:", e.get("type"), str(e.get("payload", {}))[:80])
                    return
asyncio.run(t())
