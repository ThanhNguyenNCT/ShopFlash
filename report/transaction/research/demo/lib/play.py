#!/usr/bin/env python3
"""Transaction Demo Kit runner: render SQL từ manifest, chạy scene từng bước trên kết nối DBMS
liên tục, in đúng câu lệnh đã gửi và phản hồi thật. Gọi qua demo.sh (đã xác minh container demo)."""
import datetime
import os
import pathlib
import queue
import re
import subprocess
import sys
import threading

DEMO = pathlib.Path(__file__).resolve().parent.parent
RT = DEMO / 'runtime'
ROOT = DEMO.parents[3]
LUA = ROOT / 'report/transaction/research/redis/lua/C2-validate.lua'
DOCKER = os.environ.get('DEMO_REAL_DOCKER', 'docker')

SCENES = {
    'oracle-lifecycle': dict(no='1', db='oracle', title='Oracle Transaction Lifecycle — COMMIT / ROLLBACK',
                             src='oracle/experiments/A1-commit.sql, A1-rollback.sql',
                             files=['1-lifecycle-A.sql', '1-lifecycle-B.sql']),
    'redis-lifecycle': dict(no='2', db='redis', title='Redis Transaction Lifecycle — MULTI/EXEC, DISCARD',
                            src='redis/experiments/A4.sh', script='2-redis-lifecycle.redis'),
    'oracle-savepoint': dict(no='3', db='oracle', title='Oracle SAVEPOINT — partial rollback',
                             src='oracle/experiments/A3.sql', files=['3-savepoint-A.sql']),
    'oracle-atomicity': dict(no='4', db='oracle', title='Oracle Atomicity & Error Handling — Checkout lỗi giữa chừng',
                             src='oracle/experiments/D1.sql', files=['4-atomicity-A.sql']),
    'redis-atomicity': dict(no='5', db='redis', title='Redis Atomicity & Error Handling — lỗi runtime trong EXEC',
                            src='redis/experiments/A5.sh (runtime)', script='5-redis-atomicity.redis'),
    'oracle-consistency': dict(no='6a', db='oracle', title='Consistency (Oracle) — CHECK constraint',
                               src='oracle/experiments/D1.sql, C1.sql', files=['6-consistency-A.sql']),
    'redis-lua': dict(no='6b', db='redis', title='Consistency (Redis) — Lua kiểm tra trước khi ghi',
                      src='redis/lua/C2-validate.lua, redis/experiments/C2.py', script='6-redis-lua.redis'),
    'oracle-isolation': dict(no='7', db='oracle', title='Oracle Isolation — READ COMMITTED, hai session',
                             src='oracle/experiments/two-session.py (B1)',
                             files=['7-isolation-A.sql', '7-isolation-B.sql']),
    'redis-watch': dict(no='8', db='redis', title='Redis Isolation — WATCH conflict và retry',
                        src='redis/experiments/B4.py (B4.2)', script='8-redis-watch.redis'),
}
GROUPS = {'6': ['oracle-consistency', 'redis-lua'], 'consistency': ['oracle-consistency', 'redis-lua']}

