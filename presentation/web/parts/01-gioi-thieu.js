/* Phần 1 · Giới thiệu đề tài và kiến trúc (3 phút). Nguồn: Report/Sections/Introduction. */
Deck.opener({part:1,title:'Giới thiệu đề tài'});

/* Canva trang 4. Câu trả lời lấy từ phần [Điền: …] của Canva. */
Deck.add({part:1,title:'Bài toán và mục tiêu',
html:`
<style>
.bt4{flex:1;min-height:0;margin-top:1.4rem;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:1.2rem}
.bt4 .card{display:flex;flex-direction:column;justify-content:center;gap:1rem;padding:2rem 2.4rem;border-left:.4rem solid var(--brand)}
.bt4 h3{font-size:2.2rem;color:var(--brand)}
.bt4 p{font-size:1.75rem;line-height:1.45;color:var(--ink)}
</style>
${Deck.head(1,'Bài toán và mục tiêu')}
<div class="bt4">
  <div class="card" data-a style="--i:1"><h3>Đề tài nghiên cứu điều gì?</h3><p>So sánh Oracle và Redis trên sáu chủ đề của môn học.</p></div>
  <div class="card" data-a style="--i:2"><h3>ShopFlash là gì?</h3><p>Ứng dụng bán hàng có Flash Sale, dùng làm bối cảnh thực nghiệm.</p></div>
  <div class="card" data-a style="--i:3"><h3>Mục tiêu so sánh?</h3><p>Cơ chế hoạt động, kết quả đo, DBMS nào phù hợp với loại dữ liệu nào.</p></div>
  <div class="card" data-a style="--i:4"><h3>Phạm vi triển khai?</h3><p>Môi trường local, không hướng tới production.</p></div>
</div>`});

