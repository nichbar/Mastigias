# SimpleTag Core Features Specification & Replication Plan

This document details the core features, architectural specifications, and implementation roadmap to replicate the core product of **SimpleTag** (`dev.secam.simpletag`) in a new Android project.

---

## 1. Core Features Specification

### 1.1 Audio Discovery & Media Scanning
* **MediaStore Content Query**:
  * Scans device audio via `ContentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)`.
  * Selection clause: `${MediaStore.Audio.Media.IS_MUSIC} != 0`.
  * Sort order: `${MediaStore.Audio.Media.DATE_ADDED} DESC`.
  * Projection:
    * API 29+: `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `DATA`, `TRACK`, `DURATION`.
    * API < 29: `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `DATA`, `TRACK`.
* **Supported Audio Containers (`COMPATIBLE_TYPES`)**:
  * `mp3`, `wav`, `wave`, `dsf`, `aiff`, `aif`, `aifc`, `wma`, `ogg`, `ogx`, `mp4`, `m4a`, `m4p`, `flac`, `aac`, `opus`.
* **Hybrid Fast Scanning Pipeline**:
  * **MP3 & FLAC**: Parsed directly from MediaStore cursor columns (`title`, `artist`, `album`, `track`, `duration`). Avoids opening physical files during initial discovery.
  * **Other Formats**: Parsed concurrently on background coroutines (`Dispatchers.Default`) using the native TagLib audio tagging engine (`TagLibEngine.read()`) with bounded parallel dispatch (`async { ... }` / `awaitAll()`).
  * **Fallback**: If deep audio tagging read fails, cursor values are retained as fallbacks and the error is recorded in the scan log.
* **Lazy On-Demand Enrichment**:
  * `hasArtwork`: Initialized to `null` on list creation. When an item becomes visible in the Compose `LazyColumn`, a `LaunchedEffect` opens the file header to test `tag.firstArtwork != null`.
  * `duration`: For albums without pre-loaded duration, `LaunchedEffect` queries `MediaMetadataRetriever` on `MediaStore.Audio.Media.getContentUri("external", id)`.
* **Folder Filtering (Include / Exclude)**:
  * User selects folders via the Storage Access Framework (SAF) folder picker (`Intent.ACTION_OPEN_DOCUMENT_TREE`).
  * SAF Tree URIs are converted to filesystem paths (`uriToPath()`):
    * Primary volume: `/tree/primary:Music` $\rightarrow$ `/storage/emulated/0/Music`.
    * Secondary volumes: `/tree/<uuid>:<subpath>` $\rightarrow$ `/storage/<uuid>/<subpath>`.
  * Filter modes: `FolderSelectMode.Include` or `FolderSelectMode.Exclude`.
  * Evaluated per file: `checkPath(pathList, mode, path)`. Empty folder list defaults to allowing all audio.
* **Post-Write Media Synchronization**:
  * After editing metadata, triggers `MediaScannerConnection.scanFile(context, paths, mimeTypes, null)` with exact 1:1 MIME type mappings.
  * Invalidates Coil image memory cache entries by MediaStore ID.
  * Re-reads modified audio files with the tag engine, updates the in-memory map, and increments the repository version token to trigger UI recomposition.

---

### 1.2 Track Listing, Sorting & Grouping
* **Flat Song List**:
  * Standard view when sorted by `Title` or `Artist`.
  * Built using Jetpack Compose `LazyColumn`, keyed by `MusicData.id`.
  * Displays 48dp cover art thumbnail, title, and `"album - artist"`.
  * Default placeholder music note icon when `hasArtwork != true`.
* **Album Accordion View**:
  * Activated when sort order is set to `SortOrder.Album`.
  * Groups tracks by `album` name into `albumMap: Map<String, List<MusicData>>`.
  * Expandable accordion header showing album cover, album title, and artist.
  * Children sorted by track number (`it.track`). Child rows display track index badge and formatted duration (`mm:ss`).
  * Tapping album header toggles smooth vertical accordion expansion (`ANIMATION_DURATION = 200ms`).
* **UX Controls**:
  * **Scroll-to-Top**: Animated Floating Action Button appearing when `firstVisibleItemIndex != 0`.
  * **Pull-to-Refresh**: Material 3 `PullToRefreshBox` re-scans the system MediaStore.

