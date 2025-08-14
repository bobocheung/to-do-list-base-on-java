(function(){
  const el = sel => document.querySelector(sel);
  const listEl = el('#taskList');
  const formEl = el('#taskForm');
  const chkSuggested = el('#chkSuggested');
  const btnRefresh = el('#btnRefresh');
  const calEl = el('#calendar');
  const currentDateLabel = el('#currentDateLabel');
  const weekStartSelect = el('#weekStartSelect');
  const searchInput = el('#searchInput');
  const statusSelect = el('#statusSelect');
  const prioritySelect = el('#prioritySelect');
  const tagInput = el('#tagInput');
  const calToolbar = el('#calendarToolbar');
  const selCount = el('#selCount');
  const btnMakeNotes = el('#btnMakeNotes');
  const btnClearSel = el('#btnClearSel');
  const btnBatchRescheduleToSel = el('#btnBatchRescheduleToSel');
  const timeline = el('#timeline');
  const timeContainer = el('#timeContainer');
  const weekNoLabel = el('#weekNoLabel');
  const zoomSelect = el('#zoomSelect');
  let view = 'month';
  let anchor = new Date();
  let multiSel = new Set();

  async function fetchTasks() {
    const url = chkSuggested.checked ? '/tasks?suggested=true' : '/tasks';
    const res = await fetch(url);
    const data = await res.json();
    renderTasks(data);
    renderStats(data);
    renderCalendar(data);
    updateShareLink();
  }

  function escapeHtml(str){
    return (str||'').replace(/[&<>"']/g, c=>({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'
    })[c]);
  }

  function renderTasks(tasks){
    listEl.innerHTML = '';
    for(const t of tasks){
      const li = document.createElement('li');
      li.className = 'task-item' + (t.status === 'COMPLETED' ? ' done' : '');
      li.draggable = true;
      li.addEventListener('dragstart', (ev)=>{ ev.dataTransfer.setData('text/plain', t.id); });
      li.addEventListener('dragover', (ev)=>{ ev.preventDefault(); });
      li.addEventListener('drop', (ev)=>{ ev.preventDefault(); const fromId = ev.dataTransfer.getData('text/plain'); if(fromId && fromId!==t.id){ reorder(fromId, t.id); }});
      const tagsHtml = (t.tags||[]).map(tag=>`<span class="tag">${escapeHtml(tag)}</span>`).join(' ');
      const prClass = t.priority === 'CRITICAL' ? 'critical' : (t.priority === 'HIGH' ? 'high' : '');
      li.innerHTML = `
        <div class="selectbox"><input type="checkbox" data-select="${t.id}" /></div>
        <div>
          <h3 class="task-title">${escapeHtml(t.title)}</h3>
          <div class="task-meta">
            狀態：${t.status || ''}
            <span class="pill ${prClass}">優先：${t.priority||''}</span>
            <span class="pill">估時：${t.estimatedMinutes||0} 分</span>
            <span class="pill">截止：${t.dueDateTime||'(無)'}</span>
          </div>
          <div class="task-meta">標籤：${tagsHtml||''}</div>
        </div>
        <div class="actions">
          ${t.status === 'COMPLETED' ? '' : `
            <button data-action="start" data-id="${t.id}" class="btn">開始</button>
            <button data-action="snooze" data-id="${t.id}" class="btn">延後15分</button>
            <button data-action="complete" data-id="${t.id}" class="btn">完成</button>
            <button data-action="delete" data-id="${t.id}" class="btn">刪除</button>
          `}
        </div>
      `;
      li.addEventListener('click', (ev)=>{
        const b = ev.target.closest('button'); if(!b) return;
        const id = b.getAttribute('data-id');
        const act = b.getAttribute('data-action');
        if(act==='complete') completeTask(id);
        else if(act==='delete') deleteTask(id);
        else if(act==='start') startTask(id);
        else if(act==='snooze') snoozeTask(id);
      });
      listEl.appendChild(li);
    }
  }

  async function completeTask(id){
    await fetch(`/tasks/${id}/complete`, { method: 'POST' });
    fetchTasks();
  }

  async function startTask(id){
    await fetch(`/tasks/${id}/start`, { method: 'POST' });
    fetchTasks();
  }

  async function snoozeTask(id){
    await fetch(`/tasks/${id}/snooze`, { method: 'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:'minutes=15' });
    fetchTasks();
  }

  async function deleteTask(id){
    await fetch(`/tasks/${id}`, { method: 'DELETE' });
    fetchTasks();
  }

  async function reorder(fromId, toId){
    await fetch('/tasks/reorder', { method:'PATCH', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({from:fromId, to:toId}).toString() });
    fetchTasks();
  }

  // 任務編輯彈窗（簡易版）
  document.addEventListener('dblclick', async (e)=>{
    const li = e.target.closest('.task-item'); if(!li) return;
    const id = li.querySelector('input[type=checkbox][data-select]')?.getAttribute('data-select');
    if(!id) return;
    const res = await fetch('/tasks'); const tasks = await res.json();
    const t = tasks.find(x=>x.id===id); if(!t) return;
    const title = prompt('標題', t.title||''); if(title===null) return;
    const description = prompt('描述', t.description||''); if(description===null) return;
    const priority = prompt('優先級(LOW/MEDIUM/HIGH/CRITICAL)', t.priority||'MEDIUM'); if(priority===null) return;
    const dueDateTime = prompt('截止(yyyy-MM-dd HH:mm)', t.dueDateTime||''); if(dueDateTime===null) return;
    const estimatedMinutes = prompt('估時(分鐘)', String(t.estimatedMinutes||30)); if(estimatedMinutes===null) return;
    const tags = prompt('標籤(以 ; 分隔)', (t.tags||[]).join(';')); if(tags===null) return;
    await fetch(`/tasks/${id}`, { method:'PUT', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({ title, description, priority, dueDateTime, estimatedMinutes, tags }).toString() });
    fetchTasks();
  });

  // 匯出 JSON
  el('#btnExport').addEventListener('click', async ()=>{
    const res = await fetch('/tasks');
    const data = await res.json();
    const blob = new Blob([JSON.stringify(data, null, 2)], {type:'application/json'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'tasks.json'; a.click();
    URL.revokeObjectURL(url);
  });
  // ICS 匯出（依目前視圖日期範圍）
  el('#btnIcs')?.addEventListener('click', (e)=>{
    e.preventDefault();
    let start, end;
    if (view==='day') { start = end = anchor; }
    else if (view==='week') { const s=startOfWeek(anchor); start=s; end=addDays(s,6); }
    else { const s=new Date(anchor.getFullYear(), anchor.getMonth(), 1); const startW=startOfWeek(s); start=startW; end=addDays(startW,41); }
    const url = `/ics?start=${fmtDate(start)}&end=${fmtDate(end)}`;
    window.location.href = url;
  });

  // 批次完成/刪除
  function getSelectedIds(){ return Array.from(document.querySelectorAll('input[type=checkbox][data-select]:checked')).map(x=>x.getAttribute('data-select')); }
  el('#btnBatchComplete').addEventListener('click', async ()=>{
    const ids = getSelectedIds();
    for (const id of ids) await completeTask(id);
    fetchTasks();
  });
  el('#btnBatchDelete').addEventListener('click', async ()=>{
    const ids = getSelectedIds();
    for (const id of ids) await deleteTask(id);
    fetchTasks();
  });

  // 快捷鍵：r 重新整理，n 聚焦標題欄位
  window.addEventListener('keydown', (e)=>{
    if (e.target && ['INPUT','TEXTAREA','SELECT','BUTTON','A'].includes(e.target.tagName)) return;
    if (e.key === 'r') { e.preventDefault(); fetchTasks(); }
    if (e.key === 'n') { e.preventDefault(); formEl.querySelector('input[name=title]').focus(); }
  });

  formEl.addEventListener('submit', async (e)=>{
    e.preventDefault();
    const fd = new FormData(formEl);
    const body = new URLSearchParams();
    for (const [k,v] of fd.entries()) body.append(k, v);
    // 將可空白的擴充欄位也提交（後端目前只使用基本欄位，保留擴充）
    await fetch('/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    });
    formEl.reset();
    fetchTasks();
  });

  chkSuggested.addEventListener('change', fetchTasks);
  btnRefresh.addEventListener('click', fetchTasks);

  fetchTasks();

  function renderStats(tasks){
    const svg = document.getElementById('chart');
    if(!svg) return;
    const w = 600, h = 160, pad = 24; // 手繪感：用不規則線條
    svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
    svg.innerHTML = '';
    // 統計：近 7 天完成數
    const days = Array.from({length:7}, (_,i)=>i).map(i=>{
      const d = new Date(); d.setDate(d.getDate() - (6-i)); return d.toISOString().slice(0,10);
    });
    const map = new Map(days.map(d=>[d,0]));
    for(const t of tasks){
      if(t.status==='COMPLETED' && t.completedAt){
        const day = t.completedAt.slice(0,10);
        if(map.has(day)) map.set(day, map.get(day)+1);
      }
    }
    const vals = Array.from(map.values());
    const maxV = Math.max(1, ...vals);
    const barW = (w - pad*2) / vals.length * 0.7;
    vals.forEach((v,idx)=>{
      const x = pad + idx * ((w - pad*2)/vals.length) + 6*Math.random();
      const y = h - pad - (v/maxV) * (h - pad*2) - 3*Math.random();
      const rect = document.createElementNS('http://www.w3.org/2000/svg','rect');
      rect.setAttribute('x', x);
      rect.setAttribute('y', y);
      rect.setAttribute('width', barW);
      rect.setAttribute('height', (h - pad - y));
      rect.setAttribute('fill', '#ffe1de');
      rect.setAttribute('stroke', '#222');
      rect.setAttribute('stroke-width', '3');
      svg.appendChild(rect);
      // 數值標記
      const tx = document.createElementNS('http://www.w3.org/2000/svg','text');
      tx.setAttribute('x', x + barW/2); tx.setAttribute('y', y - 6);
      tx.setAttribute('text-anchor','middle'); tx.setAttribute('font-size','12'); tx.textContent = v;
      svg.appendChild(tx);
      // 日期刻度
      const dayLbl = document.createElementNS('http://www.w3.org/2000/svg','text');
      dayLbl.setAttribute('x', x + barW/2); dayLbl.setAttribute('y', h - 6);
      dayLbl.setAttribute('text-anchor','middle'); dayLbl.setAttribute('font-size','10');
      dayLbl.textContent = days[idx].slice(5); // MM-dd
      svg.appendChild(dayLbl);
    });
    // 手繪邊框
    const frame = document.createElementNS('http://www.w3.org/2000/svg','rect');
    frame.setAttribute('x','3'); frame.setAttribute('y','3'); frame.setAttribute('width', w-6); frame.setAttribute('height', h-6);
    frame.setAttribute('fill','none'); frame.setAttribute('stroke','#222'); frame.setAttribute('stroke-width','3');
    svg.appendChild(frame);
  }

  // ======= Calendar (Notion 風格簡化，手繪格線) =======
  function getWeekStart(){ return (localStorage.getItem('weekStart')||'mon')==='sun' ? 0 : 1; }
  function startOfWeek(d){ const x=new Date(d); const day=x.getDay(); const ws=getWeekStart(); const delta=(day-(ws===1?1:0)+7)%7; x.setDate(x.getDate()-delta); x.setHours(0,0,0,0); return x; }
  function startOfMonth(d){ const x=new Date(d.getFullYear(), d.getMonth(), 1); x.setHours(0,0,0,0); return x; }
  function addDays(d,n){ const x=new Date(d); x.setDate(x.getDate()+n); return x; }
  function fmtDate(d){ return d.toISOString().slice(0,10); }

  async function renderCalendar(tasks){
    if(!calEl) return;
    calEl.innerHTML = '';
    currentDateLabel.textContent = anchor.toISOString().slice(0,10);
    const grid = document.createElement('div'); grid.className='cal-grid';
    // 在 Day/Week 視圖同步渲染時間軸
    if (view!=='month') renderTimeline(tasks);
    else { timeline.classList.add('hidden'); timeContainer.innerHTML=''; }
    let days = [];
    if (view==='day') {
      days = [new Date(anchor)];
      grid.style.gridTemplateColumns = '1fr';
    } else if (view==='week') {
      const s = startOfWeek(anchor); for(let i=0;i<7;i++) days.push(addDays(s,i));
      grid.style.gridTemplateColumns = 'repeat(7, 1fr)';
    } else { // month
      const s = startOfMonth(anchor); const start = startOfWeek(s); for(let i=0;i<42;i++) days.push(addDays(start,i));
      grid.style.gridTemplateColumns = 'repeat(7, 1fr)';
    }
    // 搜尋/過濾
    const q = (searchInput?.value||'').trim().toLowerCase();
    const st = statusSelect?.value||'';
    const pr = prioritySelect?.value||'';
    const tg = (tagInput?.value||'').trim().toLowerCase();
    if (q) tasks = tasks.filter(t => (t.title||'').toLowerCase().includes(q) || (t.tags||[]).some(tag=>tag.toLowerCase().includes(q)) );
    if (st) tasks = tasks.filter(t=> (t.status||'').toUpperCase()===st);
    if (pr) tasks = tasks.filter(t=> (t.priority||'').toUpperCase()===pr);
    if (tg) tasks = tasks.filter(t=> (t.tags||[]).some(x=>x.toLowerCase()===tg));
    const map = tasks.reduce((m,t)=>{ if(!t.dueDateTime) return m; const k=t.dueDateTime.slice(0,10); (m[k]||(m[k]=[])).push(t); return m; }, {});
    // 熱力圖模式（僅月視圖）：根據某日完成數量改變背景濃淡
    const heat = (el,count)=>{ if(!heatmapEnabled || view!=='month') return; const alpha=Math.min(0.35, 0.06*count); el.style.background = `rgba(226, 121, 95, ${alpha})`; };
    days.forEach(d=>{
      const cell = document.createElement('div'); cell.className='cal-cell';
      const dateSpan = document.createElement('span'); dateSpan.className='cal-date'; dateSpan.textContent = d.getDate();
      cell.appendChild(dateSpan);
      const k = fmtDate(d);
      heat(cell, (map[k]||[]).filter(x=>x.status==='COMPLETED').length);
      // 多日選取（Shift 點擊）
      cell.addEventListener('click', (ev)=>{ if (!ev.shiftKey) return; if (multiSel.has(k)) multiSel.delete(k); else multiSel.add(k); updateSelUi(); });
      (map[k]||[]).forEach(t=>{
        const a = document.createElement('a'); a.href='#'; a.className='cal-item'; a.textContent = t.title; a.title = t.title;
        // 類別色彩
        const cat = (t.category||'').toLowerCase(); if(cat==='work') a.classList.add('cat-work'); else if(cat==='personal') a.classList.add('cat-personal'); else if(cat==='study') a.classList.add('cat-study');
        // 拖拽改期：拖起任務 id
        a.draggable = true;
        a.addEventListener('dragstart', (ev)=>{ ev.dataTransfer.setData('text/task', t.id); });
        a.addEventListener('click', (ev)=>{ev.preventDefault(); openEditModal(t);});
        cell.appendChild(a);
      });
      // 放下到某天 -> reschedule
      cell.addEventListener('dragover', (ev)=>{ if (ev.dataTransfer.types.includes('text/task')) { ev.preventDefault(); cell.classList.add('drag-hover'); }});
      cell.addEventListener('dragleave', ()=> cell.classList.remove('drag-hover'));
      cell.addEventListener('drop', async (ev)=>{
        ev.preventDefault();
        cell.classList.remove('drag-hover');
        const taskId = ev.dataTransfer.getData('text/task');
        if(taskId){
          await fetch(`/tasks/${taskId}/reschedule`, { method:'PUT', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({ date: k }).toString() });
          fetchTasks();
        }
      });
      grid.appendChild(cell);
    });
    calEl.appendChild(grid);
    // 顯示 ISO 週次
    weekNoLabel.textContent = view==='week' ? `Week ${getIsoWeek(anchor)}` : '';

    // 日曆記事（notes）：取目前區間的 notes 並渲染
    const start = days[0]; const end = days[days.length-1];
    try {
      const res = await fetch(`/notes?start=${fmtDate(start)}&end=${fmtDate(end)}`);
      const notes = await res.json();
      const noteMap = notes.reduce((m,n)=>{ (m[n.date]||(m[n.date]=[])).push(n); return m; }, {});
      Array.from(grid.children).forEach((cell,idx)=>{
        const k = fmtDate(days[idx]);
        (noteMap[k]||[]).forEach(n=>{
          const a = document.createElement('a'); a.href='#'; a.className='cal-item cal-note'; a.textContent = n.content.slice(0,18);
          a.title = n.content;
          a.addEventListener('click', (ev)=>{ ev.preventDefault(); openNoteModal(k, n); });
          cell.appendChild(a);
        });
        // 新增記事入口
        const addBtn = document.createElement('button'); addBtn.className='btn'; addBtn.textContent='＋'; addBtn.style.marginTop='6px';
        addBtn.addEventListener('click', ()=>openNoteModal(k));
        cell.appendChild(addBtn);
      });
    } catch(e) {}
  }

  function renderTimeline(tasks){
    timeline.classList.remove('hidden');
    timeContainer.innerHTML='';
    const hours = 24; const unit = parseInt(zoomSelect?.value||'30',10); const hourHeight = unit===60?75:(unit===30?37.5:18.75);
    const cols = view==='day' ? 1 : 7;
    timeContainer.style.gridTemplateColumns = `repeat(${cols}, 1fr)`;
    const base = view==='day' ? [new Date(anchor)] : Array.from({length:7},(_,i)=>addDays(startOfWeek(anchor), i));
    // 過濾範圍
    const start = base[0], end = base[base.length-1];
    const map = tasks.reduce((m,t)=>{ if(!t.dueDateTime) return m; const d=new Date(t.dueDateTime); const k = d.toISOString().slice(0,10); (m[k]||(m[k]=[])).push(t); return m; }, {});
    base.forEach(d=>{
      const col = document.createElement('div'); col.className='time-col';
      // 小時刻度
      for(let h=0; h<hours; h++){
        const line = document.createElement('div'); line.className='time-hour'; line.style.top = `${h*hourHeight}px`;
        col.appendChild(line);
      }
      // 事件方塊（用 dueDate 與 estimatedMinutes 粗略估計）
      const key = fmtDate(d);
      (map[key]||[]).forEach(t=>{
        if(!t.dueDateTime){ return; }
        const dt = new Date(t.dueDateTime);
        const endMin = dt.getHours()*60 + dt.getMinutes();
        const startMin = Math.max(0, endMin - (t.estimatedMinutes||30));
        const top = startMin/60*hourHeight;
        const height = Math.max(18, (t.estimatedMinutes||30)/60*hourHeight);
        const ev = document.createElement('div'); ev.className='event-block'; ev.style.top=`${top}px`; ev.style.height=`${height}px`;
        ev.textContent = t.title;
        const cat=(t.category||'').toLowerCase(); if(cat==='work') ev.style.background='#e6f7ff'; else if(cat==='personal') ev.style.background='#fff1f0'; else if(cat==='study') ev.style.background='#f9fbe7';
        const handle = document.createElement('div'); handle.className='event-handle'; ev.appendChild(handle);
        // 拖拽上下調整時長（釋放後呼叫 API 更新 estimatedMinutes）
        let resizing=false, startY=0, startH=0;
        handle.addEventListener('mousedown',(e)=>{ resizing=true; startY=e.clientY; startH=ev.offsetHeight; e.preventDefault(); });
        document.addEventListener('mousemove',(e)=>{ if(!resizing) return; const dy=e.clientY-startY; ev.style.height=Math.max(18, startH+dy)+'px'; });
        document.addEventListener('mouseup',async ()=>{ if(resizing){ resizing=false; const h=parseFloat(ev.style.height); const minutes=Math.max(5, Math.round(h/hourHeight*60)); await fetch(`/tasks/${t.id}/duration`, { method:'PATCH', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:new URLSearchParams({minutes}).toString() }); fetchTasks(); }});
        col.appendChild(ev);
      });
      timeContainer.appendChild(col);
    });
  }

  function getIsoWeek(d){ const date=new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())); const dayNum=(date.getUTCDay()||7); date.setUTCDate(date.getUTCDate()+4-dayNum); const yearStart=new Date(Date.UTC(date.getUTCFullYear(),0,1)); return Math.ceil((((date-yearStart)/86400000)+1)/7); }
  function updateShareLink(){ const params=new URLSearchParams({ view, date: anchor.toISOString().slice(0,10) }); history.replaceState(null,'',`?${params.toString()}`); }
  (function readShareLink(){ const u=new URL(location.href); const v=u.searchParams.get('view'); const d=u.searchParams.get('date'); if(v){ view=v; document.querySelectorAll('.view-switch [data-view]').forEach(b=>b.classList.toggle('primary', b.getAttribute('data-view')===v)); } if(d){ const [y,m,dd]=d.split('-'); anchor=new Date(parseInt(y), parseInt(m)-1, parseInt(dd)); } })();
  zoomSelect?.addEventListener('change', fetchTasks);

  document.querySelectorAll('.view-switch [data-view]')?.forEach(btn=>{
    btn.addEventListener('click', ()=>{ document.querySelectorAll('.view-switch [data-view]').forEach(b=>b.classList.remove('primary')); btn.classList.add('primary'); view = btn.getAttribute('data-view'); fetchTasks(); });
  });
  el('#btnPrev')?.addEventListener('click', ()=>{ if(view==='day') anchor=addDays(anchor,-1); else if(view==='week') anchor=addDays(anchor,-7); else anchor=new Date(anchor.getFullYear(), anchor.getMonth()-1, 1); fetchTasks(); });
  el('#btnToday')?.addEventListener('click', ()=>{ anchor=new Date(); fetchTasks(); });
  el('#btnNext')?.addEventListener('click', ()=>{ if(view==='day') anchor=addDays(anchor,1); else if(view==='week') anchor=addDays(anchor,7); else anchor=new Date(anchor.getFullYear(), anchor.getMonth()+1, 1); fetchTasks(); });
  el('#btnQuickToday')?.addEventListener('click', async ()=>{
    const title = prompt('快速新增（今天）標題'); if(!title) return;
    const due = new Date(); const yyyy=due.getFullYear(); const mm=('0'+(due.getMonth()+1)).slice(-2); const dd=('0'+due.getDate()).slice(-2); const hh='12', mi='00';
    await fetch('/tasks', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({ title, dueDateTime: `${yyyy}-${mm}-${dd} ${hh}:${mi}`, priority:'MEDIUM', estimatedMinutes:'30' }).toString() });
    fetchTasks();
  });
  weekStartSelect?.addEventListener('change', ()=>{ localStorage.setItem('weekStart', weekStartSelect.value); fetchTasks(); });
  (function initWeekStart(){ const ws=localStorage.getItem('weekStart')||'mon'; weekStartSelect.value=ws; })();
  [searchInput, statusSelect, prioritySelect, tagInput].forEach(x=> x && x.addEventListener('change', fetchTasks));
  let heatmapEnabled=false; el('#btnHeatmap')?.addEventListener('click', ()=>{ heatmapEnabled=!heatmapEnabled; fetchTasks(); });
  el('#btnShare')?.addEventListener('click', ()=>{ navigator.clipboard?.writeText(location.href); alert('連結已複製'); });

  function updateSelUi(){
    selCount.textContent = multiSel.size;
    calToolbar.classList.toggle('hidden', multiSel.size===0);
    document.querySelectorAll('.cal-cell').forEach((cell,idx)=>{
      const k = fmtDate(view==='day' ? anchor : (view==='week' ? addDays(startOfWeek(anchor), idx) : addDays(startOfWeek(new Date(anchor.getFullYear(), anchor.getMonth(), 1)), idx)));
      if (multiSel.has(k)) cell.classList.add('sel'); else cell.classList.remove('sel');
    });
  }
  btnClearSel?.addEventListener('click', ()=>{ multiSel.clear(); updateSelUi(); });
  btnMakeNotes?.addEventListener('click', async ()=>{
    const content = prompt(`於 ${multiSel.size} 天建立相同記事，請輸入內容`); if(!content) return;
    for (const k of multiSel) {
      await fetch('/notes', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({ date:k, content }).toString() });
    }
    multiSel.clear(); fetchTasks();
  });
  btnBatchRescheduleToSel?.addEventListener('click', async ()=>{
    const ids = Array.from(document.querySelectorAll('input[type=checkbox][data-select]:checked')).map(x=>x.getAttribute('data-select'));
    if(ids.length===0 || multiSel.size===0){ alert('請先勾選清單中的任務，並在日曆選擇至少一天'); return; }
    const target = Array.from(multiSel).sort()[0];
    for (const id of ids) {
      await fetch(`/tasks/${id}/reschedule`, { method:'PUT', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({ date: target }).toString() });
    }
    fetchTasks();
  });

  // 視圖切換快捷鍵 1/2/3，T 回今天
  window.addEventListener('keydown', (e)=>{
    if (e.target && ['INPUT','TEXTAREA','SELECT','BUTTON','A'].includes(e.target.tagName)) return;
    if (e.key==='1'){ view='day'; fetchTasks(); }
    if (e.key==='2'){ view='week'; fetchTasks(); }
    if (e.key==='3'){ view='month'; fetchTasks(); }
    if (e.key.toLowerCase()==='t'){ anchor=new Date(); fetchTasks(); }
    if (e.key==='ArrowLeft'){ if(view==='day') anchor=addDays(anchor,-1); else if(view==='week') anchor=addDays(anchor,-7); else anchor=new Date(anchor.getFullYear(), anchor.getMonth()-1, 1); fetchTasks(); }
    if (e.key==='ArrowRight'){ if(view==='day') anchor=addDays(anchor,1); else if(view==='week') anchor=addDays(anchor,7); else anchor=new Date(anchor.getFullYear(), anchor.getMonth()+1, 1); fetchTasks(); }
    if (e.key==='Enter' && view!=='month'){ const title=prompt('快速新增於當前視圖日期'); if(title){ const yyyy=anchor.getFullYear(); const mm=('0'+(anchor.getMonth()+1)).slice(-2); const dd=('0'+anchor.getDate()).slice(-2); fetch('/tasks', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:new URLSearchParams({title, dueDateTime:`${yyyy}-${mm}-${dd} 12:00`, estimatedMinutes:'30', priority:'MEDIUM'}).toString()}).then(()=>fetchTasks()); }}
  });

  // ======= Modal (手繪風編輯表單) =======
  const modal = el('#modal');
  const modalForm = el('#modalForm');
  const btnModalDelete = el('#btnModalDelete');
  // 可複用於 Task 或 Note
  function openEditModal(task){
    modal.classList.remove('hidden'); modal.setAttribute('aria-hidden','false');
    modalForm.id.value = task.id;
    modalForm.title.value = task.title||'';
    modalForm.description.value = task.description||'';
    modalForm.priority.value = task.priority||'MEDIUM';
    modalForm.status.value = task.status||'PENDING';
    modalForm.dueDateTime.value = task.dueDateTime||'';
    modalForm.estimatedMinutes.value = task.estimatedMinutes||30;
    modalForm.category.value = task.category||'';
    modalForm.actualMinutes.value = task.actualMinutes||'';
    modalForm.reminderBeforeMinutes.value = task.reminderBeforeMinutes||'';
    modalForm.tags.value = (task.tags||[]).join(';');
    modalForm.setAttribute('data-kind','task');
  }
  function closeModal(){ modal.classList.add('hidden'); modal.setAttribute('aria-hidden','true'); }
  el('#btnModalCancel')?.addEventListener('click', closeModal);
  btnModalDelete?.addEventListener('click', async ()=>{
    const id = modalForm.id.value;
    if (modalForm.getAttribute('data-kind') === 'note') {
      // notes 刪除
      if (!id) { closeModal(); return; }
      await fetch('/notes', { method:'DELETE', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({id}).toString() });
    } else {
      // tasks 刪除
      if (!id) { closeModal(); return; }
      await fetch(`/tasks/${id}`, { method:'DELETE' });
    }
    closeModal(); fetchTasks();
  });
  modalForm?.addEventListener('submit', async (e)=>{
    e.preventDefault();
    if (modalForm.getAttribute('data-kind')==='task'){
      const id = modalForm.id.value;
      const body = new URLSearchParams(new FormData(modalForm));
      await fetch(`/tasks/${id}`, { method:'PUT', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body });
      closeModal(); fetchTasks();
      return;
    }
  });

  // Note Modal（簡化：用同一張卡，但只啟用 content/date/id）
  function openNoteModal(date, note){
    modal.classList.remove('hidden'); modal.setAttribute('aria-hidden','false');
    modalForm.reset();
    modalForm.id.value = note? note.id : '';
    modalForm.title.value = note? ('記事：'+(note.content||'').slice(0,12)) : '記事';
    modalForm.description.value = note? note.content||'' : '';
    modalForm.priority.value = 'MEDIUM';
    modalForm.status.value = 'PENDING';
    modalForm.dueDateTime.value = date + ' 12:00';
    modalForm.estimatedMinutes.value = 15;
    modalForm.category.value = 'note';
    modalForm.actualMinutes.value = '';
    modalForm.reminderBeforeMinutes.value = '';
    modalForm.tags.value = 'note';
    // 暫存一個旗標以區分儲存到 notes 還是 tasks
    modalForm.setAttribute('data-kind','note');
  }
  modalForm?.addEventListener('submit', async (e)=>{
    if (modalForm.getAttribute('data-kind') !== 'note') return; // 由上面的 submit 處理 tasks
    e.preventDefault();
    const id = modalForm.id.value || '';
    const date = (modalForm.dueDateTime.value||'').slice(0,10) || new Date().toISOString().slice(0,10);
    const content = modalForm.description.value || modalForm.title.value || '記事';
    const body = new URLSearchParams({ id, date, content });
    await fetch('/notes', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body });
    modalForm.removeAttribute('data-kind');
    closeModal(); fetchTasks();
  }, true);
})();


