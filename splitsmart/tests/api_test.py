"""Real HTTP sessions, CSRF, database restart persistence, and legacy import. No user data touched."""

import http.cookiejar
import json
import os
import pathlib
import socket
import struct
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]


class Client:
    def __init__(self, base):
        self.base = base
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf = None

    def call(self, path, data=None, token=True):
        headers = {}
        body = None
        if data is not None:
            body = urllib.parse.urlencode(data, doseq=True).encode()
            if token:
                if not self.csrf:
                    self.refresh()
                headers[self.csrf["headerName"]] = self.csrf["token"]
        request = urllib.request.Request(self.base + path, data=body, headers=headers)
        try:
            with self.opener.open(request, timeout=15) as response:
                code, payload = response.status, response.read()
        except urllib.error.HTTPError as error:
            code, payload = error.code, error.read()
        try:
            return code, json.loads(payload)
        except ValueError:
            return code, payload.decode()

    def refresh(self):
        status, self.csrf = self.call("/api/auth/csrf")
        assert status == 200

    def expect(self, path, status, data=None, token=True):
        actual, result = self.call(path, data, token)
        assert actual == status, (path, actual, result)
        return result

    def register(self, user):
        self.expect(
            "/api/auth/register",
            201,
            {"username": user, "displayName": user.title(), "password": "testing-password-123"},
        )

    def login(self, user):
        self.expect("/api/auth/login", 200, {"username": user, "password": "testing-password-123"})
        self.refresh()