---

### 1.3 Search & Library Filtering
* **Real-Time Search**:
  * SearchBar component in the pinned top bar.
  * In-memory, case-insensitive substring matching against `title`, `artist`, and `album`.
  * In album view: matches against album name.
* **Sorting Options**:
  * Dialog offering combinations of:
    * Orders: `Title`, `Artist`, `Album`.
    * Directions: `Ascending`, `Descending`.
  * Optional "Remember sort" setting persisted in DataStore.
* **Untagged Filter**:
  * Dedicated toggle filter displaying only untagged files (`!song.tagged`).
  * Tagged definition: must have non-blank `title`, `artist`, and `album` (and artist is not `"<unknown>"`).

---

### 1.4 Multi-Selection & Batch Tag Editing
* **Multi-Select Trigger**:
  * Long-press on a single track enters selection mode and adds the track.
  * Long-press on an album header toggles selection of all songs in that album.
* **Selection Interface**:
  * Contextual top bar showing selected item count, cancel button, and Edit action.
  * Checkmark badge overlay (`SelectCheckCircle`) rendered over album thumbnails.
  * Deselecting all items automatically exits multi-select mode.
* **Batch Tag Editing Mechanics**:
  * Transitions to `EditorScreen` with multiple items.
  * **Selective Field Updates**: Each field row is paired with an enabled checkbox:
    * Disabled checkbox $\rightarrow$ Displays `<unchanged>` placeholder; field is skipped during write.
    * Enabled checkbox $\rightarrow$ Field is editable; entered value replaces metadata across all selected files.
  * **Artwork Batch Toggle**: Dedicated toggle to enable/disable cover art replacement across the batch.
  * **Select All / Deselect All**: Single toolbar action toggling all checkboxes at once.
  * **Safety Isolation**: Lyrics are explicitly skipped during batch operations. Each file write is isolated in a `try/catch` block so individual file failures do not halt the batch.

---

### 1.5 Audio Metadata & Tag Editing Engine (TagLib C++ via JNI)
* **Core Engine**: TagLib compiled via Android NDK / CMake with Kotlin JNI wrapper (`TagLibEngine`). Native stream-based reading and writing without loading whole audio files into memory.
* **Supported Containers**:
  * MP3, MP4, M4A, M4P, AAC, OGG, OGX, Opus, FLAC, WAV, AIFF, DSF, WMA.
* **Tag Implementations**:
  * FLAC: `TagLib::FLAC::File` / FLAC Xiph Comment & Picture blocks
  * MP4 / M4A / AAC: `TagLib::MP4::File` / MP4 Item List
  * OGG / OGX / Opus: `TagLib::Ogg::Vorbis` / `TagLib::Ogg::Opus` (VorbisComment)
  * WMA: `TagLib::ASF::File`
  * MP3 / WAV / DSF / AIFF: `TagLib::ID3v2::Tag` (ID3v2.4)
* **50+ Tag Fields (`SimpleTagField`)**:
  * **Basic Fields (0–9)**: Title, Artist, Album, Year, Track, Genre, Album Artist, Composer, Disc Number, Comment.
  * **Advanced Fields (10+)**: ReplayGain Track/Album, Lyrics, AcoustID (Fingerprint, ID), MusicBrainz IDs (Artist, Disc, Release, Track, Work, etc.), Sort Orders (Title, Artist, Album, Composer), BPM, Musical Key, Mood, Tempo, Barcode, Catalog Number, ISRC, Web URLs (Discogs, Wikipedia, Official), Custom Fields (1–5).
* **Format-Specific Gating**:
  * ReplayGain fields are conditionally visible only on supported containers (`mp3`, `wav`, `wave`, `dsf`, `wma`, `ogg`, `flac`).
* **Deletion Semantics**:
  * Setting a field text to empty issues `tag.deleteField(key)`.

---

### 1.6 Artwork Management Pipeline
* **Thumbnail Image Loader (Coil 3)**:
  * Custom `MusicDataFetcher` reads raw bytes via `tag.firstArtwork.binaryData`.
  * Two-pass decoding: uses `BitmapFactory.Options.inJustDecodeBounds` to determine dimensions and calculates `inSampleSize` down to **120×120 pixels** before allocating bitmap memory.
  * Custom `MusicDataKeyer` caches by MediaStore ID.
  * Memory cache configured at 20% heap; disk cache at `cacheDir/image_cache` (25–50 MB recommended).
