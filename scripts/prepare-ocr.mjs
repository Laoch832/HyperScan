import { mkdir, copyFile, readdir } from 'node:fs/promises';
const target = new URL('../public/ocr/', import.meta.url);
await mkdir(new URL('core/', target), { recursive: true });
await mkdir(new URL('lang/', target), { recursive: true });
await copyFile('node_modules/tesseract.js/dist/worker.min.js', new URL('worker.min.js', target));
for (const name of await readdir('node_modules/tesseract.js-core')) {
  if (/\.wasm(?:\.js)?$/.test(name)) await copyFile(`node_modules/tesseract.js-core/${name}`, new URL(`core/${name}`, target));
}
for (const language of ['eng', 'chi_sim']) {
  await copyFile(`node_modules/@tesseract.js-data/${language}/4.0.0_best_int/${language}.traineddata.gz`, new URL(`lang/${language}.traineddata.gz`, target));
}
console.log('Local Chinese/English OCR assets prepared.');
