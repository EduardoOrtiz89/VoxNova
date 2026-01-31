#!/bin/bash
# VoxNova Build Script

set -e

export ANDROID_HOME="${ANDROID_HOME:-/home/eduardo/android-sdk}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "=== VoxNova Build Script ==="
echo "ANDROID_HOME: $ANDROID_HOME"

# Check for gradle wrapper jar
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper
    
    # Try multiple sources
    curl -sL -o gradle/wrapper/gradle-wrapper.jar \
        "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || \
    curl -sL -o gradle/wrapper/gradle-wrapper.jar \
        "https://github.com/nicoulaj/gradle-wrapper-binaries/raw/gradle-8.2/gradle-wrapper.jar" 2>/dev/null || \
    {
        echo "Could not download wrapper. Trying gradle init..."
        if command -v gradle &> /dev/null; then
            gradle wrapper --gradle-version 8.2
        else
            echo "Please install gradle or download gradle-wrapper.jar manually"
            exit 1
        fi
    }
fi

# Build
echo "Building APK..."
./gradlew assembleDebug --stacktrace

echo ""
echo "=== Build Complete ==="
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
