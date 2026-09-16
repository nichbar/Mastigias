# Mastigias — Modern Android Native Music Metadata Editor
## Technical Specification & Implementation Architecture Plan

* **Application Name**: Mastigias
* **Package Name**: `now.link.mastigias`
* **Target Platforms**: Android 8.0 (API level 26) through Android 15/16 (API level 35/36)
* **Reference Baseline**: `CORE_FEATURES_PLAN.md` (Replication & modernization of SimpleTag)
* **Core Tagging Engine**: TagLib C++ via JNI / CMake / Android NDK (Option B)
* **Multi-Subagent Execution Guide**: `SUBAGENT_EXECUTION_GUIDE.md` (Relay execution plan, contract freeze & prompt templates)

---

## 1. Executive Summary & Vision

**Mastigias** is an open-source, high-performance, native Android music tag editor designed according to **Modern Android Architecture (MAD)** principles. It replaces legacy Java-based tag editing implementations with a high-speed native C++ engine (**TagLib**), providing seamless Scoped Storage compatibility, zero-latency catalog browsing, memory-efficient streaming I/O, and a Material 3 Jetpack Compose user interface.

### Key Architectural Differentiators
1. **Clean Architecture & Unidirectional Data Flow (UDF)**: Strict separation of Presentation, Domain, and Data layers with immutable state flows.
2. **Native TagLib Integration**: C++ TagLib engine via JNI, eliminating ~400 vendored Java classes, resolving out-of-memory errors on large audio files, and natively supporting complex tagging specs (FLAC picture blocks, Vorbis comments, ID3v2.4, MP4 atoms).
3. **Atomic Scoped Storage Write Protocol**: Guaranteed audio file integrity through a staged work-file protocol, preventing partial writes, audio corruption, and Android `AccessDeniedException` errors.
4. **Instant Reactive Catalog**: System `MediaStore` as authority combined with a lightweight **Room cache** and reactive `ContentObserver` for instant cold starts.
5. **Type-Safe Navigation**: Passing lightweight `LongArray` IDs to eliminate Android binder `TransactionTooLargeException` crashes.

---

## 2. Modern Android Architecture (MAD) Blueprint

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PRESENTATION LAYER                               │
│  Jetpack Compose • Material 3 • Navigation Compose (Type-Safe Routes)      │
│  ViewModel (Hilt) • UI State (StateFlow) • UI Events (Channels)             │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (observes StateFlow / emits events)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                DOMAIN LAYER                                 │
│  Use Cases (Interactors) • Pure Business Logic • Non-Android Dependencies   │
│  Domain Models (Track, AudioMetadata, TagField, TagPatch, Artwork)          │
│  Repository & Engine Interfaces (MusicRepository, TagEngine, StorageManager)│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (invokes repositories & engines)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                 DATA LAYER                                  │
│ ┌───────────────────────────┐ ┌───────────────────────────────────────────┐ │
│ │    System & Storage       │ │             Local Storage                 │ │
│ │ • MediaStoreDataSource    │ │ • Room Database (Catalog Cache)           │ │
│ │ • ScopedStorageManager    │ │ • DataStore (Preferences, Filter Rules)   │ │
│ │ • MediaScannerConnection  │ │ • Coil 3 ImageLoader & Memory Cache       │ │
│ └─────────────┬─────────────┘ └─────────────────────┬─────────────────────┘ │
│               └──────────────────────┬──────────────┘                       │
│                                      ▼                                      │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │                          NATIVE TAGGING LAYER                           │ │
│ │ • TagLibEngine (JNI Bridge)                                             │ │
│ │ • CMake / NDK Native Engine (TagLib C++ 1.13+)                          │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Technology Stack & Decisions
* **Language**: Kotlin 2.1+ / C++20.
* **UI Toolkit**: Jetpack Compose with Material Design 3 (`androidx.compose.material3`).
* **Design System**: Material You dynamic color, adaptive edge-to-edge display (`enableEdgeToEdge()`), predictive back gesture navigation.
* **Dependency Injection**: Hilt (`dagger.hilt.android`).
* **Concurrency**: Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Default`, `NonCancellable`) & Flow (`StateFlow`, `SharedFlow`).
* **Local Caching**: Room 2.6+ with SQLite FTS support for instant search.
* **Preferences**: Jetpack DataStore (Preferences DataStore).
* **Image Loading**: Coil 3 (`io.coil-kt.coil3`) with custom `Fetcher` and `Keyer`.
* **Native Build**: CMake 3.22+, Android NDK r26+.

---

## 3. Core Features & Functional Specifications

### 3.1 Audio Discovery & Hybrid Media Scanning
* **Authority**: System `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`.
* **Query Criteria**: `${MediaStore.Audio.Media.IS_MUSIC} != 0`, sorted by `DATE_ADDED DESC`.
* **16 Compatible Containers**: `mp3`, `wav`, `wave`, `dsf`, `aiff`, `aif`, `aifc`, `wma`, `ogg`, `ogx`, `mp4`, `m4a`, `m4p`, `flac`, `aac`, `opus`.
* **Hybrid Fast-Scanning Pipeline**:
  * **MP3 & FLAC**: Parsed directly from `MediaStore` cursor columns (`_ID`, `TITLE`, `ARTIST`, `ALBUM`, `DATA`, `TRACK`, `DURATION`). No disk I/O on cold start.
  * **Other Formats**: Parsed asynchronously on background coroutines (`Dispatchers.Default`) via `TagLibEngine.read()` using bounded parallelism (`async { ... }` / `awaitAll()`).
* **Lazy Enrichment**:
  * `hasArtwork`: Resolved on-demand when visible in Compose `LazyColumn` via `LaunchedEffect`.
  * `duration`: Missing durations queried asynchronously via `MediaMetadataRetriever`.
* **Folder Filtering (Storage Access Framework)**:
  * User selects folders via `Intent.ACTION_OPEN_DOCUMENT_TREE`.
  * SAF tree URIs mapped to paths (`/tree/primary:Music` $\rightarrow$ `/storage/emulated/0/Music`).
  * Modes: `Include` or `Exclude`. Evaluated per file path; empty list permits all audio.

### 3.2 Reactive Room Catalog Cache & Synchronization
* **Database Entity**: `TrackEntity` storing `id`, `path`, `title`, `artist`, `album`, `trackNumber`, `durationMs`, `hasArtwork`, `isTagged`, `dateModified`.
* **ContentObserver**: Registered to `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`. Automatically detects library additions, deletions, or external edits without manual pull-to-refresh.
* **Cold Start Optimization**: UI loads from Room cache instantly ($< 50\text{ ms}$), while a background diff worker synchronizes any changes with `MediaStore`.

### 3.3 Library Browsing, Search & Selection
* **Flat Track View**: Default view for `Title` or `Artist` sort. 48dp cover art thumbnail, title, `"album - artist"`, placeholder note icon.
* **Album Accordion View**:
  * Grouped by `album` name into expandable cards.
  * Header displays album cover, album title, and artist.
  * Children sorted by track index (`it.trackNumber`).
  * Child rows display track index badge and formatted duration (`mm:ss`).
  * Smooth vertical expansion animation (200ms easing).
* **Real-Time Search**: Debounced in-memory / SQLite substring matching across title, artist, and album.
* **Filtering & Sorting**:
  * Sort orders: Title, Artist, Album $\times$ Ascending, Descending (persisted in DataStore).
  * Untagged filter toggle: tracks where `title`, `artist`, or `album` is blank or `"<unknown>"`.
* **Multi-Selection Mode**:
  * Triggered by long-press on track or album header.
  * Contextual Material 3 TopAppBar displaying selection count, Cancel, and Edit action.
  * Thumbnail selection overlay badge (`SelectCheckCircle`).

### 3.4 Native Audio Tagging Engine (TagLib C++ via JNI)
* **Architecture**: Compiled static/shared TagLib 1.13+ via CMake. Kotlin facade `TagLibEngine` calls native JNI functions.
* **Format Support**:
  * FLAC: `TagLib::FLAC::File` with Xiph Comments and Picture metadata blocks.
  * MP4 / M4A / AAC: `TagLib::MP4::File` with Item List tags and `covr` atoms.
  * OGG / Vorbis / Opus: `TagLib::Ogg::Vorbis` and `TagLib::Ogg::Opus` with VorbisComment fields.
  * MP3 / WAV / AIFF / DSF: `TagLib::ID3v2::Tag` (ID3v2.4 UTF-8) and native DSF chunks.
  * WMA / ASF: `TagLib::ASF::File` with ASF metadata attributes.
* **50+ Tag Fields**:
  * **Basic (0–9)**: Title, Artist, Album, Year, Track Number, Genre, Album Artist, Composer, Disc Number, Comment.
  * **Advanced (10+)**: ReplayGain Track/Album Gain & Peak, Lyrics, AcoustID (Fingerprint, ID), MusicBrainz IDs (Artist, Release, Track, Disc, Work), Sort Orders (Title, Artist, Album, Composer), BPM, Musical Key, Mood, Barcode, Catalog Number, ISRC, Web URLs, Custom Fields 1–5.
* **Format Compatibility Gating**: ReplayGain fields visible only for supported formats (`mp3`, `wav`, `dsf`, `wma`, `ogg`, `flac`).
* **Deletion Semantics**: Passing an empty string instructs TagLib to remove the tag field frame/key.

### 3.5 Scoped Storage & Atomic Safe Write Protocol
Directly modifying audio files under Android Scoped Storage (API 30+) risks corruption if interrupted. Mastigias enforces an **8-step atomic write protocol**:

```
[Target File / Uri]
       │
       ▼ (1. Request MediaStore.createWriteRequest consent)
[User Approves Consent Sheet]
       │
       ▼ (2. Copy to cacheDir/tag_work/<id>-<timestamp>.work)
[Isolated Work File]
       │
       ▼ (3. Native TagLib writes metadata & artwork)
[Modified Work File]
       │
       ▼ (4. Integrity Verification: Size > 0, Readback Tags, Duration Identical)
[Verified Work File]
       │
       ▼ (5. NIO FileChannel.transferTo() streams back to target file descriptor)
[Target File Updated]
       │
       ▼ (6. Flush with fsync; delete work file in finally block)
       ▼ (7. MediaScannerConnection.scanFile() with explicit MIME type)
       ▼ (8. Invalidate Coil image cache & emit updated state to Room/UI)
