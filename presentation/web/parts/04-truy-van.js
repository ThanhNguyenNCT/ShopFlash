/* Phần 4 · Xử lý truy vấn (5 phút). Nguồn: Report/Sections/Tasks/task3.tex (mục 2.3), số đo ngày 19/11/2026. */
Deck.opener({part:4,title:'Xử lý truy vấn'});

Deck.add({part:4,title:'Hai cách chạy báo cáo doanh thu',
demo:{label:'Query Lab',path:'/admin/reports/lab'},
html:`
<style>
.p4-two{display:grid;grid-template-columns:1fr 1fr;gap:1.2rem;flex:1;min-height:0}
.p4-two .card{display:flex;flex-direction:column;gap:.9rem}
.p4-steps{display:grid;gap:.45rem}
.p4-step{display:grid;grid-template-columns:2rem 1fr auto;gap:.8rem;align-items:center;padding:.75rem 1rem;border-radius:.7rem;background:var(--surface2);font-size:1.08rem}
.p4-step i{font-style:normal;width:2rem;height:2rem;border-radius:50%;display:grid;place-items:center;font-size:.72rem;font-weight:800;color:#fff}
.p4-two .o .p4-step i{background:var(--ora)}.p4-two .r .p4-step i{background:var(--red)}
.p4-step b{font-family:var(--mono);font-size:.82rem;color:var(--muted);font-weight:600}
.p4-step.hl{background:var(--ora-soft)}
.p4-who{margin-top:auto;padding-top:.8rem;border-top:.08rem solid var(--line);font-size:1.3rem;font-weight:800}
.p4-mini{display:grid;grid-template-columns:repeat(3,1fr);gap:.6rem}
.p4-mini div{background:var(--surface2);border-radius:.7rem;padding:.7rem .9rem}
.p4-mini b{display:block;font-size:1.7rem;font-weight:900;line-height:1.05;font-variant-numeric:tabular-nums}
.p4-mini span{font-size:.8rem;color:var(--muted)}
.o .p4-mini b{color:var(--ora)}.r .p4-mini b{color:var(--red)}
</style>
${Deck.head(4,'Hai cách chạy báo cáo doanh thu')}
<div class="p4-two body">
  <div class="card o" data-a style="--i:1">
    <div class="row"><span class="chip o" data-morph="ora">ORACLE · SQL + CBO</span><span class="small">ví dụ: báo cáo top sản phẩm</span></div>
    <div class="code"><span class="k">SELECT</span> p.product_id, p.name, c.name,
       <span class="k">SUM</span>(oi.quantity), <span class="k">SUM</span>(oi.line_total)
<span class="k">FROM</span> orders o
<span class="k">JOIN</span> order_items oi <span class="k">ON</span> ...  <span class="k">JOIN</span> products p <span class="k">ON</span> ...
<span class="k">JOIN</span> categories c <span class="k">ON</span> ...
<span class="k">WHERE</span> o.status <span class="k">IN</span> (<span class="s">'CONFIRMED'</span>,<span class="s">'COMPLETED'</span>) <span class="k">AND</span> ...
<span class="k">GROUP BY</span> ... <span class="k">FETCH FIRST</span> :limit <span class="k">ROWS ONLY</span></div>
    <div class="label" style="margin:0">CBO tự chọn</div>
    <div class="p4-steps">
      <div class="p4-step"><i>1</i>Quét toàn bảng ORDERS, ORDER_ITEMS<b>87.139 · 247K dòng</b></div>
      <div class="p4-step hl"><i>2</i>Tự viết lại: gom trước, join sau<b>215K → 9.000</b></div>
      <div class="p4-step"><i>3</i>Hash join với PRODUCTS, CATEGORIES<b>ước lượng 9.001</b></div>
    </div>
    <div class="p4-mini"><div><b>4 bảng</b><span>join lúc chạy</span></div><div><b>9.000</b><span>nhóm, gom trước khi join</span></div><div><b>5.161</b><span>block đọc từ bộ đệm</span></div></div>
    <div class="p4-who o-t">CBO chọn thuật toán join và tự viết lại câu lệnh.</div>
  </div>
  <div class="card r" data-a style="--i:2">
    <div class="row"><span class="chip r" data-morph="red">REDIS · FT.AGGREGATE</span><span class="small">ví dụ: báo cáo theo danh mục</span></div>
    <div class="code"><span class="k">FT.AGGREGATE</span> idx:order-line-analytics
  <span class="s">"@status:{CONFIRMED|COMPLETED}
   @createdAt:[1761930000 (1792342800]"</span>
  <span class="k">GROUPBY</span> 2 @categoryId @categoryName
    <span class="k">REDUCE SUM</span> 1 @quantity <span class="k">AS</span> quantity
    <span class="k">REDUCE SUM</span> 1 @lineTotal <span class="k">AS</span> revenue
  <span class="k">SORTBY</span> 4 @revenue <span class="k">DESC</span> @categoryId <span class="k">ASC</span></div>
    <div class="label" style="margin:0">Chạy đúng thứ tự đã viết</div>
    <div class="p4-steps">
      <div class="p4-step"><i>1</i>Lọc theo TAG và khoảng ngày<b>215.805 dòng</b></div>
      <div class="p4-step"><i>2</i>Gom nhóm theo danh mục<b>50 nhóm</b></div>
      <div class="p4-step"><i>3</i>Sắp xếp<b>không có join</b></div>
    </div>
    <div class="p4-mini"><div><b>0 join</b><span>đã join khi dựng read model</span></div><div><b>50</b><span>nhóm sau GROUPBY</span></div><div><b>1 lệnh</b><span>một lần gọi Redis</span></div></div>
    <div class="p4-who r-t">Các bước chạy đúng thứ tự trong lệnh; không có CBO.</div>
  </div>
</div>`});

