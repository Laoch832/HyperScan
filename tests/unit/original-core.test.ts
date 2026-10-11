import { describe, expect, it } from 'vitest';
import { fullCorners, validateCorners, pdfPageSize, defaultPdfSettings, type Corners } from '../../src/original-core';

describe('original core geometry contract', () => {
  it('accepts clockwise full-page and perspective quadrilaterals', () => {
    expect(() => validateCorners(fullCorners())).not.toThrow();
    expect(() => validateCorners([{ x: .12, y: .05 }, { x: .92, y: .15 }, { x: .82, y: .95 }, { x: .08, y: .8 }])).not.toThrow();
  });
  it.each([
    [{ x: 0, y: 0 }, { x: 1, y: 1 }, { x: 1, y: 0 }, { x: 0, y: 1 }],
    [{ x: 0, y: 0 }, { x: 0, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }],
    [{ x: -.1, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }],
    [{ x: NaN, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }],
  ])('rejects crossed, collapsed or out-of-range corners', (...points) => {
    expect(() => validateCorners(points as Corners)).toThrow();
  });
});

describe('original PDF page policy', () => {
  it('follows each image orientation independently', () => {
    const settings = defaultPdfSettings();
    expect(pdfPageSize(settings, { width: 600, height: 800 })).toEqual([595, 842]);
    expect(pdfPageSize(settings, { width: 1000, height: 600 })).toEqual([842, 595]);
  });
  it('honors explicit paper and orientation', () => {
    expect(pdfPageSize({ paper: 'a5', orientation: 'portrait', margin: 0 }, { width: 1000, height: 600 })).toEqual([420, 595]);
    expect(pdfPageSize({ paper: 'letter', orientation: 'landscape', margin: .2 }, { width: 600, height: 1000 })).toEqual([792, 612]);
  });
  it('rejects invalid padding instead of exporting an empty drawing area', () => {
    expect(() => pdfPageSize({ ...defaultPdfSettings(), margin: -.01 }, { width: 600, height: 800 })).toThrow();
    expect(() => pdfPageSize({ ...defaultPdfSettings(), margin: NaN }, { width: 600, height: 800 })).toThrow();
  });
});
