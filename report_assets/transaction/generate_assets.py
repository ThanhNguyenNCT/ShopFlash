#!/usr/bin/env python3
"""Sinh minh họa báo cáo từ nguồn repo; không ghi vào benchmark/source/experiment.
Chạy từ bất kỳ cwd: python generate_assets.py (cần matplotlib).
"""
from pathlib import Path
import csv
import hashlib
import importlib.util
import json
import re
import sys

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
RESULTS = ROOT / 'report/transaction/results'
SUMMARY = RESULTS / 'summary-20261027'
BLUE, ORANGE, INK, MUTED, GREEN = '#245F91', '#B95F16', '#172B45', '#526176', '#247363'
plt.rcParams.update({'font.family':'DejaVu Sans', 'font.size':13, 'svg.fonttype':'path',
                     'axes.spines.top':False,'axes.spines.right':False,'axes.labelcolor':INK,
                     'text.color':INK,'xtick.color':INK,'ytick.color':MUTED})
FIGURES=[]
CHART_DATA=[]

def read_csv(path):
    with path.open(newline='') as f: return list(csv.DictReader(f))


def verify_data():
    """Tính lại số biểu đồ từ raw bằng summarizer hiện có, chỉ đọc."""
    spec=importlib.util.spec_from_file_location('tx_summary',ROOT/'report/transaction/scripts/summarize.py')
    module=importlib.util.module_from_spec(spec)
    sys.dont_write_bytecode=True
    spec.loader.exec_module(module)
    db_raw=RESULTS/'db-2026-10-27T00-57-08-100088724Z.csv'
    db=module.summarize(*module.load_db(db_raw))
    http=module.summarize(*module.load_http(RESULTS/'http-20261027-stable'))
    for name,rows in [('database',db),('http',http)]:
        summary=read_csv(SUMMARY/f'{name}-summary.csv')
        saved_json=json.loads((SUMMARY/f'{name}-summary.json').read_text())
        key=lambda r:(r['scenario'],r['api_or_flow'])
        original={key(r):r for r in summary}
        json_rows={key(r):r for r in saved_json}
        for row in rows:
            for field in ('p50_ms','p95_ms','throughput_success_per_second','success','rounds','samples'):
                assert float(row[field])==float(original[key(row)][field])==float(json_rows[key(row)][field]), (key(row),field)
    (OUT/'data-verification.json').write_text(json.dumps({'status':'PASS','database_scenarios':len(db),
        'http_metric_groups':len(http),'method':'Recompute from raw CSV/runs JSONL and compressed k6 JSON; compare CSV + JSON summaries',
        'fields':['p50_ms','p95_ms','throughput_success_per_second','success','rounds','samples']},indent=2)+'\n')
    return db,http


def save(fig,stem,title,sources):
    fig.savefig(OUT/(stem+'.svg'),facecolor='white',metadata={'Title':title,'Description':'Nguồn: '+ '; '.join(sources)})
    fig.savefig(OUT/(stem+'.png'),dpi=300,facecolor='white')
    plt.close(fig)
    FIGURES.append({'stem':stem,'title':title,'sources':sources})


def fmt(value,digits=4):
    return f'{value:,.{digits}f}'.replace(',','@').replace('.',',').replace('@','.')


def chart(rows,stem,title,metric,series,ylabel,decimals,subtitle,source):
    workloads=[(1,1),(1,10),(5,1),(5,10)]
    fig,ax=plt.subplots(figsize=(11,6.3))
    fig.subplots_adjust(left=.105,right=.98,top=.76,bottom=.23)
    fig.text(.055,.95,title,size=18,weight='bold',va='top')
    fig.text(.055,.875,subtitle,size=12,color=MUTED,va='top')
    max_value=0
    for j,(label,predicate,color) in enumerate(series):
        selected=[next(r for r in rows if (int(r['cart_size']),int(r['concurrency']))==w and predicate(r)) for w in workloads]
        values=[float(r[metric]) for r in selected]
        CHART_DATA.extend({'figure':stem,'series':label,'cart_size':r['cart_size'],
                           'concurrency':r['concurrency'],'metric':metric,'value':r[metric],
                           'unit':'ms' if metric=='p95_ms' else 'transaction/second',
                           'source':source} for r in selected)
        max_value=max(max_value,*values)
        bars=ax.bar([x+(-.2 if j==0 else .2) for x in range(4)],values,width=.35,label=label,color=color,
                    edgecolor=INK,linewidth=.45,hatch='' if j==0 else '//')
        ax.bar_label(bars,labels=[fmt(v,decimals) for v in values],padding=5,size=11)
    ax.set_ylim(0,max_value*1.21)
    ax.set_xticks(range(4),[f'Cart {c}\nConcurrency {n}' for c,n in workloads])
    ax.set_ylabel(ylabel,labelpad=12)
    ax.set_xlabel('Tổ hợp workload (Cart Size × Concurrency)',labelpad=12)
    ax.yaxis.set_major_formatter(FuncFormatter(lambda x,pos:fmt(x,0)))
    ax.set_axisbelow(True);ax.grid(axis='y',color='#DFE5EC',linewidth=.8)
    ax.legend(loc='lower left',bbox_to_anchor=(0,1.01),ncols=2,frameon=False)
    fig.text(.055,.05,'Nguồn: '+source+'\nĐo local 27/10/2026 • warm-up bị loại • các thanh bắt đầu từ 0.',size=10.5,color=MUTED)
    save(fig,stem,title,['report/transaction/results/summary-20261027/'+source])


def main():
    db,http=verify_data()
    chart(db,'hinh-3-1-database-p95','Hình 3.1 · p95 latency của hai implementation','p95_ms',
          [('Oracle',lambda r:r['dbms_or_http']=='oracle',BLUE),('Redis',lambda r:r['dbms_or_http']=='redis',ORANGE)],
          'p95 latency (ms)',4,'p95 gộp từ 1.000 mẫu thành công / tổ hợp • 5 round × 200 mẫu','database-summary.csv')
    chart(db,'hinh-3-2-database-tps','Hình 3.2 · Thông lượng của hai implementation','throughput_success_per_second',
          [('Oracle',lambda r:r['dbms_or_http']=='oracle',BLUE),('Redis',lambda r:r['dbms_or_http']=='redis',ORANGE)],
          'Thông lượng (transaction/giây, TPS)',2,'TPS = số transaction thành công / tổng wall time của 5 round','database-summary.csv')
    chart(http,'hinh-3-3-http-p95','Hình 3.3 · p95 của Checkout API và toàn luồng HTTP','p95_ms',
          [('checkout.place',lambda r:r['api_or_flow']=='checkout.place',BLUE),('full_flow',lambda r:r['api_or_flow']=='full_flow',ORANGE)],
          'p95 latency (ms)',4,'Mỗi metric: 1.000 mẫu / tổ hợp • full_flow gồm 7 hoặc 11 request • ứng dụng dùng Oracle','http-summary.csv')
    with (OUT/'chart-data.csv').open('w',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(CHART_DATA[0]))
        writer.writeheader();writer.writerows(CHART_DATA)
    manifest={'figures':FIGURES,'generator':'Matplotlib; PNG 300 dpi + SVG vector; DejaVu Sans hỗ trợ tiếng Việt',
              'figure_numbers':[1,2,3]}
    paths={source for f in FIGURES for source in f['sources']}
    paths.update(str(p.relative_to(ROOT)) for p in RESULTS.rglob('*') if p.is_file())
    manifest['source_sha256']={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sorted(paths)}
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print('Generated 3 SVG + 3 PNG; raw/CSV/JSON benchmark verification PASS')


if __name__=='__main__': main()
