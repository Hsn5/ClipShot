# 📸 ClipShot

> **Silent, clipboard-only screenshot utility for Android.**  
> Capture your screen and copy it straight to your clipboard — without cluttering your gallery or interrupting your workflow.

---

## ✨ Features

- 📋 **Zero Gallery Clutter:** Screenshots are stored in a private, temporary cache and copied directly to `ClipboardManager`. Nothing ever touches your Photo Gallery or MediaStore.
- ⚡ **Non-Intrusive Capture:** Operates seamlessly via `AccessibilityService` without throwing confirmation popups or kicking you back to the Home Screen.
- 🌙 **Modern Dark Mode:** Clean, developer-friendly interface themed in midnight dark aesthetics.
- 🚀 **Multiple Trigger Options:**
  - **App Shortcut:** "Copy Screen" callable from the launcher, Samsung Modes & Routines, or RegiStar Back-Tap.
  - **Quick Settings Tile:** Single-tap capture right from your notification shade.
  - **Custom Key / Automation:** Triggerable via Activity or Broadcast Intents (Tasker, MacroDroid, Key Mapper, Button Mapper).

---

## 🛠️ Integration & Intents

If you use third-party button mappers or automation apps, you can trigger ClipShot using:

- **Activity Intent:**  
  `com.example.clipshot.ACTION_CAPTURE_TO_CLIPBOARD`
- **Broadcast Intent:**  
  `com.example.clipshot.BROADCAST_CAPTURE_TO_CLIPBOARD`

---

## ⚙️ Requirements

- Android 11+ (API 30+)
- Accessibility permissions enabled for silent capture
