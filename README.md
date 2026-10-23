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

## Checklist Fitur vs Mockup Final (14 layar)
| # | Layar/Fitur mockup | Status di aplikasi |
|---|---|---|
| 1 | Beranda: sapaan jam HP (04–11 pagi, 11–15 siang, 15–19 sore, 19–04 malam), "Halo, [nama]!", tanggal & jam HP, ring progres, kegiatan berikutnya, reminder terdekat, header TANPA pintu Profil | ✅ |
| 2 | Template Harian: time-block jam 04–22 (slot kosong bisa diketuk untuk isi) + checklist tanpa jam | ✅ |
| 3 | Template Mingguan: 7 hari Senin–Minggu, tambah per hari | ✅ |
| 4 | Template Bulanan: grid bulan + daftar tanggal terpilih | ✅ |
| 5 | Template Tahunan: 4 kuartal + target + meter progres, tambah/edit/hapus target, tombol +/− progres | ✅ |
| 6 | Kalender: mode Bulan & Tahun (java.time), titik warna kategori, penanda hari ini, tap tanggal → daftar + tambah | ✅ |
| 7 | Tambah/Edit: judul, tanggal (DatePicker), jam mulai–selesai (TimePicker), template tujuan, ulangi, reminder, nada dering, prioritas, kategori, catatan | ✅ |
| 8 | Reminder global: izin notifikasi Android 13+, nada global via ringtone picker, daftar reminder terdekat | ✅ |
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
- **Kegiatan Berikutnya & Reminder Terdekat = kartu TERPISAH** berikon judul
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
  "Titik warna = kategori:"; Checklist jadi kartu terpisah
  "✅ Checklist Hari Ini"; tombol bawah GradientButton "+ Isi Kegiatan di
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
| 3 | 1.0.2 | Ronde 4 (#89): PDF deskripsi + multi-halaman, getar berlapis + channel v4 + diagnostik, Reminder Terdekat 1 entri/kegiatan, layar bangun (fullScreenIntent) |

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

### 3. Reminder Terdekat: satu entri per kegiatan
- Penyebab lama: daftar diekspansi per HARI (14 hari × semua kegiatan),
  jadi dua kegiatan harian berulang (Doa & tes 1, sama-sama 08.45) memenuhi
  daftar berselang-seling.
- Kini memakai `List<ActivityEntity>.nextReminders(now, limit)` (domain):
  SATU entri per kegiatan = kemunculan berikutnya saja, urut waktu trigger
  naik, tie-break judul (lowercase). Berlaku di layar Reminder (maks 10)
  DAN kartu Reminder Terdekat di Beranda (maks 3). Seri lengkap tetap di
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
  Reminder Terdekat baru terbukti lewat uji HP Rama berikutnya — untuk
  getar, baca baris "Diagnostik getar:" di layar Reminder setelah Tes
  Reminder agar uji berbasis bukti.