Deck.add({part:1,title:'Kiến trúc tổng thể',
html:`
<style>
.flows{display:grid;grid-template-rows:repeat(3,1fr);gap:1rem;min-height:0}
.fl{display:flex;flex-direction:column;justify-content:center;gap:.5rem;padding:1.3rem 1.6rem;border-left:.4rem solid var(--brand)}
.fl.o{border-left-color:var(--ora)}.fl.r{border-left-color:var(--red)}.fl.a{border-left-color:var(--accent)}
.fl h3{font-size:1.6rem}.fl p{font-size:1.2rem;line-height:1.5;color:var(--ink2)}
.arch{display:grid;grid-template-columns:58rem 1fr;gap:1.6rem;flex:1;min-height:0}
.dg{position:relative;width:58rem;height:38rem;align-self:center}
.dg svg{position:absolute;inset:0;width:100%;height:100%;overflow:visible}
.dg path{fill:none;stroke:#8f9bb8;stroke-width:2.4;transition:stroke .4s,stroke-width .4s}
.dg path.on{stroke:var(--brand);stroke-width:3.4;stroke-dasharray:8 6;animation:flow 1s linear infinite}
.dg path.r{stroke:var(--red)}.dg path.o{stroke:var(--ora)}
.dg path.on.r{stroke:var(--red)}.dg path.on.o{stroke:var(--ora)}
@keyframes flow{to{stroke-dashoffset:-28}}
.dg text{font-size:10px;font-family:var(--mono);fill:var(--muted)}
.nd{position:absolute;display:grid;place-items:center;text-align:center;background:var(--surface);border-radius:.8rem;box-shadow:var(--shadow);font-weight:700;font-size:.95rem;line-height:1.25;transition:box-shadow .4s,transform .4s,opacity .4s}
.nd small{display:block;font-weight:500;font-size:.72rem;color:var(--muted)}
.dg.focus .nd{opacity:.45}
.dg.focus .nd.on{opacity:1;box-shadow:0 0 0 .16rem var(--brand),var(--shadow)}
.db{position:absolute;border-radius:var(--r);padding:1rem 1.2rem;color:#fff;box-shadow:var(--shadow);transition:opacity .4s,transform .4s}
.db .chip{background:rgba(255,255,255,.18)}
.db ul{list-style:none;margin-top:.5rem;font-size:.85rem;line-height:1.5}
.db.o{background:linear-gradient(135deg,var(--ora),#2b5fe8)}.db.r{background:linear-gradient(135deg,#c42d23,var(--red))}
.dg.focus .db{opacity:.4}.dg.focus .db.on{opacity:1;transform:translateY(-.2rem)}
.flows .fl{transition:opacity .4s,transform .4s,box-shadow .4s}
.flows.focus .fl{opacity:.4}
.flows.focus .fl.on{opacity:1;transform:translateX(-.4rem);box-shadow:0 0 0 .14rem var(--brand),var(--shadow)}
.flowtxt h3{font-size:1.6rem;margin:.6rem 0 .4rem}
.flowtxt p{font-size:1.1rem;color:var(--ink2);line-height:1.55}
.flowtxt .list{margin-top:.8rem;font-size:1.1rem}
.flowtxt{display:flex;flex-direction:column}
.flowtxt .kpi{margin-top:auto;background:var(--surface2);border-radius:var(--r-sm);padding:1rem 1.1rem}
.flowtxt .kpi b{font-size:2.8rem}
.flowtxt .see{display:flex;gap:.5rem;align-items:center;margin-top:1rem;font-size:.85rem;color:var(--muted);font-weight:600}
.flowtxt .see button{padding:.45rem .8rem;border-radius:.6rem;background:var(--surface2);font-weight:700;color:var(--brand);font-size:.85rem}
.flowtxt .see button:hover{background:var(--brand);color:#fff}
</style>
${Deck.head(1,'Kiến trúc tổng thể')}
<div class="arch body">
  <div class="dg" id="dg" data-a style="--i:1">
    <svg viewBox="0 0 580 380" preserveAspectRatio="none">
      <path data-p="u-fe" d="M290 34 V62"/>
      <path data-p="fe-be" d="M290 102 V132"/>
      <path data-p="be-m1" d="M290 172 V192 H95 V215"/>
      <path data-p="be-m2" d="M290 172 V215"/>
      <path data-p="be-m3" d="M290 172 V192 H485 V215"/>
      <path data-p="m1-o" class="o" d="M95 265 V300"/>
      <path data-p="m2-o" class="o" d="M262 265 V300"/>
      <path data-p="m2-r" class="r" d="M318 265 V300"/>
      <path data-p="m3-o" class="o" d="M440 265 V286 H222 V300"/>
      <path data-p="m3-r" class="r" d="M500 265 V300"/>
      <text x="296" y="122">REST / JSON</text>
      <text x="100" y="290">JDBC</text><text x="226" y="294">JDBC</text><text x="324" y="294">Jedis</text><text x="506" y="290">Jedis · Lua</text>
    </svg>
    <div class="nd" data-n="u" style="left:21.5rem;top:0;width:15rem;height:3.4rem">Người dùng</div>
    <div class="nd" data-n="fe" style="left:19rem;top:6.2rem;width:20rem;height:4rem">Frontend<small>React + Vite</small></div>
    <div class="nd" data-n="be" style="left:19rem;top:13.2rem;width:20rem;height:4rem">Backend<small>Java 21 · Spring Boot</small></div>
    <div class="nd" data-n="m1" style="left:1rem;top:21.5rem;width:17rem;height:5rem">Giỏ hàng · Đặt hàng<small>Cart, Checkout, Order</small></div>
    <div class="nd" data-n="m2" style="left:20.5rem;top:21.5rem;width:17rem;height:5rem">Tìm kiếm · Báo cáo<small>Search, Reports</small></div>
    <div class="nd" data-n="m3" style="left:40rem;top:21.5rem;width:17rem;height:5rem">Flash Sale<small>giữ suất, đặt đơn</small></div>
    <div class="db o" data-n="o" style="left:1rem;top:30rem;width:26rem;height:8rem"><span class="chip" data-morph="ora">ORACLE · DỮ LIỆU GỐC</span><ul><li>User, Product, Inventory, Cart, Order</li><li>Cấu hình và số đã bán của Flash Sale</li></ul></div>
    <div class="db r" data-n="r" style="left:31rem;top:30rem;width:26rem;height:8rem"><span class="chip" data-morph="red">REDIS · BẢN CHIẾU, TRẠNG THÁI CHẠY</span><ul><li>Bản chiếu sản phẩm, read model báo cáo</li><li>Suất Flash Sale còn lại, suất đang giữ</li></ul></div>
  </div>
  <div class="flows" data-a style="--i:2">
    <div class="card fl o"><h3>Checkout thường</h3><p>Tạo Order, ghi Order Items, trừ Inventory, xóa Cart trong <b>một transaction Oracle</b>. Giá và tồn kho đọc thẳng từ Oracle.</p></div>
    <div class="card fl r"><h3>Tìm kiếm và báo cáo</h3><p>Redis giữ <b>bản chiếu</b> dựng từ Oracle: bản chiếu sản phẩm để tìm kiếm, read model cho báo cáo. Có thể trễ hơn Oracle.</p></div>
    <div class="card fl a"><h3>Flash Sale</h3><p>Redis + Lua <b>giữ suất</b>; Oracle <b>ghi đơn và trừ kho</b>. Hai DBMS không có transaction chung nên bù trừ theo Try–Confirm–Cancel.</p></div>
  </div>
</div>`,
/* Tự chạy lần lượt ba luồng, mỗi luồng 3 giây; bản in giữ sơ đồ tĩnh. */
enter(root,{print}){
  if(print)return;
  const F=[{p:['u-fe','fe-be','be-m1','m1-o'],n:['u','fe','be','m1','o']},
           {p:['u-fe','fe-be','be-m2','m2-o','m2-r'],n:['u','fe','be','m2','o','r']},
           {p:['u-fe','fe-be','be-m3','m3-o','m3-r'],n:['u','fe','be','m3','o','r']}];
  const dg=root.querySelector('#dg'),fl=root.querySelector('.flows'),cards=fl.querySelectorAll('.fl');
  let k=0;
  const show=()=>{const f=F[k];dg.classList.add('focus');fl.classList.add('focus');
    dg.querySelectorAll('path').forEach(e=>e.classList.toggle('on',f.p.includes(e.dataset.p)));
    dg.querySelectorAll('[data-n]').forEach(e=>e.classList.toggle('on',f.n.includes(e.dataset.n)));
    cards.forEach((c,i)=>c.classList.toggle('on',i===k));k=(k+1)%F.length};
  clearInterval(root._t);root._t0=setTimeout(()=>{show();root._t=setInterval(show,3000)},1200);
},
leave(root){clearTimeout(root._t0);clearInterval(root._t);
  root.querySelectorAll('.on').forEach(e=>e.classList.remove('on'));
  root.querySelectorAll('.focus').forEach(e=>e.classList.remove('focus'))}});

