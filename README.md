# Exam Lockdown Browser (mirip Safe Exam Browser)

Aplikasi Android (Kotlin) yang mengunci perangkat siswa hanya pada satu halaman
ujian, memblokir screenshot, split-screen, dan perpindahan aplikasi.

## Fitur

| Fitur | Mekanisme |
|---|---|
| Anti screenshot & screen recording | `WindowManager.LayoutParams.FLAG_SECURE` |
| Anti split-screen / multi-window | `android:resizeableActivity="false"` |
| Tidak muncul di Recent Apps | `android:excludeFromRecents="true"` |
| Kunci ke satu aplikasi (anti app-switching) | `startLockTask()` / Lock Task Mode |
| Blokir tombol Home, Recents, notification shade | `DevicePolicyManager.setLockTaskFeatures()` (butuh Device Owner) |
| Browser terbatas ke domain ujian | `WebViewClient.shouldOverrideUrlLoading` whitelist domain |
| Blokir copy-paste / popup window | Konfigurasi `WebSettings` |
| Watchdog anti-keluar | `KioskWatchdogService` |
| Keluar darurat oleh proktor | Dialog PIN 4 digit |

## Cara Build APK — TANPA install Android Studio

### Opsi A (paling direkomendasikan): GitHub Actions — build otomatis di cloud

Project ini sudah dilengkapi file `.github/workflows/build-apk.yml` yang akan
meng-compile APK secara otomatis di server GitHub. Anda hanya perlu akun
GitHub gratis dan browser.

1. Buat repository baru di https://github.com/new (boleh privat), misalnya
   `exam-lock-browser`.
2. Upload seluruh isi folder project ini ke repo tersebut. Caranya lewat
   browser: di halaman repo kosong, klik **"uploading an existing file"**,
   lalu seret semua file & folder hasil ekstrak ZIP, lalu klik **Commit changes**.
   (Pastikan struktur foldernya tetap sama persis, termasuk folder `.github`.)
3. Buka tab **Actions** di repo Anda. Workflow "Build Debug APK" akan otomatis
   berjalan setelah commit pertama. Jika tidak otomatis, klik workflow
   tersebut → tombol **Run workflow**.
4. Tunggu 3–6 menit hingga status berubah jadi centang hijau ✅.
5. Klik hasil run tersebut → scroll ke bagian **Artifacts** → unduh
   `ExamLockBrowser-debug-apk.zip`. Di dalamnya ada `app-debug.apk`.
6. Pindahkan APK ke perangkat Android (lewat Google Drive/kabel USB/dsb.) lalu
   install seperti biasa (aktifkan "Install dari sumber tidak dikenal" jika diminta).

Catatan: APK hasil Opsi A adalah **debug build** (sudah bisa langsung
diinstall & dites), bukan APK release yang di-signing untuk distribusi resmi
massal. Untuk keperluan ujian sungguhan skala besar, APK sebaiknya di-signing
(lihat Opsi C).

### Opsi B: Online IDE berbasis browser (tanpa install apa pun di komputer)

Layanan seperti **GitHub Codespaces** (gratis dengan kuota bulanan dari akun
GitHub) memberi Anda terminal Linux penuh di browser:

1. Di repo GitHub Anda (hasil Opsi A langkah 1–2), klik tombol hijau **Code**
   → tab **Codespaces** → **Create codespace on main**.
2. Setelah terminal berbasis VS Code terbuka di browser, jalankan:
   ```bash
   sdk install java 17.0.10-tem   # jika SDKMAN belum ada, lihat catatan di bawah
   gradle wrapper
   ./gradlew assembleDebug
   ```
   Atau cukup commit & push, lalu biarkan **Opsi A (GitHub Actions)** yang
   men-build — ini jauh lebih sederhana daripada mengatur SDK manual di Codespace.
3. Download APK hasilnya lewat panel file di sebelah kiri (klik kanan file →
   Download).

### Opsi C: Signed APK untuk distribusi resmi (tetap tanpa Android Studio)

Tambahkan langkah signing ke `build-apk.yml` (atau jalankan `jarsigner`/
`apksigner` manual) menggunakan keystore yang Anda buat sekali lewat `keytool`
(tersedia di Java, bisa dijalankan juga di Codespaces/Actions). Jika Anda mau,
saya bisa bantu susunkan workflow signing-nya — tinggal beri tahu.

### Opsi D: Android Studio (jika suatu saat ingin GUI lokal)

Lihat bagian sebelumnya di riwayat percakapan ini — Build > Generate Signed
Bundle/APK.

## ⚠️ Batasan penting

Android tidak mengizinkan aplikasi biasa memblokir total tombol Home/Recents
atau notification shade. Dua tingkat proteksi:

1. **Tanpa Device Owner** — memakai *screen pinning* standar. Tombol
   Home/Recents masih berpotensi memutus pin tergantung versi Android/OEM.
2. **Dengan Device Owner (lockdown penuh, direkomendasikan untuk ujian
   resmi)** — didaftarkan sekali lewat ADB pada perangkat yang baru di-factory
   reset:
   ```bash
   adb shell dpm set-device-owner com.examlock.browser/.ExamDeviceAdminReceiver
   ```
   Ini API resmi Android untuk aplikasi kios/MDM.

## Konfigurasi sebelum dipakai

Saat pertama kali dijalankan, aplikasi membuka `SetupActivity` untuk mengisi:
- **URL ujian**, mis. `https://ujian.sekolah.id/exam`
- **Domain yang diizinkan**, mis. `ujian.sekolah.id`
- **PIN keluar darurat 4 digit** untuk proktor

## Catatan etika & kepatuhan

- Informasikan secara jelas kepada siswa apa yang dipantau/dibatasi selama ujian.
- Pastikan sesuai kebijakan privasi & perlindungan data institusi Anda.
- Hanya pasang pada perangkat yang dikelola institusi, karena status Device
  Owner memberi kontrol signifikan atas perangkat.
