#!/usr/bin/env python3
"""Điều phối hai SQL*Plus session cố định cho B1/B2/B3; SQL là bằng chứng chính."""
import argparse
import datetime
import queue
import subprocess
import threading
import time


def stamp():
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


class Harness:
    def __init__(self, p1, p2):
        self.events = queue.Queue()
        self.sessions = {}
        self.seen = set()
        self.counter = 0
        connect = (
            '{ printf "connect shop/%s@//localhost:1521/FREEPDB1\\n" '
            '"$SHOP_PASSWORD"; cat; } | sqlplus -s -L /nolog'
        )
        for label in ("A", "B"):
            process = subprocess.Popen(
                ["docker", "compose", "exec", "-T", "oracle", "bash", "-lc", connect],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
            self.sessions[label] = process
            threading.Thread(target=self._read, args=(label, process), daemon=True).start()
        for label in ("A", "B"):
            tag = self.send(
                label,
                "SET AUTOCOMMIT OFF\nSET PAGESIZE 100\nSET LINESIZE 180\n"
                "VARIABLE p1 NUMBER\nVARIABLE p2 NUMBER\n"
                f"EXEC :p1 := {p1};\nEXEC :p2 := {p2};",
            )
            self.wait(label, tag, 10)

    def _read(self, label, process):
        for line in process.stdout:
            self.events.put((label, line.rstrip("\n")))
        self.events.put((label, None))

    def send(self, label, sql):
        self.counter += 1
        tag = f"__TXRESEARCH_{self.counter}__"
        print(f"{stamp()} {label}> {sql.replace(chr(10), ' | ')}", flush=True)
        process = self.sessions[label]
        process.stdin.write(sql + "\nPROMPT " + tag + "\n")
        process.stdin.flush()
        return tag

    def wait_any(self, expected, timeout):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for pair in expected:
                if pair in self.seen:
                    self.seen.remove(pair)
                    return pair
            try:
                label, line = self.events.get(timeout=min(0.5, deadline - time.monotonic()))
            except queue.Empty:
                continue
            if line is None:
                raise RuntimeError(f"SQL*Plus session {label} đã đóng bất ngờ")
            print(f"{stamp()} {label}< {line}", flush=True)
            if line.startswith("__TXRESEARCH_"):
                pair = (label, line)
                if pair in expected:
                    return pair
                self.seen.add(pair)
        raise TimeoutError(f"Quá hạn chờ {expected}; sẽ giải cứu session")

    def wait(self, label, tag, timeout=15):
        return self.wait_any({(label, tag)}, timeout)

    def step(self, label, sql, timeout=15):
        self.wait(label, self.send(label, sql), timeout)

    def close(self):
        for label, process in self.sessions.items():
            if process.poll() is None:
                try:
                    print(f"{stamp()} {label}> ROLLBACK; EXIT (giải phóng khóa)", flush=True)
                    process.stdin.write("ROLLBACK;\nEXIT\n")
                    process.stdin.flush()
                except (BrokenPipeError, OSError):
                    pass
        for label, process in self.sessions.items():
            try:
                process.stdin.close()
                process.wait(timeout=4)
            except subprocess.TimeoutExpired:
                print(f"{stamp()} {label}> quá hạn, terminate SQL*Plus", flush=True)
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=3)
        while not self.events.empty():
            label, line = self.events.get_nowait()
            if line is not None:
                print(f"{stamp()} {label}< {line}", flush=True)


def b1(h):
    h.step("A", "SET TRANSACTION ISOLATION LEVEL READ COMMITTED;")
    h.step("B", "SET TRANSACTION ISOLATION LEVEL READ COMMITTED;")
    q = "SELECT on_hand_quantity FROM product_inventory WHERE product_id = :p1;"
    h.step("B", q)
    h.step("A", "UPDATE product_inventory SET on_hand_quantity = 99 WHERE product_id = :p1;")
    h.step("B", q)
    h.step("A", "COMMIT;")
    h.step("B", q)
    h.step("B", "ROLLBACK;")
    h.step("A", "UPDATE product_inventory SET on_hand_quantity = 100 WHERE product_id = :p1;\nCOMMIT;")
    h.step("B", q)


