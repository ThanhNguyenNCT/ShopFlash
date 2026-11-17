#!/usr/bin/env python3
"""C2: request qua guarded EVAL; mutation trực tiếp chỉ để setup fixture lỗi."""
import copy
import datetime
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile


def log(message):
    print(datetime.datetime.now().astimezone().isoformat(timespec='milliseconds'), message, flush=True)


def main():
    if len(sys.argv) != 5 or not re.fullmatch(r'[a-z0-9]{1,12}', sys.argv[1]):
        raise SystemExit('C2.sh <run_id> <p1> <p2> <p3>')
    run, *ids = sys.argv[1:]
    assert all(re.fullmatch(r'[1-9][0-9]*', x) for x in ids) and len(set(ids)) == 3
    p1, p2, p3 = ids
    prefix = f'txresearch:{run}:'
    cart, order, product, inv = [prefix+s for s in ('cart', 'order', f'product:{p1}', f'inventory:{p1}')]
    keys = [cart, order, product, inv, prefix+f'inventory:{p2}', prefix+f'inventory:{p3}', prefix+'quota']
    lua = Path('report/transaction/research/redis/lua/C2-validate.lua').read_text()

    def redis(*args):
        out = subprocess.run(['docker', 'compose', 'exec', '-T', 'redis', 'redis-cli', '--raw', *map(str,args)],
                             capture_output=True, text=True, check=True, timeout=15).stdout.rstrip('\n')
        log(f"REDIS {args if args[0] != 'EVAL' else ('EVAL C2-validate.lua', *args[2:])} => {out!r}")
        return out

    def snapshot():
        found = redis('KEYS', prefix+'*').splitlines()  # read-only discovery; never delete discovery output
        assert set(found) <= set(keys), found
        result = {}
        for key in sorted(set(keys)):
            kind = redis('TYPE',key)
            ttl = redis('PTTL',key)
            assert ttl in ('-1','-2'), (key, ttl)
            value = None
            if kind == 'hash':
                items = redis('HGETALL', key).splitlines()
                value = dict(sorted(zip(items[::2],items[1::2])))
            elif kind == 'string': value = redis('GET',key)
            else: assert kind == 'none', kind
            result[key] = {'type':kind, 'ttl':ttl, 'value':value}
        log('SNAPSHOT '+json.dumps(result, sort_keys=True))
        return result

    with tempfile.NamedTemporaryFile(mode='w', suffix='.keys') as manifest:
        manifest.write('\n'.join(keys)+'\n'); manifest.flush()
        def reset():
            subprocess.run(['bash','report/transaction/research/redis/reset.sh',run,manifest.name,*ids],check=True)
            assert redis('HSET',product,'product_id',p1,'status','ACTIVE') == '2'

        cases = [
            ('missing_product','PRODUCT_NOT_FOUND',('DEL',product),p1,inv,'1'),
            ('mismatch','PRODUCT_INVENTORY_MISMATCH',None,p1,prefix+f'inventory:{p2}','1'),
            ('out_of_stock','OUT_OF_STOCK',None,p1,inv,'101'),
            ('zero_quantity','INVALID_QUANTITY',None,p1,inv,'0'),
            ('negative_quantity','INVALID_QUANTITY',None,p1,inv,'-1'),
            ('fraction_quantity','INVALID_QUANTITY',None,p1,inv,'1.5'),
            ('negative_stock','INVALID_INVENTORY',('HSET',inv,'on_hand','-5'),p1,inv,'1'),
            ('bad_version','INVALID_INVENTORY',('HSET',inv,'version','oops'),p1,inv,'1'),
            ('reserved_gt_stock','INVALID_INVENTORY',('HSET',inv,'reserved','101'),p1,inv,'1'),
            ('cart_mismatch','CART_MISMATCH',('HSET',cart,p1,'2'),p1,inv,'1'),
            ('wrong_inventory_type','INVALID_KEY_TYPE',('SET',inv,'wrongtype'),p1,inv,'1'),
            ('wrong_cart_type','INVALID_KEY_TYPE',('SET',cart,'wrongtype'),p1,inv,'1'),
            ('wrong_product_type','INVALID_KEY_TYPE',('SET',product,'wrongtype'),p1,inv,'1'),
            ('existing_order','ORDER_ALREADY_EXISTS',('SET',order,'existing'),p1,inv,'1'),
            ('valid','OK',None,p1,inv,'1'),
        ]
        try:
            for name, expected, fixture, pid, inventory, qty in cases:
                log('CASE='+name)
                reset()
                if fixture:
                    log('FIXTURE_INJECTION (không phải request Checkout)')
                    redis(*fixture)
                before = snapshot()
                actual = redis('EVAL',lua,4,cart,order,product,inventory,prefix,pid,qty)
                assert actual == ('ERR '+expected if expected != 'OK' else 'OK'), (expected,actual)
                after = snapshot()
                wanted = copy.deepcopy(before)
                if expected == 'OK':
                    wanted[order] = {'type':'hash','ttl':'-1','value':{'product_id':p1,'quantity':'1','status':'PENDING'}}
                    wanted[inv]['value']['on_hand']='99'
                    wanted[inv]['value']['version']='1'
                    del wanted[cart]['value'][p1]
                assert after == wanted, (name,wanted,after)
                log(f'ASSERT {name} expected={expected} actual={actual} snapshot_match=True PASS')
        finally:
            reset()
            assert redis('DEL',product) == '1'
            log('C2 RESET=baseline; Product extension removed')


if __name__ == '__main__':
    main()
