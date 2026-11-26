/* Phần 5 · Giao dịch (5 phút). Nội dung chép nguyên văn từ deck Canva, trang 22–30. */
Deck.opener({part:5,title:'Giao dịch'});

/* ---------- p.23 · Transaction trong ShopFlash ---------- */
Deck.add({part:5,title:'Transaction trong ShopFlash',
demo:{label:'Giỏ hàng',path:'/cart'},
html:`
<style>
.p5-lead{font-size:2.4rem;font-weight:800;color:var(--ink);margin-top:.8rem}
.p5-ck{display:grid;grid-template-columns:auto auto 1fr;gap:1.2rem;align-items:center;margin-top:3rem}
.p5-start{background:var(--brand);color:#fff;border-radius:var(--r);padding:2.6rem 2.8rem;font-size:2.2rem;font-weight:900;letter-spacing:.04em;box-shadow:var(--shadow)}
.p5-arr{color:var(--muted);font-size:1.8rem}
.p5-arr .ic{width:2.2rem;height:2.2rem}
.p5-unit{display:flex;flex-direction:column}
.p5-four{display:grid;grid-template-columns:repeat(4,1fr);gap:.8rem}
.p5-four div{background:var(--surface);box-shadow:var(--shadow);border-radius:var(--r);padding:2.2rem 1rem;text-align:center}
.p5-four h3{font-size:1.75rem;font-weight:800}
.p5-four p{font-size:1.3rem;color:var(--muted);margin-top:.3rem}
.p5-brk{margin-top:.8rem;height:.9rem;border:.22rem solid var(--brand2);border-top:0;border-radius:0 0 .5rem .5rem}
.p5-unit .cap{text-align:center;margin-top:.6rem;color:var(--ink2);font-size:1.45rem;font-weight:600}
.p5-acid{margin-top:3.2rem}
.p5-acid .card{display:flex;flex-direction:column;justify-content:center;gap:1rem;background:var(--ora-soft);box-shadow:none;border:0;padding:3.2rem 2rem}
.p5-acid .hd{display:flex;align-items:center;gap:1rem}
.p5-acid .ltr{font-size:6.4rem;font-weight:900;line-height:.9;color:var(--brand);letter-spacing:-.04em}
.p5-acid h3{font-size:2rem}
.p5-acid p{font-size:1.5rem;color:var(--ink2)}
</style>
${Deck.head(5,'Transaction trong ShopFlash')}
<div class="body">
  <div class="p5-lead" data-a style="--i:1">Một lần checkout, nhiều thay đổi liên quan.</div>
  <div class="p5-ck">
    <div class="p5-start" data-a style="--i:2">CHECKOUT</div>
    <div class="p5-arr" data-a style="--i:3">${Deck.icon('arrow')}</div>
    <div class="p5-unit" data-a style="--i:4">
      <div class="p5-four">
        <div><h3>Order</h3><p>Tạo đơn</p></div>
        <div><h3>Order Items</h3><p>Ghi dòng hàng</p></div>
        <div><h3>Inventory</h3><p>Trừ tồn</p></div>
        <div><h3>Cart</h3><p>Xóa dòng đã mua</p></div>
      </div>
      <div class="p5-brk"></div>
      <div class="cap">Một đơn vị xử lý logic</div>
    </div>
  </div>
  <div class="grid g4 p5-acid">
    <div class="card" data-a style="--i:5"><div class="hd"><span class="ltr">A</span><h3>Atomicity</h3></div><p>Toàn bộ hoặc hoàn tác</p></div>
    <div class="card" data-a style="--i:6"><div class="hd"><span class="ltr">C</span><h3>Consistency</h3></div><p>Duy trì trạng thái hợp lệ</p></div>
    <div class="card" data-a style="--i:7"><div class="hd"><span class="ltr">I</span><h3>Isolation</h3></div><p>Cô lập giữa giao dịch</p></div>
    <div class="card" data-a style="--i:8"><div class="hd"><span class="ltr">D</span><h3>Durability</h3></div><p>Bảo toàn dữ liệu đã xác nhận</p></div>
  </div>
</div>`});

