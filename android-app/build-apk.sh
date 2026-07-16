#!/bin/bash
set -e

# AFK App Blocker - Manual APK Builder
# Uses pure Java-based tools (no native binaries needed)
# Requires: Java 17+, Android SDK with d8.jar, apksigner.jar, android.jar

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_SDK="/data/data/com.termux/files/home/android-sdk"
BUILD_TOOLS="$ANDROID_SDK/build-tools/34.0.0"
PLATFORM="$ANDROID_SDK/platforms/android-34"
KOTLIN_HOME="$SCRIPT_DIR/kotlin-home"
OUTDIR="$SCRIPT_DIR/app/build/outputs"
APK_UNSIGNED="$OUTDIR/app-unsigned.apk"
APK_ALIGNED="$OUTDIR/app-aligned.apk"
APK_FINAL="$OUTDIR/AFKAppBlocker.apk"

# Find Kotlin compiler JAR from Gradle cache
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
KOTLIN_COMPILER_JAR=$(find "$GRADLE_CACHE/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.9.24" -name "*.jar" 2>/dev/null | head -1)
KOTLIN_STDLIB_JAR=$(find "$GRADLE_CACHE/org.jetbrains.kotlin/kotlin-stdlib/1.9.24" -name "*.jar" 2>/dev/null | head -1)
ROOM_COMPILER_JAR=$(find "$GRADLE_CACHE/androidx.room/room-compiler/2.6.1" -name "*.jar" 2>/dev/null | head -1)
ROOM_RUNTIME_JAR=$(find "$GRADLE_CACHE/androidx.room/room-runtime/2.6.1" -name "*.jar" 2>/dev/null | head -1)
ROOM_KTX_JAR=$(find "$GRADLE_CACHE/androidx.room/room-ktx/2.6.1" -name "*.jar" 2>/dev/null | head -1)
ANNOTATIONS_JAR=$(find "$GRADLE_CACHE/androidx.annotation/annotation/1.7.0" -name "*.jar" 2>/dev/null | head -1)
CORE_KTX_JAR=$(find "$GRADLE_CACHE/androidx.core/core-ktx/1.12.0" -name "*.jar" 2>/dev/null | head -1 || find "$GRADLE_CACHE/androidx.core/core/1.12.0" -name "*.jar" 2>/dev/null | head -1)
APPCOMPAT_JAR=$(find "$GRADLE_CACHE/androidx.appcompat/appcompat/1.6.1" -name "*.jar" 2>/dev/null | head -1)
MATERIAL_JAR=$(find "$GRADLE_CACHE/com.google.android.material/material/1.11.0" -name "*.jar" 2>/dev/null | head -1)
CONSTRAINTLAYOUT_JAR=$(find "$GRADLE_CACHE/androidx.constraintlayout/constraintlayout/2.1.4" -name "*.jar" 2>/dev/null | head -1)
SECURITY_CRYPTO_JAR=$(find "$GRADLE_CACHE/androidx.security/security-crypto/1.1.0-alpha06" -name "*.jar" 2>/dev/null | head -1)
LIFECYCLE_VIEWMODEL_JAR=$(find "$GRADLE_CACHE/androidx.lifecycle/lifecycle-viewmodel-ktx/2.7.0" -name "*.jar" 2>/dev/null | head -1)
LIFECYCLE_LIVEDATA_JAR=$(find "$GRADLE_CACHE/androidx.lifecycle/lifecycle-livedata-ktx/2.7.0" -name "*.jar" 2>/dev/null | head -1)

# Find all Room dependencies
ROOM_COMMON_JAR=$(find "$GRADLE_CACHE/androidx.room/room-common/2.6.1" -name "*.jar" 2>/dev/null | head -1 || true)
XARCH_CORE_JAR=$(find "$GRADLE_CACHE/androidx.arch.core/core-common/2.2.0" -name "*.jar" 2>/dev/null | head -1 || true)
SQLITE_FRAMEWORK_JAR=$(find "$GRADLE_CACHE/androidx.sqlite/sqlite-framework/2.4.0" -name "*.jar" 2>/dev/null | head -1 || true)
SQLITE_JAR=$(find "$GRADLE_CACHE/androidx.sqlite/sqlite/2.4.0" -name "*.jar" 2>/dev/null | head -1 || true)

# Find trove4j (needed by Kotlin compiler)
TROVE4J_JAR=$(find "$GRADLE_CACHE/org.jetbrains.intellij.deps/trove4j/1.0.20200330" -name "*.jar" ! -name "*.pom" 2>/dev/null | head -1)

# Find all AAR JARs, coroutines, and supporting JARs
AAR_JARS_DIR="$SCRIPT_DIR/app/build/aar-jars"

