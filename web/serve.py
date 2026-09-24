#!/usr/bin/env python3
"""Serve web/index.html on loopback, and nothing else.

The page needs a secure context for crypto.subtle, so on flatpot this sits behind
`tailscale serve --https=8453 http://127.0.0.1:8453`, which supplies TLS and keeps it
on the tailnet. This process only adds what the page's <meta> CSP cannot say for
itself: frame-ancestors (ignored in <meta>, so a framing page could otherwise
overlay the seed field), no-store, nosniff and no-referrer.

The file is re-read on every request, so updating the checkout updates the page.
Stdlib only - no install step.

    python3 web/serve.py [--port 8453] [--bind 127.0.0.1]
"""
import argparse
import http.server
import pathlib
import urllib.parse

PAGE = pathlib.Path(__file__).resolve().parent / "index.html"

HEADERS = {
    "Content-Type": "text/html; charset=utf-8",
    "Content-Security-Policy": "frame-ancestors 'none'",
    "X-Frame-Options": "DENY",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
}


class PageHandler(http.server.BaseHTTPRequestHandler):
    server_version = "dgp-web"
    sys_version = ""

    def _serve(self, with_body):
        if urllib.parse.urlsplit(self.path).path not in ("/", "/index.html"):
            self.send_error(404)
            return
        body = PAGE.read_bytes()
        self.send_response(200)
        for name, value in HEADERS.items():
            self.send_header(name, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if with_body:
            self.wfile.write(body)

    def do_GET(self):
        self._serve(with_body=True)

    def do_HEAD(self):
        self._serve(with_body=False)

    def _refuse(self):
        self.send_error(405)

    do_POST = do_PUT = do_DELETE = do_PATCH = do_OPTIONS = _refuse


def make_server(bind, port):
    return http.server.ThreadingHTTPServer((bind, port), PageHandler)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--port", type=int, default=8453)
    ap.add_argument("--bind", default="127.0.0.1")
    args = ap.parse_args()
    server = make_server(args.bind, args.port)
    print(f"dgp web: http://{args.bind}:{args.port}/", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