/* Kiểu dùng chung cho p.24 và p.25: lưới [bước | Oracle | Redis] */
const P5_TX_CSS=`
<style>
.p5-tx{display:grid;grid-template-columns:13rem 1fr 1fr;gap:.9rem 1rem;flex:1;min-height:0}
.p5-tx .hh{align-self:end}
.p5-tx .hh b{display:block;font-size:2rem;font-weight:900;line-height:1.1}
.p5-tx .hh span{display:block;font-size:1.15rem;color:var(--muted);margin-top:.2rem}
.p5-ex{grid-column:1/-1;font-size:1.2rem;color:var(--ink2)}
.p5-lbl{background:var(--brand);color:#fff;border-radius:var(--r-sm);padding:1.1rem 1.2rem}
.p5-lbl small{display:block;font-family:var(--mono);font-size:.95rem;opacity:.75;font-weight:700}
.p5-lbl b{display:block;font-size:1.9rem;font-weight:800;margin-top:.2rem}
.p5-c{border-radius:var(--r-sm);padding:1.4rem 1.7rem;display:flex;flex-direction:column;gap:.35rem;min-height:0}
.p5-c.o{background:var(--ora-soft);border-left:.35rem solid var(--ora)}
.p5-c.r{background:var(--red-soft);border-left:.35rem solid var(--red)}
.p5-c .cmd{font-family:var(--mono);font-weight:700;font-size:1.6rem;line-height:1.5;white-space:pre}
.p5-c.o .cmd{color:var(--ora)}.p5-c.r .cmd{color:var(--red)}
.p5-c .cmd i{font-style:normal;color:var(--ink)}
.p5-c .d{font-size:1.35rem;color:var(--ink2)}
.p5-c .res{margin-top:auto;padding-top:1rem;font-weight:700;font-size:1.4rem}
.p5-c.o .res{color:var(--ora)}.p5-c.r .res{color:var(--red)}
.p5-c .stk{font-weight:800;font-size:1.8rem;color:var(--ok);margin-top:auto}
.p5-bar .neq{font-family:var(--mono)}
.p5-bar{background:var(--brand);color:#fff;border-radius:var(--r-sm);padding:1rem 1.5rem;text-align:center;font-weight:800;font-size:1.35rem;margin-top:1rem}
</style>`;

/* ---------- p.24 · Bắt đầu và thực thi transaction ---------- */
Deck.add({part:5,title:'Bắt đầu và thực thi transaction',
html:`${P5_TX_CSS}
${Deck.head(5,'Bắt đầu và thực thi transaction')}
<div class="body">
  <div class="p5-tx" style="grid-template-rows:auto auto auto 1fr">
    <div></div>
    <div class="hh" data-a style="--i:1"><b class="o-t">Oracle</b></div>
    <div class="hh" data-a style="--i:1"><b class="r-t">Redis</b></div>
    <div class="p5-ex" data-a style="--i:2">Ví dụ: tồn kho P1 = 100; giảm 1 sản phẩm.</div>
    <div class="p5-lbl" data-a style="--i:3"><small>Bước 1</small><b>Bắt đầu</b></div>
    <div class="p5-c o" data-a style="--i:4"><div class="cmd">SET AUTOCOMMIT OFF</div><div class="d">DML bắt đầu transaction ngầm</div></div>
    <div class="p5-c r" data-a style="--i:5"><div class="cmd">MULTI <i>→ OK</i></div><div class="d">Mở hàng đợi command</div></div>
    <div class="p5-lbl" data-a style="--i:6"><small>Bước 2</small><b>Gửi lệnh</b></div>
    <div class="p5-c o" data-a style="--i:7"><div class="cmd"><i>UPDATE product_inventory
SET on_hand_quantity =
  on_hand_quantity - 1
WHERE product_id = :p1;
SELECT on_hand_quantity ...;</i></div><div class="res">→ 99 trong session hiện tại (chưa COMMIT)</div></div>
    <div class="p5-c r" data-a style="--i:8"><div class="cmd"><i>HINCRBY inventory:P1 on_hand -1</i>
→ QUEUED

<i>HGET inventory:P1 on_hand</i>
→ QUEUED</div><div class="res">→ Client chưa nhận kết quả đọc hay ghi</div></div>
  </div>
  <div class="p5-bar" data-a style="--i:9">Oracle thực thi ngay &nbsp;|&nbsp; Redis xếp hàng đến EXEC</div>
</div>`});

