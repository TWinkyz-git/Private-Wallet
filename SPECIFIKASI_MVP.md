# Spesifikasi MVP — Pengatur Keuangan Pribadi

Status: disetujui pengguna pada 15 Oktober 2026; implementasi MVP sedang/baru dibuat.

## 1. Tujuan dan ruang lingkup

Aplikasi Android lokal untuk satu pengguna mencatat pemasukan dan pengeluaran dalam IDR, melihat saldo dan ringkasan periode, mengelola kategori, serta mengirim pemberitahuan transaksi baru ke satu Discord webhook pilihan pengguna.

**Termasuk MVP:** transaksi pemasukan/pengeluaran yang masing-masing dicatat ke saldo non-tunai atau tunai; transfer antar dompet dari formulir Catat transaksi; daftar, filter, edit, dan hapus transaksi; ringkasan bulan berjalan; kategori bawaan dan pengelola kategori sederhana; pilihan tema putih/hitam; pengaturan webhook dan sakelar notifikasi; pengiriman latar belakang dengan WorkManager, status per transaksi, dan retry saat jaringan tersedia. Instalasi baru memulai saldo Rp0.

**Di luar cakupan:** integrasi bank/rekening, akun pengguna, sinkronisasi/cloud, multi-perangkat, ekspor/impor, anggaran per kategori (ditunda dari tujuan umum untuk versi lanjutan), grafik analitik, dan notifikasi Discord untuk edit/hapus/saldo awal/riwayat lama.

## 2. Layar dan navigasi

Navigasi bawah: **Ringkasan**, **Transaksi**, **Pengaturan**. Tombol aksi tambah transaksi mudah dijangkau dari Ringkasan dan Transaksi.

1. **Ringkasan** — total saldo saat ini, rincian saldo non-tunai dan tunai; total pemasukan/pengeluaran bulan kalender berjalan; pintasan tambah; transaksi terbaru; status notifikasi tertunda yang perlu perhatian.
2. **Transaksi** — daftar terbaru lebih dahulu yang menampilkan pemasukan, pengeluaran, dan transfer dengan arah dompet; filter jenis, kategori, dan rentang tanggal; keadaan kosong; transaksi dapat diedit, penghapusan melalui konfirmasi. Transfer hanya tampil di riwayat dan tidak dapat diedit sebagai transaksi pemasukan/pengeluaran.
3. **Tambah/Edit Transaksi** — pilih jenis pemasukan, pengeluaran, atau transfer; nominal IDR; pilih sumber Tunai/Saldo non-tunai; tujuan transfer ditentukan otomatis; kategori hanya untuk pemasukan/pengeluaran; tanggal dan waktu; catatan opsional hanya untuk pemasukan/pengeluaran. Transfer tersedia saat menambah catatan, bukan mengedit transaksi.
4. **Pengaturan** — pilihan tema putih/terang atau hitam/gelap yang tersimpan; status Discord aktif/nonaktif; input webhook rahasia, uji koneksi opsional, instruksi privasi; pengelolaan kategori; status notifikasi tertunda/gagal.
5. **Kategori** — daftar kategori, tambah/ubah/nonaktifkan. Kategori yang sudah dipakai transaksi tidak dihapus permanen; kategori nonaktif tak muncul untuk transaksi baru namun tetap tampak di riwayat.

Alur utama: Ringkasan → Catat transaksi → pilih pemasukan/pengeluaran/transfer → simpan → kembali ke Ringkasan/Transaksi; Transaksi → filter atau pilih item → edit/hapus; Pengaturan → tema/kategori/Discord.

## 3. Model data Room

Semua nominal disimpan sebagai `Long` rupiah (bilangan bulat, tanpa pecahan); waktu sebagai epoch milliseconds UTC dan ditampilkan menurut zona waktu perangkat. ID memakai UUID string.

