package com.voxnova;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {
    private static final String PREFS_NAME = "voxnova_prefs";
    private static final String KEY_GATEWAY_URL = "gateway_url";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_CARTESIA_API_KEY = "cartesia_api_key";
    private static final String KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key";
    
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
}