# Query quan sát dùng chung, chèn vào template SQL. Chỉ đọc bảng thật, lọc theo ID fixture của run.
QUERIES = {
    'SESSION': """SELECT SYS_CONTEXT('USERENV', 'SID') AS sid, SYS_CONTEXT('USERENV', 'CON_NAME') AS pdb,
       USER AS db_user, DBMS_TRANSACTION.LOCAL_TRANSACTION_ID AS txn_id,
       SYS_CONTEXT('USERENV', 'SERVER_HOST') AS server_host
FROM dual;""",
    'SUMMARY': """SELECT (SELECT COUNT(*) FROM orders WHERE user_id = {{USER_ID}}) AS orders,
       (SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
         WHERE o.user_id = {{USER_ID}}) AS order_items,
       (SELECT COUNT(*) FROM cart_items WHERE cart_id = {{CART_ID}}) AS cart_lines,
       (SELECT on_hand_quantity FROM product_inventory WHERE product_id = {{P1}}) AS p1_stock,
       (SELECT on_hand_quantity FROM product_inventory WHERE product_id = {{P2}}) AS p2_stock,
       (SELECT on_hand_quantity FROM product_inventory WHERE product_id = {{P3}}) AS p3_stock,
       (SELECT quantity FROM cart_items WHERE cart_id = {{CART_ID}} AND product_id = {{P1}}) AS p1_cart,
       (SELECT quantity FROM cart_items WHERE cart_id = {{CART_ID}} AND product_id = {{P2}}) AS p2_cart,
       (SELECT quantity FROM cart_items WHERE cart_id = {{CART_ID}} AND product_id = {{P3}}) AS p3_cart
FROM dual;""",
    'STOCK': """SELECT (SELECT on_hand_quantity FROM product_inventory WHERE product_id = {{P1}}) AS p1_stock,
       (SELECT on_hand_quantity FROM product_inventory WHERE product_id = {{P2}}) AS p2_stock,
       (SELECT quantity FROM cart_items WHERE cart_id = {{CART_ID}} AND product_id = {{P1}}) AS p1_cart
FROM dual;""",
    'PRODUCTS': """SELECT CASE i.product_id WHEN {{P1}} THEN 'P1' WHEN {{P2}} THEN 'P2' ELSE 'P3' END AS product,
       i.on_hand_quantity AS stock, ci.quantity AS cart_qty,
       i.reserved_flash_quantity AS reserved, i.version
FROM product_inventory i
LEFT JOIN cart_items ci ON ci.cart_id = {{CART_ID}} AND ci.product_id = i.product_id
WHERE i.product_id IN ({{P1}}, {{P2}}, {{P3}})
ORDER BY product;""",
    'ORDERS': """SELECT o.order_id, o.order_code, o.status, o.total_amount,
       (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.order_id) AS items
FROM orders o WHERE o.user_id = {{USER_ID}} ORDER BY o.order_id;""",
    'READ_P1': """SELECT on_hand_quantity AS p1_stock, SYS_CONTEXT('USERENV', 'SID') AS sid,
       DBMS_TRANSACTION.LOCAL_TRANSACTION_ID AS txn_id
FROM product_inventory WHERE product_id = {{P1}};""",
}

TTY = sys.stdout.isatty()
STYLE = {'h': '1;36', 'cmd': '1;33', 'resp': '32', 'dim': '2', 'err': '31', 'note': '35', 'ok': '1;32'}


class Out:
    """In ra màn hình và ghi transcript thô (không màu) vào runtime/transcripts/."""
    log = None

    @classmethod
    def open(cls, name):
        (RT / 'transcripts').mkdir(parents=True, exist_ok=True)
        stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
        cls.log = open(RT / 'transcripts' / f'{name}-{stamp}.log', 'w', encoding='utf-8')

    @classmethod
    def p(cls, text='', style=None):
        print(f'\033[{STYLE[style]}m{text}\033[0m' if TTY and style else text, flush=True)
        if cls.log:
            cls.log.write(text + '\n')
            cls.log.flush()


def manifest():
    m = {}
    for line in (RT / 'manifest.env').read_text().splitlines():
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            m[k] = v
    for k in ('USER_ID', 'CATEGORY_ID', 'CART_ID', 'P1', 'P2', 'P3'):
        assert re.fullmatch(r'[1-9][0-9]*', m.get(k, '')), f'manifest: {k} không hợp lệ'
    assert re.fullmatch(r'[a-z0-9]{1,12}', m['RUN_ID']) and m['TAG'] == 'TXRESEARCH_' + m['RUN_ID'].upper()
    pre = f"txresearch:{m['RUN_ID']}:"
    m.update(PREFIX=pre, CART=pre + 'cart', ORDER=pre + 'order', PRODUCT1=pre + f"product:{m['P1']}",
             INV1=pre + f"inventory:{m['P1']}", INV2=pre + f"inventory:{m['P2']}", INV3=pre + f"inventory:{m['P3']}")
    return m


