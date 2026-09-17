#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "=== Mastigias Release Builder ==="
echo ""

# Check for key.properties
if [ ! -f "key.properties" ]; then
  echo "ERROR: key.properties not found at ./key.properties"
  echo ""
  echo "To set up release signing:"
  echo "  1. Copy key.properties.example to key.properties:"
  echo "     cp key.properties.example key.properties"
  echo "  2. Generate a keystore if you don't already have one:"
  echo "     keytool -genkey -v -keystore upload-keystore.jks -alias mastigias -keyalg RSA -keysize 2048 -validity 10000"
  echo "  3. Update key.properties with your keystore path and passwords."
  exit 1
fi

# Check for keystore file referenced in key.properties
KEYSTORE_FILE=$(grep '^storeFile=' key.properties | cut -d'=' -f2- | tr -d '\r\n')
if [ -z "$KEYSTORE_FILE" ] || { [ ! -f "$KEYSTORE_FILE" ] && [ ! -f "app/$KEYSTORE_FILE" ]; }; then
  echo "ERROR: Keystore file '$KEYSTORE_FILE' referenced in key.properties not found."
  exit 1
fi

VERSION_NAME=$(grep 'versionName =' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
VERSION_CODE=$(grep 'versionCode =' app/build.gradle.kts | head -1 | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/')

echo "Version Name: $VERSION_NAME"
echo "Version Code: $VERSION_CODE"
echo ""

# Run verification tests
echo "--- Running verification tests ---"
./gradlew test compileDebugKotlin

# Build arm64 release APK
echo ""
echo "--- Building arm64-v8a Release APK ---"
./gradlew assembleRelease -PtargetAbi=arm64-v8a
ARM64_OUTPUT="Mastigias.v${VERSION_NAME}.apk"
cp app/build/outputs/apk/release/app-release.apk "$ARM64_OUTPUT"

# Build universal release APK
echo ""
echo "--- Building Universal Release APK ---"
./gradlew clean assembleRelease
UNIVERSAL_OUTPUT="Mastigias.v${VERSION_NAME}-universal.apk"
cp app/build/outputs/apk/release/app-release.apk "$UNIVERSAL_OUTPUT"

echo ""
echo "=== Build Complete ==="
echo ""
echo "Artifacts generated:"
ls -lh "$ARM64_OUTPUT" "$UNIVERSAL_OUTPUT"
echo ""
echo "Native debug symbols:"
echo "  app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip"
echo ""
echo "Next steps:"
echo "  1. Test on connected device:"
echo "     adb install -r $ARM64_OUTPUT"
echo "  2. Create git tag:"
echo "     git tag v${VERSION_NAME}"
echo "  3. Push git tag to trigger GitHub Actions release:"
echo "     git push origin v${VERSION_NAME}"
