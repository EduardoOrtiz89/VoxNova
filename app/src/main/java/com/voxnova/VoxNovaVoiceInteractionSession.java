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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class VoxNovaVoiceInteractionSession extends VoiceInteractionSession {

    public enum VoiceStatus {
        LISTENING(R.drawable.ic_status_listening, R.color.listening_color, "Escuchando..."),
        LISTENING_READY(R.drawable.ic_status_listening, R.color.listening_color, "Habla ahora..."),
        PROCESSING(R.drawable.ic_status_processing, R.color.processing_color, "Pensando..."),
        SPEAKING(R.drawable.ic_status_speaking, R.color.speaking_color, "Respondiendo..."),
        COMMANDS(R.drawable.ic_status_commands, R.color.commands_color, "Comandos"),
        RESET(R.drawable.ic_status_reset, R.color.reset_color, "Reiniciando..."),
        SUCCESS(R.drawable.ic_status_success, R.color.success_color, "Listo");

        public final int iconRes;
        public final int colorRes;
        public final String label;

        VoiceStatus(int iconRes, int colorRes, String label) {
            this.iconRes = iconRes;
            this.colorRes = colorRes;
            this.label = label;
        }
    }

    private final Context context;
    private final Handler mainHandler;
    private PreferencesManager prefs;
    private SpeechRecognizer speechRecognizer;
    private ClawdbotClient clawdbotClient;
    private TTSManager ttsManager;

    private View contentView;
    private ImageView statusIcon;
    private TextView txtStatusLabel;
    private TextView txtTranscript;
    private ScrollView transcriptScrollView;
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
                showMessage("Not configured - open VoxNova");
                mainHandler.postDelayed(this::finish, 2000);
                return;
            }

            clawdbotClient = new ClawdbotClient(prefs.getGatewayUrl(), prefs.getAuthToken());

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                showMessage("Speech recognition not available");
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
            statusIcon = contentView.findViewById(R.id.statusIcon);
            txtStatusLabel = contentView.findViewById(R.id.txtStatusLabel);
            txtTranscript = contentView.findViewById(R.id.txtTranscript);
            transcriptScrollView = contentView.findViewById(R.id.transcriptScrollView);
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
        setStatus(VoiceStatus.COMMANDS);
        showMessage("Select a command");

        if (commandsPanel != null && commandsScrollView != null) {
            commandsPanel.removeAllViews();

            QuickCommand[] commands = QuickCommand.getCommands();
            DebugLogger.log("Adding " + commands.length + " commands");

            for (QuickCommand cmd : commands) {
                LinearLayout itemLayout = new LinearLayout(context);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(
                    dpToPx(32), dpToPx(16),
                    dpToPx(32), dpToPx(16)
                );
                itemLayout.setBackgroundResource(android.R.drawable.list_selector_background);
                itemLayout.setClickable(true);
                itemLayout.setFocusable(true);
                itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                ImageView icon = new ImageView(context);
                icon.setImageResource(cmd.iconRes);
                icon.setColorFilter(ContextCompat.getColor(context, R.color.command_icon_color));
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    dpToPx(24), dpToPx(24)
                );
                iconParams.setMarginEnd(dpToPx(12));
                icon.setLayoutParams(iconParams);

                TextView label = new TextView(context);
                label.setText(cmd.label);
                label.setTextSize(18);
                label.setTextColor(ContextCompat.getColor(context, R.color.text_primary));

                itemLayout.addView(icon);
                itemLayout.addView(label);

                final String command = cmd.command;
                itemLayout.setOnClickListener(v -> {
                    DebugLogger.log("Command clicked: " + command);
                    hideCommandsPanel();
                    executeCommand(command);
                });

                commandsPanel.addView(itemLayout);
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
        setStatus(VoiceStatus.PROCESSING);
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
        mainHandler.post(() -> {
            if (txtTranscript != null) {
                txtTranscript.setText(text);
                // Scroll to bottom for long responses
                if (transcriptScrollView != null) {
                    transcriptScrollView.post(() ->
                        transcriptScrollView.fullScroll(View.FOCUS_DOWN)
                    );
                }
            }
        });
    }

    private void setStatus(VoiceStatus status) {
        mainHandler.post(() -> {
            if (txtStatusLabel != null) {
                txtStatusLabel.setText(status.label);
            }
            if (statusIcon != null) {
                statusIcon.setImageResource(status.iconRes);
                statusIcon.setColorFilter(ContextCompat.getColor(context, status.colorRes));
            }
        });
    }

    private void startListening() {
        DebugLogger.log("Starting STT...");
        setStatus(VoiceStatus.LISTENING);
        showMessage("");

        mainHandler.post(() -> {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {
                        DebugLogger.log("STT ready");
                        setStatus(VoiceStatus.LISTENING_READY);
                    }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() { setStatus(VoiceStatus.PROCESSING); }

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
                            showMessage("Voice not recognized");
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
        setStatus(VoiceStatus.PROCESSING);
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
        setStatus(VoiceStatus.SPEAKING);
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
            case SpeechRecognizer.ERROR_AUDIO: return "Audio error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "No permission";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NO_MATCH: return "Not understood";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Timeout";
            default: return "Error " + error;
        }
    }

    private void sendResetCommand() {
        DebugLogger.log("Resetting session");
        setStatus(VoiceStatus.RESET);
        showMessage("Restarting conversation...");

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
                setStatus(VoiceStatus.SUCCESS);
                showMessage("New conversation started");
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

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
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