def fill(text, m):
    for name, q in QUERIES.items():
        text = text.replace('{{' + name + '}}', q)
    return re.sub(r'\{\{(\w+)\}\}', lambda x: m[x.group(1)], text)


def render():
    m = manifest()
    out = RT / 'oracle'
    out.mkdir(parents=True, exist_ok=True)
    for tpl in sorted((DEMO / 'oracle').glob('*.sql')):
        (out / tpl.name).write_text(fill(tpl.read_text(), m))
    print(out)
    # Bản Redis để chiếu lên màn hình; {CALC} chỉ tính được lúc chạy nên giữ nguyên.
    rout = RT / 'redis'
    rout.mkdir(exist_ok=True)
    for tpl in sorted((DEMO / 'redis').glob('*.redis')):
        (rout / tpl.name).write_text(re.sub(r'\{(\w+)\}', lambda x: m.get(x[1], x[0]), tpl.read_text()))
    print(rout)


# ---------- Tiến trình giữ kết nối ----------

class Proc:
    def __init__(self, argv):
        self.p = subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, bufsize=1, encoding='utf-8', errors='replace')
        self.q = queue.Queue()
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.p.stdout:
            self.q.put(line.rstrip('\r\n'))
        self.q.put(None)

    def write(self, text):
        self.p.stdin.write(text + '\n')
        self.p.stdin.flush()

    def line(self, timeout):
        try:
            line = self.q.get(timeout=timeout)
        except queue.Empty:
            raise TimeoutError(f'Quá {timeout}s không có phản hồi (session có thể đang chờ khóa)') from None
        if line is None:
            raise RuntimeError('Kết nối đã đóng')
        return line

    def until(self, marker, timeout):
        out = []
        while True:
            line = self.line(timeout)
            if marker in line:
                return out
            out.append(line)

    def close(self, final=None):
        try:
            if final:
                self.write(final)
            self.p.stdin.close()
            self.p.wait(timeout=10)
        except Exception:
            self.p.kill()


class OracleSession:
    """Một SQL*Plus = một Oracle session; autocommit OFF, đóng bằng ROLLBACK (EXITCOMMIT OFF)."""
    INIT = """SET EXITCOMMIT OFF
SET AUTOCOMMIT OFF
SET DEFINE OFF
SET SQLBLANKLINES ON
SET PAGESIZE 200
SET LINESIZE 250
SET TRIMOUT ON
SET TAB OFF
SET COLSEP '|'
SET NULL '-'
COLUMN sid FORMAT A6
COLUMN pdb FORMAT A9
COLUMN db_user FORMAT A7
COLUMN txn_id FORMAT A16
COLUMN server_host FORMAT A14
COLUMN product FORMAT A7
COLUMN order_code FORMAT A24
COLUMN status FORMAT A9
COLUMN constraint_name FORMAT A22
COLUMN table_name FORMAT A10
COLUMN constraint_type FORMAT A15
COLUMN search_condition_vc FORMAT A19
COLUMN validated FORMAT A9
COLUMN deferrable FORMAT A14
COLUMN deferred FORMAT A9"""

    def __init__(self, label):
        self.label, self.n = label, 0
        connect = ('{ printf "whenever oserror exit failure\\nconnect shop/%s@//localhost:1521/FREEPDB1\\n" '
                   '"$SHOP_PASSWORD"; cat; } | sqlplus -s -L /nolog')
        self.proc = Proc([DOCKER, 'exec', '-i', os.environ['DEMO_ORA_CTR'], 'bash', '-c', connect])
        out = self.run(self.INIT, timeout=60)
        if any('ORA-' in x or 'SP2-' in x for x in out):
            raise RuntimeError(f'Không mở được Oracle session {label}: ' + ' '.join(out))

    def run(self, sql, timeout=30):
        self.n += 1
        marker = f'__DEMO_END_{self.label}_{self.n}__'
        self.proc.write(sql + f'\nPROMPT {marker}')
        out = self.proc.until(marker, timeout)
        while out and not out[0].strip():
            out.pop(0)
        while out and not out[-1].strip():
            out.pop()
        return out

    def close(self):
        self.proc.close('ROLLBACK;\nEXIT')


