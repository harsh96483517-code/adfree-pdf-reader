# Ad-Free PDF Reader

A clean, **100% ad-free** PDF reader and editor for Android — built with Kotlin.
No banners, no popups, no trackers, no "premium" paywall. Everything runs
**on-device and offline**.

> Built as an alternative to ad-heavy Play Store PDF readers. Read the source,
> build it yourself, sideload it, and never see an ad again.

---

## ⬇️ Download the APK

**[Download the latest APK](https://github.com/harsh96483517-code/adfree-pdf-reader/releases/latest)**

1. Download `app-debug.apk` and transfer it to your phone.
2. Allow **Install unknown apps** for your browser / file manager.
3. Open the APK and install.

> Debug build (signed with the Android debug key), so it is not on the Play Store.
> Google Play Protect may warn about an unknown developer — that is expected for
> a sideloaded APK.

---

## Features

**Read**
- Auto-scans and lists every PDF on your device
- Open from any app ("Open with…"), or pick any file via the system picker
- Page-by-page and continuous scrolling, pinch-to-zoom, pan
- Light / dark theme
- Jump to page, full-document text search (jumps to the matching page)
- Bookmarks per page
- Sort by name / date / size

**Annotate & sign**
- Highlight, freehand pen, text notes and **e-signature** on any page
- Undo, erase, clear
- Flattens annotations onto a **new PDF copy** (your original is never modified)

**Tools** (all offline, powered by Apache PDFBox)
- **Merge** multiple PDFs (reorder before merging)
- **Split** — extract any pages (e.g. `1-3,5`)
- **Compress** — reduce file size with a quality slider
- **Images to PDF** — combine JPG/PNG photos into one PDF

Every result is saved to `Downloads/AdFreePdfReader/` and offered for sharing.

---

## Why it has no ads

There is no ad SDK in `app/build.gradle.kts` and no analytics library anywhere in
the project. The only dependencies are AndroidX, Material Components, Coil (page
thumbnails) and PDFBox-Android. That's it.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Material 3, View Binding, RecyclerView |
| PDF render | `android.graphics.pdf.PdfRenderer` (native) |
| PDF edit / tools | `com.tom-roush:pdfbox-android` (pure Java) |
| Images | Coil |

Min SDK 26 (Android 8.0) · Target SDK 34.

---

## Build it

### Option A — Android Studio (easiest)
1. **File → Open** and select this folder.
2. Android Studio will offer to create the Gradle wrapper — accept.
3. Press **Run ▶**. The app installs on your connected device/emulator.

### Option B — Build an APK from the command line
```bash
# one-time: generate the Gradle wrapper
gradle wrapper --gradle-version 8.7

./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/app-debug.apk
```

### Option C — Let GitHub build it for you
Push to `main` (or run the **Build APK** workflow manually). GitHub Actions
builds a debug APK and attaches it under **Actions → Build APK → Artifacts**.
Download the artifact, transfer it to your phone and install it.

> First install requires enabling **Install unknown apps** for your browser or
> file manager.

---

## Permissions

| Permission | Why |
|---|---|
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` | list PDFs already on the device |
| `MANAGE_EXTERNAL_STORAGE` | optional, full-storage scan like other readers; the app also works fully through the system file picker without it |

No internet permission is requested — the app cannot phone home even if it wanted to.

---

## Project layout

```
app/src/main/java/com/adfreepdf/reader/
├── App.kt                     # initialises PDFBox
├── data/                      # PdfFile, repository (scan), Prefs (bookmarks/recents)
├── pdf/PdfTools.kt            # merge, split, compress, images→PDF, annotate, text
├── ui/home/                   # file list, tabs, search, sort
├── ui/viewer/                 # PdfRenderer pager + zoom
├── ui/edit/                   # DrawingView + annotate/sign screen
├── ui/tools/                  # merge / split / compress / images→PDF
└── util/FileUtils.kt          # uri helpers, save-to-Downloads
```

---

## License

MIT — see [LICENSE](LICENSE). Use it, fork it, ship it.
