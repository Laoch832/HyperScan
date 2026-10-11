import { Capacitor, registerPlugin } from '@capacitor/core';

export type Corner = { x: number; y: number };
export type Corners = [Corner, Corner, Corner, Corner];
export const fullCorners = (): Corners => [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }];
export function validateCorners(points: Corners): void {
  if (points.length !== 4 || points.some(p => !Number.isFinite(p.x) || !Number.isFinite(p.y) || p.x < 0 || p.x > 1 || p.y < 0 || p.y > 1))
    throw new Error('四个角必须位于图片范围内。');
  for (let i = 0; i < 4; i++) {
    const a = points[i], b = points[(i + 1) % 4], c = points[(i + 2) % 4];
    if ((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) < 0.001)
      throw new Error('请按左上、右上、右下、左下调整四角，边框不能交叉或过于狭窄。');
  }
}
export type PdfSettings = { paper: 'a4' | 'a5' | 'letter'; orientation: 'auto' | 'portrait' | 'landscape'; margin: number };
export const defaultPdfSettings = (): PdfSettings => ({ paper: 'a4', orientation: 'auto', margin: 0.03 });
export function pdfPageSize(settings: PdfSettings, image: { width: number; height: number }): [number, number] {
  if (!Number.isFinite(settings.margin) || settings.margin < 0 || settings.margin > 0.2) throw new Error('PDF 边距需在 0–20% 内。');
  const size = { a4: [595, 842], a5: [420, 595], letter: [612, 792] }[settings.paper];
  if (!size || !['auto', 'portrait', 'landscape'].includes(settings.orientation)) throw new Error('PDF 页面设置无效。');
  const landscape = settings.orientation === 'landscape' || (settings.orientation === 'auto' && image.width > image.height);
  return landscape ? [size[1], size[0]] : [size[0], size[1]];
}
interface OriginalCore {
  detectDocument(options: { image: string }): Promise<{ corners: number[] | null; engine: string }>;
  capabilities(): Promise<{ perspective: boolean; pdf: boolean; automaticEdgeDetection: boolean; originalOcr: boolean }>;
  perspective(options: { image: string; corners: number[] }): Promise<{ image: string; width: number; height: number; engine: string }>;
  savePdf(options: PdfSettings & { pages: string[]; filename: string }): Promise<{ cancelled: boolean; engine: string }>;
}
export const originalCore = registerPlugin<OriginalCore>('OriginalCore');
export const nativeCorePlatform = () => Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
export async function correctOriginal(image: string, corners: Corners): Promise<string> {
  validateCorners(corners);
  if (!nativeCorePlatform()) throw new Error('四角校正需要安装 Android 版 HyperScan。');
  // No substitute transform or false success when the original native engine fails.
  const result = await originalCore.perspective({ image, corners: corners.flatMap(p => [p.x, p.y]) });
  if (!result.image.startsWith('data:image/jpeg;base64,') || result.width <= 0 || result.height <= 0)
    throw new Error('原生校正未返回有效图片。');
  return result.image;
}
