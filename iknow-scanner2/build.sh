#!/system/bin/sh
set -u

DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="com.iknowscanner2"
MAIN="com.iknowscanner2.MainActivity"

ANDROID_JAR="${ANDROID_JAR:-/usr/local/lib/android-36.jar}"
R8_JAR="${R8_JAR:-/usr/local/lib/r8.jar}"

rm -rf "$DIR/build"
mkdir -p "$DIR/build/obj" "$DIR/build/dex" "$DIR/build/apk"

echo "=== 1/5 javac ==="
javac -encoding UTF-8 -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$DIR/build/obj" \
  $(find "$DIR/java" -name "*.java") || exit 1

echo "=== 2/5 d8 ==="
java -cp "$R8_JAR" com.android.tools.r8.D8 \
  --min-api 21 \
  --output "$DIR/build/dex" \
  $(find "$DIR/build/obj" -name "*.class") || exit 1

echo "=== 3/5 aapt2 compile/link ==="
aapt2 link \
  -o "$DIR/build/apk/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$DIR/AndroidManifest.xml" \
  --min-sdk-version 21 \
  --target-sdk-version 36 \
  --java "$DIR/build/obj" || exit 1

cd "$DIR/build/dex"
zip -q "$DIR/build/apk/base.apk" classes.dex || exit 1

echo "=== 4/5 align/sign ==="
zipalign -f 4 "$DIR/build/apk/base.apk" "$DIR/build/apk/aligned.apk" || cp "$DIR/build/apk/base.apk" "$DIR/build/apk/aligned.apk"

apksigner sign --ks "$HOME/.apksigner/keystore.jks" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias android \
  --out "$DIR/iknow-scanner2.apk" \
  "$DIR/build/apk/aligned.apk" || exit 1

[ -d /data/local/tmp ] && cp "$DIR/iknow-scanner2.apk" /data/local/tmp/iknow-scanner2.apk

echo "=== 完成 ==="
echo "APK: $DIR/iknow-scanner2.apk"
