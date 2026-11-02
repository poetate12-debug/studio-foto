# Reang Studio Foto — Native Android

Aplikasi Android ini dibangun dengan **Kotlin + Jetpack Compose**, AndroidX Room, Coil, dan Android MediaStore. Modul ini tidak memakai WebView atau Capacitor sebagai UI host. Kode PWA lama tetap berada di direktori terpisah dan tidak dimuat ke aplikasi native.

## Build

- Gradle project: `native-android/`
- Application ID: `id.reang.studiofoto.nativeapp`
- Build type: signed release APK melalui GitHub Actions
- Minimum Android: API 26
- Versi aplikasi: 1.1.0-native-workflow (versionCode 6)

Workflow: `.github/workflows/native-android.yml`

Required GitHub Actions secrets:
- `REANG_KEYSTORE_BASE64`
- `REANG_KEYSTORE_PASSWORD`
- `REANG_KEY_ALIAS`

Workflow memverifikasi application ID dan fingerprint sertifikat APK. Build berhasil membuktikan kompilasi dan penandatanganan, tetapi bukan pengganti pengujian setiap fitur pada perangkat Android.

## Fitur native yang sudah dipindahkan ke alur kerja

- Beranda, daftar proyek, pembuatan proyek, galeri proyek, dan impor beberapa foto.
- Foto sumber disalin ke direktori privat aplikasi. Metadata dan jalur file disimpan dalam Room; pengeditan tidak menimpa file sumber.
- Tahap Cahaya dengan brightness/exposure, contrast, saturation, warmth, highlights, shadows, blacks, dehaze, clarity, vibrance, dan vignette. Penyesuaian diproses pada piksel hasil render/ekspor.
- Tahap Cutout dengan Bria RMBG, BiRefNet v2 Portrait, atau Rembg melalui fal.ai.
- Kuas koreksi Cutout: pulihkan piksel dari foto asli atau hapus area menjadi transparan.
- Mode bokeh dengan subjek terpisah dan latar blur.
- Background AI dibuat terpisah dari subjek agar wajah/foto asli tidak ikut digambar ulang oleh model generasi background.
- Koleksi lokal reusable untuk background, PNG template, dan logo; aset disalin ke penyimpanan aplikasi dan dapat dipilih lagi pada proyek lain.
- Tahap Template dengan preset A3/A4 potret/lanskap, ukuran khusus, pemilihan background, transformasi posisi/zoom background, transformasi ukuran/posisi objek, PNG overlay, logo, nama acara, dan tanggal.
- Tahap Enhance dengan pilihan Topaz Precision, Real-ESRGAN, atau Recraft Crisp; tersedia kontrol 2×/4× dan kekuatan campuran hasil dengan sumber.
- Alat tambahan penghapusan objek dan perbaikan detail/arah mata melalui model AI.
- Tahap Export menghasilkan file JPEG atau PNG ke galeri Android, dengan ukuran cetak dan DPI yang dapat dipilih. Ekspor satu foto maupun seluruh foto dalam proyek tersedia.
- Ukuran JPEG diberi metadata resolusi DPI. Dimensi raster hasil mengikuti ukuran cetak/DPI yang dipilih, dengan pembatasan piksel untuk mengurangi risiko kehabisan memori.

## Batasan yang masih perlu diselesaikan

Ini adalah implementasi native bertahap, belum parity 100% terhadap seluruh fitur PWA lama.

- Belum ada pengembangan RAW/DNG, HSL mixer per warna, brush cahaya lokal, layer arbitrer berlapis dengan urutan/locking, pengaturan tekstur selektif, atau semua alat kecil dari editor web lama.
- Template PNG dipakai sebagai satu overlay penuh; logo memakai penempatan sederhana. Editor layer bebas seperti Photoshop belum tersedia.
- Bokeh saat ini menggunakan pemrosesan native blur sederhana, bukan simulasi lensa bokeh optik.
- Perbaikan wajah/arah mata memakai model generatif. Model dapat mengubah detail wajah; hasil harus diperiksa dan bukan dijamin mempertahankan identitas secara sempurna.
- Key fal.ai hanya disimpan dalam memori aplikasi untuk sesi aktif dan perlu dimasukkan lagi setelah aplikasi ditutup. Jangan membagikan APK yang berisi key pribadi. Setiap pekerjaan AI memerlukan internet, akses model, dan saldo akun fal.ai.
- Belum ada sinkronisasi cloud atau mekanisme backup/restore proyek. Data di direktori privat dan Room bertahan setelah aplikasi ditutup atau cache browser dibersihkan, tetapi akan hilang bila pengguna menghapus data aplikasi atau meng-uninstall aplikasi tanpa backup.
- Perlu uji fungsional pada perangkat nyata untuk setiap varian Android, jenis file foto, model AI, dan ukuran ekspor sebelum digunakan sebagai alur produksi harian.

## Penyimpanan dan privasi

Foto asli yang diimpor disalin ke `filesDir` privat aplikasi. Hasil Cutout, background, overlay, logo, hasil AI, serta metadata proyek juga disimpan secara lokal. Foto sumber tidak ditimpa. Menghapus cache browser tidak memengaruhi penyimpanan native. Penghapusan data aplikasi atau uninstall tetap menghapus basis data dan file privat.

Untuk proses cetak penting, selalu ekspor file akhir ke galeri/penyimpanan eksternal dan simpan salinan cadangan secara terpisah.
