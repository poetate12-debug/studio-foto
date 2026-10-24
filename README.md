# Reang Studio Foto

Prototipe workspace editor foto untuk alur foto acara: proyek, penyesuaian foto, template berlapis, pilihan model AI, dan ekspor cetak.

## Deploy backend (disarankan)

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/poetate12-debug/studio-foto)

1. Buka tombol **Deploy to Render** dan masuk ke akun Render.
2. Tinjau Blueprint dari file `render.yaml`, lalu deploy.
3. Setelah service aktif, salin URL HTTPS `*.onrender.com` yang diberikan Render.
4. Pada aplikasi Android, buka **Pengaturan → AI & fal.ai**, tempel URL itu pada **Alamat backend fal.ai**.
5. Buat API key baru di dashboard fal.ai, tempel sementara ke kolom API key aplikasi, lalu tekan **Tes koneksi fal.ai**. Tes ini hanya menanyakan status job yang tidak ada dan tidak membuat inference job.
6. Untuk pemrosesan AI, model harus diizinkan melalui `FAL_ALLOWED_MODELS`; endpoint inference sesungguhnya dan alur alat di front-end masih perlu diselesaikan.

Render akan meminta nilai `FAL_KEY` karena backend dapat menggunakannya sebagai key default. Rahasia itu harus diisi pada environment server, bukan di GitHub atau di APK. Jika memakai key per sesi, aplikasi hanya mengirimkannya melalui HTTPS untuk tes dan tidak menyimpannya di browser.

## Menjalankan secara lokal

Persyaratan: Node.js 20 atau lebih baru.

```bash
npm install
cp .env.example .env
# Isi FAL_KEY hanya pada .env lokal/server; jangan commit file ini.
# Isi FAL_ALLOWED_MODELS dengan endpoint IDs yang sudah diverifikasi.
npm start
```

Kemudian buka `http://localhost:3000`. Endpoint `/api/health` menunjukkan status backend. Endpoint `/api/fal/connection-test` memeriksa autentikasi key tanpa membuat pekerjaan inferensi.

## Fitur front-end yang tersedia
- Navigasi Proyek, Alat AI, Template, dan Pengaturan.
- Membuat proyek, memasukkan banyak foto, dan menyimpan metadata lokal di IndexedDB.
- Penyesuaian lokal kecerahan, kontras, saturasi, serta posisi dan ukuran area subjek.
- Template per-proyek dengan ukuran A3 default, A4, ukuran foto, dan dimensi khusus.
- Layer template untuk background, gambar/logo/ornamen, bingkai, dan teks; urutan, visibilitas, penguncian, dan properti layer.
- Nama acara dan tanggal proyek dapat digunakan pada teks template.
- Thumbnail proyek menggunakan pratinjau komposisi akhir.
- Ekspor PNG/JPEG satu foto atau batch unduhan berurutan; ekspor template.
- Pemilih kandidat model per fitur: hapus background, hapus objek, blur background, buat background, enhance/upscale, dan perbaikan arah mata.
- PWA manifest dan service worker untuk app shell.

## Batasan saat ini
Ini masih **prototipe**, belum aplikasi produksi penuh.

- Tombol AI di front-end belum memproses semua fitur melalui backend fal.ai.
- Koneksi test perlu URL backend yang sudah di-deploy dan API key valid.
- Backend proxy membatasi endpoint melalui `FAL_ALLOWED_MODELS`, tetapi autentikasi pengguna, kuota per pengguna, dan dashboard penggunaan belum tersedia.
- Penghapusan background/objek, kuas mask Keep/Remove, bokeh berbasis segmentasi, generasi background, enhancement AI asli, serta perbaikan arah mata belum aktif.
- Ekspor ZIP, antrean ekspor tangguh, pemulihan job gagal, dan sinkronisasi cloud belum tersedia.
- Komposisi layer perlu diuji dengan foto dan template asli sebelum dipakai untuk produksi cetak A3.
- Proyek dan template tersimpan pada browser/perangkat; menghapus data browser dapat menghilangkannya.

## Ukuran cetak
A3 potret pada 300 DPI sekitar **3508 × 4961 px**. Hasil akhir juga dibatasi resolusi sumber dan memori perangkat/browser.

## Keamanan
Repository ini **publik**. Jangan commit API key, foto pelanggan, template privat, atau data pribadi. API key yang sempat dibagikan di chat harus dicabut dan diganti. Simpan `FAL_KEY` hanya pada environment server, bukan di front-end atau APK.
