# PriWallet

**PriWallet** adalah aplikasi Android untuk mencatat pemasukan, pengeluaran, dan perpindahan antara saldo non-tunai dan tunai. Data keuangan disimpan secara lokal di perangkat menggunakan Room.

> **Status: masih dalam tahap pengembangan.** Fitur dan tampilan masih dapat berubah; aplikasi akan terus dikembangkan. Versi ini ditujukan untuk penggunaan satu pengguna pada satu perangkat dan belum menyediakan integrasi bank, akun pengguna, atau sinkronisasi cloud.

## Preview

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_foreground.png" alt="Logo PriWallet" width="160" />
</p>

<p align="center">
  <img src="docs/images/priwallet-preview.png" alt="Preview aplikasi PriWallet" width="320" />
</p>

Screenshot adalah preview tampilan aplikasi yang dikirim sebelum perubahan nama layar menjadi PriWallet.

## Fitur

- Mencatat, mengubah, dan menghapus pemasukan serta pengeluaran.
- Mencatat transfer antara saldo non-tunai dan tunai.
- Melihat saldo, ringkasan bulanan, dan riwayat transaksi dengan filter.
- Mengelola kategori dan memilih tema terang atau gelap.
- Menyimpan data transaksi secara lokal di Room dengan mata uang Rupiah.
- Mengirim notifikasi transaksi baru ke Discord melalui webhook; notifikasi tertunda ditangani WorkManager.

## Teknologi

- Kotlin dan Jetpack Compose
- Room sebagai penyimpanan transaksi lokal
- WorkManager untuk pekerjaan notifikasi di latar belakang
- Android Keystore dan enkripsi AES-GCM untuk melindungi URL webhook yang tersimpan

## Keamanan dan Privasi

### Perlindungan yang sudah ada

- Data transaksi disimpan lokal di database Room dalam ruang penyimpanan privat aplikasi. Tidak ada integrasi bank, akun pengguna, atau sinkronisasi cloud.
- URL webhook disamarkan saat diketik, dienkripsi dengan AES-GCM, dan kuncinya dikelola Android Keystore. Aplikasi memvalidasi URL agar memakai HTTPS dan domain webhook Discord.
- Izin backup aplikasi dinonaktifkan melalui `android:allowBackup="false"`.
- URL webhook tidak ditulis ke log aplikasi. Transaksi tetap disimpan meskipun notifikasi Discord gagal.

### Batasan keamanan saat ini

- Database Room **belum dienkripsi secara terpisah**. Penyimpanan privat Android membantu membatasi akses dari aplikasi lain, tetapi bukan perlindungan terhadap perangkat yang sudah di-root atau proses aplikasi yang berhasil diakses pihak lain.
- Belum ada PIN, kata sandi, atau kunci biometrik di dalam aplikasi.
- Menonaktifkan backup aplikasi tidak selalu mencegah pemindahan data langsung antarperangkat pada semua versi atau perangkat Android.
- Saat notifikasi Discord digunakan, pesan transaksi pemasukan/pengeluaran memuat jenis, jumlah, saldo, kategori, sumber dana, waktu, dan catatan jika diisi. Transfer memuat jumlah, arah transfer, saldo total, dan waktu. Jangan masukkan informasi sensitif ke catatan.

### Rencana pengembangan keamanan

- Menambahkan opsi kunci aplikasi dengan PIN atau biometrik.
- Mengevaluasi enkripsi database lokal untuk melindungi data transaksi saat perangkat tidak terkunci.
- Menetapkan aturan backup dan pemindahan perangkat dengan lebih jelas, serta mempertimbangkan ekspor data terenkripsi yang dikendalikan pengguna.
- Menyiapkan konfigurasi build rilis dan penandatanganan yang aman, lalu melakukan pemeriksaan keamanan sebelum distribusi lebih luas.
- Mengevaluasi backend untuk mengelola pengiriman Discord jika aplikasi didistribusikan secara luas, sehingga URL webhook tidak disimpan di aplikasi klien.

## Menyiapkan webhook Discord

Webhook mengirim pesan ke satu channel Discord. Buat webhook pada channel privat yang hanya dapat dilihat orang yang Anda percaya.

1. Di Discord, buka pengaturan server lalu **Integrations / Integrasi → Webhooks**. Pilih **New Webhook / Buat Webhook**.
2. Beri nama webhook dan pilih channel privat tujuan. Simpan, lalu salin URL webhook. Label menu dapat berbeda sedikit antarversi Discord; lihat [panduan integrasi server resmi Discord](https://support.discord.com/hc/en-us/articles/360045093012-Server-Integrations-Page).
3. Buka PriWallet → **Pengaturan**. Tempel URL ke kolom **URL webhook Discord**, lalu pilih **Simpan webhook**. URL disamarkan di layar dan disimpan terenkripsi pada perangkat.
4. Aktifkan **Notifikasi Discord**.
5. Tambahkan transaksi pemasukan atau pengeluaran baru untuk mencoba integrasi. Transaksi lama tidak dikirim ulang. Transfer baru juga memiliki notifikasi tersendiri.

Jika perangkat offline, transaksi tetap tersimpan dan notifikasi menunggu koneksi. WorkManager mencoba mengirim ulang saat jaringan tersedia. Status notifikasi terlihat pada transaksi. Jika status gagal, pastikan URL masih valid, notifikasi aktif, dan webhook belum dihapus; URL yang dicabut perlu diganti dengan URL baru. Setelah memperbaiki masalah, pilih **Coba lagi** pada transaksi berstatus gagal.

**Jaga URL webhook seperti kata sandi.** Siapa pun yang memilikinya dapat mengirim pesan ke channel terkait. Jangan bagikan URL, unggah ke tangkapan layar, atau masukkan ke commit. Untuk transaksi pemasukan/pengeluaran, embed menyertakan catatan bila catatan diisi; jangan tulis informasi sensitif di sana. Untuk distribusi aplikasi yang lebih luas, penggunaan backend perlu dievaluasi agar webhook tidak dikelola langsung oleh aplikasi klien.

## Menjalankan dari Android Studio

1. Buka folder project ini di Android Studio.
2. Pasang Android SDK Platform 35 dan gunakan JDK 17.
3. Tunggu Gradle selesai sinkronisasi, lalu jalankan konfigurasi `app` pada perangkat Android atau emulator (minimum Android API 26).

Dependensi Gradle memerlukan koneksi internet saat pertama kali disiapkan.
