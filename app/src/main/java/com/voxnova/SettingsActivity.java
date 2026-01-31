package com.voxnova;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {
    private TextInputEditText editGatewayUrl, editAuthToken, editCartesiaKey, editElevenLabsKey;
    private TextView txtStatus, txtDebugLog;
    private PreferencesManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        prefs = new PreferencesManager(this);
        initViews();
        loadSettings();
        checkPermissions();
        updateStatus();
        setupDebugLogger();
    }

    private void initViews() {
        editGatewayUrl = findViewById(R.id.editGatewayUrl);
        editAuthToken = findViewById(R.id.editAuthToken);
        editCartesiaKey = findViewById(R.id.editCartesiaKey);
        editElevenLabsKey = findViewById(R.id.editElevenLabsKey);
        txtStatus = findViewById(R.id.txtStatus);
        txtDebugLog = findViewById(R.id.txtDebugLog);
        
        findViewById(R.id.btnSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btnTestConnection).setOnClickListener(v -> testConnection());
        findViewById(R.id.btnOpenAssistantSettings).setOnClickListener(v -> openAssistantSettings());
        findViewById(R.id.btnCopyLog).setOnClickListener(v -> copyLog());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> clearLog());
    }
    
    private void setupDebugLogger() {
        txtDebugLog.setText(DebugLogger.getLogsAsString());
        DebugLogger.setListener(log -> runOnUiThread(() -> txtDebugLog.append(log + "\n")));
        DebugLogger.log("SettingsActivity started");
    }
    
    private void copyLog() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("VoxNova Log", DebugLogger.getLogsAsString()));
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
    }
    
    private void clearLog() {
        DebugLogger.clear();
        txtDebugLog.setText("");
        DebugLogger.log("Log cleared");
    }

    private void loadSettings() {
        editGatewayUrl.setText(prefs.getGatewayUrl());
        editAuthToken.setText(prefs.getAuthToken());
        editCartesiaKey.setText(prefs.getCartesiaApiKey());
        editElevenLabsKey.setText(prefs.getElevenLabsApiKey());
    }

    private void saveSettings() {
        String url = getText(editGatewayUrl);
        String token = getText(editAuthToken);
        
        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "URL y Token requeridos", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.setGatewayUrl(url);
        prefs.setAuthToken(token);
        prefs.setCartesiaApiKey(getText(editCartesiaKey));
        prefs.setElevenLabsApiKey(getText(editElevenLabsKey));
        
        Toast.makeText(this, "Guardado ✓", Toast.LENGTH_SHORT).show();
        updateStatus();
    }
    
    private String getText(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }

    private void testConnection() {
        String url = getText(editGatewayUrl);
        String token = getText(editAuthToken);
        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Llena URL y Token", Toast.LENGTH_SHORT).show();
            return;
        }
        
        DebugLogger.log("=== TEST CONNECTION START ===");
        MaterialButton btn = findViewById(R.id.btnTestConnection);
        btn.setEnabled(false);
        btn.setText("...");

        new ClawdbotClient(url, token).sendMessage("ping", new ClawdbotClient.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                DebugLogger.success("=== TEST SUCCESS ===");
                btn.setEnabled(true);
                btn.setText("Test");
                Toast.makeText(SettingsActivity.this, "✓ Conectado!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(String error) {
                DebugLogger.error("=== TEST FAILED: " + error + " ===");
                btn.setEnabled(true);
                btn.setText("Test");
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

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(prefs.isConfigured() ? "✓ Gateway configurado\n" : "✗ Gateway no configurado\n");
        sb.append(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED ? "✓ Micrófono OK\n" : "✗ Sin permiso micrófono\n");
        sb.append(!prefs.getCartesiaApiKey().isEmpty() ? "✓ Cartesia TTS\n" : "");
        sb.append(!prefs.getElevenLabsApiKey().isEmpty() ? "✓ ElevenLabs TTS\n" : "");
        sb.append(prefs.getCartesiaApiKey().isEmpty() && prefs.getElevenLabsApiKey().isEmpty() 
                ? "→ Usando Google TTS\n" : "");
        txtStatus.setText(sb.toString());
    }

    @Override protected void onResume() { super.onResume(); updateStatus(); }
    @Override protected void onDestroy() { super.onDestroy(); DebugLogger.setListener(null); }
}
