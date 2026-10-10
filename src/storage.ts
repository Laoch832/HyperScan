import type { Edit } from './image';
export type Page = { id: string; original: string; image: string; edit: Edit; text: string };
export type ScanDocument = { id: string; name: string; updatedAt: number; pages: Page[] };
const openDatabase = (): Promise<IDBDatabase> => new Promise((resolve, reject) => {
  const request = indexedDB.open('hyperscan-offline', 1);
  request.onupgradeneeded = () => request.result.createObjectStore('documents', { keyPath: 'id' });
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});
async function transaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const database = await openDatabase();
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = database.transaction('documents', mode);
      const request = action(tx.objectStore('documents'));
      tx.oncomplete = () => resolve(request.result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error ?? new Error('文档保存已中断。'));
    });
  } finally { database.close(); }
}
export const listDocuments = async () => (await transaction('readonly', s => s.getAll()) as ScanDocument[]).sort((a, b) => b.updatedAt - a.updatedAt);
export const saveDocument = (doc: ScanDocument) => transaction('readwrite', s => s.put(doc));
export const removeDocument = (id: string) => transaction('readwrite', s => s.delete(id));
