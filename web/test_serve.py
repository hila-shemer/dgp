"""Gate for web/serve.py: it serves index.html and nothing else, with the headers
a <meta> CSP cannot carry. Stdlib only - run as `python3 -m unittest test_serve`."""
import http.client
import pathlib
import threading
import unittest

import serve

HERE = pathlib.Path(__file__).resolve().parent


class ServeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = serve.make_server("127.0.0.1", 0)
        cls.port = cls.server.server_address[1]
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def request(self, method, path):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        conn.request(method, path)
        resp = conn.getresponse()
        body = resp.read()
        conn.close()
        return resp, body

    def test_root_is_the_page_verbatim(self):
        resp, body = self.request("GET", "/")
        self.assertEqual(resp.status, 200)
        self.assertEqual(body, (HERE / "index.html").read_bytes())
        self.assertEqual(resp.getheader("Content-Type"), "text/html; charset=utf-8")

    def test_index_html_alias(self):
        resp, body = self.request("GET", "/index.html")
        self.assertEqual(resp.status, 200)
        self.assertEqual(body, (HERE / "index.html").read_bytes())

    def test_headers_a_meta_tag_cannot_set(self):
        resp, _ = self.request("GET", "/")
        self.assertEqual(resp.getheader("Content-Security-Policy"), "frame-ancestors 'none'")
        self.assertEqual(resp.getheader("X-Frame-Options"), "DENY")
        self.assertEqual(resp.getheader("Cache-Control"), "no-store")
        self.assertEqual(resp.getheader("X-Content-Type-Options"), "nosniff")
        self.assertEqual(resp.getheader("Referrer-Policy"), "no-referrer")

    def test_head_has_headers_and_no_body(self):
        resp, body = self.request("HEAD", "/")
        self.assertEqual(resp.status, 200)
        self.assertEqual(body, b"")
        self.assertEqual(resp.getheader("Cache-Control"), "no-store")

    def test_nothing_else_is_served(self):
        for path in ("/serve.py", "/vectors.json", "/../CLAUDE.md", "/%2e%2e/CLAUDE.md",
                     "/index.html/", "/web/index.html"):
            with self.subTest(path=path):
                resp, body = self.request("GET", path)
                self.assertEqual(resp.status, 404)
                self.assertNotIn(b"<script", body)

    def test_query_string_on_root_still_serves(self):
        resp, _ = self.request("GET", "/?utm=x")
        self.assertEqual(resp.status, 200)

    def test_writes_are_refused(self):
        resp, _ = self.request("POST", "/")
        self.assertEqual(resp.status, 405)


if __name__ == "__main__":
    unittest.main()
