import { jsPDF } from 'jspdf';
import { Capacitor, registerPlugin } from '@capacitor/core';
import type { ScanDocument } from './storage';
import { loadImage } from './image';
import { defaultPdfSettings, nativeCorePlatform, originalCore, pdfPageSize, type PdfSettings } from './original-core';
const localExport = registerPlugin<{ save(options: { filename: string; mime: string; data: string }): Promise<{ cancelled: boolean }> }>('OfflineExport');

export async function createPdf(doc: ScanDocument, settings: PdfSettings = defaultPdfSettings()): Promise<Blob> {
  if (!doc.pages.length) throw new Error('请先添加至少一页文档。');
  let pdf: jsPDF | undefined;
  for (const [index, page] of doc.pages.entries()) {
    const image = await loadImage(page.image);
    const [pageWidth, pageHeight] = pdfPageSize(settings, image);
    const orientation = pageWidth > pageHeight ? 'landscape' : 'portrait';
    if (index) pdf!.addPage([pageWidth, pageHeight], orientation);
    else pdf = new jsPDF({ format: [pageWidth, pageHeight], orientation, unit: 'pt', compress: true });
    const padX = Math.trunc(pageWidth * settings.margin), padY = Math.trunc(pageHeight * settings.margin);
    const ratio = Math.min((pageWidth - 2 * padX) / image.width, (pageHeight - 2 * padY) / image.height);
    const width = image.width * ratio, height = image.height * ratio;
    pdf!.addImage(page.image, 'JPEG', (pageWidth - width) / 2, (pageHeight - height) / 2, width, height);
  }
  pdf!.setProperties({ title: doc.name, creator: 'HyperScan · Laoch832' });
  return pdf!.output('blob');
}

export async function exportPdf(doc: ScanDocument, settings: PdfSettings): Promise<void> {
  if (!doc.pages.length) throw new Error('请先添加至少一页文档。');
  pdfPageSize(settings, { width: 1, height: 1 });
  const filename = `${doc.name.trim() || '文档'}.pdf`.replace(/[\\/:*?"<>|]/g, '_');
  if (nativeCorePlatform()) {
    const result = await originalCore.savePdf({ ...settings, filename, pages: doc.pages.map(page => page.image) });
    if (result.cancelled) throw new Error('已取消保存。');
  } else await exportFile(await createPdf(doc, settings), filename);
}

export async function exportFile(blob: Blob, filename: string): Promise<void> {
  const safeName = filename.replace(/[\\/:*?"<>|]/g, '_');
  if (Capacitor.isNativePlatform()) {
    const data = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1]);
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(blob);
    });
    const result = await localExport.save({ filename: safeName, mime: blob.type.startsWith('text/') ? 'text/plain' : 'application/pdf', data });
    if (result.cancelled) throw new Error('已取消保存。');
  } else {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = safeName; anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }
}
