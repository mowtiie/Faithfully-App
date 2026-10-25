# CLAUDE.md: Faithfully-App

**Read this file first.** It contains everything you need to know to work in this repo effectively.

---

## What this project is

An Android app for managing content on the Faithfully-Web sister project. Java + XML views (no Kotlin, no Compose). Uses Firebase Firestore + Storage + Auth. Single admin (the owner) writes; a specific reader (Ali) reads real data; everyone else sees mock data.

## Three-role auth model (critical)

`AuthHelper.currentRole()` returns one of three values, defined in `AuthHelper.Role` enum:

- **`ADMIN`**: signed in as the admin UID (owner). Full CRUD. Sees FAB, edit menus, drag-to-reorder, and the gallery editors.
- **`READER`**: signed in as Ali's UID. Reads real Firestore data. **No writes, with one deliberate exception: she can record voice clips for herself** (`AddVoiceActivity`, see "Voice"). No edit affordances. Shows a "🩷 Signed in (read-only)" banner via `NoticeAdapter` (not on the Voice screen, where she can add).
- **`GUEST`**: everyone else (not signed in, or signed in as a non-allowlisted UID). Sees mock data loaded from `assets/mock_data.json` by `MockDataLoader`. No writes. Shows a "👀 Demo mode (showing sample content)" banner.

Every screen that displays data (fragment or activity) reads `role = AuthHelper.currentRole()` once when it is created and branches:
- `role == ADMIN` → Firestore + write UI enabled
- `role == READER` → Firestore + read-only UI (NoticeAdapter banner)
- `role == GUEST` → MockDataLoader + read-only UI (NoticeAdapter banner)

**Every write callback (`onEdit`, `onDelete`, `onReordered`, etc.) must guard with `if (role != Role.ADMIN) return;`** as belt and suspenders. The UI already hides these affordances, but a bug shouldn't be able to write.

The allow-list UIDs are `AuthHelper.ADMIN_UID` and `AuthHelper.ALI_UID`.

## Package structure

- `com.mowtiie.faithfully.ui.activities.*`: Activities (extend `FaithfullyActivity` base)
- `com.mowtiie.faithfully.ui.fragments.*`: The sections shown inside `MainActivity`
- `com.mowtiie.faithfully.ui.adapters.*`: RecyclerView adapters
- `com.mowtiie.faithfully.data.*`: POJO models (Chapter, Card, Photo, VoiceClip)
- `com.mowtiie.faithfully.helper.*`: AuthHelper, ChapterDbHelper, NetworkHelper, MockDataLoader, WorkerClient (Cloudflare Worker calls), PhotoStorageHelper, VoiceStorageHelper, VoiceFormat, ReaderSession, GallerySession

## Navigation

One host activity and a navigation drawer. `MainActivity` holds the toolbar, a `DrawerLayout` + `NavigationView` (`menu_drawer.xml`) and a `NavHostFragment` driven by `res/navigation/nav_graph.xml` (Navigation 2.9). The drawer lists Home, Chapters, Gallery, Voice, Settings, About and Sign in/out; the header shows the role.

- **Top-level destinations** (hamburger icon, no Back arrow) are listed in the `AppBarConfiguration` in `MainActivity`. To add a section, add it there, in the nav graph and in `menu_drawer.xml` using the same id (`xxxFragment`).
- `cardsFragment` (the letters of one chapter) is not in the drawer. It takes `chapter_id` and `chapter_title` arguments, its label is `{chapter_title}`, and the drawer keeps Chapters highlighted while it is open.
- **Up is Back.** `FaithfullyActivity.onSupportNavigateUp()` calls the back dispatcher, so the Back arrow in the form screens returns to wherever the user came from. The host overrides it with `NavigationUI.navigateUp`. Manifest `parentActivityName` values only point at `MainActivity`.
- **Drawer back priority:** `MainActivity.drawerBack` is registered after the NavHostFragment and is enabled only while the drawer is open, so Back closes the drawer first.
- Sign out and Sign in (guest) are drawer actions handled by `MainActivity`, not destinations.
- Toolbar menus belong to the fragment: register a `MenuProvider` on the activity with the view lifecycle (see `VoiceFragment`).

