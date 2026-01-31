package com.voxnova;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

public class VoxNovaVoiceInteractionSession extends VoiceInteractionSession {
    private final Context context;
    private final Handler mainHandler;
    private PreferencesManager prefs;
    private SpeechRecognizer speechRecognizer;
    private ClawdbotClient clawdbotClient;
    private TTSManager ttsManager;
    
    private View contentView;
    private TextView txtStatusLabel;
    private TextView txtTranscript;
    private ScrollView commandsScrollView;
    private LinearLayout commandsPanel;
    
    private boolean showingCommands = false;

    public VoxNovaVoiceInteractionSession(Context context) {
        super(context);
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DebugLogger.log("=== Session CREATED ===");
        try {
            prefs = new PreferencesManager(context);
            ttsManager = new TTSManager(context, prefs);
        } catch (Exception e) {
            DebugLogger.error("onCreate: " + e.getMessage());
        }
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        DebugLogger.log("Session SHOW");
        
        try {
            if (!prefs.isConfigured()) {
                showMessage("No configurado - abre VoxNova");
                mainHandler.postDelayed(this::finish, 2000);
                return;
            }
            
            clawdbotClient = new ClawdbotClient(prefs.getGatewayUrl(), prefs.getAuthToken());
            
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                showMessage("Speech recognition no disponible");
                mainHandler.postDelayed(this::finish, 2000);
                return;
            }
            
            startListening();
        } catch (Exception e) {
            DebugLogger.error("onShow: " + e.getMessage());
            showMessage("Error: " + e.getMessage());
            mainHandler.postDelayed(this::finish, 2000);
        }
    }

    @Override
    public View onCreateContentView() {
        try {
            contentView = LayoutInflater.from(context).inflate(R.layout.voice_interaction_ui, null);
            txtStatusLabel = contentView.findViewById(R.id.txtStatusLabel);
            txtTranscript = contentView.findViewById(R.id.txtTranscript);
            commandsScrollView = contentView.findViewById(R.id.commandsScrollView);
            commandsPanel = contentView.findViewById(R.id.commandsPanel);
            
            View btnCancel = contentView.findViewById(R.id.btnCancel);
            if (btnCancel != null) btnCancel.setOnClickListener(v -> { cleanup(); finish(); });
            
            View btnNewSession = contentView.findViewById(R.id.btnNewSession);
            if (btnNewSession != null) btnNewSession.setOnClickListener(v -> sendResetCommand());
            
            View btnQuickCommands = contentView.findViewById(R.id.btnQuickCommands);
            if (btnQuickCommands != null) btnQuickCommands.setOnClickListener(v -> toggleCommandsPanel());
            
            return contentView;
        } catch (Exception e) {
            DebugLogger.error("onCreateContentView: " + e.getMessage());
            TextView tv = new TextView(context);
            tv.setText("Error loading UI: " + e.getMessage());
            tv.setPadding(32, 32, 32, 32);
            return tv;
        }
    }
    
    private void toggleCommandsPanel() {
        DebugLogger.log("toggleCommandsPanel: showing=" + showingCommands);
        if (showingCommands) {
            hideCommandsPanel();
            startListening();
        } else {
            showCommandsPanel();
        }
    }
    
    private void showCommandsPanel() {
        DebugLogger.log("showCommandsPanel");
        
        // Stop listening
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        
        showingCommands = true;
        setStatus("⚡ Comandos");
        showMessage("Selecciona un comando");
        
        if (commandsPanel != null && commandsScrollView != null) {
            commandsPanel.removeAllViews();
            
            QuickCommand[] commands = QuickCommand.getCommands();
            DebugLogger.log("Adding " + commands.length + " commands");
            
            for (QuickCommand cmd : commands) {
                TextView btn = new TextView(context);
                btn.setText(cmd.icon + "  " + cmd.label);
                btn.setTextSize(18);
                btn.setTextColor(0xFF000000);
                btn.setPadding(32, 28, 32, 28);
                btn.setBackgroundResource(android.R.drawable.list_selector_background);
                btn.setClickable(true);
                btn.setFocusable(true);
                
                final String command = cmd.command;
                btn.setOnClickListener(v -> {
                    DebugLogger.log("Command clicked: " + command);
                    hideCommandsPanel();
                    executeCommand(command);
                });
                
                commandsPanel.addView(btn);
            }
            
            commandsScrollView.setVisibility(View.VISIBLE);
            commandsPanel.setVisibility(View.VISIBLE);
        } else {
            DebugLogger.error("commandsPanel or commandsScrollView is null!");
        }
    }
    
    private void hideCommandsPanel() {
        DebugLogger.log("hideCommandsPanel");
        showingCommands = false;
        if (commandsScrollView != null) {
            commandsScrollView.setVisibility(View.GONE);
        }
        if (commandsPanel != null) {
            commandsPanel.setVisibility(View.GONE);
        }
    }
    
    private void executeCommand(String command) {
        DebugLogger.log("executeCommand: " + command);
        setStatus("🤔 Procesando...");
        showMessage(command);
        
        // Create fresh client for command
        if (clawdbotClient != null) {
            clawdbotClient.disconnect();
        }
        clawdbotClient = new ClawdbotClient(prefs.getGatewayUrl(), prefs.getAuthToken());
        
        clawdbotClient.sendMessage(command, new ClawdbotClient.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                DebugLogger.log("Command response received, length=" + response.length());
                speak(response);
            }
            @Override
            public void onError(String error) {
                DebugLogger.error("Command error: " + error);
                showMessage("Error: " + error);
                mainHandler.postDelayed(() -> finish(), 2000);
            }
        });
    }

    private void showMessage(String text) {
        mainHandler.post(() -> { if (txtTranscript != null) txtTranscript.setText(text); });
    }

    private void setStatus(String status) {
        mainHandler.post(() -> { if (txtStatusLabel != null) txtStatusLabel.setText(status); });
    }

    private void startListening() {
        DebugLogger.log("Starting STT...");
        setStatus("🎤 Escuchando...");
        showMessage("");
        
        mainHandler.post(() -> {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) { 
                        DebugLogger.log("STT ready");
                        setStatus("🎤 Habla ahora..."); 
                    }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() { setStatus("Procesando..."); }
                    
                    @Override
                    public void onError(int error) {
                        String msg = getErrorText(error);
                        DebugLogger.error("STT error: " + msg);
                        showMessage(msg);
                        mainHandler.postDelayed(() -> finish(), 2000);
                    }
                    
                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String text = matches.get(0);
                            DebugLogger.log("STT result: " + text);
                            processText(text);
                        } else {
                            showMessage("No se reconoció voz");
                            mainHandler.postDelayed(() -> finish(), 2000);
                        }
                    }
                    
                    @Override
                    public void onPartialResults(Bundle partial) {
                        ArrayList<String> matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) showMessage(matches.get(0));
                    }
                    
                    @Override public void onEvent(int eventType, Bundle params) {}
                });

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                speechRecognizer.startListening(intent);
                
            } catch (Exception e) {
                DebugLogger.error("startListening: " + e.getMessage());
                showMessage("Error: " + e.getMessage());
                mainHandler.postDelayed(() -> finish(), 2000);
            }
        });
    }

    private void processText(String text) {
        DebugLogger.log("Sending to Clawdbot: " + text);
        setStatus("🤔 Pensando...");
        showMessage(text);
        
        clawdbotClient.sendMessage(text, new ClawdbotClient.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                DebugLogger.log("Got response, length=" + response.length());
                speak(response);
            }
            @Override
            public void onError(String error) {
                DebugLogger.error("Clawdbot error: " + error);
                showMessage("Error: " + error);
                mainHandler.postDelayed(() -> finish(), 2000);
            }
        });
    }

    private void speak(String text) {
        setStatus("🔊 Respondiendo...");
        showMessage(TTSManager.stripEmojis(text));
        
        ttsManager.speak(text, new TTSManager.TTSCallback() {
            @Override public void onStart() { DebugLogger.log("TTS started"); }
            @Override public void onDone() { DebugLogger.log("TTS done"); finish(); }
            @Override public void onError(String error) { 
                DebugLogger.error("TTS error: " + error);
                mainHandler.postDelayed(() -> finish(), 3000);
            }
        });
    }

    private String getErrorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Error de audio";
            case SpeechRecognizer.ERROR_CLIENT: return "Error cliente";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Sin permiso";
            case SpeechRecognizer.ERROR_NETWORK: return "Error de red";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No entendí";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Timeout";
            default: return "Error " + error;
        }
    }

    private void sendResetCommand() {
        DebugLogger.log("Resetting session");
        setStatus("🔄 Reiniciando...");
        showMessage("Reiniciando conversación...");
        
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        
        if (clawdbotClient != null) {
            clawdbotClient.disconnect();
        }
        clawdbotClient = new ClawdbotClient(prefs.getGatewayUrl(), prefs.getAuthToken());
        
        clawdbotClient.resetSession(new ClawdbotClient.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                DebugLogger.log("Session reset OK");
                setStatus("✓ Listo");
                showMessage("Nueva conversación iniciada");
                mainHandler.postDelayed(() -> finish(), 1500);
            }
            @Override
            public void onError(String error) {
                DebugLogger.error("Reset failed: " + error);
                showMessage("Error: " + error);
                mainHandler.postDelayed(() -> finish(), 2000);
            }
        });
    }
    
    private void cleanup() {
        try {
            if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
            if (ttsManager != null) { ttsManager.stop(); }
            if (clawdbotClient != null) { clawdbotClient.disconnect(); }
        } catch (Exception e) {}
    }

    @Override public void onHide() { super.onHide(); cleanup(); }
    @Override public void onDestroy() { 
        super.onDestroy(); 
        cleanup();
        if (ttsManager != null) ttsManager.shutdown();
    }
}