/* ---------- p.25 · Kết thúc transaction ---------- */
Deck.add({part:5,title:'Kết thúc transaction',
html:`${P5_TX_CSS}
${Deck.head(5,'Kết thúc transaction')}
<div class="body">
  <div class="p5-tx" style="grid-template-rows:auto 1fr 1fr auto">
    <div></div>
    <div class="hh" data-a style="--i:1"><b class="o-t">Oracle</b><span>Thay đổi đã thực thi, chưa commit</span></div>
    <div class="hh" data-a style="--i:1"><b class="r-t">Redis</b><span>Hai command đang ở hàng đợi</span></div>
    <div class="p5-lbl" data-a style="--i:2"><small>Bước 3</small><b>Hoàn tất</b></div>
    <div class="p5-c o" data-a style="--i:3"><div class="cmd">COMMIT;</div><div class="d">Xác nhận thay đổi đã thực thi</div><div class="stk">Stock = 99</div></div>
    <div class="p5-c r" data-a style="--i:4"><div class="cmd">EXEC <i>→ [99, "99"]</i></div><div class="d">Thực thi cả hai command</div><div class="stk">Stock = 99</div></div>
    <div class="p5-lbl" data-a style="--i:5"><small>Bước 3</small><b>Hủy</b></div>
    <div class="p5-c o" data-a style="--i:6"><div class="cmd">ROLLBACK;</div><div class="d">Hoàn tác thay đổi chưa commit</div><div class="stk">Stock = 100</div></div>
    <div class="p5-c r" data-a style="--i:7"><div class="cmd">DISCARD <i>→ OK</i></div><div class="d">Bỏ các command chưa thực thi</div><div class="stk">Stock = 100</div></div>
    <div></div>
    <div class="p5-c o" data-a style="--i:8"><div class="cmd">SAVEPOINT <i>→</i> ROLLBACK TO</div><div class="d">Hoàn tác một phần</div></div>
    <div class="p5-c r" data-a style="--i:9"><div class="cmd" style="font-family:var(--font)">Không có rollback tương đương</div><div class="d">Sau khi EXEC đã chạy</div></div>
  </div>
  <div class="p5-bar" data-a style="--i:10">EXEC <span class="neq">≠</span> COMMIT &nbsp;&nbsp;|&nbsp;&nbsp; DISCARD <span class="neq">≠</span> ROLLBACK</div>
</div>`});

