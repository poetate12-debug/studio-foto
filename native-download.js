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
    let saved;
    if (source.startsWith('data:')) {
      const comma = source.indexOf(',');
      if (comma < 0) throw new Error('Data gambar lokal tidak valid.');
      const mime = (source.slice(0, comma).match(/^data:([^;,]+)/i) || [])[1] || 'image/png';
      saved = await NativeDownloader.saveBase64({
        filename,
        mimeType: mime,
        data: source.slice(comma + 1)
      });
    } else {
      saved = await NativeDownloader.downloadUrl({ url: source, filename });
    }

    const localUrl = saved && saved.localPath ? Capacitor.convertFileSrc(saved.localPath) : null;
    return {
      ...(saved || {}),
      saved: true,
      filename,
      localUrl,
      path: (saved && saved.path) || `Download/ReangStudioFoto/${filename}`
    };
  }

  let response;
  try {
    response = await fetch(source, { mode: 'cors' });
  } catch {
    throw new Error('Browser menolak unduhan lintas domain. Gunakan APK Android terbaru untuk menyimpan dan memakai hasil AI dalam proyek.');
  }
  if (!response.ok) throw new Error(`Unduhan gagal (HTTP ${response.status}).`);
  const blob = await response.blob();
  if (!blob || !blob.size) throw new Error('File gambar kosong.');
  triggerBrowserDownload(blob, filename);
  return { saved: true, filename, path: 'Unduhan browser', localUrl: source.startsWith('data:') ? source : null };
}

window.ReangNativeDownloads = {
  isNative: () => Capacitor.isNativePlatform(),
  saveFalImage
};
