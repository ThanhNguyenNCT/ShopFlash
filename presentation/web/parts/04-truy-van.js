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

/* ---------- 5 · So kè bốn báo cáo ---------- */
Deck.add({part:4,title:'Kết quả đo bốn báo cáo',
html:`
<style>
.p4t{font-size:1.3rem}.p4t th{font-size:.85rem}.p4t td{padding:.9rem .9rem;vertical-align:middle}
.p4t td:first-child{white-space:nowrap}
.p4t td.n,.p4t th.n{text-align:right;font-variant-numeric:tabular-nums;font-family:var(--mono)}
.p4t td.best{color:var(--ora);font-weight:800}.p4t td.bad{color:var(--bad);font-weight:800}.p4t td.r{color:var(--red);font-weight:800}
.p4-bench .p4t{font-size:1.3rem}.p4-bench .p4t td{padding:1.15rem .8rem}.p4-bench .p4t td:first-child{white-space:nowrap}
.p4-bench{display:grid;grid-template-columns:1fr;gap:1.2rem;flex:1;min-height:0}
.p4-bench .card{display:flex;flex-direction:column;gap:1rem}
.p4-fact{display:grid;gap:.8rem;grid-template-columns:repeat(3,1fr)}
.p4-fact .f{display:grid;grid-template-columns:10.6rem 1fr;gap:1rem;align-items:center;padding:.7rem 1rem;border-radius:var(--r-sm);background:var(--surface2)}
.p4-fact .f b.big{font-size:2.1rem;font-weight:900;line-height:1.05;white-space:nowrap}
.p4-fact .f b.big .ic{width:2.4rem;height:2.4rem}
.p4-bench .hbars.big .hb .tr{height:1.9rem}.p4-bench .hbars.big{gap:1.9rem}.p4-bench .hbars.big .hb .lb{font-size:1.15rem}
.p4-fact .f p{margin:0;font-size:1rem;line-height:1.4;color:var(--ink2)}
</style>
${Deck.head(4,'Kết quả đo bốn báo cáo','<div class="row"><span class="chip o" data-morph="ora">ORACLE</span><span class="chip r" data-morph="red">REDIS</span></div>')}
<div class="p4-bench body">
  <div class="card" data-a style="--i:1">
    <div class="label" style="margin:0">Thời gian (ms) · trung bình 5 lần đo ngày 19/11/2026</div>
    <table class="tbl p4t"><tr><th>Báo cáo</th><th class="n">Oracle<br>CBO tự chọn</th><th class="n">Oracle<br>ép nested loops</th><th class="n">Redis<br>AGGREGATE</th><th class="n">Redis<br>CLIENT</th></tr>
      <tr><td>Theo tháng</td><td class="n best">49,7</td><td class="n">–</td><td class="n">62,7</td><td class="n">625,0</td></tr>
      <tr><td>Theo danh mục</td><td class="n">94,5</td><td class="n">459,4</td><td class="n r">55,8</td><td class="n">1.686,0</td></tr>
      <tr><td>Top sản phẩm</td><td class="n">101,4</td><td class="n">528,4</td><td class="n r">92,2</td><td class="n">1.961,9</td></tr>
      <tr><td>Khách VIP</td><td class="n best">126,5</td><td class="n">160,3</td><td class="n">192,8</td><td class="n">673,7</td></tr>
      <tr><td>Sản phẩm chưa từng bán</td><td class="n best">31,7</td><td class="n">–</td><td class="n bad">không làm được</td><td class="n bad">không làm được</td></tr></table>
    <div class="take soft" style="margin-top:auto"><div class="label">Lần đo trước, ngày 18/10/2026</div><p>Kết quả ngược với bảng trên: Oracle nhanh hơn Redis AGGREGATE ở cả bốn báo cáo (1,8–5,5 lần). Hai lần đo cùng máy, cùng dữ liệu, cùng khoảng ngày, cùng câu SQL, cùng index Redis và lệnh FT.AGGREGATE; nhóm chưa tìm ra nguyên nhân nên không dùng chênh lệch này để chọn DBMS.</p></div>
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
