/**
 * EN: Photos picked in the browser are shown through object URLs; this remembers which File each URL
 *     stands for, so saving the product can upload the real file.
 * VI: Ảnh chọn trên trình duyệt được hiển thị qua object URL; nơi này nhớ mỗi URL ứng với File nào, để khi
 *     lưu sản phẩm thì tải lên đúng file thật.
 */

const files = new Map<string, File>();

export function rememberLocalFile(url: string, file: File): void {
  files.set(url, file);
}

export function localFileFor(url: string): File | undefined {
  return files.get(url);
}

export function forgetLocalFile(url: string): void {
  files.delete(url);
}

/** EN: True for a photo still on this device, not yet uploaded. / VI: True với ảnh còn trên máy, chưa tải lên. */
export function isLocalFileUrl(url: string): boolean {
  return url.startsWith("blob:");
}
