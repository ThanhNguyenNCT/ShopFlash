/* Tổng kết (1 phút) và cảm ơn. */
Deck.add({part:0,title:'Tổng kết',
html:`
<style>
.sm-wrap{flex:1;min-height:0;margin-top:1.4rem;display:grid;grid-template-rows:1fr auto;gap:1rem}
.sm-tbl{display:grid;grid-template-rows:auto repeat(6,1fr);padding:.6rem 1.4rem;min-height:0}
.sm-row{display:grid;grid-template-columns:3.2rem 14rem 1fr 13rem 15.5rem;gap:1.2rem;align-items:center;border-bottom:.06rem solid var(--line);border-radius:.5rem;padding:0 .6rem}
.sm-row:last-child{border-bottom:0}
.sm-row:not(.hd):hover{background:var(--surface2)}
.sm-row.hd{cursor:default;font-size:.72rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--muted);padding-bottom:.6rem}
.sm-row .n{font-size:1.6rem;font-weight:900;color:var(--brand);letter-spacing:-.03em}
.sm-row .d{font-size:1.15rem;font-weight:700}
.sm-row .w{font-size:1.2rem;color:var(--ink2);line-height:1.4}
.sm-row .u{display:flex;gap:.35rem;flex-wrap:wrap}
.sm-row .k b{display:block;font-size:1.45rem;font-weight:900;line-height:1;font-variant-numeric:tabular-nums}
.sm-row .k span{display:block;margin-top:.3rem;font-size:.86rem;line-height:1.3;color:var(--muted)}
</style>
<div class="head"><div>
  <div class="eyebrow" data-morph="eyebrow">Tổng kết</div><br>
  <h2 class="title h2" data-morph="title">Phân chia dữ liệu trong ShopFlash</h2></div>
  <div class="row" style="padding-bottom:.4rem"><span class="chip o" data-morph="ora">ORACLE</span><span class="chip r" data-morph="red">REDIS</span></div>
</div>
<div class="sm-wrap">
  <div class="card sm-tbl" data-a style="--i:1">
    <div class="sm-row hd"><span>Phần</span><span>Dữ liệu</span><span>Lý do</span><span>ShopFlash dùng</span><span>Bằng chứng đo được</span></div>
    ${[
      [2,'Danh mục, sản phẩm','Ràng buộc chặn ghi sai ngay trong DBMS.',['o','Oracle'],['r','bản chiếu'],'CHECK','Oracle chặn giá âm, Redis trả OK','o-t'],
      [3,'Tìm kiếm sản phẩm','Từ khóa qua RediSearch, SKU qua B-tree.',['r','RediSearch'],['o','B-tree'],'0,006 ms','tra một SKU qua B-tree','o-t'],
      [4,'Báo cáo quản trị','SQL viết được mọi câu hỏi, Redis chỉ cache.',['o','Oracle'],['r','cache'],'1.147','sản phẩm chưa từng bán, chỉ Oracle trả lời được','o-t'],
      [5,'Giỏ hàng, đơn hàng','Lỗi giữa chừng thì hoàn tác cả đơn.',['o','Oracle'],null,'ROLLBACK','lỗi CHECK ở bước cuối, cả đơn được hoàn tác','o-t'],
      [6,'Suất Flash Sale','Lua giữ suất, Oracle chốt đơn.',['r','Redis + Lua'],['o','UPDATE'],'8.951','lượt/giây ở 64 luồng, không bán vượt','r-t'],
      [7,'Sao lưu, khôi phục','Data Pump cho Oracle, AOF everysec cho Redis.',['o','Data Pump'],['r','AOF'],'~33 s','khôi phục cả hai DBMS, số bản ghi khớp','ok-t']
    ].map((r,i)=>`<div class="sm-row" data-a style="--i:${i+2}">
      <span class="n" data-morph="sec${r[0]}">0${r[0]}</span><span class="d">${r[1]}</span><span class="w">${r[2]}</span>
      <span class="u"><span class="chip ${r[3][0]}">${r[3][1]}</span>${r[4]?`<span class="chip soft ${r[4][0]}-t">${r[4][1]}</span>`:''}</span>
      <span class="k">${r[5]?`<b class="${r[7]}">${r[5]}</b><span>${r[6]}</span>`:''}</span></div>`).join('')}
  </div>
  <div class="take" data-a style="--i:10"><div class="label">ShopFlash dùng cả hai</div><p>Đơn hàng, tồn kho, số đã bán ghi vào <b>Oracle</b>. <b>Redis</b> giữ bản chiếu để tìm kiếm, kết quả báo cáo đã tính và suất Flash Sale đang giữ. Mất Redis thì dựng lại từ Oracle, trừ suất đang giữ dở.</p></div>
</div>`});