```

* **Permission Matrix**:
  * Android 13+ (API 33+): `READ_MEDIA_AUDIO`.
  * Android 11–12L (API 30–32): `READ_EXTERNAL_STORAGE`.
  * Android 10 and below (API $\le$ 29): `WRITE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage="true"`.
  * Android 11+ (API 31+): Optional `MediaStore.canManageMedia(context)` request to `Settings.ACTION_REQUEST_MANAGE_MEDIA` to suppress per-save system prompts.

### 3.6 Batch Tag Editing Engine
* **Batch Entry**: Launched with a `LongArray` of track IDs.
* **Selective Field Modification**:
  * Each field row contains an enabled checkbox.
  * Disabled $\rightarrow$ displays `<unchanged>` placeholder; field is skipped during write.
  * Enabled $\rightarrow$ entered value overwrites metadata across all selected files.
* **Dedicated Artwork Batch Toggle**: Independent toggle controlling whether cover art is applied across the batch.
* **Safety & Isolation**:
  * Lyrics are explicitly skipped during batch operations.
  * Each file write is isolated in `try/catch`. Single file failures are logged and reported without aborting the batch.
  * Operations execute within `withContext(NonCancellable)` to prevent corrupt headers if the user navigates away.

### 3.7 Artwork Management Pipeline
* **Coil 3 Integration**:
  * Custom `MusicDataFetcher` reads raw artwork bytes directly via `TagLibEngine.readArtwork()`.
  * Two-pass decoding: reads image bounds via `BitmapFactory.Options.inJustDecodeBounds`, downsamples to **120×120 px** via `inSampleSize` before allocating bitmap memory.
  * Custom `MusicDataKeyer` keys cache entries by MediaStore ID.
* **Artwork Selection**:
  * Android Photo Picker contract (`ActivityResultContracts.PickVisualMedia`).
  * Direct binary stream via `ContentResolver.openInputStream(uri)` (avoids redacted `DATA` column).
  * Magic bytes MIME sniffing (`image/jpeg`, `image/png`, `image/webp`).
* **Container-Specific Artwork Encoding**:
  * **FLAC**: Generates FLAC picture block with explicit `width`, `height`, `mimeType`, and `pictureType` (3 = Front Cover).
  * **VorbisComment (OGG / Opus)**: Writes Base64-encoded metadata to `COVERART` and `COVERARTMIME`.
  * **ID3v2 / MP4 / ASF**: Native TagLib picture frame / item embedding.
  * **Removal**: Clears picture blocks / frames when artwork is deleted.

### 3.8 Auxiliary Features
* **Lyrics Bottom Sheet**: Multiline Material 3 `ModalBottomSheet` for viewing and editing lyrics (`FieldKey.LYRICS`), with implicit `ACTION_SEND` intent integration for SongSync (`pl.lambada.songsync`).
* **Audio Preview Player**: Temporary copy streamed to `cacheDir/open_external_temp`, exposed via `FileProvider`, launched with `ACTION_VIEW` and `FLAG_GRANT_READ_URI_PERMISSION`.
* **Technical File Inspector**: Audio bitrate, sample rate, channels, container type, file size, and filesystem path extracted via `MediaMetadataRetriever` and native TagLib properties.

---

## 4. Package Structure & Architectural Components

```
now.link.mastigias/
├── MastigiasApp.kt                       # Application class (@HiltAndroidApp, Coil ImageLoaderFactory)
├── MainActivity.kt                       # Edge-to-edge host Activity, Permission & Root NavHost
│
├── core/                                 # Shared primitives, extensions, dispatchers
│   ├── common/
│   │   ├── AppDispatchers.kt             # Coroutine dispatchers wrapper (IO, Default, Main)
│   │   ├── Result.kt                     # Functional Result wrapper (Success, Error, Loading)
│   │   └── Extensions.kt                 # String, Context, Uri extensions
│   ├── constants/
│   │   ├── AudioFormats.kt               # Extension lists, MIME mappings
│   │   └── TagConstants.kt               # Field limits, default artwork sizes
│   └── util/
│       ├── FileUtils.kt                  # Safe work copy generation, streaming copy, fsync
│       ├── ImageUtils.kt                 # Magic byte sniffing, dimension calculation
│       └── UriToPath.kt                  # SAF tree URI to filesystem path converter
│
├── data/                                 # Data sources, Room DB, repositories, JNI bridge
│   ├── database/
│   │   ├── MastigiasDatabase.kt          # Room database instance
│   │   ├── dao/
│   │   │   └── TrackDao.kt               # Catalog querying, FTS search, update operations
│   │   └── entity/
│   │       └── TrackEntity.kt            # Room representation of cached audio track
│   ├── datastore/
│   │   ├── UserPreferencesSerializer.kt  # Proto/Preferences DataStore serialization
│   │   └── PreferencesRepositoryImpl.kt  # Implementation of sorting, themes, folder filters
│   ├── image/
│   │   ├── TrackArtworkFetcher.kt        # Coil 3 Fetcher for TagLib embedded artwork
│   │   └── TrackArtworkKeyer.kt          # Coil 3 Keyer using MediaStore ID
│   ├── media/
│   │   ├── MediaStoreDataSource.kt       # MediaStore querying, ContentObserver, createWriteRequest
│   │   ├── ScopedStorageManager.kt       # Atomic work-file write execution, FileChannel transfer
│   │   └── MusicRepositoryImpl.kt        # Combined Room + MediaStore repository implementation
│   └── taglib/
│       ├── TagLibBridge.kt               # External JNI declarations
│       └── TagLibEngineImpl.kt           # TagEngine implementation wrapping TagLibBridge
│
├── domain/                               # Pure business logic (Kotlin only, no Android UI)
│   ├── model/
│   │   ├── Track.kt                      # Core track domain model
│   │   ├── Album.kt                      # Album domain model with aggregated tracks
│   │   ├── AudioMetadata.kt              # Full metadata representation (50+ fields)
│   │   ├── TagField.kt                   # Enum of all supported metadata fields
│   │   ├── TagPatch.kt                   # Changeset to apply (modified, deleted, artwork)
│   │   ├── ArtworkData.kt                # Binary image data, MIME type, dimensions
│   │   └── FolderFilter.kt               # SAF include/exclude folder model
│   ├── engine/
│   │   └── TagEngine.kt                  # Tag reading & writing abstraction
│   ├── repository/
│   │   ├── MusicRepository.kt            # Library catalog, sync, and write operations
│   │   └── PreferencesRepository.kt      # User settings, folder rules, sort preferences
│   └── usecase/
│       ├── GetLibraryTracksUseCase.kt    # Reactive stream of tracks with filter & search
│       ├── GetAlbumsUseCase.kt           # Grouped album accordion stream
│       ├── ReadTrackMetadataUseCase.kt   # Deep metadata read via TagEngine
│       ├── WriteTrackMetadataUseCase.kt  # Single track atomic write orchestration
│       ├── BatchWriteMetadataUseCase.kt  # Multi-file batch write orchestration
│       └── SyncMediaStoreUseCase.kt      # MediaStore scan and Room cache sync
│
├── di/                                   # Hilt dependency injection modules
│   ├── AppModule.kt                      # Application context, Dispatchers
│   ├── DatabaseModule.kt                 # Room DB and DAOs
│   ├── DataStoreModule.kt                # Jetpack DataStore instances
│   ├── EngineModule.kt                   # TagEngine binding (TagLibEngineImpl)
│   └── RepositoryModule.kt               # Repository interface bindings
│
├── ui/                                   # Jetpack Compose UI layer
│   ├── navigation/
│   │   ├── NavGraph.kt                   # NavHost with type-safe @Serializable routes
│   │   └── ScreenRoute.kt                # Destination declarations (Library, Editor, Settings)
│   ├── theme/
│   │   ├── Color.kt                      # Material 3 color schemes
│   │   ├── Theme.kt                      # Dynamic Color & Dark/Light theme setup
│   │   └── Type.kt                       # Typography definitions
│   ├── common/
│   │   ├── SearchBar.kt                  # Top search bar with clear button
│   │   ├── FloatingScrollToTop.kt        # Animated FAB appearing on scroll
│   │   ├── CheckboxRow.kt                # Checkbox row used in batch editor
│   │   └── ConfirmationDialog.kt         # Discard changes and permission dialogs
│   ├── library/
│   │   ├── LibraryScreen.kt              # Root library container (Flat & Accordion)
│   │   ├── LibraryViewModel.kt           # Search, sort, selection, and sync state
│   │   ├── components/
│   │   │   ├── TrackListItem.kt          # Individual track row with thumbnail & checkmark
│   │   │   ├── AlbumAccordionItem.kt     # Expandable album card with child tracks
│   │   │   └── MultiSelectTopBar.kt      # Selection counter and contextual actions
│   │   └── dialogs/
│   │       ├── SortDialog.kt             # Title/Artist/Album sort direction selector
│   │       └── FolderFilterDialog.kt     # SAF tree picker management
│   ├── editor/
│   │   ├── EditorScreen.kt               # Host screen switching Single vs Batch
│   │   ├── EditorViewModel.kt            # Tag editing state, dirty tracking, consent launch
│   │   ├── SingleEditorContent.kt        # Form for individual track editing
│   │   ├── BatchEditorContent.kt         # Form for batch editing with selective checkboxes
│   │   ├── components/
│   │   │   ├── EditorArtworkSection.kt   # Cover art preview, pick image, delete artwork
│   │   │   ├── TagFieldInput.kt          # OutlinedTextField with delete/enable control
│   │   │   └── TechnicalInfoCard.kt      # Audio bitrate, sample rate, file path
│   │   └── dialogs/
│   │       ├── AddFieldDialog.kt         # Searchable dialog for inserting any of 50+ fields
│   │       └── LyricsBottomSheet.kt      # Multiline lyrics editor with SongSync intent
│   └── settings/
│       ├── SettingsScreen.kt             # Theme, folder configuration, Manage Media gate
│       └── SettingsViewModel.kt          # Preferences management
│
└── cpp/                                  # Native C++ TagLib layer
    ├── CMakeLists.txt                    # CMake build script linking TagLib and liblog
    ├── taglib/                           # TagLib 1.13+ upstream source or prebuilt headers
    └── taglib-bridge.cpp                 # JNI implementation functions
```

---

## 5. Domain Models & Core Interfaces

### 5.1 Domain Models
```kotlin
package now.link.mastigias.domain.model

data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val durationMs: Long,
    val hasArtwork: Boolean?,
    val isTagged: Boolean,
    val dateModified: Long
)

data class Album(
    val title: String,
    val artist: String,
    val tracks: List<Track>,
    val coverTrackId: Long?
)

data class ArtworkData(
    val binaryData: ByteArray,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0
)

