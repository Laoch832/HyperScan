import { Capacitor, registerPlugin } from '@capacitor/core';
interface CameraPlugin { open(): Promise<{ count: number }>; read(options: { index: number }): Promise<{ image: string }>; release(): Promise<void> }
const nativeCamera = registerPlugin<CameraPlugin>('ScanCamera');
/** Feed each captured page to the same editing pipeline as imported images. */
export async function scanCamera(add: (image: string) => void): Promise<void> {
  if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android') {
    try { const { count } = await nativeCamera.open(); for (let index = 0; index < count; index++) add((await nativeCamera.read({ index })).image); }
    finally { await nativeCamera.release(); }
    return;
  }
  if (!navigator.mediaDevices?.getUserMedia) throw new Error('当前浏览器不能打开实时相机，请使用 HTTPS 或 Android 版。');
  const dialog = document.createElement('dialog'); dialog.className = 'live-camera';
  dialog.innerHTML = '<div class="camera-bar"><strong>HyperScan · 扫描</strong><button type="button" data-close>返回编辑</button></div><video autoplay muted playsinline aria-label="相机实时取景"></video><p role="status">正在启动实时取景…</p><div class="camera-bar"><button type="button" data-shutter class="primary" disabled>● 拍摄</button><button type="button" data-done>完成 · 0 页</button></div>';
  document.body.append(dialog); dialog.showModal();
  const video = dialog.querySelector('video')!;
  const shutter = dialog.querySelector<HTMLButtonElement>('[data-shutter]')!;
  const status = dialog.querySelector('p')!;
  const done = dialog.querySelector<HTMLButtonElement>('[data-done]')!;
  let stream: MediaStream | undefined, closed = false, count = 0;
  let finish!: () => void; const finished = new Promise<void>(resolve => { finish = resolve; });
  const close = () => { closed = true; stream?.getTracks().forEach(track => track.stop()); dialog.close(); finish(); };
  dialog.querySelector('[data-close]')!.addEventListener('click', close); done.addEventListener('click', close);
  dialog.addEventListener('cancel', event => { event.preventDefault(); close(); });
  const visibility = () => { if (document.hidden) close(); }; document.addEventListener('visibilitychange', visibility);
  shutter.onclick = () => {
    if (video.readyState < 2 || !video.videoWidth || closed) return;
    const scale = Math.min(1, 2400 / Math.max(video.videoWidth, video.videoHeight));
    const canvas = document.createElement('canvas'); canvas.width = Math.round(video.videoWidth * scale); canvas.height = Math.round(video.videoHeight * scale);
    canvas.getContext('2d')!.drawImage(video, 0, 0, canvas.width, canvas.height);
    add(canvas.toDataURL('image/jpeg', 0.92)); count++; done.textContent = `完成 · ${count} 页`; status.textContent = `已拍 ${count} 页 · 可继续拍摄`; if (count >= 20) shutter.disabled = true;
  };
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false });
    if (closed) return;
    video.srcObject = stream; await video.play();
    shutter.disabled = false; status.textContent = '实时取景 · 将纸张放在画面中';
    await finished;
  } catch (error) {
    if (!closed) throw new Error(error instanceof DOMException && error.name === 'NotAllowedError' ? '相机权限未允许，请在浏览器设置中开启，也可导入图片。' : '无法启动相机，请检查设备或是否被其他应用占用。');
  } finally { stream?.getTracks().forEach(track => track.stop()); document.removeEventListener('visibilitychange', visibility); dialog.remove(); }
}