* **Cover Art Selection**:
  * Uses Android Photo Picker (`PickVisualMedia.ImageOnly`).
  * Reads binary bytes directly through `ContentResolver.openInputStream(uri)` (never relies on the redacted `MediaStore.Images.Media.DATA` column).
  * Resolves MIME type from binary magic bytes (`ImageFormats.getMimeTypeForBinarySignature`).
* **Container-Specific Artwork Encoding**:
  * **FLAC**: Requires `width`, `height`, `mimeType`, and `pictureType` (Front Cover = 3) to build the FLAC picture metadata block.
  * **VorbisComment (OGG / Opus)**: Encodes binary bytes to Base64 and writes to `COVERART` and `COVERARTMIME` fields.
  * **ID3v2 / MP4 / ASF**: Native TagLib picture / artwork embedding.
  * **Delete Artwork**: Executes `tag.deleteArtworkField()`.

---

### 1.7 Android Storage Access & Permissions
* **Version-Gated Read Permissions**:
  * Android 13+ (API 33+): `Manifest.permission.READ_MEDIA_AUDIO`
  * Android 11–12L (API 30–32): `Manifest.permission.READ_EXTERNAL_STORAGE`
  * Android 10 and below (API $\le$ 29): `Manifest.permission.WRITE_EXTERNAL_STORAGE` + `android:requestLegacyExternalStorage="true"`
* **Scoped Storage Write Consent (API 30+)**:
  * Collects selected tracks into `List<Uri>` using `ContentUris.withAppendedId(EXTERNAL_CONTENT_URI, id)`.
  * Generates write intent via `MediaStore.createWriteRequest(contentResolver, uris)`.
  * Launches system confirmation sheet using `ActivityResultContracts.StartIntentSenderForResult`.
  * Modifies files directly on disk after user approval.
* **Manage Media Permission (API 31+)**:
  * Checks `MediaStore.canManageMedia(context)`.
  * Routes user to `Settings.ACTION_REQUEST_MANAGE_MEDIA` along with `ACCESS_MEDIA_LOCATION`.
  * When granted, suppresses per-save system write dialogs.
* **Scoped Storage Safe Write Protocol**:
  1. Request write grant via `MediaStore.createWriteRequest(uris)`.
  2. Copy source file to private app cache: `context.cacheDir/tag_work/<id>-<timestamp>.work`.
  3. Execute native TagLib tag write against the work copy.
  4. Verify work file integrity: size valid, newly written tags readable, audio duration unchanged.
  5. Stream verified bytes back to target file/URI using NIO `FileChannel.transferTo()`.
  6. Flush (`fsync`), close file descriptor, and clean up temporary work files.
  7. Trigger `MediaScannerConnection.scanFile()` with explicit MIME type.
  8. Invalidate Coil image cache and update repository.

---

### 1.8 Auxiliary Features
* **Lyrics Bottom Sheet**:
  * Material 3 `ModalBottomSheet` for viewing and editing multiline lyrics (`FieldKey.LYRICS`).
  * Integrates with external SongSync app via implicit `ACTION_SEND` intent (`pl.lambada.songsync`).
* **External Audio Player Preview**:
  * Single editor top bar action to preview the track.
  * Copies audio file bytes to `cacheDir/open_external_temp`, generates URI via `FileProvider`, and launches `Intent.ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION`.
  * Cleans up temporary cache on `Lifecycle.Event.ON_RESUME`.
* **Technical File Inspector**:
  * Displays file path, duration, and audio bitrate extracted asynchronously via `MediaMetadataRetriever`.

---

## 2. Phase-by-Phase Implementation Roadmap