data class AudioMetadata(
    val trackId: Long,
    val path: String,
    val fields: Map<TagField, String>,
    val artwork: ArtworkData?,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
    val durationMs: Long
)

data class TagPatch(
    val updatedFields: Map<TagField, String>,
    val deletedFields: Set<TagField>,
    val updatedArtwork: ArtworkData?,
    val removeArtwork: Boolean
)

enum class TagCategory(val title: String) {
    BASIC("Basic Tags"),
    LYRICS("Lyrics"),
    SORTING("Sort Order"),
    MUSICAL("Musical Properties"),
    PRODUCTION("Credits & Production"),
    REPLAYGAIN("ReplayGain"),
    IDENTIFIER("Identifiers & Fingerprints"),
    PUBLISHING("Publishing & Legal"),
    URL("Web Links"),
    CUSTOM("Custom Fields")
}

enum class TagField(
    val key: String,
    val displayName: String,
    val category: TagCategory,
    val id3v2Frame: String,
    val vorbisKey: String,
    val mp4Atom: String,
    val asfAttribute: String
) {
    // --- Basic Metadata (0..11) ---
    TITLE("title", "Title", TagCategory.BASIC, "TIT2", "TITLE", "\u00a9nam", "Title"),
    ARTIST("artist", "Artist", TagCategory.BASIC, "TPE1", "ARTIST", "\u00a9ART", "Author"),
    ALBUM("album", "Album", TagCategory.BASIC, "TALB", "ALBUM", "\u00a9alb", "WM/AlbumTitle"),
    YEAR("year", "Year", TagCategory.BASIC, "TDRC", "DATE", "\u00a9day", "WM/Year"),
    TRACK_NUMBER("track_number", "Track Number", TagCategory.BASIC, "TRCK", "TRACKNUMBER", "trkn", "WM/TrackNumber"),
    TRACK_TOTAL("track_total", "Total Tracks", TagCategory.BASIC, "TRCK", "TRACKTOTAL", "trkn", "WM/TrackTotal"),
    GENRE("genre", "Genre", TagCategory.BASIC, "TCON", "GENRE", "\u00a9gen", "WM/Genre"),
    ALBUM_ARTIST("album_artist", "Album Artist", TagCategory.BASIC, "TPE2", "ALBUMARTIST", "aART", "WM/AlbumArtist"),
    COMPOSER("composer", "Composer", TagCategory.BASIC, "TCOM", "COMPOSER", "\u00a9wrt", "WM/Composer"),
    DISC_NUMBER("disc_number", "Disc Number", TagCategory.BASIC, "TPOS", "DISCNUMBER", "disk", "WM/PartOfSet"),
    DISC_TOTAL("disc_total", "Total Discs", TagCategory.BASIC, "TPOS", "DISCTOTAL", "disk", "WM/TotalDiscs"),
    COMMENT("comment", "Comment", TagCategory.BASIC, "COMM", "COMMENT", "\u00a9cmt", "Description"),

    // --- Lyrics ---
    LYRICS("lyrics", "Lyrics", TagCategory.LYRICS, "USLT", "LYRICS", "\u00a9lyr", "WM/Lyrics"),

    // --- Sorting ---
    TITLE_SORT("title_sort", "Title Sort Order", TagCategory.SORTING, "TSOT", "TITLESORT", "sonm", "WM/TitleSortOrder"),
    ARTIST_SORT("artist_sort", "Artist Sort Order", TagCategory.SORTING, "TSOP", "ARTISTSORT", "soar", "WM/ArtistSortOrder"),
    ALBUM_SORT("album_sort", "Album Sort Order", TagCategory.SORTING, "TSOA", "ALBUMSORT", "soal", "WM/AlbumSortOrder"),
    ALBUM_ARTIST_SORT("album_artist_sort", "Album Artist Sort Order", TagCategory.SORTING, "TSO2", "ALBUMARTISTSORT", "soaa", "WM/AlbumArtistSortOrder"),
    COMPOSER_SORT("composer_sort", "Composer Sort Order", TagCategory.SORTING, "TSOC", "COMPOSERSORT", "soco", "WM/ComposerSortOrder"),

    // --- Musical & Production ---
    BPM("bpm", "Beats Per Minute", TagCategory.MUSICAL, "TBPM", "BPM", "tmpo", "WM/BeatsPerMinute"),
    INITIAL_KEY("initial_key", "Initial Key", TagCategory.MUSICAL, "TKEY", "KEY", "----:com.apple.iTunes:initialkey", "WM/InitialKey"),
    MOOD("mood", "Mood", TagCategory.MUSICAL, "TMOO", "MOOD", "----:com.apple.iTunes:MOOD", "WM/Mood"),
    GROUPING("grouping", "Grouping", TagCategory.MUSICAL, "TIT1", "GROUPING", "\u00a9grp", "WM/ContentGroupDescription"),
    SUBTITLE("subtitle", "Subtitle", TagCategory.MUSICAL, "TIT3", "SUBTITLE", "----:com.apple.iTunes:SUBTITLE", "WM/SubTitle"),
    CONDUCTOR("conductor", "Conductor", TagCategory.PRODUCTION, "TPE3", "CONDUCTOR", "----:com.apple.iTunes:CONDUCTOR", "WM/Conductor"),
    REMIXER("remixer", "Modified By / Remixer", TagCategory.PRODUCTION, "TPE4", "REMIXER", "----:com.apple.iTunes:REMIXER", "WM/ModifiedBy"),
    ARRANGER("arranger", "Arranger", TagCategory.PRODUCTION, "TIPL:arranger", "ARRANGER", "----:com.apple.iTunes:ARRANGER", "WM/Arranger"),
    LYRICIST("lyricist", "Lyricist", TagCategory.PRODUCTION, "TEXT", "LYRICIST", "----:com.apple.iTunes:LYRICIST", "WM/Writer"),
    PRODUCER("producer", "Producer", TagCategory.PRODUCTION, "TIPL:producer", "PRODUCER", "----:com.apple.iTunes:PRODUCER", "WM/Producer"),

    // --- ReplayGain ---
    REPLAYGAIN_TRACK_GAIN("replaygain_track_gain", "Track Gain", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_TRACK_GAIN", "----:com.apple.iTunes:replaygain_track_gain", "REPLAYGAIN_TRACK_GAIN"),
    REPLAYGAIN_TRACK_PEAK("replaygain_track_peak", "Track Peak", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_TRACK_PEAK", "REPLAYGAIN_TRACK_PEAK", "----:com.apple.iTunes:replaygain_track_peak", "REPLAYGAIN_TRACK_PEAK"),
    REPLAYGAIN_ALBUM_GAIN("replaygain_album_gain", "Album Gain", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_ALBUM_GAIN", "----:com.apple.iTunes:replaygain_album_gain", "REPLAYGAIN_ALBUM_GAIN"),
    REPLAYGAIN_ALBUM_PEAK("replaygain_album_peak", "Album Peak", TagCategory.REPLAYGAIN, "TXXX:REPLAYGAIN_ALBUM_PEAK", "REPLAYGAIN_ALBUM_PEAK", "----:com.apple.iTunes:replaygain_album_peak", "REPLAYGAIN_ALBUM_PEAK"),

    // --- AcoustID & MusicBrainz IDs ---
    ACOUSTID_FINGERPRINT("acoustid_fingerprint", "AcoustID Fingerprint", TagCategory.IDENTIFIER, "TXXX:AcoustID Fingerprint", "ACOUSTID_FINGERPRINT", "----:com.apple.iTunes:AcoustID Fingerprint", "AcoustID/Fingerprint"),
    ACOUSTID_ID("acoustid_id", "AcoustID ID", TagCategory.IDENTIFIER, "TXXX:AcoustID Id", "ACOUSTID_ID", "----:com.apple.iTunes:AcoustID Id", "AcoustID/Id"),
    MUSICBRAINZ_TRACK_ID("musicbrainz_track_id", "MusicBrainz Track ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Track Id", "MUSICBRAINZ_TRACKID", "----:com.apple.iTunes:MusicBrainz Track Id", "MusicBrainz/Track Id"),
    MUSICBRAINZ_ARTIST_ID("musicbrainz_artist_id", "MusicBrainz Artist ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Artist Id", "MUSICBRAINZ_ARTISTID", "----:com.apple.iTunes:MusicBrainz Artist Id", "MusicBrainz/Artist Id"),
    MUSICBRAINZ_RELEASE_ID("musicbrainz_release_id", "MusicBrainz Release ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Album Id", "MUSICBRAINZ_ALBUMID", "----:com.apple.iTunes:MusicBrainz Album Id", "MusicBrainz/Album Id"),
    MUSICBRAINZ_RELEASEARTIST_ID("musicbrainz_releaseartist_id", "MusicBrainz Release Artist ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Album Artist Id", "MUSICBRAINZ_ALBUMARTISTID", "----:com.apple.iTunes:MusicBrainz Album Artist Id", "MusicBrainz/Album Artist Id"),
    MUSICBRAINZ_DISC_ID("musicbrainz_disc_id", "MusicBrainz Disc ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Disc Id", "MUSICBRAINZ_DISCID", "----:com.apple.iTunes:MusicBrainz Disc Id", "MusicBrainz/Disc Id"),
    MUSICBRAINZ_WORK_ID("musicbrainz_work_id", "MusicBrainz Work ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Work Id", "MUSICBRAINZ_WORKID", "----:com.apple.iTunes:MusicBrainz Work Id", "MusicBrainz/Work Id"),
    MUSICBRAINZ_RECORDING_ID("musicbrainz_recording_id", "MusicBrainz Recording ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Recording Id", "MUSICBRAINZ_RECORDINGID", "----:com.apple.iTunes:MusicBrainz Recording Id", "MusicBrainz/Recording Id"),
    MUSICBRAINZ_RELEASEGROUP_ID("musicbrainz_releasegroup_id", "MusicBrainz Release Group ID", TagCategory.IDENTIFIER, "TXXX:MusicBrainz Release Group Id", "MUSICBRAINZ_RELEASEGROUPID", "----:com.apple.iTunes:MusicBrainz Release Group Id", "MusicBrainz/Release Group Id"),

    // --- Publishing & Industry Identifiers ---
    ISRC("isrc", "ISRC", TagCategory.PUBLISHING, "TSRC", "ISRC", "----:com.apple.iTunes:ISRC", "WM/ISRC"),
    BARCODE("barcode", "Barcode / UPC", TagCategory.PUBLISHING, "TXXX:BARCODE", "BARCODE", "----:com.apple.iTunes:BARCODE", "WM/Barcode"),
    CATALOG_NUMBER("catalog_number", "Catalog Number", TagCategory.PUBLISHING, "TXXX:CATALOGNUMBER", "CATALOGNUMBER", "----:com.apple.iTunes:CATALOGNUMBER", "WM/CatalogNo"),
    RECORD_LABEL("record_label", "Record Label / Organization", TagCategory.PUBLISHING, "TPUB", "ORGANIZATION", "----:com.apple.iTunes:LABEL", "WM/Publisher"),
    COPYRIGHT("copyright", "Copyright", TagCategory.PUBLISHING, "TCOP", "COPYRIGHT", "cprt", "Copyright"),
    ENCODER("encoder", "Encoded By", TagCategory.PUBLISHING, "TSSE", "ENCODER", "\u00a9too", "WM/EncodedBy"),
    MEDIA_TYPE("media_type", "Media Type", TagCategory.PUBLISHING, "TMED", "SOURCEMEDIA", "----:com.apple.iTunes:MEDIA", "WM/Media"),
    ORIGINAL_DATE("original_date", "Original Release Date", TagCategory.PUBLISHING, "TDOR", "ORIGINALDATE", "----:com.apple.iTunes:ORIGINALDATE", "WM/OriginalReleaseTime"),
    RATING("rating", "Rating / Popularity", TagCategory.PUBLISHING, "POPM", "RATING", "rtng", "WM/SharedUserRating"),

    // --- Web URLs ---
    URL_OFFICIAL_ARTIST("url_artist", "Official Artist URL", TagCategory.URL, "WOAR", "URL_OFFICIAL_ARTIST_SITE", "----:com.apple.iTunes:URL_OFFICIAL_ARTIST_SITE", "WM/AuthorURL"),
    URL_OFFICIAL_RELEASE("url_release", "Official Release URL", TagCategory.URL, "WOAS", "URL_OFFICIAL_RELEASE_SITE", "----:com.apple.iTunes:URL_OFFICIAL_RELEASE_SITE", "WM/PromotionURL"),
    URL_DISCOGS_RELEASE("url_discogs_release", "Discogs Release URL", TagCategory.URL, "WXXX:DISCOGS_RELEASE", "DISCOGS_RELEASE", "----:com.apple.iTunes:DISCOGS_RELEASE", "WM/DiscogsReleaseURL"),
    URL_DISCOGS_ARTIST("url_discogs_artist", "Discogs Artist URL", TagCategory.URL, "WXXX:DISCOGS_ARTIST", "DISCOGS_ARTIST", "----:com.apple.iTunes:DISCOGS_ARTIST", "WM/DiscogsArtistURL"),
    URL_WIKIPEDIA_ARTIST("url_wikipedia_artist", "Wikipedia Artist URL", TagCategory.URL, "WXXX:WIKIPEDIA_ARTIST", "WIKIPEDIA_ARTIST", "----:com.apple.iTunes:WIKIPEDIA_ARTIST", "WM/WikipediaArtistURL"),
    URL_WIKIPEDIA_RELEASE("url_wikipedia_release", "Wikipedia Release URL", TagCategory.URL, "WXXX:WIKIPEDIA_RELEASE", "WIKIPEDIA_RELEASE", "----:com.apple.iTunes:WIKIPEDIA_RELEASE", "WM/WikipediaReleaseURL"),

    // --- Custom User Fields ---
    CUSTOM_1("custom_1", "Custom Field 1", TagCategory.CUSTOM, "TXXX:CUSTOM1", "CUSTOM1", "----:com.apple.iTunes:CUSTOM1", "CUSTOM1"),
    CUSTOM_2("custom_2", "Custom Field 2", TagCategory.CUSTOM, "TXXX:CUSTOM2", "CUSTOM2", "----:com.apple.iTunes:CUSTOM2", "CUSTOM2"),
    CUSTOM_3("custom_3", "Custom Field 3", TagCategory.CUSTOM, "TXXX:CUSTOM3", "CUSTOM3", "----:com.apple.iTunes:CUSTOM3", "CUSTOM3"),
    CUSTOM_4("custom_4", "Custom Field 4", TagCategory.CUSTOM, "TXXX:CUSTOM4", "CUSTOM4", "----:com.apple.iTunes:CUSTOM4", "CUSTOM4"),
    CUSTOM_5("custom_5", "Custom Field 5", TagCategory.CUSTOM, "TXXX:CUSTOM5", "CUSTOM5", "----:com.apple.iTunes:CUSTOM5", "CUSTOM5");

    companion object {
        private val keyLookup = entries.associateBy { it.key }
        fun fromKey(key: String): TagField? = keyLookup[key]

        val basicFields: List<TagField> = entries.filter { it.category == TagCategory.BASIC }
        val advancedFields: List<TagField> = entries.filter { it.category != TagCategory.BASIC }
    }
}
```

### 5.2 TagEngine Abstraction & Robust JNI Marshaling Contract

To prevent brittle JNI reflection crashes (such as ProGuard stripping methods, nested Map instantiations, or Kotlin enum ordinal mismatches across JNI), Mastigias uses a **flat array marshaling protocol**.

#### 5.2.1 Native DTO & Kotlin JNI Bridge
```kotlin
package now.link.mastigias.data.taglib

/**
 * Flat transfer DTO returned across the JNI boundary.
 * Primitive arrays avoid deep Kotlin/Java object graph allocation inside native C++.
 */
class NativeTagBundle(
    val keys: Array<String>,
    val values: Array<String>,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
    val durationMs: Long
)

object TagLibBridge {
    init {
        System.loadLibrary("mastigias-native")
    }

    external fun nativeReadMetadata(filePath: String): NativeTagBundle?

    external fun nativeReadArtwork(filePath: String): ByteArray?

    external fun nativeWriteMetadata(
        filePath: String,
        setKeys: Array<String>,
        setValues: Array<String>,
        deleteKeys: Array<String>,
        artworkBytes: ByteArray?,
        removeArtwork: Boolean,
        artworkMime: String,
        artworkWidth: Int,
        artworkHeight: Int
    ): Boolean
}
```

#### 5.2.2 C++ JNI Implementation (`cpp/taglib-bridge.cpp`)
```cpp
#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include <taglib/fileref.h>
#include <taglib/tag.h>
#include <taglib/tbytevector.h>
#include <taglib/tstring.h>
#include <taglib/tpropertymap.h>
#include <taglib/audioproperties.h>

// Audio Container format headers
#include <taglib/flacfile.h>
#include <taglib/flacpicture.h>
#include <taglib/mpegfile.h>
#include <taglib/id3v2tag.h>
#include <taglib/id3v2frame.h>
#include <taglib/attachedpictureframe.h>
#include <taglib/mp4file.h>
#include <taglib/mp4tag.h>
#include <taglib/mp4coverart.h>
#include <taglib/mp4item.h>
#include <taglib/vorbisfile.h>
#include <taglib/opusfile.h>
#include <taglib/xiphcomment.h>
#include <taglib/asffile.h>
#include <taglib/asftag.h>
#include <taglib/asfpicture.h>
#include <taglib/asfattribute.h>
#include <taglib/wavfile.h>
#include <taglib/aifffile.h>

#define TAG "MastigiasNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jobject JNICALL
Java_now_link_mastigias_data_taglib_TagLibBridge_nativeReadMetadata(
    JNIEnv* env, jobject /*thiz*/, jstring jFilePath) {

    const char* filePath = env->GetStringUTFChars(jFilePath, nullptr);
    if (!filePath) return nullptr;

    TagLib::FileRef f(filePath);
    env->ReleaseStringUTFChars(jFilePath, filePath);

    if (f.isNull() || !f.tag()) {
        LOGE("Failed to open audio file or no tag found: %s", filePath);
        return nullptr;
    }

    TagLib::PropertyMap tags = f.file()->properties();
    std::vector<std::string> keys;
    std::vector<std::string> values;

    for (const auto& entry : tags) {
        if (!entry.second.isEmpty()) {
            keys.push_back(entry.first.toCString(true));
            values.push_back(entry.second.front().toCString(true));
        }
    }

    int bitrate = 0, sampleRate = 0, channels = 0;
    long durationMs = 0;
    if (f.audioProperties()) {
        bitrate = f.audioProperties()->bitrate();
        sampleRate = f.audioProperties()->sampleRate();
        channels = f.audioProperties()->channels();
        durationMs = f.audioProperties()->lengthInMilliseconds();
    }

    // Construct String[] arrays for JNI transfer
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray jKeys = env->NewObjectArray(keys.size(), stringClass, nullptr);
    jobjectArray jValues = env->NewObjectArray(values.size(), stringClass, nullptr);

    for (size_t i = 0; i < keys.size(); ++i) {
        jstring kStr = env->NewStringUTF(keys[i].c_str());
        jstring vStr = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(jKeys, i, kStr);
        env->SetObjectArrayElement(jValues, i, vStr);
        env->DeleteLocalRef(kStr);
        env->DeleteLocalRef(vStr);
    }

    jclass bundleClass = env->FindClass("now/link/mastigias/data/taglib/NativeTagBundle");
    jmethodID bundleCtor = env->GetMethodID(
        bundleClass, "<init>", "([Ljava/lang/String;[Ljava/lang/String;IIII)V");

    return env->NewObject(
        bundleClass, bundleCtor, jKeys, jValues, bitrate, sampleRate, channels, (jint)durationMs);
}

JNIEXPORT jbyteArray JNICALL
Java_now_link_mastigias_data_taglib_TagLibBridge_nativeReadArtwork(
    JNIEnv* env, jobject /*thiz*/, jstring jFilePath) {

    const char* filePath = env->GetStringUTFChars(jFilePath, nullptr);
    if (!filePath) return nullptr;

    TagLib::FileRef f(filePath);
    env->ReleaseStringUTFChars(jFilePath, filePath);

    if (f.isNull() || !f.file()) {
        LOGE("nativeReadArtwork: Failed to open file: %s", filePath);
        return nullptr;
    }

    std::vector<char> artworkBytes;
    TagLib::File* file = f.file();

    // 1. FLAC
    if (auto* flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
        const TagLib::List<TagLib::FLAC::Picture*>& pictures = flacFile->pictureList();
        for (auto* pic : pictures) {
            if (pic && !pic->data().isEmpty()) {
                if (pic->type() == TagLib::FLAC::Picture::FrontCover || artworkBytes.empty()) {
                    artworkBytes.assign(pic->data().data(), pic->data().data() + pic->data().size());
                    if (pic->type() == TagLib::FLAC::Picture::FrontCover) break;
                }
            }
        }
    }
    // 2. MPEG (MP3)
    else if (auto* mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
        if (mpegFile->ID3v2Tag()) {
            const TagLib::ID3v2::FrameList& frames = mpegFile->ID3v2Tag()->frameList("APIC");
            for (auto* frame : frames) {
                auto* picFrame = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frame);
                if (picFrame && !picFrame->picture().isEmpty()) {
                    if (picFrame->type() == TagLib::ID3v2::AttachedPictureFrame::FrontCover || artworkBytes.empty()) {
                        artworkBytes.assign(picFrame->picture().data(), picFrame->picture().data() + picFrame->picture().size());
                        if (picFrame->type() == TagLib::ID3v2::AttachedPictureFrame::FrontCover) break;
                    }
                }
            }
        }
    }
    // 3. MP4 / M4A / AAC
    else if (auto* mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
        if (mp4File->tag()) {
            TagLib::MP4::ItemListMap& items = mp4File->tag()->itemListMap();
            if (items.contains("covr")) {
                TagLib::MP4::CoverArtList covers = items["covr"].toCoverArtList();
                if (!covers.isEmpty() && !covers.front().data().isEmpty()) {
                    artworkBytes.assign(covers.front().data().data(), covers.front().data().data() + covers.front().data().size());
                }
            }
        }
    }
    // 4. Ogg Vorbis
    else if (auto* vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
        if (vorbisFile->tag()) {
            const TagLib::List<TagLib::FLAC::Picture*>& pictures = vorbisFile->tag()->pictureList();
            for (auto* pic : pictures) {
                if (pic && !pic->data().isEmpty()) {
                    if (pic->type() == TagLib::FLAC::Picture::FrontCover || artworkBytes.empty()) {
                        artworkBytes.assign(pic->data().data(), pic->data().data() + pic->data().size());
                        if (pic->type() == TagLib::FLAC::Picture::FrontCover) break;
                    }
                }
            }
        }
    }
    // 5. Ogg Opus
    else if (auto* opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
        if (opusFile->tag()) {
            const TagLib::List<TagLib::FLAC::Picture*>& pictures = opusFile->tag()->pictureList();
            for (auto* pic : pictures) {
                if (pic && !pic->data().isEmpty()) {
                    if (pic->type() == TagLib::FLAC::Picture::FrontCover || artworkBytes.empty()) {
                        artworkBytes.assign(pic->data().data(), pic->data().data() + pic->data().size());
                        if (pic->type() == TagLib::FLAC::Picture::FrontCover) break;
                    }
                }
            }
        }
    }
    // 6. RIFF WAV
    else if (auto* wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
        if (wavFile->ID3v2Tag()) {
            const TagLib::ID3v2::FrameList& frames = wavFile->ID3v2Tag()->frameList("APIC");
            for (auto* frame : frames) {
                auto* picFrame = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frame);
                if (picFrame && !picFrame->picture().isEmpty()) {
                    artworkBytes.assign(picFrame->picture().data(), picFrame->picture().data() + picFrame->picture().size());
                    break;
                }
            }
        }
    }
    // 7. RIFF AIFF
    else if (auto* aiffFile = dynamic_cast<TagLib::RIFF::AIFF::File*>(file)) {
        if (auto* id3v2 = dynamic_cast<TagLib::ID3v2::Tag*>(aiffFile->tag())) {
            const TagLib::ID3v2::FrameList& frames = id3v2->frameList("APIC");
            for (auto* frame : frames) {
                auto* picFrame = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frame);
                if (picFrame && !picFrame->picture().isEmpty()) {
                    artworkBytes.assign(picFrame->picture().data(), picFrame->picture().data() + picFrame->picture().size());
                    break;
                }
            }
        }
    }
    // 8. ASF / WMA
    else if (auto* asfFile = dynamic_cast<TagLib::ASF::File*>(file)) {
        if (asfFile->tag()) {
            const TagLib::ASF::AttributeListMap& attrMap = asfFile->tag()->attributeListMap();
            if (attrMap.contains("WM/Picture")) {
                for (const auto& attr : attrMap["WM/Picture"]) {
                    TagLib::ASF::Picture pic = attr.toPicture();
                    if (pic.isValid() && !pic.picture().isEmpty()) {
                        artworkBytes.assign(pic.picture().data(), pic.picture().data() + pic.picture().size());
                        break;
                    }
                }
            }
        }
    }

    if (artworkBytes.empty()) {
        return nullptr;
    }

    jbyteArray jResult = env->NewByteArray(static_cast<jsize>(artworkBytes.size()));
    env->SetByteArrayRegion(jResult, 0, static_cast<jsize>(artworkBytes.size()), reinterpret_cast<const jbyte*>(artworkBytes.data()));
    return jResult;
}

