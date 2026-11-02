#!/usr/bin/env bash
# Builds Glass_Alarm.apk with aapt2 + javac + d8 + apksigner (no Gradle).
#
# Requires: JDK 17, Android SDK build-tools 34.0.0, platform android-34.
#   export ANDROID_SDK_ROOT=/path/to/android-sdk
#   export JAVA_HOME=/path/to/jdk-17
set -euo pipefail

: "${ANDROID_SDK_ROOT:?set ANDROID_SDK_ROOT to your Android SDK}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-34.0.0}"
PLATFORM="${PLATFORM:-android-34}"

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
BT="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
AJ="$ANDROID_SDK_ROOT/platforms/$PLATFORM/android.jar"
KS="${KEYSTORE:-$ROOT/glassalarm.jks}"
KS_PASS="${KS_PASS:-glassalarm}"

[ -x "$BT/aapt2" ] || { echo "aapt2 not found at $BT"; exit 1; }
[ -f "$AJ" ] || { echo "android.jar not found at $AJ"; exit 1; }

cd "$APP"
rm -rf build && mkdir -p build/gen build/classes build/dex

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir res -o build/res.zip

echo "== aapt2 link =="
"$BT/aapt2" link -o build/base.apk \
  -I "$AJ" \
  --manifest AndroidManifest.xml \
  -A assets \
  --java build/gen \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 2 --version-name 1.1 \
  --no-version-vectors \
  build/res.zip

echo "== javac =="
javac -nowarn -source 8 -target 8 -bootclasspath "$AJ" \
  -d build/classes $(find src build/gen -name '*.java')

echo "== d8 =="
"$BT/d8" --release --min-api 24 --lib "$AJ" --output build/dex \
  $(find build/classes -name '*.class')

echo "== package dex =="
( cd build/dex && zip -q -X ../base.apk classes.dex )

echo "== zipalign =="
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk

echo "== sign =="
if [ ! -f "$KS" ]; then
  echo "generating keystore at $KS (keep it safe — needed for future updates)"
  keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 \
    -alias glassalarm -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=Glass Alarm, OU=Mobile, O=Rajok, L=Bengaluru, C=IN" >/dev/null
fi
"$BT/apksigner" sign --ks "$KS" --ks-key-alias glassalarm \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$ROOT/dist/Glass_Alarm.apk" build/aligned.apk

echo "== verify =="
"$BT/apksigner" verify --verbose "$ROOT/dist/Glass_Alarm.apk" | head -5
ls -la "$ROOT/dist/Glass_Alarm.apk"
