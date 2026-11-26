/* Phần 6 · Kiểm soát đồng thời (5 phút). Nội dung chép nguyên văn từ deck Canva, trang 31–35. */
Deck.opener({part:6,title:'Kiểm soát đồng thời<br>(Concurrency)'});

/* ---------- p.32 · Bài toán và bốn cơ chế ---------- */
Deck.add({part:6,title:'Bài toán và bốn cơ chế',
html:`
<style>
.p6-prob{background:var(--brand);color:#fff;border-radius:var(--r-sm);padding:1.3rem 1.6rem;font-size:1.4rem;font-weight:700;line-height:1.45}
.p6-mech{flex:1;min-height:0;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:1.1rem;margin-top:1.1rem}
.p6-mech .card{display:flex;flex-direction:column;justify-content:center;gap:1.1rem;background:var(--surface);padding:1.6rem 1.9rem;border-left:.35rem solid var(--accent)}
.p6-mech h3{font-size:2rem;color:var(--brand)}
.p6-mech h3 small{font-size:1.35rem;font-weight:600;color:var(--muted)}
.p6-mech p{font-size:1.6rem;line-height:1.55;color:var(--ink2)}
.p6-mech p .o-t,.p6-mech p .r-t{font-weight:700}
</style>
${Deck.head(6,'Bài toán và bốn cơ chế')}
<div class="body">
  <div class="p6-prob" data-a style="--i:1">Bài toán (lost update): hai khách cùng đọc sold = 99, cùng ghi sold = 100. Cả hai được báo mua thành công nhưng bộ đếm chỉ tăng 1, ràng buộc CHECK không phát hiện được.</div>
  <div class="p6-mech">
    <div class="card" data-a style="--i:2"><h3>Khóa bi quan <small>(Pessimistic Lock)</small></h3><p>Khóa dòng trước khi đọc để ghi, người đến sau phải chờ. <span class="o-t">Oracle: SELECT … FOR UPDATE.</span> Có thể gây deadlock.</p></div>
    <div class="card" data-a style="--i:3"><h3>Khóa lạc quan <small>(Optimistic Lock)</small></h3><p>Không khóa khi đọc; lúc ghi mới kiểm tra dữ liệu có bị đổi chưa, bị đổi thì thử lại. <span class="o-t">Oracle: cột version.</span> <span class="r-t">Redis: WATCH.</span></p></div>
    <div class="card" data-a style="--i:4"><h3>MVCC</h3><p>Giữ nhiều phiên bản dữ liệu: đọc không chặn ghi, ghi không chặn đọc. <span class="o-t">Oracle dựng bản chụp từ undo.</span></p></div>
    <div class="card" data-a style="--i:5"><h3>Thao tác nguyên tử</h3><p>Kiểm tra và ghi trong một lệnh, không còn khoảng hở. <span class="o-t">Oracle: UPDATE … WHERE sold + 1 &lt;= allocated.</span> <span class="r-t">Redis: Lua script.</span></p></div>
  </div>
</div>`});

