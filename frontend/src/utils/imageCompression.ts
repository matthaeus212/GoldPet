import imageCompression from 'browser-image-compression';

export async function compressImage(file: File): Promise<File> {
  // Skip GIFs (preserve animation) and already-small files
  if (file.type === 'image/gif') return file;
  if (file.size <= 500 * 1024) return file;  // Skip if under 500KB

  try {
    const options = {
      maxSizeMB: 1,
      maxWidthOrHeight: 1920,
      useWebWorker: true,
      // PNG 투명도 보존: PNG는 PNG로, 나머지는 JPEG로 변환
      fileType: file.type === 'image/png' ? 'image/png' as const : 'image/jpeg' as const,
    };
    const compressed = await imageCompression(file, options);
    return compressed;
  } catch (error) {
    console.warn('Image compression failed, using original:', error);
    return file;  // Graceful fallback
  }
}
