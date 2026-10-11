import { correctOriginal, type Corners } from './original-core';
export type Filter = 'original' | 'gray' | 'document' | 'clean' | 'bw';
export type Crop = { left: number; top: number; right: number; bottom: number };
export type Edit = { filter: Filter; rotation: number; crop: Crop; corners?: Corners };
export const defaultEdit = (): Edit => ({ filter: 'original', rotation: 0, crop: { left: 0, top: 0, right: 100, bottom: 100 } });

export function validateCrop(crop: Crop): void {
  const { left, top, right, bottom } = crop;
  if (![left, top, right, bottom].every(Number.isFinite) || left < 0 || top < 0 || right > 100 || bottom > 100 || right <= left || bottom <= top) {
    throw new Error('裁剪范围需在 0–100% 内，右边和下边必须大于左边和上边。');
  }
}

export function filterPixels(data: Uint8ClampedArray, filter: Filter): void {
  if (filter === 'original' || filter === 'clean' || filter === 'bw') return;
  for (let i = 0; i < data.length; i += 4) {
    const gray = Math.round(data[i] * 0.299 + data[i + 1] * 0.587 + data[i + 2] * 0.114);
    const value = filter === 'gray' ? gray : Math.max(0, Math.min(255, (gray - 128) * 1.65 + 158));
    data[i] = data[i + 1] = data[i + 2] = value;
  }
}

export async function loadImage(src: string): Promise<HTMLImageElement> {
  const image = new Image();
  image.src = src;
  await image.decode();
  return image;
}

export async function readPhoto(file: File): Promise<string> {
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) throw new Error('请选择 JPEG、PNG 或 WebP 图片。');
  if (file.size > 25 * 1024 * 1024) throw new Error('单张图片请小于 25 MB。');
  const url = URL.createObjectURL(file);
  try {
    const image = await loadImage(url);
    const scale = Math.min(1, 2400 / Math.max(image.width, image.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(image.width * scale));
    canvas.height = Math.max(1, Math.round(image.height * scale));
    canvas.getContext('2d')!.drawImage(image, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', 0.92);
  } finally { URL.revokeObjectURL(url); }
}

export async function renderPhoto(src: string, edit: Edit): Promise<string> {
  validateCrop(edit.crop);
  const image = await loadImage(edit.corners ? await correctOriginal(src, edit.corners) : src);
  const { left, top, right, bottom } = edit.crop;
  const width = Math.max(1, Math.round(image.width * (right - left) / 100));
  const height = Math.max(1, Math.round(image.height * (bottom - top) / 100));
  const angle = ((edit.rotation % 360) + 360) % 360;
  if (![0, 90, 180, 270].includes(angle)) throw new Error('仅支持 90 度整数倍旋转。');
  const canvas = document.createElement('canvas');
  canvas.width = angle % 180 ? height : width;
  canvas.height = angle % 180 ? width : height;
  const context = canvas.getContext('2d', { willReadFrequently: true })!;
  context.translate(canvas.width / 2, canvas.height / 2);
  context.rotate(angle * Math.PI / 180);
  context.drawImage(image, image.width * left / 100, image.height * top / 100, width, height, -width / 2, -height / 2, width, height);
  const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
  if (edit.filter === 'clean' || edit.filter === 'bw') paperFilter(pixels.data, canvas.width, canvas.height, edit.filter); else filterPixels(pixels.data, edit.filter);
  context.putImageData(pixels, 0, 0);
  return canvas.toDataURL('image/jpeg', 0.92);
}

/** Local background normalization for shadowed paper; HyperScan implementation, not an original model. */
export function paperFilter(data: Uint8ClampedArray, width: number, height: number, mode: 'clean' | 'bw'): void {
  if (width * height * 4 !== data.length || width < 1 || height < 1) throw new Error('无效的图像尺寸');
  const stride = width + 1, integral = new Float64Array(stride * (height + 1));
  for (let y = 0; y < height; y++) { let sum = 0; for (let x = 0; x < width; x++) {
    const i = (y * width + x) * 4; sum += data[i] * .299 + data[i + 1] * .587 + data[i + 2] * .114;
    integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + sum;
  } }
  const radius = Math.max(12, Math.min(64, Math.round(Math.min(width, height) * .025)));
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const l = Math.max(0, x - radius), r = Math.min(width, x + radius + 1), t = Math.max(0, y - radius), b = Math.min(height, y + radius + 1);
    const mean = (integral[b * stride + r] - integral[t * stride + r] - integral[b * stride + l] + integral[t * stride + l]) / ((r - l) * (b - t));
    const i = (y * width + x) * 4, gray = data[i] * .299 + data[i + 1] * .587 + data[i + 2] * .114;
    if (mode === 'bw') { const value = gray < mean * .82 ? 0 : 255; data[i] = data[i + 1] = data[i + 2] = value; }
    else { const gain = Math.min(3, 255 / Math.max(mean, 32)); for (let c = 0; c < 3; c++) data[i + c] = Math.max(0, Math.min(255, (data[i + c] * gain - 128) * 1.15 + 128)); }
  }
}
