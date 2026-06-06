<div align="center">

# 🌻 Faithfully App

### *The Android app I built to manage letters on her website.*

A small Java + XML admin app that lets me write, organize, and reorder chapters of letters that appear in real time on a public website I built for her.

**[🌐 Website repo](https://github.com/mowtiie/Faithfully-Web)** · **[📱 This repo](https://github.com/mowtiie/Faithfully-App)**

</div>

---

## 💛 Why I built this

The website I made for her ([Faithfully Website](https://github.com/mowtiie/Faithfully-Web)) needed a way for me to add new letters from anywhere — without editing JSON files or pushing to a repo every time. So I built the admin side: a small Android app, signed in silently as me, that talks to the same Firebase database the site reads from.

I picked Java and XML views instead of Kotlin and Compose on purpose — it's the stack I'm most comfortable with from my coursework, and I wanted to focus on the polish of the actual UX rather than fighting a new language while building something personal.

---

## ✨ Features

- 📖 **Chapters** — add, edit, delete, and reorder chapters that group letters into eras of our story
- 💌 **Letters** — write new ones with a built-in date picker, assign them to a chapter, edit or delete later
- 🔀 **Drag-to-reorder** — long-press any chapter or card to drag it into a new position; updates Firestore in a single batch write
- 🏷️ **Reassign chapters** — move a letter to a different chapter, or unassign it entirely, with a quick dialog
- 💾 **Offline-friendly** — chapters are cached locally in SQLite so the list opens instantly without waiting for the network
- 🎨 **Material 3 styling** — dragged cards lift with a soft color shift using `colorPrimaryContainer` from the theme
- 🔒 **Admin and guest modes** — sign in to manage everything, or continue as a guest to just browse (perfect for letting others peek at the project)

---

## 🔐 Admin vs Guest mode

The app has two modes:

| Mode | What they can do |
|---|---|
| **Admin** (me, signed in with a Firebase Auth account) | Add, edit, delete, and reorder chapters and letters |
| **Guest** (anyone, no login needed) | Browse chapters, expand letters to read — **no write access** |

A small "👀 Read-only mode" banner appears at the top of the home screen when in guest mode, and all FABs, edit buttons, delete buttons, and drag handles are hidden. Even if someone tried to bypass the UI checks, Firestore security rules reject any write attempt that doesn't come from my specific admin user UID — so the app is locked down at the database layer too.

### Default credentials for cloning

This repo ships with no working credentials. To build and run locally you'd need your own Firebase project. The **"Continue as Guest"** button on the login screen lets anyone browse without signing in.

---

## 📸 Screenshots

| Login | Home | Chapters |
|:---:|:---:|:---:|
| ![Login](screenshots/login.png) | ![Home](screenshots/home.png) | ![Chapters](screenshots/chapters.png) |

---

## 🛠️ Tech stack

| Layer | What I used |
|---|---|
| **Language** | Java |
| **UI** | XML views (no Compose) with Material Components |
| **Backend** | [Firebase Firestore](https://firebase.google.com/docs/firestore) |
| **Auth** | Firebase Auth (email/password + persistent guest flag) |
| **Local cache** | Android SQLite via `SQLiteOpenHelper` |
| **Min SDK** | 24 (Android 7.0+) |

---

## 📁 Project structure

```
app/src/main/java/com/example/alicards/
├── LoginActivity.java         # entry screen — sign in or continue as guest
├── MainActivity.java          # landing screen with two nav cards
├── ChaptersActivity.java      # list, reorder, delete chapters
├── AddChapterActivity.java    # add or edit a chapter
├── CardsActivity.java         # list cards inside a chapter
├── AddCardActivity.java       # add or edit a card
├── ChapterAdapter.java        # RecyclerView adapter for chapters
├── CardAdapter.java           # RecyclerView adapter for cards
├── Chapter.java               # POJO model
├── Card.java                  # POJO model
├── ChapterDbHelper.java       # SQLite cache for offline access
└── AuthHelper.java            # admin/guest mode detection
```

---

## 🏗️ How it fits with the website

```
   ┌──────────────────┐         ┌─────────────────┐
   │  This app        │         │  Public website │
   │  (admin only)    │         │  (read-only)    │
   └────────┬─────────┘         └────────┬────────┘
            │ writes                     │ reads
            │ (UID-restricted)           │
            └───────────┬────────────────┘
                        ▼
              ┌──────────────────┐
              │  Firestore       │
              │                  │
              │  • chapters/     │
              │  • cards/        │
              └──────────────────┘
```

Firestore security rules check the user's UID on every write. Only my admin account can modify data — so even though guests can sign in to Firebase Auth, they cannot write anything.

---

## 🔧 Setup

If you want to fork it and run your own version:

1. Clone the repo and open in Android Studio
2. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
3. Enable Firestore and Authentication (Email/Password provider)
4. Create your admin user under **Authentication → Users**
5. Copy your admin user's UID and paste it into `AuthHelper.java` (and the Firestore rules)
6. Drop your `google-services.json` from Firebase into the `app/` folder
7. Run on a device or emulator

The first launch shows the login screen — sign in with your admin credentials. After that, Firebase persists the session and the app skips the login screen on future launches. You can sign out anytime from the toolbar menu.

---

## 🧠 What I learned

- How to combine a real-time cloud database with a local SQLite cache without conflict
- Building drag-and-drop reorder with `ItemTouchHelper` and committing the result as a Firestore batch write so it's atomic
- That offline-first UI feels noticeably better — chapters appear from cache instantly while Firestore catches up
- That Material 3's color system (`MaterialColors.getColor()`) makes themed drag states elegant in just a few lines
- How to design a clean dual-mode auth flow (admin + guest) where the UI and the backend rules both enforce permissions

---

## 👤 Made by

**Her Mowtiie.**

Made with 🌻 for someone who already knows it's hers.
