/* Deck engine: cảnh, morph (View Transitions), rail mục lục, presenter mode, ngăn chi tiết, nút demo, in PDF.
   Nội dung nằm trong parts/*.js, mỗi file gọi Deck.add(...) cho từng cảnh. */
(function(){
'use strict';

const PARTS=[
  {n:1,name:'Giới thiệu đề tài và kiến trúc',min:3},
  {n:2,name:'Lưu trữ và quản lý dữ liệu',min:3},
  {n:3,name:'Chỉ mục',min:5},
  {n:4,name:'Xử lý truy vấn',min:5},
  {n:5,name:'Giao dịch',min:5},
  {n:6,name:'Kiểm soát đồng thời',min:5},
  {n:7,name:'Sao lưu và khôi phục',min:3},
];
const TOTAL_MIN=30;
const DEMO_BASE='http://localhost:5173';
const HEALTH=DEMO_BASE+'/api/health';

const scenes=[];
const Deck={PARTS,TOTAL_MIN,DEMO_BASE,scenes};

/* ---------- đăng ký cảnh ----------
   {part:0-8, title, notes, html, full?, detail?:{eyebrow,title,html}, demo?:{label,path}, enter?(root,ctx), leave?(root)} */
Deck.add=s=>{scenes.push(s);return s};

/* Cảnh mở chương: số chương lớn morph từ ô mục lục. */
Deck.opener=o=>{
  const p=PARTS[o.part-1],nn=String(o.part).padStart(2,'0');
  return Deck.add({part:o.part,title:'Mở phần '+o.part+': '+p.name,notes:o.notes||'',html:`
  <div class="opener">
    <div class="big" data-morph="sec${o.part}">${nn}</div>
    <div>
      <div class="eyebrow" data-morph="eyebrow">Phần ${o.part} · ${p.min} phút</div><br>
      <h2 class="title" data-morph="title">${o.title||p.name}</h2>
      ${o.q?`<p class="q" data-a style="--i:0">${o.q}</p>`:''}
      ${o.who?`<div class="who" data-a style="--i:5">Trình bày: <b>${o.who}</b></div>`:''}
    </div>
  </div>`});
};

/* Tiêu đề cảnh nội dung: eyebrow + h2 (cả hai morph), phần phải tùy chọn. */
Deck.head=(part,title,right='')=>{
  const p=PARTS[part-1];
  return `<div class="head"><div><div class="eyebrow" data-morph="eyebrow"><span class="dotn" data-morph="sec${part}">${part}</span>${p.name}</div><br>
  <h2 class="title h2" data-morph="title">${title}</h2></div>${right?`<div data-a style="--i:0">${right}</div>`:''}</div>`;
};

/* ---------- tiện ích cho phần nội dung ---------- */
const fmtVN=(v,dec=0)=>Number(v).toLocaleString('vi-VN',{minimumFractionDigits:dec,maximumFractionDigits:dec});
Deck.fmt=fmtVN;

/* Thanh ngang. rows:[{label,value,db:'o'|'r'|'a'|'m',text,win,na,dim}] opts:{max,log,min,unit,dec,delay,big} */
Deck.hbars=(el,rows,opts={})=>{
  const vals=rows.filter(r=>!r.na).map(r=>r.value);
  const max=opts.max||Math.max(...vals);
  const min=opts.log?(opts.min||Math.min(...vals)/3):0;
  const w=v=>{if(opts.log){const a=Math.log10(min),b=Math.log10(max);return Math.max(1.5,(Math.log10(v)-a)/(b-a)*100)}return Math.max(.8,v/max*100)};
  el.classList.add('hbars');el.classList.toggle('big',!!opts.big);
  el.innerHTML=rows.map(r=>`<div class="hb ${r.db||''} ${r.win?'win':''} ${r.na?'na':''} ${r.dim?'dim':''}">
    <div class="lb"><span>${r.label}</span><b>${r.na?(r.text||'không làm được'):(r.text||fmtVN(r.value,opts.dec||0)+(opts.unit?' '+opts.unit:''))}</b></div>
    <div class="tr"><div class="fl" data-w="${r.na?0:w(r.value)}"></div></div></div>`).join('');
  const go=()=>el.querySelectorAll('.fl').forEach((f,i)=>{f.style.transitionDelay=(i*.06)+'s';f.style.width=f.dataset.w+'%'});
  if(document.body.classList.contains('printing'))go();
  else setTimeout(()=>requestAnimationFrame(go),opts.delay??60);
};

/* Đếm số chạy lên cho [data-count] (data-dec = số lẻ). */
Deck.count=(root,delay=700)=>{
  root.querySelectorAll('[data-count]').forEach(el=>{
    const to=+el.dataset.count,dec=+(el.dataset.dec||0);
    if(document.body.classList.contains('printing')){el.textContent=fmtVN(to,dec);return}
    const t0=performance.now();el.textContent=fmtVN(0,dec);
    const tick=t=>{const p=Math.min(Math.max((t-t0-delay)/1300,0),1),e=1-Math.pow(1-p,3);el.textContent=fmtVN(to*e,dec);if(p<1)requestAnimationFrame(tick)};
    requestAnimationFrame(tick);
  });
};

/* Nút chọn kiểu segmented: <div class="seg"><button data-k="..."> */
Deck.seg=(el,cb,initial)=>{
  const btns=[...el.querySelectorAll('button[data-k]')];
  const set=k=>{btns.forEach(b=>b.classList.toggle('on',b.dataset.k===k));cb&&cb(k)};
  btns.forEach(b=>b.onclick=()=>set(b.dataset.k));
  set(initial||btns[0].dataset.k);return set;
};

/* Icon SVG nét (kiểu Lucide), không dùng emoji. */
const ICONS={play:'<polygon points="6 4 20 12 6 20 6 4"/>',replay:'<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>',x:'<path d="M18 6 6 18M6 6l12 12"/>',check:'<path d="M20 6 9 17l-5-5"/>',arrow:'<path d="M5 12h14M13 6l6 6-6 6"/>',ext:'<path d="M15 3h6v6M10 14 21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',info:'<circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/>',bolt:'<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>',db:'<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>'};
Deck.icon=n=>`<svg class="ic" viewBox="0 0 24 24" aria-hidden="true">${ICONS[n]||''}</svg>`;

Deck.toast=msg=>{const t=document.getElementById('toast');t.textContent=msg;t.classList.add('show');clearTimeout(t._h);t._h=setTimeout(()=>t.classList.remove('show'),2600)};

/* ---------- trạng thái ---------- */
let cur=0,busy=false,presenterWin=null,healthUp=false;
const canMorph=!!document.startViewTransition&&!matchMedia('(prefers-reduced-motion: reduce)').matches;
const isPresenter=new URLSearchParams(location.search).has('presenter');
const isExport=new URLSearchParams(location.search).has('export');

function build(){
  const deck=document.getElementById('deck');
  scenes.forEach((s,i)=>{
    const sec=document.createElement('section');
    sec.className='scene'+(s.full?' full':'');sec.dataset.part=s.part;sec.dataset.i=i;
    sec.innerHTML=`<div class="frame">${s.html}</div><img class="print-logo" src="assets/hcmut.png" alt="">`;
    deck.append(sec);s.el=sec;
  });
  /* rail */
  const rail=document.getElementById('rail');
  PARTS.forEach(p=>{
    const it=document.createElement('div');it.className='it';
    it.innerHTML=`<button data-part="${p.n}" aria-label="Phần ${p.n}: ${p.name}">${p.n}</button><span class="t">${p.n}. ${p.name} · ${p.min}'</span>`;
    it.querySelector('button').onclick=()=>goPart(p.n);rail.append(it);
  });
}

function tagMorph(){
  scenes.forEach((s,i)=>s.el.querySelectorAll('[data-morph]').forEach(e=>{e.style.viewTransitionName=i===cur?e.dataset.morph:'none'}));
  document.getElementById('cornerLogo').style.viewTransitionName=scenes[cur].full?'none':'logo';
}

function actions(){
  const s=scenes[cur],a=document.getElementById('actions');a.innerHTML='';
  if(s.detail){const b=document.createElement('button');b.className='btn ghost sm';b.innerHTML=Deck.icon('info')+' Chi tiết <span class="m-t">D</span>';b.onclick=openDrawer;a.append(b)}
  if(s.demo){const b=document.createElement('button');b.className='btn sm';b.innerHTML=`<span class="hdot ${healthUp?'up':''}" id="hdot"></span>Mở demo: ${s.demo.label} ${Deck.icon('ext')}`;b.onclick=()=>openDemo(s.demo.path);a.append(b);ping()}
}

function render(){
  const s=scenes[cur];
  scenes.forEach((x,i)=>x.el.classList.toggle('active',i===cur));
  document.body.classList.toggle('no-chrome',!!s.full);
  tagMorph();
  document.querySelectorAll('#rail button').forEach(b=>{const n=+b.dataset.part;b.classList.toggle('on',n===s.part);b.classList.toggle('done',n<s.part)});
  document.getElementById('count').textContent=String(cur+1).padStart(2,'0')+'/'+scenes.length;
  document.getElementById('progress').style.width=((cur+1)/scenes.length*100)+'%';
  actions();closeDrawer(true);
  history.replaceState(null,'','#'+cur);
  try{s.enter&&s.enter(s.el,{print:isExport})}catch(e){console.error(e)}
  sync();
}

function go(n,instant){
  if(n<0||n>=scenes.length||n===cur&&!instant)return;
  const prev=scenes[cur];
  const apply=()=>{try{prev.leave&&prev.leave(prev.el)}catch(e){}cur=n;render()};
  if(instant||!canMorph){apply();return}
  if(busy)return;busy=true;
  document.startViewTransition(apply).finished.finally(()=>busy=false);
}
function goPart(p){const k=scenes.findIndex(s=>s.part===p);if(k>=0)go(k)}
Deck.go=go;

/* ---------- ngăn chi tiết ---------- */
function openDrawer(){
  const s=scenes[cur];if(!s.detail)return;
  const d=document.getElementById('drawer');
  d.querySelector('.inner').innerHTML=`${s.detail.eyebrow?`<div class="eyebrow">${s.detail.eyebrow}</div>`:''}<h3>${s.detail.title}</h3>${s.detail.html}`;
  d.classList.add('open');document.getElementById('scrim').classList.add('open');
}
function closeDrawer(){document.getElementById('drawer').classList.remove('open');document.getElementById('scrim').classList.remove('open')}

/* ---------- demo ShopFlash ---------- */
async function ping(){
  try{const c=new AbortController();const t=setTimeout(()=>c.abort(),1500);
    await fetch(HEALTH,{mode:'no-cors',cache:'no-store',signal:c.signal});clearTimeout(t);healthUp=true}catch{healthUp=false}
  const d=document.getElementById('hdot');if(d)d.classList.toggle('up',healthUp);return healthUp;
}
async function openDemo(path){
  if(await ping())window.open(DEMO_BASE+path,'shopflash-demo');
  else Deck.toast('ShopFlash chưa chạy ở '+DEMO_BASE.replace('http://',''));
}

/* ---------- presenter mode ---------- */
function openPresenter(){
  const url=location.href.split('#')[0].split('?')[0]+'?presenter#'+cur;
  presenterWin=window.open(url,'shopflash-presenter','width=1200,height=760');
  setTimeout(sync,800);
}
function sync(){if(presenterWin&&!presenterWin.closed)presenterWin.postMessage({type:'state',cur},'*')}
addEventListener('message',e=>{
  const m=e.data||{};
  if(m.type==='go'&&!isPresenter)go(m.n);
  if(m.type==='hello'&&!isPresenter){presenterWin=e.source;sync()}
});

function presenterUI(){
  document.body.className='presenter';
  document.body.innerHTML=`<div class="pv">
    <div class="top">
      <div class="box"><div class="clock" id="pvTotal"><small>Tổng thời gian · mục tiêu ${TOTAL_MIN}'</small>00:00</div><div class="bar"><i id="pvTotalBar"></i></div></div>
      <div class="box"><div class="clock" id="pvPart"><small>Phần hiện tại</small>00:00</div><div class="bar"><i id="pvPartBar"></i></div></div>
      <div class="box" style="flex:1"><div class="lbl">Đang ở</div><h2 id="pvTitle"></h2><div class="ctl"><button id="pvPrev">‹ Trước</button><button id="pvNext">Sau ›</button><button id="pvStart">Bắt đầu tính giờ</button><button id="pvReset">Đặt lại</button></div></div>
    </div>
    <div class="box"><div class="lbl">Các cảnh trong phần này</div><ol class="notes" id="pvNotes" style="padding-left:1.4rem"></ol></div>
    <div class="box next"><div class="lbl">Cảnh tiếp theo</div><h3 id="pvNext2"></h3><div class="parts" id="pvParts"></div></div>
  </div>`;
  let pcur=+(location.hash.slice(1))||0,t0=null,partStart=null,lastPart=-1;
  const send=n=>window.opener&&window.opener.postMessage({type:'go',n},'*');
  const mmss=ms=>{const s=Math.max(0,Math.floor(ms/1000));return String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0')};
  function show(){
    const s=scenes[pcur],n=scenes[pcur+1];
    document.getElementById('pvTitle').textContent=(pcur+1)+'/'+scenes.length+' · '+(s.title||'');
    document.getElementById('pvNotes').innerHTML=scenes.map((x,i)=>x.part===s.part?`<li style="${i===pcur?'font-weight:800;color:#fff':'opacity:.6'}">${x.title||''}</li>`:'').join('');
    document.getElementById('pvNext2').textContent=n?(n.title||''):'Hết';
    document.getElementById('pvParts').innerHTML=PARTS.map(p=>`<div class="${p.n===s.part?'on':''}"><span>${p.n}. ${p.name}</span><span>${p.min}'</span></div>`).join('');
    if(s.part!==lastPart){lastPart=s.part;partStart=t0?Date.now():null}
  }
  function tick(){
    const now=Date.now(),p=PARTS[scenes[pcur].part-1];
    const tot=t0?now-t0:0,pe=partStart?now-partStart:0,budget=p?p.min*60000:0;
    const te=document.getElementById('pvTotal'),pe2=document.getElementById('pvPart');
    te.innerHTML=`<small>Tổng thời gian · mục tiêu ${TOTAL_MIN}'</small>${mmss(tot)}`;
    te.className='clock '+(tot>TOTAL_MIN*60000?'over':tot>(TOTAL_MIN-3)*60000?'warnc':'');
    pe2.innerHTML=`<small>${p?'Phần '+p.n+' · còn '+mmss(budget-pe):'Mở đầu / kết thúc'}</small>${mmss(pe)}`;
    pe2.className='clock '+(p&&pe>budget?'over':p&&pe>budget-30000?'warnc':'');
    document.getElementById('pvTotalBar').style.width=Math.min(100,tot/(TOTAL_MIN*600))+'%';
    document.getElementById('pvPartBar').style.width=p?Math.min(100,pe/budget*100)+'%':'0';
  }
  addEventListener('message',e=>{if(e.data&&e.data.type==='state'){pcur=e.data.cur;show()}});
  document.getElementById('pvPrev').onclick=()=>send(pcur-1);
  document.getElementById('pvNext').onclick=()=>send(pcur+1);
  document.getElementById('pvStart').onclick=()=>{t0=Date.now();partStart=Date.now()};
  document.getElementById('pvReset').onclick=()=>{t0=null;partStart=null};
  addEventListener('keydown',e=>{if(['ArrowRight','PageDown',' '].includes(e.key)){e.preventDefault();send(pcur+1)}else if(['ArrowLeft','PageUp'].includes(e.key)){e.preventDefault();send(pcur-1)}});
  if(window.opener)window.opener.postMessage({type:'hello'},'*');
  show();setInterval(tick,500);tick();
}

/* ---------- in PDF ---------- */
addEventListener('beforeprint',()=>{document.body.classList.add('printing');scenes.forEach(s=>{try{s.enter&&s.enter(s.el,{print:true})}catch(e){}})});
addEventListener('afterprint',()=>{document.body.classList.remove('printing');render()});

/* ---------- phím ---------- */
function keys(e){
  if(e.target.closest('input,textarea,select'))return;
  const help=document.getElementById('help');
  if(e.key==='Escape'){closeDrawer();help.classList.remove('open');return}
  if(document.getElementById('drawer').classList.contains('open')&&e.key!=='d'&&e.key!=='D')return;
  if(['ArrowRight','PageDown',' '].includes(e.key)){e.preventDefault();go(cur+1)}
  else if(['ArrowLeft','PageUp'].includes(e.key)){e.preventDefault();go(cur-1)}
  else if(e.key==='Home')go(0);
  else if(e.key==='End')go(scenes.length-1);
  else if(/^[1-7]$/.test(e.key))goPart(+e.key);
  else if(e.key==='d'||e.key==='D'){document.getElementById('drawer').classList.contains('open')?closeDrawer():openDrawer()}
  else if(e.key==='p'||e.key==='P')openPresenter();
  else if(e.key==='f'||e.key==='F')toggleFs();
  else if(e.key==='?')help.classList.toggle('open');
}

const toggleFs=()=>document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen?.();
const fsIcon=()=>{const b=document.getElementById('fs');if(!b)return;const on=!!document.fullscreenElement;
  b.innerHTML=`<svg class="ic" viewBox="0 0 24 24" aria-hidden="true">${on?'<path d="M9 3v6H3M15 3v6h6M9 21v-6H3M15 21v-6h6"/>':'<path d="M3 9V3h6M21 9V3h-6M3 15v6h6M21 15v6h-6"/>'}</svg>`;
  b.setAttribute('aria-label',on?'Thoát toàn màn hình (F)':'Toàn màn hình (F)');b.title=b.getAttribute('aria-label')};
addEventListener('fullscreenchange',fsIcon);
Deck.start=()=>{
  if(isExport)document.body.classList.add('export','printing');
  {const b=document.getElementById('fs');if(b){b.onclick=toggleFs;fsIcon()}}
  if(isPresenter){presenterUI();return}
  build();
  document.getElementById('next').onclick=()=>go(cur+1);
  document.getElementById('prev').onclick=()=>go(cur-1);
  document.getElementById('scrim').onclick=closeDrawer;
  document.querySelector('#drawer .x').onclick=closeDrawer;
  document.getElementById('help').onclick=e=>{if(e.target.id==='help')e.currentTarget.classList.remove('open')};
  addEventListener('keydown',keys);
  cur=Math.min(Math.max(+(location.hash.slice(1))||0,0),scenes.length-1);
  render();
};

window.Deck=Deck;
})();
