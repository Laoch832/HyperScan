import './style.css';
import { defaultEdit, readPhoto, renderPhoto, type Edit } from './image';
import { listDocuments, saveDocument, removeDocument, type ScanDocument, type Page } from './storage';
import { createPdf, exportFile } from './export';
import { recognize } from './ocr';

const app = document.querySelector<HTMLDivElement>('#app')!;
let documents: ScanDocument[] = [];
let current: ScanDocument = freshDocument();
let selectedId = '';
let busy = false;
let dirty = false;
let search = '';
let notice = '';
const escape = (value: string) => value.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]!));
function freshDocument(): ScanDocument { return { id: crypto.randomUUID(), name: '未命名文档', updatedAt: Date.now(), pages: [] }; }
function selected(): Page | undefined { return current.pages.find(page => page.id === selectedId); }
const icons: Record<string, string> = {
  scan: '<path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5M7 12h10"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  camera: '<path d="M4 7h4l2-3h4l2 3h4v13H4z"/><circle cx="12" cy="13" r="4"/>',
  image: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="m3 17 6-6 4 4 3-3 5 5"/><circle cx="16" cy="8" r="1"/>',
  folder: '<path d="M3 6h7l2 3h9v11H3z"/>',
  pdf: '<path d="M6 3h8l4 4v14H6zM14 3v5h4M9 12h6M9 16h6"/>',
  rotate: '<path d="M4 10a8 8 0 1 1 1 8M4 4v6h6"/>',
  text: '<path d="M4 5h16M12 5v15M8 20h8M4 9V5m16 4V5"/>',
  trash: '<path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/>',
  save: '<path d="M4 3h14l3 3v15H3V3zM8 3v7h8V3M7 21v-7h10v7"/>',
  shield: '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6zM8 12l3 3 5-6"/>',
};
const icon = (name: string) => `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] ?? ''}</svg>`;

