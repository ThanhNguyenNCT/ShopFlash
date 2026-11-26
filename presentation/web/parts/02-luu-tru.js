/* Phần 2 · Lưu trữ và quản lý dữ liệu (3 phút). Nguồn: deck Canva trang 6–10, chữ giữ nguyên. */
Deck.opener({part:2,title:'Lưu trữ và quản lý dữ liệu'});

/* ---------- Canva p.7 ---------- */
Deck.add({part:2,title:'Lưu trữ logic của Oracle',
demo:{label:'Storage Lab',path:'/admin/products/storage-lab'},
html:`
<style>
.p2-o{flex:1;min-height:0;display:grid;grid-template-columns:38rem 1fr;gap:1.4rem}
.p2-fig{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1rem;padding:1.4rem 1.6rem}
.p2-fig img{display:block;height:31rem;width:auto;max-width:100%;object-fit:contain}
.p2-fig figcaption{font-size:1rem;font-style:italic;color:var(--muted);text-align:center;line-height:1.45}
.p2-tiers{display:grid;grid-template-rows:repeat(4,1fr);gap:.9rem;min-height:0}
.p2-tier{display:grid;grid-template-columns:auto 1fr;gap:1.3rem;align-items:center;padding:1.1rem 1.6rem;border-left:.35rem solid var(--ora)}
.p2-hex{width:2.6rem;height:2.3rem;background:var(--accent);clip-path:polygon(25% 0,75% 0,100% 50%,75% 100%,25% 100%,0 50%)}
.p2-tier h3{font-size:1.75rem;color:var(--ora)}
.p2-tier p{font-size:1.3rem;line-height:1.45;margin-top:.25rem}
</style>
${Deck.head(2,'Lưu trữ logic của Oracle')}
<div class="body">
  <div class="p2-o">
    <figure class="card p2-fig" data-a style="--i:1">
      <img src="assets/canva/p07-oracle-storage.png" alt="Sơ đồ Logical và Physical: Tablespace, Segment, Extent, Oracle data block; Data File, OS block">
      <figcaption>Hình: Cấu trúc lưu trữ logic và vật lý của Oracle (Nguồn: Oracle Database Concepts).</figcaption>
    </figure>
    <div class="p2-tiers">
      <div class="card p2-tier" data-a style="--i:2"><div class="p2-hex"></div><div><h3>Tablespace</h3><p>Vùng logic cao nhất, chứa các segment và gắn với các data file ở phía vật lý.</p></div></div>
      <div class="card p2-tier" data-a style="--i:3"><div class="p2-hex"></div><div><h3>Segment</h3><p>Tập các extent của một đối tượng: table, index hoặc LOB.</p></div></div>
      <div class="card p2-tier" data-a style="--i:4"><div class="p2-hex"></div><div><h3>Extent</h3><p>Nhóm các block liên tục, được cấp phát mỗi lần mở rộng.</p></div></div>
      <div class="card p2-tier" data-a style="--i:5"><div class="p2-hex"></div><div><h3>Data Block</h3><p>Đơn vị I/O nhỏ nhất, thường 8 KB, chứa các dòng dữ liệu.</p></div></div>
    </div>
  </div>
</div>`});

/* ---------- Canva p.8 ---------- */
Deck.add({part:2,title:'Lưu trữ của Redis',
html:`
<style>
.p2-r{flex:1;min-height:0;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:1.2rem}
.p2-ri{display:flex;flex-direction:column;justify-content:center;gap:1rem;padding:2rem 2.2rem;border-left:.35rem solid var(--red)}
.p2-ri .p2-hex{width:3.2rem;height:2.8rem;background:var(--red);clip-path:polygon(25% 0,75% 0,100% 50%,75% 100%,25% 100%,0 50%)}
.p2-ri h3{font-size:2.1rem;line-height:1.25}
.p2-ri p{font-size:1.32rem;line-height:1.5;margin-top:0}
</style>
${Deck.head(2,'Lưu trữ của Redis')}
<div class="body">
  <div class="p2-r">
    <div class="card p2-ri" data-a style="--i:1"><div class="p2-hex"></div><h3>Key-value hoạt động trên RAM</h3><p>Cấp phát bằng jemalloc, có eviction khi vượt maxmemory.</p></div>
    <div class="card p2-ri" data-a style="--i:2"><div class="p2-hex"></div><h3>RedisJSON lưu document dạng cây</h3><p>Sửa field con trực tiếp, không cần encode lại cả chuỗi.</p></div>
    <div class="card p2-ri" data-a style="--i:3"><div class="p2-hex"></div><h3>Ghi đĩa qua RDB và AOF</h3><p>Hai cơ chế persistence, dùng độc lập hoặc kết hợp.</p></div>
    <div class="card p2-ri" data-a style="--i:4"><div class="p2-hex"></div><h3>Không có ràng buộc schema</h3><p>Không có foreign key hay check constraint khai báo.</p></div>
  </div>
</div>`});