- `TransactionEntity`: `id`, `type` (`INCOME`/`EXPENSE`), `amountIdr`, `categoryId`, `wallet` (`BALANCE`/`CASH`), `occurredAtEpochMs`, `note` nullable, `createdAtEpochMs`, `updatedAtEpochMs`.
- `CashTransferEntity`: `id`, `amountIdr`, `fromWallet` (`BALANCE`/`CASH`), waktu transfer, waktu pencatatan. Transfer antar dompet bukan transaksi pemasukan/pengeluaran; jika Discord aktif, transfer baru memiliki antrean notifikasi tersendiri. Transfer yang sudah dicatat sebelum dukungan notifikasi dianggap tidak memiliki notifikasi historis.
- `CategoryEntity`: `id`, `name`, `type` (jenis yang diizinkan, atau `BOTH`), `isActive`, `sortOrder`. Seed: Makanan, Transportasi, Tagihan, Belanja, Gaji, Lainnya. Nama dan jenis kategori persis akan diputuskan saat implementasi; kategori dapat dibatasi per jenis transaksi.
- `NotificationOutboxEntity`: satu baris maksimal per transaksi baru, `transactionId` unik + foreign key, `state` (`PENDING`, `SENDING`, `SENT`, `FAILED`), `workId` nullable/unik, `attemptCount`, `lastAttemptAtEpochMs`, `sentAtEpochMs`, `lastErrorCode` aman (tanpa URL atau isi sensitif). Outbox tidak menyimpan catatan transaksi atau webhook.
- `TransferNotificationOutboxEntity`: antrean dengan status dan retry yang sama untuk satu transfer baru, memakai `transferId` sebagai primary key dan foreign key.
- `AppSettingsEntity` (satu baris): `openingBalanceIdr` (saldo non-tunai awal), `openingCashIdr` (tunai awal), `discordEnabled`, preferensi zona waktu opsional. URL webhook disimpan terpisah secara terenkripsi, bukan sebagai nilai plaintext Room.

Relasi: banyak transaksi ke satu kategori; satu transaksi ke paling banyak satu outbox. Transfer tunai disimpan terpisah. Kategori yang dirujuk tidak dihapus fisik. Foreign key dan indeks tanggal, jenis, kategori dipakai untuk integritas dan filter.

Saldo non-tunai = saldo non-tunai awal + pemasukan non-tunai − pengeluaran non-tunai − transfer keluar non-tunai + transfer masuk non-tunai. Saldo tunai = tunai awal + pemasukan tunai − pengeluaran tunai − transfer keluar tunai + transfer masuk tunai. Total saldo adalah penjumlahan keduanya. Transfer tidak memengaruhi pemasukan/pengeluaran. Saldo dan ringkasan dihitung dari Room; tidak disimpan sebagai angka cache yang bisa menyimpang. Saat migrasi dari satu saldo, saldo lama dan semua transaksi lama ditempatkan pada saldo non-tunai, saldo tunai awal Rp0.

## 4. Arsitektur

- **UI:** Kotlin + Jetpack Compose, Material 3, satu Activity, Navigation Compose; seluruh label dan format tanggal/IDR berbahasa Indonesia.
- **Presentasi:** ViewModel per alur layar, state immutable/Flow; UI hanya merender state dan mengirim aksi.
- **Domain:** use case untuk simpan/edit/hapus, saldo/ringkasan, filter, dan pengantrean notifikasi.
- **Data:** repository, DAO Room, migrasi skema; Room menjadi sumber transaksi utama.
- **Pengiriman:** WorkManager `CoroutineWorker` dengan kendala `NetworkType.CONNECTED`; HTTP client mengirim webhook dari background worker. URL disuntikkan saat worker berjalan dari penyimpanan aman.
- **Keamanan:** simpan secret dengan Android Keystore melalui penyimpanan terenkripsi; redaksi URL dari log, exception, analytics, dan pesan UI. Batasi tampilan URL; jelaskan bahwa siapa pun yang memilikinya dapat mengirim ke channel. Koneksi HTTPS saja. Untuk distribusi publik, evaluasi backend proxy karena secret dalam aplikasi klien pada akhirnya dapat diekstrak.

## 5. Alur transaksi → Discord