/* ---------- p.26 · Lỗi sau khi đã ghi dữ liệu ---------- */
Deck.add({part:5,title:'Lỗi sau khi đã ghi dữ liệu',
html:`
<style>
.p5-err{flex:1;min-height:0;display:grid;grid-template-columns:1.3fr 1fr;gap:1.2rem}
.p5-err .card{display:flex;flex-direction:column}
.p5-err .hh b{display:block;font-size:2rem;font-weight:900;line-height:1.1}
.p5-err .hh span{display:block;font-size:1.15rem;color:var(--muted);margin-top:.2rem}
.p5-err .tbl{margin-top:1.2rem;font-size:1.5rem}
.p5-err .tbl th{font-size:.9rem;padding:.7rem .9rem}
.p5-err .tbl td{padding:1.35rem .9rem}
.p5-err td.c2{color:var(--bad);font-weight:700}
.p5-err td.c3{color:var(--ok);font-weight:700}
.p5-err th.c3{color:var(--ok)}
.p5-err .tbl td:first-child{font-weight:600}
.p5-err .tbl tr:first-child td{font-weight:800}
.p5-ft{margin-top:auto;padding-top:1rem;display:flex;gap:2rem;align-items:center;font-size:1.45rem;font-weight:800}
.p5-rep{display:grid;margin-top:1rem}
.p5-rep>div{display:grid;grid-template-columns:1fr 1fr;padding:1.35rem .9rem;border-bottom:.06rem solid var(--line);font-size:1.5rem}
.p5-rep>div:first-child{font-weight:800;font-size:1.15rem;border-bottom-width:.12rem}
.p5-rep code{font-weight:600;color:var(--ink2)}
.p5-rep b{font-family:var(--mono)}
.p5-rep .e b{color:var(--bad)}
.p5-bar .neq{font-family:var(--mono)}
.p5-bar{background:var(--brand);color:#fff;border-radius:var(--r-sm);padding:1rem 1.5rem;text-align:center;font-weight:800;font-size:1.35rem;margin-top:1rem}
</style>
${Deck.head(5,'Lỗi sau khi đã ghi dữ liệu')}
<div class="body">
  <div class="p5-err">
    <div class="card o" data-a style="--i:1">
      <div class="hh"><b class="o-t">Oracle</b><span>Checkout nhiều bảng · lỗi CHECK</span></div>
      <table class="tbl">
        <tr><th>Dữ liệu</th><th class="n">Trước</th><th class="n">Sau lỗi</th><th class="n c3">Rollback</th></tr>
        <tr><td>Order</td><td class="n">0</td><td class="n c2">1</td><td class="n c3">0</td></tr>
        <tr><td>Order Items</td><td class="n">0</td><td class="n c2">2</td><td class="n c3">0</td></tr>
        <tr><td>Cart Items</td><td class="n">3</td><td class="n c2">1</td><td class="n c3">3</td></tr>
        <tr><td>Stock P1/P2</td><td class="n">100/100</td><td class="n c2">99/99</td><td class="n c3">100/100</td></tr>
      </table>
      <div class="p5-ft"><span class="bad-t">ORA-02290</span><span class="ok-t">→ Full ROLLBACK</span></div>
    </div>
    <div class="card r" data-a style="--i:2">
      <div class="hh"><b class="r-t">Redis</b><span>MULTI/EXEC · lỗi WRONGTYPE</span></div>
      <div class="p5-rep">
        <div>Phản hồi EXEC</div>
        <div><code>HINCRBY</code><b>99</b></div>
        <div><code>HSET</code><b>2</b></div>
        <div class="e"><code>LPUSH</code><b>WRONGTYPE</b></div>
        <div><code>HDEL</code><b>1</b></div>
      </div>
      <div class="p5-ft bad-t">Stock = 99 · Order còn tồn tại</div>
    </div>
  </div>
  <div class="p5-bar" data-a style="--i:3">Thực thi không bị xen ngang <span class="neq">≠</span> khả năng hoàn tác khi lỗi</div>
</div>`});

