<!-- AUTO_META_START -->
# Ultimate Cleaner — Root Edition (by Gendev)

> **Pembersih & pengoptimal Android Root (`su -c`) & Non-Root oleh Gendev — dilengkapi WhatsApp Deep Root Cleaner aman akun, Anti-Recovery Shredder, Zero-Trace, AI Storage Hub, dan Pembersih Otomatis.**

- **Versi Rilis Terbaru:** `v1.0` (siap build otomatis via GitHub Actions)
- **Target Perangkat Optimal:** Android 8.0+ (teruji untuk OPPO A16 / Android 11)
- **Download APK Terbaru:** Cek tab **Releases** atau **Actions → Build & Release Android APK** di repositori GitHub ini
<!-- AUTO_META_END -->

---

## Fitur Utama

- **Dual Mode (Root & Non-Root)**
  - **Root (`su -c`)**: Pembersihan mendalam cache semua aplikasi (`/data/data`, `/data/user/0`), Dalvik/ART (`fstrim`, `pm trim-caches`, `cmd package bg-dexopt-job`), log sistem, tombstones, dan folder sampah tersembunyi.
  - **Non-Root**: Akses aman via **Storage Access Framework (SAF)**, **Usage Stats**, dan API native Android tanpa memerlukan akses root.
- **Legacy & WhatsApp Khusus (Root) — Akun Terkunci Aman**
  - Memindai seluruh media & cache WhatsApp / WhatsApp Business (`/sdcard/WhatsApp*`, `/sdcard/Android/media/com.whatsapp*`, `/data/user/0/com.whatsapp*/cache`, `code_cache`, `app_webview`, `files/Logs`, `files/ViewOnce`, `files/Stickers`, `files/Avatars`).
  - **Proteksi Ketat Akun & Chat DB**: `shared_prefs`, `databases`, `Databases`, `Backups`, `keystore`, `no_backup`, `*.db*`, `*.crypt*`, `me`, `key`, `rc2`, `secret`, `pw`, `backup_token`, dan `noise_key` selalu dikunci agar akun & riwayat chat tidak pernah terhapus.
- **Hapus Permanen Anti-Recovery & Mode Tanpa Riwayat (Zero-Trace)**
  - Menimpa blok data dengan nol (`dd if=/dev/zero` / `RandomAccessFile`) dengan perintah POSIX/`mksh` aman, melakukan `sync`, memutus ukuran file (`truncate`), mengganti nama acak sebelum unlink, serta menghapus jejak `.thumbnails`/`.Trash` dan indeks *MediaStore* Galeri.
  - Mode **Tanpa Riwayat (Zero-Trace)** menjaga riwayat aplikasi dan log terminal root bersih seketika setelah operasi selesai.
- **Opsi Pilih Semua di Semua Pembersih**
  - Kontrol **`Pilih Semua / Batal Pilih Semua`** tersedia di Folder Legacy, WhatsApp Root, Smart Junk, Per-App Cache, Residu & APK, File Besar & Duplikat, serta AI Storage Hub.
- **AI Storage Intelligence Hub & Autonomus Cleaner**
  - Prediksi tren penuhnya penyimpanan, pembersih kemiripan visual (foto buram/duplikat), *Context Triage*, *Ghost File Radar*, *Pre-Launch Optimizer*, dan pembersihan otomatis terjadwal / layar mati / penjaga penyimpanan rendah.

---

## Cara Mengubah Kode GitHub Menjadi APK Otomatis

Repositori ini sudah dilengkapi workflow **GitHub Actions** (`.github/workflows/build-apk.yml`) yang otomatis berjalan setiap kali ada pembaruan di-push ke branch `main` atau `master`:

1. **Aktifkan Izin Write untuk GitHub Actions (1 Kali Saja):**
   - Buka repositori Anda di GitHub → **Settings** → **Actions** → **General**.
   - Pada bagian **Workflow permissions**, pilih **Read and write permissions**, lalu klik **Save**.
   *(Ini diperlukan agar GitHub Actions bisa langsung membuat **GitHub Releases** berisi `.apk` sekaligus memperbarui `README.md` secara otomatis.)*
2. **Push Pembaruan atau Jalankan Manual:**
   - Setiap kali Anda push ke `main`/`master` (atau klik tab **Actions** → **Build & Release Android APK** → **Run workflow**), GitHub akan:
     - Menjalankan unit test (`gradle :app:testDebugUnitTest`).
     - Memulihkan keystore konsisten dari `debug.keystore.base64` dan membangun file `.apk` (`gradle :app:assembleDebug`).
     - Menyatukan **Judul**, **Deskripsi**, **SHA-256**, dan **Riwayat Perubahan** langsung ke **GitHub Releases** dan **`README.md`**.
3. **Download File APK Hasil Build:**
   - Cara termudah: Buka menu **Releases** di halaman utama repositori GitHub Anda → unduh file `.apk` pada rilis paling atas → pasang langsung di HP Android Anda.
   - Alternatif: Buka tab **Actions** → klik workflow terbaru yang centang hijau → unduh **Artifacts** `UltimateCleaner-APK-...`.
4. *(Opsional) Menambahkan Release Upload Key Sendiri:*
   - Jika ingin menandatangani varian `release`, tambahkan 3 **Repository Secrets** di **Settings → Secrets and variables → Actions**:
     - `KEYSTORE_BASE64`: isi base64 dari file `.jks` dengan alias `upload`
     - `STORE_PASSWORD`: password keystore
     - `KEY_PASSWORD`: password key alias `upload`

---

## Integrasi Otomatis Judul, Deskripsi & `README.md`

Setiap kali Anda melakukan **commit** atau menjalankan workflow manual:
- **Baris pertama pesan commit** (atau input *Judul* pada *Run workflow*) otomatis menjadi **Judul Rilis** di GitHub Releases dan **Judul Pembaruan Terakhir** di `README.md`.
- **Baris lanjutan pesan commit** (atau `description` pada `metadata.json`) otomatis masuk sebagai **Deskripsi Rilis** dan catatan pembaruan.
- **Nama & Deskripsi Aplikasi** disinkronkan langsung dari `metadata.json`.

<!-- AUTO_CHANGELOG_START -->
### Rilis Terbaru: Menunggu Build Pertama di GitHub Actions

Setelah Anda mengunggah repositori ini ke GitHub dan mengaktifkan GitHub Actions, bagian ini akan terisi otomatis oleh setiap build APK baru lengkap dengan tautan unduhan, checksum SHA-256, judul rilis, deskripsi, dan 10 commit terakhir.
<!-- AUTO_CHANGELOG_END -->