/* ---------- p.33 · So sánh Oracle và Redis ---------- */
Deck.add({part:6,title:'So sánh Oracle và Redis',
html:`
<style>
.p6-cmp{flex:1;min-height:0;display:grid;grid-template-columns:30rem 1fr;gap:1.4rem}
.p6-core{background:var(--surface2);border-radius:var(--r);padding:2.2rem 2.4rem;display:flex;flex-direction:column;justify-content:center;gap:1.5rem}
.p6-core .ey{font-size:1rem;font-weight:800;letter-spacing:.12em;color:var(--accent)}
.p6-core h3{font-size:4.6rem;font-weight:900;line-height:1.05;letter-spacing:-.03em;color:var(--brand)}
.p6-core p{font-size:1.5rem;line-height:1.5;color:var(--ink2)}
.p6-tw{display:flex;flex-direction:column}
.p6-tw .tbl{font-size:1.5rem}
.p6-tw .tbl th{font-size:1rem;padding:1rem 1.1rem;background:var(--brand);color:#fff}
.p6-tw .tbl th:first-child{border-radius:var(--r-sm) 0 0 0}.p6-tw .tbl th:last-child{border-radius:0 var(--r-sm) 0 0}
.p6-tw .tbl td{padding:2.05rem 1.1rem;vertical-align:middle}
.p6-tw .tbl td:nth-child(2){color:var(--ora)}.p6-tw .tbl td:nth-child(3){color:var(--red)}
.p6-tw .nt{margin-top:auto;font-size:1.45rem;font-weight:700;color:var(--ink);padding:1rem 1.2rem;border-radius:var(--r-sm);background:var(--surface2)}
</style>
${Deck.head(6,'So sánh Oracle và Redis')}
<div class="body">
  <div class="p6-cmp">
    <div class="p6-core" data-a style="--i:1">
      <div class="ey">KHÁC BIỆT CỐT LÕI</div>
      <h3>Khóa dòng<br>vs. tuần tự</h3>
      <p>Oracle khóa từng dòng; MVCC cho đọc không chặn ghi.</p>
      <p>Redis chạy từng lệnh hoặc script một mình: không khóa nên không deadlock, nhưng cũng không rollback.</p>
    </div>
    <div class="p6-tw" data-a style="--i:2">
      <table class="tbl">
        <tr><th>Tiêu chí</th><th>Oracle</th><th>Redis</th></tr>
        <tr><td>Rollback</td><td>Có, theo câu lệnh và transaction</td><td>Không (cả MULTI/EXEC lẫn Lua)</td></tr>
        <tr><td>Khóa bi quan</td><td>SELECT … FOR UPDATE</td><td>Không có sẵn</td></tr>
        <tr><td>Khóa lạc quan</td><td>Cột version</td><td>WATCH</td></tr>
        <tr><td>Kiểm tra rồi ghi nguyên tử</td><td>UPDATE … WHERE có điều kiện</td><td>Lua script</td></tr>
      </table>
      <div class="nt">Cả hai đều chặn được bán vượt; khác nhau ở chi phí khi tranh chấp cao.</div>
    </div>
  </div>
</div>`});

/* ---------- p.34 · Thiết kế Flash Sale ShopFlash ---------- */
Deck.add({part:6,title:'Thiết kế Flash Sale ShopFlash',
demo:{label:'Flash Sale',path:'/flash-sale'},
html:`
<style>
.p6-fs{justify-content:space-between}
.p6-fs .flow{flex:none}
.p6-fs .flow .st{padding:1.8rem 1.7rem 2.2rem;background:var(--surface)}
.p6-fs .flow .st::before{background:var(--accent);width:2.4rem;height:2.4rem;font-size:1.05rem}
.p6-fs .flow .st h3{font-size:1.65rem;color:var(--brand)}
.p6-fs .flow .st p{font-size:1.4rem;line-height:1.5;margin-top:.5rem}
.p6-where{margin-top:0}
.p6-where .lbl{font-size:1.6rem;font-weight:800;color:var(--brand);margin-bottom:.7rem}
.p6-stack{display:flex;border-radius:var(--r-sm);overflow:hidden;box-shadow:var(--shadow)}
.p6-stack div{padding:1.8rem 1rem;text-align:center;color:#fff}
.p6-stack b{display:block;font-size:1.65rem;font-weight:800}
.p6-stack span{display:block;font-size:1.25rem;opacity:.92;margin-top:.2rem}
.p6-stack .o{flex:41;background:var(--ora)}.p6-stack .r{flex:41;background:var(--red)}.p6-stack .a{flex:18;background:var(--accent)}
.p6-fs .list{margin-top:0;font-size:1.45rem}
.p6-fs .list li::before{background:var(--brand)}
</style>
${Deck.head(6,'Thiết kế Flash Sale ShopFlash')}
<div class="body p6-fs">
  <div class="flow">
    <div class="st" data-a style="--i:1"><h3>Giữ suất (Redis)</h3><p>Một Lua script kiểm tra và trừ suất, tạo suất giữ có hạn 60 giây. Hết suất thì từ chối ngay.</p></div>
    <div class="st" data-a style="--i:2"><h3>Đặt đơn (Oracle)</h3><p>Một transaction: UPDATE có điều kiện tăng số đã bán, trừ kho, ghi đơn.</p></div>
    <div class="st" data-a style="--i:3"><h3>Xác nhận hoặc trả suất</h3><p>Commit thì xác nhận suất; rollback thì Lua trả suất về Redis, đúng một lần.</p></div>
    <div class="st" data-a style="--i:4"><h3>Job dọn (mỗi 5 giây)</h3><p>Trả các suất hết hạn mà khách chưa đặt đơn, kể cả khi backend sập giữa chừng.</p></div>
  </div>
  <div class="p6-where" data-a style="--i:5">
    <div class="lbl">Dữ liệu Flash Sale nằm ở đâu</div>
    <div class="p6-stack">
      <div class="o"><b>Oracle: nguồn gốc</b><span>chương trình, số đã bán, tồn kho, đơn hàng</span></div>
      <div class="r"><b>Redis: trạng thái chạy</b><span>suất còn lại, suất đang giữ, số đã mua mỗi khách</span></div>
      <div class="a"><b>Dựng lại</b><span>Redis từ Oracle</span></div>
    </div>
  </div>
  <ul class="list" data-a style="--i:6">
    <li>Hai DBMS không có transaction chung (Redis không hỗ trợ XA) nên luồng mua theo Saga kiểu Try–Confirm–Cancel.</li>
    <li>Oracle là chốt chặn cuối: sold + q &lt;= allocated giữ số bán không vượt số suất, kể cả khi Redis lệch.</li>
  </ul>
</div>`});