/* ---------- Canva p.9 ---------- */
Deck.add({part:2,title:'So sánh và thiết kế ShopFlash',
demo:{label:'Storage Lab',path:'/admin/products/storage-lab'},
html:`
<style>
.p2c{flex:1;min-height:0;display:grid;grid-template-columns:30rem 1fr;gap:1.4rem}
.p2c .take{display:flex;flex-direction:column;padding:2rem 2.1rem}
.p2c .take .label{font-size:.95rem}
.p2c .take h3{font-size:4.6rem;font-weight:900;letter-spacing:-.03em;line-height:1.05;margin:.3rem 0 1.2rem}
.p2c .take p{font-size:1.6rem;line-height:1.5;margin-top:0}
.p2c .take p+p{margin-top:1.3rem}
.p2c .card.tb{display:flex;flex-direction:column;padding:1.6rem 1.8rem}
.p2c .tbl th{font-size:.95rem;padding:.9rem 1rem}
.p2c .tbl td{font-size:1.32rem;padding:0 1rem;vertical-align:middle;height:7.5rem;white-space:nowrap}
.p2c .tbl td:first-child{width:9.5rem}
.p2-foot{margin-top:auto;padding-top:1.1rem;border-top:.12rem solid var(--line);font-size:1.3rem;font-weight:700;color:var(--ink)}
</style>
${Deck.head(2,'So sánh và thiết kế ShopFlash')}
<div class="body">
  <div class="p2c">
    <div class="take" data-a style="--i:1">
      <div class="label">Thiết kế trong ShopFlash</div>
      <h3>Hybrid</h3>
      <p>Oracle thực thi mọi thao tác ghi và ràng buộc. Redis chỉ nhận bản chiếu sản phẩm phái sinh từ Oracle.</p>
      <p>Commit Oracle xong mới cập nhật Redis. Redis lỗi thì Oracle không rollback; bản chiếu được dựng lại từ Oracle.</p>
    </div>
    <div class="card tb" data-a style="--i:2">
      <table class="tbl">
        <tr><th>Tiêu chí</th><th><span class="chip o" data-morph="ora">ORACLE</span></th><th><span class="chip r" data-morph="red">REDIS</span></th></tr>
        <tr><td>Nơi lưu</td><td>Đĩa cứng</td><td>Bộ nhớ RAM</td></tr>
        <tr><td>Cấu trúc</td><td>Block → Extent → Segment</td><td>Key-value, RedisJSON</td></tr>
        <tr><td>Ràng buộc</td><td>Foreign key, unique, check</td><td>Không có ràng buộc schema</td></tr>
        <tr><td>Vai trò</td><td>Nguồn dữ liệu gốc</td><td>Bản chiếu sản phẩm</td></tr>
      </table>
      <div class="p2-foot">Mô hình kết hợp tận dụng độ tin cậy của Oracle và tốc độ đọc của Redis.</div>
    </div>
  </div>
</div>`});

/* ---------- Canva p.10 ---------- */
Deck.add({part:2,title:'Kết luận',
html:`
<style>
.p2k{flex:1;min-height:0;display:grid;grid-template-rows:repeat(3,1fr);gap:1.2rem}
.p2k .card{display:flex;align-items:center;gap:2.2rem;padding:1.6rem 2.6rem;border-left:.35rem solid var(--accent)}
.p2k .n{flex:none;display:grid;place-items:center;width:5rem;height:5rem;border-radius:50%;background:var(--accent);color:#fff;font-size:2.2rem;font-weight:900}
.p2k p{font-size:2.05rem;line-height:1.4;color:var(--ink);font-weight:600;margin:0}
</style>
${Deck.head(2,'Kết luận')}
<div class="body">
  <div class="p2k">
    <div class="card" data-a style="--i:1"><div class="n">1</div><p>Oracle thực thi ghi và ràng buộc; Redis chỉ giữ bản chiếu để đọc nhanh.</p></div>
    <div class="card" data-a style="--i:2"><div class="n">2</div><p>Redis không tự kiểm tra: ghi thẳng Category không tồn tại hay giá âm vẫn được nhận.</p></div>
    <div class="card" data-a style="--i:3"><div class="n">3</div><p>Client không ghi trực tiếp vào Redis; mọi thao tác ghi đi qua API và Oracle.</p></div>
  </div>
</div>`});
