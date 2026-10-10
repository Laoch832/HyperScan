import { jsPDF } from 'jspdf';
import { Capacitor } from '@capacitor/core';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import type { ScanDocument } from './storage';
import { loadImage } from './image';

export async function createPdf(doc: ScanDocument): Promise<Blob> {
  if (!doc.pages.length) throw new Error('请先添加至少一页文档。');
  const pdf = new jsPDF({ format: 'a4', unit: 'mm', compress: true });
  for (const [index, page] of doc.pages.entries()) {
    if (index) pdf.addPage();
    const image = await loadImage(page.image);
    const ratio = Math.min(190 / image.width, 277 / image.height);
    const width = image.width * ratio, height = image.height * ratio;
    pdf.addImage(page.image, 'JPEG', (210 - width) / 2, (297 - height) / 2, width, height);
  }
  pdf.setProperties({ title: doc.name, creator: 'HyperScan · Laoch832' });
  return pdf.output('blob');
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
    const file = await Filesystem.writeFile({ path: `exports/${safeName}`, data, directory: Directory.Cache, recursive: true });
    await Share.share({ title: safeName, files: [file.uri] });
  } else {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = safeName; anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }
}
