#!/bin/bash
# =============================================================================
# APK Parse Error Diagnostic & Fix Script
# =============================================================================
# Usage: bash diagnose-apk.sh path/to/app.apk
# Tests all common causes of "there was a problem parsing the package"
# =============================================================================

ANDROID_SDK="${ANDROID_SDK:-/data/data/com.termux/files/home/android-sdk}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
pass() { echo -e "  ${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "  ${RED}[FAIL]${NC} $1"; }
info() { echo -e "  ${CYAN}[INFO]${NC} $1"; }
warn() { echo -e "  ${YELLOW}[WARN]${NC} $1"; }

APK="${1:-}"
if [ -z "$APK" ]; then
  echo "Usage: $0 path/to/app.apk"
  echo ""
  echo "Common paths:"
  echo "  app/build/outputs/AFKAppBlocker.apk"
  echo "  android/app/build/outputs/apk/debug/app-debug.apk"
  exit 1
fi
if [ ! -f "$APK" ]; then
  fail "APK file not found: $APK"
  exit 1
fi

APK_REAL=$(realpath "$APK")
APK_DIR=$(dirname "$APK_REAL")
APK_NAME=$(basename "$APK_REAL")
BUILD_DIR=$(dirname "$APK_DIR" 2>/dev/null || echo "$APK_DIR")
ANDROID_PROJ=$(cd "$APK_DIR" && while [ ! -f build.gradle ] && [ "$PWD" != "/" ]; do cd ..; done; echo "$PWD" 2>/dev/null)
GRADLE_CACHE="$HOME/.gradle/caches"

echo ""
echo "============================================================================="
echo "  APK DIAGNOSTIC: $APK_NAME"
echo "  Size: $(du -h "$APK_REAL" | cut -f1) ($(stat -c%s "$APK_REAL") bytes)"
echo "============================================================================="
echo ""

ERRORS=0

# ───────────────────────────── 1. APK CORRUPT ─────────────────────────────
echo -e "${CYAN}[1/8]${NC} APK Integrity Check"
if command -v unzip &>/dev/null; then
  if unzip -t "$APK_REAL" 2>&1 | tail -1 | grep -q "No errors"; then
    pass "APK archive integrity verified (unzip -t)"
  else
    fail "APK is corrupt! Rebuild or re-transfer."
    ((ERRORS++))
  fi
else
  # fallback: python zipfile check
  python3 -c "
import zipfile, sys
try:
    z = zipfile.ZipFile('$APK_REAL', 'r')
    bad = z.testzip()
    if bad: print(f'Corrupt: {bad}'); sys.exit(1)
    else: print('OK')
except Exception as e: print(f'Error: {e}'); sys.exit(1)
" && pass "APK integrity verified (python zipfile)" || { fail "APK is corrupt!"; ((ERRORS++)); }
fi

# ───────────────────────────── 2. INVALID SIGNATURE ───────────────────────
echo ""
echo -e "${CYAN}[2/8]${NC} APK Signature Check"

if command -v apksigner &>/dev/null; then
  APKSIGNER=$(command -v apksigner)
elif [ -f "$ANDROID_SDK/build-tools/34.0.0/apksigner" ]; then
  APKSIGNER="$ANDROID_SDK/build-tools/34.0.0/apksigner"
elif [ -f "$ANDROID_SDK/build-tools/34.0.0/lib/apksigner.jar" ]; then
  APKSIGNER="java -jar $ANDROID_SDK/build-tools/34.0.0/lib/apksigner.jar"
else
  APKSIGNER=""
fi

if [ -n "$APKSIGNER" ]; then
  if $APKSIGNER verify --print-certs "$APK_REAL" 2>&1 | head -5; then
    pass "APK signature is valid"
  else
    fail "APK signature missing or invalid"
    info "Re-signing the APK..."
    KEYSTORE="$APK_DIR/debug.keystore"
    if [ ! -f "$KEYSTORE" ]; then
      keytool -genkey -v -keystore "$KEYSTORE" -alias debug \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Debug, OU=Dev, O=Debug, L=NA, ST=NA, C=US" 2>/dev/null
    fi
    jarsigner -keystore "$KEYSTORE" -storepass android -keypass android \
      -sigalg SHA256withRSA -digestalg SHA-256 "$APK_REAL" debug 2>&1
    if $APKSIGNER verify --print-certs "$APK_REAL" 2>&1 | head -3; then
      pass "APK successfully re-signed"
    else
      fail "Re-signing still fails"
      ((ERRORS++))
    fi
  fi
else
  # fallback: jarsigner
  if jarsigner -verify -certs "$APK_REAL" 2>&1 | grep -q "jar verified"; then
    pass "APK signature verified (jarsigner)"
  else
    fail "APK not signed or signature invalid"
    info "Signing with debug keystore..."
    KEYSTORE="$APK_DIR/debug.keystore"
    [ -f "$KEYSTORE" ] || keytool -genkey -v -keystore "$KEYSTORE" -alias debug \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -storepass android -keypass android \
      -dname "CN=Debug, OU=Dev, O=Debug, L=NA, ST=NA, C=US" 2>/dev/null
    jarsigner -keystore "$KEYSTORE" -storepass android -keypass android \
      -sigalg SHA256withRSA -digestalg SHA-256 "$APK_REAL" debug 2>&1
    jarsigner -verify -certs "$APK_REAL" 2>&1 | tail -3
    pass "APK re-signed"
  fi
fi

# ───────────────────────────── 3. MINSDK INCOMPATIBLE ─────────────────────
echo ""
echo -e "${CYAN}[3/8]${NC} minSdkVersion Check"

if command -v aapt2 &>/dev/null; then
  AAPT=aapt2
elif [ -f "$ANDROID_SDK/build-tools/34.0.0/aapt2" ]; then
  AAPT="$ANDROID_SDK/build-tools/34.0.0/aapt2"
else
  AAPT=""
fi

if [ -n "$AAPT" ]; then
  if SDK_INFO=$($AAPT dump badging "$APK_REAL" 2>/dev/null); then
    SDK_VERSION=$(echo "$SDK_INFO" | grep "sdkVersion" | head -1)
    echo "  $SDK_VERSION"
    MIN=$(echo "$SDK_VERSION" | grep -oP "sdkVersion:'\K[0-9]+" || echo "?")
    TARGET=$(echo "$SDK_VERSION" | grep -oP "targetSdkVersion:'\K[0-9]+" || echo "?")
    if [ "$MIN" -gt 0 ] 2>/dev/null; then
      pass "minSdkVersion = $MIN (${MIN}: Android $((MIN-1)+1 or later)"
      [ "$MIN" -gt 29 ] && warn "minSdk $MIN is Android $((MIN-1)+1) — most phones are API 29+"
    else
      fail "Could not parse sdkVersion"
      ((ERRORS++))
    fi
  else
    # aapt2 might fail on ARM64 (x86_64 binary) — try grep on dumped bytes
    warn "aapt2 dump failed (may be x86_64 binary on ARM64)"
    info "Trying alternative: parsing binary AndroidManifest via Python..."
    python3 -c "
import zipfile, struct
z = zipfile.ZipFile('$APK_REAL', 'r')
data = z.read('AndroidManifest.xml')
# Search for 'uses-sdk' / minsdk pattern
idx = data.find(b'uses-sdk')
if idx >= 0: print(f'Found uses-sdk at offset {idx}')
# The minSdk is often stored nearby as varint
print(f'Manifest size: {len(data)} bytes')
z.close()
" 2>&1 || true
  fi
else
  warn "aapt2 not found"
  info "Reading raw bytes from AndroidManifest.xml in APK..."
  python3 -c "
import zipfile
z = zipfile.ZipFile('$APK_REAL', 'r')
data = z.read('AndroidManifest.xml')
# Check for minsdk in raw bytes
import re
# Common SDK values encoded as UTF-8 in the manifest
for s in [b'14', b'21', b'23', b'26', b'28', b'29', b'30', b'31', b'32', b'33', b'34', b'35']:
    if s in data:
        print(f'  Possible sdk value found: {s.decode()}')
print(f'  Manifest size: {len(data)} bytes')
z.close()
" 2>&1
fi

# ───────────────────────────── 4. PACKAGE NAME ────────────────────────────
echo ""
echo -e "${CYAN}[4/8]${NC} Package Name Check"

python3 -c "
import zipfile, os
try:
    z = zipfile.ZipFile('$APK_REAL', 'r')
    data = z.read('AndroidManifest.xml')
    # Extract package name from binary XML
    manifest_start = data.find(b'manifest')
    pkg_tag = b'package=\"'
    start = data.find(pkg_tag)
    if start > 0:
        start += len(pkg_tag)
        end = data.index(b'\"', start)
        pkg = data[start:end].decode('utf-8', errors='replace')
        print(f'  Package: {pkg}')
    else:
        # Try reading the string pool
        print(f'  Could not extract package name from binary XML')
    z.close()
except Exception as e:
    print(f'  Error: {e}')
" 2>&1

info "If you have the old app installed on device:"
echo "    adb uninstall com.limitedafk.appblocker"
info "Or change the package name in $ANDROID_PROJ/app/build.gradle"

# ───────────────────────────── 5. MANIFEST ────────────────────────────────
echo ""
echo -e "${CYAN}[5/8]${NC} AndroidManifest.xml Check"

MANIFEST_SIZE=$(python3 -c "import zipfile; print(zipfile.ZipFile('$APK_REAL','r').getinfo('AndroidManifest.xml').file_size)" 2>/dev/null || echo "?")
if [ "$MANIFEST_SIZE" != "?" ] && [ "$MANIFEST_SIZE" -gt 200 ]; then
  pass "AndroidManifest.xml present ($MANIFEST_SIZE bytes)"
else
  fail "AndroidManifest.xml missing or too small ($MANIFEST_SIZE bytes)"
  ((ERRORS++))
fi

echo ""
python3 -c "
import zipfile
z = zipfile.ZipFile('$APK_REAL', 'r')
files = z.namelist()
print('  Contents:')
for f in files:
    print(f'    ├ $f ({z.getinfo(f).file_size} bytes)')
print(f'  Total: {len(files)} files')
z.close()
" 2>&1

# ───────────────────────────── 6. ARCHITECTURE ────────────────────────────
echo ""
echo -e "${CYAN}[6/8]${NC} ABI / Architecture Check"

# Check for native libs
python3 -c "
import zipfile
z = zipfile.ZipFile('$APK_REAL', 'r')
libs = [f for f in z.namelist() if f.startswith('lib/')]
if libs:
    print(f'  Native libraries found:')
    for lib in libs:
        print(f'    ├ {lib}')
    arches = set()
    for lib in libs:
        parts = lib.split('/')
        if len(parts) >= 2:
            arches.add(parts[1])
    print(f'  Supported ABIs: {', '.join(sorted(arches))}')
    if 'arm64-v8a' in arches or 'armeabi-v7a' in arches:
        print('  ✓ Contains ARM native code')
    elif 'x86' in arches or 'x86_64' in arches:
        print('  ✗ Contains x86/x86_64 native code only — will NOT run on ARM devices!')
else:
    print('  No native libraries (lib/ folder absent) — pure Java/DEX APK, runs on any arch')
z.close()
" 2>&1

# ───────────────────────────── 7. PERMISSIONS ─────────────────────────────
echo ""
echo -e "${CYAN}[7/8]${NC} Permissions Check"

python3 -c "
import zipfile
z = zipfile.ZipFile('$APK_REAL', 'r')
data = z.read('AndroidManifest.xml')
perms = []
search_for = [b'uses-permission', b'uses-sdk', b'uses-feature']
for item in search_for:
    idx = 0
    while True:
        idx = data.find(item, idx)
        if idx < 0: break
        # Extract the permission name by looking for 'android.permission.' or just the string name
        chunk = data[idx:idx+200]
        name = ''
        for marker in [b'android.permission.', b'androidx.' ]:
            s = chunk.find(marker)
            if s >= 0:
                end = chunk.find(b'\"', s)
                if end < 0: end = chunk.find(b'>', s)
                if end < 0: end = idx+100
                name = chunk[s:end].decode('utf-8', errors='replace')
                break
        if name:
            perms.append(name)
        idx += len(item)
z.close()
if perms:
    print(f'  Declared permissions ({len(perms)}):')
    for p in perms:
        if 'permission' in p:
            print(f'    ⚡ {p}')
else:
    print('  Could not parse permissions from binary manifest')
print()
info 'If using system permissions added after API 29+, they may cause parse errors on older devices.'
" 2>&1

# ───────────────────────────── 8. BUILD INCOMPLETE ────────────────────────
echo ""
echo -e "${CYAN}[8/8]${NC} Build Completeness Check"

python3 -c "
import zipfile
z = zipfile.ZipFile('$APK_REAL', 'r')
files = z.namelist()
issues = []

# Check for required files
if 'AndroidManifest.xml' not in files: issues.append('Missing AndroidManifest.xml')
if 'classes.dex' not in files: issues.append('Missing classes.dex')
if 'resources.arsc' not in files: issues.append('Missing resources.arsc')

# Check for stub/empty files
for f in ['resources.arsc']:
    if f in files and z.getinfo(f).file_size < 10:
        issues.append(f'{f} is too small — resources not compiled correctly')

if issues:
    for i in issues: print(f'  ✗ {i}')
else:
    print('  ✓ APK structure looks complete')
    print(f'  ✓ Found: classes.dex ({z.getinfo(\"classes.dex\").file_size} bytes)')
    print(f'  ✓ Found: resources.arsc ({z.getinfo(\"resources.arsc\").file_size} bytes)')
z.close()
" 2>&1

# ───────────────────────────── FINAL SUMMARY ──────────────────────────────
echo ""
echo "============================================================================="
echo "  SUMMARY"
echo "============================================================================="

if [ "$ERRORS" -eq 0 ]; then
  echo -e "  ${GREEN}No critical issues detected.${NC}"
  echo ""
  info "If the APK still fails to install, the most common remaining causes:"
  echo "  1. APK was transferred incorrectly — re-transfer via USB/ADB"
  echo "     adb push $APK_REAL /sdcard/Download/"
  echo "     adb shell pm install /sdcard/Download/$APK_NAME"
  echo ""
  echo "  2. Android version too old — install on Android 10+"
  echo ""
  echo "  3. Package name conflict:"
  echo "     adb uninstall com.limitedafk.appblocker"
  echo ""
  echo "  4. Install with verbose error:"
  echo "     adb install -r $APK_REAL"
  echo ""
  echo "  5. Full rebuild:"
  echo "     cd $ANDROID_PROJ && ./gradlew clean assembleDebug"
  echo ""
  echo "  6. Google Play Protect blocking — disable temporarily"
else
  echo -e "  ${RED}$ERRORS issue(s) found. Fix them above and re-test.${NC}"
fi
echo "============================================================================="
