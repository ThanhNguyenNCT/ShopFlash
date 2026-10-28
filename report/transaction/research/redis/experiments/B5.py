#!/usr/bin/env python3
"""B5: khởi phát bốn redis-cli clients cùng lúc, chỉ Lua quyết định quota."""
import concurrent.futures
import datetime
import pathlib
import re
import subprocess
import sys
import threading


def stamp():
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def main():
    if len(sys.argv) != 2 or not re.fullmatch(r"[a-z0-9]{1,12}", sys.argv[1]):
        raise SystemExit("B5.py <run_id>")
    key = f"txresearch:{sys.argv[1]}:quota"
    lua = pathlib.Path("report/transaction/research/redis/lua/B5-quota.lua").read_text()
    barrier = threading.Barrier(4)

    def client(i):
        barrier.wait(timeout=5)
        result = subprocess.run(
            ["docker", "compose", "exec", "-T", "redis", "redis-cli", "--raw", "EVAL", lua, "1", key],
            capture_output=True, text=True, timeout=10,
        )
        return i, result.returncode, result.stdout.strip(), result.stderr.strip()

    print(f"{stamp()} RUN_ID={sys.argv[1]} KEY={key} CLIENTS=4 QUOTA=2", flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(client, i) for i in range(1, 5)]
        results = [f.result(timeout=15) for f in futures]
    for i, code, out, err in sorted(results):
        print(f"{stamp()} CLIENT={i} EXIT={code} OUTPUT={out!r} STDERR={err!r}", flush=True)
    if sum(out in {"0", "1"} for _, _, out, _ in results) != 2:
        raise AssertionError("Không đúng hai client giữ suất")
    if sum("QUOTA_EXHAUSTED" in out for _, _, out, _ in results) != 2:
        raise AssertionError("Không đúng hai client bị từ chối")
    print(f"{stamp()} RESULT=COMPLETED", flush=True)


if __name__ == "__main__":
    main()
