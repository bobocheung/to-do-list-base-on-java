(function(){
  const el = sel => document.querySelector(sel);
  const listEl = el('#taskList');
  const formEl = el('#taskForm');
  const chkSuggested = el('#chkSuggested');
  const btnRefresh = el('#btnRefresh');
  const calEl = el('#calendar');
  const currentDateLabel = el('#currentDateLabel');
  let view = 'month';
  let anchor = new Date();

  async function fetchTasks() {
    const url = chkSuggested.checked ? '/tasks?suggested=true' : '/tasks';
    const res = await fetch(url);
    const data = await res.json();
    renderTasks(data);
    renderStats(data);
    renderCalendar(data);
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
  function startOfWeek(d){ const x=new Date(d); const day=x.getDay(); x.setDate(x.getDate()-((day+6)%7)); x.setHours(0,0,0,0); return x; }
  function startOfMonth(d){ const x=new Date(d.getFullYear(), d.getMonth(), 1); x.setHours(0,0,0,0); return x; }
  function addDays(d,n){ const x=new Date(d); x.setDate(x.getDate()+n); return x; }
  function fmtDate(d){ return d.toISOString().slice(0,10); }

  async function renderCalendar(tasks){
    if(!calEl) return;
    calEl.innerHTML = '';
    currentDateLabel.textContent = anchor.toISOString().slice(0,10);
    const grid = document.createElement('div'); grid.className='cal-grid';
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
    const map = tasks.reduce((m,t)=>{ if(!t.dueDateTime) return m; const k=t.dueDateTime.slice(0,10); (m[k]||(m[k]=[])).push(t); return m; }, {});
    days.forEach(d=>{
      const cell = document.createElement('div'); cell.className='cal-cell';
      const dateSpan = document.createElement('span'); dateSpan.className='cal-date'; dateSpan.textContent = d.getDate();
      cell.appendChild(dateSpan);
      const k = fmtDate(d);
      (map[k]||[]).forEach(t=>{
        const a = document.createElement('a'); a.href='#'; a.className='cal-item'; a.textContent = t.title; a.title = t.title;
        // 拖拽改期：拖起任務 id
        a.draggable = true;
        a.addEventListener('dragstart', (ev)=>{ ev.dataTransfer.setData('text/task', t.id); });
        a.addEventListener('click', (ev)=>{ev.preventDefault(); openEditModal(t);});
        cell.appendChild(a);
      });
      // 放下到某天 -> reschedule
      cell.addEventListener('dragover', (ev)=>{ if (ev.dataTransfer.types.includes('text/task')) ev.preventDefault(); });
      cell.addEventListener('drop', async (ev)=>{
        ev.preventDefault();
        const taskId = ev.dataTransfer.getData('text/task');
        if(taskId){
          await fetch(`/tasks/${taskId}/reschedule`, { method:'PUT', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({ date: k }).toString() });
          fetchTasks();
        }
      });
      grid.appendChild(cell);
    });
    calEl.appendChild(grid);

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

  document.querySelectorAll('.view-switch [data-view]')?.forEach(btn=>{
    btn.addEventListener('click', ()=>{ document.querySelectorAll('.view-switch [data-view]').forEach(b=>b.classList.remove('primary')); btn.classList.add('primary'); view = btn.getAttribute('data-view'); fetchTasks(); });
  });
  el('#btnPrev')?.addEventListener('click', ()=>{ if(view==='day') anchor=addDays(anchor,-1); else if(view==='week') anchor=addDays(anchor,-7); else anchor=new Date(anchor.getFullYear(), anchor.getMonth()-1, 1); fetchTasks(); });
  el('#btnToday')?.addEventListener('click', ()=>{ anchor=new Date(); fetchTasks(); });
  el('#btnNext')?.addEventListener('click', ()=>{ if(view==='day') anchor=addDays(anchor,1); else if(view==='week') anchor=addDays(anchor,7); else anchor=new Date(anchor.getFullYear(), anchor.getMonth()+1, 1); fetchTasks(); });

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
    const id = modalForm.id.value;
    const body = new URLSearchParams(new FormData(modalForm));
    await fetch(`/tasks/${id}`, { method:'PUT', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body });
    closeModal(); fetchTasks();
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