class RedisClient:
    """Một redis-cli = một kết nối Redis. Ngoài MULTI dùng ECHO marker để biết hết phản hồi;
    trong MULTI mỗi lệnh chỉ có một dòng (QUEUED hoặc lỗi), không gửi thêm lệnh nào."""

    def __init__(self, label):
        self.label, self.n, self.in_multi = label, 0, False
        self.proc = Proc([DOCKER, 'exec', '-i', os.environ['DEMO_REDIS_CTR'], 'redis-cli', '--no-raw'])
        self.client_id = self.send('CLIENT ID')[0].replace('(integer) ', '')

    def send(self, cmd, timeout=10):
        word = cmd.split()[0].upper()
        self.proc.write(cmd)
        if word == 'MULTI' or (self.in_multi and word not in ('EXEC', 'DISCARD')):
            out = [self.proc.line(timeout)]
        else:
            self.n += 1
            marker = f'__DEMO_END_{self.label}_{self.n}__'
            self.proc.write(f'ECHO {marker}')
            out = self.proc.until(marker, timeout)
        if word == 'MULTI' and out == ['OK']:
            self.in_multi = True
        elif word in ('EXEC', 'DISCARD'):
            self.in_multi = False
        return out

    def close(self):
        self.proc.close('QUIT')


def rcli(*args):
    return subprocess.run([DOCKER, 'exec', '-i', os.environ['DEMO_REDIS_CTR'], 'redis-cli', *map(str, args)],
                          capture_output=True, text=True, check=True, timeout=15, stdin=subprocess.DEVNULL
                          ).stdout.rstrip('\n')


# ---------- Quan sát trạng thái ----------

def redis_state(m):
    def read(key):
        kind = rcli('--raw', 'TYPE', key)
        if kind != 'hash':
            return kind, None
        items = rcli('--raw', 'HGETALL', key).splitlines()
        return kind, dict(zip(items[::2], items[1::2]))
    cart_type, cart = read(m['CART'])
    state = {'cart_type': cart_type, 'cart': cart or {}, 'products': {}}
    for n in ('1', '2', '3'):
        kind, inv = read(m['INV' + n])
        state['products']['P' + n] = {'type': kind, **(inv or {}), 'cart': (cart or {}).get(m['P' + n])}
    state['order_type'], state['order'] = read(m['ORDER'])
    return state


def show_redis(m, label='STATE'):
    s = redis_state(m)
    Out.p(f'{label}  (client độc lập: TYPE/HGETALL trực tiếp trên Redis)', 'h')
    Out.p(f"  {'PRODUCT':<8}{'STOCK':>6}{'RESERVED':>10}{'VERSION':>9}{'CART_QTY':>10}")
    for name, v in s['products'].items():
        if v['type'] != 'hash':
            Out.p(f"  {name:<8}key {m['INV' + name[1]]} có type {v['type']}", 'err')
            continue
        Out.p(f"  {name:<8}{v.get('on_hand', '-'):>6}{v.get('reserved', '-'):>10}{v.get('version', '-'):>9}"
              f"{v['cart'] or '-':>10}")
    if s['cart_type'] != 'hash':
        Out.p(f"  Cart {m['CART']}: type {s['cart_type']}", 'err')
    if s['order_type'] == 'none':
        Out.p(f"  Order {m['ORDER']}: không tồn tại (exists = 0)")
    elif s['order'] is not None:
        fields = ' '.join(f'{k}={v}' for k, v in s['order'].items())
        Out.p(f"  Order {m['ORDER']}: tồn tại (exists = 1)  {fields}")
    else:
        Out.p(f"  Order {m['ORDER']}: type {s['order_type']}", 'err')
    return s


