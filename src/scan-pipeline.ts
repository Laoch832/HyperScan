import { correctOriginal, nativeCorePlatform, originalCore, validateCorners, type Corners } from './original-core';
import { defaultEdit } from './image';
import type { Page } from './storage';

/** Keep source photos even when detection/correction fails; never silently replace an edited page. */
export async function prepareScan(image: string, automatic: boolean): Promise<{ page: Page; message?: string }> {
  const page: Page = { id: crypto.randomUUID(), original: image, image, edit: defaultEdit(), text: '' };
  if (!automatic || !nativeCorePlatform()) return { page };
  try {
    const detected = await originalCore.detectDocument({ image });
    page.scanEngine = detected.engine;
    if (!detected.corners) { page.scanMessage = '未找到可靠边缘，已保留完整原图，可手动调整四角。'; return { page, message: page.scanMessage }; }
    const corners = detected.corners.reduce((out, _, i, a) => { if (i % 2 === 0) out.push({ x: a[i], y: a[i + 1] }); return out; }, [] as { x: number; y: number }[]) as Corners;
    validateCorners(corners);
    const corrected = await correctOriginal(image, corners);
    page.edit.corners = corners; page.corrected = corrected; page.image = corrected;
    return { page };
  } catch {
    page.scanMessage = '自动校正未完成，原图已保留，可手动调整。'; return { page, message: page.scanMessage };
  }
}
