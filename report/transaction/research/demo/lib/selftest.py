"""Kiểm thử Demo Kit trên DBMS thật: 8 scene × N lượt, so phản hồi/trạng thái với kết quả nghiên cứu,
rồi các tình huống bất thường và cleanup. Chỉ PASS khi giá trị đọc được từ Oracle/Redis khớp."""
import re
import subprocess

import play
from play import DEMO, OracleSession, RedisClient, Out, manifest, rcli, redis_state

SUM0 = '0|0|3|100|100|100|1|1|1'      # ORDERS|ORDER_ITEMS|CART_LINES|P1|P2|P3|P1_CART|P2_CART|P3_CART
AFTER_P1 = '1|1|2|99|100|100|-|1|1'
ORACLE = {
    'oracle-lifecycle': {
        (1, 'A'): [SUM0], (2, 'A'): ['1rowcreated.', '1rowupdated.', '1rowdeleted.'],
        (3, 'A'): [AFTER_P1], (3, 'B'): [SUM0], (4, 'A'): ['Commitcomplete.'],
        (5, 'B'): [AFTER_P1, 'TXRESEARCH_DEMO_A1C'],
        (7, 'A'): ['2|2|1|99|99|100|-|-|1', 'TXRESEARCH_DEMO_A1R'], (8, 'A'): ['Rollbackcomplete.'],
        (9, 'A'): [AFTER_P1, 'TXRESEARCH_DEMO_A1C', '!TXRESEARCH_DEMO_A1R'],
        (9, 'B'): [AFTER_P1, 'TXRESEARCH_DEMO_A1C', '!TXRESEARCH_DEMO_A1R'],
    },
    'oracle-savepoint': {
        (1, 'A'): ['^100|100|1'], (3, 'A'): ['Savepointcreated.'], (4, 'A'): ['^99|98|1'],
        (5, 'A'): ['ORA-02290', '^99|98|1'], (6, 'A'): ['^99|100|1'], (7, 'A'): ['^100|100|1'],
    },
    'oracle-atomicity': {
        (1, 'A'): [SUM0], (2, 'A'): ['1rowcreated.', '2rowscreated.', '2rowsupdated.', '2rowsdeleted.'],
        (3, 'A'): ['ORA-02290'], (4, 'A'): ['1|2|1|99|99|100|-|-|1'], (6, 'A'): [SUM0],
    },
    'oracle-consistency': {
        (1, 'A'): ['CK_CART_ITEMS_QUANTITY|CART_ITEMS|C|quantity>0|ENABLED|VALIDATED|NOTDEFERRABLE|IMMEDIATE'],
        (2, 'A'): ['ORA-02290', SUM0],
    },
    'oracle-isolation': {(3, 'B'): ['^100|'], (4, 'A'): ['1rowupdated.'], (5, 'B'): ['^100|'], (7, 'B'): ['^99|']},
}
# Trạng thái đã COMMIT sau scene, đọc bằng session độc lập.
ORACLE_FINAL = {'oracle-lifecycle': AFTER_P1}

fails = []


def check(ok, what, actual=''):
    Out.p(f"  {'PASS' if ok else 'FAIL'}  {what}" + ('' if ok else f'  → thực tế: {actual}'), 'ok' if ok else 'err')
    if not ok:
        fails.append(what)


def norm(lines):
    return [re.sub(r'\s+', '', x) for x in lines if x.strip()]


def has(lines, item):
    rows = norm(lines)
    if item.startswith('!'):
        return not any(item[1:] in r for r in rows)
    if item.startswith('^'):
        return any(r.startswith(item[1:]) for r in rows)
    return any(item in r for r in rows)


def oracle_summary():
    s = OracleSession('verify')
    try:
        return norm(s.run(play.fill(play.QUERIES['SUMMARY'], manifest())))[-1]
    finally:
        s.close()


def p_state(s, name):
    return {k: s['products'][name].get(k) for k in ('on_hand', 'reserved', 'version', 'cart')}


BASE = {'on_hand': '100', 'reserved': '0', 'version': '0', 'cart': '1'}


def is_baseline(s):
    return all(p_state(s, n) == BASE for n in ('P1', 'P2', 'P3')) and s['order_type'] == 'none'


