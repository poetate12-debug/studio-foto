# Reang Studio Foto

Prototipe workspace editor foto untuk alur foto acara: proyek, penyesuaian foto, template berlapis, pilihan model AI, dan ekspor cetak.

## Menjalankan aplikasi

### Front-end saja
Buka melalui GitHub Pages atau server statis. PWA responsif untuk Android dan desktop.

### Menjalankan dengan backend fal.ai
Persyaratan: Node.js 20 atau lebih baru.

```bash
npm install
cp .env.example .env
# Isi FAL_KEY di .env pada server, jangan masukkan ke Git.
# Isi FAL_ALLOWED_MODELS dengan ID endpoint yang sudah diverifikasi, pisahkan dengan koma.
npm start
```

Kemudian buka `http://localhost:3000`. Endpoint `/api/health` menunjukkan apakah fal.ai sudah dikonfigurasi.

**Catatan:** backend proxy sudah menjadi fondasi, tetapi alur tombol AI pada front-end belum dihubungkan ke endpoint ini. Model ID dan schema input harus diverifikasi per fitur sebelum dipakai.

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

- Tombol AI front-end belum memanggil backend fal.ai.
- Backend proxy mengamankan API key di sisi server dan membatasi endpoint ke model ID dalam `FAL_ALLOWED_MODELS`, tetapi belum ada autentikasi pengguna, kuota per pengguna, atau dashboard penggunaan.
- Penghapusan background/objek, kuas mask Keep/Remove, bokeh berbasis segmentasi, generasi background, enhancement AI asli, serta perbaikan arah mata belum aktif.
- Ekspor ZIP, antrean ekspor tangguh, pemulihan job gagal, dan sinkronisasi cloud belum tersedia.
- Komposisi layer perlu diuji dengan foto dan template asli sebelum dipakai untuk produksi cetak A3.
- Proyek dan template tersimpan pada browser/perangkat; menghapus data browser dapat menghilangkannya.

## Ukuran cetak
A3 potret pada 300 DPI sekitar **3508 × 4961 px**. Hasil akhir juga dibatasi resolusi sumber dan memori perangkat/browser.

## Keamanan
Repository ini **publik**. Jangan commit API key, foto pelanggan, template privat, atau data pribadi. Simpan `FAL_KEY` hanya pada environment server, jangan di front-end atau APK.
