<div align="center">

# 🌻 Faithfully App

### *The Android app I built to manage letters and photos on her website.*

A small Java + XML admin app that lets me write letters, organize them into chapters, and upload cat photos — all syncing in real time to a public website I built for her.

**[🌐 Website repo](https://github.com/mowtiie/Faithfully-App)** · **[🌐 Live Site](https://alliyannah.love)**

</div>

---

## 💛 Why I built this

The website I made for her ([Faithfully Web](https://github.com/mowtiie/Faithfully-Web)) needed a way for me to add new letters and photos from anywhere — without editing JSON files or pushing to a repo every time. So I built the admin side: a small Android app, signed in silently as me, that talks to the same Firebase backend the site reads from.

I picked Java and XML views instead of Kotlin and Compose on purpose — it's the stack I'm most comfortable with from my coursework, and I wanted to focus on the polish of the actual UX rather than fighting a new language while building something personal.

---

## ✨ Features

### Letters
- 📖 **Chapters** — add, edit, delete, and reorder chapters that group letters into eras of our story
- 💌 **Letters** — write new ones with a built-in date picker, assign them to a chapter, edit or delete later
- 🔀 **Drag-to-reorder** — long-press any chapter or card to drag it into a new position
- 🏷️ **Reassign chapters** — move a letter to a different chapter, or unassign it entirely

### Gallery
- 🐱 **Photo upload** — pick a cat photo with the system photo picker
- 🪶 **Automatic compression** — full photo resized to 1920px + thumbnail at 400px, JPEG-compressed to ~10x smaller
- 🔃 **Auto-rotation** — EXIF orientation read so portrait photos don't end up sideways
- 📝 **Captions** — optional, editable later via long-press menu
- 🗑️ **Cascade delete** — deleting a photo removes both the full-res and thumbnail from Firebase Storage

### Auth & permissions
- 🔒 **Admin and guest modes** — sign in to manage everything, or continue as a guest to just browse
- 🛡️ **UID-restricted writes** — Firestore rules reject any write attempt that doesn't come from my specific admin user UID

### Polish
- 💾 **Offline-friendly chapters** — cached in SQLite so the chapters list opens instantly without waiting for the network
- 🎨 **Material 3 styling** — dragged cards lift with a soft color shift using `colorPrimaryContainer` from the theme
- 🔄 **Atomic batch reorders** — all `order` field updates go through a Firestore batch write so partial-update edge cases are impossible

---

## 🔐 Admin vs Guest mode

The app has two modes:

| Mode | What they can do |
|---|---|
| **Admin** (me, signed in with a Firebase Auth account) | Add, edit, delete, reorder chapters, letters, and photos |
| **Guest** (anyone, no login needed) | Browse chapters, expand letters, view the gallery — **no write access** |

A small "👀 Read-only mode" banner appears at the top of the home screen when in guest mode. All FABs, edit buttons, delete buttons, drag handles, and upload screens are hidden for guests. Even if someone tried to bypass the UI checks, Firestore and Storage security rules reject any write attempt that doesn't come from my admin user UID.

---

## 📸 Screenshots

| Login | Home | Letters | Dark Mode |
|:---:|:---:|:---:|:---:|
| ![Login](metadata/en-US/images/screenshots/login.jpg) | ![Home](metadata/en-US/images/screenshots/home.jpg) | ![Chapters](metadata/en-US/images/screenshots/letters.jpg) | ![Gallery](metadata/en-US/images/screenshots/dark-mode.jpg) |

---

## ✅ Verification

APK releases on GitHub are signed using my key. They can
be verified using
[apksigner](https://developer.android.com/studio/command-line/apksigner.html#options-verify):

```
apksigner verify --print-certs --verbose faithfully.apk
```

The output should look like:

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v3.2 scheme (APK Signature Scheme v3.2): false
Verified using v4 scheme (APK Signature Scheme v4): false
```

The certificate fingerprints should correspond to the ones listed below:

```
Owner: CN=Mowtiie
Issuer: CN=Mowtiie
Serial number: 8a256fdcdde50069
Valid from: Wed Jun 10 22:57:23 PST 2026 until: Sun Oct 26 22:57:23 PST 2053
Certificate fingerprints:
         SHA1: 56:4E:2C:DB:E4:06:C9:EC:15:E6:BC:D9:0A:88:38:72:8B:FB:13:20
         SHA256: 8B:67:51:F3:C3:31:85:63:5F:98:95:30:B6:C0:73:A1:39:7B:3D:41:2B:EF:AE:69:06:A2:EB:58:45:D2:DE:63
```

---

## 🛠️ Tech stack

| Layer | What I used |
|---|---|
| **Language** | Java |
| **UI** | XML views (no Compose) with Material Components |
| **Database** | [Firebase Firestore](https://firebase.google.com/docs/firestore) |
| **File storage** | [Firebase Storage](https://firebase.google.com/docs/storage) |
| **Auth** | [Firebase Auth](https://firebase.google.com/docs/auth) (email/password + persistent guest flag) |
| **Local cache** | Android SQLite via `SQLiteOpenHelper` |
| **Image loading** | [Glide](https://github.com/bumptech/glide) for async grid loading |
| **Image processing** | Built-in Android `Bitmap` + `ExifInterface` (resize, rotate, JPEG-encode) |
| **Min SDK** | 24 (Android 7.0+) |

---

## 📁 Project structure

```
app/src/main/java/com/mowtiie/faithfully/
├── LoginActivity.java         # entry screen — sign in or continue as guest
├── MainActivity.java          # landing screen with nav cards
│
│  --- Letters ---
├── ChaptersActivity.java      # list, reorder, delete chapters
├── AddChapterActivity.java    # add or edit a chapter
├── CardsActivity.java         # list cards inside a chapter
├── AddCardActivity.java       # add or edit a card
├── ChapterAdapter.java
├── CardAdapter.java
├── Chapter.java
├── Card.java
├── ChapterDbHelper.java       # SQLite cache for chapters
│
│  --- Gallery ---
├── GalleryActivity.java       # photo grid + admin actions
├── AddPhotoActivity.java      # picker + compress + upload
├── PhotoViewerActivity.java   # full-screen photo viewer
├── PhotoAdapter.java
├── Photo.java
├── ImageUtils.java            # resize, rotate, JPEG-encode
│
└── AuthHelper.java            # admin/guest mode detection
```

---

## 🏗️ How it fits together

```
   ┌──────────────────┐         ┌─────────────────┐
   │  This app        │         │  Public website │
   │  (admin only)    │         │  (read-only)    │
   └────────┬─────────┘         └────────┬────────┘
            │ writes                     │ reads
            │ (UID-restricted)           │
            └───────────┬────────────────┘
                        ▼
          ┌────────────────────────────────┐
          │  Firebase                       │
          │   • Firestore                  │
          │     - chapters/                │
          │     - cards/                   │
          │     - gallery/                 │
          │   • Storage                    │
          │     - gallery/*.jpg            │
          └────────────────────────────────┘
```

Firestore + Storage security rules check the user's UID on every write. Only the admin account can modify data — so even though guests can sign in to Firebase Auth, they cannot write anything.

---

## 📤 Upload pipeline (gallery)

When you tap the upload FAB:

1. **System photo picker** opens (no permissions required on Android 13+)
2. **On a background thread**, the chosen image is:
   - decoded with `inSampleSize` to avoid OOM on huge photos
   - rotated based on EXIF orientation
   - resized to **1920px wide** (full-res) and **400px wide** (thumbnail)
   - JPEG-compressed (quality 85 / 80)
3. **Two parallel uploads** to Firebase Storage (`gallery/{timestamp}.jpg` and `_thumb.jpg`)
4. **Firestore document** created with both download URLs, optional caption, and auto-incremented `order`
5. Website's real-time listener picks it up → photo appears in the gallery within ~1 second

A typical 5MB phone photo becomes ~350KB total uploaded.

---

## 🔧 Setup

If you want to fork it and run your own version:

1. Clone the repo and open in Android Studio
2. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
3. Enable Firestore, Authentication (Email/Password), and Storage
4. Create your admin user under **Authentication → Users**
5. Copy your admin user's UID and paste it into `AuthHelper.java` (and the Firestore + Storage rules)
6. Drop your `google-services.json` from Firebase into the `app/` folder
7. Run on a device or emulator

The first launch shows the login screen — sign in with your admin credentials. After that, Firebase persists the session and the app skips the login screen on future launches. You can sign out anytime from the toolbar menu.

---

## 🧠 What I learned

- How to combine a real-time cloud database with a local SQLite cache without conflict
- Building drag-and-drop reorder with `ItemTouchHelper` and committing the result as a Firestore batch write so it's atomic
- That offline-first UI feels noticeably better — chapters appear from cache instantly while Firestore catches up
- Material 3's color system (`MaterialColors.getColor()`) makes themed drag states elegant in just a few lines
- How to design a clean dual-mode auth flow (admin + guest) where the UI and the backend rules both enforce permissions
- How to do image processing right — EXIF rotation, `inSampleSize`, JPEG quality tradeoffs, dual-size uploads for thumbnails

---

## 👤 Made by

**Her Mowtiie.**

Made with 🌻 for someone who already knows it's hers.
