package com.voxnova;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

public class VoxNovaVoiceInteractionService extends VoiceInteractionService {
    private static final String TAG = "VoxNovaVIS";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VoiceInteractionService created");
        DebugLogger.log("=== VoiceInteractionService CREATED ===");
        DebugLogger.initRemote(this);
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.d(TAG, "VoiceInteractionService ready");
        DebugLogger.log("VoiceInteractionService READY");
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        Log.d(TAG, "VoiceInteractionService shutdown");
        DebugLogger.log("VoiceInteractionService SHUTDOWN");
        DebugLogger.closeRemote();
    }
}