/* ---------- 3 · Oracle: CBO cần thống kê đúng ---------- */
Deck.add({part:4,title:'Oracle chọn kế hoạch dựa trên thống kê',
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
.p4-cbo .p4t.p4s{font-size:1.25rem;width:100%}.p4-cbo .p4t.p4s td{padding:1.3rem .4rem}.p4-cbo .p4t.p4s th{padding-left:.4rem;padding-right:.4rem}.p4-cbo .p4t.p4s td.m{color:var(--muted)}.p4-cbo .p4t.p4s tr.grp td{padding:.9rem .4rem .2rem;font-size:.85rem;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:var(--muted);border-bottom:0}.p4-cbo .p4t.p4s td{padding:.8rem .4rem}
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
    <table class="tbl p4t p4s"><tr><th></th><th class="n">CBO đoán</th><th class="n">Thực tế</th><th>Kế hoạch</th><th class="n">Block đọc</th></tr>
      <tr class="grp"><td colspan="5">Lọc đơn trên <code>ORDERS</code></td></tr>
      <tr><td>Có thống kê</td><td class="n best">87.139</td><td class="n m">87.139</td><td>Quét toàn bảng</td><td class="n best">2.139</td></tr>
      <tr><td>Xóa thống kê</td><td class="n bad">2</td><td class="n m">87.139</td><td>Đi index</td><td class="n bad">88.010</td></tr>
      <tr class="grp"><td colspan="5">Số sản phẩm sau <code>GROUP BY</code></td></tr>
      <tr><td>Có thống kê</td><td class="n best">9.000</td><td class="n m">9.000</td><td>Hash join</td><td class="n"></td></tr>
      <tr><td>Xóa thống kê</td><td class="n bad">285.000</td><td class="n m">9.000</td><td>Merge join</td><td class="n"></td></tr></table>
    <p class="small">Mất thống kê, CBO tưởng điều kiện lọc chỉ còn 2 đơn nên đi index, rồi nhảy 87.139 lần theo <code>ROWID</code> sang bảng. Cả báo cáo đọc 91.035 block thay vì 5.161, gấp 17,6 lần. Thu thập lại thì về đúng kế hoạch cũ.</p>
    <div class="take soft"><div class="label">Thu thập lại thống kê</div><p>Sau khi nạp nhiều dữ liệu, chạy lại <code>GATHER_TABLE_STATS</code> với <code>no_invalidate =&gt; FALSE</code> để Oracle bỏ kế hoạch cũ đang lưu.</p></div>
  </div>
</div>`,
enter(root,{print}){const bars=root.querySelectorAll('.p4-cbo .bar');
  const set=()=>bars.forEach(e=>e.style.width=(e.dataset.v/528.4*6.5).toFixed(2)+'rem');
  if(print){bars.forEach(e=>e.style.transition='none');set();return}
  bars.forEach(e=>e.style.width='0');root._b=setTimeout(set,700)},
leave(root){clearTimeout(root._b)}});

/* ---------- 4 · Redis: lập trình viên quyết ---------- */
Deck.add({part:4,title:'Hai quyết định thiết kế trên Redis',
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

/* ---------- 6 · Lựa chọn ---------- */
Deck.add({part:4,title:'Lựa chọn cho báo cáo của ShopFlash',
demo:{label:'Báo cáo',path:'/admin/reports'},
html:`
<style>
.p4-ch{display:grid;grid-template-columns:1.15fr 1fr 1fr;gap:1rem;flex:1;min-height:0}
.p4-ch .card{display:flex;flex-direction:column;gap:.8rem}
.p4-ch .card{gap:.9rem}
.p4-ch h3{font-size:1.6rem}
.p4-ch .kpi b{font-size:2.7rem;white-space:nowrap}
.p4-ch .kpi span{font-size:1rem}
.p4-ch .list{font-size:1.22rem;gap:.75rem}
.p4-ch .kpi{background:var(--surface2);border-radius:var(--r-sm);padding:1rem 1.1rem;margin-top:auto}
.p4-ch .main{border-top:.35rem solid var(--ora)}
</style>
${Deck.head(4,'Lựa chọn cho báo cáo của ShopFlash')}
<div class="body" style="gap:1rem">
  <div class="p4-ch">
    <div class="card main" data-a style="--i:1">
      <span class="chip o" data-morph="ora">ORACLE · MẶC ĐỊNH</span>
      <h3>Báo cáo quản trị</h3>
      <ul class="list o">
        <li>Luôn đọc dữ liệu đã commit mới nhất, thấy cả đơn vừa đặt</li>
        <li>Join, subquery, anti-join trong một câu SQL</li>
        <li>Báo cáo mới chỉ cần thêm một câu SQL</li>
        <li>Không có bản sao phải dựng lại</li>
        <li>Chênh tốc độ với Redis nhỏ và đổi chiều giữa hai lần đo, nên không phải lý do chọn</li>
      </ul>
      <div class="kpi o"><b>31,7 ms</b><span>anti-join ra 1.147 sản phẩm chưa từng bán</span></div>
    </div>
    <div class="card" data-a style="--i:2">
      <span class="chip r" data-morph="red">REDIS · CACHE-ASIDE</span>
      <h3>Xem lại cùng khoảng ngày</h3>
      <ul class="list r"><li>Oracle tính, Redis giữ kết quả</li><li>Số liệu có thể trễ tối đa 5 phút</li><li>Đơn mới không xóa cache</li></ul>
      <div class="kpi r"><b>1,05–1,90 ms</b><span>mỗi lần cache HIT, nhanh hơn gọi Oracle 24–96 lần</span></div>
    </div>
    <div class="card" data-a style="--i:3">
      <span class="chip soft">REDIS · READ MODEL</span>
      <h3>Tính sẵn cho câu hỏi đã biết</h3>
      <ul class="list"><li>Phải denormalize, tự thiết kế index SORTABLE</li><li>Quên dựng lại thì trả số cũ, không báo lỗi</li><li>Giữ trong Query Lab để minh họa</li></ul>
      <div class="kpi"><b>14–39 s</b><span>mỗi lần dựng lại; giữa hai lần dựng, số liệu cũ</span></div>
    </div>
  </div>
  <div class="take" data-a style="--i:4"><div class="label">ShopFlash dùng</div><p>Báo cáo quản trị chạy SQL trên Oracle. Redis giữ kết quả đã tính trong 5 phút (cache-aside). Read model trên Redis chỉ còn trong Query Lab để so sánh.</p></div>
</div>`,
});
