#!/usr/bin/env python3
"""
cereal-proxy: Serializing HTTP proxy

Listens for HTTP connections and forwards them to a downstream port,
processing requests one at a time. The serialization lock is held until
the response body is fully relayed, so SSE streams are serialized end-to-end.

Usage: cereal-proxy.py --listen PORT --downstream PORT [--host HOST] [--timeout SECONDS]
"""

import sys
import threading
import time
import http.server
import http.client
import argparse


# Hop-by-hop headers that should not be forwarded
HOP_BY_HOP = {
    'connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization',
    'te', 'trailers', 'transfer-encoding', 'upgrade',
}


class CerealProxy(http.server.BaseHTTPRequestHandler):
    semaphore = threading.Semaphore(1)
    downstream_host = 'localhost'
    downstream_port = 8080
    timeout_seconds = 30.0

    def log_message(self, format, *args):
        sys.stderr.write(
            f"[{self.log_date_time_string()}] {self.address_string()} - {format % args}\n"
        )

    def handle_any(self):
        start = time.monotonic()

        # Read request body before acquiring the lock so we don't hold it
        # while waiting on slow client I/O.
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length) if content_length > 0 else b''

        # Wait for the serialization lock, respecting the overall timeout.
        remaining = self.__class__.timeout_seconds - (time.monotonic() - start)
        if remaining <= 0 or not self.__class__.semaphore.acquire(timeout=remaining):
            self.send_error(504, "Gateway Timeout")
            return

        response_started = False
        conn = None
        try:
            remaining = self.__class__.timeout_seconds - (time.monotonic() - start)
            if remaining <= 0:
                self.send_error(504, "Gateway Timeout")
                return

            forward_headers = {
                k: v for k, v in self.headers.items()
                if k.lower() not in HOP_BY_HOP and k.lower() != 'host'
            }

            conn = http.client.HTTPConnection(
                self.__class__.downstream_host,
                self.__class__.downstream_port,
                timeout=remaining,
            )

            try:
                conn.request(
                    self.command,
                    self.path,
                    body=body or None,
                    headers=forward_headers,
                )
                resp = conn.getresponse()

                # Relay status and headers.
                self.send_response(resp.status, resp.reason)
                response_started = True

                for key, value in resp.getheaders():
                    if key.lower() not in HOP_BY_HOP:
                        self.send_header(key, value)
                self.end_headers()

                # Stream the body chunk by chunk with immediate flushing.
                # The lock is held throughout so SSE streams are serialized.
                while True:
                    chunk = resp.read(4096)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    self.wfile.flush()

            except (TimeoutError, http.client.HTTPException, OSError) as exc:
                if not response_started:
                    self.send_error(504, f"Gateway Timeout: {exc}")
                # If response already started the connection will just close.

        finally:
            if conn is not None:
                conn.close()
            self.__class__.semaphore.release()

    # Route all standard HTTP methods through handle_any.
    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = do_HEAD = do_OPTIONS = handle_any


def main():
    parser = argparse.ArgumentParser(
        description='cereal-proxy: serializing HTTP proxy'
    )
    parser.add_argument('--listen',     type=int,   required=True,
                        help='Port to listen on')
    parser.add_argument('--downstream', type=int,   required=True,
                        help='Downstream port to proxy to')
    parser.add_argument('--host',       default='localhost',
                        help='Downstream host (default: localhost)')
    parser.add_argument('--timeout',    type=float, default=30.0,
                        help='Total request timeout in seconds (default: 30)')
    args = parser.parse_args()

    CerealProxy.downstream_host  = args.host
    CerealProxy.downstream_port  = args.downstream
    CerealProxy.timeout_seconds  = args.timeout

    server = http.server.ThreadingHTTPServer(('', args.listen), CerealProxy)
    server.daemon_threads = True

    print(
        f"cereal-proxy: listening on :{args.listen} "
        f"-> {args.host}:{args.downstream}  timeout={args.timeout}s",
        flush=True,
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.", flush=True)
        server.server_close()


if __name__ == '__main__':
    main()
