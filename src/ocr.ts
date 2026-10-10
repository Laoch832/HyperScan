import { createWorker } from 'tesseract.js';
export async function recognize(image: string, progress: (value: number) => void): Promise<string> {
  const worker = await createWorker(['chi_sim', 'eng'], 1, {
    workerPath: `${location.origin}/ocr/worker.min.js`,
    corePath: `${location.origin}/ocr/core`,
    langPath: `${location.origin}/ocr/lang`,
    logger: message => { if (message.status === 'recognizing text') progress(message.progress); },
  });
  try { return (await worker.recognize(image)).data.text; }
  finally { await worker.terminate(); }
}
