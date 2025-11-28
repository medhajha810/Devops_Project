(function(){
  // Simple theme toggle using data-theme attribute on <html>
  const storageKey = 'sa_theme';
  function getPreferred() {
    const stored = localStorage.getItem(storageKey);
    if (stored) return stored;
    // prefer dark if user prefers dark media query
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function applyTheme(theme) {
    if (!theme) theme = 'light';
    document.documentElement.setAttribute('data-theme', theme);
    // Update toggle button icons if present
    document.querySelectorAll('#themeToggle').forEach(btn => {
      try {
        btn.textContent = theme === 'dark' ? '🌙' : '☀️';
      } catch (e){}
    });
  }

  function toggleTheme() {
    const cur = document.documentElement.getAttribute('data-theme') || 'light';
    const next = cur === 'dark' ? 'light' : 'dark';
    localStorage.setItem(storageKey, next);
    applyTheme(next);
    // For Chart.js charts: try to update their legends/labels by re-drawing if needed
    try {
      if (window.Chart && Array.isArray(window.Chart.instances)) {
        Object.values(window.Chart.instances).forEach(c => c.update());
      }
    } catch (e) { /* ignore */ }
  }

  // initialize
  const initial = getPreferred();
  applyTheme(initial);

  // attach to any existing toggle button(s)
  document.addEventListener('click', (e) => {
    const t = e.target;
    if (t && (t.id === 'themeToggle' || t.closest && t.closest('#themeToggle'))) {
      e.preventDefault();
      toggleTheme();
    }
  }, false);

  // also expose on window for manual control
  window.SATheme = { applyTheme, toggleTheme };
})();