Deck.add({part:1,title:'Phân chia dữ liệu Oracle – Redis',
html:`
<style>
.split .tbl td{font-size:1.25rem;padding:1rem 1rem}
.split>.card{flex:1;display:flex;min-height:0}.split .tbl{height:100%}
.split .take p{font-size:1.3rem}
.split .tbl td:first-child{width:15rem}
.split .tbl th .chip{font-size:.72rem}
</style>
${Deck.head(1,'Phân chia dữ liệu giữa Oracle và Redis')}
<div class="body split">
  <div class="card" data-a style="--i:1">
    <table class="tbl">
      <tr><th>Nhóm dữ liệu</th><th><span class="chip o" data-morph="ora">ORACLE</span></th><th><span class="chip r" data-morph="red">REDIS</span></th></tr>
      <tr><td>Sản phẩm, tồn kho</td><td>Product, Category, Inventory: trạng thái chính thức</td><td>Bản chiếu sản phẩm để tìm kiếm, lọc, hiển thị</td></tr>
      <tr><td>Giỏ hàng, đơn hàng</td><td>Cart, Order, Order Items; transaction đặt và hủy đơn</td><td>Bản dẫn xuất từ Order cho thống kê, báo cáo</td></tr>
      <tr><td>Flash Sale</td><td>Cấu hình chương trình, số suất, số đã bán, đơn thành công</td><td>Suất còn lại, giới hạn mỗi khách, suất giữ tạm</td></tr>
      <tr><td>Truy vấn, báo cáo</td><td>Dữ liệu gốc, truy vấn quan hệ</td><td>Index tìm kiếm, cache, read model cho báo cáo</td></tr>
    </table>
  </div>
  <div class="grid g3" style="margin-top:1rem">
    <div class="take" data-a style="--i:2"><div class="label">Oracle</div><p>Giữ dữ liệu gốc. Đặt hàng, trừ kho, ghi số đã bán đều ghi vào Oracle trước.</p></div>
    <div class="take soft" data-a style="--i:3"><div class="label">Redis</div><p>Mất dữ liệu thì dựng lại từ Oracle: bản chiếu, suất còn lại = cấp − đã bán. Suất đang giữ dở thì mất, khách phải giữ lại.</p></div>
    <div class="take soft" data-a style="--i:4"><div class="label">Độ trễ</div><p>Bản chiếu có thể chậm hơn Oracle. Giá và tồn kho lúc đặt hàng đọc thẳng Oracle.</p></div>
  </div>
</div>`});
