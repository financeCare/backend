"""End-to-end check of the async slip pipeline against a running backend, RabbitMQ and mock OCR.

  python slip_e2e.py            runs: happy path, OCR outage -> retries -> dead-letter queue, duplicate delivery
Needs: docker compose up (loadtest), ./run-backend.sh, python mock_ocr.py
"""
import base64
import json
import os
import sys
import time
import urllib.request
import uuid

API = "http://localhost:18080/api"
RABBIT = "http://localhost:55673/api"
HERE = os.path.dirname(os.path.abspath(__file__))
FAIL_FLAG = os.path.join(HERE, "ocr_fail")
PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")


def call(url, data=None, headers=None, method=None, auth=None):
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    if auth:
        req.add_header("Authorization", "Basic " + base64.b64encode(auth.encode()).decode())
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read()
        return r.status, (json.loads(body) if body else None)


def multipart(names):
    boundary = uuid.uuid4().hex
    out = b""
    for n in names:
        out += (f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="{n}"\r\n'
                f'Content-Type: image/png\r\n\r\n').encode() + PNG + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return out, f"multipart/form-data; boundary={boundary}"


def token():
    return call(f"{API}/auth/demo-login", data=b"", method="POST")[1]["accessToken"]


def upload(tok, names):
    body, ctype = multipart(names)
    return call(f"{API}/slips/upload-async", data=body, headers={"Authorization": f"Bearer {tok}", "Content-Type": ctype})


def status(tok, slip_id):
    return call(f"{API}/slips/{slip_id}/status", headers={"Authorization": f"Bearer {tok}"})[1]["status"]


def wait_for(tok, ids, wanted, timeout):
    start = time.time()
    while time.time() - start < timeout:
        states = {i: status(tok, i) for i in ids}
        if all(s == wanted for s in states.values()):
            return time.time() - start, states
        time.sleep(0.5)
    return None, states


def ocr_call_count():
    path = os.path.join(HERE, "mock_ocr.log")
    return open(path, encoding="utf-8").read().count("POST /slip/verify") if os.path.exists(path) else 0


def queue_stats(name):
    q = call(f"{RABBIT}/queues/%2F/{name}", auth="guest:guest")[1]
    return {"ready": q["messages_ready"], "unacked": q["messages_unacknowledged"],
            "published": q.get("message_stats", {}).get("publish", 0)}


def main():
    tok = token()
    ok = True

    print("1) happy path: 3 slips")
    code, accepted = upload(tok, ["a.png", "b.png", "c.png"])
    ids = [a["id"] for a in accepted]
    print("   HTTP", code, accepted)
    took, states = wait_for(tok, ids, "processed", 30)
    print("   ->", "all processed in %.1fs" % took if took is not None else f"NOT processed: {states}")
    ok &= code == 202 and took is not None

    print("2) OCR outage: retries, then the dead-letter queue")
    ocr_calls_before = ocr_call_count()
    open(FAIL_FLAG, "w").close()
    try:
        code, accepted = upload(tok, ["broken.png"])
        bad_id = accepted[0]["id"]
        took, states = wait_for(tok, [bad_id], "failed", 60)
    finally:
        os.remove(FAIL_FLAG)
    attempts = ocr_call_count() - ocr_calls_before
    # "failed" is only ever set by the dead-letter consumer once the retries are exhausted
    print("   ->", f"slip {bad_id} marked failed after %.1fs" % took if took is not None else f"NOT failed: {states}",
          f"| OCR attempts: {attempts}")
    ok &= took is not None and attempts == 3

    print("3) duplicate delivery of an already processed slip is skipped")
    body = json.dumps({
        "properties": {"content_type": "application/json"},
        "routing_key": "slip.ocr",
        "payload": json.dumps({"slipId": ids[0], "userId": "00000000-0000-0000-0000-000000000000",
                               "filename": "a.png", "contentType": "image/png"}),
        "payload_encoding": "string",
    }).encode()
    call(f"{RABBIT}/exchanges/%2F/slip.exchange/publish", data=body, headers={"Content-Type": "application/json"},
         method="POST", auth="guest:guest")
    ocr_calls_before = ocr_call_count()
    time.sleep(6)   # management stats refresh every ~5 s
    q = queue_stats("slip.ocr")
    print("   status still:", status(tok, ids[0]), "| OCR calls for the duplicate:", ocr_call_count() - ocr_calls_before,
          "| slip.ocr queue:", q)
    ok &= status(tok, ids[0]) == "processed" and ocr_call_count() == ocr_calls_before and q["ready"] == 0 and q["unacked"] == 0

    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
