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
│   ├── ReminderScheduler.kt   # AlarmManager (exact bila diizinkan, fallback setAndAllowWhileIdle)
│   ├── ReminderReceiver.kt    # tampilkan notifikasi; suara = channel dari URI nada pilihan user
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
  nada (id channel = hash URI) supaya suara selalu mengikuti pilihan terakhir. Nada bisa diatur
  global (layar Reminder) dan per kegiatan (form); Detail menampilkan nada efektif kegiatan.
- **Reminder setelah reboot:** `BootReceiver` menjadwalkan ulang semua reminder saat `BOOT_COMPLETED`.
  Izin alarm eksak (`SCHEDULE_EXACT_ALARM`) dipakai bila tersedia; bila tidak, fallback
  `setAndAllowWhileIdle` (bisa bergeser beberapa menit oleh Doze Android — perilaku OS, bukan bug).
- **Export PDF:** digambar dengan `PdfDocument` native (header gradasi Langit Pagi, judul periode,
  ringkasan, tabel 22 baris/halaman, footer tanggal cetak + nomor halaman). API 29+: tersimpan di
  `Unduhan/TodoPlanner/` via MediaStore. API 26–28: tersimpan di folder eksternal aplikasi dan tetap
  bisa dibagikan. Tombol Bagikan memakai salinan cache + FileProvider (share intent).
- **Foto profil:** memakai photo picker Android (`PickVisualMedia`) — tanpa permission galeri.
  Foto disalin ke `filesDir/profile_photo.jpg`, ditampilkan lingkaran `ContentScale.Crop` (crop tengah)
  di semua avatar. Hapus Foto mengembalikan avatar ke inisial/emoji.
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

## Hasil Compile
- **Status: BERHASIL — `BUILD SUCCESSFUL` (Gradle 8.7, AGP 8.5.2, Kotlin 2.0.20), 23 Okt 2026.**
- **APK debug:** `app/build/outputs/apk/debug/app-debug.apk` — **16.788.744 byte (±16,0 MB)**,
  sha256 `9de295a05095117af3676a63368401e7c92679e9a9ee447199721d083ddf1c4c`.
  Salinan praktis juga ada di `../TodoPlanner-debug.apk` (isi identik, hash sama).
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
