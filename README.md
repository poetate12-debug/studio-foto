# Reang Studio Foto

Prototipe workspace editor foto untuk alur foto acara: proyek, penyesuaian foto, template berlapis, pilihan model AI, dan ekspor cetak.

## Jalankan aplikasi

**Cara paling mudah:** buka `index.html` melalui server statis atau GitHub Pages. Aplikasi dirancang sebagai web app responsif yang bisa digunakan dari Android dan desktop. Jika dipasang melalui HTTPS, service worker menyediakan app shell offline.

Untuk GitHub Pages:
1. Buka **Settings → Pages** pada repository ini.
2. Pilih **Deploy from a branch**.
3. Pilih branch **main**, folder **/(root)**, lalu simpan.
4. Buka URL Pages yang diberikan GitHub.

## Fitur yang sudah tersedia pada prototipe

- Navigasi Proyek, Alat AI, Template, dan Pengaturan.
- Membuat proyek, memasukkan banyak foto, dan menyimpan metadata lokal.
- Penyimpanan proyek dan template menggunakan IndexedDB pada browser.
- Penyesuaian lokal kecerahan, kontras, saturasi, serta posisi dan ukuran area subjek.
- Template per-proyek dengan pilihan ukuran A3, A4, ukuran foto, dan dimensi khusus.
- Layer template: background, gambar/logo/ornamen, bingkai, dan teks; urutan, visibilitas, penguncian, dan properti layer.
- Nama acara dan tanggal proyek dapat dipakai pada teks template melalui placeholder `NAMA ACARA` dan `TANGGAL ACARA`.
- Thumbnail proyek memakai pratinjau komposisi final, bukan file sumber secara langsung.
- Ekspor PNG/JPEG untuk satu foto atau batch unduhan berurutan; ekspor template.
- Pemilih kandidat model per fitur: hapus background, hapus objek, blur background, buat background, enhance/upscale, dan perbaikan arah mata.
- PWA manifest dan service worker untuk app shell.

## Batasan penting versi ini

Ini **prototipe front-end awal**, belum aplikasi produksi penuh.

- Panggilan fal.ai belum terhubung. Pilihan model di UI adalah kandidat konfigurasi; endpoint, parameter, biaya aktual, dan kompatibilitas wajib diverifikasi sebelum integrasi.
- API key tidak disimpan atau ditanam pada browser/repository. Integrasi produksi membutuhkan backend proxy untuk menjaga rahasia API, mengatur autentikasi, pembatasan biaya, dan validasi input.
- Penghapusan background/objek, kuas mask Keep/Remove, bokeh berbasis segmentasi, generasi background, enhancement AI asli, serta perbaikan arah mata belum aktif.
- Ekspor saat ini berupa file gambar per foto. Pembuatan ZIP, antrean ekspor yang tangguh, pemulihan job gagal, dan sinkronisasi cloud belum tersedia.
- Tata letak layer dan alur komposisi adalah dasar yang bisa diuji; perlu pengujian dengan foto asli dan template Anda sebelum dipakai untuk produksi cetak A3.
- Proyek dan template tersimpan di perangkat/browser yang digunakan. Menghapus data browser dapat menghilangkannya; belum ada backup cloud.

## Ukuran cetak

A3 potret pada 300 DPI sekitar **3508 × 4961 px**. Hasil akhir dapat dibatasi oleh memori perangkat/browser dan dimensi foto sumber. Untuk foto penting, lakukan pemeriksaan kualitas hasil ekspor sebelum dicetak.

## Keamanan

Repository ini bersifat **public**. Jangan commit API key, foto pelanggan, file template privat, ataupun data pribadi. Aplikasi menyimpan proyek lokal di browser, bukan pada repository GitHub.