def check_redis(name, res):
    cmds = [r for r in res if r['kind'] == 'cmd']
    shows = {r['label']: r['state'] for r in res if r['kind'] == 'show'}
    out = lambda word: [r['out'] for r in cmds if r['cmd'].split()[0] == word]
    m = manifest()
    if name == 'redis-lifecycle':
        check(sum(r['out'] == ['QUEUED'] for r in cmds) == 6, 'sáu lệnh trả QUEUED (hai nhánh)')
        check(out('EXEC') == [['1) (integer) 4', '2) (integer) 99', '3) (integer) 1']], 'EXEC → 4, 99, 1', out('EXEC'))
        check(out('DISCARD') == [['OK']], 'DISCARD → OK', out('DISCARD'))
        check(is_baseline(shows['Trước EXEC — client độc lập chưa thấy thay đổi']), 'trước EXEC client độc lập thấy baseline')
        after = shows['AFTER EXEC']
        check(p_state(after, 'P1')['on_hand'] == '99' and p_state(after, 'P1')['cart'] is None
              and after['order'] == {'user_id': m['USER_ID'], 'status': 'PENDING', 'product_id': m['P1'], 'quantity': '1'},
              'sau EXEC: P1 = 99, Cart mất P1, Order tồn tại', after)
        check(is_baseline(shows['AFTER DISCARD']), 'sau DISCARD: stock 100, Order không tồn tại', shows['AFTER DISCARD'])
    elif name == 'redis-atomicity':
        ex = out('EXEC')
        check(len(ex) == 1 and ex[0][:2] == ['1) (integer) 99', '2) (integer) 2'] and ex[0][2].startswith('3) (error) WRONGTYPE')
              and ex[0][3] == '4) (integer) 1', 'EXEC → 99, 2, WRONGTYPE, 1', ex)
        after = shows['AFTER EXEC']
        check(p_state(after, 'P1')['on_hand'] == '99' and p_state(after, 'P1')['cart'] is None
              and after['order'] == {'status': 'PENDING', 'product_id': m['P1']}, 'sau lỗi: P1 = 99, Order tồn tại, Cart mất P1', after)
    elif name == 'redis-lua':
        bad, good = [r for r in res if r['kind'] == 'lua']
        check(bad['out'] == ['(error) ERR OUT_OF_STOCK'] and bad['before'] == bad['after'] and is_baseline(bad['after']),
              'quantity=101 → OUT_OF_STOCK, dữ liệu không đổi', bad['out'])
        a = good['after']
        check(good['out'] == ['"OK"'] and p_state(a, 'P1') == {'on_hand': '99', 'reserved': '0', 'version': '1', 'cart': None}
              and a['order'] == {'product_id': m['P1'], 'quantity': '1', 'status': 'PENDING'}
              and p_state(a, 'P2') == BASE and p_state(a, 'P3') == BASE,
              'quantity=1 → OK, stock 99, version 1, Order tạo, Cart mất P1', (good['out'], a))
    elif name == 'redis-watch':
        ex, hget = out('EXEC'), out('HGET')
        check(ex == [['(nil)'], ['1) (integer) 0']], 'EXEC lần 1 = (nil), retry EXEC = HSET 0', ex)
        check(hget == [['"100"'], ['"99"'], ['"99"'], ['"98"']], 'HGET: 100 → 99 (sau conflict) → 99 → 98', hget)
        check(p_state(shows['AFTER'], 'P1')['on_hand'] == '98', 'stock cuối = 98')
    quota = rcli('--raw', 'HGETALL', m['PREFIX'] + 'quota').split()
    check(quota == ['limit', '2', 'remaining', '2', 'held', '0'], 'quota giữ nguyên', quota)


def demo(*args, ok=True):
    r = subprocess.run([str(DEMO / 'demo.sh'), *args], capture_output=True, text=True)
    if ok and r.returncode != 0:
        raise RuntimeError(f'demo.sh {" ".join(args)} lỗi:\n{r.stdout}{r.stderr}')
    return r


def run_scenes(rounds):
    for n in range(1, rounds + 1):
        for name, sc in play.SCENES.items():
            Out.p(f'\n[lượt {n}] Scene {sc["no"]} {name}', 'h')
            demo('prepare', name)
            if sc['db'] == 'oracle':
                check(oracle_summary() == SUM0, 'prepare → baseline Oracle')
                res = play.play_oracle(name, auto=True)
                got = {(r['n'], r['session']): r['out'] for r in res}
                for key, items in ORACLE[name].items():
                    for item in items:
                        check(has(got[key], item), f'STEP {key[0]} [{key[1]}] có "{item}"', norm(got[key]))
                if name == 'oracle-isolation':
                    sid = lambda k: norm(got[k])[-1].split('|')[0]
                    check(sid((1, 'A')) != sid((1, 'B')), f'hai session khác SID ({sid((1, "A"))} ≠ {sid((1, "B"))})')
                    txn = [norm(got[(k, 'B')])[-1].split('|')[2] for k in (3, 5, 7)]
                    check(len(set(txn)) == 1 and txn[0] != '-', f'B giữ một transaction qua 3 lần đọc (TXN_ID {txn[0]})', txn)
                final = oracle_summary()
                check(final == ORACLE_FINAL.get(name, SUM0), f'session độc lập sau scene: {final}', final)
            else:
                check(is_baseline(redis_state(manifest())), 'prepare → baseline Redis')
                check_redis(name, play.play_redis(name, True, manifest()))


