#!/usr/bin/env python3
"""Tái lập đúng B2/B4/C2; chạy từ root repo, cần Docker local. Không chạy các case khác."""
import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess

ROOT = Path('report/transaction/research')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('run_id')
    parser.add_argument('evidence', type=Path)
    parser.add_argument('--repeat', type=int, default=5)
    args = parser.parse_args()
    assert re.fullmatch('[a-z0-9]{1,12}', args.run_id) and args.repeat > 0
    run, out = args.run_id, args.evidence
    out.mkdir(parents=True, exist_ok=True)

    def call(name, cmd):
        result = subprocess.run(list(map(str,cmd)), capture_output=True, text=True, timeout=300)
        output = result.stdout + result.stderr
        (out/(name+'.txt')).write_text('$ '+' '.join(map(str,cmd))+'\n'+output)
        assert result.returncode == 0 and not re.search(r'^\s*(ORA-|SP2-|Traceback)',output,re.M), (name,output[-1500:])
        print(name, 'OK', flush=True)
        return output

    def sql(name, file, ids=()):
        return call(name,['bash',ROOT/'oracle/run-sql.sh',run,ROOT/'oracle'/file,*ids])

    setup = sql('setup','setup.sql')
    ids = [re.search(r'^'+field+r'=(\d+)$',setup,re.M)[1] for field in
           ('USER_ID','CATEGORY_ID','CART_ID','PRODUCT_1_ID','PRODUCT_2_ID','PRODUCT_3_ID')]
    p1,p2,p3 = ids[3:]
    prefix = f'txresearch:{run}:'
    keys = [prefix+s for s in ('cart',f'inventory:{p1}',f'inventory:{p2}',f'inventory:{p3}',
                               'quota','order',f'product:{p1}')]
    keyfile=out/'keys.txt';keyfile.write_text('\n'.join(keys)+'\n')
    (out/'manifest.json').write_text(json.dumps({'run_id':run,'ids':ids,'keys':keys,
        'timestamp':datetime.datetime.now().astimezone().isoformat(),'repeat':args.repeat},indent=2)+'\n')
    try:
        call('redis-setup',['bash',ROOT/'redis/setup.sh',run,p1,p2,p3])
        baseline = sql('oracle-before','snapshot.sql')
        baseline_state = baseline[baseline.index('RUN_ID='):].split('PL/SQL')[0].strip()
        redis_baseline=call('redis-before',['bash',ROOT/'redis/snapshot.sh',run,p1,p2,p3])
        for n in range(1,args.repeat+1):
            b2=call(f'{n}-B2',['python3',ROOT/'oracle/experiments/two-session.py','B2',p1,p2])
            assert 'B2 ASSERTIONS=PASS RESET=100 LOCK_REACQUIRED=YES' in b2
            assert b2.count('B_WAIT_CONFIRMED:') == 2
            after = sql(f'{n}-oracle-after','snapshot.sql')
            assert after[after.index('RUN_ID='):].split('PL/SQL')[0].strip() == baseline_state
            b4=call(f'{n}-B4',['python3',ROOT/'redis/experiments/B4.py',run,p1])
            assert 'ASSERT B4.1' in b4 and 'ASSERT B4.2' in b4
            call(f'{n}-B4-after',['bash',ROOT/'redis/snapshot.sh',run,p1,p2,p3])
            call(f'{n}-redis-reset',['bash',ROOT/'redis/reset.sh',run,keyfile,p1,p2,p3])
            c2=call(f'{n}-C2',['bash',ROOT/'redis/experiments/C2.sh',run,p1,p2,p3])
            assert c2.count('snapshot_match=True PASS') == 15
            after_redis=call(f'{n}-redis-after',['bash',ROOT/'redis/snapshot.sh',run,p1,p2,p3])
            assert after_redis == redis_baseline
        (out/'assertions.json').write_text(json.dumps({'status':'PASS','repeat':args.repeat,
            'B2':['plain: reads 100/100, eligible 2, stock 98','locked: reads 100/99, eligible 1, stock 98'],
            'B4':['atomic replies {99,98}, final 98','WATCH nil, stock 99, retry HSET reply 0, stock 98'],
            'C2':'14 rejected snapshots unchanged + 1 valid snapshot per repetition'},indent=2)+'\n')
    finally:
        call('redis-cleanup-preview',['bash',ROOT/'redis/preview.sh',run,keyfile])
        call('redis-cleanup',['bash',ROOT/'redis/cleanup.sh',run,keyfile])
        verify=call('redis-verify',['bash',ROOT/'redis/preview.sh',run,keyfile])
        assert verify.rstrip().endswith('ALL_RUN_KEYS:') and verify.count(' none') == len(keys)
        sql('oracle-cleanup-preview','cleanup-preview.sql',[*ids,'[]'])
        sql('oracle-cleanup','cleanup.sql',[*ids,'[]'])
        verify=sql('oracle-verify','verify-absent.sql',[*ids,'[]'])
        assert 'VERIFY_ABSENT=PASS' in verify


if __name__ == '__main__':
    main()
