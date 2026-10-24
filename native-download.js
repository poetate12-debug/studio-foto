import { Capacitor, registerPlugin } from '@capacitor/core';

const NativeDownloader = registerPlugin('NativeDownloader');

function safeFilename(value) {
  const cleaned = String(value || 'reang-hasil.png')
    .replace(/[^a-zA-Z0-9._-]/g, '_')
    .replace(/_{2,}/g, '_')
    .replace(/^\.+/, '')
    .slice(0, 120);
  return cleaned || 'reang-hasil.png';
}

function triggerBrowserDownload(blob, filename) {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
}

async function saveFalImage(source, requestedFilename) {
  const filename = safeFilename(requestedFilename);
  if (!source || typeof source !== 'string') {
    throw new Error('Alamat gambar hasil tidak tersedia.');
  }

  if (Capacitor.isNativePlatform()) {
    if (source.startsWith('data:')) {
      const comma = source.indexOf(',');
      if (comma < 0) throw new Error('Data gambar lokal tidak valid.');
      const mime = (source.slice(0, comma).match(/^data:([^;,]+)/i) || [])[1] || 'image/png';
      const saved = await NativeDownloader.saveBase64({
        filename,
        mimeType: mime,
        data: source.slice(comma + 1)
      });
      return { ...saved, filename, path: saved.path || `Download/ReangStudioFoto/${filename}` };
    }

    const queued = await NativeDownloader.downloadUrl({ url: source, filename });
    return {
      ...queued,
      queued: true,
      filename,
      path: queued.path || `Download/ReangStudioFoto/${filename}`
    };
  }

  let blob;
  if (source.startsWith('data:')) {
    const response = await fetch(source);
    blob = await response.blob();
  } else {
    let response;
    try {
      response = await fetch(source, { mode: 'cors' });
    } catch {
      throw new Error('Browser menolak unduhan lintas domain. Gunakan APK Reang Studio Foto untuk menyimpan langsung ke folder Download.');
    }
    if (!response.ok) throw new Error(`Unduhan gagal (HTTP ${response.status}).`);
    blob = await response.blob();
  }
  if (!blob || !blob.size) throw new Error('File gambar kosong.');
  triggerBrowserDownload(blob, filename);
  return { saved: true, filename, path: 'Unduhan browser' };
}

window.ReangNativeDownloads = {
  isNative: () => Capacitor.isNativePlatform(),
  saveFalImage
};