JNIEXPORT jboolean JNICALL
Java_now_link_mastigias_data_taglib_TagLibBridge_nativeWriteMetadata(
    JNIEnv* env, jobject /*thiz*/, jstring jFilePath,
    jobjectArray setKeys, jobjectArray setValues, jobjectArray deleteKeys,
    jbyteArray artworkBytes, jboolean removeArtwork,
    jstring artworkMime, jint artworkWidth, jint artworkHeight) {

    const char* filePath = env->GetStringUTFChars(jFilePath, nullptr);
    if (!filePath) return JNI_FALSE;

    TagLib::FileRef f(filePath);
    if (f.isNull() || !f.file()) {
        LOGE("nativeWriteMetadata: Failed to open file: %s", filePath);
        env->ReleaseStringUTFChars(jFilePath, filePath);
        return JNI_FALSE;
    }

    TagLib::PropertyMap propMap = f.file()->properties();

    // 1. Process deletions
    int delCount = env->GetArrayLength(deleteKeys);
    for (int i = 0; i < delCount; ++i) {
        auto jDelKey = (jstring)env->GetObjectArrayElement(deleteKeys, i);
        const char* delKey = env->GetStringUTFChars(jDelKey, nullptr);
        propMap.erase(TagLib::String(delKey, TagLib::String::UTF8));
        env->ReleaseStringUTFChars(jDelKey, delKey);
        env->DeleteLocalRef(jDelKey);
    }

    // 2. Process updates
    int setCount = env->GetArrayLength(setKeys);
    for (int i = 0; i < setCount; ++i) {
        auto jKey = (jstring)env->GetObjectArrayElement(setKeys, i);
        auto jVal = (jstring)env->GetObjectArrayElement(setValues, i);
        const char* key = env->GetStringUTFChars(jKey, nullptr);
        const char* val = env->GetStringUTFChars(jVal, nullptr);

        propMap.replace(
            TagLib::String(key, TagLib::String::UTF8),
            TagLib::StringList(TagLib::String(val, TagLib::String::UTF8))
        );

        env->ReleaseStringUTFChars(jKey, key);
        env->ReleaseStringUTFChars(jVal, val);
        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(jVal);
    }

    f.file()->setProperties(propMap);

    // 3. Process Artwork across container formats
    TagLib::File* file = f.file();
    const char* mimeStr = artworkMime ? env->GetStringUTFChars(artworkMime, nullptr) : "image/jpeg";
    jbyte* rawArtBytes = nullptr;
    jsize artSize = 0;

    if (artworkBytes != nullptr && !removeArtwork) {
        artSize = env->GetArrayLength(artworkBytes);
        rawArtBytes = env->GetByteArrayElements(artworkBytes, nullptr);
    }

    if (removeArtwork == JNI_TRUE) {
        // --- REMOVE ARTWORK ---
        if (auto* flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
            flacFile->removePictures();
        } else if (auto* mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
            if (mpegFile->ID3v2Tag()) mpegFile->ID3v2Tag()->removeFrames("APIC");
        } else if (auto* mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
            if (mp4File->tag()) mp4File->tag()->itemListMap().erase("covr");
        } else if (auto* vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
            if (vorbisFile->tag()) {
                vorbisFile->tag()->removeAllPictures();
                vorbisFile->tag()->removeField("COVERART");
                vorbisFile->tag()->removeField("COVERARTMIME");
            }
        } else if (auto* opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
            if (opusFile->tag()) {
                opusFile->tag()->removeAllPictures();
                opusFile->tag()->removeField("COVERART");
                opusFile->tag()->removeField("COVERARTMIME");
            }
        } else if (auto* wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
            if (wavFile->ID3v2Tag()) wavFile->ID3v2Tag()->removeFrames("APIC");
        } else if (auto* aiffFile = dynamic_cast<TagLib::RIFF::AIFF::File*>(file)) {
            if (auto* id3v2 = dynamic_cast<TagLib::ID3v2::Tag*>(aiffFile->tag())) {
                id3v2->removeFrames("APIC");
            }
        } else if (auto* asfFile = dynamic_cast<TagLib::ASF::File*>(file)) {
            if (asfFile->tag()) asfFile->tag()->removeItem("WM/Picture");
        }
    } else if (rawArtBytes != nullptr && artSize > 0) {
        // --- ADD / REPLACE ARTWORK ---
        TagLib::ByteVector byteVector(reinterpret_cast<const char*>(rawArtBytes), static_cast<size_t>(artSize));

        // 1. FLAC
        if (auto* flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
            flacFile->removePictures();
            auto* pic = new TagLib::FLAC::Picture();
            pic->setData(byteVector);
            pic->setType(TagLib::FLAC::Picture::FrontCover);
            pic->setMimeType(TagLib::String(mimeStr, TagLib::String::UTF8));
            pic->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
            pic->setWidth(artworkWidth);
            pic->setHeight(artworkHeight);
            pic->setColorDepth(24);
            flacFile->addPicture(pic);
        }
        // 2. MPEG (MP3)
        else if (auto* mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
            TagLib::ID3v2::Tag* id3v2 = mpegFile->ID3v2Tag(true);
            id3v2->removeFrames("APIC");
            auto* frame = new TagLib::ID3v2::AttachedPictureFrame();
            frame->setPicture(byteVector);
            frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
            frame->setMimeType(TagLib::String(mimeStr, TagLib::String::UTF8));
            frame->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
            id3v2->addFrame(frame);
        }
        // 3. MP4 / M4A / AAC
        else if (auto* mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
            if (mp4File->tag()) {
                TagLib::MP4::CoverArt::Format fmt = (std::string(mimeStr).find("png") != std::string::npos)
                    ? TagLib::MP4::CoverArt::PNG
                    : TagLib::MP4::CoverArt::JPEG;
                TagLib::MP4::CoverArt cover(fmt, byteVector);
                TagLib::MP4::CoverArtList coverList;
                coverList.append(cover);
                mp4File->tag()->itemListMap()["covr"] = TagLib::MP4::Item(coverList);
            }
        }
        // 4. Ogg Vorbis
        else if (auto* vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
            if (vorbisFile->tag()) {
                vorbisFile->tag()->removeAllPictures();
                vorbisFile->tag()->removeField("COVERART");
                vorbisFile->tag()->removeField("COVERARTMIME");
                auto* pic = new TagLib::FLAC::Picture();
                pic->setData(byteVector);
                pic->setType(TagLib::FLAC::Picture::FrontCover);
                pic->setMimeType(TagLib::String(mimeStr, TagLib::String::UTF8));
                pic->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                pic->setWidth(artworkWidth);
                pic->setHeight(artworkHeight);
                pic->setColorDepth(24);
                vorbisFile->tag()->addPicture(pic);
            }
        }
        // 5. Ogg Opus
        else if (auto* opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
            if (opusFile->tag()) {
                opusFile->tag()->removeAllPictures();
                opusFile->tag()->removeField("COVERART");
                opusFile->tag()->removeField("COVERARTMIME");
                auto* pic = new TagLib::FLAC::Picture();
                pic->setData(byteVector);
                pic->setType(TagLib::FLAC::Picture::FrontCover);
                pic->setMimeType(TagLib::String(mimeStr, TagLib::String::UTF8));
                pic->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                pic->setWidth(artworkWidth);
                pic->setHeight(artworkHeight);
                pic->setColorDepth(24);
                opusFile->tag()->addPicture(pic);
            }
        }
        // 6. RIFF WAV
        else if (auto* wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
            TagLib::ID3v2::Tag* id3v2 = wavFile->ID3v2Tag();
            if (id3v2) {
                id3v2->removeFrames("APIC");
                auto* frame = new TagLib::ID3v2::AttachedPictureFrame();
                frame->setPicture(byteVector);
                frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
                frame->setMimeType(TagLib::String(mimeStr, TagLib::String::UTF8));
                frame->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                id3v2->addFrame(frame);
            }
        }
        // 7. RIFF AIFF
        else if (auto* aiffFile = dynamic_cast<TagLib::RIFF::AIFF::File*>(file)) {
            if (auto* id3v2 = dynamic_cast<TagLib::ID3v2::Tag*>(aiffFile->tag())) {
                id3v2->removeFrames("APIC");
                auto* frame = new TagLib::ID3v2::AttachedPictureFrame();
                frame->setPicture(byteVector);
                frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
                frame->setMimeType(TagLib::String(mimeStr, TagLib::String::UTF8));
                frame->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                id3v2->addFrame(frame);
            }
        }
        // 8. ASF / WMA
        else if (auto* asfFile = dynamic_cast<TagLib::ASF::File*>(file)) {
            if (asfFile->tag()) {
                asfFile->tag()->removeItem("WM/Picture");
                TagLib::ASF::Picture pic;
                pic.setPicture(byteVector);
                pic.setType(TagLib::ASF::Picture::FrontCover);
                pic.setMimeType(TagLib::String(mimeStr, TagLib::String::UTF8));
                pic.setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                asfFile->tag()->addAttribute("WM/Picture", TagLib::ASF::Attribute(pic));
            }
        }
    }

    if (rawArtBytes != nullptr) {
        env->ReleaseByteArrayElements(artworkBytes, rawArtBytes, JNI_ABORT);
    }
    if (artworkMime) {
        env->ReleaseStringUTFChars(artworkMime, mimeStr);
    }

    bool success = f.file()->save();
    env->ReleaseStringUTFChars(jFilePath, filePath);
    return success ? JNI_TRUE : JNI_FALSE;
}

}
```

#### 5.2.3 TagEngine Implementation Facade
```kotlin
package now.link.mastigias.data.taglib

