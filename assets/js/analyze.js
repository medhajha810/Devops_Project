document.addEventListener('DOMContentLoaded', () => {
    // --- 1. CONFIGURATION AND ELEMENT SETUP ---
    const API = 'http://localhost:8080/analyze';
    const API_FILE = 'http://localhost:8080/analyze/file';
    const API_URL = 'http://localhost:8080/analyze/url';
    const btn = document.getElementById('btnAnalyze');
    const clear = document.getElementById('btnClear');
    const feedback = document.getElementById('feedback');
    const resultCard = document.getElementById('resultCard');
    const resultSentiment = document.getElementById('resultSentiment');
    const resultModel = document.getElementById('resultModel');
    const resultDetails = document.getElementById('resultDetails');
    
    // Charts and Highlights
    const miniBarEl = document.getElementById('miniBar');
    const miniPieEl = document.getElementById('miniPie');
    const tokenHighlightsEl = document.getElementById('tokenHighlights');

    // Dynamic inputs setup (unchanged)
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = '.txt';

    const urlInput = document.createElement('input');
    urlInput.type = 'url';
    urlInput.placeholder = 'https://example.com/review.html';
    urlInput.className = 'px-4 py-2 rounded-xl border border-gray-300 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-indigo transition duration-150 flex-grow';

    const controls = document.querySelector('.controls');
    if (controls) {
        const fileBtn = document.createElement('button');
        fileBtn.className = 'px-6 py-3 rounded-xl border border-gray-300 text-gray-700 font-medium hover:bg-gray-100 transition duration-150';
        fileBtn.textContent = 'Upload File';
        fileBtn.type = 'button'; 
        fileBtn.addEventListener('click', () => fileInput.click());

        fileInput.addEventListener('change', async (e) => {
            const f = e.target.files[0];
            if (!f) return;
            btn.disabled = true; btn.textContent = 'Analyzing file...';
            const form = new FormData(); form.append('file', f);
            try {
                const resp = await fetch(API_FILE, {method:'POST', body: form});
                const data = await resp.json();
                showResult(data);
            } catch (err) { alert('File upload failed.'); }
            finally { btn.disabled=false; btn.textContent='Analyze Text'; }
        });
        
        controls.appendChild(fileBtn);
        controls.appendChild(urlInput);
    }
    
    // 🔥 ULTIMATE, GLOBAL FIX FOR PAGE RELOAD: CATCH ALL FORM SUBMISSIONS
    // This listener is attached to the document and runs in the capture phase,
    // stopping any form submission from anywhere on the page instantly.
    document.addEventListener('submit', (e) => {
        console.warn('--- GLOBAL FORM SUBMISSION CATCH AND PREVENT ---');
        e.preventDefault();
        // Optional: If the submit came from the Analyze button, manually trigger the analysis
        if (e.target.contains(btn)) {
             btn.click();
        }
    }, true); // Use capture phase to intercept the event before it bubbles up

    // --- 2. HELPER FUNCTIONS ---

    function debounce(fn, wait) {
        let t;
        return function(...args) { clearTimeout(t); t = setTimeout(() => fn.apply(this,args), wait); };
    }

    function hideResult() {
        if (resultCard) resultCard.classList.add('hidden');
    }

    async function liveAnalyze(text) {
        const trimmedText = text.trim();
        if (!trimmedText || trimmedText.length < 5) {
            hideResult();
            return;
        }
        
        btn.disabled = true;
        
        try {
            const res = await fetch(API, {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({text: trimmedText})});
            
            const data = await res.json();
            
            if (feedback.value.trim() === trimmedText && data && !data.error) {
                showResult(data);
            } else {
                 hideResult();
            }
        } catch (e) {
            console.warn('Live analyze failed', e);
            hideResult();
        } finally {
            btn.disabled = false;
        }
    }

    function safeNumber(v) { return (typeof v === 'number' && isFinite(v)) ? v : 0; }

    function colorForLabel(label) {
        if (!label) return '#D1D5DB';
        const l = label.toLowerCase();
        if (l.includes('pos') || l.includes('joy') || l.includes('happy')) return '#FBBF24';
        if (l.includes('neg') || l.includes('anger') || l.includes('angry')) return '#EF4444';
        if (l.includes('sad')) return '#60A5FA';
        if (l.includes('fear')) return '#8B5CF6';
        if (l.includes('surprise') || l.includes('surpr')) return '#06B6D4';
        if (l.includes('negation') || l.includes("n't") || l.includes('not')) return '#F97316';
        return '#9CA3AF';
    }

    function escapeHtml(s) { 
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }
    
    let miniBarChart = null;
    let miniPieChart = null;
    

    function updateMiniCharts(data) {
        // Backend may provide `emotions` (new) or `emotionScores` (older). Prefer `emotions`.
        const emotionScores = data.emotions || data.emotionScores || {};
        const defaultLabels = ['joy','anger','sadness','fear','surprise'];
        const labels = (Object.keys(emotionScores).length > 0) ? Object.keys(emotionScores) : defaultLabels;
        const values = labels.map(l => {
            // allow either exact key or lowercase lookup
            return safeNumber(emotionScores[l] ?? emotionScores[l.toLowerCase()] ?? 0);
        });

        if (miniBarEl) {
            if (!miniBarChart) {
                miniBarChart = new Chart(miniBarEl.getContext('2d'), {
                    type: 'bar',
                    data: { labels, datasets: [{ label: 'Emotion score', data: values, backgroundColor: labels.map(l=>colorForLabel(l)) }] },
                    options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales:{y:{beginAtZero:true, max:1}} }
                });
            } else {
                miniBarChart.data.labels = labels;
                miniBarChart.data.datasets[0].data = values;
                miniBarChart.data.datasets[0].backgroundColor = labels.map(l=>colorForLabel(l));
                miniBarChart.update();
            }
        }

        if (miniPieEl) {
            const pos = safeNumber(data.positiveScore);
            const neg = safeNumber(data.negativeScore);
            let neutral = 1 - (pos + neg);
            if (!isFinite(neutral) || neutral < 0) neutral = 0;
            const pieLabels = ['Positive','Negative','Neutral'];
            const pieValues = [pos, neg, neutral];
            const pieColors = ['#10B981','#EF4444','#9CA3AF'];
            if (!miniPieChart) {
                miniPieChart = new Chart(miniPieEl.getContext('2d'), {
                    type: 'doughnut',
                    data: { labels: pieLabels, datasets: [{ data: pieValues, backgroundColor: pieColors }] },
                    options: { responsive:true, maintainAspectRatio:false, plugins: { legend: { position: 'bottom' } } }
                });
            } else {
                miniPieChart.data.datasets[0].data = pieValues;
                miniPieChart.update();
            }
        }
    }

    function renderTokenHighlights(data) {
        if (!tokenHighlightsEl) return;
        const map = data.tokenHighlights || {};
        const text = feedback.value || '';
        if (!text.trim()) { tokenHighlightsEl.innerHTML = '<em class="text-gray-500">No text to highlight.</em>'; return; }
        
        const safeText = escapeHtml(text);
        const parts = safeText.split(/(\s+)/);
        
        const out = parts.map(part => {
            if (!part) return '';
            if (/^\s+$/.test(part)) return part;

            const key = part.replace(/^[^\w]+|[^\w]+$/g,'').toLowerCase();
            const label = map[key];
            
            if (label) {
                const color = colorForLabel(label);
                return `<span title="${label}" class="font-semibold" style="background:${color};padding:2px 6px;border-radius:6px;color:#111;">${part}</span>`;
            }
            return part;
        }).join('');
        
        tokenHighlightsEl.innerHTML = out;
    }


    function showResult(data) {
        if (!data || data.error) return alert(data.error || 'Analysis failed.');
        
        resultSentiment.textContent = data.sentiment || 'Neutral';
        // style the sentiment badge for clear contrast
        try {
            const s = (data.sentiment || data.dominantEmotion || 'neutral').toString().toLowerCase();
            if (s.includes('pos') || s.includes('joy') || s.includes('happy')) {
                resultSentiment.style.background = '#10B981';
                resultSentiment.style.color = '#ffffff';
                resultSentiment.style.borderColor = 'transparent';
            } else if (s.includes('neg') || s.includes('anger') || s.includes('angry')) {
                resultSentiment.style.background = '#ef4444';
                resultSentiment.style.color = '#ffffff';
                resultSentiment.style.borderColor = 'transparent';
            } else {
                resultSentiment.style.background = 'var(--card-bg)';
                resultSentiment.style.color = 'var(--text)';
                resultSentiment.style.borderColor = 'var(--border-muted)';
            }
        } catch (e) { /* ignore styling errors */ }
        resultModel.textContent = data.modelVersion || '-';
        const rmv = document.getElementById('resultModelVersion');
        if (rmv) rmv.textContent = data.modelVersion || '-';
        
        const langEl = document.getElementById('resultLanguage');
        if (langEl) langEl.textContent = data.language || 'en';

        let detailsText = `Positive=${safeNumber(data.positiveScore).toFixed(2)} Negative=${safeNumber(data.negativeScore).toFixed(2)}`;
        
        if (data.emotions) {
            const emo = data.emotions;
            detailsText += ' | ' + Object.entries(emo).map(e=>`${e[0]}:${e[1].toFixed(2)}`).join(', ');
        }
        resultDetails.textContent = detailsText;
        
        resultCard.classList.remove('hidden');
        updateMiniCharts(data);
        renderTokenHighlights(data);
    }

    // --- 3. EVENT LISTENERS ---

    // Initial Chart setup (so charts exist even with no data)
    try {
        if (miniBarEl && typeof Chart !== 'undefined' && !miniBarChart) {
            const labels = ['joy','anger','sadness','fear','surprise'];
            miniBarChart = new Chart(miniBarEl.getContext('2d'), {
                type: 'bar', data: { labels, datasets: [{ label: 'Emotion score', data: [0,0,0,0,0], backgroundColor: labels.map(l=>colorForLabel(l)) }] },
                options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales:{y:{beginAtZero:true, max:1}} }
            });
        }
        if (miniPieEl && typeof Chart !== 'undefined' && !miniPieChart) {
            miniPieChart = new Chart(miniPieEl.getContext('2d'), { 
                type: 'doughnut', data: { labels:['Positive','Negative','Neutral'], datasets:[{ data: [0,0,1], backgroundColor:['#10B981','#EF4444','#9CA3AF'] }] }, 
                options:{responsive:true, maintainAspectRatio:false, plugins: { legend: { position: 'bottom' } }} 
            });
        }
    } catch (e) { console.warn('Chart init failed', e); }


    // LIVE PREDICTION: Use INPUT event again.
    const liveDebounced = debounce(() => liveAnalyze(feedback.value.trim()), 700);
    feedback.addEventListener('input', liveDebounced);


    // Manual Analyze Button Click
    btn.addEventListener('click', async (e) => {
        // Essential: Prevent default action for the specific button (double safeguard)
        e.preventDefault(); 
        
        const text = feedback.value.trim();
        const urlVal = urlInput.value && urlInput.value.trim();
        
        if (urlVal) {
            btn.disabled = true; btn.textContent = 'Analyzing URL...';
            try {
                const resp = await fetch(API_URL + '?url=' + encodeURIComponent(urlVal), {method:'POST'});
                const data = await resp.json();
                showResult(data);
            } catch (e) { alert('URL analyze failed.'); }
            finally { btn.disabled=false; btn.textContent='Analyze Text'; }
            return;
        }

        if (!text) return alert('Please enter text to analyze or provide a URL/file.');
        btn.disabled = true; btn.textContent = 'Analyzing...';
        try {
            const res = await fetch(API, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text})});
            const data = await res.json();
            showResult(data);
        } catch (e) {
            alert('Failed to call backend. Ensure Spring Boot app is running on port 8080.');
        } finally { btn.disabled = false; btn.textContent = 'Analyze Text'; }
    });

    // Clear Button Click
    if (clear) clear.addEventListener('click', () => { 
        feedback.value=''; 
        urlInput.value = '';
        hideResult();
        if (tokenHighlightsEl) tokenHighlightsEl.innerHTML = '<em class="text-gray-500">No text to highlight.</em>';
        // Ensure no analysis runs after clear
        liveAnalyze('');
    });
});