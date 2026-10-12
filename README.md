# BLE Pelacak — Bluetooth Proximity Tracker

Aplikasi Android untuk memindai sinyal advertising perangkat **Bluetooth Low Energy (BLE)** di sekitar secara real-time, memantau kekuatan sinyal (RSSI), dan melacak kedekatan satu perangkat target menggunakan visualisasi radar berbasis zona jarak.

> Study Case — Mobile Engineer · Repository: https://github.com/ajienergystar/pelacakBluetoothAndroid

| | |
|---|---|
| **Platform** | Android 8.0 (API 26) – Android 16 (API 36) |
| **Bahasa** | Kotlin 2.2 |
| **UI** | Jetpack Compose + Material 3 |
| **Arsitektur** | MVVM + Clean Architecture, Hilt DI |
| **Penyimpanan lokal** | Room (SQLite) |
| **Diuji di** | Samsung Galaxy A14 (SM-A145F), Android 15 / One UI 7.0 |

---

## Daftar Isi

1. [Fitur](#fitur)
2. [Download APK](#download-apk)
3. [Setup & Menjalankan Aplikasi](#setup--menjalankan-aplikasi)
4. [Cara Penggunaan](#cara-penggunaan)
5. [Izin Aplikasi](#izin-aplikasi)
6. [Arsitektur](#arsitektur)
7. [Alur Data](#alur-data)
8. [Library & Alasan Pemilihan](#library--alasan-pemilihan)
9. [Pemetaan RSSI → Zona & Jarak](#pemetaan-rssi--zona--jarak)
10. [Error Handling & Resilience](#error-handling--resilience)
11. [App Lifecycle](#app-lifecycle)
12. [Skema Database](#skema-database)
13. [Testing](#testing)
14. [Status Pengujian](#status-pengujian)
15. [Troubleshooting](#troubleshooting)
16. [Known Issues / Keterbatasan](#known-issues--keterbatasan)
17. [Asumsi Teknis & Kendala](#asumsi-teknis--kendala)
18. [Riwayat Commit](#riwayat-commit)
19. [Pengembangan Selanjutnya](#pengembangan-selanjutnya)

---

## Fitur

### Screen 1 — Dashboard Scanner
- Tombol **Start / Stop** pemindaian manual (FAB).
- Daftar perangkat real-time: **nama** (jika ada), **MAC address**, **RSSI mentah (raw dBm)**, **kategori sinyal**, dan **estimasi jarak**.
- **Urut otomatis** berdasarkan sinyal terkuat (RSSI tertinggi). Perangkat yang sinyalnya hilang turun ke bawah dan disembunyikan setelah 30 detik.
- **Pencarian** berdasarkan nama atau MAC address (boleh tanpa titik dua, mis. `a4c138`).
- **Filter ambang sinyal minimum** (≥ -60 / -70 / -80 / -90 dBm) dan filter **"Bernama saja"**.
- Filter tersimpan di `SavedStateHandle` → bertahan saat rotasi maupun process death.

### Screen 2 — Detail Pelacakan (Radar View)
- **Radar animasi**: cincin batas zona (1 m, 3 m, 10 m, 20 m) dengan skala logaritmik, titik target bergerak halus mengikuti estimasi jarak.
- Warna & kecepatan pulse **berubah mengikuti kategori sinyal** (hijau → kuning → oranye → merah → abu-abu saat hilang).
- Kartu zona: kategori, estimasi jarak, rentang jarak zona, RSSI raw vs RSSI terfilter, dan **arah pergerakan** (Mendekat / Menjauh / Stabil).
- **Grafik RSSI** (raw = titik, terfilter = garis) dengan garis batas zona.
- **Detail stabilitas & koneksi**: standar deviasi sinyal (Stabil / Cukup stabil / Fluktuatif), laju paket advertising per detik, terakhir terlihat, Tx Power, connectable, vendor (Bluetooth SIG Company ID), Service UUID, data historis dari database.
- Deteksi **sinyal hilang/terputus** (tanpa paket > 6 detik) dengan banner "Terus mencari…", lalu otomatis pulih saat perangkat muncul lagi.
- **Haptic feedback** (bisa dimatikan) setiap target masuk ke zona yang lebih dekat — berguna untuk mencari barang ("hot & cold").

### Screen 3 — Riwayat Perangkat (History Log)
- Semua perangkat yang pernah terdeteksi tersimpan di **Room** dan tetap ada setelah aplikasi ditutup.
- Menampilkan waktu terakhir ditemukan (relatif & absolut), RSSI terakhir, jumlah sesi scan perangkat terdeteksi, dan vendor.
- Pencarian, hapus per item, dan hapus semua (dengan konfirmasi).
- Tap item untuk membuka Radar perangkat tersebut.

---

## Download APK

APK siap uji tersedia di halaman **[Releases](https://github.com/ajienergystar/pelacakBluetoothAndroid/releases)**.

1. Download `app-release.apk` (atau `app-debug.apk`) ke HP Android.
2. Buka file tersebut, izinkan **Instal aplikasi yang tidak dikenal** untuk aplikasi File Manager / browser jika diminta.
3. Install, lalu buka aplikasi **BLE Pelacak**.

Atau via `adb`:
```bash
adb install -r app-release.apk
```

---

## Setup & Menjalankan Aplikasi

### Prasyarat
| Tool | Versi |
|---|---|
| Android Studio | Versi terbaru yang mendukung AGP 9.2 |
| JDK | 21 (bawaan Android Studio) |
| Android SDK | Platform 36 |
| Perangkat uji | **HP Android fisik** dengan Bluetooth LE, Android 8.0 (API 26) ke atas |

> Emulator Android tidak memiliki radio Bluetooth sungguhan, jadi pemindaian BLE harus diuji di perangkat fisik.

### Langkah
```bash
git clone https://github.com/ajienergystar/pelacakBluetoothAndroid.git
cd pelacakBluetoothAndroid
```
1. Buka folder project di Android Studio → tunggu **Gradle Sync** selesai.
2. Aktifkan **Developer Options → USB debugging** di HP, sambungkan via USB (atau Wireless debugging).
3. Pilih konfigurasi `app` lalu **Run ▶**.

### Build APK lewat terminal
```bash
# APK debug
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# APK release (ditandatangani debug keystore agar langsung bisa di-install untuk review)
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk

# Install langsung ke HP yang terhubung
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Menjalankan unit test
```bash
./gradlew testDebugUnitTest
```

---

## Cara Penggunaan

1. **Buka aplikasi** → tab **Scanner** terbuka sebagai layar utama.
2. Tekan **Start**. Saat pertama kali, Android meminta izin **"Perangkat di sekitar"** (Android 12+) atau **Lokasi** (Android 11 ke bawah) → pilih **Izinkan**.
3. Jika Bluetooth mati, banner muncul dengan tombol **Nyalakan**.
4. Daftar perangkat muncul dan terus diperbarui; perangkat dengan sinyal terkuat berada paling atas.
5. Gunakan **kolom pencarian** (nama / MAC) dan **chip filter** (≥ -60/-70/-80/-90 dBm, "Bernama saja") untuk menyaring daftar. Tombol **Reset filter** mengembalikan semua.
6. **Tap salah satu perangkat** untuk masuk ke **Radar**:
   - Bergeraklah sambil memperhatikan zona, angka jarak, dan indikator **▲ Mendekat / ▼ Menjauh**.
   - HP bergetar setiap kali target masuk ke zona yang lebih dekat (bisa dimatikan lewat ikon getar di kanan atas).
7. Buka tab **Riwayat** untuk melihat semua perangkat yang pernah terdeteksi beserta waktu terakhir ditemukan. Tap item untuk membuka Radar-nya, ikon ✕ untuk menghapus satu item, atau ikon tempat sampah untuk menghapus semua.
8. Tekan **Stop** untuk menghentikan pemindaian. Menekan tombol Home juga otomatis menjeda scan, dan scan berlanjut saat aplikasi dibuka kembali.

---

## Izin Aplikasi

| Izin | Versi Android | Alasan |
|---|---|---|
| `BLUETOOTH_SCAN` (`neverForLocation`) | 12+ (API 31+) | Memindai advertising BLE tanpa menurunkan lokasi pengguna |
| `BLUETOOTH_CONNECT` | 12+ (API 31+) | Membaca nama perangkat & meminta user menyalakan Bluetooth |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | ≤ 11 (API ≤ 30) | Diwajibkan sistem untuk scan BLE pada Android lama |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | ≤ 11 (API ≤ 30) | Izin Bluetooth legacy |

Fitur `android.hardware.bluetooth_le` dideklarasikan `required="true"`, sehingga aplikasi hanya bisa di-install di perangkat yang mendukung BLE.

---

## Arsitektur

Pola **MVVM + Clean Architecture ringan** dengan **unidirectional data flow** dan **Hilt** untuk Dependency Injection.

```
┌──────────────────────────── UI (Jetpack Compose) ────────────────────────────┐
│  ScannerScreen        RadarScreen          HistoryScreen                     │
│       │ state ↑  event ↓    │                    │                           │
│  ScannerViewModel     RadarViewModel        HistoryViewModel   (@HiltViewModel)
└───────┼─────────────────────┼────────────────────┼───────────────────────────┘
        ▼                     ▼                    ▼
┌──────────────────────────── Domain (pure Kotlin) ────────────────────────────┐
│  ScanRepository / HistoryRepository (interface)                              │
│  BleDevice, ScanState, ScanError, SignalZone, DeviceFilter                   │
│  DistanceEstimator, RssiKalmanFilter, SignalAnalyzer                         │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   ▼
┌──────────────────────────────── Data ────────────────────────────────────────┐
│  DefaultScanRepository ──► BleScanner (AndroidBleScanner, callbackFlow)      │
│        │                ──► BleEnvironment (izin, status BT & lokasi)        │
│        └──────────────► DeviceDao (Room)  ◄── RoomHistoryRepository          │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Struktur package
```
com.example.mobilepelacak
├── MobilePelacakApp.kt          # @HiltAndroidApp, daftar observer lifecycle proses
├── MainActivity.kt              # @AndroidEntryPoint, host Compose
├── di/                          # Modul Hilt (Database, Binding, Coroutine scope)
├── domain/
│   ├── model/                   # BleDevice, SignalZone, ScanState, ScanError, DeviceFilter, HistoryDevice
│   ├── repository/              # Kontrak repository + TimeProvider
│   └── signal/                  # DistanceEstimator, RssiKalmanFilter, SignalAnalyzer
├── data/
│   ├── ble/                     # BleScanner, AndroidBleScanner, BleEnvironment, AndroidBleEnvironment
│   ├── local/                   # Room: AppDatabase, DeviceDao, DeviceEntity
│   └── repository/              # DefaultScanRepository, RoomHistoryRepository
├── lifecycle/                   # ScanLifecycleObserver (ProcessLifecycleOwner)
└── ui/
    ├── scanner/ radar/ history/ # Screen + ViewModel per fitur
    ├── components/              # Komponen sinyal, banner status, handler izin
    ├── navigation/              # NavHost + bottom navigation
    └── common/ theme/
```

### Keputusan desain penting
- **Single source of truth**: `DefaultScanRepository` (singleton) memegang `StateFlow<ScanState>`. Semua layar membaca state yang sama, sehingga Scanner dan Radar selalu sinkron dan scan tidak terputus saat berpindah layar.
- **Thread confinement**: state internal repository (job scan, filter Kalman, buffer tulis) hanya diakses dari `CoroutineScope` dengan dispatcher `Dispatchers.Default.limitedParallelism(1)`. Aman tanpa `synchronized`/`Mutex`, dan callback BLE yang sangat sering tidak membebani main thread.
- **Scanner sebagai cold `Flow`** (`callbackFlow`): scan otomatis berhenti (`stopScan`) saat collector dibatalkan, jadi tidak ada callback yang bocor.
- **Persistensi batch**: hasil scan ditulis ke Room tiap 3 detik (dan saat scan berhenti), bukan per paket advertising yang bisa puluhan per detik.
- **Domain bebas Android SDK** → logika sinyal, filter, dan repository bisa di-unit test di JVM.
- **UI di-throttle**: daftar perangkat di-*sample* tiap 500 ms supaya urutan list tidak berubah terus-menerus, sementara status kontrol (Start/Stop, error) tetap instan.

---

## Alur Data

```
BluetoothLeScanner ──ScanCallback──► AndroidBleScanner (callbackFlow<Advertisement>)
                                           │
                                           ▼
                         DefaultScanRepository (single-thread scope)
                         • Kalman filter per perangkat
                         • gabung nama / Tx power / vendor
                         • simpan 90 sampel RSSI terakhir
                         • StateFlow<ScanState>  ───────────────► ScannerViewModel / RadarViewModel
                         • batch tulis tiap 3 dtk ──► Room ──Flow──► HistoryViewModel / RadarViewModel
                                           ▲
BroadcastReceiver (status Bluetooth) ──────┘   ProcessLifecycleOwner (foreground/background) ──┘
```

1. User menekan **Start** → `ScannerViewModel.startScan()` → `ScanRepository.startScan()`.
2. Repository melakukan *preflight* (izin, Bluetooth, lokasi, throttle), lalu meng-collect `BleScanner.scan()`.
3. Setiap paket advertising diproses (filter Kalman, histori), lalu `StateFlow<ScanState>` di-update.
4. ViewModel menggabungkan state dengan filter & ticker waktu menjadi `UiState` yang di-render Compose.
5. Perubahan perangkat dikumpulkan lalu ditulis ke Room secara batch; layar Riwayat membaca dari Room via `Flow`.

---

## Library & Alasan Pemilihan

| Library | Kegunaan | Alasan |
|---|---|---|
| **Kotlin + Coroutines/Flow** | Bahasa & async | Standar modern Android; `callbackFlow` cocok membungkus callback BLE, `StateFlow` untuk state UI |
| **Jetpack Compose + Material 3** | UI | Deklaratif, cocok untuk UI yang sering berubah (RSSI real-time); `Canvas` untuk radar & grafik tanpa library chart tambahan |
| **Navigation Compose** | Navigasi 3 layar | Back stack & argumen (`address`) terkelola, terintegrasi dengan `SavedStateHandle` |
| **Hilt (Dagger)** | Dependency Injection | DI resmi Android, compile-time safe, integrasi langsung dengan ViewModel & Application |
| **Room** | Database lokal riwayat | Abstraksi SQLite resmi, query diverifikasi saat compile, mendukung `Flow` untuk UI reaktif |
| **KSP** | Annotation processing | Lebih cepat dari kapt, wajib untuk AGP 9 (built-in Kotlin) |
| **Lifecycle (ViewModel, runtime-compose, process)** | State & lifecycle | `collectAsStateWithLifecycle`, `ProcessLifecycleOwner` untuk pause/resume scan |
| **Material Icons Extended** | Ikon | Ikon Bluetooth, getar, dsb. |
| **JUnit 4 + kotlinx-coroutines-test** | Unit test | Waktu virtual (`advanceTimeBy`) untuk menguji throttling & persistensi tanpa menunggu sungguhan |

Library BLE pihak ketiga (mis. Nordic, RxAndroidBle) sengaja **tidak** dipakai: kebutuhan hanya scanning (tanpa GATT), sehingga API `BluetoothLeScanner` bawaan sudah cukup dan dependency tetap minimal.

---

## Pemetaan RSSI → Zona & Jarak

| RSSI (dBm) | Kategori | Perkiraan Jarak | Warna |
|---|---|---|---|
| -10 s/d -30 | Sangat Kuat (Sangat Dekat) | < 1 m | Hijau |
| -30 s/d -50 | Kuat (Dekat) | 1 – 3 m | Hijau muda |
| -50 s/d -70 | Cukup / Baik | 3 – 10 m | Kuning |
| -70 s/d -80 | Lemah | 10 – 20 m | Oranye |
| -80 s/d -90 | Sangat Lemah / Putus-putus | > 20 m | Merah |
| < -90 / tidak ada paket > 6 dtk | Sinyal Hilang (Lost) | Di luar jangkauan | Abu-abu |

Batas atas setiap zona bersifat inklusif (mis. -30 dBm = Sangat Kuat).

**Estimasi jarak numerik** memakai model *log-distance path loss*:

```
d = 10 ^ ((P − RSSI) / (10 · n))      P = −32 dBm, n = 3.8
```

Konstanta dikalibrasi supaya angka jarak **konsisten dengan tabel di atas** (-30 dBm ≈ 0,9 m, -50 dBm ≈ 3 m, -70 dBm ≈ 10 m, -80 dBm ≈ 18 m).

**Peredaman noise**: RSSI mentah berfluktuasi ±5–10 dBm walau perangkat diam. Zona dan jarak dihitung dari RSSI hasil **Kalman filter 1D** (Q = 0,5, R = 4) agar indikator tidak berkedip, sementara nilai **raw** tetap ditampilkan sesuai spesifikasi. Filter di-reset otomatis saat perangkat muncul kembali setelah hilang.

---

## Error Handling & Resilience

| Skenario | Penanganan |
|---|---|
| Izin ditolak | Banner penjelasan + tombol **Berikan Izin**; bila ditolak permanen ("Don't ask again") tombol berubah menjadi **Buka Pengaturan**. Status dicek ulang saat kembali ke aplikasi. |
| Bluetooth dimatikan (sebelum/saat scan) | Scan berhenti dengan aman, banner + tombol **Nyalakan** (`ACTION_REQUEST_ENABLE`). Status Bluetooth dipantau via `BroadcastReceiver`; scan **otomatis dilanjutkan** saat Bluetooth menyala lagi. |
| Location Services mati (Android ≤ 11) | Banner + tombol menuju pengaturan lokasi; scan lanjut setelah lokasi aktif. |
| Perangkat target terputus / berhenti advertising | Setelah 6 detik tanpa paket → status **Sinyal Hilang**, radar menyembunyikan titik, banner "Terus mencari…"; pulih otomatis saat sinyal kembali. |
| `onScanFailed` (registration failed, internal error, dll.) | Kode error dipetakan ke pesan yang bisa dipahami + tombol **Coba Lagi**. |
| Batas Android 5× start scan / 30 detik | Repository melacak waktu start; jika melewati batas, start **ditunda otomatis** dengan banner hitung mundur (Android sendiri akan diam-diam tidak mengirim hasil). |
| Perangkat tanpa BLE | `uses-feature bluetooth_le required=true` + pengecekan runtime. |
| Izin dicabut saat scan berjalan / adapter mati di tengah `startScan` | `SecurityException` & `IllegalStateException` ditangkap dan dipetakan ke error. |
| Gagal menulis database | Ditangkap & di-log; scan tetap berjalan. |

---

## App Lifecycle

| Kejadian | Perilaku |
|---|---|
| **Rotasi layar / config change** | Scan **tetap berjalan tanpa restart**. State ada di repository singleton & ViewModel; `ProcessLifecycleOwner` tidak mengirim `ON_STOP` untuk config change. |
| **App ke background** (Home, pindah app, layar mati) | Scan **dijeda** untuk hemat baterai dan mematuhi batasan background Android; data dari scanner di-flush ke Room. |
| **App kembali ke foreground** | Scan **dilanjutkan otomatis** bila sebelumnya aktif. |
| **Process death** | Filter & query tersimpan via `SavedStateHandle`; riwayat aman di Room. Scan perlu di-start ulang oleh user (disengaja). |
| Pindah layar Scanner ↔ Radar ↔ Riwayat | Scan tidak terputus (state dimiliki repository, bukan screen). |

---

## Skema Database

Database: `mobile_pelacak.db` (Room, versi 1) — tabel `devices`:

| Kolom | Tipe | Keterangan |
|---|---|---|
| `address` | TEXT (PK) | MAC address perangkat |
| `name` | TEXT? | Nama terakhir yang diketahui (tidak ditimpa nilai kosong) |
| `last_rssi` | INTEGER | RSSI terakhir (dBm) |
| `best_rssi` | INTEGER | RSSI terkuat yang pernah tercatat |
| `first_seen_at` | INTEGER | Epoch millis pertama kali terdeteksi |
| `last_seen_at` | INTEGER (index) | Epoch millis terakhir terdeteksi |
| `session_count` | INTEGER | Jumlah sesi scan perangkat ini terdeteksi |
| `manufacturer_id` | INTEGER? | Bluetooth SIG Company ID dari manufacturer data |

---

## Testing

Unit test (JVM) di `app/src/test`:

| Test | Cakupan |
|---|---|
| `SignalZoneTest` | Pemetaan RSSI → zona sesuai tabel, termasuk nilai batas |
| `DistanceEstimatorTest` | Monotonic, konsistensi dengan rentang zona, format |
| `RssiKalmanFilterTest` | Peredaman noise, konvergensi, reset |
| `DeviceFilterTest` | Pencarian nama/MAC, ambang RSSI, bernama saja, urutan, perangkat hilang |
| `SignalAnalyzerTest` | Trend mendekat/menjauh, stabilitas, laju paket |
| `DefaultScanRepositoryTest` | Start/stop, persistensi & merge sesi, Bluetooth on/off + auto-resume, izin ditolak, lokasi mati, scan gagal, pause/resume background, throttling |

```bash
./gradlew testDebugUnitTest
```

---

## Status Pengujian

| Item | Hasil |
|---|---|
| Unit test (`./gradlew testDebugUnitTest`) | ✅ 36 test lolos |
| Build & install ke perangkat fisik | ✅ Samsung Galaxy A14 (SM-A145F), Android 15, One UI 7.0 |

Skenario manual yang disarankan untuk reviewer:
- Start scan → daftar terisi & terurut; ubah filter/pencarian.
- Matikan Bluetooth dari Quick Settings saat scan berjalan → banner muncul; nyalakan lagi → scan lanjut otomatis.
- Tolak izin → banner "Berikan Izin"; tolak permanen → tombol "Buka Pengaturan".
- Buka Radar sebuah perangkat lalu matikan perangkat tersebut → status "Sinyal Hilang" setelah ±6 detik.
- Putar layar saat scan → scan tetap berjalan; tekan Home lalu kembali → scan dijeda & dilanjutkan.
- Tutup aplikasi sepenuhnya, buka lagi → data di tab Riwayat tetap ada.

---

## Troubleshooting

| Masalah | Solusi |
|---|---|
| Daftar perangkat kosong padahal scan berjalan | Pastikan ada perangkat BLE aktif di sekitar (smartwatch, earbuds, HP lain dengan Bluetooth menyala). Pada beberapa vendor, nyalakan juga **Lokasi**. |
| HP tidak terdeteksi Android Studio (Samsung) | Aktifkan **Opsi pengembang → Debugging USB**, pilih mode USB **Mentransfer file**, tap **Izinkan** di popup. Matikan **Pemblokir Otomatis** (One UI 6+) dan **Blokir koneksi USB saat terkunci** bila ada. |
| `adb devices` menampilkan `unauthorized` | Buka kunci HP, cabut-colok kabel, lalu tap **Izinkan**; atau *Revoke USB debugging authorizations* lalu ulangi. |
| Error "Scan terlalu sering" / banner hitung mundur | Android membatasi 5× start scan per 30 detik; tunggu hitung mundur selesai, scan akan mulai otomatis. |
| "Gagal mendaftarkan scanner ke stack Bluetooth" | Matikan lalu nyalakan Bluetooth, kemudian tekan **Coba Lagi**. |
| Gradle Sync gagal | Gunakan JDK 21 bawaan Android Studio (Settings → Build Tools → Gradle → Gradle JDK) dan pastikan Android SDK Platform 36 ter-install. |

---

## Known Issues / Keterbatasan

1. **Estimasi jarak hanya perkiraan.** RSSI dipengaruhi daya pancar perangkat, orientasi antena, tubuh manusia, dinding, dan pantulan. Akurasi realistis ±50% atau lebih; zona lebih dapat diandalkan daripada angka meter.
2. **Radar tidak menunjukkan arah.** RSSI dari satu antena tidak memberi informasi arah; posisi sudut titik di radar hanya dekoratif (konsisten per perangkat), yang bermakna adalah jarak dari pusat. Gunakan indikator "Mendekat/Menjauh" sambil bergerak.
3. **MAC address acak (privacy randomization).** iPhone, Android modern, dan banyak wearable mengganti alamat secara berkala, sehingga satu perangkat fisik bisa muncul sebagai beberapa entri di riwayat dan pelacakan dapat "hilang" saat alamat berganti.
4. **Nama perangkat sering kosong.** Banyak perangkat tidak menyertakan nama di paket advertising (ditampilkan "Perangkat tanpa nama").
5. **Beacon tertentu tidak muncul di Android 12+.** Karena izin `BLUETOOTH_SCAN` memakai flag `neverForLocation`, sistem memfilter sebagian paket beacon (mis. iBeacon). Ini trade-off agar aplikasi tidak perlu izin lokasi.
6. **Scan tidak berjalan di background** (disengaja). Pemindaian berkelanjutan di background butuh Foreground Service + notifikasi, di luar cakupan tugas.
7. **Interval advertising perangkat bervariasi** (20 ms – 10 dtk). Perangkat yang jarang advertising bisa sesekali terdeteksi sebagai "hilang" walau masih dekat (timeout 6 detik).
8. Beberapa vendor (mis. sebagian Xiaomi/Oppo) memerlukan **Location Services aktif** walau di Android 12+; jika daftar kosong, coba nyalakan lokasi.

---

## Asumsi Teknis & Kendala

- **Hanya scanning, tanpa koneksi GATT.** "Terputus" diartikan sebagai berhentinya paket advertising dari perangkat target. "Terkoneksi sebelumnya" pada riwayat diartikan sebagai "pernah terdeteksi".
- **minSdk 26 (Android 8.0)** — mencakup mayoritas perangkat aktif dan memberi akses ke `ScanResult.isConnectable/txPower` tanpa compat layer.
- RSSI yang dipakai untuk **urutan, filter ambang, zona, dan jarak** adalah RSSI terfilter (dibulatkan) agar stabil; nilai **raw** tetap ditampilkan di daftar dan radar.
- Konstanta estimasi jarak (`P = -32 dBm`, `n = 3.8`) dipilih agar sesuai tabel kategori di spesifikasi, bukan hasil kalibrasi per perangkat. Untuk akurasi lebih, nilai ini bisa dikalibrasi per lingkungan (ukur RSSI pada 1 m).
- Riwayat disimpan per MAC address (primary key), dengan merge: nama tidak ditimpa nilai kosong, `first_seen` dipertahankan, `best_rssi` = tertinggi, `session_count` bertambah sekali per sesi scan.
- Project memakai **AGP 9 (built-in Kotlin)**, sehingga dipilih versi KSP (2.3.6) dan Hilt (2.59.2) yang sudah mendukung AGP 9.
- APK release ditandatangani dengan debug keystore semata-mata agar reviewer bisa langsung meng-install; untuk produksi gunakan keystore sendiri.

---

## Riwayat Commit

Commit dibuat bertahap per lapisan agar mudah direview:

| Commit | Isi |
|---|---|
| `chore` | Project awal dari template Android Studio (Empty Compose Activity) |
| `build` | Dependency Hilt, Room, KSP, Navigation, Lifecycle |
| `feat(domain)` | Zona sinyal, estimasi jarak, Kalman filter, filter & pengurutan perangkat |
| `feat(data)` | BLE scanner, monitor status Bluetooth, Room, repository + DI |
| `feat(ui)` | Layar Scanner, Radar, Riwayat + navigasi |
| `test` | Unit test domain & repository, hardening receiver Bluetooth |
| `docs` | README |

Lihat detail: `git log --oneline`.

---

## Pengembangan Selanjutnya

- **Foreground Service** agar pelacakan tetap berjalan di background dengan notifikasi.
- **Kalibrasi jarak per perangkat** (ukur RSSI di 1 m) dan penyimpanan konstanta kalibrasi.
- **Favorit / label** untuk perangkat yang sering dilacak, serta notifikasi saat perangkat favorit menjauh (anti-lost).
- **Dekode format beacon** (iBeacon, Eddystone) dan tampilan manufacturer data mentah.
- **Ekspor riwayat** ke CSV.
- **UI test** Compose & instrumented test untuk DAO Room.

---

## Lisensi

Dibuat untuk keperluan study case rekrutmen Mobile Engineer.
