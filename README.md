# OWLVNEditor

Ứng dụng **chỉnh sửa và sao lưu dữ liệu game Otherworld Legends (Chiến Hồn Minh Giới) v3.8.0** chạy lõi **Rust** siêu nhanh trên điện thoại Android đã Root hoặc giả lập PC (**LDPlayer, MuMu, BlueStacks, Nox, MEmu**) — thao tác 1 chạm ở **Mức An Toàn Chuẩn**, hỗ trợ đầy đủ **Tiếng Việt & Tiếng Anh**.

- **Tác giả:** Nguyễn Phan Đức Mạnh (`© 2026`)
- **Kênh YouTube:** [youtube.com/@2ndofSeptember](https://www.youtube.com/@2ndofSeptember)
- **Tải APK chính thức (`v1.3.0`):** [GitHub Releases](https://github.com/NPDMVN/OWLVNEditor/releases/latest) • [MediaFire](https://www.mediafire.com/file/bdbqokv7qh3h9kv/OWLVNEditor-v1.3.0-release.apk/file) • [Google Drive](https://drive.google.com/file/d/1v-G0wdJuTjTCtthpo3KDfWMX0y-bLJWq/view?usp=drivesdk)
- **Giấy phép:** [PolyForm Noncommercial 1.0.0](LICENSE) *(Miễn phí sử dụng & chỉnh sửa phi thương mại — bắt buộc giữ tên tác giả, xem [NOTICE](NOTICE))*

> `Required Notice: Copyright (c) 2026 Nguyễn Phan Đức Mạnh (https://github.com/NPDMVN/OWLVNEditor | https://www.youtube.com/@2ndofSeptember)`

---

## 1. Tính năng nổi bật

### Chỉnh sửa & Mở khóa Otherworld Legends v3.8.0 chỉ với 1 chạm (Mức An Toàn Chuẩn)
- **14 gói Preset an toàn chuẩn hóa trực tiếp từ `GameLogic.dll` v3.8.0:** Tự động đồng bộ khóa chống gian lận XOR (`EncryptValue`) và cấu trúc nhị phân MS-NRBF (`BinaryFormatter`).
- **Tài nguyên & Vé:** 500.000 Linh Thạch, 50.000 Lam Ngọc, 50.000 Hồng Ngọc, 200 Vé Vũ Khí / Hồi Sinh / Phân Bón / Tẩy Ngọc / Giám Định, 99 Vé Dùng Thử, 500 Mảnh Tướng & Skin đa năng, 300 Xu Ma Dạ (Moye Token).
- **46 Tướng Max Lv.15 & 112 Skin:** Mở toàn bộ 46 Tướng (kèm Kỹ năng chính/phụ & Phong cách), 112 Skin Tướng, 23 Skin Power đặc biệt, 28 Skin NPC và 18 Giao diện phòng chờ.
- **236+ Thành Tựu (Achievements):** Hỗ trợ 2 chế độ:
  - *Mở Hết (Tự nhận)* — giữ nguyên ngày hoàn thành lịch sử cũ, để sẵn trạng thái chưa nhận để bạn vào game tự bấm **"Nhận Tất Cả"** lấy thưởng & tăng điểm thành tích tự nhiên.
  - *Đánh dấu đã nhận hết* — hoàn tất gọn gàng toàn bộ thành tựu.
- **Thú cưng, Tu La, Lục Đạo & Phòng chờ:**
  - 32 Thú cưng (có Tiểu Asura, bản Vàng, Thân thiện/Độ no 100) + 8 Tự động nhặt + 300 Thức ăn & 300 Đồ chơi.
  - Sức Mạnh Asura 12.000 điểm an toàn (+95.6% toàn chỉ số, tự cân bằng chuẩn cả 8 sơ đồ Tu La).
  - 40 Thần Lực Naga Lv.5, 37 Sức Mạnh Lục Đạo, Độ khó 14 (Real Mirage / Pandemonium).
  - 122 Tượng Phòng Thành Tựu (Lv.7 chuẩn), 42 Đĩa nhạc Walkman, 401 Vật phẩm, 14 Kỹ năng Boss chia sẻ, 22 Phụ tố Affix, 49 Nguyên liệu Quầy Bar, 9/9 Ô Khu Vườn, 31 Ô Cấm vật phẩm, Chiến Lệnh Mirage Pass & 11 Công trình phòng chờ.
- **Chuẩn Hóa Về Mức An Toàn (`Safe-Cap Audit`):** Tự động kiểm tra và đưa các chỉ số từng bị chỉnh quá trần về ngưỡng an toàn tự nhiên giúp đồng bộ **Cloud Save** mượt mà.

### An toàn dữ liệu & Công cụ nâng cao
- **Giải mã & Ghi nguyên tử siêu nhanh (Rust Core):** Đọc/giải mã AES-256-CBC + MS-NRBF chỉ trong ~50–80ms; tự dừng game (*Force-Stop*), ghi nguyên tử (*Atomic Write*), khôi phục quyền `uid/gid/mode` chuẩn và đối chiếu **SHA-256 100%**.
- **Xem trước thay đổi (Diff Preview) & Sao lưu (`.owldb`):** Hiển thị chi tiết từng khóa thay đổi trước khi ghi, tự động chụp **Auto-Rollback** trước mỗi lần lưu và hỗ trợ xuất/nhập gói sao lưu `.owldb` có mã kiểm tra SHA-256.
- **Explorer & Trình sửa NRBF/JSON trực tiếp:** Tìm kiếm, lọc theo danh mục và chỉnh sửa từng mục bên trong các container BinaryFormatter hoặc chuỗi JSON.
- **Account Sanitizer & SSAID Spoofer:** Dọn khóa đăng nhập/CloudSaveId sạch sẽ mà không mất tiến trình chơi; đọc/đổi SSAID hỗ trợ cả **Plain XML** (Android 8–11 / Giả lập) và **ABX Binary** (Android 12–15).
- **Song ngữ Tiếng Việt / English tức thì:** Chuyển ngôn ngữ 1 chạm bằng nút **`VI / EN`** trên thanh tiêu đề mà không cần khởi động lại ứng dụng.

---

## 2. Yêu cầu & Cách sử dụng (3 bước)

**Yêu cầu:** Điện thoại Android đã **Root** (Magisk / KernelSU / APatch, Android 7.0+) **hoặc** giả lập PC đã bật Root (**LDPlayer, MuMu, BlueStacks, Nox, MEmu**). *(Hỗ trợ đủ 4 kiến trúc `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` và 22 gói vùng của Otherworld Legends).*

1. **Tải và cài đặt:** Tải tệp `OWLVNEditor-v1.3.0-release.apk` từ [mục Releases chính thức](https://github.com/NPDMVN/OWLVNEditor/releases/latest) và cài vào máy/giả lập.
2. **Mở game tạo save trước:** Mở **Otherworld Legends**, đăng nhập tài khoản (nếu dùng Cloud Save) và vào sảnh phòng chờ rồi thoát game.
3. **Chỉnh sửa 1 chạm:** Mở **OWLVNEditor** → cấp quyền **Root** → bấm **Mở Save Trực Tiếp Bằng Root** → sang tab **Chỉnh nhanh** chọn gói **Full Safe Bundle v3.8.0** → bấm **Lưu** rồi mở game trải nghiệm.

**Lưu ý an toàn:**
- Luôn tạo 1 bản **Sao lưu (`.owldb`)** trước khi chỉnh sửa để bảo toàn dữ liệu gốc 100%.
- Giữ các chỉ số ở ngưỡng **Safe-Cap** (hoặc chạy preset **Chuẩn Hóa Về Mức An Toàn**) trước khi tải lên Cloud Save.
- Chỉ tải APK từ kênh phát hành chính thức của tác giả để tránh bản repack chỉnh sửa trái phép.

---

## 3. Dành cho lập trình viên (Mã nguồn & Hướng dẫn tự Build)

<details>
<summary>Bấm vào đây để xem kiến trúc mã nguồn và hướng dẫn biên dịch</summary>

### Kiến trúc dự án
```text
OWLVNEditor/
├── android/                          # Ứng dụng Android (Kotlin + Jetpack Compose + Material 3)
│   └── app/src/main/
│       ├── assets/bin/               # Root helper native 4 ABI (aarch64, armv7, x86, x86_64)
│       ├── assets/profiles/          # Profile, Catalog song ngữ VI/EN & 14 Presets v3.8.0
│       ├── jniLibs/                  # Thư viện lõi Rust libowledit_core.so (4 ABI)
│       └── java/com/owledit/         # UI screens, ViewModel, RootBridge, RustCore JNI, I18n
├── rust/                             # Workspace lõi Rust hiệu năng cao
│   └── crates/
│       ├── owledit-core/             # AES-256-CBC, MS-NRBF BinaryFormatter, XOR sync, Unlockables, Sanitizer, ABX SSAID, .owldb
│       ├── owledit-ffi/              # Export 25 hàm JNI -> libowledit_core.so
│       ├── owledit-helper/           # Daemon root nguyên tử (stat, atomic-write, chown/chmod, sha256)
│       └── owledit-cli/              # CLI kiểm thử & thao tác save trên PC
├── scripts/                          # Script tự động build Rust 4-ABI & đóng gói APK
├── LICENSE                           # PolyForm Noncommercial License 1.0.0
└── NOTICE                            # Thông báo bản quyền bắt buộc giữ nguyên
```

### Yêu cầu môi trường build
- **JDK 17** và **Android SDK 34** (`compileSdk = 34`, `targetSdk = 34`, `minSdk = 24`).
- *(Chỉ cần khi biên dịch lại lõi Rust)* **Rust toolchain** + **Android NDK 26.x**.

### Chạy kiểm thử lõi Rust
```powershell
cd rust
cargo test --workspace
```

### Biên dịch APK (Android)
```powershell
cd android
gradle assembleRelease --no-daemon
```
APK đầu ra: `android/app/build/outputs/apk/release/app-release.apk`.

</details>
