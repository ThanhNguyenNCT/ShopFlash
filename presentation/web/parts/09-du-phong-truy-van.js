/* Cảnh dự phòng của phần 4 (đặt sau Cảm ơn): phần demo Query Lab đã cho thấy cùng nội dung; mở khi được hỏi. */
/* ---------- 3 · Oracle: CBO cần thống kê đúng ---------- */
Deck.add({part:0,title:'Oracle chọn kế hoạch dựa trên thống kê',
demo:{label:'Query Lab · Optimizer',path:'/admin/reports/lab'},
html:`
<style>
.p4t{font-size:1.3rem}.p4t th{font-size:.85rem}.p4t td{padding:.9rem .9rem;vertical-align:middle}
.p4t td:first-child{white-space:nowrap}
.p4t td.n,.p4t th.n{text-align:right;font-variant-numeric:tabular-nums;font-family:var(--mono)}
.p4t td.best{color:var(--ora);font-weight:800}.p4t td.bad{color:var(--bad);font-weight:800}.p4t td.r{color:var(--red);font-weight:800}

.p4-cbo{display:grid;grid-template-columns:1.15fr 1fr;gap:1.2rem;flex:1;min-height:0}
.p4-cbo .card{display:flex;flex-direction:column;gap:1rem}
.p4-cbo .p4t{font-size:1.5rem}.p4-cbo .p4t td{padding:1.5rem .7rem}.p4-cbo .p4t th{font-size:.9rem}
.p4-cbo .p4t td{white-space:nowrap}
.p4-cbo td .bar{display:block;height:.4rem;border-radius:99rem;background:currentColor;opacity:.4;width:0;margin:.45rem 0 0 auto;transition:width 1.1s var(--ease-out)}
.p4-cbo .take{margin-top:auto}.p4-cbo .take p{font-size:1.2rem}
</style>
${Deck.head(4,'Oracle chọn kế hoạch dựa trên thống kê','<span class="chip o" data-morph="ora">ORACLE · CBO</span>')}
<div class="p4-cbo body">
  <div class="card" data-a style="--i:1">
    <div class="label" style="margin:0">Ép thuật toán join bằng hint · thời gian (ms)</div>
    <table class="tbl p4t"><tr><th>Báo cáo</th><th class="n">CBO tự chọn</th><th class="n">Nested loops</th><th class="n">Hash</th><th class="n">Merge</th></tr>
      <tr><td>Theo danh mục</td><td class="n best">94,5<i class="bar" data-v="94.5"></i></td><td class="n bad">459,4<i class="bar" data-v="459.4"></i></td><td class="n">108,5<i class="bar" data-v="108.5"></i></td><td class="n">314,1<i class="bar" data-v="314.1"></i></td></tr>
      <tr><td>Top sản phẩm</td><td class="n best">101,4<i class="bar" data-v="101.4"></i></td><td class="n bad">528,4<i class="bar" data-v="528.4"></i></td><td class="n">136,1<i class="bar" data-v="136.1"></i></td><td class="n">306,4<i class="bar" data-v="306.4"></i></td></tr>
      <tr><td>Khách VIP</td><td class="n best">126,5<i class="bar" data-v="126.5"></i></td><td class="n">160,3<i class="bar" data-v="160.3"></i></td><td class="n">138,9<i class="bar" data-v="138.9"></i></td><td class="n">140,1<i class="bar" data-v="140.1"></i></td></tr></table>
    <div class="take soft"><div class="label">Kết quả</div><p>Kế hoạch CBO tự chọn nhanh nhất ở cả ba báo cáo. Ép nested loops chậm <b>4,9–5,2 lần</b> ở hai báo cáo join 4 bảng; khách VIP chỉ join 2 bảng nên chậm 1,3 lần.</p></div>
  </div>
  <div class="card" data-a style="--i:2">
    <div class="label" style="margin:0">Thống kê của bảng · báo cáo top sản phẩm</div>
    <table class="tbl p4t"><tr><th>Trạng thái</th><th class="n">CBO ước lượng</th><th>Join</th><th class="n">Block đọc</th></tr>
      <tr><td>Có thống kê</td><td class="n best">9.001</td><td>Hash join</td><td class="n best">5.161</td></tr>
      <tr><td>Xóa thống kê</td><td class="n bad">285.000</td><td>Merge join</td><td class="n bad">91.035</td></tr>
      <tr><td>Thu thập lại</td><td class="n best">9.002</td><td>Hash join</td><td class="n best">5.161</td></tr></table>
    <p class="small">Thực tế có 9.000 dòng. Mất thống kê thì CBO đoán sai, đổi sang merge join và đọc gấp 17,6 lần số block.</p>
    <div class="take soft"><div class="label">Thu thập lại thống kê</div><p>Sau khi nạp nhiều dữ liệu, chạy lại <code>GATHER_TABLE_STATS</code> với <code>no_invalidate =&gt; FALSE</code> để Oracle bỏ kế hoạch cũ đang lưu.</p></div>
  </div>
</div>`,
enter(root,{print}){const bars=root.querySelectorAll('.p4-cbo .bar');
  const set=()=>bars.forEach(e=>e.style.width=(e.dataset.v/528.4*6.5).toFixed(2)+'rem');
  if(print){bars.forEach(e=>e.style.transition='none');set();return}
  bars.forEach(e=>e.style.width='0');root._b=setTimeout(set,700)},
leave(root){clearTimeout(root._b)}});