with tempfile.TemporaryDirectory() as folder:
    directory = pathlib.Path(folder)
    data = directory / "data"
    data.mkdir()

    def utf(value):
        encoded = value.encode()
        return struct.pack(">H", len(encoded)) + encoded

    legacy = (
        struct.pack(">i", 2)
        + utf("You")
        + utf("Guest")
        + struct.pack(">i", 1)
        + utf("legacy")
        + utf("Legacy dinner")
        + struct.pack(">q", 101)
        + utf("You")
        + struct.pack(">i", 2)
        + utf("You")
        + utf("Guest")
    )
    (data / "state.bin").write_bytes(legacy)
    groupId = "00000000-0000-0000-0000-000000000001"
    other = data / "groups" / groupId
    other.mkdir(parents=True)
    (other / "name.txt").write_text("Imported repayments")
    v2 = (
        struct.pack(">ii", -2, 2)
        + utf("You")
        + utf("Guest")
        + struct.pack(">i", 1)
        + utf("v2")
        + utf("Saved exact split")
        + struct.pack(">q", 100)
        + utf("You")
        + struct.pack(">i", 2)
        + utf("You")
        + utf("Guest")
        + utf("exact")
        + struct.pack(">qq", 50, 50)
    )
    (other / "state.bin").write_bytes(v2)
    (other / "payments.json").write_text(
        json.dumps(
            [
                {
                    "orderId": "demo_fixture",
                    "from": "Guest",
                    "to": "You",
                    "cents": 50,
                    "status": "demo_success",
                    "paymentId": "sim_fixture",
                    "createdAt": "2026-11-14T00:00:00Z",
                    "verifiedAt": "2026-11-14T00:00:01Z",
                }
            ]
        )
    )
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    base = f"http://127.0.0.1:{port}"
    env = {
        **os.environ,
        "PORT": str(port),
        "SPLITSMART_DATA_DIRECTORY": str(data),
        "DATABASE_URL": f"jdbc:h2:file:{data}/test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "DATABASE_USERNAME": "sa",
        "DATABASE_PASSWORD": "",
        "RAZORPAY_KEY_ID": "",
        "RAZORPAY_KEY_SECRET": "",
    }

    def start():
        return subprocess.Popen(
            ["java", "-jar", str(ROOT / "target/splitsmart-1.0.0.jar")],
            cwd=folder,
            env=env,
            stdout=log,
            stderr=log,
        )

    def ready():
        for _ in range(200):
            if process.poll() is not None:
                raise AssertionError((directory / "server.log").read_text())
            try:
                with urllib.request.urlopen(base + "/api/auth/csrf", timeout=1) as response:
                    if response.status == 200:
                        return
            except OSError:
                pass
            time.sleep(0.05)
        raise AssertionError("Server did not start")

    with (directory / "server.log").open("w") as log:
        process = start()
        try:
            ready()
            alice, bob, eve = [Client(base) for _ in range(3)]
            alice.expect("/api/groups", 401)
            alice.expect(
                "/api/auth/register",
                403,
                {"username": "bad", "password": "testing-password-123"},
                token=False,
            )
            for client, user in [(alice, "alice"), (bob, "bob"), (eve, "eve")]:
                client.register(user)
                client.login(user)
            imported = alice.expect("/api/state?group=default", 200)
            assert imported["expenses"][0]["shares"] == [51, 50]
            assert imported["viewerMember"] == "You"
            bob.expect("/api/state?group=default", 403)
            saved = alice.expect("/api/state?group=" + groupId, 200)
            assert (
                all(b["cents"] == 0 for b in saved["balances"])
                and saved["payments"][0]["status"] == "demo_success"
            )
            assert (other / "state.bin").read_bytes() == v2
            group = alice.expect("/api/groups", 201, {"name": "Shared trip"})["selected"]
            scope = "?group=" + group
            assert "groupPage" in alice.expect("/groups/" + group, 200)
            assert "groupCards" in alice.expect("/", 200)
            found = alice.expect("/api/users" + scope + "&q=BoB", 200)
            assert found == [{"username": "bob", "displayName": "Bob"}]
            eve.expect("/api/users" + scope, 403)
            alice.expect("/api/members" + scope, 200, {"username": "bob"})
            assert bob.expect("/api/state" + scope, 200)["members"] == ["alice", "bob"]
            assert alice.expect("/api/users" + scope + "&q=bob", 200) == []
            alice.expect("/api/members" + scope, 409, {"username": "bob"})
            bob.expect("/api/members" + scope, 403, {"username": "eve"})
            alice.expect(
                "/api/expenses" + scope,
                200,
                {"title": "Hotel", "amount": "100", "payer": "alice", "member": ["alice", "bob"]},
            )
            alice.expect("/api/payments/demo-orders" + scope, 403, {"from": "bob", "to": "alice"})
            order = bob.expect(
                "/api/payments/demo-orders" + scope,
                201,
                {"from": "bob", "to": "alice", "amount": "0.01"},
            )
            assert order["amount"] == 5000
            alice.expect(
                "/api/payments/demo-complete" + scope,
                403,
                {"orderId": order["orderId"], "outcome": "success"},
            )
            final = bob.expect(
                "/api/payments/demo-complete" + scope,
                200,
                {"orderId": order["orderId"], "outcome": "success"},
            )
            assert all(b["cents"] == 0 for b in final["balances"])
            eve.expect("/api/state" + scope, 403)
            process.terminate()
            process.wait(timeout=15)
            process = start()
            ready()
            alice.expect("/api/groups", 401)
            alice.csrf = None
            bob.csrf = None
            alice.login("alice")
            bob.login("bob")
            persisted = bob.expect("/api/state" + scope, 200)
            assert (
                persisted["balances"] == final["balances"]
                and persisted["payments"] == final["payments"]
            )
            assert alice.expect("/api/state?group=default", 200)["expenses"][0]["shares"] == [
                51,
                50,
            ]
            assert (data / "state.bin").read_bytes() == legacy
            alice.expect("/api/auth/logout", 200, {})
            alice.expect("/api/groups", 401)
            print(
                "PASS: real sessions, CSRF, user search, direct membership, access isolation, payer enforcement, legacy import, account/group/payment persistence across restart, logout"
            )
        finally:
            process.terminate()
            process.wait(timeout=15)
