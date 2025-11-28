document.addEventListener('DOMContentLoaded', () => {
  const API = 'http://localhost:8080/analyze';
  const API_HISTORY = 'http://localhost:8080/history';
  const btn = document.getElementById('btnGenerate');
  const clear = document.getElementById('btnClear');
  const text = document.getElementById('dashboardText');
  const pieCtx = document.getElementById('pieChart').getContext('2d');
  const barCtx = document.getElementById('barChart').getContext('2d');
  const historyLineCtx = document.getElementById('historyLine') ? document.getElementById('historyLine').getContext('2d') : null;
  const historyListEl = document.getElementById('historyList');
  const loadHistoryBtn = document.getElementById('btnLoadHistory');
  const historyLimitEl = document.getElementById('historyLimit');

  let pieChart = new Chart(pieCtx, {type:'pie', data:{labels:['Positive','Negative','Neutral'], datasets:[{data:[1,1,1],backgroundColor:['#22c55e','#ef4444','#f59e0b']}]}, options:{}});
  let barChart = new Chart(barCtx, {type:'bar', data:{labels:['joy','anger','sadness','fear','surprise'], datasets:[{label:'Emotion count',data:[0,0,0,0,0],backgroundColor:['#f97316','#ef4444','#60a5fa','#a78bfa','#34d399']}]}, options:{scales:{y:{beginAtZero:true}}}});
  let historyLineChart = null;
  const dominantLabel = document.getElementById('dominantEmotionLabel');

  btn.addEventListener('click', async () => {
    const t = text.value.trim();
    if (!t) return alert('Please paste text to visualize.');
    btn.disabled = true; btn.textContent = 'Generating...';
    try {
      const res = await fetch(API, {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({text: t})});
      const data = await res.json();
      if (data.error) return alert(data.error);

      // show dominant emotion classification
      if (dominantLabel) {
        const dom = (data.dominantEmotion || 'neutral');
        const score = Number(data.dominantEmotionScore || 0);
        // capitalize first letter
        const labelText = dom.charAt(0).toUpperCase() + dom.slice(1);
        dominantLabel.textContent = `${labelText} (${score.toFixed(2)})`;
      }

      // pie chart
      // Use normalized positive/negative scores returned by backend.
      const positive = Number(data.positiveScore || 0);
      const negative = Number(data.negativeScore || 0);
      // Compute neutral as remainder (clamped)
      let neutral = 1 - (positive + negative);
      if (!isFinite(neutral) || neutral < 0) neutral = 0;
      pieChart.data.datasets[0].data = [positive, negative, neutral];
      pieChart.update();

      // bar chart
      const emo = data.emotions || {};
      const arr = ['joy','anger','sadness','fear','surprise'].map(k => emo[k] || 0);
      barChart.data.datasets[0].data = arr;
      barChart.update();
    } catch (e) { alert('Failed to generate charts. Is backend running?'); }
    finally { btn.disabled=false; btn.textContent='Generate Charts'; }
  });

  clear.addEventListener('click', () => { text.value=''; pieChart.data.datasets[0].data=[1,1,1]; pieChart.update(); barChart.data.datasets[0].data=[0,0,0,0,0]; barChart.update(); });

  // History load and render
  async function fetchHistory(limit) {
    try {
      const url = API_HISTORY + (limit ? ('?limit=' + encodeURIComponent(limit)) : '');
      const resp = await fetch(url);
      if (!resp.ok) throw new Error('History fetch failed');
      const arr = await resp.json();
      return arr;
    } catch (e) {
      alert('Failed to fetch history. Is backend running?');
      return null;
    }
  }

  function renderHistoryList(items) {
    if (!historyListEl) return;
    if (!items || items.length === 0) { historyListEl.innerHTML = '<div style="padding:12px;color:var(--muted)">No history entries found.</div>'; return; }
    // show simple rows
    const rows = items.slice().reverse().map(it => {
      const ts = it.timestamp || it.time || '';
      const s = it.sentiment || '-';
      const dom = it.dominantEmotion || '-';
      const pos = it.positiveScore ?? 0;
      const neg = it.negativeScore ?? 0;
      const src = it.source ? String(it.source) : '-';
      return `<div style="padding:8px;border-bottom:1px solid rgba(255,255,255,0.03)"><div style="font-size:13px;color:var(--muted)">${escapeHtml(ts)} • ${escapeHtml(src)}</div><div style="margin-top:6px"><strong>${escapeHtml(s)}</strong> — ${escapeHtml(dom)} — P:${pos} N:${neg}</div></div>`;
    }).join('');
    historyListEl.innerHTML = rows;
  }

  function escapeHtml(s){ return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

  function renderHistoryChart(items) {
    if (!historyLineCtx) return;
    if (!items || items.length === 0) {
      if (historyLineChart) { historyLineChart.data.labels = []; historyLineChart.data.datasets.forEach(d=>d.data=[]); historyLineChart.update(); }
      return;
    }
    const labels = items.map(it => (it.timestamp || '').slice(0,19));
    const pos = items.map(it => Number(it.positiveScore || 0));
    const neg = items.map(it => Number(it.negativeScore || 0));
    if (!historyLineChart) {
      historyLineChart = new Chart(historyLineCtx, {
        type: 'line',
        data: { labels, datasets: [
          { label: 'Positive', data: pos, borderColor: '#22c55e', backgroundColor: 'rgba(34,197,94,0.12)', fill:true },
          { label: 'Negative', data: neg, borderColor: '#ef4444', backgroundColor: 'rgba(239,68,68,0.12)', fill:true }
        ]},
        options: { responsive:true, maintainAspectRatio:false, scales:{x:{display:true,ticks:{maxRotation:45}}, y:{beginAtZero:true}} }
      });
    } else {
      historyLineChart.data.labels = labels;
      historyLineChart.data.datasets[0].data = pos;
      historyLineChart.data.datasets[1].data = neg;
      historyLineChart.update();
    }
  }

  if (loadHistoryBtn) {
    loadHistoryBtn.addEventListener('click', async () => {
      const limit = historyLimitEl ? Number(historyLimitEl.value) : 50;
      loadHistoryBtn.disabled = true; loadHistoryBtn.textContent = 'Loading...';
      const items = await fetchHistory(limit);
      renderHistoryList(items);
      renderHistoryChart(items || []);
      loadHistoryBtn.disabled = false; loadHistoryBtn.textContent = 'Load History';
    });
  }
});