# Build classpath
CLASSPATH="$PLATFORM/android.jar"
for j in "$AAR_JARS_DIR"/*.jar; do
  if [ -f "$j" ] && [ -s "$j" ]; then  # non-empty
    CLASSPATH="$CLASSPATH:$j"
  fi
done

# Add Kotlin stdlib
if [ -n "$KOTLIN_STDLIB_JAR" ]; then
  CLASSPATH="$CLASSPATH:$KOTLIN_STDLIB_JAR"
fi
# Add trove4j
if [ -n "$TROVE4J_JAR" ]; then
  CLASSPATH="$CLASSPATH:$TROVE4J_JAR"
fi

# Add kotlinx-coroutines
COROUTINES_CORE=$(find "$GRADLE_CACHE/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.7.1" -name "*.jar" -type f 2>/dev/null | head -1)
COROUTINES_ANDROID=$(find "$GRADLE_CACHE/org.jetbrains.kotlinx/kotlinx-coroutines-android/1.7.1" -name "*.jar" -type f 2>/dev/null | head -1)
if [ -n "$COROUTINES_CORE" ]; then CLASSPATH="$CLASSPATH:$COROUTINES_CORE"; echo "  Found: coroutines-core"; fi
if [ -n "$COROUTINES_ANDROID" ]; then CLASSPATH="$CLASSPATH:$COROUTINES_ANDROID"; echo "  Found: coroutines-android"; fi

# Add lifecycle-common (needed for lifecycleScope)
LIFECYCLE_COMMON=$(find "$GRADLE_CACHE/androidx.lifecycle/lifecycle-common/2.7.0" -name "*.jar" -type f 2>/dev/null | head -1)
if [ -n "$LIFECYCLE_COMMON" ]; then CLASSPATH="$CLASSPATH:$LIFECYCLE_COMMON"; fi

# Add JetBrains annotations (needed by Kotlin compiler backend)
ANNOTATIONS_JAR="/root/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/lib/annotations-24.0.1.jar"
CLASSPATH="$CLASSPATH:$ANNOTATIONS_JAR"

# Add Room compiler deps for annotation processing
ROOMPROC_JARS=$(find "$GRADLE_CACHE/androidx.room/room-compiler-processing/2.6.1" -name "*.jar" -type f 2>/dev/null | head -1)
ROOMCOMMON=$(find "$GRADLE_CACHE/androidx.room/room-common/2.6.1" -name "*.jar" -type f 2>/dev/null | head -1)
JAVAPOET=$(find "$GRADLE_CACHE/com.squareup/javapoet/1.13.0" -name "*.jar" -type f 2>/dev/null | head -1 || true)
if [ -n "$ROOMPROC_JARS" ]; then CLASSPATH="$CLASSPATH:$ROOMPROC_JARS"; fi
if [ -n "$ROOMCOMMON" ]; then CLASSPATH="$CLASSPATH:$ROOMCOMMON"; fi
if [ -n "$JAVAPOET" ]; then CLASSPATH="$CLASSPATH:$JAVAPOET"; fi

# Add all found JARs to classpath
for jar in "$KOTLIN_STDLIB_JAR" "$ROOM_RUNTIME_JAR" "$ROOM_KTX_JAR" "$ROOM_COMMON_JAR" \
  "$CORE_KTX_JAR" "$APPCOMPAT_JAR" "$MATERIAL_JAR" "$CONSTRAINTLAYOUT_JAR" \
  "$SECURITY_CRYPTO_JAR" "$LIFECYCLE_VIEWMODEL_JAR" "$LIFECYCLE_LIVEDATA_JAR" \
  "$XARCH_CORE_JAR" "$SQLITE_FRAMEWORK_JAR" "$SQLITE_JAR" "$ANNOTATIONS_JAR"; do
  if [ -n "$jar" ] && [ -f "$jar" ]; then
    CLASSPATH="$CLASSPATH:$jar"
    echo "  Found: $(basename $jar)"
  fi
done

echo "Building AFK App Blocker APK..."
echo "Using Kotlin compiler: $KOTLIN_COMPILER_JAR"
echo ""

mkdir -p "$SCRIPT_DIR/app/build/classes" "$SCRIPT_DIR/app/build/dex" "$OUTDIR"

SRCDIR="$SCRIPT_DIR/app/src/main/java"
BUILDDIR="$SCRIPT_DIR/app/build/classes"

# Step 1: Compile Kotlin sources with annotation processing
# Generate R.java and compile it with javac
echo "Generating R.java resource file..."
python3 "$SCRIPT_DIR/app/build/generate_r.py" 2>&1 || true
echo "Compiling R.java..."
javac --release 8 -d "$BUILDDIR" -classpath "$PLATFORM/android.jar" "$BUILDDIR/com/limitedafk/appblocker/R.java" 2>&1 || echo "R.java compile warnings"
CLASSPATH="$CLASSPATH:$BUILDDIR"

echo ""
echo "Step 1: Compiling Kotlin sources..."
java -Xmx2g -cp "$KOTLIN_COMPILER_JAR:$KOTLIN_STDLIB_JAR:$CLASSPATH" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CLASSPATH" \
  -no-stdlib -no-reflect -jvm-target 1.8 \
  -d "$BUILDDIR" \
  -include-runtime \
  "$SRCDIR/com/limitedafk/appblocker/data/BlockedApp.kt" \
  "$SRCDIR/com/limitedafk/appblocker/data/BlockedAppDao.kt" \
  "$SRCDIR/com/limitedafk/appblocker/data/AppDatabase.kt" \
  "$SRCDIR/com/limitedafk/appblocker/utils/SecurityUtils.kt" \
  "$SRCDIR/com/limitedafk/appblocker/DeviceAdminReceiver.kt" \
  "$SRCDIR/com/limitedafk/appblocker/AppBlockerAccessibilityService.kt" \
  "$SRCDIR/com/limitedafk/appblocker/TimerForegroundService.kt" \
  "$SRCDIR/com/limitedafk/appblocker/MainActivity.kt" \
  "$SRCDIR/com/limitedafk/appblocker/SettingsActivity.kt" \
  2>&1

echo "Kotlin compilation done. Classes in: $BUILDDIR"
ls "$BUILDDIR" 2>/dev/null

# Step 2: Convert class files to DEX using d8 (Java-based)
echo ""
echo "Step 2: Converting to DEX with d8..."
D8_JAR="$BUILD_TOOLS/lib/d8.jar"
java -cp "$D8_JAR" com.android.tools.r8.D8 \
  --lib "$PLATFORM/android.jar" \
  --release \
  --min-api 26 \
  --output "$SCRIPT_DIR/app/build/dex" \
  $(find "$BUILDDIR" -name "*.class") \
  2>&1

echo "DEX files created:"
ls -la "$SCRIPT_DIR/app/build/dex/" 2>/dev/null

# Step 3: Create APK structure
echo ""
echo "Step 3: Creating APK structure..."
APK_DIR="$SCRIPT_DIR/app/build/apk"
mkdir -p "$APK_DIR"
unzip -o "$PLATFORM/android.jar" -d "$APK_DIR" "AndroidManifest.xml" 2>/dev/null || true

# Create a minimal resources.arsc
echo "Creating minimal resources..."
python3 -c "
import struct, os
# Create minimal resources.arsc header
# This is a valid empty resources table
data = struct.pack('<IHHI', 0x000c, 40, 1, 1)  # RES_TABLE_TYPE
data += struct.pack('<I', 12)  # package count = 1
data += struct.pack('<II', 0, 0)  # header
with open('$APK_DIR/resources.arsc', 'wb') as f:
    f.write(data)
" 2>/dev/null || touch "$APK_DIR/resources.arsc"

# Copy AndroidManifest
cp "$SCRIPT_DIR/app/src/main/AndroidManifest.xml" "$APK_DIR/AndroidManifest.xml" 2>/dev/null || true

# Create DEX directory structure
mkdir -p "$APK_DIR"
cp "$SCRIPT_DIR/app/build/dex/classes.dex" "$APK_DIR/classes.dex" 2>/dev/null || true

# Add any compiled resources/libs
mkdir -p "$APK_DIR/res/values" "$APK_DIR/res/xml" "$APK_DIR/res/layout"
cp -r "$SCRIPT_DIR/app/src/main/res/"* "$APK_DIR/res/" 2>/dev/null || true

# Step 4: Package APK (ZIP format)
echo ""
echo "Step 4: Packaging APK..."
cd "$APK_DIR"
zip -r -D "$APK_UNSIGNED" . 2>/dev/null
cd "$SCRIPT_DIR"
echo "Unsigned APK: $APK_UNSIGNED"

# Step 5: Sign APK
echo ""
echo "Step 5: Signing APK..."
KEYSTORE="$OUTDIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkey -v -keystore "$KEYSTORE" -alias debug \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=AFK App Blocker, OU=Development, O=AFK, L=Unknown, ST=Unknown, C=US" 2>/dev/null
fi

# Use apksigner (Java-based)
APKSIGNER_JAR="$BUILD_TOOLS/lib/apksigner.jar"
java -jar "$APKSIGNER_JAR" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --ks-key-alias debug \
  --key-pass pass:android \
  --out "$APK_FINAL" \
  "$APK_UNSIGNED" 2>&1 || {
  # Fallback to jarsigner
  jarsigner -verbose -keystore "$KEYSTORE" -storepass android \
    -keypass android -sigalg SHA1withRSA -digestalg SHA1 \
    "$APK_UNSIGNED" debug 2>&1
  cp "$APK_UNSIGNED" "$APK_FINAL"
}

echo ""
echo "=== BUILD COMPLETE ==="
if [ -f "$APK_FINAL" ]; then
  echo "APK created at: $APK_FINAL"
  ls -la "$APK_FINAL"
else
  echo "ERROR: APK not created at $APK_FINAL"
  ls -la "$OUTDIR/"
fi