```
Phase 0: Project Setup & Core Interfaces
  ├── Min SDK 26, Target SDK 36, Kotlin 2.x, Jetpack Compose, Material 3
  ├── AndroidManifest with scoped storage permissions & FileProvider
  └── Domain models: Music, TagField, TagPatch, TagEngine interface

Phase 1: Permission Onboarding Flow
  ├── Version-gated read permissions (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE)
  ├── PermissionScreen & OptionalPermissionScreen (MANAGE_MEDIA + ACCESS_MEDIA_LOCATION)
  └── Gate library access behind granted permissions

Phase 2: Discovery & Basic Track List (First Vertical Slice)
  ├── MediaStore query with IS_MUSIC != 0
  ├── In-memory MusicRepository with StateFlow
  ├── Compose LazyColumn with title, artist, album, and placeholder cover
  ├── SAF tree folder inclusion/exclusion filter
  └── Pull-to-refresh & Scroll-to-top FAB

Phase 3: Library Search, Sort & Multi-Selection
  ├── Debounced in-memory search across title, artist, and album
  ├── Sort dialog (Title, Artist, Album x Ascending, Descending)
  ├── Untagged tracks filter
  ├── Expandable Album accordion view
  └── Long-press multi-selection with contextual count top bar

Phase 4: Tag Reading & Coil Image Pipeline
  ├── Audio tag engine integration (TagLib C++ via CMake/NDK & JNI bindings)
  ├── Background metadata parsing for non-MP3/FLAC tracks
  ├── Coil 3 custom Fetcher and Keyer downsampling embedded art to 120x120
  ├── Lazy hasArtwork resolution on visible items
  └── Editor init tag loader

Phase 5: Tag Writing & Scoped Storage Engine (Critical Path)
  ├── Android Photo Picker integration (reading raw ContentResolver stream)
  ├── MediaStore.createWriteRequest intent launcher (API 30+)
  ├── Safe work-directory write protocol with verification
  ├── Container-specific artwork codecs (FLAC bounds, Vorbis Base64)
  ├── MediaScannerConnection.scanFile() post-write sync
  └── Batch editor per-field checkboxes (<unchanged> vs override)

Phase 6: Editor Polish & Auxiliary Features
  ├── Searchable Add Field dialog (50+ tags)
  ├── ReplayGain format compatibility gating
  ├── Lyrics editor bottom sheet (+ SongSync intent integration)
  ├── FileProvider external player preview
  └── Unsaved changes confirmation dialog

Phase 7: Hardening & Performance Optimization
  ├── Streaming IO to eliminate OutOfMemoryError on 100MB+ tracks
  ├── Unique temporary work file naming
  ├── Room catalog cache + MediaStore ContentObserver
  └── ProGuard / R8 rules for TagLib JNI native methods and reflection
```

---

## 3. Critical Files and Components to Create

### Domain & Data Layer
* `domain/model/Music.kt`: Entity with `id`, `path`, `title`, `artist`, `album`, `track`, `duration`, `hasArtwork`, `tagged`.
* `domain/model/TagField.kt`: Enum of 50+ metadata fields with UI labels and engine field mappings (`BASIC_CUTOFF = 9`).
* `domain/model/TagPatch.kt`: Data class containing modified fields, deleted fields, artwork bytes, and lyrics.
* `domain/engine/TagEngine.kt`: Abstract interface for `read(path)` and `write(path, patch)`.
* `data/taglib/TagLibEngine.kt`: JNI bridge implementing `TagEngine` via native TagLib.
* `cpp/CMakeLists.txt` & `cpp/taglib-bridge.cpp`: Native NDK build configuration and JNI wrapper for TagLib.
* `data/media/MediaStoreDataSource.kt`: MediaStore querying, `createWriteRequest`, and `scanFile`.
* `data/media/MusicRepository.kt`: State management for library tracks, folder filtering, and rescan orchestration.
* `data/images/MusicDataFetcher.kt`: Coil 3 fetcher extracting and downsampling embedded art.
* `data/images/MusicDataKeyer.kt`: Coil 3 keyer hashing by track ID.
* `data/prefs/PreferencesRepository.kt`: Jetpack DataStore for user settings and folder paths.
* `util/MimeTypes.kt`: Accurate extension-to-MIME mapping.
* `util/UriToPath.kt`: SAF document tree URI parser.

