#!/usr/bin/env python3
"""B4: điều phối redis-cli A liên tục từ WATCH đến EXEC; B là kết nối khác."""
import datetime
import queue
import re
import subprocess
import sys
import threading


def stamp():
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def main():
    if len(sys.argv) != 3 or not re.fullmatch(r"[a-z0-9]{1,12}", sys.argv[1]) or not sys.argv[2].isdigit():
        raise SystemExit("B4.py <run_id> <product_1_id>")
    key = f"txresearch:{sys.argv[1]}:inventory:{sys.argv[2]}"
    a = subprocess.Popen(
        ["docker", "compose", "exec", "-T", "redis", "redis-cli", "--raw"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1,
    )
    lines = queue.Queue()

    def read():
        for line in a.stdout:
            lines.put(line.rstrip("\n"))
        lines.put(None)

    threading.Thread(target=read, daemon=True).start()

    def cmd(command, expected=None):
        print(f"{stamp()} A> {command}", flush=True)
        a.stdin.write(command + "\n")
        a.stdin.flush()
        response = lines.get(timeout=5)
        if response is None:
            raise RuntimeError("Kết nối A đóng sớm")
        print(f"{stamp()} A< {response!r}", flush=True)
        if expected is not None and response != expected:
            raise AssertionError(f"{command}: expected {expected!r}, actual {response!r}")
        return response

    try:
        print(f"{stamp()} RUN_ID={sys.argv[1]} KEY={key}", flush=True)
        cmd(f"WATCH {key}", "OK")
        cmd(f"HGET {key} on_hand", "100")
        cmd("MULTI", "OK")
        cmd(f"HINCRBY {key} on_hand -1", "QUEUED")
        print(f"{stamp()} B> HINCRBY {key} on_hand -1 (kết nối khác)", flush=True)
        b = subprocess.run(
            ["docker", "compose", "exec", "-T", "redis", "redis-cli", "--raw", "HINCRBY", key, "on_hand", "-1"],
            capture_output=True, text=True, timeout=5, check=True,
        )
        print(f"{stamp()} B< {b.stdout.strip()!r}", flush=True)
        if b.stdout.strip() != "99":
            raise AssertionError("B không đổi tồn 100→99")
        cmd("EXEC", "")  # nil trong --raw: WATCH abort, không chạy HINCRBY của A.
        cmd(f"HGET {key} on_hand", "99")
        print(f"{stamp()} RETRY=1/1", flush=True)
        cmd(f"WATCH {key}", "OK")
        cmd(f"HGET {key} on_hand", "99")
        cmd("MULTI", "OK")
        cmd(f"HINCRBY {key} on_hand -1", "QUEUED")
        cmd("EXEC", "98")
        cmd(f"HGET {key} on_hand", "98")
        print(f"{stamp()} RESULT=COMPLETED", flush=True)
    finally:
        try:
            a.stdin.write("UNWATCH\nQUIT\n")
            a.stdin.flush()
            a.stdin.close()
        except (BrokenPipeError, OSError):
            pass
        try:
            a.wait(timeout=3)
        except subprocess.TimeoutExpired:
            a.terminate()
            a.wait(timeout=3)


if __name__ == "__main__":
    main()
