/* Phần 7 · Sao lưu và khôi phục (3 phút). Nguồn: deck Canva trang 36–40, chữ giữ nguyên. */
Deck.opener({part:7,title:'Sao lưu và khôi phục'});

/* ---------- Canva p.37 ---------- */
Deck.add({part:7,title:'Sao lưu của Oracle: Data Pump',
html:`
<style>
.p7-pump{display:grid;grid-template-columns:1fr 7rem 1fr 7rem 1fr;align-items:stretch;height:19rem}
.p7-bx{display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;gap:.35rem;padding:1.4rem 1.2rem;border-radius:var(--r);background:var(--ora-soft)}
.p7-bx h3{font-size:2.3rem;font-weight:800;color:var(--ora);line-height:1.2}
.p7-bx b{font-size:1.55rem;color:var(--ink)}
.p7-bx span{font-size:1.4rem;color:var(--ink2)}
.p7-bx.f{background:var(--ora);box-shadow:var(--shadow)}
.p7-bx.f h3,.p7-bx.f b,.p7-bx.f span{color:#fff}
.p7-ar{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:.4rem;color:var(--accent)}
.p7-ar .ic{width:3.4rem;height:3.4rem;stroke-width:2.6}
.p7-ar span{font-family:var(--mono);font-size:1.15rem;font-weight:700;color:var(--muted)}
.p7-bul{flex:1;min-height:0;margin-top:1.4rem;display:flex;flex-direction:column;justify-content:center;padding:1.8rem 2.4rem}
.p7-bul .list{font-size:1.7rem;gap:1.6rem}
.p7-bul .list li::before{width:.7rem;height:.7rem;margin-top:.55em}
</style>
${Deck.head(7,'Sao lưu của Oracle: Data Pump')}
<div class="body">
  <div class="p7-pump">
    <div class="p7-bx" data-a style="--i:1"><h3>Schema SHOP</h3><b>Oracle Database</b><span>bảng, index, ràng buộc</span></div>
    <div class="p7-ar" data-a style="--i:2">${Deck.icon('arrow')}<span>expdp</span></div>
    <div class="p7-bx f" data-a style="--i:3"><h3>File .dmp</h3><b>Data Pump export</b><span>cấu trúc và dữ liệu dòng</span></div>
    <div class="p7-ar" data-a style="--i:4">${Deck.icon('arrow')}<span>impdp</span></div>
    <div class="p7-bx" data-a style="--i:5"><h3>Oracle phục hồi</h3><b>Import</b><span>tạo lại đối tượng</span></div>
  </div>
  <div class="card o p7-bul" data-a style="--i:6">
    <ul class="list o">
      <li>Oracle có hai mức sao lưu: RMAN (vật lý) và Data Pump (logic).</li>
      <li>ShopFlash dùng Data Pump vì phù hợp ứng dụng quy mô vừa.</li>
      <li>File .dmp chứa cả định nghĩa schema lẫn dữ liệu dòng.</li>
      <li>Khi import, Data Pump tạo lại bảng, index, ràng buộc và nạp dữ liệu nhanh nhờ Direct Path I/O.</li>
    </ul>
  </div>
</div>`});

/* ---------- Canva p.38 ---------- */
Deck.add({part:7,title:'Persistence của Redis',
html:`
<style>
.p7-rg{flex:1;min-height:0;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:1.2rem}
.p7-rg .card{display:flex;flex-direction:column;justify-content:center;gap:.9rem;padding:1.8rem 2.2rem;border-left:.35rem solid var(--red)}
.p7-rg h3{font-size:2.1rem;color:var(--red)}
.p7-rg p{font-size:1.45rem;line-height:1.5;margin:0}
.p7-rg code{font-weight:700;color:var(--ink);background:var(--surface2);padding:.05rem .4rem;border-radius:.35rem;font-size:.92em}
.p7-ban{margin-top:1.2rem;padding:1.6rem 2.2rem}
.p7-ban p{font-size:1.6rem;font-weight:700;margin:0}
</style>
${Deck.head(7,'Persistence của Redis')}
<div class="body">
  <div class="p7-rg">
    <div class="card" data-a style="--i:1"><h3>RDB: snapshot</h3><p>Chụp toàn bộ bộ nhớ ra file nhị phân gọn, nạp lại rất nhanh; có thể mất dữ liệu giữa hai lần chụp.</p></div>
    <div class="card" data-a style="--i:2"><h3>AOF: nhật ký lệnh ghi</h3><p>Ghi lại từng lệnh ghi nên bền vững hơn RDB; khi khởi động Redis ưu tiên đọc AOF.</p></div>
    <div class="card" data-a style="--i:3"><h3>Ba chế độ đồng bộ đĩa</h3><p><code>always</code>: fsync mỗi lệnh. <code>everysec</code>: mỗi giây. <code>no</code>: giao cho hệ điều hành.</p></div>
    <div class="card" data-a style="--i:4"><h3>Kết hợp RDB và AOF</h3><p>Dùng độc lập hoặc kết hợp để cân bằng tốc độ khôi phục và độ an toàn.</p></div>
  </div>
  <div class="take p7-ban" data-a style="--i:5"><p>Đánh đổi chính: đồng bộ đĩa càng thường xuyên thì càng an toàn nhưng ghi càng chậm.</p></div>
</div>`});

