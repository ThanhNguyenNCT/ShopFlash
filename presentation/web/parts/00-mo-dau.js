/* Bìa và mục lục, theo Canva trang 1–2. */
Deck.add({part:0,full:true,title:'Bìa',
html:`
<style>
.cv{height:100%;display:grid;grid-template-columns:minmax(0,1fr) 34rem;gap:3.4rem;align-items:stretch}
.cv-l{display:flex;flex-direction:column;min-height:0}
.cv-top{display:flex;align-items:center;gap:1.1rem}
.cv-top img{height:5.2rem;display:block}
.cv-top small{display:block;font-size:.85rem;font-weight:600;letter-spacing:.06em;color:var(--muted)}
.cv-top strong{display:block;font-size:1.55rem;font-weight:800;color:var(--brand)}
.cv-mid{flex:1;display:flex;flex-direction:column;justify-content:center}
.cv-title{font-size:6.6rem;line-height:1.05;letter-spacing:-.035em;margin-top:1rem}
.cv-title .o{color:var(--ora)}.cv-title .r{color:var(--red)}
.cv-sub{font-size:3rem;font-weight:800;margin-top:1rem;line-height:1.15;color:var(--ink)}
.cv-foot{font-size:1.1rem;color:var(--muted);font-weight:600}
.cv-r{display:flex;flex-direction:column;justify-content:center;gap:1.2rem}
.cv-card{padding:2rem 2.2rem}
.cv-card .lb{font-size:.85rem;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:var(--muted)}
.cv-card .gv{font-size:2rem;font-weight:800;color:var(--brand);margin-top:.5rem}
.cv-mem{display:grid;gap:.2rem;margin-top:.8rem}
.cv-mem div{display:flex;justify-content:space-between;align-items:baseline;padding:.7rem 0;border-bottom:.06rem solid var(--line);font-size:1.35rem;font-weight:700}
.cv-mem div:last-child{border-bottom:0}
.cv-mem span{font-family:var(--mono);font-size:1.05rem;font-weight:500;color:var(--muted)}
</style>
<div class="cv">
  <div class="cv-l">
    <div class="cv-top">
      <img src="assets/hcmut.png" alt="Logo Trường ĐH Bách Khoa" data-morph="logo">
      <div data-a style="--i:0"><small>ĐẠI HỌC QUỐC GIA THÀNH PHỐ HỒ CHÍ MINH</small><strong>Trường Đại học Bách Khoa</strong></div>
    </div>
    <div class="cv-mid">
      <div class="eyebrow" data-morph="eyebrow">Hệ quản trị cơ sở dữ liệu</div>
      <h1 class="title cv-title" data-morph="title">So sánh<br><span class="o">Oracle</span> vs <span class="r">Redis</span></h1>
      <div class="cv-sub" data-a style="--i:1">và ứng dụng vào ShopFlash</div>
    </div>
    <div class="cv-foot" data-a style="--i:3">Thành phố Hồ Chí Minh – 11/2026</div>
  </div>
  <div class="cv-r">
    <div class="card cv-card" data-a style="--i:1"><div class="lb">GVHD</div><div class="gv">Lê Thị Bảo Thu</div></div>
    <div class="card cv-card" data-a style="--i:2"><div class="lb">Nhóm 11</div>
      <div class="cv-mem">
        <div>Võ Lâm Gia Bảo<span>2310273</span></div>
        <div>Nguyễn Hữu Thiên Cường<span>2310393</span></div>
        <div>Đặng Hải Sơn<span>2312955</span></div>
        <div>Nguyễn Chí Duy Khang<span>2311436</span></div>
        <div>Nguyễn Công Thành<span>2313119</span></div>
      </div></div>
  </div>
</div>`});

Deck.add({part:0,title:'Nội dung chính',
html:`
<style>
.ag-list{flex:1;margin-top:1.4rem;display:grid;grid-template-rows:repeat(7,1fr);gap:.7rem;min-height:0}
.ag{display:grid;grid-template-columns:5rem 1fr;align-items:center;gap:1.4rem;padding:0 2.2rem}
.ag .n{font-size:2.8rem;font-weight:900;line-height:1;color:var(--brand);letter-spacing:-.04em}
.ag h3{font-size:2.1rem}
</style>
<div class="head"><div>
  <div class="eyebrow" data-morph="eyebrow">Hệ quản trị cơ sở dữ liệu · Nhóm 11</div><br>
  <h2 class="title h2" data-morph="title">Nội dung chính</h2></div>
</div>
<div class="ag-list">
  ${Deck.PARTS.map((p,i)=>`<div class="card ag" data-a style="--i:${i}">
    <div class="n" data-morph="sec${p.n}">${p.n}</div><h3>${p.name}</h3></div>`).join('')}
</div>`});
