package id.reang.studiofoto;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "NativeDownloader")
public class NativeDownloaderPlugin extends Plugin {
    private static final int MAX_IMAGE_BYTES = 30 * 1024 * 1024;

    @PluginMethod
    public void downloadUrl(PluginCall call) {
        String url = call.getString("url");
        String filename = safeFilename(call.getString("filename"));
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            call.reject("Alamat gambar tidak valid.");
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "ReangStudioFoto/1.0 Android");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new Exception("Server gambar memberi HTTP " + status + ".");
                }

                String mimeType = connection.getContentType();
                if (mimeType == null || !mimeType.toLowerCase().startsWith("image/")) {
                    mimeType = mimeFromName(filename);
                } else {
                    int semicolon = mimeType.indexOf(';');
                    if (semicolon >= 0) mimeType = mimeType.substring(0, semicolon).trim();
                }

                byte[] bytes;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        if (output.size() > MAX_IMAGE_BYTES) {
                            throw new Exception("File gambar melebihi batas 30 MB.");
                        }
                    }
                    bytes = output.toByteArray();
                }
                if (bytes.length == 0) throw new Exception("File gambar kosong.");

                File localFile = saveToPrivateFiles(bytes, filename);
                Uri downloadUri = saveToPublicDownloads(bytes, filename, mimeType);

                JSObject result = new JSObject();
                result.put("saved", true);
                result.put("path", "Download/ReangStudioFoto/" + filename);
                result.put("localPath", localFile.getAbsolutePath());
                result.put("uri", downloadUri == null ? "" : downloadUri.toString());
                result.put("mimeType", mimeType);
                result.put("bytes", bytes.length);
                call.resolve(result);
            } catch (Exception e) {
                call.reject("Gagal mengunduh dan menyimpan gambar: " + e.getMessage(), e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "ReangImageDownload").start();
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

        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            if (bytes.length == 0) throw new Exception("Data gambar kosong.");
            File localFile = saveToPrivateFiles(bytes, filename);
            Uri downloadUri = saveToPublicDownloads(bytes, filename, mimeType);

            JSObject result = new JSObject();
            result.put("saved", true);
            result.put("path", "Download/ReangStudioFoto/" + filename);
            result.put("localPath", localFile.getAbsolutePath());
            result.put("uri", downloadUri == null ? "" : downloadUri.toString());
            result.put("mimeType", mimeType);
            result.put("bytes", bytes.length);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Gagal menyimpan gambar ke folder Download: " + e.getMessage(), e);
        }
    }

    private File saveToPrivateFiles(byte[] bytes, String filename) throws Exception {
        File directory = new File(getContext().getFilesDir(), "ReangStudioFoto");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("Folder penyimpanan aplikasi tidak dapat dibuat.");
        }
        File output = new File(directory, filename);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(bytes);
            stream.flush();
        }
        return output;
    }

    private Uri saveToPublicDownloads(byte[] bytes, String filename, String mimeType) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType == null ? mimeFromName(filename) : mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/ReangStudioFoto");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Android tidak dapat membuat file di folder Download.");
            try {
                try (OutputStream stream = getContext().getContentResolver().openOutputStream(uri)) {
                    if (stream == null) throw new Exception("Tidak dapat membuka file tujuan.");
                    stream.write(bytes);
                    stream.flush();
                }
                ContentValues complete = new ContentValues();
                complete.put(MediaStore.Downloads.IS_PENDING, 0);
                getContext().getContentResolver().update(uri, complete, null, null);
                return uri;
            } catch (Exception e) {
                try { getContext().getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
                throw e;
            }
        }

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
        return Uri.fromFile(output);
    }

    private String mimeFromName(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    private String safeFilename(String value) {
        String name = value == null ? "reang-hasil.png" : value;
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        name = name.replaceAll("_+", "_");
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isEmpty()) name = "reang-hasil.png";
        return name.length() > 120 ? name.substring(0, 120) : name;
    }
}
