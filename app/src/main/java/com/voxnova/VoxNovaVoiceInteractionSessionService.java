package com.voxnova;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;

/**
 * Service that creates voice interaction sessions.
 */
public class VoxNovaVoiceInteractionSessionService extends VoiceInteractionSessionService {
    private static final String TAG = "VoxNovaVISS";

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        Log.d(TAG, "Creating new VoiceInteractionSession");
        return new VoxNovaVoiceInteractionSession(this);
    }
}