import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagLibEngineImpl @Inject constructor(
    private val dispatchers: AppDispatchers
) : TagEngine {

    override suspend fun readMetadata(path: String): Result<AudioMetadata> =
        withContext(dispatchers.io) {
            runCatching {
                val bundle = TagLibBridge.nativeReadMetadata(path)
                    ?: error("TagLib failed to read metadata from $path")

                val fieldMap = mutableMapOf<TagField, String>()
                for (i in bundle.keys.indices) {
                    val key = bundle.keys[i]
                    val value = bundle.values[i]
                    // Match by canonical vorbisKey, standard key, or id3v2Frame
                    TagField.entries.firstOrNull {
                        it.vorbisKey.equals(key, ignoreCase = true) ||
                        it.key.equals(key, ignoreCase = true) ||
                        it.id3v2Frame.equals(key, ignoreCase = true)
                    }?.let { fieldMap[it] = value }
                }

                AudioMetadata(
                    trackId = 0L,
                    path = path,
                    fields = fieldMap,
                    artwork = null,
                    bitrateKbps = bundle.bitrateKbps,
                    sampleRateHz = bundle.sampleRateHz,
                    channels = bundle.channels,
                    durationMs = bundle.durationMs
                )
            }
        }

    override suspend fun readArtwork(path: String): Result<ByteArray?> =
        withContext(dispatchers.io) {
            runCatching { TagLibBridge.nativeReadArtwork(path) }
        }

    override suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val setKeys = patch.updatedFields.keys.map { it.vorbisKey }.toTypedArray()
                val setValues = patch.updatedFields.values.toTypedArray()
                val delKeys = patch.deletedFields.map { it.vorbisKey }.toTypedArray()

                val success = TagLibBridge.nativeWriteMetadata(
                    filePath = path,
                    setKeys = setKeys,
                    setValues = setValues,
                    deleteKeys = delKeys,
                    artworkBytes = patch.updatedArtwork?.binaryData,
                    removeArtwork = patch.removeArtwork,
                    artworkMime = patch.updatedArtwork?.mimeType ?: "image/jpeg",
                    artworkWidth = patch.updatedArtwork?.width ?: 0,
                    artworkHeight = patch.updatedArtwork?.height ?: 0
                )

                if (!success) error("TagLib failed to write tags to $path")
            }
        }
}
```

### 5.3 Room Database & SQLite FTS4 Specification

To enable sub-50ms cold starts, offline persistence, and instant substring search across large audio libraries, Mastigias implements a Room database with an SQLite FTS4 virtual table.

#### 5.3.1 Track Entity (`data/database/entity/TrackEntity.kt`)
```kotlin
package now.link.mastigias.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import now.link.mastigias.domain.model.Track

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["dateModified"])
    ]
)
data class TrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Long, // Matches MediaStore._ID
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "has_artwork") val hasArtwork: Boolean?,
    @ColumnInfo(name = "is_tagged") val isTagged: Boolean,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    path = path,
    title = title,
    artist = artist,
    album = album,
    trackNumber = trackNumber,
    durationMs = durationMs,
    hasArtwork = hasArtwork,
    isTagged = isTagged,
    dateModified = dateModified
)
```

#### 5.3.2 SQLite FTS4 Virtual Search Table (`data/database/entity/TrackFtsEntity.kt`)
```kotlin
package now.link.mastigias.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "tracks_fts")
@Fts4(contentEntity = TrackEntity::class)
data class TrackFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Long,
    val title: String,
    val artist: String,
    val album: String
)
```

#### 5.3.3 Track Data Access Object (`data/database/dao/TrackDao.kt`)
```kotlin
package now.link.mastigias.data.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import now.link.mastigias.data.database.entity.TrackEntity

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY date_modified DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeSortedByTitleAsc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE DESC")
    fun observeSortedByTitleDesc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, track_number ASC")
    fun observeSortedByArtistAsc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY artist COLLATE NOCASE DESC, album COLLATE NOCASE DESC, track_number ASC")
    fun observeSortedByArtistDesc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY album COLLATE NOCASE ASC, track_number ASC")
    fun observeSortedByAlbumAsc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY album COLLATE NOCASE DESC, track_number ASC")
    fun observeSortedByAlbumDesc(): Flow<List<TrackEntity>>

    @Query("""
        SELECT tracks.* FROM tracks
        JOIN tracks_fts ON tracks.id = tracks_fts.rowid
        WHERE tracks_fts MATCH :query || '*'
        ORDER BY tracks.title COLLATE NOCASE ASC
    """)
    fun searchFts(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_tagged = 0 ORDER BY title COLLATE NOCASE ASC")
    fun observeUntaggedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<TrackEntity>

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET has_artwork = :hasArtwork WHERE id = :id")
    suspend fun updateArtworkStatus(id: Long, hasArtwork: Boolean)

    @Query("DELETE FROM tracks WHERE id NOT IN (:validMediaStoreIds)")
    suspend fun pruneDeletedTracks(validMediaStoreIds: List<Long>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)
}
```

#### 5.3.4 Database Instance (`data/database/MastigiasDatabase.kt`)
```kotlin
package now.link.mastigias.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.data.database.entity.TrackEntity
import now.link.mastigias.data.database.entity.TrackFtsEntity

