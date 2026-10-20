"""Loopback-only MySQL greeting/TLS fixture. NOT a MySQL database or SELECT test.

Usage: python3 tools/p2_tls_fixture.py --directory /workspace/.ffl/p2-fixture
Creates throwaway test keys outside the repository, never uses application config.
Requires openssl. Ports 19061-19064, accessed by emulator through adb reverse.
The valid TLS case deliberately closes BEFORE authentication/query processing.
"""
import argparse
import json
import os
from pathlib import Path
import socket
import ssl
import struct
import subprocess
import threading


def generate(directory):
    directory.mkdir(parents=True, exist_ok=True)
    for label in ("ca", "other-ca"):
        if not (directory / f"{label}.pem").exists():
            subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                            "-keyout", str(directory / f"{label}.key"), "-out", str(directory / f"{label}.pem"),
                            "-days", "3650", "-subj", f"/CN=FFL P2 fixture {label}",
                            "-addext", "basicConstraints=critical,CA:TRUE"], check=True, capture_output=True)
    for label, dns, ca in (("valid", "localhost", "ca"), ("wrong-host", "wrong.fixture.invalid", "ca"),
                           ("untrusted", "localhost", "other-ca")):
        ext = directory / f"{label}.ext"
        ext.write_text(f"subjectAltName=DNS:{dns}\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n")
        subprocess.run(["openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes", "-keyout",
                        str(directory / f"{label}.key"), "-out", str(directory / f"{label}.csr"),
                        "-subj", f"/CN={dns}"], check=True, capture_output=True)
        subprocess.run(["openssl", "x509", "-req", "-in", str(directory / f"{label}.csr"), "-CA",
                        str(directory / f"{ca}.pem"), "-CAkey", str(directory / f"{ca}.key"), "-CAcreateserial",
                        "-out", str(directory / f"{label}.pem"), "-days", "3650", "-extfile", str(ext)],
                       check=True, capture_output=True)
    for key in directory.glob("*.key"):
        key.chmod(0o600)


def greeting():
    capabilities = 0x0800 | 0x0200 | 0x8000 | 0x80000 | 0x2000 | 0x20000 | 8
    payload = (b"\x0a8.0.0-ffl-tls-fixture\x00" + struct.pack("<I", 1) + b"abcdefgh\x00" +
               struct.pack("<H", capabilities & 0xffff) + b"\x2d" + struct.pack("<H", 2) +
               struct.pack("<H", capabilities >> 16) + b"\x15" + bytes(10) +
               b"ijklmnopqrst\x00mysql_native_password\x00")
    return len(payload).to_bytes(3, "little") + b"\x00" + payload


def serve(directory, label, port):
    context = None
    if label != "stall":
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(directory / f"{label}.pem", directory / f"{label}.key")
    with socket.socket() as listener:
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listener.bind(("127.0.0.1", port))
        listener.listen(5)
        print(json.dumps({"fixture": label, "state": "listening"}), flush=True)
        while True:
            connection, _ = listener.accept()
            with connection:
                connection.settimeout(3)
                try:
                    if label == "stall":
                        # Wait for client timeout/close; do not send a greeting.
                        connection.recv(1)
                        continue
                    connection.sendall(greeting())
                    header = connection.recv(4)
                    if len(header) != 4:
                        continue
                    length = int.from_bytes(header[:3], "little")
                    request = b""
                    while len(request) < length:
                        part = connection.recv(length - len(request))
                        if not part:
                            break
                        request += part
                    if len(request) != 32 or not (int.from_bytes(request[:4], "little") & 0x0800):
                        print(json.dumps({"fixture": label, "state": "invalid_ssl_request"}), flush=True)
                        continue
                    with context.wrap_socket(connection, server_side=True) as secured:
                        # Consume one encrypted record so TLS 1.3 client's validation completes.
                        secured.recv(4096)
                        print(json.dumps({"fixture": label, "state": "tls_accepted_auth_not_processed"}), flush=True)
                except ssl.SSLError:
                    print(json.dumps({"fixture": label, "state": "tls_rejected"}), flush=True)
                except (OSError, ValueError):
                    print(json.dumps({"fixture": label, "state": "closed_or_timeout"}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    arguments = parser.parse_args()
    os.umask(0o077)
    generate(arguments.directory)
    for name, fixture_port in (("valid", 19061), ("wrong-host", 19062), ("untrusted", 19063)):
        threading.Thread(target=serve, args=(arguments.directory, name, fixture_port), daemon=True).start()
    serve(arguments.directory, "stall", 19064)
