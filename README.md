<div align="center">

# 🌻 Faithfully App

### *The Android app I built to manage her letters, memories, and photos.*

A small Java + XML app that lets me write letters, organize them into chapters, upload cat photos, and swap little voice messages, all syncing in real time to a public website I built for her. Fully private for the two of us, with a friendly demo mode for everyone else.

**[🌐 Website repo](https://github.com/mowtiie/Faithfully-Web)** · **[🌐 Live site](https://alliyannah.love)**

</div>

---

## 💛 Why I built this

The website I made for her ([Faithfully Web](https://github.com/mowtiie/Faithfully-Web)) needed a way for me to add new letters and photos from anywhere, without editing JSON files or pushing to a repo every time. So I built the admin side: a small Android app, signed in silently as me, that talks to the same Firebase backend the site reads from.

I picked Java and XML views over Kotlin and Compose on purpose. It's the stack I'm most comfortable with, and I wanted to spend my time on how it feels to use rather than fighting a new language.

---

## ✨ Features

### 🏠 Home
- 🎂 Live countdown to the second, and a days-together counter
- 🌻 Text and dates come from the same `settings/home` document the website reads, so the two always agree
- 🌙 Light and dark mode, three contrast levels, and optional dynamic color

### 💌 Letters
- 📖 Chapters that group letters into eras of our story: add, edit, delete, and reorder
- 💌 Letters with a built-in date picker, assigned to a chapter and editable later
- 🔀 Long-press anything to drag it into a new position

### 🐱 Gallery
- 🖼 Upload with the system photo picker, no storage permission needed
- 🪶 Photos are resized and compressed automatically, with a thumbnail for the grid
- 📝 Optional captions, editable from the long-press menu
- ☁️ Photos live on Cloudflare R2, the same place the website serves them from

### 🎙️ Voice
- 🎤 Record a short voice message (up to 90 seconds), preview it, add a caption, and send
- 🐝🌻 Each message shows who sent it, and either of us can record
- 🔒 Recordings are private: they play through a signed-in request, never from a public link
- 🗑️ Only the admin can edit a caption or delete a message

### 💾 Backup & Restore
- 📦 Export everything (chapters, letters, photos, voice messages, and settings) to one `.zip`
- ♻️ Restore from a backup, using the same format as the website's backup
- 🔒 Admin only, found in Settings

### 🔐 Auth & Demo mode
- 🔑 Email/password sign-in; only the two allow-listed accounts unlock the real content
- 👑 **Admin** (me): add, edit, delete, reorder, upload, and back up
- 🩷 **Reader** (her): sees everything for real, read-only except for recording her own voice messages
- 👀 **Guest** (everyone else): placeholder chapters, sample letters, and stock cat photos, so visitors can see the layout without seeing anything personal. It never touches Firebase

### 🧰 Under the hood
- ⚡ Real-time updates through Firestore listeners
- 💾 Chapters are cached in SQLite so the list opens instantly, and the cache is cleared on sign-out
- 🎨 Material 3 styling with theme-aware colors
- 🔒 Two-layer security: role checks in the app, plus Firestore and Worker rules on the server

---

## 📸 Screenshots

| Login | Home | Letters | Dark mode |
|:---:|:---:|:---:|:---:|
| ![Login](metadata/en-US/images/screenshots/login.jpg) | ![Home](metadata/en-US/images/screenshots/home.jpg) | ![Letters](metadata/en-US/images/screenshots/letters.jpg) | ![Dark mode](metadata/en-US/images/screenshots/dark-mode.jpg) |

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
| **Language & UI** | Java, XML views (no Compose), and Material 3 components |
| **Database** | [Firebase Firestore](https://firebase.google.com/docs/firestore) (real-time NoSQL) |
| **File storage** | [Cloudflare R2](https://developers.cloudflare.com/r2/) behind a small upload Worker, the same one the website uses (older photos still on [Firebase Storage](https://firebase.google.com/docs/storage)) |
| **Auth** | [Firebase Auth](https://firebase.google.com/docs/auth): email/password. The same ID token authorizes the Worker |
| **Local cache** | Android SQLite via `SQLiteOpenHelper` |
| **Images** | [Glide](https://github.com/bumptech/glide) to load, plus `Bitmap` and `ExifInterface` to resize |
| **Min SDK** | 24 (Android 7.0+) |
| **Companion** | [Faithfully Web](https://github.com/mowtiie/Faithfully-Web), the public website |

---

## 🏗️ Architecture

```
      ┌──────────────────┐         ┌─────────────────┐         ┌─────────────────┐
      │  Admin           │         │  Reader         │         │  Everyone else  │
      │  (signed in)     │         │  (signed in)    │         │  (demo mode)    │
      └────────┬─────────┘         └────────┬────────┘         └────────┬────────┘
               │ reads + writes             │ reads, records voice      │ reads
               │ (UID-restricted)           │ (UID-restricted)          │ sample content
               │                            │                           │ from mock_data.json
               └───────────┬────────────────┘                           │
                           ▼                                            ▼
             ┌────────────────────────────────┐             (no Firebase call)
             │  Firebase                      │
             │   • Firestore                  │
             │     - chapters/                │
             │     - cards/                   │
             │     - gallery/                 │
             │     - voice/ (both can add)    │
             │     - settings/home            │
             │   • Auth                       │
             │     - allow-listed UIDs only   │
             └────────────────────────────────┘

             ┌────────────────────────────────┐
  Admin ───► │  Upload Worker  (ID token)     │ ───► Cloudflare R2
  and Ali    └────────────────────────────────┘       - gallery/*.jpg (public)
  (voice)                                             - voice/*.m4a (private)
```

The admin account writes everything except voice messages, which either of us can add for ourselves. Firestore rules check the UID on every write and the Worker checks the Firebase ID token, so other people can sign in but cannot change anything. Voice messages sit in a private bucket and are fetched through the Worker, never by a public link. The website reads the same Firestore documents and the same R2 files.

---

## 🔧 Running it yourself

If you want to fork it and run your own version:

1. Clone the repo and open it in Android Studio
2. Create a Firebase project and enable Firestore and Authentication (Email/Password)
3. Create your admin user, then put your admin's UID (and the reader's, if you have one) in `AuthHelper.java` and write matching Firestore security rules
4. Drop your `google-services.json` into the `app/` folder
5. Set up an R2 bucket and an upload Worker that only accepts your Firebase ID token, then point `WorkerClient.java` and `PhotoStorageHelper.java` at them
6. Run on a device or emulator

Release builds read their signing details from a git-ignored `keystore.properties`; without it they build unsigned, which is handy for CI.

Without signing in, you'll still see the app, just guest mode with the placeholder content from `app/src/main/assets/mock_data.json`.

---

## 👤 Made by

**Her Mowtiie.**

Made with 🌻 for someone who already knows it's hers.