1. Validasi input (nominal positif, kategori aktif yang sesuai, tanggal valid; transfer memvalidasi saldo dompet sumber).
2. Dalam satu transaksi database, simpan transaksi; jika ini transaksi **baru**, notifikasi aktif dan webhook tersedia, buat satu baris outbox `PENDING`. Edit tidak membuat/mengubah outbox.
3. Setelah commit, jadwalkan pekerjaan unik berdasarkan ID transaksi (`discord-transaction-{id}`), kebijakan `KEEP`. Kegagalan penjadwalan tidak membatalkan transaksi; sinkronisasi outbox saat aplikasi mulai dapat menjadwalkannya lagi.
4. Worker membaca baris outbox dan URL rahasia, menandai `SENDING`, lalu mengirim embed Discord berisi jenis, jumlah IDR, saldo gabungan terbaru pada saat pembuatan, sumber dana, kategori, waktu, serta catatan opsional sesuai permintaan pengguna. Jangan masukkan informasi rekening atau data lain.
5. Respons sukses → `SENT` dan waktu kirim. Kegagalan sementara/offline → retry dengan backoff eksponensial. Kegagalan permanen (mis. webhook ditolak) → `FAILED` dengan kode aman dan opsi coba lagi setelah pengguna memperbaiki pengaturan.
6. Transfer baru memakai outbox transfer dan pesan embed dengan nominal, sumber, tujuan, saldo total, serta waktu; transfer tidak menyertakan catatan dan tidak mengubah laporan pemasukan/pengeluaran. Jika notifikasi dinonaktifkan/tidak ada webhook saat transaksi atau transfer dibuat, data tetap tersimpan. MVP tidak mengirim notifikasi historis otomatis setelah pengguna mengaktifkan webhook.

## 6. Offline, retry, dan duplikasi

Outbox lokal memastikan transaksi lebih dulu tersimpan. WorkManager dengan jaringan tersedia, exponential backoff, pekerjaan unik per transaksi, dan status outbox menangani restart serta kondisi offline. UI dapat meminta retry manual untuk status gagal; jadwal retry tidak membuat transaksi baru. ID transaksi unik menjaga satu pekerjaan logis per notifikasi.

**Batas teknis penting:** webhook Discord tidak menyediakan kunci idempotensi yang dapat dipakai klien untuk jaminan exactly-once. Jika Discord menerima pesan tetapi respons hilang/timeout, klien tidak dapat mengetahui hasilnya secara pasti. Kebijakan retry otomatis memberi pengiriman setidaknya sekali dan berpotensi duplikat pada kasus ambigu; mencegah seluruh duplikat dengan jaminan mutlak dapat berarti tidak mengulang kasus tersebut dan berisiko notifikasi hilang. Draf ini memilih retry otomatis dan pencegahan duplikat best-effort (unique work + outbox), dengan risiko kasus timeout ambigu perlu disetujui.

## 7. Kriteria penerimaan

