# ⌚ LegacyVault SmartWatch DMS (Dead Man's Switch)

> **Hệ sinh thái điểm danh sinh trắc học BioActive PPG trên Wear OS (Samsung Galaxy Watch 4 / 5 / 6 / 7 / Ultra) dành cho hệ thống Di sản Số LegacyVault.**

---

## 🌟 Tính năng nổi bật

1. **Sinh trắc học PPG theo thời gian thực**:
   - Sử dụng cảm biến quang học BioActive (`Sensor.TYPE_HEART_RATE`, PPG) để đo nhịp tim đập qua mạch máu cổ tay và xác thực người dùng đang còn sống.
   - Cơ chế tự động phát hiện tiếp xúc da tay (`isOnWrist: true`).
2. **Ghép nối động bảo mật (Dynamic PIN Pairing)**:
   - Web Dashboard sinh mã PIN 6 số dùng một lần (hiệu lực 5 phút).
   - Nhập mã PIN trực tiếp trên đồng hồ để liên kết và nhận Access Token JWT lưu trữ an toàn trong `SharedPreferences`.
3. **Cấu hình máy chủ linh hoạt**:
   - Mặc định trỏ về máy chủ chính thức Render (`https://legacyvault-api.onrender.com`).
   - Hỗ trợ đổi máy chủ linh hoạt (Cloudflare Tunnel, Local IP, Staging) chỉ bằng 1 lần chạm vào biểu tượng 🌐 trên đồng hồ.
4. **Cơ chế dự phòng khẩn cấp**:
   - Nếu ở phòng thi hoặc cảm biến gặp sự cố, nhấn giữ lâu (Long Press) nút "ĐO NHỊP TIM" trong 2 giây để gửi điểm danh dự phòng.

---

## 🚀 Cài đặt nhanh lên Galaxy Watch (1-Click)

### Yêu cầu:
1. Máy tính và Galaxy Watch kết nối cùng một mạng Wi-Fi.
2. Bật **Developer Options** (Tùy chọn cho nhà phát triển) trên Galaxy Watch:
   - Vào *Cài đặt* $\rightarrow$ *Thông tin đồng hồ* $\rightarrow$ *Phần mềm* $\rightarrow$ Chạm 7 lần vào *Số phiên bản phần mềm*.
3. Bật **Gỡ lỗi qua Wi-Fi (Wireless Debugging)**:
   - Vào *Cài đặt* $\rightarrow$ *Tùy chọn nhà phát triển* $\rightarrow$ Bật *Gỡ lỗi ADB* và *Gỡ lỗi qua Wi-Fi*.
   - Ghi lại địa chỉ IP hiển thị trên đồng hồ (ví dụ: `192.168.1.236:5555`).

### Cài đặt:
Chạy file script tự động:
```cmd
install-watch-app.bat
```
Nhập IP của đồng hồ khi được hỏi $\rightarrow$ Script sẽ tự động kết nối ADB và nạp file APK cài đặt lên đồng hồ trong 10 giây!

---

## 📁 Cấu trúc thư mục

```text
tools/
├── apk/
│   └── LegacyVault-Watch-debug.apk     # Bản dựng APK sẵn sàng cài đặt
├── watch-app/                          # Mã nguồn ứng dụng Android Wear OS (Kotlin)
│   ├── app/
│   │   ├── src/main/java/com/legacyvault/watch/
│   │   │   └── MainActivity.kt         # Logic đo nhịp tim, ghép nối và gửi API
│   │   └── src/main/res/               # Giao diện Wear OS tối ưu cho màn hình tròn
│   ├── build.gradle.kts
│   └── gradlew.bat
├── connect-watch.bat                   # Script kết nối nhanh ADB
├── install-watch-app.bat               # Script tự động cài đặt APK 1-click
└── README.md
```

---

## 🛠️ Build từ mã nguồn (Dành cho Developer)

Yêu cầu: Android Studio Hedgehog+ hoặc JDK 21+.

```bash
cd watch-app
./gradlew assembleDebug
```
File APK đầu ra nằm tại: `watch-app/app/build/outputs/apk/debug/app-debug.apk`.

---

## 🔒 Bản quyền & Nhóm phát triển
- **Dự án**: SWP391 — LegacyVault
- **Nhóm**: FPT-SWP391-SE2040-GROUP02
