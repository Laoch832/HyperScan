import { test, expect, type Page } from '@playwright/test';
async function importPage(page: Page, label = 'TEST DOCUMENT') {
  const encoded = await page.evaluate(text => {
    const canvas = document.createElement('canvas'); canvas.width = 1000; canvas.height = 600;
    const context = canvas.getContext('2d')!;
    context.fillStyle = 'white'; context.fillRect(0, 0, 1000, 600);
    context.fillStyle = 'black'; context.font = 'bold 68px sans-serif'; context.fillText(text, 65, 220);
    context.font = '38px sans-serif'; context.fillText('COMMUNITY SCANNER 2026', 65, 330);
    return canvas.toDataURL('image/png').split(',')[1];
  }, label);
  await page.locator('#file-input').setInputFiles({ name: 'scan.png', mimeType: 'image/png', buffer: Buffer.from(encoded, 'base64') });
  await expect(page.getByRole('status')).toContainText('已导入 1 页');
}

test('imports, crops, rotates, enhances, persists, reorders and exports a two page PDF', async ({ page }) => {
  await page.goto('/'); await importPage(page);
  await page.getByLabel('文档名称', { exact: true }).fill('我的测试文档');
  await page.getByRole('button', { name: '裁剪边缘' }).click();
  await page.locator('[name=left]').fill('10');
  await page.locator('[name=right]').fill('90');
  await page.getByRole('button', { name: '应用裁剪' }).click();
  await expect(page.getByRole('status')).toContainText('裁剪已应用');
  await expect.poll(() => page.locator('.preview img').evaluate((img: HTMLImageElement) => [img.naturalWidth, img.naturalHeight])).toEqual([800, 600]);
  await page.getByRole('button', { name: '旋转 90°' }).click();
  await expect(page.getByRole('status')).toContainText('页面已旋转');
  await expect.poll(() => page.locator('.preview img').evaluate((img: HTMLImageElement) => [img.naturalWidth, img.naturalHeight])).toEqual([600, 800]);
  await page.getByRole('button', { name: '灰度', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('图像增强已应用');
  await importPage(page, 'SECOND PAGE');
  await page.getByRole('button', { name: '← 前移' }).click();
  await page.getByRole('button', { name: '保存文档', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('文档已保存');
  await page.reload();
  await page.getByRole('button', { name: '打开 我的测试文档', exact: true }).click();
  await expect(page.locator('.thumbnail')).toHaveCount(2);
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出 PDF', exact: true }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('我的测试文档.pdf');
  const stream = await download.createReadStream();
  const buffers: Buffer[] = []; for await (const chunk of stream!) buffers.push(Buffer.from(chunk));
  const pdf = Buffer.concat(buffers).toString('latin1');
  expect(pdf.startsWith('%PDF-')).toBe(true);
  expect(pdf.match(/\/Type \/Page\b/g)).toHaveLength(2);
  expect(pdf).toContain('/Subtype /Image');
  await page.screenshot({ path: 'test-results/workspace.png', fullPage: true });
});

test('recognizes text locally without requests to external services', async ({ page }) => {
  const externalRequests: string[] = [];
  page.on('request', request => { if (/^https?:/.test(request.url()) && !request.url().startsWith('http://127.0.0.1:5173')) externalRequests.push(request.url()); });
  await page.goto('/'); await importPage(page);
  await page.getByRole('button', { name: '识别当前页文字' }).click();
  await expect(page.getByLabel('识别结果')).toHaveValue(/TEST DOCUMENT/, { timeout: 80000 });
  expect(externalRequests).toEqual([]);
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出全部文字 .txt' }).click();
  expect((await downloadPromise).suggestedFilename()).toMatch(/\.txt$/);
});

test('small screens fit and deleting a saved document survives reload', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/'); await importPage(page);
  await page.getByRole('button', { name: '保存文档', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('文档已保存');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  page.on('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: '删除 未命名文档', exact: true }).click();
  await expect(page.locator('.document-card')).toHaveCount(0);
  await page.reload();
  await expect(page.locator('.empty-library')).toContainText('保存的文档会出现在这里');
  await page.screenshot({ path: 'test-results/mobile.png', fullPage: true });
});
