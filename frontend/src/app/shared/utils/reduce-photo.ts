const MAX_SIDE = 2400;
const JPEG_QUALITY = 0.9;

// CA-23: an iPhone photo may be HEIC, which the server does not read; the browser that can open it turns it into a
// JPEG, and a large photo travels lighter. If the browser cannot, the original goes and the server says what is wrong.
export async function reducePhoto(file: File): Promise<File> {
  if (typeof createImageBitmap === 'undefined') {
    return file;
  }
  let bitmap: ImageBitmap | null = null;
  try {
    bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    const context = canvas.getContext('2d');
    if (!context) {
      return file;
    }
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY));
    return blob ? new File([blob], 'factura.jpg', { type: 'image/jpeg' }) : file;
  } catch {
    return file;
  } finally {
    bitmap?.close();
  }
}