## Screen map

| Screen | Kind | Purpose | Role branching |
|---|---|---|---|
| `LoginActivity` | Activity | Email/password sign-in + "Continue as Guest" | Rejects non-allowlisted UIDs silently |
| `MainActivity` | Activity | The drawer host | Shows the role in the drawer header |
| `HomeFragment` | Fragment | A simple welcome: title, countdown, day counter | ADMIN/READER read `settings/home`; GUEST sample content |
| `ChaptersFragment` | Fragment | Chapters list | Branches by role |
| `CardsFragment` | Fragment | Letters inside a chapter | Branches by role |
| `GalleryFragment` | Fragment | Photo grid + viewer trigger | Branches by role |
| `VoiceFragment` | Fragment | Voice clips list with playback, filter and sort in the top bar | ADMIN edits/deletes; READER plays and records; GUEST sample clips, playback off |
| `SettingsFragment` | Fragment | App preferences (theme, contrast, dynamic color) | Any role |
| `AboutFragment` | Fragment | App info | Any role |
| `AddChapterActivity` / `AddCardActivity` | Activity | CRUD screens | Admin only (implicit, since non-admins can't get here) |
| `AddPhotoActivity` | Activity | Choose up to 20 photos, a caption each, upload with per-photo status and retry | Admin only |
| `AddVoiceActivity` | Activity | Record, preview, caption and save a clip | ADMIN and READER only |
| `ReaderActivity` | Activity | Full-screen letter reader: swipe or Previous/Next through a chapter's letters, progress bar, "Back to chapter" on the last one | All roles. READER and GUEST open it by tapping a letter; ADMIN uses the Read button on the expanded card (tapping expands it for the edit actions) |
| `PhotoViewerActivity` | Activity | Full-screen photos you swipe through, with a "3 of 12" counter | All roles |

The forms, the reader and the photo viewer stay separate activities on purpose (full screen, own Back behavior).

## Key architectural patterns

- **View binding** everywhere. In fragments, inflate in `onCreateView` and set `binding = null` in `onDestroyView`.
- **EdgeToEdge + WindowInsetsCompat** in every Activity's `onCreate`. Follow the existing pattern. Inside the host, `MainActivity` applies the side and bottom insets once, so fragments do not.
- **`FaithfullyActivity` base class** for every activity: it applies the user's contrast and dynamic-colour settings. New activities must extend it, not `AppCompatActivity`.
- **Auto Backup** excludes the chapter cache database (`backup_rules.xml`, `data_extraction_rules.xml`). Keep private content out of it.
- **Firestore listeners** in activities use `addSnapshotListener(this, ...)` so they detach with the activity. In fragments keep the `ListenerRegistration` and remove it in `onDestroyView` (Home registers/removes its own in `onStart`/`onStop`), and return early in the callback when `binding == null`. Never pass a fragment to `addSnapshotListener`.
- **Read Firestore fields with `DocFields`**, not `getTimestamp`/`getString`/`getLong`: the typed getters throw if a field holds another type (e.g. a date saved as milliseconds), which crashes the whole list.
- **`ConcatAdapter` + `NoticeAdapter`**: used to prepend a read-only banner to lists in READER and GUEST modes. Adapter is instantiated with the message string.
- **`ChapterDbHelper` SQLite cache**: only for chapters. Cards and photos rely on Firestore's built-in offline persistence.
- **`ItemTouchHelper` drag-and-drop**: admin only. Long-press-to-drag; releases commit a `WriteBatch` reordering to Firestore.

## Material 3 theming

- Colors from `md_theme_*` XML color resources: never hardcode.
- `MaterialAlertDialogBuilder` for all dialogs.
- `MaterialCardView` for cards.
- `MaterialButton` / `MaterialButton.OutlinedButton` styles.
- Drag state uses `md_theme_primaryContainer` for background; rest state uses `md_theme_surfaceContainer`.

## Firebase configuration

- Same Firebase project as the website (`faithfully-ac2cd`).
- Firestore collections: `chapters`, `cards`, `gallery`, `voice` (and `apps`, `settings`, `draw` which only the website uses).
- Photos live in Cloudflare R2, not Firebase Storage: see "R2 photo storage" below. Existing photos may still point at Firebase Storage (legacy).
- Security rules enforced server-side. Activities also enforce via role checks in UI.
- Composite index needed for `cards` collection: `chapterId` ASC + `order` ASC. Created via clickable link in the FAILED_PRECONDITION error.

## Build config

- Gradle Kotlin DSL (`build.gradle.kts`).
- Signing config loaded from `keystore.properties` (git-ignored) via a helper in `app/build.gradle.kts`. Release builds without the keystore build unsigned, useful for CI.
- Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true`. ProGuard rules in `proguard-rules.pro`.
- Package: `com.mowtiie.faithfully`. Min SDK 30 (Android 11), target SDK 36, compile SDK 36.1.

## R2 photo storage

All photo writes go through `helper/PhotoStorageHelper` (blocking; call off the main thread; throws `SecurityException` unless role is ADMIN). It mirrors the website's `js/photo-upload.js`. The HTTP, token and error handling live in `helper/WorkerClient` (mirrors `js/worker-client.js`), shared with `VoiceStorageHelper`; failures throw the top-level `StorageException`, whose message is safe to show.

- Worker: `PUT`/`DELETE https://faithfully-upload.mowtiie-dev.workers.dev/<key>` with `Authorization: Bearer <Firebase ID token>`. No R2 secrets in the app. For `gallery/` keys the Worker only accepts `ADMIN_UID` (403 otherwise), requires a real JPEG/PNG/WebP body (max 10 MB) and **rejects requests that carry an `Origin` header**. Never set one. `voice/` keys have their own rules, see "Voice".
- Keys: full `gallery/<ts>_<rand6>.jpg`, thumb `gallery/thumbs/<ts>_<rand6>.jpg`. Public URL = `PHOTO_BASE_URL + "/" + key`. Firestore `gallery` docs keep `imageUrl`, `thumbnailUrl`, `storagePath`, `thumbnailPath` (the paths are the keys).
- **Hosted vs legacy:** a photo is hosted if `imageUrl` starts with `PHOTO_BASE_URL + "/"`; anything else is legacy Firebase Storage. Reading/displaying is identical (Glide takes the URL). Deleting: hosted → Worker first, then the doc; legacy → doc first, then a best-effort Firebase Storage delete.
- Upload rolls back: if the thumb fails the full image is deleted; if the Firestore write fails both are deleted.
- `firebase.storage` stays as a dependency only for legacy deletes.

## Voice

Mirrors the website's Voice section (`js/voice.js`, `js/voice-recorder.js`).

- **Data:** Firestore `voice/{id}`: `{ audioPath, caption, duration (seconds), mimeType, size, uid, createdAt (server timestamp) }`. `audioPath` is `voice/<uid>/<ts>_<rand6>.<webm|m4a|mp4|ogg>` in a **private** R2 bucket (no public URL). Read with `DocFields` into `VoiceClip` by hand (no `toObject`, so no ProGuard rule). The sender emoji comes from `settings/draw.players[uid]`.
- **Roles:** ADMIN and READER read, play and add (`AddVoiceActivity`); only ADMIN edits the caption or deletes (guarded in `VoiceFragment`, the Worker and the Firestore rules). GUEST sees the sample clips from `mock_data.json` (`voice`, `voicePlayers`), with playback off.
- **Creating a clip must match the Firestore rule exactly:** exactly the seven fields above, `uid == auth.uid`, `audioPath` under the sender's own `voice/<uid>/` folder, `caption` a string (use `""`, never null), `createdAt` from `FieldValue.serverTimestamp()` (not `Timestamp.now()`).
- **Recording:** `MediaRecorder` to AAC in `.m4a` (`audio/mp4`, plays in every browser), mono, 48 kbps, hard cap `VoiceFormat.MAX_SECONDS` (90 s) and `MAX_BYTES` (2 MB, the Worker's limit). Needs the `RECORD_AUDIO` runtime permission. Recording stops if the screen is left.
- **Worker rules for `voice/` keys:** `PUT` any approved account into its own folder (the app only ever uses its own; the website's restore lets the admin write any folder); `GET` any approved account; `DELETE` admin only; first bytes must be real WebM/MP4/Ogg audio.
- **Playback:** `VoiceStorageHelper.download` fetches the file through the Worker into `cacheDir/voice/` and `VoiceFragment` plays it with one `MediaPlayer`. The cache is cleared by `AuthHelper.signOutEverything`.
- **Delete order:** file first (Worker), then the document, so a failure can be retried. If the Firestore write fails after an upload, the new file is deleted best-effort (only the admin can; for Ali it is left behind).
- Pure helpers (keys, MIME/extension, clock text) are in `VoiceFormat`, unit-tested in `VoiceFormatTest`.
- **Filter and sort** (mirrors the website): two menus in the top app bar (`menu_voice.xml`). Filter lists All plus one entry per sender, with counts; both accounts always get an entry, and the entries are rebuilt in `onPrepareMenu`. Sort is newest, oldest, longest or shortest. `VoiceFragment` keeps the full list in `allClips` and a filtered, sorted copy in `clips` (the adapter's list), and calls `invalidateOptionsMenu()` after each change; a clip still waiting for its server timestamp counts as the newest. Entries read the sender emoji from `settings/draw.players`, falling back to "Me" / "Them".

## Reader and photo viewer

- Both are fed through small in-memory holders instead of Intent extras, because a chapter of letters can be larger than the Intent limit: `ReaderSession` (set by `CardsFragment.onRead`) and `GallerySession` (set by `GalleryFragment.onTap`). Mirrors the website's `js/letter-store.js`. If the holder is empty (process death) the reader finishes and the viewer falls back to the single `image_url` extra.
- Both use `ViewPager2` (it comes in through the Material dependency).
- Set toolbar titles through `getSupportActionBar()`, not `binding.toolbar.setTitle`: after `setSupportActionBar` the toolbar title is reset to the app name.
- Not done yet compared with the website: next-chapter at the end of the reader, print, and pinch-to-zoom in the photo viewer.

## Adding photos

`AddPhotoActivity` (admin only, opened from the Gallery's add button) mirrors the website's photo dialog: pick up to 20 photos at once (`PickMultipleVisualMedia`), a caption for each, then they are compressed, uploaded and saved one after another with a status per photo (`PendingPhotoAdapter`). A photo that fails keeps its place and can be retried without redoing the others; each photo rolls back its own R2 files if its Firestore write fails (see "R2 photo storage"). The next `order` is read once, then incremented per saved photo. Captions and removal are locked while uploading.

## Home screen

`HomeFragment` is the start destination (login lands on `MainActivity`, which opens it); every other section is one tap away in the drawer. It is a simple welcome and mirrors the website's `js/home.js`, read-only for every role:

- Sample text lives in the `home` block of `assets/mock_data.json` (loaded by `MockDataLoader.getHomeDefaults`, same strings as the website's `HOME_MOCK`; hardcoded fallback in `HomeContentResolver.Text.fallback()`).
- ADMIN/READER watch `settings/home` (`{ title, subtitle, countdownLabel, countdownFinish, anniversaryLabel, anniversaryUnit, countdownTarget, anniversaryStart }`); a non-empty string replaces the sample text. Content stays hidden until the first snapshot. The listener is attached in `onStart` and removed in `onStop`.
- GUEST never queries Firestore. Their day counter counts from the first time they opened Home (`home_prefs`, like the website's `demoFirstVisit`).
- All merge/countdown/day-count logic is in `helper/HomeContentResolver` (pure Java, unit-tested in `HomeContentResolverTest`). Parse the Firestore doc by hand into `HomeContentResolver.Settings`. There is no model class, so no ProGuard rule.
- Sign out and back-to-login use `NEW_TASK | CLEAR_TASK` so the host is not left on the back stack.

## Known gaps

- A few dialogs/sheets use `findViewById` (`GalleryFragment`, `CardsFragment`, `VoiceFragment`).
- Voice playback and recording, and every admin-only screen (multi-photo upload), have only been compile-, unit-test- and R8-checked, not run signed in. `minSdk` was raised from 24 to 30, which also removed the old `NewApi` lint errors from `ValueAnimator.areAnimatorsEnabled()` in `HomeFragment` (API 26).

## Firestore ProGuard warnings

Model classes (`Chapter`, `Card`, `Photo`) MUST be preserved by ProGuard because Firestore uses reflection. Rules are already in `proguard-rules.pro`. If you add a new model that gets serialized to/from Firestore, add a `-keep class ...` rule for it.

## Mock data

- File: `app/src/main/assets/mock_data.json`
- Loader: `MockDataLoader` (lazy, cached in memory after first read)
- Structure: `{ "home": {...}, "chapters": [...], "cards": [...], "gallery": [...], "voicePlayers": {...}, "voice": [...] }`. It matches the website's `MOCK_CHAPTERS` / `MOCK_CARDS` / `MOCK_GALLERY` / `MOCK_VOICE` in `js/data/mock.js` (Faithfully-Web). Keep the chapter/card/voice text in sync with that file.
- GUEST on `ChaptersFragment`, `CardsFragment`, `GalleryFragment` and `VoiceFragment` loads from the loader and never touches Firestore or the SQLite chapter cache (`showMock()` in each). The getters return copies sorted by `order`.
- `AuthHelper.signOutEverything` clears the `ChapterDbHelper` cache so a later guest never sees a previous session's chapters.
- READER and GUEST get a `NoticeAdapter.forRole(ctx, role)` banner on those three screens (text in `strings.xml`: `notice_reader`, `notice_guest`). The Gallery grid gives it a full-width span.
- To update mock content, edit this JSON file. Loader's cache resets on process restart, not per-Activity.

## Backup and restore

The app has no backup, restore or clear-all. They were removed on purpose: backup and restore live on the website only (`js/backup.js`, the admin's Backup & restore button on Home), and clear-all was removed with them because its own warning told you to export a backup first. The git history before the "remove backup and restore" commit has the old `BackupManager` if it is ever needed again.

## Sibling repos

- **Faithfully-Web**: the public-facing website. Same Firebase project. Same UID allow list. If you change Firestore schema here, also change it there.
- **Faithful**: a separate, simpler note-taking app. Different project entirely.

## Things to be careful about

- **Never write from READER or GUEST role**: the one exception is a READER creating her own voice clip in `AddVoiceActivity` (`VoiceStorageHelper.upload` re-checks the role and the folder). Every other write callback needs `if (role != Role.ADMIN) return;` as the first line. This is enforced both in UI (hidden affordances) and in code (early return).
- **NoticeAdapter takes a message string**: don't hardcode text inside the adapter.
- **`AuthHelper.isAdmin()` is kept for backwards-compat**: new code should prefer `currentRole()` and match on the enum.
- **`ChapterDbHelper` cache**: invalidate via `dbHelper.clearAll()` on sign-out.
- **APK signing key**: do NOT lose or leak `alicards-release.jks`. Play Store requires the same key for all updates.
- **`google-services.json`**: git-ignored. Restrict the Android API key by SHA-1 fingerprint + package name in Google Cloud Console.

## What to do when starting a new task

1. Read this file. Confirm understanding briefly.
2. Read the specific files the task will touch. Don't guess.
3. Ask clarifying questions if the task ambiguously affects roles or role-specific rendering.
4. Propose a plan for anything that touches more than 2 files. Multi-file Android changes (activity + layout + adapter + manifest) are easy to get wrong.
5. Match existing patterns explicitly: reference specific files like "follow PhotoAdapter's pattern".
6. Never invent a new architecture. If a pattern doesn't obviously fit, ask.

## Commit style

Conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `style:`, `chore:`. Keep messages single-line when possible.
