(function () {
  'use strict';
  const seen = new Map();
  const mediaExt = /\.(mp4|m4v|webm|mov|mkv|3gp|ts|m3u8|mpd)(?:[?#]|$)/i;
  function add(url, label, mime) {
    if (!url || seen.size >= 80) return;
    try {
      const full = new URL(url, document.baseURI).href;
      if (!/^https?:\/\//i.test(full) || seen.has(full)) return;
      seen.set(full, { url: full, label: String(label || 'Video file').slice(0, 120),
        mime: String(mime || '').slice(0, 100) });
    } catch (_) { /* not an absolute network URL */ }
  }
  document.querySelectorAll('video').forEach((video, i) => {
    const label = (video.getAttribute('title') || document.title || 'Video') + ' #' + (i + 1);
    add(video.currentSrc, label, 'video');
    add(video.getAttribute('src'), label, 'video');
    video.querySelectorAll('source').forEach(source => add(source.src, label, source.type || 'video'));
  });
  document.querySelectorAll('source[src]').forEach(el => {
    const type = el.type || '';
    if (/video|mpegurl|dash/i.test(type) || mediaExt.test(el.src)) add(el.src, document.title, type);
  });
  document.querySelectorAll('a[href]').forEach(el => {
    const url = el.href;
    if (mediaExt.test(url)) add(url, el.textContent.trim().slice(0, 90) || 'Linked video', '');
  });
  document.querySelectorAll('meta[property], meta[name]').forEach(el => {
    const key = (el.getAttribute('property') || el.getAttribute('name') || '').toLowerCase();
    if (['og:video', 'og:video:url', 'og:video:secure_url', 'twitter:player:stream'].includes(key)) {
      add(el.content, 'Page video', 'video');
    }
  });
  if (window.performance && performance.getEntriesByType) {
    performance.getEntriesByType('resource').forEach(entry => {
      if (mediaExt.test(entry.name)) add(entry.name, 'Loaded video resource', '');
    });
  }
  return JSON.stringify([...seen.values()]);
})()