/* ---------- p.27 · Ai chịu trách nhiệm bảo vệ dữ liệu? ---------- */
Deck.add({part:5,title:'Ai chịu trách nhiệm bảo vệ dữ liệu?',
html:`
<style>
.p5-g{flex:1;min-height:0;display:grid;grid-template-columns:1fr 1fr;gap:1.2rem}
.p5-g .card{display:flex;flex-direction:column;gap:.7rem}
.p5-g .hh b{display:block;font-size:2rem;font-weight:900;line-height:1.1}
.p5-g .hh span{display:block;font-size:1.1rem;color:var(--muted);margin-top:.15rem}
.p5-g h4{font-size:1.35rem;font-weight:800;color:var(--ink);margin:.4rem 0 0;text-transform:none;letter-spacing:0}
.p5-cons{display:grid;gap:.6rem}
.p5-cons div{padding:1rem 1.2rem;background:var(--ora-soft);border-radius:.6rem;display:grid;grid-template-columns:1fr auto;gap:.15rem 1rem}
.p5-cons b{font-size:1.4rem;color:var(--ora)}
.p5-cons code{font-size:1rem;color:var(--ink2);align-self:center}
.p5-cons span{grid-column:1/-1;font-size:1.25rem;color:var(--ink2)}
.p5-lua{display:grid;grid-template-columns:1fr auto 1fr auto 1fr;gap:.5rem;align-items:center}
.p5-lua span{text-align:center;padding:1.3rem .5rem;border-radius:.6rem;background:var(--red-soft);color:var(--red);font-weight:800;font-size:1.2rem;line-height:1.3}
.p5-lua span small{display:block;font-weight:500;color:var(--ink2);font-size:1rem}
.p5-lua i{color:var(--muted)}
.p5-case{padding:1.3rem 1.3rem;background:var(--red-soft);border-radius:.6rem;display:grid;grid-template-columns:1fr auto;gap:.3rem 1rem;font-size:1.3rem}
.p5-case b{grid-column:1/-1;font-size:1.35rem}
.p5-case code{color:var(--bad);font-weight:700}
.p5-case span{color:var(--ink2)}
.p5-end{margin-top:auto;padding:1rem 1.1rem;border-radius:.6rem;font-weight:800;font-size:1.3rem}
.p5-end.o{background:var(--ora-soft);color:var(--ora)}.p5-end.r{background:var(--red-soft);color:var(--red)}
.p5-bar .neq{font-family:var(--mono)}
.p5-bar{background:var(--brand);color:#fff;border-radius:var(--r-sm);padding:1rem 1.5rem;text-align:center;font-weight:800;font-size:1.35rem;margin-top:1rem}
</style>
${Deck.head(5,'Ai chịu trách nhiệm bảo vệ dữ liệu?')}
<div class="body">
  <div class="p5-g">
    <div class="card o" data-a style="--i:1">
      <div class="hh"><b class="o-t">Oracle</b><span>Declarative constraints</span></div>
      <h4>Thử ghi dữ liệu vi phạm constraint</h4>
      <div class="p5-cons">
        <div><b>UNIQUE</b><code>ORA-00001</code><span>Thêm sản phẩm trùng trong giỏ</span></div>
        <div><b>FOREIGN KEY</b><code>ORA-02291</code><span>Tham chiếu Order không tồn tại</span></div>
        <div><b>CHECK</b><code>ORA-02290</code><span>Đặt số lượng Cart Item = 0</span></div>
      </div>
      <div class="p5-end o">DBMS tự từ chối câu SQL vi phạm</div>
    </div>
    <div class="card r" data-a style="--i:2">
      <div class="hh"><b class="r-t">Redis</b><span>Lua scripting · logic chạy tại Redis server</span></div>
      <h4>Một lần EVAL, không bị command khác xen ngang</h4>
      <div class="p5-lua"><span>EVAL<small>Gọi script</small></span><i>${Deck.icon('arrow')}</i><span>Đọc &amp; kiểm tra<small>Cart, tồn kho</small></span><i>${Deck.icon('arrow')}</i><span>Ghi có điều kiện<small>Nếu hợp lệ</small></span></div>
      <h4>Thử Checkout qua Lua</h4>
      <div class="p5-case"><b>Yêu cầu vượt tồn khả dụng</b><code>OUT_OF_STOCK</code><span>Dữ liệu không đổi</span></div>
      <div class="p5-end r">Tiền kiểm trước ghi, không phải rollback</div>
    </div>
  </div>
  <div class="p5-bar" data-a style="--i:3">Oracle: ràng buộc tại schema &nbsp;|&nbsp; Redis: kiểm soát đường ghi</div>
</div>`});

/* ---------- p.28 · Cô lập và lưu bền dữ liệu ---------- */
Deck.add({part:5,title:'Cô lập và lưu bền dữ liệu',
html:`
<style>
.p5-id{flex:1;min-height:0;display:grid;grid-template-columns:16rem 1fr 1fr;grid-template-rows:auto 1fr 1fr;gap:1rem}
.p5-id .hh{font-size:2rem;font-weight:900;align-self:end}
.p5-id .lb{background:var(--brand);color:#fff;border-radius:var(--r-sm);padding:1.4rem 1.3rem;display:flex;flex-direction:column;justify-content:center}
.p5-id .lb b{display:block;font-size:1.5rem;font-weight:800;letter-spacing:.04em}
.p5-id .lb span{display:block;font-size:1.15rem;margin-top:.8rem;opacity:.9;line-height:1.35}
.p5-id .c{border-radius:var(--r-sm);padding:1.4rem 1.6rem;display:flex;flex-direction:column;justify-content:center;gap:.8rem}
.p5-id .c.o{background:var(--ora-soft)}.p5-id .c.r{background:var(--red-soft)}
.p5-id .c .t{font-size:1.6rem;font-weight:800}
.p5-id .c.o .t{color:var(--ora)}.p5-id .c.r .t{color:var(--red)}
.p5-id .c .big{font-size:2.6rem;font-weight:900;color:var(--ink);font-variant-numeric:tabular-nums}
.p5-id .c .big.t{font-size:2.4rem}
.p5-id .c p{font-size:1.25rem;color:var(--ink2)}
</style>
${Deck.head(5,'Cô lập và lưu bền dữ liệu')}
<div class="body">
  <div class="p5-id">
    <div></div><div class="hh o-t" data-a style="--i:1">Oracle</div><div class="hh r-t" data-a style="--i:1">Redis</div>
    <div class="lb" data-a style="--i:2"><b>ISOLATION</b><span>Kiểm soát truy cập đồng thời</span></div>
    <div class="c o" data-a style="--i:3"><div class="t">READ COMMITTED</div><div class="big">100 → 100 → 99</div><p>Không đọc ghi chưa commit</p></div>
    <div class="c r" data-a style="--i:4"><div class="t">WATCH / MULTI / EXEC</div><div class="big">Abort → Retry</div><p>Phát hiện key đã thay đổi</p></div>
    <div class="lb" data-a style="--i:5"><b>DURABILITY</b><span>Bảo toàn thay đổi đã xác nhận</span></div>
    <div class="c o" data-a style="--i:6"><div class="big t">Redo Log</div><p>Chính sách COMMIT</p></div>
    <div class="c r" data-a style="--i:7"><div class="big t">RDB / AOF</div><p>Môi trường đo: AOF everysec</p></div>
  </div>
</div>`});