def b2(h):
    q = "SELECT on_hand_quantity FROM product_inventory WHERE product_id = :p1;"
    print(f"{stamp()} PHASE=risk stale read/write; hai lượt mua cùng đọc 100", flush=True)
    read_old_stock = (
        "VARIABLE old_stock NUMBER\n"
        "BEGIN\n"
        "  SELECT on_hand_quantity INTO :old_stock FROM product_inventory WHERE product_id = :p1;\n"
        "  IF :old_stock <> 100 THEN RAISE_APPLICATION_ERROR(-20010, 'B2 fixture phai bat dau tu 100'); END IF;\n"
        "END;\n/\nPRINT old_stock"
    )
    h.step("A", read_old_stock)
    h.step("B", read_old_stock)
    update_from_read = (
        "UPDATE product_inventory SET on_hand_quantity = :old_stock - 1 WHERE product_id = :p1;"
    )
    h.step("A", update_from_read)
    b_update = h.send("B", update_from_read)
    try:
        h.wait("B", b_update, 1.5)
    except TimeoutError:
        print(f"{stamp()} B_WAIT_CONFIRMED: UPDATE chua hoan tat khi A giu khoa", flush=True)
    else:
        raise AssertionError("B UPDATE hoan tat truoc A COMMIT; khong co bang chung cho khoa cho")
    h.step("A", "COMMIT;")
    h.wait("B", b_update, 5)
    h.step("B", "COMMIT;")
    h.step("A", q)
    print(f"{stamp()} PHASE=protected FOR UPDATE + conditional UPDATE", flush=True)
    h.step("A", "UPDATE product_inventory SET on_hand_quantity = 1 WHERE product_id = :p1;\nCOMMIT;")
    h.step("A", "SELECT on_hand_quantity FROM product_inventory WHERE product_id = :p1 FOR UPDATE WAIT 3;")
    blocked = h.send("B", "SELECT on_hand_quantity FROM product_inventory WHERE product_id = :p1 FOR UPDATE WAIT 2;")
    h.wait("B", blocked, 5)
    h.step("B", "ROLLBACK;")
    h.step("A", "UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - 1, version = version + 1 "
           "WHERE product_id = :p1 AND on_hand_quantity - reserved_flash_quantity >= 1;\nCOMMIT;")
    h.step("B", "UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - 1, version = version + 1 "
           "WHERE product_id = :p1 AND on_hand_quantity - reserved_flash_quantity >= 1;\nCOMMIT;")
    h.step("B", q)
    h.step("A", "UPDATE product_inventory SET on_hand_quantity = 100, version = 0 WHERE product_id = :p1;\nCOMMIT;")


def b3(h):
    print(f"{stamp()} PHASE=timeout; A khóa P1, B chờ hữu hạn", flush=True)
    h.step("A", "SELECT product_id FROM product_inventory WHERE product_id = :p1 FOR UPDATE WAIT 3;")
    h.step("B", "SELECT product_id FROM product_inventory WHERE product_id = :p1 FOR UPDATE WAIT 2;", 5)
    h.step("B", "ROLLBACK;")
    h.step("A", "ROLLBACK;")
    print(f"{stamp()} PHASE=deadlock; A khóa P1, B khóa P2, A chờ P2, B chờ P1", flush=True)
    h.step("A", "SELECT product_id FROM product_inventory WHERE product_id = :p1 FOR UPDATE WAIT 3;")
    h.step("B", "SELECT product_id FROM product_inventory WHERE product_id = :p2 FOR UPDATE WAIT 3;")
    a_wait = h.send("A", "SELECT product_id FROM product_inventory WHERE product_id = :p2 FOR UPDATE WAIT 10;")
    time.sleep(0.5)
    b_wait = h.send("B", "SELECT product_id FROM product_inventory WHERE product_id = :p1 FOR UPDATE WAIT 10;")
    first = h.wait_any({("A", a_wait), ("B", b_wait)}, 13)
    print(f"{stamp()} FIRST_RETURN={first[0]}; chủ động rollback session này", flush=True)
    h.step(first[0], "ROLLBACK;")
    other = ("B", b_wait) if first[0] == "A" else ("A", a_wait)
    h.wait(other[0], other[1], 5)
    h.step(other[0], "ROLLBACK;")
    h.step("A", "SELECT product_id FROM product_inventory WHERE product_id IN (:p1, :p2) FOR UPDATE WAIT 2;")
    h.step("A", "ROLLBACK;")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", choices=("B1", "B2", "B3"))
    parser.add_argument("p1", type=int)
    parser.add_argument("p2", type=int)
    args = parser.parse_args()
    if args.p1 <= 0 or args.p2 <= 0 or args.p1 == args.p2:
        parser.error("Cần hai Product ID dương khác nhau từ manifest")
    print(f"{stamp()} SCENARIO={args.scenario} P1={args.p1} P2={args.p2}", flush=True)
    h = None
    try:
        h = Harness(args.p1, args.p2)
        {"B1": b1, "B2": b2, "B3": b3}[args.scenario](h)
        print(f"{stamp()} RESULT=COMPLETED; đối chiếu ORA/output và snapshot trước PASS", flush=True)
    finally:
        if h:
            h.close()


if __name__ == "__main__":
    main()
