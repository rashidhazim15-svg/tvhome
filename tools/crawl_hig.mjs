import fs from 'node:fs';
import path from 'node:path';

const outDir = 'docs/hig';
fs.mkdirSync(outDir, { recursive: true });

const slugs = [
  'designing-for-tvos', 'typography', 'layout', 'color', 'materials', 'app-icons',
  'tab-bars', 'settings', 'focus-and-selection', 'playing-video', 'motion', 'top-shelf',
  'keyboards', 'sidebars', 'buttons', 'lists-and-tables', 'collections', 'text-fields',
  'toolbars', 'images', 'branding', 'launching', 'onboarding', 'loading', 'feedback',
  'drag-and-drop', 'game-controls', 'gestures', 'pointing-devices', 'augmented-reality',
  'spatial-layout', 'layout', 'accessibility', 'design-principles',
];

function collectText(node, out, ctx = {}) {
  if (node == null) return;
  if (typeof node === 'string') return;
  if (Array.isArray(node)) {
    for (const child of node) collectText(child, out, ctx);
    return;
  }
  if (typeof node !== 'object') return;

  const local = { ...ctx };
  if (typeof node.platform === 'string') local.platform = node.platform;
  if (typeof node.name === 'string' && Array.isArray(node.platforms)) local.platform = node.platforms.join('/');
  if (typeof node.title === 'string' && node.type === 'tab') local.tab = node.title;

  if (typeof node.text === 'string' && node.text.trim()) {
    out.push({ text: node.text.trim(), platform: local.platform || '', tab: local.tab || '', kind: node.type || '' });
  }
  if (node.type === 'codeVoice' && node.code) {
    out.push({ text: '`' + node.code + '`', platform: local.platform || '', tab: local.tab || '', kind: 'code' });
  }
  if (node.type === 'codeListing' && Array.isArray(node.code)) {
    out.push({ text: node.code.join('\n'), platform: local.platform || '', tab: local.tab || '', kind: 'code' });
  }
  if (node.type === 'table' && Array.isArray(node.rows)) {
    for (const row of node.rows) {
      const cells = [];
      for (const cell of row) {
        const tmp = [];
        collectText(cell, tmp, local);
        cells.push(tmp.map((t) => t.text).join(' '));
      }
      out.push({ text: cells.join(' | '), platform: local.platform || '', tab: local.tab || '', kind: 'row' });
    }
  }
  for (const [key, value] of Object.entries(node)) {
    if (key === 'references' || key === 'metadata') continue;
    if (key === 'text' || key === 'code') continue;
    collectText(value, out, local);
  }
}

for (const slug of slugs) {
  const url = `https://developer.apple.com/tutorials/data/design/human-interface-guidelines/${slug}.json`;
  try {
    const res = await fetch(url, { headers: { 'user-agent': 'Mozilla/5.0 tvhome-doc-crawler' } });
    if (!res.ok) { console.log(`${slug}: ${res.status}`); continue; }
    const json = await res.json();
    const out = [];
    collectText(json.primaryContentSections, out);
    collectText(json.abstract, out);
    const lines = out.map((t) => {
      const tags = [t.platform && `[${t.platform}]`, t.tab && `{${t.tab}}`].filter(Boolean).join(' ');
      return (tags ? tags + ' ' : '') + t.text;
    });
    fs.writeFileSync(path.join(outDir, `${slug}.txt`), lines.join('\n'));
    console.log(`${slug}: ${lines.length} lines`);
  } catch (e) {
    console.log(`${slug}: ERR ${e.message}`);
  }
}