def show_oracle(m):
    s = OracleSession('show')
    try:
        Out.p('STATE  (session Oracle độc lập: chỉ thấy dữ liệu đã COMMIT)', 'h')
        rows = {}
        for name in ('SUMMARY', 'PRODUCTS', 'ORDERS'):
            rows[name] = s.run(fill(QUERIES[name], m))
            for line in rows[name]:
                Out.p('  ' + line)
            Out.p()
        return rows
    finally:
        s.close()


def lua(m, qty):
    if not re.fullmatch(r'-?[0-9]+(\.[0-9]+)?', qty):
        raise SystemExit('quantity phải là số (ví dụ 101 hoặc 1)')
    src = LUA.read_text()
    lines = src.splitlines()
    first_write = next(i for i, x in enumerate(lines, 1) if x.lstrip().startswith("redis.call('HSET'"))
    first_check = next(i for i, x in enumerate(lines, 1) if x.startswith('if #KEYS'))
    last_check = max(i for i, x in enumerate(lines[:first_write - 1], 1) if not x.lstrip().startswith('--'))
    keys = [m['CART'], m['ORDER'], m['PRODUCT1'], m['INV1']]
    argv = [m['PREFIX'], m['P1'], qty]
    before = show_redis(m, 'BEFORE')
    Out.p('COMMAND', 'h')
    Out.p(f'  EVAL <source {LUA.relative_to(ROOT)}> 4 ' + ' '.join(keys) + ' ' + ' '.join(argv), 'cmd')
    Out.p(f'  KEYS[1..4] = cart, order, product:{m["P1"]}, inventory:{m["P1"]} | '
          f'ARGV[1..3] = prefix, product_id, quantity={qty}')
    Out.p(f'  Validation: dòng {first_check}–{last_check}; lệnh ghi đầu tiên: dòng {first_write}', 'dim')
    out = rcli('--no-raw', 'EVAL', src, 4, *keys, *argv)
    Out.p('RESPONSE', 'h')
    Out.p('  ' + out, 'resp')
    after = show_redis(m, 'AFTER')
    return {'kind': 'lua', 'qty': qty, 'out': [out], 'before': before, 'after': after}


# ---------- Scene ----------

STEP_RE = re.compile(r'^--\s*=+\s*STEP\s+(\d+)\s*\[(A|B)\]\s*(.*?)\s*=*\s*$')


def split_statements(lines):
    stmts, cur, plsql = [], [], False
    for line in lines:
        s = line.strip()
        if not cur and (not s or s.startswith('--')):
            continue
        if not cur and re.match(r'(DECLARE|BEGIN)\b', s, re.I):
            plsql = True
        cur.append(line.rstrip())
        if (plsql and s == '/') or (not plsql and s.endswith(';')):
            stmts.append('\n'.join(cur))
            cur, plsql = [], False
    return stmts


def parse_steps(path):
    steps, cur = [], None
    for line in path.read_text().splitlines():
        hit = STEP_RE.match(line)
        if hit:
            cur = {'n': int(hit[1]), 'session': hit[2], 'title': hit[3], 'notes': [], 'lines': []}
            steps.append(cur)
        elif cur is not None:
            if not cur['lines'] and line.startswith('--'):
                cur['notes'].append(line[2:].strip())
            else:
                cur['lines'].append(line)
    for s in steps:
        s['stmts'] = split_statements(s['lines'])
    return steps


def wait(auto):
    if auto:
        return
    try:
        if input('  ⏎ Enter = chạy | q = dừng ') .strip().lower() == 'q':
            raise KeyboardInterrupt
    except EOFError:
        pass


