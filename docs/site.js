const steps = [
  { tab: 'tab-capture', index: '01 / CAPTURE', title: '让页面更清楚。', description: '导入照片后裁剪、旋转与增强，整理为文档页面。', note: '裁剪 · 旋转 · 图像增强' },
  { tab: 'tab-recognize', index: '02 / RECOGNIZE', title: '让文字可检索。', description: '独立原型使用本地中英文 OCR，识别结果可以导出为文本。', note: '本地识别 · 中文与英文 · 文本导出' },
  { tab: 'tab-export', index: '03 / EXPORT', title: '让记录有去处。', description: '重新排序页面，保存到本地文档库，导出多页 PDF。', note: '页面排序 · 本地保存 · PDF 导出' }
];
const tabs = [...document.querySelectorAll('[role="tab"]')];
function selectStep(index, focus = false) {
  const step = steps[index];
  tabs.forEach((tab, i) => {
    tab.setAttribute('aria-selected', String(i === index));
    tab.tabIndex = i === index ? 0 : -1;
  });
  document.querySelector('#workflow-panel').setAttribute('aria-labelledby', step.tab);
  document.querySelector('.demo-canvas').dataset.mode = String(index);
  document.querySelector('#demo-index').textContent = step.index;
  document.querySelector('#demo-title').textContent = step.title;
  document.querySelector('#demo-description').textContent = step.description;
  document.querySelector('#canvas-note').textContent = step.note;
  if (focus) tabs[index].focus();
}
tabs.forEach((tab, index) => {
  tab.addEventListener('click', () => selectStep(index));
  tab.addEventListener('keydown', event => {
    let next;
    if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % tabs.length;
    if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = tabs.length - 1;
    if (next !== undefined) { event.preventDefault(); selectStep(next, true); }
  });
});
