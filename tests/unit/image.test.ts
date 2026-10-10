import { describe, it, expect } from 'vitest';
import { filterPixels, validateCrop } from '../../src/image';
describe('document image processing', () => {
  it('preserves original pixels and alpha', () => {
    const pixels = new Uint8ClampedArray([255, 100, 40, 128]);
    filterPixels(pixels, 'original');
    expect([...pixels]).toEqual([255, 100, 40, 128]);
  });
  it('computes luminance rather than taking one color channel', () => {
    const pixels = new Uint8ClampedArray([255, 0, 0, 128, 0, 255, 0, 255]);
    filterPixels(pixels, 'gray');
    expect([...pixels]).toEqual([76, 76, 76, 128, 150, 150, 150, 255]);
  });
  it('increases contrast and clips highlights and shadows', () => {
    const pixels = new Uint8ClampedArray([0, 0, 0, 255, 255, 255, 255, 255]);
    filterPixels(pixels, 'document');
    expect([...pixels]).toEqual([0, 0, 0, 255, 255, 255, 255, 255]);
  });
  it('rejects inverted, out of bounds, empty and non-finite crops', () => {
    expect(() => validateCrop({ left: 0, top: 0, right: 100, bottom: 100 })).not.toThrow();
    for (const crop of [
      { left: 80, top: 0, right: 20, bottom: 100 },
      { left: -1, top: 0, right: 100, bottom: 100 },
      { left: 0, top: 50, right: 100, bottom: 50 },
      { left: NaN, top: 0, right: 100, bottom: 100 },
    ]) expect(() => validateCrop(crop)).toThrow();
  });
});
