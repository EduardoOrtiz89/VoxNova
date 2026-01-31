# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

VoxNova is an Android voice assistant app that integrates with Clawdbot (an AI gateway service). It's a native Android app targeting SDK 26-34, written in Java 17.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean and rebuild
./gradlew clean assembleDebug

# Install to connected device
./gradlew installDebug

# Alternative: use custom build script (sets up environment automatically)
./build.sh
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

### Core Flow
```
Voice Input → VoxNovaVoiceInteractionSession (Android STT)
    → ClawdbotClient (WebSocket) → Clawdbot Gateway
    → Response aggregation → TTSManager → Audio Output
```

### Key Components

| Class | Purpose |
|-------|---------|
| `ClawdbotClient` | WebSocket client for Clawdbot gateway (protocol v3). Handles connection, chat messages, streaming response aggregation |
| `VoxNovaVoiceInteractionSession` | Main voice interaction logic. Manages STT, UI overlay, and TTS coordination |
| `TTSManager` | Multi-provider TTS with fallback chain: Cartesia → ElevenLabs → Google TTS |
| `SettingsActivity` | Configuration UI for gateway URL, auth tokens, API keys, and debugging |
| `PreferencesManager` | SharedPreferences wrapper for app configuration |
| `DebugLogger` | In-memory logging (max 100 entries) with UI listener support |

### WebSocket Protocol (ClawdbotClient)
- Protocol version: 3
- Message types: `req`, `res`, `event`
- Streaming handled via `agent` events with `lifecycle.phase` tracking
- Uses idempotency keys for message deduplication

### TTS Provider Priority
1. **Cartesia AI** - Uses voice "Daniela MX" / `sonic-2` model (requires API key)
2. **Google TTS** - Default fallback (always available)

### Quick Commands (QuickCommand.java)
Predefined command buttons shown in the UI overlay. Includes gateway built-in commands (`/status`, `/help`) and Clawdbot skill invocations (`/skill morning-briefing`).

## Configuration

Runtime settings stored in SharedPreferences:
- `gateway_url` - Clawdbot WebSocket URL (e.g., `ws://192.168.1.100:18789`)
- `auth_token` - Clawdbot authentication token
- `cartesia_api_key` - Optional Cartesia API key for TTS

## Language & Localization

- UI and speech recognition: Spanish (Mexico) - `es-MX`
- Status indicators use emoji for visual feedback

## Android Setup

The app registers as a system voice assistant. Users configure it at:
Settings → Apps → Default apps → Digital assistant app

Required permissions: INTERNET, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE

## Development Notes

- Cleartext HTTP traffic is enabled (`network_security_config.xml`) for local development
- View Binding is enabled in the build configuration
- No test suite currently exists