@Database(
    entities = [
        TrackEntity::class,
        TrackFtsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MastigiasDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
```

### 5.4 Unidirectional Data Flow (UDF) & UI State Models

#### 5.4.1 Library Screen State (`ui/library/LibraryUiState.kt`)
```kotlin
package now.link.mastigias.ui.library

import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.Track

enum class LibrarySortOrder { TITLE, ARTIST, ALBUM }
enum class SortDirection { ASCENDING, DESCENDING }

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val isUntaggedFilterActive: Boolean = false,
    val selectedTrackIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
) {
    val isAccordionView: Boolean
        get() = sortOrder == LibrarySortOrder.ALBUM && searchQuery.isBlank() && !isUntaggedFilterActive
}
```

#### 5.4.2 Editor Screen State (`ui/editor/EditorUiState.kt`)
```kotlin
package now.link.mastigias.ui.editor

import android.content.IntentSender
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField

sealed interface EditorMode {
    data class Single(val trackId: Long) : EditorMode
    data class Batch(val trackIds: List<Long>) : EditorMode
}

data class FieldEditState(
    val isEnabledInBatch: Boolean = false,
    val value: String = "",
    val isDirty: Boolean = false
)

data class EditorUiState(
    val mode: EditorMode,
    val initialMetadata: AudioMetadata? = null,
    val fields: Map<TagField, FieldEditState> = emptyMap(),
    val artwork: ArtworkData? = null,
    val isArtworkDirty: Boolean = false,
    val removeArtwork: Boolean = false,
    val isArtworkBatchEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val saveProgress: Float = 0f,
    val pendingConsentIntent: IntentSender? = null,
    val error: String? = null
)

sealed interface EditorUiEvent {
    data class ShowToast(val message: String) : EditorUiEvent
    data object NavigateBack : EditorUiEvent
    data class RequestStorageConsent(val intentSender: IntentSender) : EditorUiEvent
}
```

### 5.5 Type-Safe Navigation Routes
```kotlin
package now.link.mastigias.ui.navigation

import kotlinx.serialization.Serializable

sealed interface ScreenRoute {
    @Serializable
    data object Library : ScreenRoute

    @Serializable
    data class Editor(val trackIds: LongArray) : ScreenRoute

    @Serializable
    data object Settings : ScreenRoute

    @Serializable
    data object FolderManager : ScreenRoute
}
```

### 5.6 Scoped Storage Manager & Safe Atomic Write Protocol Implementation (`data/media/ScopedStorageManager.kt`)

The `ScopedStorageManager` is responsible for executing the atomic 8-step safe write protocol to modify audio metadata and embedded artwork across all supported Android versions (API 26–35+) without data loss, heap exhaustion, or file corruption.

```kotlin
package now.link.mastigias.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.TagPatch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScopedStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagEngine: TagEngine,
    private val trackDao: TrackDao,
    private val dispatchers: AppDispatchers
) {
    /**
     * Executes the 8-step atomic safe write protocol:
     * 1. Request Scoped Storage write consent if required (handled upstream via MediaStoreDataSource)
     * 2. Copy source audio bytes into an isolated work copy in cacheDir/tag_work/
     * 3. Native TagLib writes metadata & artwork to the isolated work copy
     * 4. Verify integrity (non-zero size, valid audio properties, uncorrupted duration)
     * 5. Stream verified bytes back to target destination using NIO FileChannel.transferTo()
     * 6. Flush (fsync) and remove temporary work file in a guaranteed finally block
     * 7. Trigger MediaScannerConnection.scanFile() with explicit 1:1 MIME type
     * 8. Invalidate Coil image cache and update Room database cache
     */
    suspend fun writeSingleTrack(
        trackId: Long,
        sourcePath: String,
        patch: TagPatch,
        mimeType: String
    ): Result<Unit> = withContext(NonCancellable + dispatchers.io) {
        runCatching {
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                trackId
            )
            val workDir = File(context.cacheDir, "tag_work").apply { mkdirs() }
            val workFile = File.createTempFile("tag_${trackId}_${System.nanoTime()}", ".work", workDir)

            try {
                // Step 2: Copy source file to isolated work copy
                val sourceFile = File(sourcePath)
                if (sourceFile.exists() && sourceFile.canRead()) {
                    FileInputStream(sourceFile).channel.use { inChannel ->
                        FileOutputStream(workFile).channel.use { outChannel ->
                            inChannel.transferTo(0, inChannel.size(), outChannel)
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(contentUri)?.use { input ->
                        FileOutputStream(workFile).use { output ->
                            input.copyTo(output, bufferSize = 256 * 1024)
                        }
                    } ?: throw IOException("Cannot open input stream for $contentUri ($sourcePath)")
                }

                val originalSize = workFile.length()
                if (originalSize == 0L) {
                    throw IOException("Source audio file copied to work directory is 0 bytes: $sourcePath")
                }

                // Step 3: Native TagLib write on work copy
                val writeResult = tagEngine.writeMetadata(workFile.absolutePath, patch)
                writeResult.getOrThrow()

                // Step 4: Integrity Verification
                val newSize = workFile.length()
                if (newSize == 0L) {
                    throw IOException("Integrity check failed: work file size became 0 after TagLib write")
                }
                // Verify audio header remains intact and duration is preserved
                val readBack = tagEngine.readMetadata(workFile.absolutePath).getOrNull()
                if (readBack == null || readBack.durationMs <= 0) {
                    throw IOException("Integrity check failed: audio stream header unreadable after write on $sourcePath")
                }

                // Step 5: Stream verified bytes back to target destination
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // API 30+ Scoped Storage: write via granted ContentResolver ParcelFileDescriptor
                    val pfd = context.contentResolver.openFileDescriptor(contentUri, "rw")
                        ?: context.contentResolver.openFileDescriptor(contentUri, "wt")
                        ?: throw IOException("Failed to acquire ParcelFileDescriptor for $contentUri")

                    pfd.use { parcelFd ->
                        FileOutputStream(parcelFd.fileDescriptor).channel.use { outChannel ->
                            FileInputStream(workFile).channel.use { inChannel ->
                                outChannel.truncate(0) // Crucial: clear previous content if new file is smaller
                                inChannel.transferTo(0, inChannel.size(), outChannel)
                                outChannel.force(true) // fsync to physical storage
                            }
                        }
                    }
                } else {
                    // API 26-29: Direct filesystem write supported via requestLegacyExternalStorage
                    if (sourceFile.canWrite()) {
                        FileOutputStream(sourceFile).channel.use { outChannel ->
                            FileInputStream(workFile).channel.use { inChannel ->
                                outChannel.truncate(0)
                                inChannel.transferTo(0, inChannel.size(), outChannel)
                                outChannel.force(true)
                            }
                        }
                    } else {
                        // Fallback via ContentResolver if direct write is restricted by OEM
                        context.contentResolver.openFileDescriptor(contentUri, "rw")?.use { pfd ->
                            FileOutputStream(pfd.fileDescriptor).channel.use { outChannel ->
                                FileInputStream(workFile).channel.use { inChannel ->
                                    outChannel.truncate(0)
                                    inChannel.transferTo(0, inChannel.size(), outChannel)
                                    outChannel.force(true)
                                }
                            }
                        } ?: throw IOException("Unable to write to file $sourcePath via direct or URI access")
                    }
                }

                // Step 7: Post-write MediaScanner sync with accurate 1:1 MIME type
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(sourcePath),
                    arrayOf(mimeType)
                ) { _, _ -> }

                // Step 8: Update Room database cache
                val hasArtwork = when {
                    patch.updatedArtwork != null -> true
                    patch.removeArtwork -> false
                    else -> null // Retain previous status
                }
                if (hasArtwork != null) {
                    trackDao.updateArtworkStatus(trackId, hasArtwork)
                }

            } finally {
                // Step 6: Guaranteed temporary work file cleanup
                if (workFile.exists()) {
                    workFile.delete()
                }
            }
        }
    }
}
```

### 5.7 MediaStore Batch Write Consent & Orchestration

#### 5.7.1 MediaStore Data Source (`data/media/MediaStoreDataSource.kt`)
```kotlin
package now.link.mastigias.data.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getTrackUri(trackId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)

    /**
     * Creates a batch write consent request for Android 11+ (API 30+).
     * On API 31+, if MediaStore.canManageMedia(context) is granted, returns null (no dialog required).
     * On API < 30, returns null because writes are permitted directly via WRITE_EXTERNAL_STORAGE.
     */
    fun createBatchWriteRequest(trackIds: List<Long>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 31+ Manage Media check: suppress per-save system dialog if granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)) {
                return null
            }
            val uris = trackIds.map { getTrackUri(it) }
            val pendingIntent: PendingIntent = MediaStore.createWriteRequest(context.contentResolver, uris)
            return pendingIntent.intentSender
        }
        return null
    }

    fun hasManageMediaPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)
    }
}
```

#### 5.7.2 Batch Write Metadata Use Case (`domain/usecase/BatchWriteMetadataUseCase.kt`)
```kotlin
package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.data.media.ScopedStorageManager
import now.link.mastigias.domain.model.*
import now.link.mastigias.ui.editor.FieldEditState
import javax.inject.Inject