/* ---------- Canva p.39 ---------- */
Deck.add({part:7,title:'Thiết kế sao lưu ShopFlash',
/* demo:{label:'Sao lưu',path:'/admin/backups'},  (ẩn nút demo) */
html:`
<style>
.p7-flow{flex:none;height:18rem}
.p7-flow .st{padding:1.4rem 1.5rem}
.p7-flow .st::before{width:2.4rem;height:2.4rem;font-size:1.1rem;margin-bottom:.8rem;background:var(--accent)}
.p7-flow .st h3{font-size:1.65rem}
.p7-flow .st p{font-size:1.3rem;line-height:1.45;margin-top:.4rem}
.p7-flow .st.o h3{color:var(--ora)}.p7-flow .st.r h3{color:var(--red)}
.p7-pk{margin-top:1.8rem}
.p7-pk .label{font-size:1rem;color:var(--brand);margin-bottom:.8rem}
.p7-bar{display:flex;height:8.6rem;border-radius:var(--r);overflow:hidden;box-shadow:var(--shadow)}
.p7-bar>div{display:flex;flex-direction:column;justify-content:center;align-items:center;gap:.2rem;color:#fff;text-align:center}
.p7-bar b{font-size:1.75rem;font-weight:800}
.p7-bar span{font-size:1.25rem;opacity:.9}
.p7-bar .o{flex:42;background:var(--ora)}.p7-bar .r{flex:42;background:var(--red)}.p7-bar .m{flex:16;background:var(--accent)}
.p7-pkn{flex:1;min-height:0;margin-top:1.4rem;display:flex;flex-direction:column;justify-content:center;padding:1.2rem 2.2rem}
.p7-pkn .list{font-size:1.6rem;gap:1rem}
.p7-pkn .list li::before{width:.65rem;height:.65rem;margin-top:.55em}
</style>
${Deck.head(7,'Thiết kế sao lưu ShopFlash')}
<div class="body">
  <div class="flow p7-flow" data-a style="--i:1">
    <div class="st"><h3>Tạo gói backup</h3><p>Mỗi lượt xuất Data Pump cho schema SHOP và RDB snapshot cho Redis.</p></div>
    <div class="st"><h3>Kiểm tra SHA-256</h3><p>Manifest lưu mã băm; đối chiếu trước khi khôi phục.</p></div>
    <div class="st o"><h3>Khôi phục Oracle</h3><p>Import Data Pump đưa dữ liệu nghiệp vụ gốc về trạng thái chuẩn.</p></div>
    <div class="st r"><h3>Khôi phục Redis</h3><p>Nạp RDB trong container cô lập để tạo lại AOF, tránh AOF cũ đè dữ liệu.</p></div>
  </div>
  <div class="p7-pk" data-a style="--i:2">
    <div class="label">Một gói backup gồm ba phần</div>
    <div class="p7-bar">
      <div class="o"><b>Oracle Data Pump (.dmp)</b><span>schema SHOP</span></div>
      <div class="r"><b>Redis RDB</b><span>keyspace</span></div>
      <div class="m"><b>Manifest</b><span>SHA-256</span></div>
    </div>
  </div>
  <div class="card p7-pkn" data-a style="--i:3">
    <ul class="list">
      <li>Oracle và Redis được sao lưu cùng lúc để hai bên nhất quán với nhau.</li>
      <li>Mã SHA-256 giúp phát hiện file hỏng trước khi khôi phục.</li>
    </ul>
  </div>
</div>`});

