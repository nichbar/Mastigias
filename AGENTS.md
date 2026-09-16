# Project Contract

## Build And Test

- Compile Kotlin: `./gradlew compileDebugKotlin`
- Unit Tests: `./gradlew test`
- Debug APK: `./gradlew assembleDebug`
- Release APK (ProGuard/R8): `./gradlew assembleRelease`
- Install to Device: `./gradlew installDebug`
- Launch Main Activity: `adb shell am start -n now.link.mastigias.debug/now.link.mastigias.MainActivity`

## Architecture Boundaries

- Domain layer (`app/src/main/java/now/link/mastigias/domain/`) contains pure Kotlin business logic only:
  - Zero Android framework imports (`android.*`), zero Compose imports, zero Room annotations.
  - Repositories and engines are declared strictly as interfaces (`TagEngine`, `MusicRepository`, `PreferencesRepository`).
- Data layer (`app/src/main/java/now/link/mastigias/data/`) contains all concrete implementations:
  - Room entities, DAOs, and SQLite FTS4 virtual tables live in `data/database/`.
  - Scoped Storage atomic write protocol and MediaStore data sources live in `data/media/`.
  - Native TagLib JNI bridge and engine implementation live in `data/taglib/` and `app/src/main/cpp/`.
  - DataStore preference persistence lives in `data/datastore/`.
  - Coil 3 custom fetchers and keyers live in `data/image/`.
- Dependency Injection (`app/src/main/java/now/link/mastigias/di/`) binds all singletons and repositories via Hilt modules.
- Presentation layer (`app/src/main/java/now/link/mastigias/ui/`) contains Jetpack Compose UI, Material 3 theming, ViewModels, and navigation.

## Coding Conventions

- Strictly preserve Clean Architecture invariants between layers.
- Enforce Unidirectional Data Flow (UDF): ViewModels expose immutable `StateFlow<UiState>` and one-off event `Channel<UiEvent>`.
- Never execute disk I/O or blocking operations on `Dispatchers.Main`; inject and use `AppDispatchers.io` or `AppDispatchers.default`.
- Execute file writes within `withContext(NonCancellable + dispatchers.io)` to prevent audio corruption if coroutines are cancelled.
- Use primitive flat array transfer DTOs (`NativeTagBundle`) across the JNI boundary to prevent reflection overhead and ProGuard breakage.
- Always provide functional JVM fallbacks in `TagLibEngineImpl` when native binaries are unavailable in host test environments.

## Safety Rails

### NEVER

- Import `android.*`, `androidx.compose.*`, or `androidx.room.*` into `domain/`.
- Write directly next to audio files in Scoped Storage (always use the staged work-file protocol in `cacheDir/tag_work/`).
- Modify or batch-overwrite `TagCategory.LYRICS` during multi-file batch operations.
- Pass heavy data structures or serialized entities across Compose navigation routes (pass primitive `LongArray` IDs only).
- Query `MediaStore.Images.Media.DATA` path for picked images; always open direct binary streams via `ContentResolver.openInputStream(uri)`.
- Commit code without passing `./gradlew test compileDebugKotlin`.

### ALWAYS

- Decode image dimensions using `BitmapFactory.Options.inJustDecodeBounds` or `ImageUtils` before passing artwork bytes to TagLib.
- Ensure temporary work files are deleted in a guaranteed `finally` block.
- Pass accurate 1:1 MIME type mappings (`AudioFormats.getMimeTypeForPath`) to `MediaScannerConnection.scanFile()`.
- Use `FileChannel.transferTo()` with `truncate(0)` and `force(true)` (fsync) for physical storage persistence.
- Show `git status` / `git diff` before creating commits.

## Verification

- Domain & Data logic: `./gradlew test` (all unit test suites must pass).
- Kotlin & KSP compilation: `./gradlew compileDebugKotlin`.
- Release & ProGuard verification: `./gradlew assembleRelease` (R8 must shrink without stripping native JNI methods or DTOs).
- Device verification: run on connected device/emulator via `./gradlew installDebug` and inspect `adb logcat` for clean execution.

## Compact Instructions

Preserve:

1. Architecture decisions (Clean Architecture, TagLib JNI flat arrays, 8-step atomic Scoped Storage write protocol, type-safe navigation)
2. Modified files and key changes
3. Current verification status (test pass counts, build status)
4. Open risks, TODOs, rollback notes
