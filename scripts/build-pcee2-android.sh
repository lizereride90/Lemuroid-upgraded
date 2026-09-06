#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PCEE2_DIR="$ROOT_DIR/pcee2-libretro"
OUTPUT_DIR="$ROOT_DIR/build/pcee2/arm64-v8a"
DEPS_DIR="$ROOT_DIR/build/pcee2/deps"
BUILD_DIR="$ROOT_DIR/build/pcee2/cmake"
NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}}"

# Building PCEE2 on CI runners with unlimited parallelism is a frequent source
# of OOM kills (ninja exit 74). Default to 2 jobs unless JOBS is set.
JOBS="${JOBS:-2}"

if [[ ! -f "$PCEE2_DIR/CMakeLists.txt" ]]; then
    echo "PCEE2 submodule is missing. Run: git submodule update --init --recursive"
    exit 1
fi

if [[ -z "$NDK" || ! -f "$NDK/build/cmake/android.toolchain.cmake" ]]; then
    echo "ANDROID_NDK_ROOT, ANDROID_NDK_HOME, or ANDROID_NDK must point to an Android NDK."
    exit 1
fi

# Upstream CI builds this recipe with the system cmake from apt; do not try to
# swap in the older SDK-bundled cmake 3.22.1, which is below the toolchain's
# expectations for this codebase.
if ! command -v cmake >/dev/null || ! command -v ninja >/dev/null; then
    echo "cmake and ninja are required to build PCEE2."
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

export ANDROID_NDK_HOME="$NDK"
export NINJA_JOBS="$JOBS"

# The dependency recipe computes parallelism from `getconf _NPROCESSORS_ONLN`,
# which on a CI runner reports every vCPU and causes OOM (clang exit 74) while
# compiling shaderc. Shadow getconf with a shim that caps it at JOBS so the
# recipe stays untouched. NINJA_JOBS is exported as belt-and-braces for any
# recipe that honors it.
SHIM_DIR="$(mktemp -d)"
trap 'rm -rf "$SHIM_DIR"' EXIT
REAL_GETCONF="$(command -v getconf)"
cat > "$SHIM_DIR/getconf" <<SHIM
#!/usr/bin/env bash
if [ "\$1" = "_NPROCESSORS_ONLN" ]; then
    echo "\${BUILD_JOBS:-2}"
    exit 0
fi
exec "$REAL_GETCONF" "\$@"
SHIM
chmod +x "$SHIM_DIR/getconf"

if [[ ! -f "$DEPS_DIR/lib/libshaderc_combined.a" ]]; then
    (
        cd "$PCEE2_DIR"
        PATH="$SHIM_DIR:$PATH" BUILD_JOBS="$JOBS" NINJA_JOBS="$JOBS" \
            ANDROID_NDK="$NDK" ANDROID_ABI=arm64-v8a ANDROID_API=24 \
            bash pcee2-libretro/scripts/build-deps-android.sh "$DEPS_DIR"
    )
    if [[ ! -f "$DEPS_DIR/lib/libshaderc_combined.a" ]]; then
        echo "PCEE2 dependencies build failed: libshaderc_combined.a was not produced."
        exit 1
    fi
fi

cmake -S "$PCEE2_DIR" -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_QT_UI=OFF \
    -DENABLE_TESTS=OFF \
    -DENABLE_LIBRETRO=ON \
    -DCMAKE_PREFIX_PATH="$DEPS_DIR" \
    -DCMAKE_FIND_ROOT_PATH="$DEPS_DIR" \
    -DSHADERC_STATIC=ON \
    "-DSHADERC_LIBRARY=$DEPS_DIR/lib/libshaderc_combined.a" \
    -DDISABLE_ADVANCE_SIMD=ON

cmake --build "$BUILD_DIR" --target pcee2_libretro --parallel "$JOBS"

CORE="$BUILD_DIR/bin/pcee2_libretro.so"
if [[ ! -s "$CORE" ]]; then
    echo "PCEE2 build completed without producing $CORE"
    exit 1
fi

echo "Built core size before stripping:"
ls -lh "$CORE"
df -h "$ROOT_DIR" || true

# Strip debug info only: a full strip would also remove the symbol table
# that the retro_init sanity check below relies on.
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-debug "$CORE"
cp "$CORE" "$OUTPUT_DIR/libpcee2_libretro_android.so"

if [[ ! -s "$OUTPUT_DIR/libpcee2_libretro_android.so" ]]; then
    echo "PCEE2 staging failed."
    exit 1
fi

echo "Staged core:"
ls -lh "$OUTPUT_DIR/libpcee2_libretro_android.so"
READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
HEADER_FILE="$(mktemp)"
SYMBOLS_FILE="$(mktemp)"
trap 'rm -rf "$SHIM_DIR" "$HEADER_FILE" "$SYMBOLS_FILE"' EXIT

"$READELF" -h "$OUTPUT_DIR/libpcee2_libretro_android.so" > "$HEADER_FILE"
"$READELF" -Ws "$OUTPUT_DIR/libpcee2_libretro_android.so" > "$SYMBOLS_FILE"

grep -q "AArch64" "$HEADER_FILE"
grep -q " retro_init" "$SYMBOLS_FILE"
echo "PCEE2 ARM64 core staged at $OUTPUT_DIR/libpcee2_libretro_android.so"