/* ---------- 4 · Redis: lập trình viên quyết ---------- */
Deck.add({part:0,title:'Hai quyết định thiết kế trên Redis',
demo:{label:'Query Lab · Round-trip',path:'/admin/reports/lab'},
html:`
<style>
.p4t{font-size:1.3rem}.p4t th{font-size:.85rem}.p4t td{padding:.9rem .9rem;vertical-align:middle}
.p4t td:first-child{white-space:nowrap}
.p4t td.n,.p4t th.n{text-align:right;font-variant-numeric:tabular-nums;font-family:var(--mono)}
.p4t td.best{color:var(--ora);font-weight:800}.p4t td.bad{color:var(--bad);font-weight:800}.p4t td.r{color:var(--red);font-weight:800}
.p4-rd{display:grid;grid-template-columns:1fr 1.25fr;gap:1.2rem;flex:1;min-height:0}
.p4-rd .card{display:flex;flex-direction:column;gap:1rem}
.p4-sort{display:grid;grid-template-columns:1fr auto 1fr;gap:.8rem;align-items:center}
.p4-sort .kpi{background:var(--surface2);border-radius:var(--r-sm);padding:1.1rem}
.p4-sort .kpi b{font-size:2.3rem}
.p4-sort .arrow{font-size:2rem;color:var(--muted)}
.p4-rt{display:flex;gap:2.2rem;align-items:baseline;margin-top:auto;padding-top:1rem;border-top:.08rem solid var(--line)}
.p4-rd .hbars.big .hb .tr{height:1.8rem}.p4-rd .hbars.big{gap:1.8rem}.p4-rd .hbars.big .hb .lb{font-size:1.15rem}
.p4-rt .kpi b{font-size:4.4rem}
</style>
${Deck.head(4,'Hai quyết định thiết kế trên Redis','<span class="chip r" data-morph="red">REDIS · KHÔNG CÓ CBO</span>')}
<div class="p4-rd body">
  <div class="card" data-a style="--i:1">
    <div class="label" style="margin:0">Quyết định 1 · khai báo index SORTABLE</div>
    <p style="font-size:1.05rem">Trường dùng trong GROUPBY, REDUCE chưa SORTABLE thì Redis phải đọc lại JSON của từng document.</p>
    <div class="p4-sort">
      <div class="kpi bad"><b>~1,5 s</b><span>chưa SORTABLE</span></div>
      <div class="arrow">→</div>
      <div class="kpi ok"><b>0,15–0,33 s</b><span>có SORTABLE</span></div>
    </div>
    <p class="small">Báo cáo theo danh mục, đo phía server bằng INFO commandstats lúc dựng index; không cùng lần đo với bảng bên phải.</p>
    <div class="code" style="font-size:.8rem"><span class="k">FT.CREATE</span> idx:order-line-analytics <span class="k">ON JSON</span> <span class="k">SCHEMA</span>
  $.status       <span class="k">AS</span> status       <span class="k">TAG</span>
  $.createdAt    <span class="k">AS</span> createdAt    <span class="k">NUMERIC</span> <span class="g">SORTABLE</span>
  $.categoryId   <span class="k">AS</span> categoryId   <span class="k">NUMERIC</span> <span class="g">SORTABLE</span>
  $.categoryName <span class="k">AS</span> categoryName <span class="k">TEXT NOINDEX</span> <span class="g">SORTABLE</span> UNF
  $.quantity     <span class="k">AS</span> quantity     <span class="k">NUMERIC</span> <span class="g">SORTABLE</span>
  $.lineTotal    <span class="k">AS</span> lineTotal    <span class="k">NUMERIC</span> <span class="g">SORTABLE</span>
  <span class="c">... trích từ init.lua</span></div>
    <div class="take soft" style="margin-top:auto"><div class="label">Thiếu SORTABLE</div><p>Báo cáo chậm <b>4,5–10 lần</b>. Redis không có CBO để chọn cách khác, phải sửa index.</p></div>
  </div>
  <div class="card" data-a style="--i:2">
    <div class="label" style="margin:0">Quyết định 2 · để Redis gom nhóm hay kéo dữ liệu về ứng dụng (ms)</div>
    <table class="tbl p4t"><tr><th>Báo cáo</th><th class="n">Redis gom<br>(AGGREGATE)</th><th class="n">Kéo về ứng dụng<br>(CLIENT)</th><th class="n">Chậm hơn</th><th class="n">Dòng kéo về</th></tr>
      <tr><td>Theo tháng</td><td class="n r">62,7</td><td class="n">625,0</td><td class="n">10,0×</td><td class="n">87.139</td></tr>
      <tr><td>Theo danh mục</td><td class="n r">55,8</td><td class="n">1.686,0</td><td class="n">30,2×</td><td class="n">215.805</td></tr>
      <tr><td>Top sản phẩm</td><td class="n r">92,2</td><td class="n">1.961,9</td><td class="n">21,3×</td><td class="n">215.805</td></tr>
      <tr><td>Khách VIP</td><td class="n r">192,8</td><td class="n">673,7</td><td class="n">3,5×</td><td class="n">87.139</td></tr></table>
    <div class="take soft" style="margin-top:auto"><div class="label">Kéo dữ liệu thô về ứng dụng</div><p>Chậm hơn <b>3,5–30 lần</b> so với để Redis tự gom, vì phải gọi Redis 9–22 lần và chuyển tới 215.805 dòng qua mạng.</p></div></div>
  </div>
</div>`
});

