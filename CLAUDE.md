# CLAUDE.md — Faithfully-App

**Read this file first.** It contains everything you need to know to work in this repo effectively.

---

## What this project is

An Android app for managing content on the Faithfully-Web sister project. Java + XML views (no Kotlin, no Compose). Uses Firebase Firestore + Storage + Auth. Single admin (the owner) writes; a specific reader (Ali) reads real data; everyone else sees mock data.

## Three-role auth model (critical)

`AuthHelper.currentRole()` returns one of three values, defined in `AuthHelper.Role` enum:

- **`ADMIN`** — signed in as the admin UID (owner). Full CRUD. Sees FAB, edit menus, drag-to-reorder, and the Backup & Restore section in Settings.
- **`READER`** — signed in as Ali's UID. Reads real Firestore data. **No writes, with one deliberate exception: she can record voice clips for herself** (`AddVoiceActivity`, see "Voice"). No edit affordances. Shows a "🩷 Signed in — read-only" banner via `NoticeAdapter` (not on the Voice screen, where she can add).
- **`GUEST`** — everyone else (not signed in, or signed in as a non-allowlisted UID). Sees mock data loaded from `assets/mock_data.json` by `MockDataLoader`. No writes. Shows a "👀 Demo mode — showing sample content" banner.

Every activity that displays data reads `role = AuthHelper.currentRole()` in `onCreate` and branches:
- `role == ADMIN` → Firestore + write UI enabled
- `role == READER` → Firestore + read-only UI (NoticeAdapter banner)
- `role == GUEST` → MockDataLoader + read-only UI (NoticeAdapter banner)

**Every write callback (`onEdit`, `onDelete`, `onReordered`, etc.) must guard with `if (role != Role.ADMIN) return;`** as belt and suspenders — the UI already hides these affordances, but a bug shouldn't be able to write.

The allow-list UIDs are `AuthHelper.ADMIN_UID` and `AuthHelper.ALI_UID`.

## Package structure

- `com.mowtiie.faithfully.ui.activities.*` — Activities (extend `FaithfullyActivity` base)
- `com.mowtiie.faithfully.ui.adapters.*` — RecyclerView adapters
- `com.mowtiie.faithfully.data.*` — POJO models (Chapter, Card, Photo, VoiceClip)
- `com.mowtiie.faithfully.helper.*` — AuthHelper, ChapterDbHelper, NetworkHelper, MockDataLoader, WorkerClient (Cloudflare Worker calls), PhotoStorageHelper, VoiceStorageHelper, VoiceFormat

## Activity map