function render(): void {
  const page = selected();
  const visible = documents.filter(doc => doc.name.toLowerCase().includes(search.toLowerCase()));
  app.innerHTML = `
    <aside class="sidebar">
      <a class="brand" href="#" aria-label="HyperScan 首页"><span class="brand-mark"><img src="/hyperscan.svg" width="40" height="40" alt="" /></span><span>HyperScan<small>OFFLINE · OPEN SOURCE</small></span></a>
      <button class="new-document primary" data-action="new">${icon('plus')} 新建扫描</button>
      <div class="nav active">${icon('folder')} 我的文档 <span>${documents.length}</span></div>
      <div class="sidebar-bottom">${icon('shield')}<div>文档留在你的设备上<small>本地处理 · 自由使用</small></div></div>
    </aside>
    <main>
      <header><span class="breadcrumb">工作空间 <span>/</span> 我的文档</span><span class="edition">离线版 0.2</span></header>
      <section class="heading"><div><p class="eyebrow">MAKE ROOM FOR YOUR IDEAS</p><h1>纸张，变成可能。</h1><p class="subtitle">拍摄、编辑、识字、导出。每一步，都在你的设备上。</p></div><div class="local-badge"><span></span> 无需联网</div></section>
      <section class="workspace">
        <div class="editor-panel">
          <div class="panel-heading"><div><label class="sr-only" for="document-name">文档名称</label><input id="document-name" maxlength="120" value="${escape(current.name)}" /><span class="page-count">${current.pages.length} 页${dirty ? ' · 未保存' : ''}</span></div><button class="icon-button" data-action="save" aria-label="保存文档" title="保存文档" ${!current.pages.length ? 'disabled' : ''}>${icon('save')}</button></div>
          <div class="preview ${page ? 'has-page' : ''}">
            ${page ? `<img src="${page.image}" alt="当前文档第 ${current.pages.indexOf(page) + 1} 页" />` : `<div class="empty-preview"><div class="paper-illustration"><span></span><span></span><span></span><span></span><div>${icon('scan')}</div></div><h2>扫描你的第一份文档</h2><p>拍摄纸张，或导入现有图片<br>支持 JPEG、PNG 和 WebP</p><button class="primary" data-action="import">${icon('image')} 导入图片</button><button class="text-button" data-action="camera">${icon('camera')} 用相机拍摄</button></div>`}
          </div>
          ${current.pages.length ? `<div class="page-strip">${current.pages.map((item, index) => `<button class="thumbnail ${item.id === selectedId ? 'selected' : ''}" data-page="${item.id}" aria-label="选择第 ${index + 1} 页"><img src="${item.image}" alt="" /><span>${index + 1}</span></button>`).join('')}<button class="add-page" data-action="import" aria-label="添加页面">${icon('plus')}</button></div>` : ''}
          <div class="editor-footer"><span>${icon('shield')} 图片与文字均在本地处理</span><button class="primary" data-action="pdf" ${!current.pages.length ? 'disabled' : ''}>${icon('pdf')} 导出 PDF</button></div>
        </div>
        <aside class="tools-panel"><div class="tools-heading"><h2>文档工具</h2><span>随手整理，轻松导出</span></div>
          <div class="tool-grid"><button data-action="camera">${icon('camera')}<span>拍摄文档</span></button><button data-action="import">${icon('image')}<span>导入图片</span></button></div>
          <div class="tool-section"><h3>图像增强</h3><div class="filters">${([['original', '原图'], ['gray', '灰度'], ['document', '清晰文档']] as const).map(([value, label]) => `<button data-filter="${value}" class="${page?.edit.filter === value ? 'chosen' : ''}" ${!page ? 'disabled' : ''}><span class="filter-sample ${value}" aria-hidden="true">Aa</span>${label}</button>`).join('')}</div></div>
          <div class="tool-section"><h3>调整页面</h3><div class="tool-grid compact"><button data-action="crop" ${!page ? 'disabled' : ''}>${icon('scan')}<span>裁剪边缘</span></button><button data-action="rotate" ${!page ? 'disabled' : ''}>${icon('rotate')}<span>旋转 90°</span></button></div><div class="reorder"><button data-action="previous" ${!page || current.pages.indexOf(page) === 0 ? 'disabled' : ''}>← 前移</button><button data-action="next" ${!page || current.pages.indexOf(page) === current.pages.length - 1 ? 'disabled' : ''}>后移 →</button><button data-action="remove-page" class="danger" ${!page ? 'disabled' : ''} aria-label="删除当前页">${icon('trash')}</button></div></div>
          <div class="tool-section"><h3>文字识别 <span class="small-tag">中 / EN</span></h3><button class="ocr-button" data-action="ocr" ${!page ? 'disabled' : ''}>${icon('text')} 识别当前页文字 <span>↗</span></button><p class="hint">使用设备上的 OCR 引擎。首次加载可能稍慢。</p>${page?.text ? `<label class="sr-only" for="recognized-text">识别结果</label><textarea id="recognized-text" rows="6">${escape(page.text)}</textarea><button class="text-button" data-action="text">导出全部文字 .txt</button>` : ''}</div>
          <div class="tip"><span>✦</span><p>让扫描更清晰<small>把纸张放在平整的表面，保持光线充足，拍摄时尽量正对文档。</small></p></div>
        </aside>
      </section>
      <section class="library"><div class="library-heading"><h2>最近文档 <span>${documents.length}</span></h2><label class="search"><span>⌕</span><input id="search" placeholder="搜索文档名称" aria-label="搜索文档" value="${escape(search)}" /></label></div><div class="document-grid">${visible.length ? visible.map(doc => `<article class="document-card"><button data-open="${doc.id}" class="document-cover" aria-label="打开 ${escape(doc.name)}"><img src="${doc.pages[0]?.image ?? ''}" alt="" /><span>${doc.pages.length} 页</span></button><div class="document-meta"><button data-open="${doc.id}">${escape(doc.name)}</button><button data-delete="${doc.id}" class="icon-button danger" aria-label="删除 ${escape(doc.name)}">${icon('trash')}</button><small>${new Date(doc.updatedAt).toLocaleDateString('zh-CN')}</small></div></article>`).join('') : `<div class="empty-library">${icon('folder')}<span>${search ? '没有匹配的文档' : '保存的文档会出现在这里'}</span></div>`}</div></section>
      <footer>HyperScan · Laoch832 <span>·</span> 由 Laoch832 创作与维护。</footer>
    </main>
    <input class="sr-only" id="file-input" type="file" accept="image/jpeg,image/png,image/webp" multiple />
    <input class="sr-only" id="camera-input" type="file" accept="image/*" capture="environment" />
    <div id="notice" role="status" aria-live="polite" class="notice ${notice ? 'visible' : ''}">${escape(notice)}</div>
    <dialog id="crop-dialog"><form id="crop-form"><h2>裁剪边缘</h2><p>按原图百分比调整保留区域，再应用到当前页。</p><div class="crop-preview">${page ? `<img src="${page.original}" alt="裁剪原图" /><div id="crop-box"></div>` : ''}</div><div class="crop-fields">${(['left', 'top', 'right', 'bottom'] as const).map((key, index) => `<label>${['左边', '上边', '右边', '下边'][index]} %<input type="number" name="${key}" min="0" max="100" step="1" value="${page?.edit.crop[key] ?? 0}" required /></label>`).join('')}</div><div class="dialog-actions"><button type="button" data-action="close-crop">取消</button><button type="submit" class="primary">应用裁剪</button></div></form></dialog>
  `;
  bindEvents();
  if (busy) app.querySelectorAll<HTMLButtonElement>('button').forEach(button => { button.disabled = true; });
}

