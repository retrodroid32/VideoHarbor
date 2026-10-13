'use strict';
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const script = fs.readFileSync('app/src/main/assets/video_detector.js', 'utf8');
function querySelectorAll(selector) {
  if (selector === 'video') return [{
    currentSrc: 'https://cdn.example.com/video.mp4?sig=abc',
    getAttribute: () => null,
    querySelectorAll: () => [{src: 'https://cdn.example.com/fallback.webm', type: 'video/webm'}]
  }];
  if (selector === 'source[src]') return [];
  if (selector === 'a[href]') return [{href: 'https://cdn.example.com/file.MP4?token=x', textContent: 'File video'},
                                   {href: 'javascript:alert(1)', textContent: 'Bad'}];
  if (selector === 'meta[property], meta[name]') return [{getAttribute: a => a === 'property' ? 'og:video' : null,
    content: 'https://cdn.example.com/trailer.mp4'}];
  return [];
}
const context = {
  URL,
  document: {title: 'Sample page', baseURI: 'https://example.com/watch', querySelectorAll},
  window: {performance: {}},
  performance: {getEntriesByType: () => [{name: 'https://cdn.example.com/live.m3u8'}]},
};
const raw = vm.runInNewContext(script, context);
const found = JSON.parse(raw);
assert.strictEqual(found.length, 5);
assert(found.some(x => x.url.includes('video.mp4?sig=')));
assert(found.some(x => x.url.includes('live.m3u8')));
assert(!found.some(x => x.url.startsWith('javascript:')));
console.log('PASS: DOM video detector discovers media and rejects script URLs');
