package com.voxnova;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {
    private static final String PREFS_NAME = "voxnova_prefs";
    private static final String KEY_GATEWAY_URL = "gateway_url";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_CARTESIA_API_KEY = "cartesia_api_key";
    private static final String KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_SILENCE_TIMEOUT = "silence_timeout";
    private static final String KEY_TTS_PROVIDER = "tts_provider";

    public static final String DEFAULT_LANGUAGE = "es-MX";
    public static final String TTS_PROVIDER_AUTO = "auto";
    public static final String TTS_PROVIDER_CARTESIA = "cartesia";
    public static final String TTS_PROVIDER_ELEVENLABS = "elevenlabs";
    public static final String TTS_PROVIDER_GOOGLE = "google";
    public static final int DEFAULT_SILENCE_TIMEOUT = 2000; // 2 seconds

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getGatewayUrl() {
        return prefs.getString(KEY_GATEWAY_URL, "");
    }

    public void setGatewayUrl(String url) {
        prefs.edit().putString(KEY_GATEWAY_URL, url).apply();
    }

    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, "");
    }

    public void setAuthToken(String token) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
    }

    public String getCartesiaApiKey() {
        return prefs.getString(KEY_CARTESIA_API_KEY, "");
    }

    public void setCartesiaApiKey(String key) {
        prefs.edit().putString(KEY_CARTESIA_API_KEY, key).apply();
    }

    public String getElevenLabsApiKey() {
        return prefs.getString(KEY_ELEVENLABS_API_KEY, "");
    }

    public void setElevenLabsApiKey(String key) {
        prefs.edit().putString(KEY_ELEVENLABS_API_KEY, key).apply();
    }

    public boolean isConfigured() {
        return !getGatewayUrl().isEmpty() && !getAuthToken().isEmpty();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public int getSilenceTimeout() {
        return prefs.getInt(KEY_SILENCE_TIMEOUT, DEFAULT_SILENCE_TIMEOUT);
    }

    public void setSilenceTimeout(int timeout) {
        prefs.edit().putInt(KEY_SILENCE_TIMEOUT, timeout).apply();
    }

    /**
     * Returns the language code for TTS (e.g., "es" from "es-MX")
     */
    public String getTtsLanguageCode() {
        String lang = getLanguage();
        if (lang.contains("-")) {
            return lang.split("-")[0];
        }
        return lang;
    }

    /**
     * Returns language and country as separate values for Locale
     */
    public String[] getLanguageParts() {
        String lang = getLanguage();
        if (lang.contains("-")) {
            return lang.split("-");
        }
        return new String[]{lang, ""};
    }

    public String getTtsProvider() {
        return prefs.getString(KEY_TTS_PROVIDER, TTS_PROVIDER_AUTO);
    }

    public void setTtsProvider(String provider) {
        prefs.edit().putString(KEY_TTS_PROVIDER, provider).apply();
    }
}