def play_oracle(name, auto):
    sc = SCENES[name]
    steps = []
    for f in sc['files']:
        steps += parse_steps(RT / 'oracle' / f)
    steps.sort(key=lambda s: (s['n'], s['session']))
    m = manifest()
    observe = {fill(q, m): n for n, q in QUERIES.items()}
    sessions, results = {}, []
    try:
        for label in sorted({s['session'] for s in steps}):
            sessions[label] = OracleSession(label)
        sid = {k: next(x.strip() for x in v.run("SELECT SYS_CONTEXT('USERENV','SID') AS sid FROM dual;")
                       if x.strip().isdigit()) for k, v in sessions.items()}
        Out.p('Session: ' + ', '.join(f'{k} = SID {v}' for k, v in sid.items()) + ' (SQL*Plus, autocommit OFF)', 'dim')
        for s in steps:
            Out.p()
            Out.p(f"── STEP {s['n']} · Session {s['session']} — {s['title']}", 'h')
            for note in s['notes']:
                Out.p('   ' + note, 'note')
            for stmt in s['stmts']:
                label = observe.get(stmt)
                if label:  # SELECT quan sát dài: CLI chỉ in nhãn, SQL đầy đủ nằm trong file DBeaver
                    Out.p(f'   SELECT … ({label}: truy vấn quan sát, đọc trực tiếp bảng)', 'cmd')
                else:
                    Out.p('\n'.join('   ' + x for x in stmt.splitlines()), 'cmd')
            wait(auto)
            out = []
            for stmt in s['stmts']:
                got = sessions[s['session']].run(stmt)
                out += got + ['']
                for line in got:
                    Out.p('   ' + line, 'err' if 'ORA-' in line else 'resp')
            results.append({'n': s['n'], 'session': s['session'], 'out': out})
    finally:
        for s in sessions.values():
            s.close()
    return results


def play_redis(name, auto, m):
    sc = SCENES[name]
    clients, results, calc, last = {}, [], {}, {}

    def subst(cmd):
        return re.sub(r'\{(\w+)\}', lambda x: calc[x[1]] if x[1] in calc else m[x[1]], cmd)

    try:
        for raw in (DEMO / 'redis' / sc['script']).read_text().splitlines():
            line = raw.strip()
            if not line or line.startswith('#'):
                continue
            if line.startswith('@'):
                word, _, arg = line[1:].partition(' ')
                if word == 'note':
                    Out.p()
                    Out.p(f'── {arg}', 'h')
                elif word == 'conclude':
                    Out.p(f'KẾT LUẬN  {arg}', 'ok')
                elif word == 'show':
                    Out.p()
                    results.append({'kind': 'show', 'label': arg, 'state': show_redis(m, arg)})
                elif word == 'reset':
                    Out.p()
                    Out.p('── Reset fixture Redis về baseline (./demo.sh reset redis)', 'h')
                    wait(auto)
                    subprocess.run([str(DEMO / 'demo.sh'), 'reset', 'redis'], check=True,
                                   stdout=subprocess.DEVNULL)
                elif word == 'calc':
                    who, delta = re.fullmatch(r'([AB])-(\d+)', arg).groups()
                    value = int(last[who].strip('"').replace('(integer) ', ''))
                    calc['CALC'] = str(value - int(delta))
                    Out.p(f'   Client {who} tính: {value} - {delta} = {calc["CALC"]}  (logic ứng dụng, không gửi Redis)',
                          'note')
                elif word == 'lua':
                    Out.p()
                    wait(auto)
                    results.append(lua(m, arg))
                continue
            who, cmd = re.fullmatch(r'([AB])>\s*(.+)', line).groups()
            if who not in clients:
                clients[who] = RedisClient(who)
                Out.p(f'   [client {who}: một redis-cli giữ kết nối liên tục, CLIENT ID = {clients[who].client_id}]', 'dim')
            cmd = subst(cmd)
            Out.p(f'{who}> {cmd}', 'cmd')
            wait(auto)
            out = clients[who].send(cmd)
            last[who] = out[-1] if out else ''
            for x in out:
                Out.p('   ' + x, 'err' if '(error)' in x else 'resp')
            results.append({'kind': 'cmd', 'client': who, 'cmd': cmd, 'out': out})
    finally:
        for c in clients.values():
            c.close()
    return results


