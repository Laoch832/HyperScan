import { mkdir, copyFile, readdir, readFile, writeFile, rm } from 'node:fs/promises';
import { gunzipSync } from 'node:zlib';
const target = new URL('../public/ocr/', import.meta.url);
await mkdir(new URL('core/', target), { recursive: true });
await mkdir(new URL('lang/', target), { recursive: true });
await copyFile('node_modules/tesseract.js/dist/worker.min.js', new URL('worker.min.js', target));
for (const name of await readdir('node_modules/tesseract.js-core')) {
  if (/\.wasm(?:\.js)?$/.test(name)) await copyFile(`node_modules/tesseract.js-core/${name}`, new URL(`core/${name}`, target));
}
for (const language of ['eng', 'chi_sim']) {
  // AAPT transparently strips .gz from assets. Ship the explicit uncompressed name on all platforms.
  const data = await readFile(`node_modules/@tesseract.js-data/${language}/4.0.0_best_int/${language}.traineddata.gz`);
  await writeFile(new URL(`lang/${language}.traineddata`, target), gunzipSync(data));
  await rm(new URL(`lang/${language}.traineddata.gz`, target), { force: true });
}
console.log('Local Chinese/English OCR assets prepared.');