### UI Layer (Jetpack Compose)
* `ui/navigation/NavGraph.kt`: Navigation routes (`Selector`, `Editor(ids: LongArray)`, `Settings`, `About`).
* `ui/selector/SelectorScreen.kt`: Permission gates and root container.
* `ui/selector/LibraryScreen.kt`: Flat list and Album accordion views with SearchBar.
* `ui/selector/SelectorViewModel.kt`: Filtering, searching, sorting, and multi-selection state.
* `ui/selector/components/SimpleMusicItem.kt`: Track row item with cover art and selection overlay.
* `ui/selector/components/SimpleAlbumItem.kt`: Accordion album header and nested track items.
* `ui/editor/EditorScreen.kt`: Host screen switching between Single and Batch editors.
* `ui/editor/SingleEditor.kt`: Full metadata form with file info inspector and audio preview action.
* `ui/editor/BatchEditor.kt`: Batch form with per-field enable checkboxes and select-all toolbar.
* `ui/editor/EditorViewModel.kt`: Tag reading, dirty tracking, `createWriteRequest` launch, and write execution.
* `ui/editor/dialogs/LyricsEditorSheet.kt`: Lyrics modal bottom sheet with SongSync action.
* `ui/editor/dialogs/AddFieldDialog.kt`: Searchable dialog to insert any of the 50+ tag fields.
* `ui/settings/SettingsScreen.kt`: Folder manager, theme selection, and editor mode preferences.

---

## 4. Critical Gotchas, Edge Cases & Mitigations

1. **Scoped Storage Sibling `.tmp` Failure**:
   * *Trap*: Default tagger write routines attempt to create sibling `.tmp` files in the audio directory, throwing `AccessDeniedException` on API 30+.
   * *Mitigation*: Run TagLib inside `context.cacheDir` on a work copy and write the finished bytes back to the granted file/URI.
2. **Batch Temp File Collisions**:
   * *Trap*: Static temporary file naming causes data races during concurrent or rapid batch writes.
   * *Mitigation*: Generate unique file names per write: `File.createTempFile("tag_${id}_", ".tmp", context.cacheDir)`.
3. **RAM Exhaustion on Large Files**:
   * *Trap*: `file.readBytes()` buffers the entire file into RAM. A 200 MB FLAC or Hi-Res WAV will cause an `OutOfMemoryError`.
   * *Mitigation*: Stream bytes with a 256 KB buffer or use NIO `FileChannel.transferTo()`.
4. **Photo Picker Path Redaction**:
   * *Trap*: `MediaStore.Images.Media.DATA` returns null or redacted paths on modern Android.
   * *Mitigation*: Always open an `InputStream` via `context.contentResolver.openInputStream(uri)`.
5. **FLAC Artwork Rejection**:
   * *Trap*: FLAC metadata picture blocks fail or corrupt if image width and height are 0.
   * *Mitigation*: Decode bounds using `BitmapFactory.Options { inJustDecodeBounds = true }` before embedding.
6. **VorbisComment Base64 Encoding**:
   * *Trap*: Vorbis comments (OGG / Opus) store artwork as Base64 strings under `COVERART` and `COVERARTMIME`, not raw binary blocks.
   * *Mitigation*: Use Base64 encoding for Vorbis tags unless using a library that abstracts picture blocks.
7. **MediaStore Scanner Missing MIME Types**:
   * *Trap*: Calling `MediaScannerConnection.scanFile()` with `null` or wrong MIME types causes OGG, Opus, AAC, or DSF files to be misclassified or dropped from MediaStore.
   * *Mitigation*: Always pass the exact computed MIME type from the file extension.
8. **Navigation `TransactionTooLargeException`**:
   * *Trap*: Serializing a JSON list of 500 selected songs into navigation route arguments crashes the Android binder.
   * *Mitigation*: Pass only an array of MediaStore track IDs (`LongArray`).
9. **Single Consent Sheet for Batch Saves**:
   * *Trap*: Invoking `createWriteRequest` per file creates a barrage of system dialogs.
   * *Mitigation*: Bundle all selected track URIs into a single `MediaStore.createWriteRequest()` call so the user approves the entire batch in one step.
10. **Interrupting Writes on Cancel**:
    * *Trap*: Cancelling coroutines while a native or blocking tag write is executing can corrupt the audio file header.
    * *Mitigation*: Perform writes inside non-cancellable blocks (`withContext(NonCancellable)`) and disable back buttons during saving.
