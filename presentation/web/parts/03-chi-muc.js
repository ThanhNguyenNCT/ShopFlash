/* Phần 3 · Chỉ mục (5 phút). Nội dung chép nguyên văn từ deck Canva, trang 11–21; mỗi trang Canva là một cảnh. */
(function(){
/* thanh nhỏ dưới số đo trong bảng, thang log (chỉ là hình minh họa số của Canva, không thêm chữ) */
const bar=(v,db,lo,hi)=>{const w=Math.max(2,(Math.log10(v)-Math.log10(lo))/(Math.log10(hi)-Math.log10(lo))*100);return `<span class="p3-bar ${db}"><i style="width:${w.toFixed(1)}%"></i></span>`};

Deck.opener({part:3,title:'Chỉ mục (Indexing)'});

/* ---------- p.12 ---------- */
Deck.add({part:3,title:'Cấu trúc index & cơ chế optimizer trong Oracle',html:`
<style>
/* dùng chung cho cả phần 3 */
.p3-tb{width:100%;border-collapse:separate;border-spacing:0}
.p3-tb th{background:var(--ora);color:#fff;font-size:1.05rem;font-weight:700;text-align:left;padding:1rem 1.2rem}
.p3-tb th:first-child{border-top-left-radius:.7rem}.p3-tb th:last-child{border-top-right-radius:.7rem}
.p3-tb td{font-size:1.3rem;padding:1.05rem 1.2rem;border-bottom:.06rem solid var(--line);color:var(--ink2);vertical-align:middle}
.p3-tb tr:nth-child(even) td{background:var(--surface2)}
.p3-tb tr:last-child td{border-bottom:0}
.p3-tb td:first-child{font-weight:700;color:var(--ink)}
.p3-tb td.o{color:var(--ora)}.p3-tb td.r{color:var(--red)}
.p3-tb tr.hl td{background:var(--ora-soft)}
.p3-tb tr.bad td{background:var(--bad-soft)}
.p3-tb .num{font-variant-numeric:tabular-nums}
.p3-bar{display:block;height:.5rem;background:var(--surface3);border-radius:99rem;margin-top:.45rem;overflow:hidden}
.p3-bar i{display:block;height:100%;border-radius:99rem;background:var(--off)}
.p3-bar.o i{background:var(--ora)}.p3-bar.r i{background:var(--red)}
.p3-foot{margin-top:1.2rem;background:var(--surface2);border-radius:var(--r);padding:1.2rem 1.6rem;text-align:center;font-size:1.4rem;font-weight:700;color:var(--ink)}
.p3-foot.o{background:var(--ora-soft)}
.p3-card h3{font-size:1.95rem;font-weight:800;display:flex;align-items:center;gap:.7rem}
.p3-card p{font-size:1.55rem;line-height:1.45;margin-top:.5rem}
.p3-ico{width:1.8rem;height:1.8rem;image-rendering:auto;flex:none}
/* p.12 */
.p3-12{display:grid;grid-template-columns:1fr 1.15fr;gap:1.4rem;flex:1;min-height:0}
.p3-12 .left{display:grid;grid-template-rows:repeat(3,1fr);gap:1.2rem;min-height:0}
.p3-12 .left .card{display:flex;flex-direction:column;justify-content:center;padding:1.4rem 1.8rem}
.p3-cbo{background:var(--ora-soft);border:.12rem solid rgba(29,78,216,.25);display:flex;flex-direction:column;justify-content:center;gap:2.6rem;padding:2.4rem 2.6rem}
.p3-cbo h3{font-size:2.5rem}
.p3-cbo .p3-ico{width:2.4rem;height:2.4rem}
.p3-cbo p{font-size:1.9rem;line-height:1.5;margin:0}
.p3-cbo p b{color:var(--ora)}
</style>
${Deck.head(3,'Cấu trúc index & cơ chế optimizer trong Oracle')}
<div class="body">
  <div class="p3-12">
    <div class="left">
      <div class="card p3-card" data-a style="--i:1"><h3><img class="p3-ico" src="assets/canva/p12-icon2.png" alt="">B-Tree Index (Mặc định)</h3><p>Cây cân bằng ánh xạ khóa tới địa chỉ vật lý ROWID.</p></div>
      <div class="card p3-card" data-a style="--i:2"><h3><img class="p3-ico" src="assets/canva/p12-icon3.png" alt="">Bitmap &amp; Function-Based</h3><p>Chuỗi bit cho cột ít giá trị phân biệt; lưu sẵn kết quả hàm.</p></div>
      <div class="card p3-card" data-a style="--i:3"><h3><img class="p3-ico" src="assets/canva/p12-icon4.png" alt="">Composite Index</h3><p>Gộp nhiều cột; ưu tiên cột dẫn đầu trong điều kiện lọc.</p></div>
    </div>
    <div class="card p3-card p3-cbo" data-a style="--i:4">
      <h3><img class="p3-ico" src="assets/canva/p12-icon1.png" alt="">Cost-Based Optimizer (CBO)</h3>
      <p><b>Ước lượng chi phí:</b> Tính toán I/O và CPU dựa trên thống kê dữ liệu.</p>
      <p><b>Selectivity &amp; Clustering Factor:</b> Cân nhắc giữa Index Scan và Full Table Scan.</p>
    </div>
  </div>
</div>`});

/* ---------- p.13 ---------- */
Deck.add({part:3,title:'Index trong Redis',html:`
<style>
.p3-13{display:grid;grid-template-rows:auto 1fr auto;gap:1.4rem;flex:1;min-height:0}
.p3-13 .lead13{background:var(--surface);border-left:.45rem solid var(--red);border-radius:var(--r);box-shadow:var(--shadow);padding:1.8rem 2.2rem;font-size:1.95rem;font-weight:700;line-height:1.4;color:var(--ink)}
.p3-13 .two{display:grid;grid-template-columns:1fr 1fr;gap:1.4rem;min-height:0}
.p3-13 .two .card{display:flex;flex-direction:column;justify-content:center;padding:2rem 2.4rem}
.p3-13 .two h3{font-size:2.8rem}
.p3-13 .two h3::before{content:'';width:1.3rem;height:1.3rem;border-radius:.2rem;background:var(--red);flex:none}
.p3-13 .two p{font-size:2.05rem;margin-top:1rem}
.p3-13 .no{background:var(--red-soft);border-radius:var(--r);padding:1.8rem 2.2rem;font-size:1.75rem;font-weight:700;color:var(--ink);line-height:1.4}
</style>
${Deck.head(3,'Index trong Redis')}
<div class="body">
  <div class="p3-13">
    <div class="lead13" data-a style="--i:1">Keyspace mặc định dùng Hash Table trên RAM (truy xuất O(1) theo Key), không tự đánh Index cho Value.</div>
    <div class="two">
      <div class="card r p3-card" data-a style="--i:2"><h3>RediSearch Engine</h3><p>Inverted Index tự động trên Hash và RedisJSON.</p></div>
      <div class="card r p3-card" data-a style="--i:3"><h3>Manual Set &amp; ZSet</h3><p>Set tìm chính xác; Sorted Set truy vấn khoảng số.</p></div>
    </div>
    <div class="no" data-a style="--i:4">Redis không có Optimizer tự chọn: mỗi đường truy vấn phải được thiết kế và duy trì chủ động.</div>
  </div>
</div>`});

/* ---------- p.14 ---------- */
Deck.add({part:3,title:'So sánh cơ chế indexing: Oracle vs. Redis',html:`
<style>
.p3-14{flex:1;display:flex;flex-direction:column;justify-content:center;min-height:0}
.p3-14 .p3-tb td{font-size:1.5rem;padding:2.1rem 1.4rem;white-space:nowrap}
.p3-14 .p3-tb th{font-size:1.2rem;padding:1.1rem 1.4rem}
.p3-14 .p3-tb th.o{color:#cfe0ff}.p3-14 .p3-tb th.r{color:#ffd1cc}
.p3-14 .p3-tb td:first-child{width:19rem}
</style>
${Deck.head(3,'So sánh cơ chế indexing: Oracle vs. Redis')}
<div class="body">
  <div class="p3-14">
    <div class="card" data-a style="--i:1;padding:0;overflow:hidden">
      <table class="p3-tb">
        <tr><th>Tiêu chí</th><th class="o">Oracle Database</th><th class="r">Redis</th></tr>
        <tr><td>Cấu trúc cốt lõi</td><td>B-Tree, Bitmap, Function-Based</td><td>Hash Table, Inverted Index, ZSet</td></tr>
        <tr><td>Quyết định truy vấn</td><td>Cost-Based Optimizer tự chọn tự động</td><td>Lập trình viên chỉ định lệnh trực tiếp</td></tr>
        <tr><td>Cập nhật đồng bộ</td><td>Tự động và ACID theo Transaction</td><td>Tự động (RediSearch) hoặc Lua thủ công</td></tr>
        <tr><td>Mục tiêu tối ưu</td><td>Giảm thiểu Disk I/O trên dữ liệu lớn</td><td>Độ trễ cực thấp trên RAM, tốn bộ nhớ</td></tr>
      </table>
    </div>
    <div class="p3-foot" data-a style="--i:2;font-size:1.5rem;padding:1.5rem 1.8rem">Oracle tối ưu truy vấn đa điều kiện phức tạp nhờ CBO; Redis tối ưu tốc độ đọc O(1) và lọc nhanh trên RAM.</div>
  </div>
</div>`});

/* ---------- p.15 ---------- */
Deck.add({part:3,title:'Tìm chính xác theo mã SKU',
demo:{label:'Tìm kiếm',path:'/search'},
html:`
<style>
.p3-15{flex:1;display:flex;flex-direction:column;justify-content:center;gap:1.4rem;min-height:0}
.p3-15 .p3-tb td{font-size:1.6rem;padding:2rem 1.3rem}
.p3-15 .p3-tb th{font-size:1.15rem;padding:1.1rem 1.3rem}
.p3-15 .p3-tb td.best{font-weight:800;color:var(--ink)}
.p3-15 .two{display:grid;grid-template-columns:1fr 1fr;gap:1.4rem}
.p3-15 .two div{border-radius:var(--r);padding:1.8rem 2rem;font-size:1.5rem;line-height:1.45;color:var(--ink2)}
.p3-15 .two b{color:var(--ink)}
</style>
${Deck.head(3,'Tìm chính xác theo mã SKU')}
<div class="body">
  <div class="p3-15">
    <div class="card" data-a style="--i:1;padding:0;overflow:hidden">
      <table class="p3-tb">
        <tr><th>Cách tìm (trung bình 5 lần)</th><th>Không index</th><th>Có index</th><th>Dung lượng index</th></tr>
        <tr><td class="o">Oracle: B-tree unique</td><td class="num">3,214 ms (1.754 block)${bar(3.214,'m',.001,1000)}</td><td class="num best">0,006 ms (4 block)${bar(.006,'o',.001,1000)}</td><td class="num">5 MB (đĩa)</td></tr>
        <tr><td class="r">Redis: key tự làm</td><td class="num">382,03 ms (SCAN)${bar(382.03,'m',.001,1000)}</td><td class="num best">0,0035 ms${bar(.0035,'r',.001,1000)}</td><td class="num">12,2 MB (RAM)</td></tr>
        <tr><td class="r">Redis: RediSearch TAG</td><td class="num">382,03 ms (SCAN)${bar(382.03,'m',.001,1000)}</td><td class="num best">0,0097 ms${bar(.0097,'r',.001,1000)}</td><td class="num">49,5 MB (RAM)</td></tr>
      </table>
    </div>
    <div class="two" data-a style="--i:2">
      <div style="background:var(--surface2)"><b>Khi thiếu index:</b> Oracle quét đĩa nhanh hơn Redis SCAN <b>119 lần</b> (do Redis gọi lẻ từng JSON.GET).</div>
      <div style="background:var(--ora-soft)"><b>ShopFlash:</b> Chọn <b>Oracle B-tree unique</b> vì đạt mức vài µs và là dữ liệu gốc.</div>
    </div>
  </div>
</div>`});

/* ---------- p.16 ---------- */
const KW=[
  ['\'615\' (200)',[37.02,1.00,0.40,424.33,0.056],['37,02 ms','1,00 ms','0,40 ms','424,33 ms','0,056 ms']],
  ['\'sony pro\' (1.679)',[33.69,1.20,1.66,435.48,0.908],['33,69 ms','1,20 ms','1,66 ms','435,48 ms','0,908 ms']],
  ['\'pro\' (20.159)',[34.19,6.36,5.64,430.67,0.709],['34,19 ms','6,36 ms','5,64 ms','430,67 ms','0,709 ms']]];
const kwDb=['m','o','o','m','r'];
Deck.add({part:3,title:'Tìm từ khóa trong tên sản phẩm',
demo:{label:'Tìm kiếm',path:'/search'},
html:`
<style>
.p3-16{flex:1;display:flex;flex-direction:column;justify-content:center;gap:1.4rem;min-height:0}
.p3-16 .p3-tb td{font-size:1.6rem;padding:2.1rem 1.2rem}
.p3-16 .p3-tb th{font-size:1.15rem;padding:1.1rem 1.2rem}
.p3-16 .p3-tb td.rs{color:var(--red);font-weight:800}
</style>
${Deck.head(3,'Tìm từ khóa trong tên sản phẩm')}
<div class="body">
  <div class="p3-16">
    <div class="card" data-a style="--i:1;padding:0;overflow:hidden">
      <table class="p3-tb">
        <tr><th>Từ khóa (kết quả)</th><th>Oracle LIKE</th><th>CONTEXT</th><th>CTXCAT</th><th>Redis SCAN</th><th>RediSearch</th></tr>
        ${KW.map(r=>`<tr><td>${r[0]}</td>${r[1].map((v,i)=>`<td class="num${i===4?' rs':''}">${r[2][i]}${bar(v,kwDb[i],.01,1000)}</td>`).join('')}</tr>`).join('')}
      </table>
    </div>
    <div class="p3-foot" data-a style="--i:2;background:var(--red-soft);font-size:1.42rem;padding:1.5rem 1.8rem">RediSearch nhanh hơn Oracle Text CONTEXT 1,3–18 lần; ShopFlash chọn RediSearch chính và CONTEXT dự phòng.</div>
  </div>
</div>`});

/* ---------- p.17 ---------- */
Deck.add({part:3,title:'Lọc, sắp xếp giá & phân trang',
demo:{label:'Tìm kiếm',path:'/search'},
html:`
<style>
.p3-17{flex:1;display:flex;flex-direction:column;gap:1rem;min-height:0}
.p3-17 .intro{font-size:1.12rem;line-height:1.5;color:var(--ink2)}
.p3-17 .intro p+p{margin-top:.35rem}
.p3-17 .intro b{color:var(--ink)}
.p3-17 .p3-tb td{font-size:1.3rem;padding:.95rem 1.2rem}
.p3-17 .p3-tb th{font-size:1.05rem;padding:.85rem 1.2rem}
.p3-17 .p3-tb td:first-child{font-weight:500;color:var(--ink2)}
.p3-17 .p3-tb tr.hl td{background:var(--ora-soft)}
.p3-17 .p3-tb tr.hl td:first-child,.p3-17 .p3-tb td.w{font-weight:800;color:var(--ora)}
.p3-17 .p3-tb td.r{font-weight:700;color:var(--red)}
.p3-17 .p3-foot{margin-top:0;background:var(--ora-soft)}
</style>
${Deck.head(3,'Lọc, sắp xếp giá & phân trang')}
<div class="body">
  <div class="p3-17">
    <div class="intro" data-a style="--i:1">
      <p>Index ghép đúng thứ tự: (category_id, status, price). Ghép sai thứ tự: (price, category_id, status). Mỗi lần tìm = trang đầu 24 sản phẩm + đếm tổng.</p>
    </div>
    <div class="card" data-a style="--i:2;padding:0;overflow:hidden">
      <table class="p3-tb">
        <tr><th>Cấu hình Index</th><th>Danh mục 5 + giá 1–5 triệu + đang bán<br>(321 kết quả)</th><th>Chỉ giá 1–5 triệu<br>(15.218)</th><th>Chỉ danh mục 5<br>(4.000)</th></tr>
        <tr><td>Oracle: không index</td><td class="num">4,941 ms</td><td class="num">6,712 ms</td><td class="num">4,585 ms</td></tr>
        <tr><td>Oracle: 2 index đơn</td><td class="num">0,582 ms</td><td class="num">2,952 ms</td><td class="num">0,679 ms</td></tr>
        <tr class="hl"><td>Oracle: ghép đúng (cat, status, price)</td><td class="num w">0,024 ms</td><td class="num">6,878 ms (quét bảng)</td><td class="num w">0,101 ms</td></tr>
        <tr><td>Oracle: ghép sai thứ tự (price đầu)</td><td class="num">0,484 ms</td><td class="num">0,508 ms</td><td class="num">2,837 ms</td></tr>
        <tr><td class="r">Redis: RediSearch</td><td class="num">1,041 ms</td><td class="num">2,990 ms</td><td class="num">1,023 ms</td></tr>
        <tr><td class="r">Redis: Sorted Set tự làm</td><td class="num">0,021 ms</td><td class="num">0,005 ms</td><td class="num">0,004 ms</td></tr>
      </table>
    </div>
    <div class="p3-foot" data-a style="--i:3">Index ghép đúng thứ tự nhanh gấp 20 lần ghép sai và gấp 43 lần RediSearch khi lọc đủ 3 điều kiện.</div>
  </div>
</div>`});

/* ---------- p.18 ---------- */
Deck.add({part:3,title:'Chi phí cập nhật index khi ghi dữ liệu',html:`
<style>
.p3-18{flex:1;display:flex;flex-direction:column;justify-content:center;gap:1.4rem;min-height:0}
.p3-18 .two{display:grid;grid-template-columns:1fr 1fr;gap:1.4rem}
.p3-18 .p3-tb td{font-size:1.32rem;padding:1.5rem .8rem}
.p3-18 .p3-tb td:first-child{white-space:normal;line-height:1.3}
.p3-18 .p3-tb th{font-size:1.05rem;padding:1.1rem 1rem}
.p3-18 .p3-tb.rd th{background:var(--red)}
.p3-18 .p3-tb td.n{text-align:right;white-space:nowrap}
.p3-18 .p3-tb th.n{text-align:right}
.p3-18 .p3-tb tr.bad td{color:var(--bad);font-weight:800}
.p3-18 .p3-tb tr.pick td{font-weight:800;color:var(--ink)}
</style>
${Deck.head(3,'Chi phí cập nhật index khi ghi dữ liệu')}
<div class="body">
  <div class="p3-18">
    <div class="two">
      <div class="card" data-a style="--i:1;padding:0;overflow:hidden">
        <table class="p3-tb">
          <tr><th>Oracle (10k dòng)</th><th class="n">THÊM</th><th class="n">SỬA</th><th class="n">Redo</th></tr>
          <tr><td>Chỉ khóa chính</td><td class="n">9,8 µs</td><td class="n">6,6 µs</td><td class="n">5,5 MB</td></tr>
          <tr><td>+ SKU + index ghép</td><td class="n">19,3 µs</td><td class="n">17,3 µs</td><td class="n">12,7 MB</td></tr>
          <tr class="bad"><td>+ SKU + index ghép + CTXCAT</td><td class="n">1.014,8 µs</td><td class="n">635,8 µs</td><td class="n">33,1 MB</td></tr>
          <tr class="pick"><td>+ SKU + index ghép + CONTEXT</td><td class="n">48,1 µs</td><td class="n">43,3 µs</td><td class="n">21,7 MB</td></tr>
        </table>
      </div>
      <div class="card" data-a style="--i:2;padding:0;overflow:hidden">
        <table class="p3-tb rd">
          <tr><th>Redis (10k doc)</th><th class="n">THÊM</th><th class="n">SỬA</th><th class="n">AOF</th></tr>
          <tr><td>Không index</td><td class="n">5,6 µs</td><td class="n">2,6 µs</td><td class="n">1,8 MB</td></tr>
          <tr><td>SKU + Sorted Set tự làm</td><td class="n">11,7 µs</td><td class="n">6,8 µs</td><td class="n">3,5 MB</td></tr>
          <tr><td>RediSearch trên SKU</td><td class="n">8,2 µs</td><td class="n">6,3 µs</td><td class="n">1,8 MB</td></tr>
          <tr class="pick"><td>RediSearch đầy đủ</td><td class="n">14,3 µs</td><td class="n">15,4 µs</td><td class="n">1,8 MB</td></tr>
        </table>
      </div>
    </div>
    <div class="p3-foot" data-a style="--i:3;font-size:1.5rem;padding:1.5rem 1.8rem">CTXCAT làm chậm THÊM gấp 53 lần (1.014,8 µs) nên bị loại; B-tree và CONTEXT chỉ tốn vài chục µs.</div>
  </div>
</div>`});

/* ---------- p.19 ---------- */
Deck.add({part:3,title:'Dung lượng lưu trữ index',html:`
<style>
.p3-19{flex:1;display:grid;grid-template-columns:1.7fr 1fr;gap:1.4rem;min-height:0}
.p3-19 .chart{display:flex;flex-direction:column;justify-content:center;padding:2rem 2.4rem}
.p3-19 .hbars.big{gap:2.6rem}
.p3-19 .hbars.big .hb .lb{font-size:1.6rem;font-weight:700;margin-bottom:.6rem}
.p3-19 .hbars.big .hb .lb b{font-family:var(--font)}
.p3-19 .hbars.big .hb .lb span{color:var(--ink)}
.p3-19 .hbars.big .hb .tr{height:1.5rem}
.p3-19 .side{display:grid;grid-template-rows:1fr 1fr;gap:1.4rem;min-height:0}
.p3-19 .side .card{display:flex;flex-direction:column;justify-content:center;padding:2rem 2.2rem}
.p3-19 .side h3{font-size:2.3rem}
.p3-19 .side p{font-size:1.8rem;line-height:1.45;margin-top:.6rem}
</style>
${Deck.head(3,'Dung lượng lưu trữ index')}
<div class="body">
  <div class="p3-19">
    <div class="card chart" data-a style="--i:1"><div id="p3Size"></div></div>
    <div class="side">
      <div class="card r" data-a style="--i:2;background:var(--red-soft)"><h3 class="r-t">Chi phí RAM cố định</h3><p>RediSearch tốn nền ~27 MB RAM cho 200.000 doc.</p></div>
      <div class="card" data-a style="--i:3;background:var(--bad-soft);border-top:.3rem solid var(--bad)"><h3 class="bad-t">Loại bỏ CTXCAT</h3><p>Chiếm 73 MB đĩa (gấp 5,2 lần bảng gốc).</p></div>
    </div>
  </div>
</div>`,
enter(root){
  Deck.hbars(root.querySelector('#p3Size'),[
    {label:'Oracle Text CTXCAT (Đĩa)',value:73.0,db:'o',text:'73,0 MB (5,2× bảng)'},
    {label:'RediSearch TAG SKU (RAM)',value:49.5,db:'r',text:'49,5 MB (72% dữ liệu)'},
    {label:'RediSearch TEXT Tên (RAM)',value:34.9,db:'r',text:'34,9 MB (51% dữ liệu)'},
    {label:'Oracle Text CONTEXT (Đĩa)',value:21.8,db:'o',text:'21,8 MB (1,6× bảng)'},
    {label:'Oracle B-tree Ghép 3 cột (Đĩa)',value:6.0,db:'o',text:'6,0 MB (43% bảng)'}],{max:73,big:true});
}});

/* ---------- p.20 ---------- */
Deck.add({part:3,title:'Thời điểm index cập nhật dữ liệu',html:`
<style>
.p3-20{flex:1;display:flex;flex-direction:column;justify-content:center;gap:1.4rem;min-height:0}
.p3-20 .p3-tb td{font-size:1.5rem;padding:1.6rem 1.3rem}
.p3-20 .p3-tb th{font-size:1.1rem;padding:1.1rem 1.3rem}
.p3-20 .p3-tb .tag{font-size:1.15rem;padding:.3rem .8rem}
</style>
${Deck.head(3,'Thời điểm index cập nhật dữ liệu')}
<div class="body">
  <div class="p3-20">
    <div class="card" data-a style="--i:1;padding:0;overflow:hidden">
      <table class="p3-tb">
        <tr><th>Cơ chế Index</th><th>Kết quả quan sát từ phiên khác</th><th>Tính nhất quán</th></tr>
        <tr><td class="o">Oracle B-tree</td><td>0 trước COMMIT · 1 ngay sau COMMIT</td><td><span class="tag ok">Luôn khớp</span></td></tr>
        <tr><td class="o">Oracle Text CONTEXT</td><td>0 ngay sau COMMIT · 1 sau ~3 giây</td><td><span class="tag warn">Trễ vài giây</span></td></tr>
        <tr><td class="r">RediSearch (khi ghi)</td><td>Sau JSON.SET: thấy ngay lập tức</td><td><span class="tag ok">Cập nhật tức thì</span></td></tr>
        <tr><td class="r">RediSearch (FT.CREATE)</td><td class="num">78 → 9.982 → 32.736 / 200.000</td><td><span class="tag warn">Thiếu lúc dựng nền</span></td></tr>
        <tr><td class="r">Index tự làm (sập giữa chừng)</td><td>Giá 9,99tr nhưng index vẫn ghi 7,14tr</td><td><span class="tag bad">Lệch âm thầm</span></td></tr>
      </table>
    </div>
    <div class="p3-foot" data-a style="--i:2;font-size:1.5rem;padding:1.5rem 1.8rem">Tìm kiếm chấp nhận trễ vài giây; đặt hàng và kiểm tra tồn kho bắt buộc đọc trực tiếp từ Oracle.</div>
  </div>
</div>`});

/* ---------- p.21 ---------- */
Deck.add({part:3,title:'Lựa chọn cơ chế indexing cho ShopFlash',html:`
<style>
.p3-21{flex:1;display:grid;grid-template-columns:1.5fr 1fr;gap:1.4rem;min-height:0}
.p3-21 .tblc{display:flex;flex-direction:column;padding:0;overflow:hidden}
.p3-21 .p3-tb{height:100%}
.p3-21 .p3-tb td{font-size:1.45rem;padding:1rem 1.4rem}
.p3-21 .p3-tb th{font-size:1.15rem;padding:1.2rem 1.4rem;height:1%}
.p3-21 .p3-tb .tag{font-size:1.25rem;padding:.35rem .85rem}
.p3-21 .side{display:grid;grid-template-rows:repeat(3,1fr);gap:1.2rem;min-height:0}
.p3-21 .side .card{display:flex;flex-direction:column;justify-content:center;padding:1.4rem 1.8rem}
.p3-21 .side h3{font-size:1.75rem}
.p3-21 .side p{font-size:1.45rem;line-height:1.4;margin-top:.4rem}
</style>
${Deck.head(3,'Lựa chọn cơ chế indexing cho ShopFlash')}
<div class="body">
  <div class="p3-21">
    <div class="card tblc" data-a style="--i:1">
      <table class="p3-tb">
        <tr><th>Tác vụ của khách hàng</th><th>Nguồn phục vụ</th></tr>
        <tr><td>Tìm theo từ khóa</td><td><span class="tag r">RediSearch TEXT</span></td></tr>
        <tr><td>Duyệt danh mục, lọc giá</td><td><span class="tag o">Oracle Index ghép</span></td></tr>
        <tr><td>Xem chi tiết, tồn kho, đặt hàng</td><td><span class="tag o">Oracle B-tree (gốc)</span></td></tr>
        <tr><td>Tìm từ khóa khi Redis lỗi</td><td><span class="tag o">Oracle Text CONTEXT</span></td></tr>
      </table>
    </div>
    <div class="side">
      <div class="card o" data-a style="--i:2;background:var(--ora-soft)"><h3 class="o-t">Oracle là nguồn gốc</h3><p>Thực thi ghi, ràng buộc và khớp ngay sau COMMIT.</p></div>
      <div class="card r" data-a style="--i:3;background:var(--red-soft)"><h3 class="r-t">Redis là bản chiếu</h3><p>Tìm từ khóa nhanh nhất; dựng lại được từ Oracle.</p></div>
      <div class="card" data-a style="--i:4;background:var(--surface2);box-shadow:none"><h3>Loại bỏ</h3><p>CTXCAT (chậm ghi, tốn đĩa) và index tự làm (dễ lệch).</p></div>
    </div>
  </div>
</div>`});
})();
