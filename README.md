# File Explorer — Windows 11 style for Android

Package: `win.android.fileexplorar`  
minSdk 21 · targetSdk 35 · All ABIs (x86, x86_64, armeabi-v7a, arm64-v8a)

## Features
- Windows 11 light theme UI
- Sidebar: Home, Pinned (Desktop, Downloads, Documents, Pictures, Music, Videos), Drives
- Toolbar: New Folder, New Window, Cut, Copy, Paste, Delete, Rename, View toggle
- Address bar with Back / Forward / Up / Refresh
- Context menu (long-press): Open, Open with, Open in new window, Cut, Copy, Paste, Rename, Delete, New Folder, Create shortcut on Desktop
- Drag & Drop (long-press then drag to move files into folders)
- Desktop shortcuts (.wlnk) — opening them navigates to the target folder
- Grid and List view
- Works on Android x86 / x86_64 and ARM devices

## Build with GitHub Actions
Push to GitHub → Actions → Build APK → download artifact.

Or locally:
```bash
./gradlew assembleDebug
```
