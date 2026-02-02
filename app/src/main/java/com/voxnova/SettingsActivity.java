package com.voxnova;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {
    private TextInputEditText editGatewayUrl, editAuthToken, editCartesiaKey, editElevenLabsKey;
    private TextView txtStatus, txtSilenceValue, txtTtsProviderWarning;
    private Spinner spinnerLanguage, spinnerTtsProvider;
    private Slider sliderSilenceTimeout;
    private PreferencesManager prefs;

    private static final String[] LANGUAGE_CODES = {"es-MX", "es-ES", "en-US", "en-GB", "pt-BR", "fr-FR", "de-DE", "it-IT"};
    private static final String[] LANGUAGE_NAMES = {"Spanish (Mexico)", "Spanish (Spain)", "English (US)", "English (UK)", "Portuguese (Brazil)", "French", "German", "Italian"};

    private static final String[] TTS_PROVIDER_CODES = {
            PreferencesManager.TTS_PROVIDER_AUTO,
            PreferencesManager.TTS_PROVIDER_CARTESIA,
            PreferencesManager.TTS_PROVIDER_ELEVENLABS,
            PreferencesManager.TTS_PROVIDER_GOOGLE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        prefs = new PreferencesManager(this);
        initViews();
        loadSettings();
        checkPermissions();
        updateStatus();
    }

    private void initViews() {
        editGatewayUrl = findViewById(R.id.editGatewayUrl);
        editAuthToken = findViewById(R.id.editAuthToken);
        editCartesiaKey = findViewById(R.id.editCartesiaKey);
        editElevenLabsKey = findViewById(R.id.editElevenLabsKey);
        txtStatus = findViewById(R.id.txtStatus);
        txtSilenceValue = findViewById(R.id.txtSilenceValue);
        txtTtsProviderWarning = findViewById(R.id.txtTtsProviderWarning);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        spinnerTtsProvider = findViewById(R.id.spinnerTtsProvider);
        sliderSilenceTimeout = findViewById(R.id.sliderSilenceTimeout);

        // Setup language spinner
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, LANGUAGE_NAMES);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(langAdapter);

        // Setup TTS provider spinner
        String[] ttsProviderNames = {
                getString(R.string.tts_provider_auto),
                getString(R.string.tts_provider_cartesia),
                getString(R.string.tts_provider_elevenlabs),
                getString(R.string.tts_provider_google)
        };
        ArrayAdapter<String> ttsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ttsProviderNames);
        ttsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTtsProvider.setAdapter(ttsAdapter);
        spinnerTtsProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                updateTtsProviderWarning();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Setup silence timeout slider
        sliderSilenceTimeout.addOnChangeListener((slider, value, fromUser) -> {
            txtSilenceValue.setText((int) value + "s");
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btnTestConnection).setOnClickListener(v -> testConnection());
        findViewById(R.id.btnOpenAssistantSettings).setOnClickListener(v -> openAssistantSettings());
    }

    private void loadSettings() {
        editGatewayUrl.setText(prefs.getGatewayUrl());
        editAuthToken.setText(prefs.getAuthToken());
        editCartesiaKey.setText(prefs.getCartesiaApiKey());
        editElevenLabsKey.setText(prefs.getElevenLabsApiKey());

        // Load language
        String savedLang = prefs.getLanguage();
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(savedLang)) {
                spinnerLanguage.setSelection(i);
                break;
            }
        }

        // Load TTS provider
        String savedProvider = prefs.getTtsProvider();
        for (int i = 0; i < TTS_PROVIDER_CODES.length; i++) {
            if (TTS_PROVIDER_CODES[i].equals(savedProvider)) {
                spinnerTtsProvider.setSelection(i);
                break;
            }
        }

        // Load silence timeout (stored in ms, display in seconds)
        int timeoutMs = prefs.getSilenceTimeout();
        int timeoutSec = timeoutMs / 1000;
        sliderSilenceTimeout.setValue(Math.max(1, Math.min(10, timeoutSec)));
        txtSilenceValue.setText(timeoutSec + "s");

        updateTtsProviderWarning();
    }

    private void saveSettings() {
        String url = getText(editGatewayUrl);
        String token = getText(editAuthToken);

        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "URL and Token required", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.setGatewayUrl(url);
        prefs.setAuthToken(token);
        prefs.setCartesiaApiKey(getText(editCartesiaKey));
        prefs.setElevenLabsApiKey(getText(editElevenLabsKey));

        // Save language
        int langIndex = spinnerLanguage.getSelectedItemPosition();
        if (langIndex >= 0 && langIndex < LANGUAGE_CODES.length) {
            prefs.setLanguage(LANGUAGE_CODES[langIndex]);
        }

        // Save TTS provider
        int providerIndex = spinnerTtsProvider.getSelectedItemPosition();
        if (providerIndex >= 0 && providerIndex < TTS_PROVIDER_CODES.length) {
            prefs.setTtsProvider(TTS_PROVIDER_CODES[providerIndex]);
        }

        // Save silence timeout (convert seconds to ms)
        int timeoutSec = (int) sliderSilenceTimeout.getValue();
        prefs.setSilenceTimeout(timeoutSec * 1000);

        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        updateStatus();
    }
    
    private String getText(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }

    private void testConnection() {
        String url = getText(editGatewayUrl);
        String token = getText(editAuthToken);
        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Fill URL and Token", Toast.LENGTH_SHORT).show();
            return;
        }

        MaterialButton btn = findViewById(R.id.btnTestConnection);
        btn.setEnabled(false);
        btn.setText("...");

        new ClawdbotClient(url, token).sendMessage("ping", new ClawdbotClient.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                btn.setEnabled(true);
                btn.setText("Test Connection");
                Toast.makeText(SettingsActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(String error) {
                btn.setEnabled(true);
                btn.setText("Test Connection");
                Toast.makeText(SettingsActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openAssistantSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private void updateTtsProviderWarning() {
        int position = spinnerTtsProvider.getSelectedItemPosition();
        if (position < 0 || position >= TTS_PROVIDER_CODES.length) {
            txtTtsProviderWarning.setVisibility(android.view.View.GONE);
            return;
        }

        String provider = TTS_PROVIDER_CODES[position];
        String cartesiaKey = getText(editCartesiaKey);
        String elevenLabsKey = getText(editElevenLabsKey);

        if (provider.equals(PreferencesManager.TTS_PROVIDER_CARTESIA) && cartesiaKey.isEmpty()) {
            txtTtsProviderWarning.setText(R.string.tts_provider_warning_cartesia);
            txtTtsProviderWarning.setVisibility(android.view.View.VISIBLE);
        } else if (provider.equals(PreferencesManager.TTS_PROVIDER_ELEVENLABS) && elevenLabsKey.isEmpty()) {
            txtTtsProviderWarning.setText(R.string.tts_provider_warning_elevenlabs);
            txtTtsProviderWarning.setVisibility(android.view.View.VISIBLE);
        } else {
            txtTtsProviderWarning.setVisibility(android.view.View.GONE);
        }
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(prefs.isConfigured() ? "OK Gateway configured\n" : "X Gateway not configured\n");
        sb.append(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED ? "OK Microphone\n" : "X No microphone permission\n");

        // Show TTS provider status
        String provider = prefs.getTtsProvider();
        switch (provider) {
            case "cartesia":
                sb.append(!prefs.getCartesiaApiKey().isEmpty() ? "OK TTS: Cartesia\n" : "X TTS: Cartesia (no API key)\n");
                break;
            case "elevenlabs":
                sb.append(!prefs.getElevenLabsApiKey().isEmpty() ? "OK TTS: ElevenLabs\n" : "X TTS: ElevenLabs (no API key)\n");
                break;
            case "google":
                sb.append("OK TTS: Google\n");
                break;
            default: // auto
                sb.append("TTS: Auto (");
                if (!prefs.getCartesiaApiKey().isEmpty()) {
                    sb.append("Cartesia");
                } else if (!prefs.getElevenLabsApiKey().isEmpty()) {
                    sb.append("ElevenLabs");
                } else {
                    sb.append("Google");
                }
                sb.append(")\n");
                break;
        }

        sb.append("Language: ").append(prefs.getLanguage()).append("\n");
        sb.append("Silence timeout: ").append(prefs.getSilenceTimeout() / 1000).append("s\n");
        txtStatus.setText(sb.toString());
    }

    @Override protected void onResume() { super.onResume(); updateStatus(); }
}
