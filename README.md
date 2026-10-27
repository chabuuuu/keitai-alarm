# Keitai Alarm (携帯アラーム)

An alarm clock application built and optimized specifically for Android "Garaho" flip phones (ガラホ / Galapagos Android Handsets), tailored for the **Fujitsu Arrows F-03L (NTT Docomo)** running Android 8.1 Oreo (API 27).

---

## 📸 Overview

Modern smartphone alarm apps are designed for capacitive touchscreens and often suffer from severe usability and reliability issues on Japanese flip phones:
* Cluttered layouts overflow on small 3.4-inch portrait displays (320dp effective width).
* Touch-only time dials are unusable with physical D-Pads and numeric keypads.
* Aggressive vendor battery managers (Fujitsu Eco Mode / Doze) kill background alarms.
* Stock alarm sound pickers artificially truncate custom songs to 30–60 seconds.

**Keitai Alarm** solves all of these problems with a 100% keypad-driven interface, full-track music playback, random playlist shuffling, and high-priority alarm scheduling designed around the physical matrix keypad of the Fujitsu F-03L.

---

## ✨ Features

### 🎵 Full-Song Audio Playback (No 30-Second Cutoff)
* Plays entire audio tracks (MP3, M4A, FLAC, WAV, AAC, OGG) from start to finish without arbitrary stock Android cutoffs.
* Runs via a foreground audio service bound to `AudioAttributes.USAGE_ALARM` with a partial wake lock to guarantee loud, uninterrupted playback even with the flip closed.

### 🎲 Random Ringtone from Playlist
* Built-in **Random Playlist Manager**:
  * **Auto Storage Scanner**: Scans internal storage (`/sdcard/Music`, `Download`) and external microSD cards (`/storage/xxxx-xxxx/`) for audio files.
  * **Manual File Picker**: Add individual music files directly.
* Alarms set to **"Random from Playlist"** will dynamically pick a new track each time the alarm fires.

### ⏰ Up to 50 Custom Alarms
* Store and manage up to **50 alarms** with persistent storage.
* Visual counter badge (`0/50`) and an upcoming alarm countdown banner (`🔔 Next: 07:00 - in 7h 15m`).

### 📅 Flexible Scheduling
* Presets: *Once*, *Daily*, *Weekdays (Mon–Fri)*, *Weekends (Sat–Sun)*.
* Individual day selections (Mon, Tue, Wed, Thu, Fri, Sat, Sun).

### 🎨 Sony Xperia & Walkman Design Aesthetic
* Inspired by classic **Sony Xperia** and **Sony Walkman** user interfaces:
  * Deep obsidian black background (`#0E1015`) and charcoal surfaces (`#161820`).
  * Signature **Amethyst Purple** (`#9D5CFF`) and **Walkman Gold** (`#E5A93C`) accents.
  * Clean, compact digital typography calibrated specifically for 480×854 (320dp width) screens.
  * Custom **Sony Dark Dialog** theme preventing white-on-white text bugs in system popups.

### 🛡️ Ironclad Reliability (Anti-Doze / Fujitsu Eco Mode)
* Uses `AlarmManager.setAlarmClock()`, the highest-priority wake mechanism in Android.
* Shows the native system alarm icon in the Android status bar and on the **F-03L external sub-display**.
* Automatically wakes up the screen and bypasses the keyguard (`FLAG_SHOW_WHEN_LOCKED`, `setTurnScreenOn`).

---

## ⌨️ T9 Keypad & D-Pad Keymap

Every single interaction is 100% controllable through the physical keypad—no touch required.

### 1. Main Alarm List (`MainActivity`)
| Key | Action |
| :--- | :--- |
| `D-Pad Up / Down` | Navigate between alarm cards (illuminated with Sony purple focus ring) |
| `D-Pad Center (OK)` | **Toggle ON / OFF** selected alarm |
| `[1]` / `Left Soft Key (F1)` | **Add new alarm** (up to 50) |
| `[2]` | **Edit** selected alarm |
| `[3]` / `Right Soft Key (F2)` | Open **Random Playlist** manager |
| `[*]`, `[4]`, or `Clear` | **Delete** selected alarm (with dark confirmation dialog) |

### 2. Alarm Edit Screen (`AlarmEditActivity`)
| Key | Action |
| :--- | :--- |
| **Numeric Keys `0` – `9`** | **Direct 4-Digit Time Entry**: Typing `0` `7` `3` `0` instantly sets `07:30` |
| `D-Pad Up / Down` | Increment / decrement hour or minute field |
| `D-Pad Left / Right` | Switch focus between Hour box and Minute box |
| `[#]` / `Left Soft Key (F1)` / `CALL` | **SAVE ALARM** (distinct from digit entry) |
| `D-Pad Center (OK)` | Select options, toggle days, or click buttons |
| `Clear / Back` | Discard changes and return |

