"""E2E media + clarify: create a session where the agent replies with MEDIA lines
(video, photo, .md, .zip), then (mode=clarify) ask a question with choices."""
import asyncio, json, sys, time, urllib.request, http.cookiejar
import websockets

BASE = "http://127.0.0.1:8790"
cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
D = "/Users/melvernmogens/.hermes/cache/mobile-e2e"


def post(p, d):
    r = urllib.request.Request(BASE + p, data=json.dumps(d).encode(), method="POST")
    r.add_header("Content-Type", "application/json")
    return json.loads(op.open(r, timeout=30).read())


post("/auth/password-login", {"username": "melvern", "password": "ted", "provider": "basic"})


async def call(ws, rid, method, params, timeout=90):
    await ws.send(json.dumps({"jsonrpc": "2.0", "id": rid, "method": method, "params": params}))
    t0 = time.time()
    while time.time() - t0 < timeout:
        x = json.loads(await asyncio.wait_for(ws.recv(), timeout=timeout))
        if x.get("id") == rid:
            return x.get("result") or x.get("error")


async def wait_complete(ws, timeout=240):
    t0 = time.time()
    while time.time() - t0 < timeout:
        x = json.loads(await asyncio.wait_for(ws.recv(), timeout=timeout))
        t = (x.get("params") or {}).get("type")
        if x.get("method") == "clarify" or t == "clarify":
            print("CLARIFY EVENT seen by laptop surface")
        if t == "message.complete":
            return True
    return False


MEDIA_PROMPT = (
    "E2E media test. Reply with EXACTLY these lines and nothing else, each on its own line:\n"
    "Here are your files.\n"
    f"MEDIA:{D}/demo-clip.mp4\n"
    f"MEDIA:{D}/demo-photo.jpg\n"
    f"MEDIA:{D}/release-notes.md\n"
    f"MEDIA:{D}/bundle.zip"
)
CLARIFY_PROMPT = (
    "Use the clarify tool now to ask me ONE question: 'Which color should the new icon be?' "
    "with choices ['Black', 'White', 'Graphite']. After I answer, reply with just: picked <answer>."
)


async def main(mode, arg):
    tk = post("/api/auth/ws-ticket", {})["ticket"]
    async with websockets.connect(f"ws://127.0.0.1:8790/api/ws?ticket={tk}",
                                  subprotocols=["hermes-gateway-v1"], max_size=64 * 1024 * 1024) as ws:
        if mode == "create":
            r = await call(ws, "c", "session.create", {})
            rt = r["session_id"]
            await call(ws, "s1", "prompt.submit", {"session_id": rt, "text": MEDIA_PROMPT})
            print("complete:", await wait_complete(ws))
            await call(ws, "t", "session.title", {"session_id": rt, "title": "Media Test"})
            print(json.dumps({"runtime": rt, "stored": r.get("stored_session_id")}))
        elif mode == "clarify":
            r = await call(ws, "r", "session.resume", {"session_id": arg, "lazy": True})
            res = await call(ws, "s2", "prompt.submit", {"session_id": r["session_id"], "text": CLARIFY_PROMPT})
            print("submit:", res)
            print("complete:", await wait_complete(ws, 400))


asyncio.run(main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else ""))