Deck.add({part:0,full:true,title:'Cảm ơn',
html:`
<style>
.ty{height:100%;display:grid;grid-template-columns:minmax(0,1fr) 36rem;gap:3rem;align-items:center}
.ty-l img{height:7rem;display:block}
.ty-l h1{font-size:5.6rem;margin-top:1.4rem;line-height:1.05;width:auto}
.ty-l .sub{font-size:1.8rem;font-weight:600;margin-top:1rem;color:var(--ink2)}
.ty-l .chips{display:flex;gap:.5rem;margin-top:1.4rem}
.ty-r{display:grid;gap:.8rem}
.ty-r .card{padding:1.1rem 1.4rem}
.ty-m{display:grid;gap:.55rem}
.ty-m div{display:flex;justify-content:space-between;font-size:1.15rem;padding:.35rem 0;border-bottom:.06rem solid var(--line)}
.ty-m div:last-child{border-bottom:0}
.ty-m span{font-family:var(--mono);font-size:.9rem;color:var(--muted)}
</style>
<div class="ty">
  <div class="ty-l">
    <img src="assets/hcmut.png" alt="Logo Trường ĐH Bách Khoa" data-morph="logo">
    <div class="eyebrow" data-morph="eyebrow" style="margin-top:2rem">Hệ quản trị cơ sở dữ liệu · HK261 · Nhóm 11</div>
    <h1 class="title" data-morph="title">Cảm ơn mọi người<br>đã lắng nghe</h1>
    <div class="sub" data-a style="--i:1">Mời thầy cô và các bạn đặt câu hỏi</div>
    <div class="chips"><span class="chip o" data-morph="ora">ORACLE</span><span class="chip r" data-morph="red">REDIS</span></div>
  </div>
  <div class="ty-r">
    <div class="card" data-a style="--i:2"><div class="label">GVHD</div><div style="font-size:1.4rem;font-weight:800;color:var(--brand)">Lê Thị Bảo Thu</div></div>
    <div class="card" data-a style="--i:3"><div class="label">Thành viên</div><div class="ty-m">
      <div>Võ Lâm Gia Bảo<span>2310273</span></div><div>Nguyễn Hữu Thiên Cường<span>2310393</span></div>
      <div>Đặng Hải Sơn<span>2312955</span></div><div>Nguyễn Chí Duy Khang<span>2311436</span></div>
      <div>Nguyễn Công Thành<span>2313119</span></div></div></div>
  </div>
</div>`});

