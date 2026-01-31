package com.voxnova;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TTSManager {
    private static final String CARTESIA_URL = "https://api.cartesia.ai/tts/bytes";
    private static final String CARTESIA_VOICE_ID = "5c5ad5e7-1020-476b-8b91-fdcbe9cc313c"; // Daniela MX
    private static final String ELEVENLABS_URL = "https://api.elevenlabs.io/v1/text-to-speech/";
    private static final String ELEVENLABS_VOICE_ID = "pFZP5JQG7iQjIQuC4Bku"; // Lily - Spanish

    public interface TTSCallback {
        void onStart();
        void onDone();
        void onError(String error);
    }

    private final Context context;
    private final PreferencesManager prefs;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;
    private TextToSpeech googleTTS;
    private boolean googleReady = false;
    private MediaPlayer mediaPlayer;
    private TTSCallback pendingCallback;

    public TTSManager(Context context, PreferencesManager prefs) {
        this.context = context;
        this.prefs = prefs;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        initGoogleTTS();
    }

    private void initGoogleTTS() {
        try {
            googleTTS = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    String[] langParts = prefs.getLanguageParts();
                    googleTTS.setLanguage(new Locale(langParts[0], langParts[1]));
                    googleTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) { 
                            if (pendingCallback != null) pendingCallback.onStart(); 
                        }
                        @Override public void onDone(String id) { 
                            if (pendingCallback != null) pendingCallback.onDone(); 
                        }
                        @Override public void onError(String id) { 
                            if (pendingCallback != null) pendingCallback.onError("Google TTS error"); 
                        }
                    });
                    googleReady = true;
                    DebugLogger.log("Google TTS ready");
                }
            });
        } catch (Exception e) {
            DebugLogger.error("Google TTS init: " + e.getMessage());
        }
    }

    public static String stripEmojis(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\p{So}\\p{Cs}]", "").trim();
    }

    public void speak(String text, TTSCallback callback) {
        this.pendingCallback = callback;
        String cleanText = stripEmojis(text);
        DebugLogger.log("TTS speak: " + cleanText.substring(0, Math.min(40, cleanText.length())) + "...");

        String cartesiaKey = prefs.getCartesiaApiKey();
        String elevenLabsKey = prefs.getElevenLabsApiKey();

        if (cartesiaKey != null && !cartesiaKey.isEmpty()) {
            DebugLogger.log("Trying Cartesia...");
            speakWithCartesia(cleanText, cartesiaKey, callback);
        } else if (elevenLabsKey != null && !elevenLabsKey.isEmpty()) {
            DebugLogger.log("Trying ElevenLabs...");
            speakWithElevenLabs(cleanText, elevenLabsKey, callback);
        } else {
            DebugLogger.log("Using Google TTS (no API keys)");
            speakWithGoogle(cleanText, callback);
        }
    }

    private void speakWithCartesia(String text, String apiKey, TTSCallback callback) {
        new Thread(() -> {
            try {
                DebugLogger.log("Cartesia: building request...");
                
                JSONObject body = new JSONObject();
                body.put("model_id", "sonic-2");
                body.put("transcript", text);
                body.put("language", prefs.getTtsLanguageCode());
                
                JSONObject voice = new JSONObject();
                voice.put("mode", "id");
                voice.put("id", CARTESIA_VOICE_ID);
                body.put("voice", voice);
                
                JSONObject format = new JSONObject();
                format.put("container", "mp3");
                format.put("bit_rate", 128000);
                format.put("sample_rate", 44100);
                body.put("output_format", format);

                DebugLogger.log("Cartesia: sending request...");
                
                Request request = new Request.Builder()
                        .url(CARTESIA_URL)
                        .addHeader("X-API-Key", apiKey)
                        .addHeader("Cartesia-Version", "2024-06-10")
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .build();

                Response response = httpClient.newCall(request).execute();
                
                DebugLogger.log("Cartesia response: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    DebugLogger.log("Cartesia: got audio, playing...");
                    playAudioStream(response.body().byteStream(), callback);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    DebugLogger.error("Cartesia failed " + response.code() + ": " + errorBody.substring(0, Math.min(100, errorBody.length())));
                    // Fallback to ElevenLabs, then Google
                    mainHandler.post(() -> {
                        String elevenLabsKey = prefs.getElevenLabsApiKey();
                        if (elevenLabsKey != null && !elevenLabsKey.isEmpty()) {
                            speakWithElevenLabs(text, elevenLabsKey, callback);
                        } else {
                            speakWithGoogle(text, callback);
                        }
                    });
                }
            } catch (Exception e) {
                DebugLogger.error("Cartesia exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                // Fallback to ElevenLabs, then Google
                mainHandler.post(() -> {
                    String elevenLabsKey = prefs.getElevenLabsApiKey();
                    if (elevenLabsKey != null && !elevenLabsKey.isEmpty()) {
                        speakWithElevenLabs(text, elevenLabsKey, callback);
                    } else {
                        speakWithGoogle(text, callback);
                    }
                });
            }
        }).start();
    }

    private void speakWithElevenLabs(String text, String apiKey, TTSCallback callback) {
        new Thread(() -> {
            try {
                DebugLogger.log("ElevenLabs: building request...");

                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("model_id", "eleven_multilingual_v2");

                JSONObject voiceSettings = new JSONObject();
                voiceSettings.put("stability", 0.5);
                voiceSettings.put("similarity_boost", 0.75);
                body.put("voice_settings", voiceSettings);

                DebugLogger.log("ElevenLabs: sending request...");

                Request request = new Request.Builder()
                        .url(ELEVENLABS_URL + ELEVENLABS_VOICE_ID)
                        .addHeader("xi-api-key", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "audio/mpeg")
                        .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .build();

                Response response = httpClient.newCall(request).execute();

                DebugLogger.log("ElevenLabs response: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    DebugLogger.log("ElevenLabs: got audio, playing...");
                    playAudioStream(response.body().byteStream(), callback);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    DebugLogger.error("ElevenLabs failed " + response.code() + ": " + errorBody.substring(0, Math.min(100, errorBody.length())));
                    mainHandler.post(() -> speakWithGoogle(text, callback));
                }
            } catch (Exception e) {
                DebugLogger.error("ElevenLabs exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                mainHandler.post(() -> speakWithGoogle(text, callback));
            }
        }).start();
    }

    private void playAudioStream(InputStream audioStream, TTSCallback callback) {
        try {
            DebugLogger.log("Saving audio to temp file...");
            File tempFile = File.createTempFile("tts_", ".mp3", context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int read;
            int total = 0;
            while ((read = audioStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                total += read;
            }
            fos.close();
            audioStream.close();
            DebugLogger.log("Audio saved: " + total + " bytes");

            mainHandler.post(() -> {
                try {
                    DebugLogger.log("Playing audio...");
                    if (mediaPlayer != null) {
                        mediaPlayer.release();
                    }
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build());
                    mediaPlayer.setDataSource(tempFile.getAbsolutePath());
                    mediaPlayer.setOnPreparedListener(mp -> {
                        DebugLogger.log("MediaPlayer prepared, starting...");
                        callback.onStart();
                        mp.start();
                    });
                    mediaPlayer.setOnCompletionListener(mp -> {
                        DebugLogger.log("Playback complete");
                        callback.onDone();
                        tempFile.delete();
                    });
                    mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        DebugLogger.error("MediaPlayer error: " + what + "/" + extra);
                        callback.onError("Playback error");
                        tempFile.delete();
                        return true;
                    });
                    mediaPlayer.prepareAsync();
                } catch (Exception e) {
                    DebugLogger.error("MediaPlayer setup: " + e.getMessage());
                    callback.onError(e.getMessage());
                    tempFile.delete();
                }
            });
        } catch (Exception e) {
            DebugLogger.error("playAudioStream: " + e.getMessage());
            callback.onError(e.getMessage());
        }
    }

    private void speakWithGoogle(String text, TTSCallback callback) {
        DebugLogger.log("Google TTS speaking...");
        mainHandler.post(() -> {
            try {
                if (!googleReady || googleTTS == null) {
                    DebugLogger.error("Google TTS not ready");
                    callback.onError("TTS not ready");
                    return;
                }
                googleTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voxnova");
            } catch (Exception e) {
                DebugLogger.error("Google TTS speak: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        });
    }

    public void stop() {
        try { if (mediaPlayer != null) mediaPlayer.stop(); } catch (Exception e) {}
        try { if (googleTTS != null) googleTTS.stop(); } catch (Exception e) {}
    }

    public void shutdown() {
        stop();
        try { if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; } } catch (Exception e) {}
        try { if (googleTTS != null) { googleTTS.shutdown(); googleTTS = null; } } catch (Exception e) {}
    }
}