def abnormal():
    m = manifest()
    Out.p('\n[bất thường] Session DBeaver còn transaction mở khi reset', 'h')
    demo('reset', 'oracle')
    s = OracleSession('dangling')
    s.run(f"UPDATE product_inventory SET on_hand_quantity = 55 WHERE product_id = {m['P1']};")
    r = demo('reset', 'oracle', ok=False)
    check(r.returncode != 0 and 'còn transaction mở' in r.stderr and 'sid ' in r.stderr, 'reset từ chối khi còn transaction mở, in SID', r.stderr[-300:])
    r = demo('unlock')
    check('KILL sid' in r.stdout, 'unlock kill đúng session còn transaction', r.stdout[-200:])
    try:
        dead = s.run('SELECT 1 FROM dual;', timeout=15)
        check(any('ORA-' in x for x in dead), 'session bị kill báo lỗi kết nối', dead)
    except (RuntimeError, TimeoutError) as e:
        check(True, f'session bị kill đã đóng ({e})')
    s.proc.p.kill()
    demo('reset', 'oracle')
    check(oracle_summary() == SUM0, 'reset sau unlock → baseline (UPDATE 55 chưa COMMIT đã bị rollback)')

    Out.p('\n[bất thường] Scene chạy dở rồi mất kết nối', 'h')
    s = OracleSession('halfway')
    for stmt in play.parse_steps(play.RT / 'oracle' / '4-atomicity-A.sql')[1]['stmts']:
        s.run(stmt)
    s.proc.p.kill()
    check(oracle_summary() == SUM0, 'kết nối Oracle đứt giữa Checkout → Oracle rollback, baseline')
    demo('reset', 'redis')
    c = RedisClient('halfway')
    c.send(f"WATCH {m['INV1']}")
    c.send('MULTI')
    c.send(f"HINCRBY {m['INV1']} on_hand -5")
    c.proc.p.kill()
    check(is_baseline(redis_state(m)), 'redis-cli đứt giữa MULTI → lệnh xếp hàng không chạy')

    Out.p('\n[bất thường] Fixture bị sửa và đã COMMIT', 'h')
    s = OracleSession('dirty')
    for stmt in (f"UPDATE product_inventory SET on_hand_quantity = 57, reserved_flash_quantity = 3, version = 9 "
                 f"WHERE product_id = {m['P1']};",
                 f"UPDATE cart_items SET quantity = 5 WHERE cart_id = {m['CART_ID']} AND product_id = {m['P3']};",
                 f"DELETE FROM cart_items WHERE cart_id = {m['CART_ID']} AND product_id = {m['P2']};",
                 "INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount, total_amount, "
                 "recipient_name, recipient_phone, shipping_address, idempotency_key, created_at, updated_at) VALUES "
                 f"('{m['TAG']}_XTRA', {m['USER_ID']}, 'NORMAL', 'PENDING', 100, 0, 100, 'TX Research', '0900000000', "
                 f"'Local fixture', '{m['TAG']}:XTRA', SYSTIMESTAMP, SYSTIMESTAMP);", 'COMMIT;'):
        s.run(stmt)
    s.close()
    r = demo('reset', 'oracle')
    check('ORDERS=1' in r.stdout and oracle_summary() == SUM0, 'reset dọn Order lạ của fixture + khôi phục tồn/Cart', r.stdout)
    rcli('SET', m['CART'], 'wrongtype')
    rcli('HSET', m['INV2'], 'on_hand', '7', 'version', 'oops')
    rcli('SET', m['ORDER'], 'existing')
    demo('reset', 'redis')
    check(is_baseline(redis_state(m)), 'reset Redis sửa key sai kiểu / giá trị lạ về baseline')
    stray = m['PREFIX'] + 'stray'
    rcli('SET', stray, '1')
    r = demo('reset', 'redis', ok=False)
    check(r.returncode != 0 and stray in r.stderr, 'reset Redis dừng khi có key lạ ngoài manifest (không xóa wildcard)', r.stderr)
    rcli('DEL', stray)
    demo('reset', 'redis')
    check(is_baseline(redis_state(m)), 'xóa đúng key lạ rồi reset → baseline')


def cleanup():
    Out.p('\n[cleanup] preview → cleanup → verify, rồi dựng lại fixture', 'h')
    r = demo('cleanup')
    check('CLEANUP_COMMITTED' in r.stdout and 'VERIFY_ABSENT=PASS' in r.stdout, 'Oracle cleanup + VERIFY_ABSENT=PASS', r.stdout)
    left = rcli('--scan', '--pattern', 'txresearch:demo:*')
    check('CLEANUP_DONE' in r.stdout and left == '', 'Redis cleanup, không còn key txresearch:demo:*', left)
    demo('reset', 'all')
    check(oracle_summary() == SUM0 and is_baseline(redis_state(manifest())), 'fixture mới ở baseline')


def main(args):
    rounds = int(args[0]) if args else 2
    Out.open('selftest')
    run_scenes(rounds)
    abnormal()
    cleanup()
    Out.p(f'\nKẾT QUẢ: {"PASS" if not fails else f"FAIL ({len(fails)})"}', 'ok' if not fails else 'err')
    for f in fails:
        Out.p('  - ' + f, 'err')
    return 1 if fails else 0
