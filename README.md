📸 ClipShot
​Smart, gallery-free screenshot & URL utility for Android.
Capture full or cropped regions straight to your clipboard, extract web links, and keep temporary shots in a 24-hour private vault — without cluttering your Photo Gallery.
​✨ Features
​✂️ Smart Crop Overlay:
​Circle-to-Search Aesthetic: Rounded glowing gradient frame with clean corner arcs and edge handles.
​Tap-to-Snap & Magnetic Edges: Tap any UI card, image, or post to snap the crop box to its exact bounds, with magnetic edge alignment while resizing.
​Live Pixel Magnifier Loupe: Circular zoom loupe above your finger when dragging corners or edges.
​Draggable Floating Bar: Lift the bottom action bar out of the way when cropping the lower screen.
​Double-Tap Outside = Full Screen: Quickly double-tap the dimmed area outside the selection to copy the full screen immediately.
​📋 Zero Gallery Clutter:
​Screenshots are copied directly to ClipboardManager and stored in an isolated internal vault (filesDir/vault with .nomedia and allowBackup="false").
​Nothing ever touches your Photo Gallery or MediaStore unless you tap Save to Gallery.
​🗄️ 24-Hour Private Vault:
​Every capture is stored in the dark-mode vault for 24 hours before auto-deleting.
​Pin 📌 important shots to keep them permanently (stored safely in filesDir, unaffected by cache clearing).
​Tap any thumbnail for a full-screen viewer with Copy, Save to Gallery, Pin, and URL actions.
​🔗 Auto Web URL Extraction:
​Detects active web page URLs in Chrome, Samsung Internet, Edge, Firefox, and Brave.
​Copy the link directly from the crop overlay or from the saved screenshot card in the vault.
​⚡ Triggers
​Quick Settings Tile: Tap ClipShot in your notification shade (auto-collapses shade before capture).
​Double-Tap Volume Down: Quickly double-tap Volume Down (with 260ms debouncing so the system volume bar doesn't photobomb your shot).
​Samsung Modes & Routines (Zero-Exit Trigger):
​Use Notifications → Show custom notification with:
​##clipshot## → Opens Smart Crop Overlay
​##clipurl## → Copies the active browser URL directly to clipboard
​Accessibility Button: Assign ClipShot to the floating Accessibility button or navigation bar shortcut.
​⚙️ Requirements
​Android 11+ (API 30+)
​Accessibility Service permission enabled for ClipShot Silent Capture (used for takeScreenshot, overlay display, and browser URL / trigger detection — zero network permissions, 100% offline).
