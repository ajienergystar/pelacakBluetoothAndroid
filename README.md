# BLE Pelacak — Bluetooth Proximity Tracker

Aplikasi Android untuk memindai sinyal advertising perangkat **Bluetooth Low Energy (BLE)** di sekitar secara real-time, memantau kekuatan sinyal (RSSI), dan melacak kedekatan satu perangkat target menggunakan visualisasi radar berbasis zona jarak.

> Study Case — Mobile Engineer

---

## Daftar Isi

1. [Fitur](#fitur)
2. [Setup & Menjalankan Aplikasi](#setup--menjalankan-aplikasi)
3. [Arsitektur](#arsitektur)
4. [Library & Alasan Pemilihan](#library--alasan-pemilihan)
5. [Pemetaan RSSI → Zona & Jarak](#pemetaan-rssi--zona--jarak)
6. [Error Handling & Resilience](#error-handling--resilience)
7. [App Lifecycle](#app-lifecycle)
8. [Testing](#testing)
9. [Known Issues / Keterbatasan](#known-issues--keterbatasan)
10. [Asumsi Teknis & Kendala](#asumsi-teknis--kendala)

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
git clone <url-repo>
cd TugasAndroid
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
