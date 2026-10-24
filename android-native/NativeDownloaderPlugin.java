package id.reang.studiofoto;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "NativeDownloader")
public class NativeDownloaderPlugin extends Plugin {
    @PluginMethod
    public void downloadUrl(PluginCall call) {
        String url = call.getString("url");
        String filename = safeFilename(call.getString("filename"));
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            call.reject("Alamat gambar tidak valid.");
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                call.reject("Layanan unduhan Android tidak tersedia.");
                return;
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("Menyimpan hasil Reang Studio Foto");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "ReangStudioFoto/" + filename
            );
            long downloadId = manager.enqueue(request);
            JSObject result = new JSObject();
            result.put("queued", true);
            result.put("downloadId", downloadId);
            result.put("path", "Download/ReangStudioFoto/" + filename);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Gagal memulai unduhan Android: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void saveBase64(PluginCall call) {
        String encoded = call.getString("data");
        String mimeType = call.getString("mimeType", "image/png");
        String filename = safeFilename(call.getString("filename"));
        if (encoded == null || encoded.isEmpty()) {
            call.reject("Data gambar kosong.");
            return;
        }
        Uri outputUri = null;
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/ReangStudioFoto");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                outputUri = getContext().getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                );
                if (outputUri == null) throw new Exception("Android tidak dapat membuat file unduhan.");
                try (OutputStream stream = getContext().getContentResolver().openOutputStream(outputUri)) {
                    if (stream == null) throw new Exception("Tidak dapat membuka file tujuan.");
                    stream.write(bytes);
                    stream.flush();
                }
                ContentValues complete = new ContentValues();
                complete.put(MediaStore.Downloads.IS_PENDING, 0);
                getContext().getContentResolver().update(outputUri, complete, null, null);
            } else {
                File directory = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ReangStudioFoto"
                );
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new Exception("Folder Download tidak dapat dibuat.");
                }
                File output = new File(directory, filename);
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    stream.write(bytes);
                    stream.flush();
                }
                outputUri = Uri.fromFile(output);
            }
            JSObject result = new JSObject();
            result.put("saved", true);
            result.put("path", "Download/ReangStudioFoto/" + filename);
            result.put("uri", outputUri.toString());
            result.put("bytes", bytes.length);
            call.resolve(result);
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outputUri != null) {
                try { getContext().getContentResolver().delete(outputUri, null, null); } catch (Exception ignored) {}
            }
            call.reject("Gagal menyimpan gambar ke folder Download: " + e.getMessage(), e);
        }
    }

    private String safeFilename(String value) {
        String name = value == null ? "reang-hasil.png" : value;
                name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        name = name.replaceAll("\\s+", "_");
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isEmpty()) name = "reang-hasil.png";
        return name.length() > 120 ? name.substring(0, 120) : name;
    }
}
