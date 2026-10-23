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
<!-- Diisi setelah build: status, path APK, ukuran. -->
