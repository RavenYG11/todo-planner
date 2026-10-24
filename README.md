# Todo Planner — Aplikasi Android (Assignment #83, Gunet)

Aplikasi To Do List Planner **native Android (Kotlin + Jetpack Compose)**, dibangun dari desain FINAL
`../mockups/mockup-varian-1-revisi.html` + `../mockups/catatan-revisi.md` (setelah dibersihkan dari teks
penjelasan di layar Profil — langkah 0 assignment ini).

**Full lokal & full offline:** tanpa server, tanpa cloud, tanpa akun, tanpa analytics, dan manifest
**tidak meminta permission INTERNET sama sekali**. Semua data ada di HP: kegiatan & target di SQLite
(Room), profil & pengaturan di SharedPreferences, foto profil disalin ke penyimpanan internal aplikasi.

## Cara Build

### Opsi A — Android Studio (laptop Windows Rama)
1. Install Android Studio (versi stabil terbaru) — JDK 17 sudah terbundel di dalamnya.
2. Buka folder proyek ini (`todo-planner/app/`) lewat *Open*.
3. Tunggu Gradle Sync selesai (butuh internet sekali untuk unduh dependensi AndroidX).
4. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. APK debug terbit di `app/build/outputs/apk/debug/app-debug.apk`.

### Opsi B — Command line (yang dipakai di VM Muse untuk verifikasi)
```bash
# butuh JDK 17 + Android SDK (platform 34, build-tools 34.0.0)
export JAVA_HOME=<path-jdk17>
export ANDROID_HOME=<path-android-sdk>
# file local.properties berisi: sdk.dir=<path-android-sdk>
./gradlew assembleDebug
```
Di VM, sdkmanager gagal memakai proxy egress (Java `HttpURLConnection.doTunneling` error), jadi paket
SDK (platform-tools, platform android-34, build-tools 34.0.0) dipasang manual: unduh zip resmi dari
`dl.google.com/android/repository/` dengan curl lalu ekstrak ke layout SDK + tulis file `licenses/`
dengan hash lisensi standar. Gradle diberi proxy lewat argumen `-Dhttp(s).proxyHost/Port/User/Password`
(diambil dari env, tidak ditulis ke file proyek).

## Cara Install di HP
1. Salin `app-debug.apk` ke HP (USB/WhatsApp ke diri sendiri/dll).
2. Buka file APK → izinkan "Install dari sumber tidak dikenal" untuk aplikasi penjelajah file bila diminta.
3. Buka **Todo Planner**. Saat pertama dibuka, aplikasi meminta izin notifikasi (Android 13+) — izinkan
   agar reminder berbunyi.

## Arsitektur (dibuat mudah dirawat, satu file satu urusan)

```
app/src/main/java/id/rama/todoplanner/
├── MainActivity.kt            # activity tunggal, setContent
├── TodoApplication.kt         # membuat database + repository (singleton)
├── data/
│   ├── Entities.kt            # tabel Room: ActivityEntity, TargetEntity
│   ├── Daos.kt                # query Room
│   ├── AppDatabase.kt         # Room database (todo_planner.db)
│   ├── AppPrefs.kt            # profil & pengaturan (SharedPreferences)
│   └── Repository.kt          # satu pintu data untuk ViewModel
├── domain/
│   ├── Dates.kt               # format tanggal Indonesia + aturan sapaan jam HP
│   └── Occurrences.kt         # logika pengulangan (harian/mingguan/bulanan/tahunan) per tanggal
├── reminder/
│   ├── ReminderScheduler.kt   # AlarmManager (exact bila diizinkan, fallback setAndAllowWhileIdle) + alarm tes
│   ├── ReminderReceiver.kt    # terima alarm -> mulai bunyi dari kode + tampilkan notifikasi heads-up
│   ├── ReminderChannels.kt    # channel v3 (senyap, khusus tampilan) + bersihkan channel lama (ronde 2)
│   ├── ReminderSoundPlayer.kt # putar nada (MediaPlayer USAGE_ALARM) + getar dari kode (ronde 2)
│   ├── ReminderSoundService.kt# foreground service penjaga putaran nada maks 20 dtk (ronde 2)
│   ├── StopSoundReceiver.kt   # hentikan bunyi/getar (notifikasi dibuang/diketuk) (ronde 2)
│   └── BootReceiver.kt        # jadwalkan ulang semua reminder setelah HP reboot
├── pdf/PdfExporter.kt         # PDF beneran via PdfDocument native + simpan MediaStore Unduhan
├── backup/BackupManager.kt    # export/import seluruh data <-> file JSON
└── ui/
    ├── Nav.kt                 # daftar 14 layar + PETA INDUK TETAP tombol Back
    ├── TodoApp.kt             # akar: bottom nav, handler Back HP, izin notifikasi
    ├── AppViewModel.kt        # state & semua aksi (CRUD, profil, foto, nada, backup)
    ├── theme/Theme.kt         # palet Langit Pagi + Material 3
    ├── components/            # AvatarView, RingProgress, ToneField (ringtone picker Android)
    └── screens/               # satu file per kelompok layar
```

### Keputusan & catatan teknis jujur
- **Pengulangan:** satu baris DB = satu seri. Penyelesaian disimpan per tanggal (`doneDates`), jadi
  mencentang selesai hari ini **tidak** menyelesaikan seri di tanggal lain. "Tunda 1 Hari" menggeser
  tanggal mulai seri +1 hari.
- **Kalender:** dihitung algoritmik dari `java.time` (`YearMonth`/`LocalDate`) — tanpa tabel tahun,
  tanpa batas tahun praktis, kabisat otomatis benar. Hari libur nasional tidak diketahui aplikasi
  (itu keputusan pemerintah per tahun, di luar kalender Gregorian).
- **Nada dering:** tombol *Ganti* membuka **ringtone picker bawaan Android**
  (`RingtoneManager.ACTION_RINGTONE_PICKER`, termasuk opsi Bawaan & Senyap). URI pilihan disimpan.
  Karena di Android suara adalah properti *notification channel*, aplikasi membuat channel per URI
  nada (id channel `reminder2_<hash URI>` sejak ronde 1 — lihat bagian Perbaikan Ronde 1) supaya
  suara selalu mengikuti pilihan terakhir. Nada bisa diatur
  global (layar Reminder) dan per kegiatan (form); Detail menampilkan nada efektif kegiatan.
  **(Ronde 2 menggantikan pendekatan ini: channel v3 sengaja senyap dan bunyi diputar dari kode —
  lihat bab "Perbaikan Ronde 2" di akhir dokumen.)**
- **Reminder setelah reboot:** `BootReceiver` menjadwalkan ulang semua reminder saat `BOOT_COMPLETED`.
  Izin alarm eksak (`SCHEDULE_EXACT_ALARM`) dipakai bila tersedia; bila tidak, fallback
  `setAndAllowWhileIdle` (bisa bergeser beberapa menit oleh Doze Android — perilaku OS, bukan bug).
- **Export PDF:** digambar dengan `PdfDocument` native (header gradasi Langit Pagi, judul periode,
  ringkasan, tabel 22 baris/halaman, footer tanggal cetak + nomor halaman). API 29+: tersimpan di
  `Unduhan/TodoPlanner/` via MediaStore. API 26–28: tersimpan di folder eksternal aplikasi dan tetap
  bisa dibagikan. Tombol Bagikan memakai salinan cache + FileProvider (share intent).
- **Foto profil:** memakai photo picker Android (`PickVisualMedia`) — tanpa permission galeri.
  Sejak ronde 1: orientasi EXIF diterapkan ke piksel saat impor, lalu user mengatur crop
  manual (geser/zoom/putar) sebelum disimpan ke `filesDir/profile_photo.jpg`; ditampilkan
  lingkaran `ContentScale.Crop` di semua avatar. Hapus Foto mengembalikan avatar ke inisial/emoji.
- **Tombol Back HP:** peta induk tetap di `ui/Nav.kt` (`parentOf()`), dijalankan satu `BackHandler`
  di akar (di atas `OnBackPressedDispatcher`). Bukan undo riwayat sesi.
- **UI bersih:** sesuai perintah Rama, UI tidak memuat paragraf penjelasan/meta — label singkat saja.
  Semua penjelasan teknis tinggal di README ini.
- **Ukuran APK:** lihat bagian Hasil Compile di bawah (diisi dari build nyata, bukan perkiraan).

## Daily Reminder Fitur vs Mockup Final (14 layar)
| # | Layar/Fitur mockup | Status di aplikasi |
|---|---|---|
| 1 | Beranda: sapaan jam HP (04–11 pagi, 11–15 siang, 15–19 sore, 19–04 malam), "Halo, [nama]!", tanggal & jam HP, ring progres, kegiatan berikutnya, Schedule Warning, header TANPA pintu Profil | ✅ |
| 2 | Template Harian: time-block jam 04–22 (slot kosong bisa diketuk untuk isi) + Daily Reminder tanpa jam | ✅ |
| 3 | Template Mingguan: 7 hari Senin–Minggu, tambah per hari | ✅ |
| 4 | Template Bulanan: grid bulan + daftar tanggal terpilih | ✅ |
| 5 | Template Tahunan: 4 kuartal + target + meter progres, tambah/edit/hapus target, tombol +/− progres | ✅ |
| 6 | Kalender: mode Bulan & Tahun (java.time), titik warna kategori, penanda hari ini, tap tanggal → daftar + tambah | ✅ |
| 7 | Tambah/Edit: judul, tanggal (DatePicker), jam mulai–selesai (TimePicker), template tujuan, ulangi, reminder, nada dering, prioritas, kategori, catatan | ✅ |
| 8 | Reminder global: izin notifikasi Android 13+, nada global via ringtone picker, daftar Schedule Warning | ✅ |
| 9 | Detail: isi lengkap + nada efektif + selesai/tunda 1 hari/edit/hapus (konfirmasi) | ✅ |
| 10 | Progress/Statistik: harian, 7 hari, bulanan, streak, per kategori | ✅ |
| 11 | Pengaturan: tema (Terang/Gelap/Sistem), notifikasi, nada, backup JSON export/import, tentang | ✅ |
| 12 | Export PDF: pilih periode + kategori → Preview A4 → Simpan ke Unduhan / Bagikan | ✅ |
| 13 | Profil & Personalisasi (UI bersih): nama live, foto galeri, warna avatar, avatar inisial/emoji | ✅ |
| 14 | Tombol Back HP peta induk tetap + form kotor konfirmasi + Beranda tekan 2× keluar | ✅ |

## Perbaikan Ronde 1 — Uji HP Rama (Assignment #85, 24 Okt 2026)

Tiga masalah dari uji perdana di HP Huawei Rama (bukti: `../feedback/uji1-*`) diperbaiki:

1. **Form rusak + jauh dari mockup.** Akar masalah: baris chip memakai `Row` biasa — chip
   terakhir terhimpit jadi kolom sempit (teks per huruf: 'Tahunan', '1 jam sebelum',
   'Kesehatan'), dan baris 3 tombol tanggal/jam saling desak (tombol 'Selesai' jadi
   lingkaran raksasa). Fix: `ChipRow` → `FlowRow` + komponen `PastelChip` (lebar mengikuti
   konten, membungkus rapi, warna pastel per grup/kategori, label 1 baris); tombol
   tanggal/jam → `OutlinedButton` proporsional di `FlowRow`; pola yang sama diterapkan di
   layar Export & Kalender. Restyle ke mockup "Langit Pagi": `DawnHeader` (gradasi fajar
   terang #CDEBFF→#FFE7CF + teks tinta) di Form & Profil, header Beranda gradasi fajar
   terang + teks tinta, `GradientButton` (gradasi peach→coral / biru→mint) untuk tombol
   utama (Simpan, Export PDF, Tandai Selesai, Simpan Profil), container pastel di tema.
2. **Foto profil miring.** Akar masalah: byte JPEG disalin mentah dari galeri; penampil
   bitmap mengabaikan tag orientasi EXIF. Fix: `PhotoStore` men-decode + menerapkan EXIF
   ke piksel (rotate/flip, maks 1024px) sebelum simpan (`android.media.ExifInterface`,
   tanpa dependensi baru). Pengaturan manual PENUH: layar `PhotoCropper` (geser + pinch
   zoom + putar 90°, guide lingkaran; hasil crop persegi yang disimpan) setiap memilih
   foto, plus tombol "Putar 90°" untuk foto yang sudah tersimpan di Profil. Cache avatar
   menyertakan `lastModified` file agar foto hasil putar/crop langsung termuat ulang.
   CATATAN: foto lama yang sudah telanjur tersimpan miring tetap miring sampai dipilih
   ulang lewat cropper atau diputar manual — file lamanya memang sudah tanpa EXIF benar.
3. **Reminder muncul tapi senyap & tidak getar.** Akar masalah: (a) `enableVibration`
   hanya dipanggil di cabang nada senyap — cabang nada normal tidak pernah mengaktifkan
   getar; (b) suara hanya hidup di channel, dan channel Android **immutable**: channel
   skema lama yang tercipta dengan konfigurasi salah mengunci senyap selamanya;
   (c) builder tidak memasang `setVibrate`. Fix: channel BARU ber-prefix `reminder2_`
   (per kunci nada), `IMPORTANCE_HIGH`, suara = URI nada pilihan kegiatan itu (fallback
   nada notifikasi bawaan sistem bila kosong), `enableVibration(true)` + pola eksplisit
   `0,250,150,250` di SEMUA cabang, channel lama `reminder_*` dibersihkan, builder
   memasang `setVibrate` + `PRIORITY_HIGH`. Permission `VIBRATE` sudah ada sejak v1.
   **Penyempurnaan Rama (ronde yang sama):** bunyi + getar berlaku di SEMUA prioritas
   kegiatan (prioritas tidak menggerbangi notifikasi); opsi "Tepat waktu" DIHAPUS —
   pilihan kini Tanpa / 15 mnt / 1 jam / 1 hari sebelum, dan setiap reminder aktif
   mendapat **2 alarm**: pengingat awal **dan** pas jam mulai (request code & id
   notifikasi terpisah per jenis; "Tanpa" = tidak ada alarm). Data lama bernilai 0
   tetap terbaca sebagai "Pas jam mulai" di Detail dan dijadwalkan sebagai alarm
   pas-mulai saja. Alarm ulangan berikutnya dijadwalkan ulang saat simpan, reboot
   (`BootReceiver`), dan buka aplikasi.
   Verifikasi ronde ini sebatas compile + signature + isi dex (string channel/label
   baru ada di APK) — bunyi/getar/crop/form terbukti pada uji HP Rama berikutnya.

File diubah/ditambah ronde 1: `ui/screens/FormScreen.kt`, `ui/screens/HomeScreen.kt`,
`ui/screens/ProfileScreen.kt`, `ui/screens/DetailScreen.kt`, `ui/screens/ExportScreens.kt`,
`ui/screens/CalendarScreen.kt`, `ui/components/Common.kt` (PastelChip/GradientButton/
DawnHeader), `ui/components/PhotoStore.kt` (baru), `ui/components/PhotoCropper.kt` (baru),
`ui/AppViewModel.kt`, `ui/theme/Theme.kt`, `reminder/ReminderReceiver.kt`,
`reminder/ReminderScheduler.kt`.

## Hasil Compile
- **Status: BERHASIL — `BUILD SUCCESSFUL` (Gradle 8.7, AGP 8.5.2, Kotlin 2.0.20), 24 Okt 2026 (ronde 1).**
- **APK debug:** `app/build/outputs/apk/debug/app-debug.apk` — **16.835.201 byte (±16,1 MB)**,
  sha256 `3ac4fec360f6db9fb6d0aada2d8121eb69396eb269018b7eac0b4d42405bab57`.
  Salinan praktis juga ada di `../TodoPlanner-debug.apk` (isi identik, hash sama).
  (Build ronde 1 pertama, 16.835.209 byte / sha256 `9fe57ab2…`, sempat terkirim lebih dulu;
  APK final di atas menyusul dengan poles tambahan: chip Export, header Beranda terang,
  rasio geser cropper, dan state nonaktif GradientButton.)
- **APK ini sudah digantikan oleh hasil Perbaikan Ronde 1 (#85) — lihat bab terakhir dokumen ini.**
- **Terverifikasi pada APK:** package `id.rama.todoplanner`, versionName `1.0`, minSdk 26, targetSdk 34;
  permission hanya POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, VIBRATE
  (**TANPA permission INTERNET**); signature debug valid (`apksigner verify` lolos).
- **Gradle Wrapper** (`gradlew`, `gradle/wrapper/`) sudah dibuat — build berikutnya cukup `./gradlew assembleDebug`.

### Perjalanan provisioning di VM (dicatat jujur — semua jalur yang dicoba)
VM awalnya tanpa Java/SDK/Gradle. Yang dilakukan, berurutan:
1. JDK 17 (Temurin) + Android command-line tools diunduh dengan curl → sdkmanager **gagal**:
   proxy egress VM menolak tunneling Java (`HttpURLConnection.doTunneling` exception).
2. Paket SDK dipasang **manual**: zip resmi platform-tools, platform android-34, build-tools 34.0.0
   dari `dl.google.com/android/repository/` (URL persis dari `repository2-1.xml`) diekstrak ke layout SDK
   + file `licenses/` diisi hash lisensi standar → aapt2/adb/android.jar terverifikasi ada.
3. Gradle daemon awalnya **gagal total** ("Could not receive a message from the daemon"): penyebabnya
   soket IPv6-mapped Java (`::ffff:127.0.0.1`) dicegat interceptor TCP VM (terbukti dengan uji
   strace + client Java/Python). Solusi: `-Djava.net.preferIPv4Stack=true` di client & daemon.
4. Unduhan dependensi Gradle lewat proxy **gagal acak** (MITM TLS + Apache HttpClient Gradle sering
   kena "Connection reset"): CA proxy (`/run/hatch/cell-anchors/hatch-egress-ca.pem`) dimasukkan ke
   truststore Java khusus — membantu sebagian, tapi reset tetap sering.
5. Jalur yang akhirnya berhasil: **local Maven repo offline** — seluruh dependensi (700+ file artefak
   publik dari Google Maven/Maven Central/Gradle Plugin Portal) diunduh dengan **curl** ke folder
   `local-repo`, lalu Gradle build `--offline` (settings.gradle.kts sudah mengenal repo lokal ini
   secara kondisional; di laptop user folder itu tidak ada sehingga repo online standar yang dipakai).
   File varian Gradle Module Metadata (mis. `kotlin-gradle-plugin-2.0.20-gradle85.jar`,
   `aapt2-...-linux.jar`) ikut dilengkapi. Satu-satunya error source nyata selama kompilasi:
   `FormScreen.kt` memanggil konstruktor `ActivityEntity` tanpa `dateEpochDay` — sudah diperbaiki.
6. Build final dijalankan pada salinan proyek terisolasi (menghindari bentrok dengan proses build
   paralel lain di VM yang sama); **source-nya identik byte-per-byte** dengan folder ini — satu-satunya
   perbedaan adalah 2 baris tambahan khusus VM di `gradle.properties` salinan
   (`kotlin.compiler.execution.strategy=in-process` + `kotlin.daemon.jvmargs` IPv4) karena daemon
   Kotlin juga terkena intersepsi soket VM. APK hasil build disalin ke path standar di folder ini
   (hash sha256 di atas membuktikan isinya sama persis).
7. Cache dependensi offline (304 MB) tertinggal di VM (`~/toolchain/build-copy/local-repo`) bila
   perlu build ulang offline; folder `local-repo/` di proyek ini aman dihapus (sudah masuk .gitignore).

## Perbaikan Ronde 1 — Uji HP (#85, 24 Okt 2026)

APK pertama (#83) diuji langsung oleh Rama di HP Huawei-nya: aplikasi jalan, tetapi
ada 3 masalah + 2 penyempurnaan perilaku atas permintaannya (00:44 WIB). Semua diperbaiki
di ronde ini; tanpa QA terpisah — APK hasil langsung diserahkan ke Rama.

### 1. Form mematah + restyle ke mockup "Langit Pagi"
- **Akar masalah:** baris chip di Form Tambah/Edit Kegiatan memakai `Row` biasa.
  Bila total lebar chip melebihi layar, chip terakhir (Tahunan / 1 jam sebelum /
  Kesehatan) terhimpit ke sisa ruang sempit → teks menumpuk per huruf jadi kolom
  vertikal; baris 3 tombol Tanggal/Mulai/Selesai juga saling desak sampai tombol
  "Selesai" jadi lingkaran raksasa.
- **Perbaikan:** `ChipRow` ditulis ulang memakai `FlowRow` + komponen `PastelChip`
  baru (lebar mengikuti konten, membungkus rapi ke baris berikut). Baris tombol
  tanggal/jam juga jadi `FlowRow` berisi `OutlinedButton` proporsional.
- **Restyle:** kepala layar Form & Profil memakai gradasi fajar mockup
  (`DawnHeader`: biru langit → krem peach); chip terpilih = pastel sesuai aksen
  per bagian (Template biru langit, Ulangi lavender, Reminder amber, Prioritas
  coral, Kategori warna kategorinya) dengan label tinta; tombol utama
  (Simpan Kegiatan, Simpan Profil, Tandai Selesai, Export PDF, Pilih Foto)
  memakai `GradientButton` gradasi coral→peach / biru langit→mint; skema warna
  Material menambah container pastel supaya chip bawaan di layar lain ikut lembut.
  File diubah: `ui/screens/FormScreen.kt`, `ui/screens/ProfileScreen.kt`,
  `ui/screens/DetailScreen.kt`, `ui/screens/HomeScreen.kt`,
  `ui/components/Common.kt`, `ui/theme/Theme.kt`.

### 2. Foto profil miring → EXIF otomatis + crop manual penuh
- **Akar masalah:** foto dari galeri disalin sebagai byte JPEG mentah; penampil
  bitmap tidak menerapkan tag orientasi EXIF → foto tampil berputar/miring.
- **Perbaikan:** util baru `ui/components/PhotoStore.kt` (framework
  `android.media.ExifInterface` — tanpa dependensi baru): saat impor, orientasi
  EXIF di-bake ke piksel bitmap, diperkecil maks 1024px, disimpan sebagai JPEG.
- **Crop manual penuh** (`ui/components/PhotoCropper.kt`, permintaan Rama 00:44):
  setelah memilih foto, muncul layar crop — geser (drag), zoom (cubit/pinch),
  dan putar 90° di dalam bingkai persegi berguide lingkaran; hasil crop itulah
  yang disimpan. Di Profil juga ada tombol **Putar 90°** untuk foto yang sudah
  tersimpan (`AppViewModel.rotatePhoto`).
- **Catatan:** foto lama yang sudah tersimpan miring dari versi sebelumnya tetap
  miring sampai user memilih ulang foto (masuk crop otomatis) atau menekan
  tombol Putar 90° di Profil.

### 3. Reminder senyap & tidak getar → kanal baru + perilaku 2 alarm
- **Akar masalah:** (a) di `ReminderReceiver` lama, `enableVibration(true)` hanya
  dipanggil di cabang nada senyap — cabang nada pilihan & bawaan tidak pernah
  mengaktifkan getar dan tidak ada pola getar; (b) channel lama `reminder_*`
  bersifat immutable — sekali tercipta dengan konfigurasi salah di HP user,
  suara/getarnya terkunci dan tidak bisa dikoreksi aplikasi.
- **Perbaikan channel:** ID channel BARU `reminder2_<kunci nada>` (IMPORTANCE_HIGH),
  suara dari URI nada pilihan kegiatan (fallback nada notifikasi bawaan HP),
  `enableVibration(true)` + pola getar eksplisit `0,250,150,250` untuk semua
  cabang (termasuk mode senyap = getar saja); channel skema lama `reminder_*`
  dibersihkan saat receiver berjalan. Suara + getar berlaku untuk SEMUA
  prioritas kegiatan — prioritas tidak menggerbangi notifikasi.
- **Perubahan perilaku (permintaan Rama 00:44):** opsi reminder "Tepat waktu"
  DIHAPUS dari form. Pilihan sekarang: Tanpa / 15 menit sebelum / 1 jam sebelum /
  1 hari sebelum. Setiap kegiatan ber-reminder kini mendapat **2 alarm**:
  pengingat awal (jam mulai − offset; isi notifikasi "⏰ 15 menit lagi: [judul]")
  dan alarm pas jam mulai ("Mulai sekarang: [judul]"). "Tanpa" = tidak ada
  notifikasi sama sekali. Nada pilihan kegiatan dipakai di kedua notifikasi.
  Alarm tunggal skema lama ikut dibatalkan saat penjadwalan ulang agar tidak
  ada bunyi ganda dari versi sebelumnya. Kegiatan lama bernilai 0 ("Tepat
  waktu") diperlakukan sebagai "Pas jam mulai" dan di form tidak menampilkan
  chip terpilih sampai user memilih ulang.
- File diubah: `reminder/ReminderReceiver.kt`, `reminder/ReminderScheduler.kt`,
  `ui/screens/FormScreen.kt` (opsi), `ui/screens/DetailScreen.kt` (label).

### Hasil build ronde 1
- **Status: BERHASIL — `BUILD SUCCESSFUL` (2m 29s, pola offline local-repo yang sama).**
- **APK debug baru:** `app/build/outputs/apk/debug/app-debug.apk` — **16.835.209 byte**,
  sha256 `9fe57ab2689d39c695579c6eafcf27fb3a363158e5b684b8e947d7b615427bad`;
  salinan praktis `../TodoPlanner-debug.apk` sudah ditimpa (hash sama).
- Terverifikasi: signature v2 valid (`apksigner verify`), package
  `id.rama.todoplanner` v1.0, permission sama seperti sebelumnya (TANPA INTERNET).
- **Catatan jujur:** build debug; perbaikan runtime (bunyi/getar reminder, crop
  foto, tampilan form) baru terbukti setelah Rama meng-install APK ini dan
  mengujinya di HP-nya — verifikasi di VM sebatas compile + signature + badging.

## Perbaikan Ronde 2 — Uji HP (#86, 24 Okt 2026)

Uji ronde 1 oleh Rama (bukti: `../feedback/uji2-*`): form AMAN, foto profil BERES,
tampilan notifikasi bagus — tetapi DUA masalah tersisa, diperbaiki di ronde ini.
Tanpa QA terpisah (perintah Rama) — APK hasil langsung diserahkan ke manager.

### A. Suara + getar reminder MASIH tidak ada → bunyi diputar DARI KODE

- **Akar masalah:** ronde 1 menggantungkan bunyi SEPENUHNYA ke notification
  channel (`reminder2_*`, IMPORTANCE_HIGH + `setSound(URI)` + pola getar).
  Di Huawei/EMUI pendekatan channel-only terbukti tidak cukup: notifikasi
  tampil bagus tetapi senyap & tidak getar — suara/getar channel berada di
  luar kendali aplikasi (klasifikasi & pengaturan channel EMUI, channel
  immutable). Selama bunyi hanya "dipesan" ke channel, tidak ada jaminan
  ia benar-benar diputar.
- **Pendekatan baru (tidak mengandalkan channel untuk bunyi sama sekali):**
  1. **Putar nada dari kode** — saat alarm berbunyi, `ReminderReceiver`
     memulai `ReminderSoundService` (foreground service ringan; konteks
     alarm eksak adalah konteks yang diizinkan Android 12+ memulainya)
     yang memutar nada kegiatan itu lewat `ReminderSoundPlayer`
     (MediaPlayer, `AudioAttributes USAGE_ALARM` +
     `CONTENT_TYPE_SONIFICATION` — jalur audio jam alarm, bukan jalur
     notifikasi). Bila OEM menolak start service dari latar, receiver
     jatuh ke pemutaran langsung di receiver. Nada di-loop maks **20 detik**
     (partial WakeLock ber-timeout), berhenti saat notifikasi diketuk
     (MainActivity), dibuang (deleteIntent), atau tombol aksi
     **"Matikan Bunyi"** ditekan — ketiganya lewat `StopSoundReceiver`.
  2. **Getar dari kode** — `Vibrator`/`VibratorManager` dengan pola
     eksplisit `0,300,200,300,200,600` (di-loop sampai berhenti), untuk
     semua prioritas & semua mode; mode nada "Senyap" pilihan user = getar saja.
  3. **Verifikasi + fallback nada** — URI nada pilihan dibuka stream-nya
     dulu; gagal dibaca → nada ALARM bawaan → NOTIFIKASI bawaan → DERING
     bawaan; bila MediaPlayer gagal di URI terverifikasi, dicoba sekali lagi
     nada alarm bawaan. Prinsip: tidak ada jalur senyap.
  4. **Channel v3** — `ReminderChannels` membuat channel BARU `reminder3_*`
     IMPORTANCE_HIGH yang SENGAJA senyap & tanpa getar (bunyi/getar 100%
     dari kode; menghindari bunyi ganda), dan menghapus channel lama
     `reminder_*` & `reminder2_*`.
  5. **Verifikasi mandiri untuk Rama** — tombol **"Tes Reminder 10 Detik"**
     (alarm tes +10 dtk dengan nada global) dan **"Buka Pengaturan
     Notifikasi"** (deep link `ACTION_CHANNEL_NOTIFICATION_SETTINGS` ke
     channel aktif) di layar Reminder DAN Pengaturan; preview nada
     (▶ Contoh / ⏹ Stop, maks 6 dtk) di semua field nada (`ToneField`).
  6. **Kegiatan LAMA ikut berbunyi** — audit jalur: alarm dijadwalkan ulang
     saat aplikasi dibuka (`TodoApplication.onCreate` +
     `AppViewModel.rescheduleAllReminders` dari `TodoApp`) dan setelah boot
     (`BootReceiver` kini juga memastikan channel v3 + `rescheduleAll`).
  7. **Temuan audit tambahan:** `ReminderScheduler` ronde 1 menyimpan
     `EXTRA_ID` sebagai Int sedangkan receiver membacanya sebagai Long —
     ID notifikasi selalu terbaca 0 (notifikasi saling menimpa). Kini
     konsisten Long end-to-end.
- **Permission baru (tetap TANPA INTERNET):** `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` (service dideklarasikan
  `foregroundServiceType="mediaPlayback"`).
- File baru: `reminder/ReminderChannels.kt`, `reminder/ReminderSoundPlayer.kt`,
  `reminder/ReminderSoundService.kt`, `reminder/StopSoundReceiver.kt`.
  File diubah: `reminder/ReminderReceiver.kt`, `reminder/ReminderScheduler.kt`
  (+`scheduleTest`/`rescheduleAll`), `reminder/BootReceiver.kt`,
  `AndroidManifest.xml`, `TodoApplication.kt`, `MainActivity.kt`,
  `ui/components/TonePicker.kt`, `ui/AppViewModel.kt`.

### B. Art/desain jauh dari mockup → design-fidelity pass per layar

Acuan PERSIS `../mockups/mockup-varian-1-revisi.html` (bandingkan
`uji2-mockup-beranda.jpg` vs `uji2-beranda-app.jpg` & `uji2-statistik-app.jpg`):

- **Beranda:** header gradasi fajar kini memuat **ilustrasi pemandangan
  fajar** (Canvas: matahari terbit ber-halo + awan + 2 lapis bukit mint,
  meniru SVG mockup) lewat `DawnIllustration`; sapaan + **emoji fase hari**;
  **tagline dinamis per fase** (`Dates.phaseTagline`, versi pagi = kalimat
  mockup persis); **chip jam pill** putih transparan kanan atas (HH.mm + zona).
- **Kartu progres:** ring gradasi **tebal** (96dp, stroke 10, ujung bulat),
  **% besar + caption "hari ini" di tengah ring**, **judul dinamis mengikuti
  %** ("Hari ini hampir selesai" ≥50%, dst), sub-teks sisa kegiatan, **pill
  "🔥 Streak N hari"** (streak dihitung nyata) + pill aksen fase
  (pengganti chip "Cuaca cerah" mockup — aplikasi offline tidak punya data
  cuaca, jadi chip-nya aksen fase hari, bukan klaim cuaca).
- **Kegiatan Berikutnya & Schedule Warning = kartu TERPISAH** berikon judul
  (⏰/🔔); item kegiatan: **garis aksen warna kategori di kiri + lingkaran
  centang kosong + judul tebal + sub-info jam–kategori–prioritas**; reminder:
  bullet "• HH.mm — judul" (maks 3) + link **"Lihat semua reminder →"**.
  Ditambah grid **"Mulai dari Template"** 2×2 seperti mockup.
- **Komponen bersama:** `SoftCard` (kartu PUTIH lembut radius 18dp +
  bayangan halus — akar masalah kartu abu polos adalah warna bawaan
  Material `surfaceVariant`), `GradientBar`, `PastelPill`, `PeakIllustration`.
  `ActivityRow` ditulis ulang gaya mockup (catbar + lingkaran centang) —
  berlaku di semua daftar.
- **Statistik:** bukan abu polos lagi — kartu puncak berilustrasi gunung
  (Canvas: pegunungan + matahari + bendera) berisi capaian bulan nyata,
  grid 2×2 mini (% besar + bar gradasi per periode: biru-peach, mint-hijau,
  lavender), kartu Streak dengan ring mini, dan kartu per-kategori
  (titik warna + % + bar warna kategori) — semua dari data nyata.
- **Bottom nav:** ikon bergaya mockup (🏠, 🌤️ matahari/awan untuk Harian,
  🗓️ kalender, ☰) dengan **pill pastel penanda aktif + label biru tebal**;
  tombol **+ tengah 54dp berbingkai putih + glow/shadow coral** (menonjol).
- **Bahasa sama ke layar lain:** latar pastel `#F4F9FB` + `SoftCard` putih
  diterapkan juga di Reminder, Statistik, Pengaturan & Menu (fungsi tidak
  berubah); Form/Template/Kalender/Detail/Export tetap memakai komponen
  ronde 1 (DawnHeader/PastelChip/GradientButton) yang sudah satu bahasa.

File diubah: `ui/screens/HomeScreen.kt` (tulis ulang), `ui/screens/OtherScreens.kt`
(Statistik tulis ulang + tombol tes), `ui/screens/ActivityRow.kt`,
`ui/components/Common.kt`, `ui/TodoApp.kt` (bottom nav), `domain/Dates.kt`
(helper fase). Catatan proses: dua draf implementasi sempat terbentuk
paralel di folder kerja pada ronde ini; yang dipertahankan adalah SATU
jalur terpadu di atas (draf duplikat `AlarmSoundPlayer`/`ReminderAlertService`
dihapus sebelum build, helper ViewModel dari draf itu diintegrasikan).

### Hasil build ronde 2
- **Status: BERHASIL — `BUILD SUCCESSFUL` (Gradle 8.7 offline, 1m 14s).**
  Source di folder ini identik byte-per-byte dengan source yang ter-build
  (diverifikasi `diff -rq`).
- **APK debug baru:** `app/build/outputs/apk/debug/app-debug.apk` —
  **16.884.271 byte (±16,1 MB)**, sha256
  `e0f3fb6be1512fb48c679a5a7ac32969e526ceb6c995973924b35c6b5d5b85a2`;
  salinan praktis `../TodoPlanner-debug.apk` sudah ditimpa (hash sama).
- Terverifikasi pada APK: signature debug valid (`apksigner verify` lolos);
  package `id.rama.todoplanner` v1.0, minSdk 26, targetSdk 34; permission =
  sebelumnya + FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK,
  WAKE_LOCK (**tetap TANPA INTERNET**); manifest memuat service
  `ReminderSoundService` + receiver `StopSoundReceiver`; string dex memuat
  `reminder3_`, "Tes Reminder 10 Detik", "Matikan Bunyi".
- **Catatan jujur:** build debug; verifikasi di VM sebatas compile +
  signature + badging + isi dex. Bunyi/getar di EMUI dan fidelity desain
  tetap terbukti hanya lewat uji HP Rama berikutnya — gunakan tombol
  "Tes Reminder 10 Detik" (kunci layar dulu) untuk verifikasi bunyi mandiri.


## Perbaikan Ronde 3 — Uji HP (#87, 24 Okt 2026)

Putusan uji ronde 2: "Udah bagus tapi" + 6 catatan. Tanpa QA (perintah Rama) —
APK hasil ronde ini langsung dikirim apa adanya. Yang TIDAK diubah: bunyi+getar
dari kode (ReminderSoundService/Player), perilaku 2 alarm, EXIF+crop foto,
form FlowRow, struktur Beranda ronde 2, tanpa permission INTERNET.

### 1. Header Beranda: varian 4 fase hari
Dulu ilustrasi selalu fajar walau ucapan sudah "Selamat malam". Sekarang
`Dates.phaseOf(hour)` (batas sama persis dengan sapaan) memilih varian
`PhaseScenery` di header:
- PAGI 04.00–10.59: fajar (persis ronde 2) — gradasi biru langit → krem peach.
- SIANG 11.00–14.59: gradasi biru terang, matahari tinggi kecil, awan lebih
  banyak, bukit hijau terang.
- SORE 15.00–18.59: gradasi sunset oranye → pink, matahari besar rendah di
  cakrawala, awan kemerahan, bukit lebih gelap.
- MALAM 19.00–03.59: gradasi indigo → navy → ungu gelap, bulan sabit (Path
  EvenOdd) + 12 bintang, siluet bukit gelap, teks putih/sub biru pucat kontras,
  chip jam berlatar putih transparan.
Semua vektor Canvas — tanpa aset gambar.

### 2. Design-fidelity semua menu vs mockup
Audit layar-per-layar terhadap 14 layar mockup final:
- Harian: time-block kini SATU SoftCard — label jam tebal + titik warna
  kategori + judul + pill kategori pastel; slot kosong = titik abu + teks
  lembut "Slot kosong — belum ada kegiatan. Isi kegiatan →"; legenda
  "Titik warna = kategori:"; Daily Reminder jadi kartu terpisah
  "✅ Daily Reminder Hari Ini"; tombol bawah GradientButton "+ Isi Kegiatan di
  Jam Kosong" & "📄 Export PDF Hari Ini" (dulu Material biru polos).
- Mingguan: kartu hari SoftCard dengan pita gradasi berbeda per hari +
  pill "Hari ini" + tombol tambah lingkaran pastel; slot kosong lembut
  "+ Isi kegiatan"; tombol bawah GradientButton Export PDF.
- Bulanan: grid dalam SoftCard; tanggal terpilih = kotak rounded GRADASI
  fajar (peach→coral→biru langit) dengan teks putih & titik kategori putih;
  hari ini = pastel biru + berbingkai biru; state kosong tanggal terpilih
  berilustrasi (DawnIllustration) + ajakan mengisi.
- Tahunan: kartu "Target Besar {tahun}" (total target tercapai +
  GradientBar); kartu kuartal SoftCard + pita gradasi per kuartal + pill
  "▶ Sekarang" + GradientBar per kuartal & per target
  (LinearProgressIndicator Material hilang dari app).
- Kalender: grid dalam SoftCard; state kosong tanggal terpilih berilustrasi
  (awan/matahari/bukit) + ajakan isi kegiatan; kartu Year View SoftCard.
- Reminder/Pengaturan: tombol Tes & Izinkan Notifikasi = GradientButton.
- Profil: kartu Material → SoftCard radius 18.
- Export: pilihan periode = ubin 2×2 mockup (pita gradasi + ikon + rentang,
  terpilih berbingkai oranye).
Bahasa visual disatukan ulang: SoftCard radius 18, latar pastel #F4F9FB,
bottom nav ronde 2 tidak berubah.

### 3–4. Detail: Tandai Selesai & Tunda 1 Hari → langsung ke Beranda
Kedua tombol kini memakai varian ViewModel berkembalian (callback dipanggil
SETELAH simpan Room + penjadwalan ulang alarm berhasil), lalu TodoApp
menavigasi ke `Screen.Home` (Detail ter-pop, bottom nav Beranda aktif).
Aturan Back HP (peta induk tetap) tidak berubah.

### 5. Reset nada bawaan HP — logika ToneResolver
Satu resolver: `reminder/ToneResolver.kt` (`effectiveToneUri` /
`effectiveToneLabel`). Semua jalur (penjadwalan alarm, tes, preview, Detail)
lewat sini.
- GLOBAL (`AppPrefs.globalRingtoneUri`): `null` = Bawaan HP (default);
  `"silent"` = senyap/getar; URI lain = nada kustom. TonePicker global punya
  opsi TERATAS "🎵 Bawaan HP (default)": memilihnya menghapus URI kustom
  (menyimpan null + nama "Bawaan HP").
- PER KEGIATAN (`ActivityEntity.ringtoneUri`): `null` = Ikuti global;
  sentinel `"default"` (`AppPrefs.RINGTONE_DEFAULT`) = override EKSPLISIT
  Bawaan HP — menang atas global kustom; `"silent"` = senyap; URI lain =
  kustom. Form menyediakan 3 mode: Ikuti global / Bawaan HP (default) /
  Atur Sendiri.
- Hasil resolver `null` berarti default sistem: ReminderSoundPlayer
  menerjemahkannya ke rantai bawaan berjenjang — nada ALARM bawaan →
  NOTIFIKASI bawaan → DERING bawaan. Preview ToneField untuk default juga
  memutar jalur yang sama.

### 6. PDF disamakan dengan mockup Preview PDF
`PdfExporter` ditulis ulang (A4 portrait):
- Header band gradasi fajar selebar halaman + art vektor (matahari gradasi
  + halo, awan putih, bukit dua lapis), "Todo Planner" + "Laporan kegiatan
  — {nama}" di atas band; judul "Laporan {Periode} — {Bulan Tahun}",
  subjudul "{nama} • {rentang periode} • {filter kategori}".
- Kartu Ringkasan Progress faktual: total, selesai + %, belum selesai, dan
  (bila ada datanya) kategori paling banyak selesai / paling banyak belum
  selesai — semua diturunkan dari data asli user.
- Tabel Kegiatan/Tanggal/Kategori/Status: kategori = pill pastel rounded
  rect + teks berwarna gelap kategorinya; status "✓ Selesai" hijau /
  "Mendatang". (Di layar Preview app, kegiatan lampau yang belum selesai
  dilabeli "Belum selesai".)
- Catatan periode HANYA dari fakta data di atas — tidak ada narasi
  karangan seperti contoh mockup.
- Footer tiap halaman: kiri "Dicetak {d MMMM yyyy, HH.mm} • Todo Planner
  (full lokal & offline)", kanan "Halaman X dari Y".
- Paginasi dari simulasi tata letak: tinggi tiap baris dihitung dulu
  (judul membungkus maks 2 baris + elipsis), baris dialihkan ke halaman
  berikutnya sebelum melewati footer — tidak ada teks terpotong.
Layar Preview di app meniru tata letak yang sama (band + art + ringkasan +
tabel pill + footer) dan tombolnya kini "⬇️ Simpan PDF" gradasi, "📤 Bagikan
PDF…", "‹ Ubah Periode / Kategori".

### Hasil build ronde 3
- **Status: BERHASIL — `BUILD SUCCESSFUL` (Gradle 8.7 offline, local-repo;
  build final 4m, hanya warning deprecation lama).** Sempat 1 error
  integrasi (`clip` belum diimpor di CalendarScreen) — diperbaiki lalu
  build ulang lulus. Source di folder ini identik byte-per-byte dengan
  source yang ter-build (diverifikasi `diff -rq` terhadap salinan build).
- **APK debug baru:** `app/build/outputs/apk/debug/app-debug.apk` —
  **16.936.408 byte (±16,2 MB)**, sha256
  `5cb0f90d66b536a5da4116a8a8291a79d8403b30591b2441039ed785f0b70d81`;
  salinan praktis `../TodoPlanner-debug.apk` sudah ditimpa dari APK ini
  (hash sama, diverifikasi ulang setelah build final: BUILD SUCCESSFUL
  rc=0, integritas zip OK, 157 entri). Catatan: hash berbeda dari draf
  build paralel sebelumnya karena build debug tidak byte-reproducible
  (timestamp zip) — ukuran & isi kode identik.
- Terverifikasi pada APK: signature debug valid (`apksigner verify`
  lolos); package `id.rama.todoplanner` v1.0, minSdk 26, targetSdk 34;
  permission TANPA INTERNET (POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM,
  RECEIVE_BOOT_COMPLETED, VIBRATE, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, WRITE_EXTERNAL_STORAGE
  maxSdk 28 untuk PDF di Android lama); string dex memuat
  "Bawaan HP (default)", "+ Isi Kegiatan di Jam Kosong", "Ringkasan
  Progress".
- **Catatan proses jujur:** ronde ini dikerjakan dua jalur implementasi
  paralel di folder yang sama (seperti ronde 2); keduanya direkonsiliasi
  menjadi SATU basis kode di atas sebelum build final (duplikasi
  `DayPhase`/`PhaseIllustration` dibuang, API ToneResolver disatukan).
- **Catatan jujur:** build debug; verifikasi di VM sebatas compile +
  signature + badging + isi dex. Varian header 4 fase, fidelity desain,
  reset nada, dan tampilan PDF baru terbukti setelah Rama meng-install
  APK ini dan mengujinya di HP-nya.

## Signing — Keystore Permanen (Ronde 3b / #88, 24 Okt 2026)

### Insiden yang melatarbelakangi
APK ronde 3 tidak bisa di-update di HP (wajib install ulang) karena
tanda tangannya BERBEDA dari ronde 1–2. Akar masalah terverifikasi:
keystore debug lama tidak pernah tersimpan persisten di disk (pencarian
seluruh filesystem: tidak ada `debug.keystore`), sehingga build ronde 3
terbit dengan kunci baru (cert APK ronde 3 = `3bd3e868...`). Android
menolak update bila signature berbeda dari aplikasi yang sudah terpasang.
Kunci ronde 1–2 hilang permanen — satu kali install ulang tidak
terhindarkan — tetapi insiden ini TIDAK BOLEH terulang; solusinya adalah
keystore permanen di bawah ini.

### Keystore permanen — lokasi & aturan keras
- **Lokasi:** `~/workspace/todo-planner/keystore/todo-planner.keystore`
  — folder ini berada **DI LUAR repo git** (root repo adalah folder
  `app/` ini). Alias: `todoplanner`.
- **Cert SHA-256:** `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
- **Password:** tersimpan HANYA di
  `~/workspace/todo-planner/keystore/keystore-info.txt` (izin file 600),
  di folder yang sama dengan keystore — juga di luar repo.
- **ATURAN KERAS:**
  1. JANGAN PERNAH menghapus file keystore ini. Bila hilang, semua update
     di HP akan kembali gagal total seperti insiden ronde 3.
  2. JANGAN PERNAH memindah/menyalin keystore ke dalam repo, dan JANGAN
     PERNAH meng-commit-nya (juga jangan commit `keystore.properties`
     maupun password-nya) ke GitHub atau tempat publik mana pun.
  3. Semua build Todo Planner selamanya WAJIB ditandatangani kunci ini.

### Mekanisme di build (app/build.gradle.kts)
- Di root proyek ada file **lokal** `keystore.properties` (sudah masuk
  `.gitignore`, jangan commit) berisi:
  `storeFile=../keystore/todo-planner.keystore`, `storePassword`,
  `keyPassword` (keduanya dibaca dari `keystore-info.txt`), dan
  `keyAlias=todoplanner`. Path `storeFile` relatif terhadap root proyek.
- `app/build.gradle.kts` membaca file itu **hanya bila ada**: bila ada,
  dibuat `signingConfigs` bernama `permanent` dari isinya dan dipakai
  untuk buildTypes **debug DAN release**. Password TIDAK PERNAH
  hardcoded di `build.gradle.kts` (file itu ter-push ke repo publik).
- **Fallback:** bila `keystore.properties` tidak ada (mis. build GitHub
  Actions yang tidak punya akses keystore lokal), build memakai signing
  debug bawaan Android seperti biasa — build tetap jalan. APK hasil
  fallback TIDAK bisa meng-update APK bertanda tangan permanen (dan
  sebaliknya); untuk rilis ke HP Rama selalu pakai build lokal berkunci
  permanen.

### Riwayat versi
| versionCode | versionName | Keterangan |
|---|---|---|
| 1 | 1.0 | Rilis awal (#83) + perbaikan ronde 1 (#85), ronde 2 (#86), ronde 3 (#87) — signing debug tidak persisten (insiden di atas) |
| 2 | 1.0.1 | Ronde 3b (#88): isi kode = ronde 3, signing diganti keystore permanen |
| 3 | 1.0.2 | Ronde 4 (#89): PDF deskripsi + multi-halaman, getar berlapis + channel v4 + diagnostik, Schedule Warning 1 entri/kegiatan, layar bangun (fullScreenIntent) |

### Hasil build ronde 3b (#88)
- **Status: BERHASIL — `BUILD SUCCESSFUL`** (Gradle 8.7 offline,
  `assembleDebug` di folder proyek asli di VM ini).
- **APK debug baru:** `app/build/outputs/apk/debug/app-debug.apk` —
  **16.936.408 byte (±16,2 MB)**, sha256
  `09fe82fd20ee930074c296f64d556db2e0da3132f9e9f14fc0018ba0e6fcdcaa`;
  salinan praktis `../TodoPlanner-debug.apk` sudah ditimpa (hash sama).
- **Gerbang penerimaan LOLOS:** `apksigner verify --print-certs` pada APK
  final → Signer #1 certificate SHA-256 digest =
  `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — **SAMA PERSIS** dengan cert keystore permanen di atas. File keystore
  terverifikasi masih ada sesudah build; integritas zip APK utuh
  (157 entri, `testzip` bersih); package `id.rama.todoplanner`
  versionCode **2** / versionName **1.0.1**, minSdk 26, targetSdk 34;
  permission **tetap TANPA INTERNET** (POST_NOTIFICATIONS,
  SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, VIBRATE,
  FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK,
  WRITE_EXTERNAL_STORAGE maxSdk 28).
- **File berubah ronde 3b:** `app/build.gradle.kts` (signingConfigs
  `permanent` + fallback, versionCode 1→2, versionName 1.0→1.0.1),
  `.gitignore` (+`keystore.properties`), `README.md` (bab ini), dan file
  lokal baru `keystore.properties` di root proyek (tidak di-commit).
- **Catatan jujur untuk install pertama versi ini:** karena kunci ronde
  1–2 hilang, APK v1.0.1 berkunci permanen ini tetap memerlukan SATU KALI
  uninstall+install bersih di HP (selamatkan data dulu lewat Backup JSON
  di Pengaturan bila perlu). Mulai dari versi inilah setiap update
  berikutnya bisa langsung ditimpa tanpa uninstall, selama keystore
  permanen di atas tidak hilang.

## Perbaikan Ronde 4 — Uji HP v1.0.1 (#89, 24 Okt 2026)

Uji Rama atas v1.0.1: desain PDF, reset nada "Bawaan HP", timeline Harian,
dan restore data LULUS. Empat catatan baru dikerjakan di ronde ini
(versionCode **3** / versionName **1.0.2**, signing keystore permanen sama):

### 1. PDF: deskripsi per kegiatan + multi-halaman penuh
- Field `note` tiap kegiatan kini dicetak DI BAWAH judulnya (bila tidak
  kosong) dengan format yang jelas beda: **9pt, abu-abu, italic, indent
  10pt**, dibungkus penuh multi-baris tanpa elipsis (`PdfExporter.wrapFull`).
- Simulasi tinggi baris (`rowHeight`) menghitung baris deskripsi, jadi
  paginasi mengalir alami ke berapa pun halaman. Diverifikasi TIDAK ada
  pembatas/truncate 1 halaman di exporter; footer "Halaman X dari Y" =
  total nyata hasil simulasi.
- Preview PDF di app disamakan: deskripsi tampil di bawah judul tiap baris
  (10.5sp italic abu-abu).

### 2. Getar reminder — perbaikan berlapis + diagnostik
Temuan kode (penyebab paling mungkin getar absen di v1.0.1): getar dipanggil
LEWAT `AudioAttributes USAGE_ALARM` di dalam try/catch yang MENELAN semua
exception — di EMUI jalur atribut alarm dapat digerbangi kebijakan haptik
OEM sehingga panggilan diterima API tetapi motor tidak berputar, tanpa
jejak apa pun; ditambah channel v3 `enableVibration(false)` menutup jalur
cadangan OS. Perbaikan:
- (a) Panggilan UTAMA kini overload POLOS `vibrate(effect)` TANPA
  AudioAttributes, waveform amplitudo PENUH 255
  (`VIBRATION_AMPLITUDES`), pola di-loop selama bunyi (maks 20 dtk,
  berhenti bersama suara). Lapisan cadangan berjenjang: waveform + atribut
  alarm (perilaku lama) → API pola lama.
- (b) Exception TIDAK ditelan: hasil setiap upaya getar (sukses/gagal +
  pesan + `hasVibrator` + jam) disimpan ke `AppPrefs.vibrationDiagnostic`
  dan ditampilkan sebagai baris "Diagnostik getar: ..." di layar Reminder
  & Pengaturan (`VibrationDiagnosticLine`).
- (c) Tombol Tes Reminder tetap lewat jalur asli (AlarmManager → Receiver
  → Service → `play()`), dan Toast kini melaporkan `hasVibrator` HP.
- (d) Channel BARU **v4** (`reminder4_*`): `enableVibration(true)` + pola
  getar kuat, suara tetap `null` (anti dobel — bunyi 100% dari kode),
  visibilitas lockscreen PUBLIC. Channel v3 & skema lama dihapus.

### 3. Schedule Warning: satu entri per kegiatan
- Penyebab lama: daftar diekspansi per HARI (14 hari × semua kegiatan),
  jadi dua kegiatan harian berulang (Doa & tes 1, sama-sama 08.45) memenuhi
  daftar berselang-seling.
- Kini memakai `List<ActivityEntity>.nextReminders(now, limit)` (domain):
  SATU entri per kegiatan = kemunculan berikutnya saja, urut waktu trigger
  naik, tie-break judul (lowercase). Berlaku di layar Reminder (maks 10)
  DAN kartu Schedule Warning di Beranda (maks 3). Seri lengkap tetap di
  Detail kegiatan.

### 4. Layar menyala saat reminder di HP terkunci
- Permission baru `USE_FULL_SCREEN_INTENT`; notifikasi reminder (receiver
  & service) memakai `setFullScreenIntent` dengan intent WAKE khusus
  (request code +500.000) yang membuka MainActivity langsung ke Detail
  kegiatan.
- MainActivity untuk intent WAKE: `setShowWhenLocked(true)` +
  `setTurnScreenOn(true)` (API 27+; flag window ekuivalen di bawahnya).
  Penting: peluncuran WAKE TIDAK menghentikan bunyi (alarm tetap berdering
  sampai user bertindak / 20 dtk); ketukan notifikasi biasa (intent konten,
  wake=false) tetap menghentikan bunyi seperti perilaku ronde 2.
- Cadangan: selama bunyi, ReminderSoundService memegang
  `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` ber-timeout seumur
  bunyi. Kategori notif tetap ALARM + visibility PUBLIC.

### Hasil build ronde 4
- **Status: BERHASIL — `BUILD SUCCESSFUL`** (Gradle 8.7 offline, folder
  proyek asli di VM ini, `keystore.properties` ADA → signing permanen).
- **APK debug baru:** `app/build/outputs/apk/debug/app-debug.apk` —
  **16.936.444 byte (±16,2 MB)**, sha256
  `9bbacd3102bdb15c7dd4e9a45884cb5bbe0b98c11f0e06e6d8379fc0e0b37dba`;
  salinan praktis `../TodoPlanner-debug.apk` sudah ditimpa (hash sama,
  diverifikasi). Angka ini dari build BERSIH final (`clean assembleDebug`)
  yang dijalankan koordinator setelah rekonsiliasi kedua jalur — build
  paralel sebelumnya sempat menghasilkan sha `228bb971...` dari source
  yang belum memuat pemisahan intent WAKE & kode deskripsi PDF terakhir;
  file itulah yang digantikan build bersih ini (ukuran kebetulan sama,
  isi dex diverifikasi ulang: `wrapFull`, `extra_wake_reminder`,
  `reminder4_`, "Diagnostik getar" semua ADA).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1 SHA-256
  digest = `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan cert keystore permanen → APK ini bisa di-UPDATE
  langsung di atas v1.0.1 tanpa uninstall.
- Terverifikasi: package `id.rama.todoplanner` versionCode **3** /
  versionName **1.0.2**, minSdk 26, targetSdk 34; permission = sebelumnya +
  `USE_FULL_SCREEN_INTENT` (**tetap TANPA INTERNET**); integritas zip utuh
  (157 entri); string dex memuat `reminder4_` & "Diagnostik getar:".
- **Catatan proses jujur:** seperti ronde 2–3, ronde ini sempat dikerjakan
  dua jalur paralel di folder yang sama; keduanya direkonsiliasi menjadi
  SATU basis kode sebelum build final (desain intent WAKE terpisah untuk
  fullScreenIntent berasal dari jalur paralel dan dipertahankan karena
  lebih benar: layar menyala tanpa mematikan bunyi). Tidak ada file sumber
  yang berubah setelah build final dimulai — APK di atas persis dari
  source folder ini.
- **Catatan jujur:** verifikasi di VM sebatas compile + signature +
  badging + isi dex. Getar, layar-bangun, deskripsi PDF, dan daftar
  Schedule Warning baru terbukti lewat uji HP Rama berikutnya — untuk
  getar, baca baris "Diagnostik getar:" di layar Reminder setelah Tes
  Reminder agar uji berbasis bukti.

## Perbaikan Ronde 5 — Uji HP v1.0.2 (#90, 24 Okt 2026)

LULUS ronde 4 & TIDAK diubah: PDF deskripsi tampil, diagnostik getar,
Schedule Warning 1 entri/kegiatan, suara/getar/layar-bangun jalur ronde 4,
signing permanen. Ronde ini (versionCode **4** / versionName **1.0.3**):

### 1. Daily Reminder dibedakan dari Todo Utama
- Definisi: **Daily Reminder = tanpa jam mulai/selesai** (`startMinutes == null`);
  Todo Utama = ada jam. Form: untuk Daily Reminder, opsi reminder "sebelum mulai"
  DIGANTI pilihan **interval pengulangan dalam 1 hari** — nilai + satuan
  (Detik/Menit/Jam; preset 30 Detik…2 Jam + input angka numerik bebas),
  disimpan sebagai field baru `intervalValue` + `intervalUnit`. Pola hari
  pengulangan (Tidak/Harian/Mingguan/Bulanan/Tahunan) dipertahankan.
- Penjadwalan Daily Reminder = **alarm berantai**: satu alarm aktif; pada hari
  kemunculannya berbunyi berulang tiap interval dalam jendela **06.00–22.00**;
  setelah tiap bunyi, `ReminderReceiver` menjadwalkan tick berikutnya;
  **berhenti permanen untuk hari itu** begitu Daily Reminder dicentang selesai
  (penjadwalan ulang dari keadaan selesai tidak menghasilkan tick hari itu).
  Bunyi/getar/layar bangun memakai jalur ronde 4 yang sama (service + player).
- Schedule Warning (Beranda & layar Reminder) tetap SATU kartu/daftar dan
  1 entri per kegiatan, tetapi barisnya dibedakan (komponen bersama
  `NextReminderRow`): todo utama = titik warna kategori + "Hari ini HH.mm —
  Judul" + "15 mnt sebelum • Kategori"; Daily Reminder = ikon ✓ + pill
  "Daily Reminder" + "tiap {interval} • Kategori". Urut waktu trigger berikutnya.

### 2. Todo utama selesai hilang dari Schedule Warning
- `nextReminderEntry` (domain) dan `nextAlarmPair` (penjadwal) kini MELEWATI
  occurrence yang sudah selesai: occurrence selesai tidak tampil di Reminder
  Terdekat dan alarmnya tidak dijadwalkan lagi; `toggleDone` selalu
  menjadwalkan ulang dari keadaan terbaru (selesai = alarm occurrence itu
  batal; **Batalkan Selesai = restart penuh**: timestamp dihapus, alarm
  dijadwalkan ulang, entri muncul lagi).
- Timestamp selesai per occurrence disimpan di kolom baru `doneTimestamps`
  ("epochDay:epochMillis,...", pasangan `doneDates`). Label
  **"Selesai • HH:mm"** (24 jam tanpa detik) tampil di baris kegiatan
  (Harian/Daily Reminder/Detail/Kalender lewat `ActivityRow`/`CompactRow`);
  data lama tanpa timestamp tampil "Selesai" saja.

### 3. PDF — desain per periode dibedakan (struktur bersama `domain/PdfStructure.kt`)
- Daily Reminder **tidak dimasukkan ke PDF sama sekali** — hanya Todo Utama
  (disaring di Preview maupun di dalam `PdfExporter`).
- **Penomoran urut per kelompok 1 hari** (1., 2., 3., … mulai ulang tiap
  hari) di semua periode; deskripsi ronde 4, ringkasan, header art, dan
  footer dipertahankan; paginasi kini berbasis blok agar sub-header tidak
  yatim di kaki halaman.
- **HARIAN:** sub-header hari + daftar bernomor + status selesai
  "Diselesaikan HH:mm" dari timestamp (tanpa timestamp: "Selesai").
- **MINGGUAN:** kelompok per hari — sub-header hari pita tanggal biru +
  ringkasan kecil ("N kegiatan • M selesai").
- **BULANAN:** sub-header minggu (pita amber) lalu sub-header hari.
- **TAHUNAN:** sub-header bertingkat bulan (pita gradasi lavender→biru) →
  minggu → hari. Preview di aplikasi mengikuti struktur yang sama persis.

### 4. Time picker baru (`ui/components/SlideTimePicker.kt`)
Dial 360° Material diganti **kolom slide vertikal** jam (00–23) & menit
(00–59): geser ↕ berubah langkah demi langkah dengan snap per langkah dan
clamp di rentang; ketuk nilai tetangga = ±1; **ketuk nilai yang sedang aktif
sekali lagi = mode ketik** dengan keyboard numerik saja + validasi rentang
& clamp. Berlaku untuk Jam Mulai & Jam Selesai; tombol Batal/Pilih tetap.

### 5. Menu Reminder disamakan mockup
Layar Reminder kini: kartu **"Mendatang"** gaya mockup (titik kategori,
judul, sublabel; baris Daily Reminder dibedakan sesuai item 1) + kartu
**"⚙️ Pengaturan Reminder"** dengan badge **AKTIF** untuk Suara & Getar
(dan status Izin Notifikasi), baris nada global + tombol Ganti. Fitur nyata
dipertahankan di dalam struktur itu: Izin Notifikasi, Tes Reminder 10
Detik, Tes Getar Langsung, Diagnostik getar, Buka Pengaturan Notifikasi,
pemilih nada global.

### Addendum getar (laporan 08.11 + koreksi device 08.22)
- Device uji Rama = **Xiaomi Redmi (MIUI/HyperOS)**, BUKAN Huawei — catatan
  "EMUI" ronde 1–4 merujuk asumsi awal yang salah. Semua jalur aplikasi ini
  **OEM-agnostik** (API standar Android berlapis), tanpa hack per merek.
- **(A) Tombol "Tes Getar Langsung (2 detik)"** (di blok Tes Reminder &
  Pengaturan): one-shot `DEFAULT_AMPLITUDE` dari proses UI — alat bisect:
  tombol bergetar tapi reminder tidak ⇒ jalur service/waveform; tombol juga
  tidak bergetar ⇒ gerbang level device/mode.
- **(B) Diagnostik diperluas:** hasil upaya + `ringerMode`, filter DND
  (`currentInterruptionFilter`), `hasAmplitudeControl`, **MANUFACTURER /
  MODEL / SDK**, dan jalur penembak (UI langsung vs reminder/service).
- **(C) Fallback API berjenjang** ditambah lapisan one-shot
  `DEFAULT_AMPLITUDE` + pola legacy di antara waveform utama dan jalur lama.

### Migrasi database (NON-DESTRUKTIF)
- Room **versi 1 → 2** dengan `Migration(1,2)` eksplisit: `ALTER TABLE
  activities ADD COLUMN intervalValue INTEGER`, `ADD COLUMN intervalUnit
  TEXT`, `ADD COLUMN doneTimestamps TEXT NOT NULL DEFAULT ''`. Tanpa
  `fallbackToDestructiveMigration` — data lama utuh saat update timpa.
- Backup JSON: field baru diekspor; file backup LAMA tetap bisa diimpor
  (field yang tidak ada dibaca sebagai null/"").

### Hasil build ronde 5
- **Status: BUILD SUCCESSFUL** (Gradle 8.7 offline, folder proyek asli di
  VM ini, `keystore.properties` ADA → signing permanen) — build bersih
  final (`clean assembleDebug`).
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` —
  **17.001.980 byte (±16,2 MB)**, sha256 `411a925a09133aaf6c60dffb286a0a534b85afa2748153f4894ca2d5f4410439`;
  salinan staging `../TodoPlanner-debug.apk` ditimpa (hash sama).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1 SHA-256
  digest = `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen → update timpa di atas v1.0.2.
- **Catatan jujur:** verifikasi di VM sebatas compile + signature + badging
  + isi dex. Perilaku runtime (rantai interval Daily Reminder, picker slide,
  PDF per periode, getar di Xiaomi Redmi) terbukti lewat uji HP berikutnya.

## Perbaikan Ronde 6 — Uji HP v1.0.3 (#91, 24 Okt 2026)

Uji Rama atas v1.0.3 di **Xiaomi Redmi Note 9 Pro** (bukti:
`../feedback/uji6-*`; diagnostik getar HP: "OK waveform 255 •
hasVibrator=true • ringer=NORMAL • DND-MATI • amplitudeControl=true").
LULUS ronde 5: menu Reminder gaya mockup + badge AKTIF, Daily Reminder tampil
beda di Mendatang, diagnostik lengkap. Ronde ini versionCode **5** /
versionName **1.0.4**, migrasi Room **2 → 3** non-destruktif, backup JSON
lama tetap kompatibel, TANPA QA (perintah Rama).

### 1. GETAR — DIBEKUKAN atas perintah Rama (amendemen 10.30 WIB)

Item getar ronde 6 (rencana "tembak bersamaan" multi-jalur + kartu
petunjuk MIUI) **DIBATALKAN/DIBEKUKAN di tengah pengerjaan** atas perintah
Rama, setelah bisect tuntas: telepon/WA yang masuk **tidak bergetar di
KEDUA HP Rama** (Redmi Note 9 Pro + Poco) padahal setting getar menyala,
dan Tes Getar Langsung pun sudah ditekan — jadi masalah getar **bukan
spesifik Todo Planner** dan belum ada validasi yang pasti. Keputusan:
**perilaku getar dipertahankan PERSIS seperti v1.0.3** di build ini —
`ReminderSoundPlayer.kt`, `ReminderChannels.kt` (channel v4), tombol Tes
Getar Langsung dan diagnostik **tidak diubah sama sekali** — file player
terverifikasi identik byte-per-byte dengan versi v1.0.3 di repo.
Item dibuka lagi hanya bila ada validasi baru dari Rama.

### 2. Bug: todo yang sudah ditandai selesai MASIH BUNYI — fix dua lapis

**Akar masalah persisnya di kode ronde 5:**
- **(a) Pembatalan tidak simetris & skema kode bertabrakan.**
  `ReminderScheduler.cancel()` lama hanya membatalkan 4 kode broadcast
  (early `id*2`, start `id*2+1`, Daily Reminder `700000+id`, legacy `id`).
  Skema `id*2 / id*2+1` **bertabrakan antar-kegiatan** (kode legacy
  kegiatan id genap N sama dengan kode early kegiatan id N/2), dan
  pembatalan **tidak pernah menyentuh varian WAKE/full-screen** —
  PendingIntent `getActivity` berkode alarm **+500.000** yang dibuat di
  `ReminderReceiver` & `ReminderSoundService`. Jadi selalu ada jalur
  PendingIntent occurrence yang tidak ikut terbatalkan.
- **(b) Receiver tidak pernah memeriksa database.** `ReminderReceiver`
  ronde <=5 membunyikan alarm apa adanya dari extras intent — tidak ada
  cek `isDoneOn(occEpochDay)`. Setiap alarm yang lolos dari pembatalan
  (tabrakan kode di atas, alarm yatim, race penjadwalan ulang) **pasti**
  tetap berbunyi walau occurrence-nya sudah selesai. Inilah penentu
  akhirnya: tidak ada rem kedua sama sekali.

**Perbaikan ronde 6:**
- **Lapis (a):** skema request code BARU anti-tabrakan — basis
  `2.000.000 + id*10 + jenis` (0=awal, 1=mulai, 2=Daily Reminder); tidak
  bertabrakan dengan kode lama, kode tes (990.002), maupun varian
  WAKE-nya sendiri. `ReminderScheduler.cancelAll()` membatalkan SEMUA
  varian milik satu kegiatan: alarm awal + pas-mulai + rantai Daily Reminder
  pada skema baru **dan** skema lama (alarm yatim versi sebelumnya ikut
  mati), varian WAKE (+500.000) dari semua kode itu, serta notifikasi
  yang telanjur tampil. `toggleDone` di ViewModel kini memanggil
  `cancelAll` secara eksplisit SEBELUM status baru disimpan, lalu
  `schedule()` (yang juga diawali `cancelAll`) memasang alarm occurrence
  berikutnya saja. Daftar kode dihitung dari fungsi yang sama untuk
  jalur schedule & cancel — identik by construction.
- **Lapis (b):** `ReminderReceiver` kini memuat kegiatan dari DB dulu
  (goAsync) untuk alarm berkegiatan: kegiatan terhapus → batalkan sisa
  alarmnya, jangan bunyi; occurrence sudah selesai → **JANGAN bunyi**,
  lewati dan jadwalkan occurrence berikutnya; belum selesai → bunyi
  seperti biasa (Daily Reminder: rantai dilanjutkan). Bila pembacaan DB gagal
  total, alarm tetap dibunyikan (alarm hilang diam-diam lebih buruk).

### 3. Menu Reminder: section Todo Utama & Daily Reminder terpisah + audit Mendatang

- Layar Reminder kini punya **dua sub-kartu berlabel**: "Mendatang —
  📌 Todo Utama" dan "Mendatang — ✅ Daily Reminder", masing-masing dengan
  teks state kosong yang jelas bila tidak ada jadwal. (Beranda tetap
  memakai daftar gabungan "Schedule Warning".)
- **Audit `nextReminders` (kenapa Todo Utama tidak muncul sama sekali):**
  entri todo lama dihitung hanya dari waktu pengingat AWAL
  (mulai − offset). Akibatnya: (i) setelah waktu awal lewat tetapi jam
  mulai **belum** tiba, todo hilang dari daftar padahal alarm pas-mulai
  masih terjadwal — untuk offset 1 hari, hilang **satu hari penuh**;
  (ii) todo ber-reminder "Tanpa" tidak pernah bisa muncul (titik acuannya
  null). Perbaikan: fungsi domain baru `nextTodoAlarm()` menjadi SATU
  sumber kebenaran pasangan alarm (dipakai penjadwal **dan** daftar
  Mendatang): entri = pengingat awal bila masih di masa depan, selain itu
  alarm pas-mulai; occurrence selesai selalu absen; seri berulang memakai
  occurrence berikutnya. Hasil audit per kasus: todo sekali jalan masa
  depan → **muncul**; todo selesai → absen; todo berulang → occurrence
  berikutnya; Daily Reminder → tick interval berikutnya.

### 4. Validasi jam Todo Utama (Ulangi = Tidak)

Di Form Tambah **dan** Edit (termasuk edit setelah "Batalkan Selesai"
atau edit langsung kegiatan yang sudah selesai): bila Ulangi = "Tidak"
dan tanggal+jam mulai terpilih **sudah lewat** dari waktu realtime HP,
simpan **diblokir** dengan pesan merah yang jelas ("Waktu kegiatan
sudah lewat. Untuk Ulangi = Tidak, pilih tanggal & jam di masa depan.")
yang juga tampil langsung di form begitu kombinasi itu terpilih. Bila
Ulangi selain "Tidak" (Harian/Mingguan/Bulanan/Tahunan), waktu lampau
**boleh** disimpan — occurrence berikutnya yang berlaku.

### 5. Logo emoji per kegiatan (migrasi Room 2 → 3)

- Field DB baru `iconEmoji` (TEXT NOT NULL DEFAULT '') lewat
  `Migration(2,3)` eksplisit — non-destruktif, tanpa
  `fallbackToDestructiveMigration`; backup JSON mengekspor field ini dan
  backup lama tetap bisa diimpor (dibaca "").
- Form Tambah/Edit (todo & Daily Reminder): field "Logo emoji (opsional)" —
  user mengetik/menempel **satu emoji dari keyboard emoji HP**; yang
  disimpan hanya **grapheme pertama** (klaster karakter utuh, termasuk
  emoji ber-codepoint ganda); tombol **Hapus** mengosongkannya.
- Logo tampil sebelum judul (`displayTitle`) di baris daftar Harian
  (timeline + Daily Reminder), Mingguan/Bulanan/Kalender (ActivityRow),
  Reminder (baris Mendatang), Beranda, Detail, dan judul notifikasi.
  Tanpa logo = tampilan persis seperti sebelumnya.

### 6. Jendela hari aktif bisa diatur user

- Setting baru `AppPrefs.dayStartMinutes/dayEndMinutes` (default
  **06.00 & 22.00**) — diubah dari kartu "🕐 Hari aktif" di layar
  **Harian** maupun baris pengaturan di **Pengaturan**, memakai
  `SlideTimePicker` yang sama dengan form.
- Template Harian merender slot jam mengikuti jendela ini, **dan**
  jendela alarm berantai Daily Reminder otomatis mengikuti setting yang sama
  (konstanta hardcoded 06.00–22.00 ronde 5 diganti parameter jendela yang
  diteruskan dari AppPrefs lewat penjadwal & domain). Mengubah jendela
  menjadwalkan ulang semua alarm.

### 7. Logic Daily Reminder berubah: selesai = RESET timer

Menggantikan semantik ronde 5 ("berhenti hari itu saat dicentang"):
- Daily Reminder yang **sudah berbunyi lalu ditandai selesai**: timernya
  **RESET** — bunyi berikutnya = **waktu ditandai selesai + interval**
  pilihan user, lalu +interval lagi selama masih di dalam jendela hari
  aktif; keluar jendela → lanjut hari kemunculan berikutnya sesuai pola
  ulangan (tick pertama di awal jendela). Jangkar reset = timestamp
  selesai per occurrence (`doneTimestamps`).
- Jadi "selesai" pada Daily Reminder = **akui & jadwalkan ulang +interval**,
  bukan membunuh rantai hari itu. **Status visual Daily Reminder hari itu
  mengikuti aksi terakhir**: setelah ditandai selesai, baris tampil
  selesai ("Selesai • HH:mm") sementara timer reset tetap berjalan;
  "Batalkan Selesai" menghapus timestamp → rantai kembali ke tick
  berjangkar awal jendela.
- Daily Reminder selesai **tanpa** timestamp (data lama) tidak punya jangkar
  reset → hari itu dilewati seperti ronde 5 (keamanan).
- Daily Reminder tetap dikecualikan dari PDF seperti ronde 5.

### Hasil build ronde 6
- **Status: BUILD SUCCESSFUL** (Gradle 8.7 offline, build bersih
  `clean assembleDebug`, folder proyek asli di VM ini,
  `keystore.properties` ADA → signing permanen). Satu error kompilasi
  sempat muncul (LocalContext di dalam lambda `remember` di FormScreen)
  — diperbaiki, build ulang lulus; hanya warning deprecation lama.
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` —
  **17.018.364 byte (±16,2 MB)**, sha256
  `326a5288c1bbdb12e08ea4589910d077d1fbf5d9369390a85364080bb7b99fd7`;
  salinan staging `../TodoPlanner-debug.apk` ditimpa (hash sama,
  diverifikasi).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1 SHA-256
  digest = `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen → update timpa di atas v1.0.3
  aman. File keystore terverifikasi masih ada sesudah build.
- Terverifikasi: package `id.rama.todoplanner` versionCode **5** /
  versionName **1.0.4**, minSdk 26, targetSdk 34; permission **tetap
  TANPA INTERNET** (sama seperti v1.0.3); integritas zip utuh (157
  entri); string dex memuat `iconEmoji`, pesan validasi "Waktu kegiatan
  sudah lewat…", teks state kosong dua section Reminder, "Logo emoji
  (opsional)", "Hari aktif", dan teks reset Daily Reminder — dan TIDAK memuat
  penanda tembak-bersamaan getar (item 1 beku, perilaku getar = v1.0.3).
- **Catatan jujur:** verifikasi di VM sebatas compile + signature +
  badging + isi dex. Perilaku runtime ronde 6 (pembatalan alarm dua
  lapis, section Reminder, validasi jam, logo emoji, jendela hari aktif,
  reset Daily Reminder) terbukti lewat uji HP Rama berikutnya. Migrasi Room
  2→3 bersifat aditif satu kolom — data v1.0.3 utuh saat update timpa.

## Perbaikan Ronde 7 — Uji HP v1.0.4 (#92, 24 Okt 2026)

Uji Rama atas v1.0.4: validasi jam lampau LULUS (warning muncul, simpan
terblokir), dengan 6 catatan baru. Ronde ini (versionCode **6** /
versionName **1.0.5**, migrasi Room **3 → 4** non-destruktif, backup JSON
lama tetap kompatibel): **GETAR TETAP DIBEKUKAN** —
`reminder/ReminderSoundPlayer.kt` TIDAK disentuh (sha256
`9441d784…` identik sebelum & sesudah ronde ini dikerjakan).

### 1. Form: warning validasi basi + tombol Simpan disabled beneran
- Akar bug: pesan error ronde 6 disimpan di state `pastTimeError` yang
  tidak pernah dibersihkan — setelah user menghapus jam (mode Daily
  Reminder), pesan lama tetap tampil walau kondisi sudah valid.
- Fix: state basi DIHAPUS. Warning kini selalu hasil hitung LIVE
  `pastOnceError` dari state form terkini (mode berjam vs tanpa jam,
  tanggal, jam, ulangan) pada setiap perubahan; mode **tanpa jam tidak
  kena aturan waktu lampau**, dan warning hilang seketika begitu valid.
- Tombol **Simpan Kegiatan** kini `enabled = isFormValid` (judul terisi
  + tidak ada error waktu lampau + interval Daily Reminder ≥ 1):
  `GradientButton` dalam keadaan disabled tampil **abu-abu beneran**
  (`#B9C4CA→#9FB0B8`) dan `clickable(enabled=false)` — tidak bisa diklik
  sama sekali, bukan klik-tapi-diam.

### 2. Pengaturan dipisahkan dari layar jadwal
- Layar jadwal (kini **Schedule Warning**) isinya HANYA dua section
  mendatang: Todo Utama + Daily Reminder.
- Kartu **Pengaturan Reminder** (badge Suara/Getar/Izin Notifikasi,
  tombol Izinkan Notifikasi, pemilih Nada Dering global) dan kartu
  **Tes & Diagnostik Reminder** (Tes Reminder 10 Detik, Tes Getar
  Langsung, Diagnostik getar) PINDAH ke layar **Pengaturan**.

### 3+6. Semantik selesai Daily Reminder — model final
- Menandai Daily Reminder selesai **bukan** state selesai: `doneDates`
  TIDAK disentuh, judul tidak dicoret, centang tidak dipakai.
- Penanda diganti **emoji WINK 😉** (baris Harian, Beranda, Detail).
- Kolom baru `dailyDoneLog` (migrasi 3→4, format per hari
  `epochDay:millis,millis,...`): SETIAP penandaan **ditambahkan** ke
  daftar hari itu; daftar jam penandaan tampil di entri Daily Reminder
  (Harian & Kegiatan Hari Ini di Beranda & Detail:
  "😉 Ditandai selesai: 07.15 • 09.30") dan **tidak ter-reset** saat
  bunyi berikutnya (receiver Daily Reminder tidak lagi menahan bunyi
  berdasarkan status penandaan).
- Jam bunyi berikutnya yang tampil = **penandaan terakhir + interval**,
  ter-update setiap penandaan baru; rantai alarm tetap restart sesuai
  logic ronde 6 (jangkar fungsi trigger kini membaca penandaan
  terakhir dari `dailyDoneLog`, dengan fallback timestamp ronde 6 untuk
  data lama). Todo Utama: semantik selesai tidak berubah sama sekali.

### 4. Dot kategori berdetak
- Titik warna kategori pada baris Todo Utama di Schedule Warning (dan
  kartu Schedule Warning di Beranda) kini **GLOW + BERDETAK**: halo
  warna kategori berdenyut mengikuti pola detak jantung **lub-dub**
  (denyut ganda 1,38×/1,30× lalu jeda panjang, siklus 1,3 dtk,
  infinite) lewat animasi **skala** — bukan kedip opacity. Bila skala
  animator sistem = 0 (reduce motion), titik tampil statis ber-glow.

### 5. Rename global teks UI
- **Daily Reminder** menggantikan istilah lama untuk kegiatan tanpa jam
  di SEMUA teks UI: label form ("Hapus jam (jadikan Daily Reminder)",
  "😉 Daily Reminder — Interval Pengulangan"), section Harian
  ("😉 Daily Reminder Hari Ini"), section & pill Schedule Warning,
  teks kosong, judul notifikasi ("😉 Daily Reminder: …"), Detail,
  pengaturan, README ini.
- **Schedule Warning** menggantikan nama lama menu jadwal: judul
  layar, entri menu Semua, judul kartu di Beranda, tautan
  "Lihat Schedule Warning →".
- Hasil grep sesudah sweeping: string UI di `.kt` untuk kedua istilah
  lama = **NIHIL**; README = **NIHIL**. Sisa kata di kode hanyalah nama
  internal (nama properti/fungsi internal diizinkan — bukan teks UI)
  dan nama kelas ikon bawaan Material Icons di dex.

### Hasil build ronde 7
- **Status: BUILD SUCCESSFUL** (Gradle 8.7 offline, `clean
  assembleDebug`, keystore.properties ADA → signing permanen); hanya
  warning deprecation lama.
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` —
  **17.018.364 byte**, sha256
  `6077bbefad89f59afb1b18b3f8829e8bb454e938b967e4fd20de538ea1f9b74e`;
  salinan staging `../TodoPlanner-debug.apk` ditimpa (hash sama,
  diverifikasi).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1
  SHA-256 digest =
  `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen → update timpa di atas v1.0.4
  aman.
- Terverifikasi: package `id.rama.todoplanner` versionCode **6** /
  versionName **1.0.5**, minSdk 26, targetSdk 34; permission tetap
  **TANPA INTERNET**; string dex memuat `dailyDoneLog`,
  `Schedule Warning`, `Daily Reminder`, "😉 Ditandai selesai",
  "Bunyi berikutnya", "Tes & Diagnostik", `HeartbeatCategoryDot`, dan
  TIDAK memuat istilah UI lama; `ReminderSoundPlayer.kt` identik
  byte-per-byte dengan keadaan v1.0.4 (getar beku).
- **Catatan jujur:** verifikasi di VM sebatas compile + signature +
  badging + isi dex. Perilaku runtime ronde 7 (warning hilang live,
  tombol disabled, penandaan wink berulang + daftar jamnya, dot
  berdetak) terbukti lewat uji HP Rama berikutnya. Migrasi Room 3→4
  bersifat aditif satu kolom — data v1.0.4 utuh saat update timpa.
  Catatan desain: karena penandaan Daily Reminder bukan lagi state
  selesai, Daily Reminder tidak lagi dihitung "selesai" pada progres
  harian; ia tampil dengan daftar jam penandaannya sendiri.

## Perbaikan Ronde 8 — Uji HP v1.0.5 (#93, 24 Okt 2026)

Catatan proses (jujur): seperti ronde 6–7, implementasi ronde 8 sudah
diterapkan penulis paralel sebelum sesi koordinator sempat menulis kode.
Peran koordinator di ronde ini = **audit baris-per-baris terhadap
instruksi #93 + 3 koreksi auditor + clean build pembuktian**. Hasil
audit: ke-6 item lengkap; koreksi auditor: (a) deskripsi timeline dibuat
miring (italic) agar persis gaya PDF; (b) limit daftar mendatang Beranda
dinaikkan 3 → 10 lalu dipangkas per jenis — limit 3 campuran bisa
mendesak Daily Reminder keluar dari kartu Ritual (atau sebaliknya);
(c) bab README ini (penulis paralel tidak menulisnya).

### 1. Penanda selesai Daily Reminder: WINK → SPARKLES ✨, tanpa buletan
Koreksi atas ronde 7: penanda selesai Daily Reminder kini emoji
**SPARKLES ✨ telanjang** — tanpa lingkaran/chip/border latar di semua
tempat ia tampil: baris Schedule Warning (`ReminderRows`), baris
`ActivityRow` (Kegiatan Hari Ini), `CompactRow` (Harian), judul section
Template Harian, header + penjelasan Form, header section layar
Reminder, judul notifikasi (`ReminderScheduler`), dan label Detail.
Area ketuk 24 dp dipertahankan (ketuk ✨ = catat penandaan baru);
bila belum pernah ditandai hari itu, area ketuk tampil kosong.
Nol emoji 😉 tersisa di seluruh `app/src`. Penanda Todo Utama
(lingkaran centang) tidak berubah.

### 2. Dot Todo Utama: skala berdetak DIGANTI expanding glow
Animasi skala lub-dub ronde 7 dinilai salah oleh Rama dan **dihapus
total** (termasuk import `scale`/`keyframes`). Pengganti
(`ExpandingGlowCategoryDot` di `ReminderRows`): dot warna kategori
**tetap diam**; sebuah cincin/halo cahaya warna kategori **mengembang
keluar dari dot (10 → 30 dp) sambil memudar hingga transparan**,
berulang tiap 2 detik — efek ini bernama **ripple** (atau **sonar**).
Bila animator sistem mati (reduce motion), dot tampil statis dengan
halo lembut tanpa gerak.

### 3. Timeline Template Harian: deskripsi tampil di bawah judul
Baris Todo Utama di timeline Harian kini menampilkan `note`
(deskripsi/catatan) di bawah judul bila tidak kosong — gaya PDF/Detail:
abu-abu, miring (italic), wrap penuh, tanpa batas baris. Bila kosong,
tampilan seperti sebelumnya.

### 4. BUG PRIORITAS: Daily Reminder bunyi TANPA notifikasi — akar + fix
**Akar pastinya (terbukti dari urutan kode, bukan tebakan):**
`ReminderReceiver.ring()` memasang notifikasi dengan ID =
`checklistCode(id)`, lalu cabang Daily Reminder langsung memanggil
`ReminderScheduler.schedule()` untuk tick berikutnya — dan `schedule()`
selalu membuka dengan `cancelAll()`, yang menjalankan
`nm.cancel(checklistCode)`. Notifikasi yang **baru saja dipasang
langsung dihapus**, sementara service/pemutar terus berbunyi 20 detik:
tanpa notifikasi, tanpa aksi "Matikan Bunyi", tanpa identitas kegiatan
— persis keluhan Rama. Todo Utama tidak kena karena cabangnya tidak
memanggil `schedule()` setelah `ring()`. Lapisan kedua: bila
`startForeground` service ditolak OEM (kasus MIUI), jalur cadangan
lama memutar bunyi langsung tanpa memasang notifikasi pengganti.

**Fix dua lapis (tanpa menyentuh file getar):**
- `schedule(..., preserveNotification = true)` → `cancelAll(...,
  clearNotifications = false)`: rantai interval Daily Reminder di
  receiver (cabang `isChecklist` dan cabang `KIND_CHECKLIST`) tidak
  lagi menghapus notifikasi bunyi yang sedang berjalan. Jalur lain
  (simpan/hapus/tandai/toggle) memakai default `false` sehingga
  notifikasi basi tetap dibersihkan seperti sebelumnya.
- `ReminderSoundService`: bila `startForeground` gagal, service kini
  memasang notifikasi yang SAMA lewat `NotificationManager` (judul
  kegiatan + aksi Berhentikan + ketuk buka Detail) sebelum memutar.
Audit semua jalur bunyi: todo early/at-start (notifikasi bertahan
karena tanpa schedule susulan), rantai interval Daily Reminder
(dua lapis di atas), dan alarm tes (jalur `ring()` yang sama).
Sementara menunggu update: membuka aplikasi menghentikan bunyi
(`MainActivity` memanggil `StopSoundReceiver.requestStop`).

### 5. Card Beranda "Kegiatan Berikutnya" DIBELAH + kartu "Ritual" 🪄
- Kartu **"Kegiatan Berikutnya"** kini HANYA Todo Utama
  (`todays` disaring `!isChecklist`).
- Kartu BARU **"🪄 Ritual"** tepat di bawahnya, HANYA Daily Reminder
  (baris `NextReminderRow` yang sama: ✨ + pill + interval + bunyi
  berikutnya), dengan state kosong "Belum ada Daily Reminder
  terjadwal".
- Kartu Beranda "Schedule Warning" ikut dipecah: kini HANYA Todo
  Utama (Daily Reminder sudah pindah ke Ritual). Layar Reminder
  (`OtherScreens`) sudah terpisah section sejak ronde 6, jadi tidak
  diubah strukturnya — hanya emoji penandanya dikoreksi ke ✨.
- "Kegiatan Hari Ini" sengaja tetap campuran: itu daftar lengkap
  seharian (bukan "berikutnya"), dan barisnya sudah dibedakan jelas
  (✨ + pill Daily Reminder vs lingkaran centang todo).

### 6. Garis aksen Todo Utama di card Kegiatan Berikutnya: neon shimmer sweep
`ShimmerAccentBar` (di `HomeScreen`, dipakai `UpcomingItem`): garis
warna kategori diam sebagai dasar; sebuah segmen pendek bergradasi
**transparan–perak–transparan** meluncur dari atas ke bawah garis
(siklus 2,2 detik, berulang) — efek ini bernama **shimmer** (disebut
juga **shine sweep**). Bukan kedip opacity: warna dasar tidak pernah
berubah, hanya sapuan cahaya perak yang lewat. Bila animator sistem
mati (reduce motion), garis tampil statis tanpa sapuan.

### Hasil build ronde 8
- **Status: BUILD SUCCESSFUL** (Gradle 8.7 offline, `clean
  assembleDebug`, keystore.properties ADA → signing permanen).
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` —
  **17.034.748 byte**, sha256
  `2f2291d62841385f28c292ead9bfdcee58317c5726d9a22f694622481eab01e0`;
  salinan staging `../TodoPlanner-debug.apk` ditimpa (hash sama,
  diverifikasi).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1
  SHA-256 digest =
  `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen → update timpa di atas v1.0.5
  aman.
- Terverifikasi: package `id.rama.todoplanner` versionCode **7** /
  versionName **1.0.6**, minSdk 26, targetSdk 34; permission tetap
  **TANPA INTERNET**; zip 157 entri, testzip bersih; string dex
  memuat `Ritual`, `ExpandingGlowCategoryDot`, `ShimmerAccentBar`,
  `preserveNotification`; `ReminderSoundPlayer.kt` identik
  byte-per-byte dengan v1.0.3 (sha256
  `9441d784fde596fa8f1a3966a391aa76e01efe1dbe41b271f16de31c2138bc7e`)
  — getar tetap dibekukan. DB tetap versi 4 (ronde 8 tidak menambah
  kolom, jadi tanpa migrasi; data v1.0.5 utuh saat update timpa).
- **Catatan jujur:** verifikasi di VM sebatas compile + signature +
  badging + isi dex. Perilaku runtime ronde 8 (✨ tanpa buletan,
  expanding glow, shimmer perak, deskripsi timeline, kartu Ritual,
  notifikasi bunyi Daily Reminder yang kini bertahan + bisa
  diberhentikan) terbukti lewat uji HP Rama berikutnya.

## Perbaikan Ronde 9 — Uji HP v1.0.6 (#94, 24 Okt 2026)

### 1. Layout ulang card Ritual (Beranda)
Baris Ritual tidak lagi memakai `NextReminderRow` (yang dipakai
Schedule Warning & layar Reminder — ✨ di sana TIDAK berubah).
Baris baru `RitualRow` (di `ReminderRows.kt`):
- **Judul + pill "Daily Reminder" rapat ke PALING KIRI** (kolom weight 1).
- **Hitung mundur ke bunyi berikutnya di SISI PALING KANAN** card —
  `formatCountdown`: "X jam Y mnt lagi" / "Z mnt lagi" / "W dtk lagi",
  diperbarui tiap detik oleh loop lokal baris (bukan tick 30 detik
  Beranda, supaya detiknya benar-benar bergerak); bila bunyi berikutnya
  bukan hari ini tampil "Besok HH:mm" (atau tanggal singkat bila lebih jauh).
- **Logo ✨ DIGANTI lingkaran checklist** (seperti Todo Utama):
  terisi hijau + ✓ bila hari ini sudah pernah ditandai. KLIK lingkaran
  membuka **MENU AKSI** (DropdownMenu): Edit, Hapus (dengan konfirmasi),
  Tandai Selesai — sama seperti aksi Todo Utama di Detail. Ketuk baris
  di luar lingkaran tetap membuka Detail. Subline tetap
  (interval • kategori • terakhir ditandai, tanpa ✨ di ekornya).
- Edit dari menu ini tidak lewat Detail dulu: `HomeScreen` menerima
  `onEditActivity` dari `TodoApp` yang langsung membuka
  `Screen.Form(editId)`.

### 2. Restruktur card Beranda
- Section **"Kegiatan Hari Ini" (daftar lengkap seharian) DIHAPUS dari
  Beranda** — datanya tetap tersimpan; Detail tetap menampilkan riwayat
  penandaan (Daily Reminder) / status (Todo Utama).
- Card **"Kegiatan Berikutnya" DIUBAH NAMA saja menjadi "Kegiatan Hari
  Ini"** (ikon ⏰ dipertahankan). Card "Schedule Warning" Beranda tidak
  berubah.

### 3. Mekanisme sortir card "Kegiatan Hari Ini" (baru)
Daftar card ini kini = SEMUA Todo Utama hari itu (bukan lagi cuplikan
take(2) yang belum selesai saja):
- **Belum selesai dulu**, paling mepet jamnya ke atas: jam mulai yang
  masih di depan didahulukan, lalu yang sudah lewat, masing-masing urut
  jam naik.
- **Sudah selesai menumpuk di BAWAH**, sesama selesai diurutkan
  berdasarkan WAKTU SELESAI naik (tanpa timestamp = paling akhir, lalu
  jam mulai).
- Lingkaran di `UpcomingItem` kini BISA DIKETUK (dulu hanya hiasan):
  ketuk = toggle Selesai, dan baris PINDAH ke bawah secara otomatis
  lewat sortir ini (recompose dari Flow). Baris selesai: lingkaran
  terisi hijau + ✓, judul dicoret abu-abu. Berlaku untuk card ini saja;
  Ritual tidak berubah semantiknya.

### 4. FITUR BARU — "Pilih Warna" di form Tambah/Edit
- Field baru per kegiatan **`colorHex`** (nullable; null = ikut warna
  kategori lama), **migrasi Room 4->5** non-destruktif, ikut backup JSON
  (backup lama tanpa field ini dibaca sebagai null = default kategori).
- Section **"Pilih Warna" tepat di bawah Kategori** di form:
  deretan **12 warna preset** (swatch) + **palette manual** (slider hue
  0–360° pelangi + input hex 6 digit + tombol "Pakai warna ini" /
  "Pakai hex"). Komponennya `ColorPickerSection` di
  `ui/components/ColorPicker.kt` (dipakai juga Pengaturan, poin 6).
- Logika anti-timpa: `colorCustom` membedakan "user sengaja memilih"
  vs "warna ini sekadar default kategori" — ganti kategori HANYA
  menggeser warna bila belum custom; "Ikuti warna kategori"
  mengembalikan ke default (disimpan null).
- Warna pilihan dipakai untuk **WARNA KATEGORI + LABEL KATEGORI (pill)**
  di card-card Todo Utama lewat SATU fungsi `activityColor()`
  (custom bila ada & valid, else warna kategori lama): aksen +
  `ShimmerAccentBar` di `UpcomingItem`, aksen `ActivityRow`, dot
  `ExpandingGlowCategoryDot` di `NextReminderRow`, `CategoryPill`
  (dengan latar 18% + teks gelap), titik timeline Harian. Daily Reminder
  tidak wajib mengikutinya (Ritual tidak memakai fungsi ini).

### 5. Warna prioritas TETAP (amendemen 5 — terpisah dari warna custom)
Pemetaan tetap `PriorityColors` (`priorityColor()`): **Rendah = hijau
(0xFF30A46C), Sedang = biru (0xFF0091FF), Tinggi = merah (0xFFE5484D)**.
TIDAK PERNAH mengikuti `colorHex`. Diterapkan di: chip Prioritas di
form (aksen per opsi), segmen "Prioritas X" di subline card
(`ActivityRow`, `UpcomingItem` — span berwarna, layout subline tidak
berubah), nilai Prioritas di Detail (teks tebal berwarna).

### 6. Warna label "Daily Reminder" GLOBAL (amendemen 6 — bukan per-item)
- Setting **`dailyReminderColorHex` di AppPrefs** (default kosong =
  hijau lama persis: latar 0xFFDFF5E9 + teks 0xFF22714F), diekspos
  lewat `ProfileState.dailyReminderColor` + `vm.setDailyReminderColor()`.
- Diubah dari **Pengaturan** (kartu "🏷️ Warna Label Daily Reminder"):
  pratinjau pill + preset + palette manual lengkap + "Kembali ke hijau
  default" (menyimpan string kosong).
- Warna ini dipakai pill "Daily Reminder" di SEMUA tempat:
  `RitualRow`, `NextReminderRow` (Schedule Warning Beranda + layar
  Reminder — lewat parameter `dailyReminderColor`, default hijau lama
  untuk pemanggil lama). Warna custom: latar 20% + teks versi gelap
  (`darkenColor`) supaya terbaca di semua preset.

### Hasil build ronde 9
- **Status: BUILD SUCCESSFUL** (Gradle 8.7 offline, `clean
  assembleDebug`, keystore.properties ADA → signing permanen).
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` —
  **17.067.516 byte**, sha256
  `1446fb2192db2364f51bc7a96bca30a4f60d7f0654d0da329c24b29f6836a6da`;
  salinan staging `../TodoPlanner-debug.apk` ditimpa (hash sama,
  diverifikasi ulang setelah clean build).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1
  SHA-256 digest =
  `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen → update timpa di atas v1.0.6
  aman.
- Terverifikasi: package `id.rama.todoplanner` versionCode **8** /
  versionName **1.0.7**, minSdk 26, targetSdk 34; permission tetap
  **TANPA INTERNET**; zip 157 entri, testzip bersih; migrasi Room 4->5
  non-destruktif (`colorHex`); `ReminderSoundPlayer.kt` identik
  byte-per-byte dengan v1.0.3 (sha256
  `9441d784fde596fa8f1a3966a391aa76e01efe1dbe41b271f16de31c2138bc7e`)
  — getar tetap dibekukan.
- **Catatan jujur:** verifikasi di VM sebatas compile + signature +
  badging + isi dex. Perilaku runtime ronde 9 (countdown Ritual, menu
  aksi lingkaran, sortir pindah-ke-bawah, warna custom & prioritas,
  warna label global) terbukti lewat uji HP Rama berikutnya.
- **Catatan proses:** seperti ronde 6–8, sebagian implementasi ronde 9
  sudah diterapkan penulis paralel sebelum koordinator menulis kode;
  koordinator mengaudit baris-per-baris terhadap instruksi #94 +
  amendemen, melengkapi 3 celah (warna prioritas di Detail, palette
  manual di setting warna label, bab README ini), lalu clean build
  pembuktian.

## Perbaikan Ronde 10 — Uji HP v1.0.7 (#95, 24 Okt 2026)

### 1. Daily Reminder: HANYA jam terakhir ditandai selesai
Rama menilai daftar lengkap penandaan ("15:22 • 15:22 • 15:22 • 15:25
• 15:25") menuh-menuhin. Aturan daftar-lengkap ronde 7 DIGANTI:
- Tampilan di SEMUA tempat kini hanya **"Terakhir ditandai: HH:mm"**:
  baris Daily Reminder di `ActivityRow` (Harian/Kalender), baris
  CompactRow Template Harian, dan Detail ("Terakhir ditandai").
- Log internal dipangkas: `withDailyCompletion()` kini MENIMPA daftar
  hari itu dengan satu entri terbaru (bukan menambah). Entri hari lama
  yang masih berisi banyak waktu tetap terbaca, tapi tampilan hanya
  memakai entri terakhir (`dailyCompletionTimes().last()`); jangkar
  reset timer (`lastDailyCompletion`) tidak berubah perilakunya.
- Subline Ritual ("terakhir ditandai …") sudah last-only sejak ronde 9
  dan tidak diubah.

### 2. KOREKSI color picker: circle wheel, bukan palette + tombol
Rama mengoreksi ronde 9: yang diminta adalah **circle color picker**
(seperti gambar referensi yang dikirimnya), pemilihannya berdasarkan
KLIK — bukan slider palette + kolom hex + tombol apply.
- DIHAPUS total dari `ColorPickerSection`: slider "Warna bebas
  (palette)", kolom input hex, tombol "Pakai hex", tombol "Pakai
  warna ini".
- PENGGANTI: `CircleColorWheel` di `ui/components/ColorPicker.kt` —
  cincin hue melingkar di luar + kotak saturasi/value di tengah,
  masing-masing dengan indikator posisi (lingkaran warna terpilih
  berbingkai putih di cincin, lingkaran kecil berbingkai putih di
  kotak). Wheel HANYA MUNCUL setelah sebuah preset diklik; sebelum itu
  form hanya menampilkan deretan preset + tombol reset.
- Klik preset = warna itu LANGSUNG terpilih SEKALIGUS membuka wheel
  untuk fine-tuning. Setiap ketuk/geser di wheel langsung berlaku
  (tanpa tombol apply): geser cincin = hue (S/V tetap), geser kotak =
  saturasi/value (hue tetap). Loop pointer mentah (down + gerak, tanpa
  menunggu ambang drag) supaya ketuk langsung melompat dan geser
  mengalir; warna dibaca lewat `rememberUpdatedState` agar gestur
  tidak dibatalkan recomposition.
- Wheel tidak menyimpan state warna sendiri — ia menggambar dari warna
  terpilih saat ini dan melaporkan tiap ketuk/geser; parent yang
  menyimpan pilihan (hex). Tombol reset ("Ikuti warna kategori" /
  "Kembali ke hijau default") tetap ada dan menutup wheel.
- Berlaku juga untuk setting warna label Daily Reminder di Pengaturan
  (memakai `ColorPickerSection` yang sama).

### 3. Pill prioritas di card "Kegiatan Hari Ini": label berlatar + fade
Subline `UpcomingItem` (card Beranda "Kegiatan Hari Ini") tidak lagi
menampilkan teks polos "Prioritas Sedang/Tinggi":
- Prioritas kini tampil sebagai **LABEL berlatar persis label kategori**
  (`CategoryPill`: latar warna 18% + teks versi gelapnya) lewat
  komponen baru `PriorityFadePill` di `Common.kt`.
- Efek tambahan: **FADE IN & OUT** — opacity label berdenyut halus
  berulang (transisi infinite 1400 ms, reverse), bukan kedip kasar;
  bila animator sistem mati (reduce motion), pill tampil statis.
- Teks HANYA "Rendah"/"Sedang"/"Tinggi" (tanpa kata "Prioritas").
  Warna tetap pemetaan ronde 9 (Rendah hijau / Sedang biru / Tinggi
  merah), terpisah dari warna custom kategori. Cakupan sesuai
  instruksi: HANYA card "Kegiatan Hari Ini" — teks "Prioritas X" di
  `ActivityRow` (Harian/Kalender) dan label "Prioritas" di Detail
  tidak diubah.

### 4. Rename global: "Bunyi berikutnya" → "Reminder Berikutnya"
Semua string UI "Bunyi berikutnya" diganti "Reminder Berikutnya":
baris Daily Reminder `ActivityRow`, baris CompactRow Template Harian,
dan label Detail. Grep `Bunyi berikutnya` di `app/src` = NIHIL
(komentar penjelas huruf kecil "bunyi berikutnya" di beberapa file
bukan string UI dan dibiarkan).

### 5. Dot card Schedule Warning diberi expanding glow
Kartu Beranda "Schedule Warning" memakai `NextReminderRow` yang
sebenarnya sudah memakai `ExpandingGlowCategoryDot` — tapi ring-nya
terlalu samar (alpha maks 0,6, tanpa halo) sehingga di mata Rama
dot-nya "kurang Glow". Glow DIPERKUAT di komponen yang sama (dipakai
juga baris Todo Utama lain, jadi tetap konsisten): border cincin
2,5 dp dengan alpha maks 0,9 + halo lembut yang ikut mengembang
(alpha 0,22) di belakang dot. Dot tetap diam; cincin mengembang
10→30 dp lalu memudar, siklus 2 detik; reduce-motion = halo statis.

### Hasil build ronde 10
- **Status: BUILD SUCCESSFUL** (Gradle 8.7 offline, `clean
  assembleDebug`, keystore.properties ADA → signing permanen).
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` —
  **17.067.516 byte**, sha256
  `159e7ea59b3604e1411a31e8aa12d22f2772fb1623e433c72a09dce20a998323`;
  salinan staging `../TodoPlanner-debug.apk` ditimpa (hash sama,
  diverifikasi ulang setelah clean build).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1
  SHA-256 digest =
  `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen → update timpa di atas v1.0.7
  aman.
- Terverifikasi: package `id.rama.todoplanner` versionCode **9** /
  versionName **1.0.8**, minSdk 26, targetSdk 34; permission tetap
  **TANPA INTERNET**; zip 157 entri, testzip bersih; TANPA migrasi
  Room baru (DB tetap v5 dari ronde 9 — ronde 10 tidak menambah kolom);
  `ReminderSoundPlayer.kt` identik byte-per-byte dengan v1.0.3 (sha256
  `9441d784fde596fa8f1a3966a391aa76e01efe1dbe41b271f16de31c2138bc7e`)
  — getar tetap dibekukan.
- **Catatan jujur:** verifikasi di VM sebatas compile + signature +
  badging + isi dex. Perilaku runtime ronde 10 (tampilan jam terakhir
  saja, wheel warna, pill prioritas fade, rename, glow Schedule
  Warning) terbukti lewat uji HP Rama berikutnya.
- **Catatan proses:** seperti ronde 6–9, sebagian implementasi ronde 10
  sudah diterapkan penulis paralel sebelum koordinator menulis kode
  (khususnya `CircleColorWheel` + bump versi); koordinator mengaudit
  baris-per-baris terhadap instruksi #95, menerapkan sendiri item 1,
  3, 4, 5 + memperkuat glow, merapikan 2 komentar usang, menulis bab
  README ini, lalu clean build pembuktian.

## Perbaikan Ronde 10 — Uji HP v1.0.7 (#95, 24 Okt 2026)

TANPA QA (perintah assignment). Getar tetap DIBEKUKAN — tidak ada
file/jalur getar yang disentuh. TIDAK ada migrasi Room baru (DB tetap
versi 5): format `dailyDoneLog` tidak berubah, hanya isinya yang kini
satu entri terakhir per hari — format itu subset valid dari format
lama, jadi backup JSON lama tetap kompatibel (multi-entri lama tetap
terbaca, lalu dipangkas di sisi baca ke yang terakhir).

### 1. Daily Reminder — HANYA jam terakhir ditandai selesai
Menggantikan aturan tampil-daftar-lengkap ronde 7 (yang dinilai
menuh-menuhin, mis. "15:22 • 15:22 • 15:25"):
- `Entities.dailyCompletionTimes()` (RONDE 10): data lama multi-entri
  dipangkas DI SISI BACA — hanya timestamp terbesar (terakhir) per hari
  yang dikembalikan (list maksimal satu elemen, supaya pemanggil lama
  `isNotEmpty` / `last` tidak berubah).
- `Entities.withDailyCompletion()`: penandaan baru MENIMPA entri hari
  itu (satu hari = satu penandaan terakhir); entri hari lain tetap
  dipertahankan (Detail tanggal lampau tetap menampilkan jam
  terakhirnya). `doneDates` tidak disentuh.
- Tampilan di SEMUA tempat kini "Terakhir ditandai: HH:mm":
  `ActivityRow` (Harian), `CompactRow` di `TemplateScreens`, dan
  Detail (label baris menjadi "Terakhir ditandai"). Perhitungan bunyi
  berikutnya tetap dari penandaan terakhir (jangkar reset tidak berubah).

### 2. KOREKSI color picker — circle wheel, bukan palette
`ui/components/ColorPicker.kt` ditulis ulang (komponen baru
`CircleColorWheel`, dipakai `ColorPickerSection` yang sama di Form dan
Pengaturan warna label — signature pemanggil TIDAK berubah):
- DIHAPUS total dari form: section palette (slider), input kode hex +
  tombol apply-nya, dan tombol apply warna slider. Grep string UI lama
  itu kini nihil di `app/src/`.
- PENGGANTI persis gambar referensi Rama (`feedback/uji10-color-wheel.jpg`):
  cincin hue melingkar di luar (tebal 19% diameter) + kotak
  saturasi/value di tengah, dengan indikator posisi di keduanya
  (lingkaran warna terpilih berbingkai putih di cincin, lingkaran kecil
  berbingkai putih di kotak). Pemetaan sudut: atas = merah, kanan =
  ungu/biru, bawah = cyan, kiri = kuning (hue = (360 - sudut dari
  atas) % 360; gradasi sweep berjalan searah jarum jam dari timur).
- Wheel MUNCUL hanya saat user mengklik salah satu preset warna
  (`wheelVisible`): klik preset = pilih warna itu SEKALIGUS membuka
  wheel untuk fine-tuning.
- Pemilihan berlaku LANGSUNG saat ketuk/geser di wheel — TANPA tombol
  apply. Geser cincin mengubah hue (saturasi/value tetap); geser kotak
  mengubah saturasi/value (hue tetap). Loop pointer mentah (down +
  gerak, tanpa ambang drag) supaya ketuk langsung melompat dan geser
  mengalir; warna terbaru dibaca lewat `rememberUpdatedState` agar
  gestur berjalan tidak dibatalkan recomposition dan tidak memakai hue
  basi. Wheel tidak menyimpan state warna sendiri — selalu menggambar
  dari warna terpilih dan melaporkannya ke parent (hex), jadi tidak ada
  state ganda yang bisa beradu.
- Tombol reset TETAP: "Ikuti warna kategori" (Form) / "Kembali ke hijau
  default" (Pengaturan); menekannya juga menutup wheel.
- Catatan build: satu error compile sempat muncul — import
  `awaitPointerEventScope` sebagai top-level tidak dikenali di Compose
  1.7 proyek ini karena ia fungsi member `PointerInputScope`; perbaikan
  = hapus import itu, pemanggilan sebagai member tetap sah.

### 3. Pill prioritas di card "Kegiatan Hari Ini"
`UpcomingItem` (HomeScreen, satu-satunya pemakai baris ini) tidak lagi
menampilkan teks polos "Prioritas X": kini memakai `PriorityFadePill`
(`Common.kt`) — bentuk PERSIS label kategori (`CategoryPill`: radius
20dp, padding 8x3dp, latar warna 18% + teks versi gelapnya, 10.5sp
tebal) dengan warna prioritas tetap (Rendah hijau / Sedang biru /
Tinggi merah) + efek FADE IN & OUT (opacity 1.0 <-> 0.45, siklus
1,4 detik, mode Reverse, berulang; statis bila animator sistem mati).
Teks pill HANYA "Rendah"/"Sedang"/"Tinggi" — tanpa kata "Prioritas".
`ActivityRow` (layar Harian, komponen berbeda) tidak diubah — perintah
ini melingkupi card Kegiatan Hari Ini dan baris yang sama bila dipakai
ulang, dan baris itu hanya dipakai card tersebut.

### 4. Rename global "Bunyi berikutnya" -> "Reminder Berikutnya"
Tiga tempat tampil diubah (`ActivityRow`, `TemplateScreens`,
`DetailScreen`); grep case-sensitive "Bunyi berikutnya" di `app/src/`
kini NIHIL. (Komentar kode huruf kecil "bunyi berikutnya" bukan string
UI dan tidak diubah.)

### 5. Dot Schedule Warning — glow diperkuat
Temuan audit (akar masalah, bukan gejala): dot card Schedule Warning
Beranda ternyata SUDAH memakai komponen glow yang sama dengan dot Todo
Utama (`ExpandingGlowCategoryDot` lewat `NextReminderRow` bersama) —
tetapi cincinnya hanya border yang memudar hingga transparan di akhir
siklus, sehingga di foto tampak polos. Perbaikan di komponen BERSAMA
itu (bukan dot kedua yang menyimpang): cincin lebih tebal (2.5dp) &
lebih pekat (alpha awal 0.9) DITAMBAH halo isi lembut (alpha awal 0.22)
yang ikut mengembang 10 -> 30dp lalu memudar — glow kini terbaca di
setiap fase siklus, dan dot Todo Utama & Schedule Warning tetap
identik karena memakai komponen yang sama.

### Hasil build ronde 10
- **Status: BUILD SUCCESSFUL** (Gradle 8.7 offline, `clean
  assembleDebug`, keystore.properties ADA → signing permanen).
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` —
  **17.067.516 byte** (kebetulan persis sama dengan v1.0.7, tetapi hash
  BERBEDA — build baru terverifikasi), sha256
  `9fcd1289bb9a5586317477b3c6b2a6a2a41a129af6bf34e012e92d2223aeef4b`;
  salinan staging `../TodoPlanner-debug.apk` ditimpa (hash sama,
  diverifikasi ulang setelah build).
- **GERBANG LOLOS:** `apksigner verify --print-certs` → Signer #1
  SHA-256 digest =
  `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen → update timpa di atas v1.0.7
  aman.
- Terverifikasi: package `id.rama.todoplanner` versionCode **9** /
  versionName **1.0.8**, minSdk 26, targetSdk 34; permission tetap
  **TANPA INTERNET**; zip 157 entri, testzip bersih; DB tetap versi 5
  (tanpa migrasi); `ReminderSoundPlayer.kt` identik byte-per-byte
  dengan v1.0.3 (sha256
  `9441d784fde596fa8f1a3966a391aa76e01efe1dbe41b271f16de31c2138bc7e`,
  sama dengan yang tercatat di bab ronde 9) — getar tetap dibekukan.
- **Catatan jujur:** seperti ronde 9, sebagian implementasi ronde 10
  sudah diterapkan penulis paralel di working tree sebelum koordinator
  menulis kode (item 1 + 4, komponen `PriorityFadePill`, dan penguatan
  glow); koordinator mengaudit baris-per-baris terhadap instruksi #95,
  melengkapi celah yang tersisa (sisi baca pemangkasan log, wiring pill
  di `UpcomingItem`, penulisan ulang `ColorPicker.kt` menjadi circle
  wheel, bump versi 9/1.0.8 + label Tentang, bab README ini), lalu
  clean build pembuktian. Verifikasi di VM sebatas compile + signature
  + badging + isi zip. Perilaku runtime ronde 10 (wheel di HP, denyut
  pill, glow yang tampak di foto, tampil jam terakhir saja) terbukti
  lewat uji HP Rama berikutnya.

## Perbaikan Ronde 11 — Uji HP v1.0.8 (#96, 24 Okt 2026)

1. **Pill prioritas card "Kegiatan Hari Ini" (3 perubahan sekaligus).**
   - POSISI: pill pindah ke baris judul, SEJAJAR di samping kanan label
     kategori (judul + pill kategori + pill prioritas satu baris) — bukan
     lagi baris sendiri di bawah baris waktu (sumber keluhan Rama di
     screenshot uji11: pill "Sedang"/"Tinggi" tampil di baris bawah).
   - WARNA LEBIH TERANG: latar pill lama (18%, disamakan label kategori)
     membuat pill tampak redup; kini latar hampir penuh (alpha 0.92) +
     teks putih tebal + bayangan neon warna prioritas — jelas lebih
     terang dari label kategori di sebelahnya.
   - EFEK: fade in & out ronde 10 DICABUT, diganti NEON FLICKER speed 1x
     (siklus 1000 ms: terang penuh diselingi dip singkat ala lampu neon).
   Komponen lama `PriorityFadePill` diganti `PriorityNeonPill`; teks tetap
   hanya "Rendah"/"Sedang"/"Tinggi" (tanpa kata "Prioritas"); warna tetap
   pemetaan ronde 9 (hijau/biru/merah); reduce-motion = statis terang.
2. **Glow dot Schedule Warning lebih terang.** Bentuk expanding glow ronde
   8/10 sudah dinilai bagus oleh Rama, tapi warnanya kurang menyala:
   halo 0.22 -> 0.45, cincin penuh 1.0 & tebal 2.5 -> 3 dp (halo statis
   reduce-motion ikut 0.28 -> 0.45). Komponen sama (`ExpandingGlowCategoryDot`)
   dipakai semua baris Todo Utama, jadi tetap konsisten.
3. **Color picker DIGANTI LAGI (ronde 10 -> ronde 11).** Circle wheel ronde
   10 DIHAPUS TOTAL (`CircleColorWheel` + `WheelGeometry` hilang dari
   `ColorPicker.kt`, grep = 0). Pengganti persis gambar referensi Rama:
   picker KOTAK (`RectColorPicker`) — kotak saturasi/value besar +
   slider hue horizontal + tampilan hex & RGB (tampil saja, bukan input).
   Pemilihan berlaku langsung saat ketuk/geser, tanpa tombol apply.
   Perilaku pemicu DIBALIK dari ronde 10: klik preset = langsung pilih
   (picker TIDAK PERNAH muncul karena klik preset); picker hanya dibuka
   oleh 1 swatch MIX WARNA baru (lingkaran pelangi) di ujung deretan
   preset — swatch itu mendapat bingkai tebal bila warna terpilih adalah
   custom (bukan preset). Tombol "Ikuti warna kategori" / "Kembali ke
   hijau default" tetap ada dan menutup picker. Berlaku juga untuk setting
   warna label Daily Reminder di Pengaturan (komponen yang sama).
4. **Ritual auto-uncheck di setengah countdown.** Lingkaran checklist card
   Ritual dulu bertahan ceklis seharian penuh setelah "Tandai Selesai"
   walau reminder terus berdering tiap interval. Kini ceklis hanya hidup
   selama (sekarang - terakhir ditandai) < interval/2, lalu OTOMATIS
   UNCHECK — contoh Rama: interval 30 detik -> uncheck di detik ke-15.
   Bunyi berikutnya tetap interval penuh; belum pernah ditandai =
   uncheck; interval kosong = fallback perilaku lama. Baris Ritual sudah
   me-recompose tiap detik (untuk countdown), jadi uncheck terjadi
   tepat waktu tanpa timer tambahan.
5. **Header interval form dihapus.** Blok "✨ Daily Reminder — Interval
   Pengulangan" + paragraf penjelasnya DIHAPUS dari section interval di
   form Tambah/Edit (sesuai crop yang dikirim Rama); kontrol interval
   (nilai, satuan Detik/Menit/Jam, preset) tetap ada & berfungsi.
   Variabel `windowLabel` yang hanya dipakai paragraf itu ikut dibersihkan.
   (Interpretasi: yang dihapus = header di FORM, bukan section "Daily
   Reminder Hari Ini" di layar Harian — Rama bisa koreksi bila lain.)

### Hasil build ronde 11

- **Status: BUILD SUCCESSFUL** (clean assembleDebug, Gradle 8.7 offline,
  `keystore.properties` ADA -> signing permanen); hanya warning
  deprecation lama.
- **GERBANG LOLOS:** `apksigner verify --print-certs` -> Signer #1
  SHA-256 digest =
  `028531fbd84626544b825ce83fe1e9b05d7ced746637393a0313327af29fc090`
  — SAMA PERSIS dengan keystore permanen -> update timpa di atas v1.0.8
  aman.
- Terverifikasi: package `id.rama.todoplanner` versionCode **10** /
  versionName **1.0.9**, minSdk 26, targetSdk 34; permission tetap
  **TANPA INTERNET**; DB tetap versi 5 (tanpa migrasi);
  `ReminderSoundPlayer.kt` identik byte-per-byte dengan v1.0.3 (sha256
  `9441d784fde596fa8f1a3966a391aa76e01efe1dbe41b271f16de31c2138bc7e`)
  — getar tetap dibekukan.
- **Catatan jujur:** verifikasi di VM sebatas compile + signature +
  badging + isi zip. Perilaku runtime ronde 11 (posisi & flicker pill,
  terang glow, picker kotak via swatch mix, uncheck setengah countdown)
  terbukti lewat uji HP Rama berikutnya.