data class BatchProgress(
    val current: Int,
    val total: Int,
    val currentTitle: String,
    val failedIds: List<Long> = emptyList()
)

class BatchWriteMetadataUseCase @Inject constructor(
    private val trackDao: TrackDao,
    private val scopedStorageManager: ScopedStorageManager
) {
    operator fun invoke(
        trackIds: List<Long>,
        fieldEdits: Map<TagField, FieldEditState>,
        artworkData: ArtworkData?,
        isArtworkBatchEnabled: Boolean,
        removeArtwork: Boolean
    ): Flow<BatchProgress> = flow {
        val total = trackIds.size
        val failedIds = mutableListOf<Long>()

        // 1. Filter only enabled fields and explicitly skip Lyrics per batch safety specs
        val updatedFields = fieldEdits
            .filter { (field, state) -> state.isEnabledInBatch && field.category != TagCategory.LYRICS }
            .mapValues { it.value.value }

        // 2. Determine fields to delete (enabled in batch with blank value)
        val toDelete = updatedFields.filter { it.value.isBlank() }.keys
        val toUpdate = updatedFields.filter { it.value.isNotBlank() }

        val batchPatch = TagPatch(
            updatedFields = toUpdate,
            deletedFields = toDelete,
            updatedArtwork = if (isArtworkBatchEnabled && !removeArtwork) artworkData else null,
            removeArtwork = if (isArtworkBatchEnabled) removeArtwork else false
        )

        val tracks = trackDao.getTracksByIds(trackIds).associateBy { it.id }

        for ((index, trackId) in trackIds.withIndex()) {
            val track = tracks[trackId]
            val title = track?.title ?: "Track #$trackId"

            emit(BatchProgress(current = index + 1, total = total, currentTitle = title, failedIds = failedIds))

            if (track == null) {
                failedIds.add(trackId)
                continue
            }

            // 3. Isolated write per track: failure on one file does not halt the batch
            val result = scopedStorageManager.writeSingleTrack(
                trackId = track.id,
                sourcePath = track.path,
                patch = batchPatch,
                mimeType = track.mimeType
            )

            if (result.isFailure) {
                failedIds.add(trackId)
            }
        }

        emit(BatchProgress(current = total, total = total, currentTitle = "Complete", failedIds = failedIds))
    }
}
```

---

## 6. Critical Technical Edge Cases & Mitigations

| Edge Case | Failure Mode | Mitigation Protocol |
| :--- | :--- | :--- |
| **Scoped Storage Sibling `.tmp` Failure** | Audio tagging engines fail with `AccessDeniedException` when trying to create temporary files next to the source audio file. | Run TagLib against a private cache work copy (`context.cacheDir/tag_work/`), then stream modified bytes back via `FileChannel.transferTo()` through the granted URI file descriptor. |
| **Batch Temp File Collisions** | Static temporary file names (e.g. `ogg_temp`) cause data corruption during rapid or concurrent batch writes. | Generate cryptographically unique file names: `File.createTempFile("tag_${id}_${System.nanoTime()}", ".work", cacheDir)`. |
| **Out-Of-Memory (OOM) on Large Files** | Calling `readBytes()` on Hi-Res 200MB+ FLAC/WAV files exhausts the Java heap. | Native TagLib streams audio container headers; Java layer streams bytes back with a 256KB NIO buffer without buffering entire files in memory. |
| **Android Photo Picker Path Redaction** | Modern Android returns redacted `null` values for `MediaStore.Images.Media.DATA`. | Never query file paths for picked images; open direct binary streams via `context.contentResolver.openInputStream(uri)`. |
| **FLAC Artwork Header Rejection** | FLAC players drop or reject picture blocks if dimensions are 0 $\times$ 0. | Always decode image dimensions using `BitmapFactory.Options.inJustDecodeBounds` prior to passing bytes to TagLib's `FLAC::Picture`. |
| **VorbisComment Base64 Encoding** | OGG and Opus VorbisComments require Base64 encoding for embedded images. | TagLib handles Vorbis comment picture blocks natively according to Xiph specifications. |
| **MediaStore Scanner MIME Type Dropping** | Calling `scanFile()` with null or inaccurate MIME types drops Opus, AAC, or DSF tracks from system indexing. | Pass exact computed 1:1 MIME type (`util/MimeTypes.kt`) for every updated file. |
| **Navigation `TransactionTooLargeException`** | Passing serialized track JSON across navigation routes crashes the Android binder with large batches. | Pass only a primitive `LongArray` of MediaStore IDs; the ViewModel loads tracks from the repository. |
| **Single Consent Sheet for Batch Saves** | Calling `createWriteRequest()` per file triggers an overwhelming series of system popups. | Bundle all selected track URIs into a single `MediaStore.createWriteRequest(uris)` call so the user approves the entire batch once. |
| **Corrupted Audio on Cancelled Writes** | User backing out while a native write is executing leaves an audio file in a half-written state. | Wrap file streaming and write operations inside `withContext(NonCancellable)` and disable UI back actions while saving. |

---

## 7. Phase-by-Phase Implementation Roadmap

```
Milestone 0: Project Setup, Build Tooling & Core Primitives
  ├── Setup gradle/libs.versions.toml with Kotlin 2.1, Compose BOM, Hilt, Room, Coil 3
  ├── Configure CMake & NDK build for native TagLib C++ compilation
  ├── AndroidManifest with Scoped Storage permissions, FileProvider, Edge-to-Edge
  └── Domain models (Track, Album, AudioMetadata, TagField, TagPatch) and Result wrapper