/* Cảnh dự phòng sau Cảm ơn: chỉ mở khi được hỏi. Nguồn: báo cáo task1, Transaction 2.4.2, Concurrency E4. */
Deck.add({part:0,title:'Dự phòng: kiến trúc hai DBMS',
html:`
<style>
.bk .tbl td{font-size:1.38rem;padding:.85rem 1.2rem;vertical-align:top}
.bk .tbl td:first-child{width:17rem;font-weight:700}
.bk>.card{flex:1;min-height:0}
</style>
<div class="head"><div>
  <div class="eyebrow" data-morph="eyebrow">Dự phòng · Hỏi đáp</div><br>
  <h2 class="title h2" data-morph="title">Kiến trúc của Oracle và Redis</h2></div>
  <div class="row" style="padding-bottom:.4rem"><span class="chip o" data-morph="ora">ORACLE</span><span class="chip r" data-morph="red">REDIS</span></div>
</div>
<div class="body bk">
  <div class="card" data-a style="--i:1">
    <table class="tbl">
      <tr><th></th><th>Oracle Database</th><th>Redis Stack</th></tr>
      <tr><td>Mô hình dữ liệu</td><td>Quan hệ: bảng, khóa ngoại, ràng buộc khai báo trong schema</td><td>Key-value: String, Hash, Sorted Set, JSON; không có schema</td></tr>
      <tr><td>Nơi giữ dữ liệu</td><td>Data file trên đĩa; Buffer Cache trong RAM giữ các block hay đọc</td><td>Toàn bộ keyspace nằm trong RAM, cấp phát bằng jemalloc</td></tr>
      <tr><td>Tổ chức lưu trữ</td><td>Tablespace → segment → extent → block 8&nbsp;KB</td><td>Mỗi key một cấu trúc dữ liệu riêng trong bộ nhớ</td></tr>
      <tr><td>Thực thi lệnh</td><td>Nhiều session chạy song song, cô lập bằng khóa dòng và read consistency</td><td>Một luồng chính chạy lần lượt từng lệnh; script Lua chạy liền một mạch</td></tr>
      <tr><td>Truy vấn</td><td>SQL; CBO chọn kế hoạch dựa trên thống kê</td><td>Lệnh theo key; RediSearch cho tìm kiếm, lập trình viên tự chọn cách chạy</td></tr>
      <tr><td>Ghi bền</td><td>LGWR ghi redo xuống đĩa trước khi báo COMMIT xong</td><td>AOF everysec: ghi đĩa mỗi giây; RDB chụp snapshot</td></tr>
    </table>
  </div>
</div>`});

Deck.add({part:0,title:'Dự phòng: ACID trên hai DBMS',
html:`
<style>
.ac .tbl td{font-size:1.42rem;padding:1.1rem 1.3rem;line-height:1.4;vertical-align:top}
.ac .tbl td:first-child{width:13rem;font-weight:800}
.ac>.card{flex:1;min-height:0;display:flex}.ac .tbl{height:100%}
.ac .take p{font-size:1.35rem}
</style>
<div class="head"><div>
  <div class="eyebrow" data-morph="eyebrow">Dự phòng · Hỏi đáp</div><br>
  <h2 class="title h2" data-morph="title">ACID trên Oracle và Redis</h2></div>
  <div class="row" style="padding-bottom:.4rem"><span class="chip o" data-morph="ora">ORACLE</span><span class="chip r" data-morph="red">REDIS</span></div>
</div>
<div class="body ac">
  <div class="card" data-a style="--i:1">
    <table class="tbl">
      <tr><th></th><th>Oracle</th><th>Redis</th></tr>
      <tr><td>Atomicity</td><td>COMMIT, ROLLBACK, SAVEPOINT. Lỗi ở bước cuối checkout thì ROLLBACK cả đơn (D1).</td><td>MULTI/EXEC chạy liền hàng đợi, DISCARD bỏ hàng đợi chưa chạy. Lỗi runtime giữa chừng thì các lệnh đã ghi vẫn còn (A5).</td></tr>
      <tr><td>Consistency</td><td>FK, CHECK trong schema từ chối câu ghi sai (C1).</td><td>Không có ràng buộc. Lua kiểm tra trước khi ghi, chỉ áp cho lệnh đi qua script (C2).</td></tr>
      <tr><td>Isolation</td><td>READ COMMITTED, không đọc dữ liệu chưa commit (B1). SELECT FOR UPDATE khóa dòng trước khi quyết định (B2).</td><td>Từng lệnh và từng script không bị xen ngang. WATCH hủy EXEC khi key bị sửa (B4).</td></tr>
      <tr><td>Durability</td><td>Redo ghi xuống đĩa khi COMMIT.</td><td>AOF everysec, có thể mất khoảng 1 giây ghi gần nhất khi sập máy.</td></tr>
    </table>
  </div>
  <div class="take soft" data-a style="--i:2;margin-top:1rem"><div class="label">Giới hạn</div><p>Phần giao dịch chưa thử sập máy rồi khôi phục. Khôi phục được đo ở phần 7.</p></div>
</div>`});