> **Note on Save Button Focus**: When navigating down to the Save button via D-pad, it transforms into high-contrast **Solid Walkman Gold with a 2.5dp pure white border and bold black text**, making current cursor focus unmistakable.

### 3. Random Playlist Screen (`RandomPlaylistActivity`)
| Key | Action |
| :--- | :--- |
| `[1]` | Add audio file via system file picker |
| `[2]` | **Auto-scan** internal storage & microSD card for music files |
| `[3]` | **Test play** a random track from the playlist |
| `D-Pad Center (OK)` | Preview / Stop playing selected track |
| `[*]`, `Clear`, or `DEL` | Remove track from playlist |

### 4. Ringing Alert Screen (`AlarmAlertActivity`)
| Key | Action |
| :--- | :--- |
| `D-Pad Center (OK)` or `[5]` | **DISMISS ALARM** |
| `[0]` or `[*]` | **SNOOZE (10 minutes)** |
| `Volume Up / Down` | Snooze / Dismiss |

---

## 📱 Hardware Target: Fujitsu Arrows F-03L

| Specification | Details |
| :--- | :--- |
| **Model** | arrows Keitai F-03L (NTT Docomo) |
| **OS** | Android 8.1.0 Oreo (API Level 27) |
| **Chipset** | Qualcomm Snapdragon 210 (MSM8909), Quad-core 1.1 GHz |
| **RAM / Storage** | 1 GB RAM / 8 GB ROM (microSD up to 32 GB) |
| **Main Display** | 3.4-inch TFT LCD, 480 × 854 pixels (240 DPI, ~320dp width) |
| **Sub Display** | 0.8-inch OLED (21 × 96 pixels) |
| **Keypad Controller** | Physical Matrix Keypad (`FJDEV.kl`) |

*Also compatible with other Android 8.0+ Garaho models (Sharp SH-02L, Kyocera KYF3x/KYF4x series).*

---

## 🛠️ Building & Installing

### Prerequisites
* JDK 17 or JDK 21
* Android SDK (API 34 compile, API 26+ min)
* ADB (Android Debug Bridge)

### 1. Build the APK
```bash
./gradlew assembleDebug
```
The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install on Device via ADB
Connect your F-03L via USB (make sure USB Debugging is enabled in *Settings > Developer Options*):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Grant Permissions & Battery Whitelist (Recommended)
Grant storage permission to read full-length songs and whitelist the app from battery optimizations:

```bash
# Grant external storage access for music files
adb shell pm grant com.keitai.alarm android.permission.READ_EXTERNAL_STORAGE

# Whitelist from Doze / power management
adb shell dumpsys deviceidle whitelist +com.keitai.alarm
```

### 4. Launch the App
```bash
adb shell am start -n com.keitai.alarm/.ui.MainActivity
```

---

## 📂 Project Structure

```
keitai-alarm/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/keitai/alarm/
│   │   │   ├── KeitaiAlarmApp.kt               # Application entry & reschedule on boot
│   │   │   ├── data/
│   │   │   │   ├── AlarmRepository.kt          # 50-alarm max constraint & JSON storage
│   │   │   │   └── PlaylistRepository.kt       # MediaStore & storage audio scanner
│   │   │   ├── model/
│   │   │   │   ├── Alarm.kt                    # Alarm data model & schedule helpers
│   │   │   │   └── PlaylistItem.kt             # Audio track model
│   │   │   ├── receiver/
│   │   │   │   ├── AlarmReceiver.kt            # Wakes phone & launches alert activity
│   │   │   │   └── BootReceiver.kt             # Reschedules alarms on system reboot
│   │   │   ├── scheduler/
│   │   │   │   └── AlarmScheduler.kt           # setAlarmClock calculation engine
│   │   │   ├── service/
│   │   │   │   └── AlarmPlaybackService.kt     # Foreground audio player & wake lock
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt             # Main alarm list & navigation
│   │   │       ├── AlarmEditActivity.kt        # T9 numeric time entry & alarm editor
│   │   │       ├── RandomPlaylistActivity.kt   # Music playlist manager & scanner
│   │   │       ├── AlarmAlertActivity.kt       # Fullscreen ringing screen
│   │   │       └── adapter/
│   │   │           ├── AlarmAdapter.kt         # Alarm list adapter with D-pad focus
│   │   │           └── PlaylistAdapter.kt      # Audio track adapter
│   │   └── res/
│   │       ├── color/                          # Dynamic text state color selectors
│   │       ├── drawable/                       # Sony Walkman/Xperia focused selectors
│   │       ├── layout/                         # 320dp-optimized layouts
│   │       └── values/                         # Sony colors, strings & SonyDarkDialog theme
│   └── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 📄 License
This project is open-source and free to use for personal side projects and Japanese flip phone enthusiasts.
# keitai-alarm