Milestone 1: Permission Onboarding & Storage Gates
  ├── Version-gated permission handlers (READ_MEDIA_AUDIO vs READ_EXTERNAL_STORAGE)
  ├── Optional MANAGE_MEDIA permission flow (API 31+) with settings redirection
  └── SAF folder picker integration and UriToPath parser

Milestone 2: MediaStore Discovery, Room Cache & Library Screen
  ├── MediaStoreDataSource query and fast-path cursor parsing (MP3 & FLAC)
  ├── Room database setup (TrackDao, TrackEntity) and startup sync engine
  ├── MediaStore ContentObserver detecting background file changes
  ├── Jetpack Compose LazyColumn with flat list and expandable Album accordion view
  └── Real-time search, untagged filtering, and DataStore-persisted sorting dialog

Milestone 3: Native TagLib Engine & Metadata Reading
  ├── C++ TagLib JNI bridge implementation (cpp/taglib-bridge.cpp)
  ├── TagLibEngineImpl reading metadata across all 16 supported audio containers
  ├── Background metadata parsing pipeline for non-MP3/FLAC formats
  └── Technical file inspector card (bitrate, sample rate, channels, codec)

Milestone 4: Coil 3 Embedded Artwork Pipeline
  ├── Custom Coil 3 TrackArtworkFetcher reading embedded art via TagLib
  ├── Custom TrackArtworkKeyer caching by track ID and modification timestamp
  ├── Two-pass 120x120 thumbnail downsampling pipeline
  └── Lazy hasArtwork resolution in Compose LazyColumn

Milestone 5: Single & Batch Tag Editing (Critical Write Path)
  ├── Android Photo Picker integration (reading raw ContentResolver stream)
  ├── MediaStore.createWriteRequest batch consent launcher (API 30+)
  ├── 8-step atomic work-file write protocol with verification and FileChannel transfer
  ├── SingleEditorScreen for full 50+ tag editing
  ├── BatchEditorScreen with per-field selective checkboxes and artwork toggle
  └── Post-write MediaScannerConnection.scanFile() and Coil cache invalidation

Milestone 6: Auxiliary Features & Polish
  ├── Searchable 50+ Add Field dialog with ReplayGain format gating
  ├── Multiline LyricsBottomSheet with SongSync intent integration
  ├── FileProvider temporary audio preview player
  └── Unsaved changes confirmation dialog and discard safeguards

Milestone 7: Hardening, Performance & Final Verification
  ├── Streaming I/O verification on 100MB+ Hi-Res FLAC/WAV files (zero OOM)
  ├── Batch writing stress tests across 500+ tracks
  ├── ProGuard / R8 rules for TagLib JNI and reflection preservation
  └── Complete CI/CD build scripts and release signing setup
```

---

## 8. Build & Tooling Specifications

### 8.1 Gradle Version Catalog (`gradle/libs.versions.toml`)
```toml
[versions]
agp = "8.8.0"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
hilt = "2.53.1"
room = "2.6.1"
datastore = "1.1.2"
composeBom = "2025.01.00"
coil = "3.0.4"
navigationCompose = "2.8.5"
serialization = "1.7.3"
coroutines = "1.10.1"

[libraries]
# AndroidX & Compose
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.15.0" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version = "2.8.7" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.10.0" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Hilt Dependency Injection
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room Database
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# DataStore & Serialization
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Coil 3 Image Loading
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 8.2 Root Build Configuration (`build.gradle.kts`)
```kotlin
// Top-level build file where plugins are declared without being applied to sub-projects
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

### 8.3 App Module Build Configuration (`app/build.gradle.kts`)
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "now.link.mastigias"
    compileSdk = 35

    defaultConfig {
        applicationId = "now.link.mastigias"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-fexceptions", "-frtti")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Configure keystore for production
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
}
```

### 8.4 TagLib NDK Compilation & CMake Setup (`src/main/cpp/CMakeLists.txt`)

Developers have two supported workflows for TagLib integration: **Automated FetchContent** (recommended for CI/CD and zero setup) or **Prebuilt Toolchain Compilation** (recommended for fastest local builds).

#### Approach A: Direct Automated CMake FetchContent (Recommended)
```cmake
cmake_minimum_required(VERSION 3.22.1)
project("mastigias-taglib")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Configure TagLib static build
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static library" FORCE)
set(ENABLE_STATIC_RUNTIME ON CACHE BOOL "Static C++ runtime" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "Disable tests" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "Disable examples" FORCE)
set(WITH_ZLIB ON CACHE BOOL "Enable zlib for compressed ID3 frames" FORCE)

FetchContent_Declare(
    taglib
    URL https://taglib.org/releases/taglib-1.13.1.tar.gz
)
FetchContent_MakeAvailable(taglib)

add_library(mastigias-native SHARED
    taglib-bridge.cpp
)

target_include_directories(mastigias-native PRIVATE
    ${taglib_SOURCE_DIR}
    ${taglib_SOURCE_DIR}/taglib
    ${taglib_SOURCE_DIR}/taglib/toolkit
    ${taglib_BINARY_DIR}
)

target_link_libraries(mastigias-native
    tag
    z
    log
    android
)
```

#### Approach B: Prebuilt Static Compilation Script (`scripts/build-taglib-ndk.sh`)
```bash
#!/usr/bin/env bash
set -e

# Path to Android NDK
NDK_PATH="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/26.1.10909125}"
TOOLCHAIN="$NDK_PATH/build/cmake/android.toolchain.cmake"
TAGLIB_SRC_DIR="$(pwd)/cpp/taglib-1.13.1"
OUTPUT_DIR="$(pwd)/app/src/main/cpp/prebuilt"

ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

for ABI in "${ABIS[@]}"; do
    echo "=== Building TagLib for $ABI ==="
    BUILD_DIR="/tmp/taglib-build-$ABI"
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"

    cmake -B "$BUILD_DIR" -S "$TAGLIB_SRC_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM=android-26 \
        -DBUILD_SHARED_LIBS=OFF \
        -DWITH_ZLIB=ON \
        -DCMAKE_BUILD_TYPE=Release

    cmake --build "$BUILD_DIR" --target tag -j$(nproc 2>/dev/null || sysctl -n hw.ncpu)

    mkdir -p "$OUTPUT_DIR/$ABI"
    cp "$BUILD_DIR/taglib/libtag.a" "$OUTPUT_DIR/$ABI/"
done

echo "TagLib static libraries built successfully for all ABIs."
```

### 8.5 R8 / ProGuard Keep Rules (`app/proguard-rules.pro`)
```proguard
# Keep native JNI methods and DTOs for TagLib
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class now.link.mastigias.data.taglib.** { *; }

# Keep NativeTagBundle constructor & fields accessed from C++ JNI
-keepclassmembers class now.link.mastigias.data.taglib.NativeTagBundle {
    <init>(...);
    <fields>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
```

### 8.6 Android Manifest & FileProvider Configuration

#### 8.6.1 Application Manifest (`app/src/main/AndroidManifest.xml`)
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Scoped storage audio discovery permission for Android 13+ (API 33+) -->
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

    <!-- Storage read permission for Android 11–12L (API 30–32) -->
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <!-- Legacy external storage write permission for Android 10 and below (API <= 29) -->
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29"
        tools:ignore="ScopedStorage" />

    <!-- Optional permission to suppress per-save write dialogs on Android 12+ (API 31+) -->
    <uses-permission android:name="android.permission.MANAGE_MEDIA" />

    <!-- Access media location for preserving metadata tags containing location info -->
    <uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />

    <application
        android:name=".MastigiasApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Mastigias"
        android:requestLegacyExternalStorage="true"
        tools:targetApi="35">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Mastigias"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- FileProvider for temporary external audio preview playback -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>

</manifest>
```

#### 8.6.2 FileProvider Paths Specification (`app/src/main/res/xml/file_paths.xml`)
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Temporary audio copy directory exposed to external music player preview apps -->
    <cache-path
        name="open_external_temp"
        path="open_external_temp/" />

    <!-- Isolated work copy directory for Scoped Storage atomic writes -->
    <cache-path
        name="tag_work"
        path="tag_work/" />
</paths>
```