| Activity | Purpose | Role branching |
|---|---|---|
| `LoginActivity` | Email/password sign-in + "Continue as Guest" | Rejects non-allowlisted UIDs silently |
| `HomeActivity` | Landing screen after login: title, countdown, day counter | ADMIN/READER read `settings/home`; GUEST sample content |
| `MainActivity` | Chapters list (opened from Home) | Branches by role |
| `CardsActivity` | Cards inside a chapter | Branches by role |
| `GalleryActivity` | Photo grid + lightbox trigger | Branches by role |
| `AddChapterActivity` / `AddCardActivity` / `AddPhotoActivity` | CRUD screens | Admin only (implicit — non-admins can't get here) |
| `VoiceActivity` | Voice clips list with playback | ADMIN edits/deletes; READER plays and records; GUEST sample clips, playback off |
| `AddVoiceActivity` | Record, preview, caption and save a clip | ADMIN and READER only |
| `PhotoViewerActivity` | Full-screen photo view | All roles |
| `SettingsActivity` | App preferences, plus Backup & Restore and Clear all (admin only, hidden for others) | Any role; backup section ADMIN only |
| `AboutActivity` | App info | Any role |

## Key architectural patterns

- **View binding** everywhere. No `findViewById` in Activities.
- **EdgeToEdge + WindowInsetsCompat** in every Activity's `onCreate`. Follow the existing pattern.
- **`FaithfullyActivity` base class** for every activity — it applies the user's contrast and dynamic-colour settings. New activities must extend it, not `AppCompatActivity`.
- **Auto Backup** excludes the chapter cache database (`backup_rules.xml`, `data_extraction_rules.xml`). Keep private content out of it.
- **Firestore listeners** in activities use `addSnapshotListener(this, ...)` so they detach with the activity (Home registers/removes its own in `onStart`/`onStop`).
- **Read Firestore fields with `DocFields`**, not `getTimestamp`/`getString`/`getLong` — the typed getters throw if a field holds another type (e.g. a date saved as milliseconds), which crashes the whole list.
- **`ConcatAdapter` + `NoticeAdapter`** — used to prepend a read-only banner to lists in READER and GUEST modes. Adapter is instantiated with the message string.
- **`ChapterDbHelper` SQLite cache** — only for chapters. Cards and photos rely on Firestore's built-in offline persistence.
- **`ItemTouchHelper` drag-and-drop** — admin only. Long-press-to-drag; releases commit a `WriteBatch` reordering to Firestore.
- **`ExecutorService` for zip work** in `BackupManager` (hosted by `SettingsActivity`, which owns the file pickers) — the heavy lifting doesn't block the UI thread.

## Material 3 theming

- Colors from `md_theme_*` XML color resources — never hardcode.
- `MaterialAlertDialogBuilder` for all dialogs.
- `MaterialCardView` for cards.
- `MaterialButton` / `MaterialButton.OutlinedButton` styles.
- Drag state uses `md_theme_primaryContainer` for background; rest state uses `md_theme_surfaceContainer`.

## Firebase configuration

- Same Firebase project as the website (`faithfully-ac2cd`).
- Firestore collections: `chapters`, `cards`, `gallery`, `voice` (and `apps`, `settings`, `draw` which the website owns).
- Photos live in Cloudflare R2, not Firebase Storage — see "R2 photo storage" below. Existing photos may still point at Firebase Storage (legacy).
- Security rules enforced server-side — activities also enforce via role checks in UI.
- Composite index needed for `cards` collection: `chapterId` ASC + `order` ASC. Created via clickable link in the FAILED_PRECONDITION error.

## Build config

- Gradle Kotlin DSL (`build.gradle.kts`).
- Signing config loaded from `keystore.properties` (git-ignored) via a helper in `app/build.gradle.kts`. Release builds without the keystore build unsigned — useful for CI.
- Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true`. ProGuard rules in `proguard-rules.pro`.
- Package: `com.mowtiie.faithfully`. Min SDK 24, target SDK 36, compile SDK 36.1.

## R2 photo storage

All photo writes go through `helper/PhotoStorageHelper` (blocking; call off the main thread; throws `SecurityException` unless role is ADMIN). It mirrors the website's `js/photo-upload.js`. The HTTP, token and error handling live in `helper/WorkerClient` (mirrors `js/worker-client.js`), shared with `VoiceStorageHelper`; failures throw the top-level `StorageException`, whose message is safe to show.

- Worker: `PUT`/`DELETE https://faithfully-upload.mowtiie-dev.workers.dev/<key>` with `Authorization: Bearer <Firebase ID token>`. No R2 secrets in the app. For `gallery/` keys the Worker only accepts `ADMIN_UID` (403 otherwise), requires a real JPEG/PNG/WebP body (max 10 MB) and **rejects requests that carry an `Origin` header** — never set one. `voice/` keys have their own rules, see "Voice".
- Keys: full `gallery/<ts>_<rand6>.jpg`, thumb `gallery/thumbs/<ts>_<rand6>.jpg`. Public URL = `PHOTO_BASE_URL + "/" + key`. Firestore `gallery` docs keep `imageUrl`, `thumbnailUrl`, `storagePath`, `thumbnailPath` (the paths are the keys).
- **Hosted vs legacy:** a photo is hosted if `imageUrl` starts with `PHOTO_BASE_URL + "/"`; anything else is legacy Firebase Storage. Reading/displaying is identical (Glide takes the URL). Deleting: hosted → Worker first, then the doc; legacy → doc first, then a best-effort Firebase Storage delete.
- Upload rolls back: if the thumb fails the full image is deleted; if the Firestore write fails both are deleted.
- `firebase.storage` stays as a dependency only for legacy deletes.

## Voice

Mirrors the website's Voice section (`js/voice.js`, `js/voice-recorder.js`).

- **Data:** Firestore `voice/{id}`: `{ audioPath, caption, duration (seconds), mimeType, size, uid, createdAt (server timestamp) }`. `audioPath` is `voice/<uid>/<ts>_<rand6>.<webm|m4a|mp4|ogg>` in a **private** R2 bucket (no public URL). Read with `DocFields` into `VoiceClip` by hand (no `toObject`, so no ProGuard rule). The sender emoji comes from `settings/draw.players[uid]`.
- **Roles:** ADMIN and READER read, play and add (`AddVoiceActivity`); only ADMIN edits the caption or deletes (guarded in `VoiceActivity`, the Worker and the Firestore rules). GUEST sees the sample clips from `mock_data.json` (`voice`, `voicePlayers`), with playback off.
- **Creating a clip must match the Firestore rule exactly:** exactly the seven fields above, `uid == auth.uid`, `audioPath` under the sender's own `voice/<uid>/` folder, `caption` a string (use `""`, never null), `createdAt` from `FieldValue.serverTimestamp()` (not `Timestamp.now()`).
- **Recording:** `MediaRecorder` to AAC in `.m4a` (`audio/mp4`, plays in every browser), mono, 48 kbps, hard cap `VoiceFormat.MAX_SECONDS` (90 s) and `MAX_BYTES` (2 MB, the Worker's limit). Needs the `RECORD_AUDIO` runtime permission. Recording stops if the screen is left.
- **Worker rules for `voice/` keys:** `PUT` any approved account into its own folder (admin may write any folder, which backup restore needs); `GET` any approved account; `DELETE` admin only; first bytes must be real WebM/MP4/Ogg audio.
- **Playback:** `VoiceStorageHelper.download` fetches the file through the Worker into `cacheDir/voice/` and `VoiceActivity` plays it with one `MediaPlayer`. The cache is cleared by `AuthHelper.signOutEverything`.
- **Delete order:** file first (Worker), then the document, so a failure can be retried. If the Firestore write fails after an upload, the new file is deleted best-effort (only the admin can; for Ali it is left behind).
- Pure helpers (keys, MIME/extension, clock text) are in `VoiceFormat`, unit-tested in `VoiceFormatTest`.

## Home screen

`HomeActivity` is where login lands; the chapters list (`MainActivity`) is one tap away ("Letters"). It mirrors the website's `js/home.js`, read-only for every role:

- Sample text lives in the `home` block of `assets/mock_data.json` (loaded by `MockDataLoader.getHomeDefaults`, same strings as the website's `HOME_MOCK`; hardcoded fallback in `HomeContentResolver.Text.fallback()`).
- ADMIN/READER watch `settings/home` (`{ title, subtitle, countdownLabel, countdownFinish, anniversaryLabel, anniversaryUnit, countdownTarget, anniversaryStart }`); a non-empty string replaces the sample text. Content stays hidden until the first snapshot. The listener is attached in `onStart` and removed in `onStop`.
- GUEST never queries Firestore. Their day counter counts from the first time they opened Home (`home_prefs`, like the website's `demoFirstVisit`).
- All merge/countdown/day-count logic is in `helper/HomeContentResolver` (pure Java, unit-tested in `HomeContentResolverTest`). Parse the Firestore doc by hand into `HomeContentResolver.Settings` — there is no model class, so no ProGuard rule.
- Sign-out / back-to-login intents use `NEW_TASK | CLEAR_TASK` so Home isn't left on the back stack. Gallery, About and Settings now have Home as their parent activity.

## Known gaps

- A few dialogs/sheets use `findViewById` (`GalleryActivity`, `CardsActivity`).
- `HomeActivity` calls `ValueAnimator.areAnimatorsEnabled()` (API 26) while `minSdk` is 24: `lintDebug` reports two `NewApi` errors there, and it would crash on Android 7.x. Voice playback and recording have only been compile-, unit-test- and R8-checked, not run on a device.

## Firestore ProGuard warnings

Model classes (`Chapter`, `Card`, `Photo`) MUST be preserved by ProGuard because Firestore uses reflection. Rules are already in `proguard-rules.pro`. If you add a new model that gets serialized to/from Firestore, add a `-keep class ...` rule for it.

## Mock data

- File: `app/src/main/assets/mock_data.json`
- Loader: `MockDataLoader` — lazy, cached in memory after first read
- Structure: `{ "home": {...}, "chapters": [...], "cards": [...], "gallery": [...], "voicePlayers": {...}, "voice": [...] }` — matches the website's `MOCK_CHAPTERS` / `MOCK_CARDS` / `MOCK_GALLERY` / `MOCK_VOICE` in `js/data/mock.js` (Faithfully-Web). Keep the chapter/card/voice text in sync with that file.
- GUEST on `MainActivity`, `CardsActivity` and `GalleryActivity` loads from the loader and never touches Firestore or the SQLite chapter cache (`showMock()` in each). The getters return copies sorted by `order`.
- `AuthHelper.signOutEverything` clears the `ChapterDbHelper` cache so a later guest never sees a previous session's chapters.
- READER and GUEST get a `NoticeAdapter.forRole(ctx, role)` banner on those three screens (text in `strings.xml`: `notice_reader`, `notice_guest`). The Gallery grid gives it a full-width span.
- To update mock content, edit this JSON file. Loader's cache resets on process restart, not per-Activity.

## Backup format

Same zip as the website's `js/backup.js`: `data.json` (`version`, `exportedAt`, and arrays `chapters`, `cards`, `gallery`, `apps`, `voice`, `settings`; each entry has `_id`, timestamps as epoch millis) + `images/{docId}.jpg` + `images/{docId}_thumb.jpg` (`_zipFull` / `_zipThumb` on gallery entries) + `audio/{docId}.<ext>` (`_zipAudio` on voice entries). Recordings are downloaded and re-uploaded through the Worker at their original `audioPath` (restoring Ali's clips needs the admin-may-write-any-folder Worker rule); in replace mode, files of voice docs not in the backup are deleted last. "Clear all" does not touch voice. `BackupManager` is ADMIN only: the Settings entries are hidden for other roles, and `SettingsActivity` and `BackupManager` both re-check the role.

- **Export:** photos are downloaded by URL (R2 or legacy). `data.json` is written last. A photo that fails to download is skipped and counted; its entry keeps its Firestore fields.
- **Import (replace mode):** parse and validate → upload photos through `PhotoStorageHelper` (new R2 keys) and write docs → delete docs not in the backup → only then delete the replaced photo files (R2 via the Worker, legacy via Firebase). Failed photos and leftover files are reported in the final toast. Timestamp fields are converted back from millis. `ChapterDbHelper.clearAll()` is called afterwards.
- **Clear all:** covers `cards`, `chapters`, `gallery` only (not `apps`/`settings`). Photo files are deleted first; if the Worker refuses, the documents are left in place so it can be retried.
- Raw-JSON backups with no `data.json` (legacy) are still accepted; they carry no images.

## Sibling repos

- **Faithfully-Web** — the public-facing website. Same Firebase project. Same UID allow list. If you change Firestore schema here, also change it there.
- **Faithful** — a separate, simpler note-taking app. Different project entirely.

## Things to be careful about

- **Never write from READER or GUEST role** — the one exception is a READER creating her own voice clip in `AddVoiceActivity` (`VoiceStorageHelper.upload` re-checks the role and the folder). Every other write callback needs `if (role != Role.ADMIN) return;` as the first line. This is enforced both in UI (hidden affordances) and in code (early return).
- **NoticeAdapter takes a message string** — don't hardcode text inside the adapter.
- **`AuthHelper.isAdmin()` is kept for backwards-compat** — new code should prefer `currentRole()` and match on the enum.
- **`ChapterDbHelper` cache** — invalidate via `dbHelper.clearAll()` after clear-all-data or import operations.
- **APK signing key** — do NOT lose or leak `alicards-release.jks`. Play Store requires the same key for all updates.
- **`google-services.json`** — git-ignored. Restrict the Android API key by SHA-1 fingerprint + package name in Google Cloud Console.

## What to do when starting a new task

1. Read this file. Confirm understanding briefly.
2. Read the specific files the task will touch. Don't guess.
3. Ask clarifying questions if the task ambiguously affects roles or role-specific rendering.
4. Propose a plan for anything that touches more than 2 files. Multi-file Android changes (activity + layout + adapter + manifest) are easy to get wrong.
5. Match existing patterns explicitly — reference specific files like "follow PhotoAdapter's pattern".
6. Never invent a new architecture. If a pattern doesn't obviously fit, ask.

## Commit style

Conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `style:`, `chore:`. Keep messages single-line when possible.
