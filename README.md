# LanSync

LanSync is a seamless, cross-platform local network synchronization tool designed to make sharing files, folders, and clipboard data between your PC and mobile devices instant and effortless. 

By bypassing the cloud entirely, LanSync utilizes your local Wi-Fi network to achieve maximum transfer speeds with zero data limits, complete privacy, and no external servers.

## Features

* **Blazing Fast Transfers:** Maximize your local router's bandwidth. No internet connection required.
* **Recursive Folder Sharing:** Drag-and-drop entire directory trees on Desktop, or select full folders on Android to sync them instantly.
* **Clipboard Sync:** Seamlessly copy text on your PC and paste it on your phone (and vice-versa) with a single click.
* **Secure Peer-to-Peer:** Custom token-based authentication and connection request modals ensure no one accesses your files without explicit permission.
* **True Background Execution:** The Android app utilizes a lightweight Foreground Service and Partial WakeLocks, ensuring transfers never drop when your screen turns off.
* **Native OS Integration:** Features native macOS unified titlebars, Windows/Linux frameless windows, and dynamic device recognition.

## Tech Stack

LanSync is built using a modern, hybrid architecture to ensure maximum performance and a beautiful native feel across all devices.

**Desktop Client**
* **Core:** [Wails v2](https://wails.io/) (Go)
* **Frontend:** React 19, TypeScript, Tailwind CSS V4
* **Icons:** Lucide React

**Mobile Client (Android)**
* **Core:** Kotlin, Jetpack Compose (Material Design 3)
* **Networking Bridge:** Go Mobile (`gomobile bind`)
* **Background Engine:** Android Foreground Services & WakeLocks

## Getting Started

### Prerequisites
* Go 1.26+
* Node.js & npm (for Desktop frontend)
* Android Studio (for Mobile client)
* `gomobile` installed and configured

### Building the Desktop App
1. Clone the repository.
2. Navigate to the desktop directory: `cd desktop`
3. Install Wails CLI if you haven't already: `go install github.com/wailsapp/wails/v2/cmd/wails@latest`
4. Run in dev mode: `wails dev`
5. Build the final executable: `wails build`

### Building the Android App
1. Navigate to the desktop bridge directory and compile the Go library for Android:
   ```bash
   gomobile bind -target=android -androidapi 30 -o ../mobile/app/libs/bridge.aar ./bridge
   ```
2.  Open the `mobile` folder in Android Studio
3.  Sync project with Gradle files
4.  Build and run on your Android device (Android 11.0+)

## How it Works

LanSync operates on a dual-server architecture. When devices pair, they exchange temporary cryptographic bearer tokens. Both the Desktop and Mobile apps spin up lightweight Go HTTP servers (defaulting to port `34931`) to handle bidirectional streaming. The Android app leverages a custom threaded download engine to bypass OS-level HTTP restrictions, providing real-time speed and progress metrics directly in the notification tray.

