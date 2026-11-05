"""Tiny stand-in for the EasyOCR service, for testing the async slip pipeline without the real OCR image.

  python mock_ocr.py [port]            answers POST /slip/verify with a fixed successful OCR result
  create the file `loadtest/ocr_fail`  makes it answer 500 instead (simulates an OCR outage; delete the file to recover)
"""
import json
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

FAIL_FLAG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ocr_fail")
DELAY_SECONDS = float(os.environ.get("MOCK_OCR_DELAY", "0.2"))


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        time.sleep(DELAY_SECONDS)
        if os.path.exists(FAIL_FLAG):
            self.send_response(500)
            self.end_headers()
            self.wfile.write(b'{"error":"simulated OCR outage"}')
            return
        names = re.findall(rb'filename="([^"]*)"', body) or [b"slip.jpg"]
        result = [
            {
                "filename": n.decode(errors="replace"),
                "status": "success",
                "method": "qr",
                "data": {"bank": "KBank", "receiver": "Mock Shop", "amount": "123.45", "date": "28 ก.พ. 2569 16:50"},
            }
            for n in names
        ]
        payload = json.dumps(result).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock-ocr] " + fmt % args + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 17860
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