/* ---------- p.29 · Hiệu năng trong workload đã khảo sát ---------- */
Deck.add({part:5,title:'Hiệu năng trong workload đã khảo sát',
html:`
<style>
.p5-sub{font-size:1.25rem;color:var(--ink2)}
.p5-perf{flex:1;min-height:0;display:grid;grid-template-columns:1fr 1fr;gap:1.2rem;margin-top:1rem}
.p5-perf .card{display:flex;flex-direction:column}
.p5-perf h3{font-size:1.8rem}
.p5-perf .u{font-size:1.1rem;color:var(--muted)}
.p5-grp{flex:1;display:grid;gap:1.6rem;align-content:center;margin-top:1rem}
.p5-grp .g{display:grid;grid-template-columns:8rem 1fr;gap:1rem;align-items:center}
.p5-grp .g>b{font-size:1.3rem;font-weight:700}
.p5-grp .bs{display:grid;gap:.45rem}
.p5-b{display:flex;align-items:center;gap:.6rem;font-family:var(--mono);font-weight:700;font-size:1.2rem}
.p5-b i{display:block;height:1.8rem;width:0;border-radius:.25rem;transition:width 1.1s var(--ease-out)}
.p5-b.o i{background:var(--ora)}.p5-b.r i{background:var(--red)}
.p5-b.o{color:var(--ora)}.p5-b.r{color:var(--red)}
.p5-leg{display:flex;gap:2rem;align-items:center;margin-top:1rem;font-size:1.1rem;color:var(--ink2)}
.p5-leg span{display:inline-flex;align-items:center;gap:.5rem;font-weight:700}
.p5-leg span i{width:1.1rem;height:1.1rem;border-radius:.2rem}
</style>
${Deck.head(5,'Hiệu năng trong workload đã khảo sát')}
<div class="body">
  <div class="p5-sub" data-a style="--i:1">Checkout mô phỏng trực tiếp · Giỏ 1 hoặc 5 sản phẩm · 1 hoặc 10 worker</div>
  <div class="p5-perf">
    <div class="card" data-a style="--i:2"><h3>P95 latency</h3><div class="u">ms · thấp hơn tốt hơn</div><div class="p5-grp" data-k="p95"></div></div>
    <div class="card" data-a style="--i:3"><h3>Throughput</h3><div class="u">TPS · cao hơn tốt hơn</div><div class="p5-grp" data-k="tps"></div></div>
  </div>
  <div class="p5-leg" data-a style="--i:4"><span><i style="background:var(--ora)"></i>Oracle</span><span><i style="background:var(--red)"></i>Redis</span></div>
</div>`,
enter(root,ctx){
  const G=['1 sản phẩm<br>1 worker','1 sản phẩm<br>10 worker','5 sản phẩm<br>1 worker','5 sản phẩm<br>10 worker'];
  const D={p95:{o:['1,34','3,01','2,64','6,12'],r:['0,21','0,70','0,46','1,40']},
           tps:{o:['847','3.503','405','1.754'],r:['4.517','12.289','1.788','5.439']}};
  const num=s=>+s.replace(/\./g,'').replace(',','.');
  Object.entries(D).forEach(([k,d])=>{
    const max=Math.max(...d.o.map(num),...d.r.map(num));
    const el=root.querySelector(`[data-k="${k}"]`);
    el.innerHTML=G.map((g,i)=>`<div class="g"><b>${g}</b><div class="bs">
      <div class="p5-b o"><i data-w="${num(d.o[i])/max*78}"></i>${d.o[i]}</div>
      <div class="p5-b r"><i data-w="${num(d.r[i])/max*78}"></i>${d.r[i]}</div></div></div>`).join('');
  });
  const bars=[...root.querySelectorAll('.p5-b i')];
  const go=()=>bars.forEach((b,i)=>{b.style.transitionDelay=(i%8*.06)+'s';b.style.width=Math.max(.6,+b.dataset.w)+'%'});
  if(ctx&&ctx.print){bars.forEach(b=>b.style.transition='none');go()}else setTimeout(()=>requestAnimationFrame(go),500);
}});

