#!/bin/sh
# Gradle wrapper
# Auto-downloads wrapper jar if missing

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

# Determine Java command
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Download wrapper if missing
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Gradle wrapper JAR not found. Downloading..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    
    # Try curl first
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "$WRAPPER_JAR" \
            "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" || \
        curl -fsSL -o "$WRAPPER_JAR" \
            "https://github.com/nicoulaj/gradle-wrapper-binaries/raw/gradle-8.2/gradle-wrapper.jar"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$WRAPPER_JAR" \
            "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" || \
        wget -q -O "$WRAPPER_JAR" \
            "https://github.com/nicoulaj/gradle-wrapper-binaries/raw/gradle-8.2/gradle-wrapper.jar"
    fi
    
    if [ ! -f "$WRAPPER_JAR" ] || [ ! -s "$WRAPPER_JAR" ]; then
        echo "Failed to download gradle-wrapper.jar"
        echo "Please download it manually from:"
        echo "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
        exit 1
    fi
    
    echo "Downloaded gradle-wrapper.jar"
fi

# Run gradle
exec "$JAVACMD" \
    -Xmx64m \
    -Dorg.gradle.appname=gradlew \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
