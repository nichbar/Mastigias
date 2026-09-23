# Release Checklist

Use this for every release. F-Droid auto-detects new versions from git tags
(`AutoUpdateMode: Version v%v`, `UpdateCheckMode: Tags`), so the tag is the
single source of truth — get it right before pushing.

## 1. Prepare

- [ ] All unit tests and compilation pass: `./gradlew test compileDebugKotlin`
- [ ] `git status` is clean; everything intended for the release is committed

## 2. Bump versions in `app/build.gradle.kts`

- [ ] `versionCode` incremented by exactly 1 (must strictly increase; never reuse or decrease)
- [ ] `versionName` updated to the new semantic version (e.g. `1.3.0`)

## 3. Write the F-Droid changelog

- [ ] Create `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
  (filename = the **new** versionCode, e.g. `5.txt` for versionCode 5)
- [ ] Content: user-facing changes as plain-text bullet points, ≤ ~500 characters
- [ ] Derived from commit history since the last tag:
  `git log --pretty=format:'%s' $(git describe --tags --abbrev=0)..HEAD`

## 4. Commit and tag (order matters)

- [ ] Commit the version bump + changelog **first**
- [ ] Build release artifacts: `./release.sh`
- [ ] Smoke-test the APK on a device: `adb install -r Mastigias.v<versionName>.apk`
- [ ] Tag the commit that contains the bump: `git tag v<versionName>`
- [ ] Push: `git push origin <branch> && git push origin v<versionName>`
- [ ] **Never** move or delete a pushed tag — F-Droid and GitHub Actions both key off it

## 5. Post-release verification

- [ ] GitHub Actions release workflow succeeded and APKs are attached to the release
- [ ] Within ~24h: F-Droid's checkupdates bot picks up the tag (no action needed)
- [ ] Within ~1 week: confirm the build succeeded on https://monitor.f-droid.org/builds
  — F-Droid sends **no notification** on build failure; a failed build silently
  skips the version until fixed
- [ ] Confirm the app page updated: https://f-droid.org/packages/now.link.mastigias/

## 6. When to edit fdroiddata metadata again

Normally never. Open an MR against `metadata/now.link.mastigias.yml`
(draft kept in `docs/fdroid/now.link.mastigias.yml`) only if:

- [ ] `ndkVersion` changed → update the pinned `ndk:` field
- [ ] A new dependency might violate F-Droid inclusion policy
  (non-free, tracking, Maven Central-only blobs)
- [ ] Gradle module/task layout changed (e.g. `subdir` or build command)
- [ ] Tag naming scheme changed (would break `UpdateCheckMode: Tags ^v\d+\.\d+\.\d+$`)