def resolve(name):
    if name in GROUPS:
        return GROUPS[name]
    for key, sc in SCENES.items():
        if name in (key, sc['no']):
            return [key]
    raise SystemExit(f'Scene không tồn tại: {name}. Xem ./demo.sh help')


def run_scene(name, auto=False):
    m = manifest()
    sc = SCENES[name]
    Out.open(name)
    Out.p(f"═══ Scene {sc['no']} — {sc['title']}", 'h')
    Out.p(f"Nguồn nghiên cứu: report/transaction/research/{sc['src']}", 'dim')
    if sc['db'] == 'oracle':
        return play_oracle(name, auto)
    return play_redis(name, auto, m)


def info(name):
    for key in resolve(name):
        sc = SCENES[key]
        print(f"\n\033[1;36m═══ Scene {sc['no']} — {sc['title']}\033[0m")
        print(f"Nguồn: report/transaction/research/{sc['src']}")
        if sc['db'] == 'oracle':
            print('Mở trong DBeaver (Manual commit, mỗi file một SQL Editor, chạy từng câu bằng Ctrl+Enter):')
            for f in sc['files']:
                print(f'  {RT / "oracle" / f}')
            order = []
            for f in sc['files']:
                order += parse_steps(RT / 'oracle' / f)
            for s in sorted(order, key=lambda s: (s['n'], s['session'])):
                print(f"  STEP {s['n']:<2} [{s['session']}] {s['title']}")
            print(f'Dự phòng không dùng DBeaver: ./demo.sh play {key}')
        else:
            print(f'Chạy: ./demo.sh play {key}   (Enter = lệnh tiếp theo; lệnh và phản hồi in nguyên văn)')
            if key == 'redis-lua':
                print('  hoặc từng lần: ./demo.sh lua 101 ; ./demo.sh lua 1 ; ./demo.sh show redis')
        print(f"Quan sát lại: ./demo.sh show {sc['db']}   Reset: ./demo.sh prepare {key}")


def main():
    cmd, args = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ('list', [])
    if cmd == 'list':
        for key, sc in SCENES.items():
            print(f"  {sc['no']:<3} {key:<19} {sc['title']}")
        print('  (6 = consistency: oracle-consistency + redis-lua)')
    elif cmd == 'db':
        dbs = {SCENES[k]['db'] for k in resolve(args[0])}
        print('both' if len(dbs) > 1 else dbs.pop())
    elif cmd == 'render':
        render()
    elif cmd == 'info':
        info(args[0])
    elif cmd == 'show':
        m = manifest()
        if args and args[0] == 'redis':
            show_redis(m)
        elif args and args[0] == 'oracle':
            show_oracle(m)
        else:
            raise SystemExit('show oracle|redis')
    elif cmd == 'lua':
        if not args:
            raise SystemExit('lua <quantity>, ví dụ: ./demo.sh lua 101')
        Out.open('lua')
        lua(manifest(), args[0])
    elif cmd == 'play':
        auto = '--auto' in args
        names = [a for a in args if not a.startswith('--')]
        if not names:
            raise SystemExit('play <scene> [--auto]')
        try:
            for key in resolve(names[0]):
                run_scene(key, auto)
        except KeyboardInterrupt:
            print('\nĐã dừng. Kết nối đóng: Oracle rollback transaction dở, Redis hủy MULTI/WATCH.')
    elif cmd == 'selftest':
        sys.path.insert(0, str(DEMO / 'lib'))
        import selftest
        sys.exit(selftest.main(args))
    else:
        raise SystemExit(f'Lệnh không hỗ trợ: {cmd}')


if __name__ == '__main__':
    main()