/* ---------- p.30 · Lựa chọn DBMS cho ShopFlash ---------- */
Deck.add({part:5,title:'Lựa chọn DBMS cho ShopFlash',
html:`
<style>
.p5-tree{flex:1;min-height:0;display:flex;flex-direction:column}
.p5-root{align-self:center;background:var(--brand);color:#fff;font-weight:800;font-size:1.6rem;letter-spacing:.06em;padding:1rem 3rem;border-radius:var(--r-sm);box-shadow:var(--shadow)}
.p5-conn{position:relative;height:3.2rem}
.p5-conn i{position:absolute;background:var(--brand2)}
.p5-conn .v0{left:50%;top:0;width:.22rem;height:50%;transform:translateX(-50%)}
.p5-conn .h{left:calc(25% - .3rem);right:calc(25% - .3rem);top:50%;height:.22rem}
.p5-conn .v1,.p5-conn .v2{top:50%;width:.22rem;height:50%}
.p5-conn .v1{left:calc(25% - .3rem)}.p5-conn .v2{right:calc(25% - .3rem)}
.p5-two{flex:1;min-height:0;display:grid;grid-template-columns:1fr 1fr;gap:1.2rem}
.p5-two .card{display:flex;flex-direction:column;justify-content:center;gap:.6rem;padding:2rem 2.4rem;border-top-width:.5rem}
.p5-two .card.o{background:var(--ora-soft)}.p5-two .card.r{background:var(--red-soft)}
.p5-two h3{font-size:4rem!important;font-weight:900;line-height:1}
.p5-two .role{font-size:2.1rem;font-weight:800;color:var(--ink)}
.p5-two .data{font-size:1.85rem;color:var(--ink2)}
.p5-two hr{border:0;border-top:.12rem solid rgba(13,24,56,.12);margin:.8rem 0}
.p5-two .mech{font-size:1.9rem;font-weight:800}
.p5-bar .neq{font-family:var(--mono)}
.p5-bar{background:var(--brand);color:#fff;border-radius:var(--r-sm);padding:1.1rem 1.5rem;text-align:center;font-weight:800;font-size:1.45rem;margin-top:1.2rem}
</style>
${Deck.head(5,'Lựa chọn DBMS cho ShopFlash')}
<div class="body">
  <div class="p5-tree">
    <div class="p5-root" data-a style="--i:1">SHOPFLASH BACKEND</div>
    <div class="p5-conn" data-a style="--i:2"><i class="v0"></i><i class="h"></i><i class="v1"></i><i class="v2"></i></div>
    <div class="p5-two">
      <div class="card o" data-a style="--i:3"><h3 class="o-t">Oracle</h3><div class="role">Source of truth</div><div class="data">Cart · Order · Inventory</div><hr><div class="mech o-t">Rollback + Constraint + Row lock</div></div>
      <div class="card r" data-a style="--i:4"><h3 class="r-t">Redis</h3><div class="role">Flash Sale runtime</div><div class="data">Quota · Reservation</div><hr><div class="mech r-t">Atomic command + Lua</div></div>
    </div>
  </div>
  <div class="p5-bar" data-a style="--i:5">Ưu tiên tính đúng đắn cho dữ liệu chính, tốc độ cho runtime.</div>
</div>`});