function showNotice(message: string): void { notice = message; const el = document.querySelector('#notice'); if (el) { el.textContent = message; el.classList.add('visible'); } }
async function run(action: () => Promise<void>): Promise<void> {
  if (busy) return;
  busy = true;
  app.querySelectorAll<HTMLButtonElement>('button').forEach(button => { button.disabled = true; });
  app.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input:not([type=file]), textarea').forEach(input => { input.disabled = true; });
  try { await action(); }
  catch (error) { showNotice(error instanceof Error ? error.message : '操作失败，请重试。'); }
  finally { busy = false; render(); }
}
async function save(): Promise<void> {
  if (!current.pages.length) throw new Error('请先导入图片。');
  current.name = current.name.trim() || '未命名文档'; current.updatedAt = Date.now();
  await saveDocument(current); documents = await listDocuments(); dirty = false;
}
async function editPage(change: (edit: Edit) => void): Promise<void> {
  const page = selected(); if (!page) return;
  const edit = structuredClone(page.edit); change(edit);
  const image = await renderPhoto(page.original, edit);
  page.edit = edit; page.image = image; page.text = ''; dirty = true;
}
function safeSwitch(): boolean { return !dirty || confirm('当前文档尚未保存。是否放弃这些更改？'); }
function bindEvents(): void {
  document.querySelector('#document-name')?.addEventListener('input', event => { current.name = (event.target as HTMLInputElement).value; dirty = true; });
  document.querySelector('#recognized-text')?.addEventListener('input', event => { const page = selected(); if (page) { page.text = (event.target as HTMLTextAreaElement).value; dirty = true; } });
  document.querySelector('#search')?.addEventListener('input', event => {
    search = (event.target as HTMLInputElement).value; render();
    const input = document.querySelector<HTMLInputElement>('#search')!; input.focus(); input.setSelectionRange(search.length, search.length);
  });
  for (const id of ['file-input', 'camera-input']) document.querySelector(`#${id}`)?.addEventListener('change', event => {
    const files = Array.from((event.target as HTMLInputElement).files ?? []);
    if (files.length) void run(async () => {
      showNotice('正在导入图片…');
      const pages: Page[] = [];
      for (const file of files) { const image = await readPhoto(file); pages.push({ id: crypto.randomUUID(), original: image, image, edit: defaultEdit(), text: '' }); }
      current.pages.push(...pages); selectedId = pages[0].id; dirty = true; showNotice(`已导入 ${pages.length} 页，请保存文档。`);
    });
  });
  app.querySelectorAll<HTMLElement>('[data-page]').forEach(button => button.onclick = () => { if (!busy) { selectedId = button.dataset.page!; render(); } });
  app.querySelectorAll<HTMLElement>('[data-filter]').forEach(button => button.onclick = () => void run(async () => { await editPage(edit => { edit.filter = button.dataset.filter as Edit['filter']; }); showNotice('图像增强已应用。'); }));
  app.querySelectorAll<HTMLElement>('[data-open]').forEach(button => button.onclick = () => { if (!busy && safeSwitch()) { current = structuredClone(documents.find(doc => doc.id === button.dataset.open)!); selectedId = current.pages[0]?.id ?? ''; dirty = false; notice = ''; render(); } });
  app.querySelectorAll<HTMLElement>('[data-delete]').forEach(button => button.onclick = () => {
    if (confirm('确定删除这份本地文档？此操作无法撤销。')) void run(async () => { await removeDocument(button.dataset.delete!); documents = await listDocuments(); if (current.id === button.dataset.delete) { current = freshDocument(); selectedId = ''; dirty = false; } showNotice('文档已删除。'); });
  });
  app.querySelectorAll<HTMLElement>('[data-action]').forEach(button => button.onclick = () => {
    const action = button.dataset.action;
    if (action === 'import' || action === 'camera') { document.querySelector<HTMLInputElement>(action === 'import' ? '#file-input' : '#camera-input')!.click(); return; }
    if (action === 'new') { if (safeSwitch()) { current = freshDocument(); selectedId = ''; dirty = false; notice = ''; render(); } return; }
    if (action === 'crop') { const dialog = document.querySelector<HTMLDialogElement>('#crop-dialog')!; dialog.showModal(); updateCropBox(); return; }
    if (action === 'close-crop') { document.querySelector<HTMLDialogElement>('#crop-dialog')!.close(); return; }
    void run(async () => {
      if (action === 'save') { await save(); showNotice('文档已保存到本机。'); }
      if (action === 'pdf') { const blob = await createPdf(current); await exportFile(blob, `${current.name.trim() || '文档'}.pdf`); showNotice('PDF 已导出。'); }
      if (action === 'rotate') { await editPage(edit => { edit.rotation = (edit.rotation + 90) % 360; }); showNotice('页面已旋转。'); }
      if (action === 'remove-page') { const index = current.pages.findIndex(page => page.id === selectedId); current.pages.splice(index, 1); selectedId = current.pages[Math.min(index, current.pages.length - 1)]?.id ?? ''; dirty = true; showNotice('页面已移除，保存后生效。'); }
      if (action === 'previous' || action === 'next') { const index = current.pages.findIndex(page => page.id === selectedId); const to = index + (action === 'previous' ? -1 : 1); if (index >= 0 && to >= 0 && to < current.pages.length) { [current.pages[index], current.pages[to]] = [current.pages[to], current.pages[index]]; dirty = true; } }
      if (action === 'ocr') { const page = selected(); if (!page) return; showNotice('正在加载本地文字识别引擎…'); page.text = await recognize(page.image, value => showNotice(`正在识别文字… ${Math.round(value * 100)}%`)); dirty = true; showNotice(page.text.trim() ? '识别完成，可编辑结果或导出文字。' : '未识别到文字，请尝试更清晰的图片。'); }
      if (action === 'text') { const text = current.pages.map((page, index) => `—— 第 ${index + 1} 页 ——\n${page.text}`).join('\n\n'); await exportFile(new Blob([text], { type: 'text/plain;charset=utf-8' }), `${current.name}.txt`); showNotice('文字已导出。'); }
    });
  });
  document.querySelector('#crop-form')?.addEventListener('input', updateCropBox);
  document.querySelector('#crop-form')?.addEventListener('submit', event => {
    event.preventDefault();
    const form = new FormData(event.target as HTMLFormElement);
    const crop = { left: Number(form.get('left')), top: Number(form.get('top')), right: Number(form.get('right')), bottom: Number(form.get('bottom')) };
    document.querySelector<HTMLDialogElement>('#crop-dialog')!.close();
    void run(async () => { await editPage(edit => { edit.crop = crop; }); showNotice('裁剪已应用。'); });
  });
}
function updateCropBox(): void {
  const form = document.querySelector<HTMLFormElement>('#crop-form')!;
  const values = new FormData(form);
  const box = document.querySelector<HTMLElement>('#crop-box')!;
  box.style.left = `${values.get('left')}%`; box.style.top = `${values.get('top')}%`;
  box.style.right = `${100 - Number(values.get('right'))}%`; box.style.bottom = `${100 - Number(values.get('bottom'))}%`;
}
window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); } });
render();
void listDocuments().then(result => { documents = result; render(); }).catch(() => showNotice('无法打开本地存储。请允许此页面使用浏览器存储。'));
