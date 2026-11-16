#!/usr/bin/env python3
"""B4: điều phối redis-cli A liên tục từ WATCH đến EXEC; B là kết nối khác."""
import concurrent.futures
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
    def independent(*args):
        result = subprocess.run(["docker", "compose", "exec", "-T", "redis", "redis-cli", "--raw", *args],
                                capture_output=True, text=True, timeout=10, check=True).stdout.strip()
        print(f"{stamp()} independent> {args} < {result!r}", flush=True)
        return result

    def snapshot(phase):
        print(f"{stamp()} SNAPSHOT {phase}", flush=True)
        independent("HGETALL", key)

    assert independent("HGET", key, "on_hand") == "100"
    snapshot("B4.1_before")
    gate = threading.Barrier(2)
    def atomic(label):
        gate.wait(timeout=5)
        return independent("HINCRBY", key, "on_hand", "-1")
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as workers:
        futures = [workers.submit(atomic, label) for label in ("A", "B")]
        results = [future.result(timeout=15) for future in futures]
    assert sorted(results) == ["98", "99"], results
    assert independent("HGET", key, "on_hand") == "98"
    snapshot("B4.1_after")
    print(f"{stamp()} ASSERT B4.1 expected=[98,99]/98 actual={results}/98 PASS", flush=True)
    independent("HSET", key, "on_hand", "100")
    snapshot("B4.2_before")
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
        observed = int(cmd(f"HGET {key} on_hand", "100"))
        calculated = observed - 1
        print(f"{stamp()} A CALCULATED={calculated}", flush=True)
        print(f"{stamp()} B> HINCRBY {key} on_hand -1 (kết nối khác)", flush=True)
        b = subprocess.run(
            ["docker", "compose", "exec", "-T", "redis", "redis-cli", "--raw", "HINCRBY", key, "on_hand", "-1"],
            capture_output=True, text=True, timeout=5, check=True,
        )
        print(f"{stamp()} B< {b.stdout.strip()!r}", flush=True)
        if b.stdout.strip() != "99":
            raise AssertionError("B không đổi tồn 100→99")
        cmd("MULTI", "OK")
        cmd(f"HSET {key} on_hand {calculated}", "QUEUED")
        cmd("EXEC", "")  # nil trong --raw: WATCH abort, không chạy HSET của A.
        cmd(f"HGET {key} on_hand", "99")
        snapshot("B4.2_conflict")
        print(f"{stamp()} RETRY=1/1", flush=True)
        cmd(f"WATCH {key}", "OK")
        observed = int(cmd(f"HGET {key} on_hand", "99"))
        calculated = observed - 1
        print(f"{stamp()} A RETRY_CALCULATED={calculated}", flush=True)
        cmd("MULTI", "OK")
        cmd(f"HSET {key} on_hand {calculated}", "QUEUED")
        cmd("EXEC", "0")
        cmd(f"HGET {key} on_hand", "98")
        snapshot("B4.2_after")
        print(f"{stamp()} ASSERT B4.2 conflict=nil/99 retry=0/98 PASS", flush=True)
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