- **Tambah pemasukan/pengeluaran:** formulir memvalidasi nominal positif dan data wajib; pengguna memilih sumber Tunai atau Saldo non-tunai; transaksi tersimpan lokal dan segera muncul; saldo dompet dan total dihitung dengan benar.
- **Saldo awal:** instalasi baru mulai dari Rp0. Nilai saldo awal lama yang sudah tersimpan dipertahankan untuk menjaga total saat migrasi, tetapi tidak lagi dapat diubah dari Pengaturan.
- **Tema:** pilihan putih/terang dan hitam/gelap mengubah tampilan seluruh layar dan tetap tersimpan setelah aplikasi ditutup.
- **Transfer antar saldo:** transfer dapat dilakukan dari saldo non-tunai ke tunai dan sebaliknya; transfer hanya berhasil bila saldo sumber cukup; total saldo tidak berubah dan ringkasan pemasukan/pengeluaran tidak bertambah.
- **Riwayat transfer:** transfer muncul di daftar transaksi terbaru dengan arah sumber → tujuan, nominal, dan waktu; transfer tidak bisa diedit/dihapus lewat aksi transaksi biasa dan tidak memiliki kategori pengeluaran.
- **Daftar/filter:** daftar terbaru lebih dahulu; filter jenis, kategori, tanggal dapat digabung dan hasil sesuai data lokal.
- **Edit/hapus:** perubahan tersimpan; hapus meminta konfirmasi; saldo/ringkasan ikut berubah; tidak mengirim Discord untuk edit/hapus.
- **Ringkasan:** angka bulan kalender berjalan dan saldo tiap dompet/total cocok dengan agregat transaksi, transfer, dan saldo awal.
- **Kategori:** kategori awal tersedia; kategori dapat disesuaikan; kategori yang dipakai tidak merusak riwayat.
- **Discord opt-in:** status aktif dan webhook dapat disimpan; pengguna melihat peringatan bahwa URL bersifat rahasia; nonaktif berarti transaksi baru tidak dikirim.
- **Notifikasi baru:** sesudah commit transaksi pemasukan/pengeluaran, satu pekerjaan logis mengirim embed dengan jenis, jumlah, saldo total terbaru, sumber dana, kategori, waktu, dan catatan opsional; saldo awal dan riwayat transaksi tidak dikirim.
- **Notifikasi transfer:** transfer baru saat Discord aktif menghasilkan satu embed berisi jumlah, arah transfer, saldo total, dan waktu; transfer lama tidak dikirim ulang. Status menunggu/terkirim/gagal dan retry tampil di riwayat transfer.
- **Offline/gagal:** transaksi tetap ada; status menunggu/gagal terlihat; worker mencoba kembali ketika jaringan tersedia; pengguna dapat mencoba ulang kegagalan terminal.
- **Status sukses:** status berubah menjadi terkirim hanya setelah respons sukses dari Discord.
- **Privasi:** URL tidak muncul di log; pesan tidak mengandung informasi rekening; catatan dapat terkirim ke channel Discord setelah pengguna meminta penambahan fitur tersebut; data transaksi tetap lokal.
- **Bahasa/format:** seluruh alur MVP berbahasa Indonesia dan nominal ditampilkan dalam format Rupiah.

## 8. Asumsi dan keputusan untuk dikonfirmasi

1. **Persetujuan spesifikasi dan ruang lingkup:** apakah setuju dengan MVP di atas, termasuk menunda anggaran per kategori walau disebut dalam tujuan umum?
2. **Periode ringkasan:** asumsi bulan kalender lokal (tanggal 1 sampai akhir bulan) dan zona waktu perangkat. Setuju?
3. **Saldo pada pesan Discord:** asumsi saldo setelah transaksi, dihitung saat transaksi baru disimpan. Ini dapat berbeda jika transaksi lain ditambahkan sebelum worker mengirim. Apakah itu yang diinginkan?
4. **Retry/duplikasi:** setuju dengan retry otomatis dan kemungkinan duplikat sangat jarang pada timeout ambigu, karena Discord webhook tidak memberi jaminan idempotensi klien?
5. **Notifikasi saat webhook belum diatur/nonaktif:** asumsi tidak ada outbox untuk transaksi tersebut dan tidak ada pengiriman historis ketika webhook kelak diaktifkan. Setuju?
6. **Webhook:** satu URL untuk satu channel privat; pengujian webhook sebelum mengaktifkan notifikasi diinginkan? Pesan uji bukan data transaksi.
7. **Pengelolaan data:** apakah perlu fitur hapus semua data atau ekspor lokal pada MVP? Keduanya belum termasuk.
8. **Perangkat sasaran:** minimum Android/API level dan nama aplikasi/package ID belum ditentukan. Jika tidak ada preferensi, saya akan memilih baseline yang wajar dan kompatibel dengan pustaka saat implementasi.
9. **Kategori:** apakah kategori harus dibatasi per jenis (misalnya Gaji hanya pemasukan), atau semua kategori dapat dipakai untuk kedua jenis? Draf mengusulkan atribut jenis kategori.

Persetujuan: pengguna menyetujui semua asumsi dan keputusan draf pada 15 Oktober 2026. Workspace awalnya tidak memiliki kerangka proyek Android; proyek Kotlin/Compose dibuat dari awal.
