# Xbox Cloud Solution for Android TV (CloudX)

CloudX is a high-performance, open-source Android application designed for **Xbox Cloud Gaming**. It is the first client to move the core streaming logic away from the browser-based WebView and into a **Pure Native Java** environment using WebRTC.

> [!TIP]
> This project is designed specifically for power users and Android TV owners who want the lowest possible latency and maximum controller compatibility.

## 👨‍💻 Developed by
**LaGab Adel** - [GitHub Profile](https://github.com/Pgeniebox)

## 🚀 Key Innovations

### Native Handover vs. WebView
Traditional xCloud clients run entirely inside a browser engine (WebView). This adds overhead, limits controller support, and introduces input lag. 
**CloudX changes the game:**
1. **The Handover**: We use a lightweight WebView only for the login and game selection process.
2. **Native Execution**: Once a game starts, the app intercepts the WebRTC signaling and "hands over" the connection to a native background service.
3. **Hardware Acceleration**: The video stream is rendered using a native `SurfaceView` with direct hardware decoding, bypassing the browser's rendering stack.

## ✨ Features

- **Ultra-Low Latency**: Native WebRTC implementation for immediate response times.
- **Microphone Support**: Full native support for in-game voice chat.
- **Advanced Gamepad Support**: 
    - Reliable (TCP-like) and Unreliable (UDP-like) input channels.
    - Low-level rumble support.
    - Custom deadzone and sensitivity settings.
    - Specialized Xbox Guide (Nexus) button mapping.
- **Android TV Optimized**: Leanback UI support and 1080p/1440p resolution forcing.
- **Performance Diagnostics**: Real-time overlay showing bitrate, FPS, and jitter.

## 🛠 Getting Started

### Prerequisites
- Android 10 (API 29) or higher.
- A valid Xbox Game Pass Ultimate subscription.

### Building from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/Pgeniebox/xbox-cloud-Solution-Android-tv.git
   ```
2. Open the project in **Android Studio Koala** or newer.
3. Create a `keystore.properties` file in the root directory (optional for debug builds).
4. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

## 📜 Acknowledgements

Special thanks to the **BetterXCloud** **XStreaming** team's. Their open-source research and scripts provided the foundation for understanding the xCloud signaling protocol, which made this native implementation possible.

## ⚖️ License

Distributed under the **MIT License**. See `LICENSE` for more information.

---
*Note: Tested on Mi Box TV.*

*Disclaimer: This project is not affiliated with, endorsed by, or sponsored by Microsoft or Xbox.*
