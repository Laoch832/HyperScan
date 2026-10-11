import { beforeEach, describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ native: vi.fn(), detect: vi.fn(), correct: vi.fn() }));
vi.mock('../../src/original-core', async importOriginal => ({ ...await importOriginal<typeof import('../../src/original-core')>(), nativeCorePlatform: mocks.native, correctOriginal: mocks.correct, originalCore: { detectDocument: mocks.detect } }));
import { prepareScan } from '../../src/scan-pipeline';
describe('automatic scan pipeline retains originals', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.native.mockReturnValue(true); });
  it('detects and corrects a page using the original bridge', async () => {
    mocks.detect.mockResolvedValue({ corners: [.1,.1,.9,.1,.9,.9,.1,.9], engine: 'original-mnn-four-corner' }); mocks.correct.mockResolvedValue('corrected');
    const { page } = await prepareScan('source', true);
    expect(page.original).toBe('source'); expect(page.image).toBe('corrected'); expect(page.corrected).toBe('corrected'); expect(page.edit.corners).toHaveLength(4); expect(page.scanEngine).toBe('original-mnn-four-corner');
  });
  it('preserves the photo without a pretend crop when detection misses', async () => {
    mocks.detect.mockResolvedValue({ corners: null, engine: 'hyperscan-local-contour' });
    const { page, message } = await prepareScan('source', true);
    expect(page.image).toBe('source'); expect(page.corrected).toBeUndefined(); expect(mocks.correct).not.toHaveBeenCalled(); expect(message).toContain('原图');
  });
  it('rejects crossed corners before correction and preserves the original on correction failure', async () => {
    mocks.detect.mockResolvedValue({ corners: [.1,.1,.9,.9,.9,.1,.1,.9] });
    expect((await prepareScan('source', true)).page.image).toBe('source'); expect(mocks.correct).not.toHaveBeenCalled();
    mocks.detect.mockResolvedValue({ corners: [.1,.1,.9,.1,.9,.9,.1,.9] }); mocks.correct.mockRejectedValue(new Error('native failure'));
    const { page, message } = await prepareScan('source', true); expect(page.original).toBe('source'); expect(page.image).toBe('source'); expect(page.edit.corners).toBeUndefined(); expect(message).toContain('未完成');
  });
  it('respects the automatic processing switch', async () => { const { page } = await prepareScan('source', false); expect(page.image).toBe('source'); expect(mocks.detect).not.toHaveBeenCalled(); });
});