/* ---------- p.35 · Kết luận ---------- */
Deck.add({part:6,title:'Kết luận',
demo:{label:'Flash Sale Lab',path:'/admin/flash-sales/lab'},
html:`
<style>
.p6-end{flex:1;min-height:0;display:grid;grid-template-columns:28rem 1fr;gap:1.4rem}
.p6-tiles{display:grid;grid-template-rows:repeat(3,1fr);gap:1rem}
.p6-tiles .kpi{background:var(--surface2);border-radius:var(--r);padding:1.3rem 1.6rem;display:flex;flex-direction:column;justify-content:center}
.p6-tiles .kpi b{font-size:3.6rem;color:var(--brand)}
.p6-tiles .kpi span{font-size:1.15rem;color:var(--ink2)}
.p6-tiles .kpi.bad b{color:var(--bad)}
.p6-rt{display:flex;flex-direction:column}
.p6-rt .tbl{font-size:1.6rem}
.p6-rt .tbl th{font-size:1rem;padding:1rem 1.1rem;background:var(--brand);color:#fff}
.p6-rt .tbl th:first-child{border-radius:var(--r-sm) 0 0 0}.p6-rt .tbl th:last-child{border-radius:0 var(--r-sm) 0 0}
.p6-rt .tbl td{padding:2.1rem 1.1rem;vertical-align:middle}
.p6-rt .tbl tr.r td:first-child{color:var(--red)}.p6-rt .tbl tr.o td:first-child{color:var(--ora)}
.p6-rt .e2{margin-top:1.2rem;font-size:1.25rem;color:var(--muted)}
.p6-rt .take{margin-top:auto}
.p6-rt .take p{font-size:1.4rem;font-weight:700}
</style>
${Deck.head(6,'Kết luận')}
<div class="body">
  <div class="p6-end">
    <div class="p6-tiles">
      <div class="kpi bad" data-a style="--i:1"><b data-count="900">900</b><span>suất bán vượt khi viết kiểu “đọc rồi ghi”, trên cả Oracle và Redis (1000 khách, 100 suất)</span></div>
      <div class="kpi" data-a style="--i:2"><b>4,6×</b><span>throughput của Lua so với UPDATE có điều kiện trên Oracle, 64 lượt đồng thời</span></div>
      <div class="kpi" data-a style="--i:3"><b>50 / 500</b><span>khách giữ được suất qua HTTP khi tranh 50 suất, đúng bằng số suất</span></div>
    </div>
    <div class="p6-rt" data-a style="--i:4">
      <table class="tbl">
        <tr><th>Chiến lược</th><th class="n">Lượt/giây</th><th class="n">p95 (ms)</th><th class="n">Bán được / 2000</th></tr>
        <tr class="r"><td>Redis Lua</td><td class="n">8.951</td><td class="n">25,9</td><td class="n">2000</td></tr>
        <tr class="o"><td>Oracle UPDATE có điều kiện</td><td class="n">1.937</td><td class="n">57,2</td><td class="n">2000</td></tr>
        <tr class="r"><td>Redis WATCH</td><td class="n">579</td><td class="n">296</td><td class="n">797</td></tr>
      </table>
      <div class="e2">2000 lượt tranh 2000 suất, 64 lượt đồng thời, trung bình 5 lần đo. Oracle FOR UPDATE: 336 lượt/giây.</div>
      <div class="take"><p>Chọn Redis + Lua để giữ suất, Oracle + UPDATE có điều kiện để chốt đơn. Không dùng “đọc rồi ghi” hay khóa lạc quan cho điểm nóng.</p></div>
    </div>
  </div>
</div>`,
enter(root){Deck.count(root)}});
