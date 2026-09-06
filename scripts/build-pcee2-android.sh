#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PCEE2_DIR="$ROOT_DIR/pcee2-libretro"
OUTPUT_DIR="$ROOT_DIR/build/pcee2/arm64-v8a"
DEPS_DIR="$ROOT_DIR/build/pcee2/deps"
BUILD_DIR="$ROOT_DIR/build/pcee2/cmake"
NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}}"

if [[ ! -f "$PCEE2_DIR/CMakeLists.txt" ]]; then
    echo "PCEE2 submodule is missing. Run: git submodule update --init --recursive"
    exit 1
fi

if [[ -z "$NDK" || ! -f "$NDK/build/cmake/android.toolchain.cmake" ]]; then
    echo "ANDROID_NDK_ROOT, ANDROID_NDK_HOME, or ANDROID_NDK must point to an Android NDK."
    exit 1
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$(dirname "$(dirname "$NDK")")}}"
if [[ -d "$SDK_ROOT/cmake/3.22.1/bin" ]]; then
    export PATH="$SDK_ROOT/cmake/3.22.1/bin:$PATH"
fi

if ! command -v cmake >/dev/null || ! command -v ninja >/dev/null; then
    echo "cmake and ninja are required to build PCEE2."
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

if [[ ! -f "$DEPS_DIR/lib/libshaderc_combined.a" ]]; then
    (
        cd "$PCEE2_DIR"
        ANDROID_NDK="$NDK" ANDROID_ABI=arm64-v8a ANDROID_API=24 \
            bash pcee2-libretro/scripts/build-deps-android.sh "$DEPS_DIR"
    )
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

cmake --build "$BUILD_DIR" --target pcee2_libretro --parallel

CORE="$BUILD_DIR/bin/pcee2_libretro.so"
if [[ ! -s "$CORE" ]]; then
    echo "PCEE2 build completed without producing $CORE"
    exit 1
fi

"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" "$CORE"
cp "$CORE" "$OUTPUT_DIR/libpcee2_libretro_android.so"

if [[ ! -s "$OUTPUT_DIR/libpcee2_libretro_android.so" ]]; then
    echo "PCEE2 staging failed."
    exit 1
fi

"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -h "$OUTPUT_DIR/libpcee2_libretro_android.so" | grep -q "AArch64"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -Ws "$OUTPUT_DIR/libpcee2_libretro_android.so" | grep -q " retro_init"
echo "PCEE2 ARM64 core staged at $OUTPUT_DIR/libpcee2_libretro_android.so"