/* ---------- Canva p.40 ---------- */
Deck.add({part:7,title:'Kết luận',
html:`
<style>
.p7-k{flex:1;min-height:0;display:grid;grid-template-columns:27rem 1fr;gap:1.4rem}
.p7-kp{display:grid;grid-template-rows:repeat(2,1fr);gap:1rem;min-height:0}
.p7-kp .card{display:flex;flex-direction:column;justify-content:center;padding:1.2rem 1.6rem}
.p7-kp .kpi b{font-size:3.6rem}
.p7-kp .kpi em{font-style:normal}
.p7-kp .kpi.n b{color:var(--brand)}
.p7-kp .kpi span{font-size:1.1rem;color:var(--ink2)}
.p7-tb{display:flex;flex-direction:column;padding:1.6rem 1.8rem}
.p7-tb .tbl th{font-size:.95rem;padding:.8rem 1rem}
.p7-tb .tbl td{font-size:1.45rem;padding:0 1rem;height:7.4rem;vertical-align:middle}
.p7-tb .tbl tr.hl td{font-weight:800;color:var(--ink)}
.p7-tb .tbl td.set{width:34%}
.p7-sb{display:flex;align-items:center;gap:1rem}
.p7-sb i{display:block;height:.75rem;border-radius:99rem;background:var(--red);width:0;transition:width 1.1s var(--ease-out)}
.p7-sb em{font-style:normal;min-width:6rem;text-align:right}
.p7-tb .tbl tr.bad .p7-sb i{background:var(--bad)}
.p7-tb .tbl tr.bad td:nth-child(3){color:var(--bad);font-weight:800}
.p7-fn{margin-top:1rem;font-size:1.1rem;color:var(--muted)}
.p7-tb .take{margin-top:auto;padding:1.2rem 1.5rem}
.p7-tb .take p{font-size:1.35rem;font-weight:700;margin:0;color:#fff}
</style>
${Deck.head(7,'Kết luận')}
<div class="body">
  <div class="p7-k">
    <div class="p7-kp">
      <div class="card" data-a style="--i:1"><div class="kpi ok"><b><em data-count="100">100</em>%</b><span>lượt khôi phục thành công, số bản ghi khớp trạng thái ban đầu</span></div></div>
      <div class="card" data-a style="--i:3"><div class="kpi bad"><b><em data-count="40">40</em>×</b><span>độ trễ ghi p95 tăng khi AOF dùng always so với everysec</span></div></div>
    </div>
    <div class="card p7-tb" data-a style="--i:4">
      <table class="tbl" id="p7Tbl">
        <tr><th>Chế độ AOF</th><th>SET (ops/s)</th><th class="n">SET p95 (ms)</th><th class="n">GET (ops/s)</th></tr>
        <tr><td>Tắt AOF</td><td class="set"><div class="p7-sb"><em>205.128</em><i data-v="205128"></i></div></td><td class="n">0,180</td><td class="n">206.018</td></tr>
        <tr class="hl"><td>everysec</td><td class="set"><div class="p7-sb"><em>227.426</em><i data-v="227426"></i></div></td><td class="n">0,186</td><td class="n">202.603</td></tr>
        <tr class="bad"><td>always</td><td class="set"><div class="p7-sb"><em>8.493</em><i data-v="8493"></i></div></td><td class="n">7,764</td><td class="n">202.353</td></tr>
      </table>
      <div class="p7-fn">redis-benchmark: 100.000 lệnh SET và GET, 50 client, value 256 byte.</div>
      <div class="take"><p>Chọn appendonly yes + appendfsync everysec: ghi nhanh như khi tắt AOF, tối đa mất khoảng 1 giây dữ liệu.</p></div>
    </div>
  </div>
</div>`,
enter(root,ctx){
  Deck.count(root);
  const bars=[...root.querySelectorAll('#p7Tbl .p7-sb i')];
  const go=()=>bars.forEach(b=>{b.style.width=(+b.dataset.v/227426*9)+'rem'});
  bars.forEach(b=>{b.style.transition=ctx&&ctx.print?'none':'';b.style.width='0'});
  if(ctx&&ctx.print){go();return}
  clearTimeout(root._p7t);root._p7t=setTimeout(go,600);
}});
