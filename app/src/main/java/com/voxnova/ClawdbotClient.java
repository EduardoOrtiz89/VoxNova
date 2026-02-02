package com.voxnova;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ClawdbotClient {
    private static final int PROTOCOL_VERSION = 3;
    
    private final OkHttpClient client;
    private final String gatewayUrl;
    private final String authToken;
    private final Handler mainHandler;
    private final String sessionKey;
    private final DeviceIdentity deviceIdentity;
    private WebSocket webSocket;
    private ResponseCallback pendingCallback;
    private StringBuilder responseBuffer;
    private boolean isConnected = false;
    private String currentReqId;
    private String pendingMessageToSend;
    private String pendingResetReqId;
    private boolean pendingResetAfterConnect = false;
    
    // Challenge handling
    private String pendingNonce;
    private long pendingNonceTs;

    public interface ResponseCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public ClawdbotClient(Context context, String gatewayUrl, String authToken) {
        String wsUrl = gatewayUrl.replace("https://", "wss://").replace("http://", "ws://");
        this.gatewayUrl = wsUrl.endsWith("/") ? wsUrl.substring(0, wsUrl.length() - 1) : wsUrl;
        this.authToken = authToken;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.responseBuffer = new StringBuilder();
        this.sessionKey = "agent:main:voxnova:android";
        this.deviceIdentity = new DeviceIdentity(context);
        
        DebugLogger.log("ClawdbotClient init, gateway=" + this.gatewayUrl);
        DebugLogger.log("Device ID: " + deviceIdentity.getDeviceId().substring(0, 16) + "...");
        
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    // Legacy constructor for compatibility
    public ClawdbotClient(String gatewayUrl, String authToken) {
        this(null, gatewayUrl, authToken);
    }

    public void sendMessage(String text, ResponseCallback callback) {
        DebugLogger.log("sendMessage: " + text);
        this.pendingCallback = callback;
        this.responseBuffer = new StringBuilder();
        this.currentReqId = UUID.randomUUID().toString();
        this.pendingMessageToSend = text;
        this.pendingResetReqId = null;
        this.pendingResetAfterConnect = false;
        
        if (webSocket != null && isConnected) {
            DebugLogger.log("Already connected, sending directly");
            sendChatMessage(text);
        } else {
            DebugLogger.log("Not connected, connecting first...");
            connect();
        }
    }
    
    private void connect() {
        DebugLogger.log("Connecting to " + gatewayUrl);
        pendingNonce = null;
        pendingNonceTs = 0;
        
        Request request = new Request.Builder().url(gatewayUrl).build();
        
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                DebugLogger.success("WebSocket OPEN - waiting for challenge");
                // Don't send connect yet, wait for challenge
            }
            
            @Override
            public void onMessage(WebSocket ws, String text) {
                DebugLogger.log("onMessage: " + text.substring(0, Math.min(150, text.length())));
                
                try {
                    JSONObject msg = new JSONObject(text);
                    String type = msg.optString("type", "");
                    
                    // Handle connect.challenge - must respond with signed connect
                    if (type.equals("event")) {
                        String event = msg.optString("event", "");
                        JSONObject payload = msg.optJSONObject("payload");
                        
                        if (event.equals("connect.challenge") && payload != null) {
                            pendingNonce = payload.optString("nonce", "");
                            pendingNonceTs = payload.optLong("ts", System.currentTimeMillis());
                            DebugLogger.log("Received challenge, nonce=" + pendingNonce.substring(0, Math.min(16, pendingNonce.length())) + "...");
                            sendConnectRequest(ws);
                            return;
                        }
                        
                        // Skip non-relevant events
                        if (event.equals("health") || event.equals("tick")) {
                            return;
                        }
                        
                        if (payload == null) return;
                        
                        // Agent event - stream response
                        if (event.equals("agent")) {
                            String stream = payload.optString("stream", "");
                            JSONObject data = payload.optJSONObject("data");
                            
                            if (stream.equals("assistant") && data != null) {
                                String delta = data.optString("delta", "");
                                if (!delta.isEmpty()) {
                                    responseBuffer.append(delta);
                                }
                            }
                            
                            // End of response
                            if (stream.equals("lifecycle") && data != null) {
                                String phase = data.optString("phase", "");
                                if (phase.equals("end")) {
                                    String fullResponse = responseBuffer.toString().trim();
                                    if (!fullResponse.isEmpty()) {
                                        DebugLogger.success("Response complete, length=" + fullResponse.length());
                                        notifySuccess(fullResponse);
                                    }
                                }
                            }
                            return;
                        }
                        
                        // Chat event - final state
                        if (event.equals("chat")) {
                            String state = payload.optString("state", "");
                            if (state.equals("final")) {
                                JSONObject message = payload.optJSONObject("message");
                                if (message != null) {
                                    JSONArray content = message.optJSONArray("content");
                                    if (content != null && content.length() > 0) {
                                        JSONObject first = content.optJSONObject(0);
                                        if (first != null && "text".equals(first.optString("type"))) {
                                            String finalText = first.optString("text", "");
                                            if (!finalText.isEmpty() && responseBuffer.length() == 0) {
                                                DebugLogger.success("Final response: " + finalText.substring(0, Math.min(50, finalText.length())));
                                                notifySuccess(finalText);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return;
                    }
                    
                    // Handle connect response (hello-ok)
                    if (type.equals("res")) {
                        JSONObject payload = msg.optJSONObject("payload");
                        if (payload != null && "hello-ok".equals(payload.optString("type"))) {
                            DebugLogger.success("Connected! hello-ok received");
                            isConnected = true;
                            
                            // Check for device token
                            JSONObject auth = payload.optJSONObject("auth");
                            if (auth != null) {
                                String deviceToken = auth.optString("deviceToken", "");
                                if (!deviceToken.isEmpty()) {
                                    DebugLogger.success("Received device token (paired)");
                                }
                            }
                            
                            // Check if we have a pending reset request
                            if (pendingResetAfterConnect) {
                                pendingResetAfterConnect = false;
                                DebugLogger.log("Sending pending reset request after connect");
                                sendResetRequest();
                                return;
                            }
                            
                            // Send pending message if any
                            if (pendingMessageToSend != null) {
                                String m = pendingMessageToSend;
                                pendingMessageToSend = null;
                                DebugLogger.log("Sending pending message: " + m);
                                sendChatMessage(m);
                            }
                            return;
                        }
                        
                        // Handle sessions.reset response
                        if (pendingResetReqId != null) {
                            String resId = msg.optString("id", "");
                            if (resId.equals(pendingResetReqId)) {
                                pendingResetReqId = null;
                                if (msg.optBoolean("ok", false)) {
                                    DebugLogger.success("Session reset OK - new session created");
                                    notifySuccess("Conversación reiniciada");
                                } else {
                                    JSONObject error = msg.optJSONObject("error");
                                    String errMsg = error != null ? error.optString("message", "Reset failed") : "Reset failed";
                                    DebugLogger.error("Reset failed: " + errMsg);
                                    notifyError(errMsg);
                                }
                                return;
                            }
                        }
                        
                        // Handle error responses
                        if (!msg.optBoolean("ok", true)) {
                            JSONObject error = msg.optJSONObject("error");
                            String errMsg = error != null ? error.optString("message", "Error") : "Failed";
                            DebugLogger.error("Error response: " + errMsg);
                            notifyError(errMsg);
                            return;
                        }
                    }
                    
                } catch (JSONException e) {
                    DebugLogger.error("Parse error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                DebugLogger.error("WebSocket failed: " + t.getMessage());
                isConnected = false;
                pendingResetReqId = null;
                pendingResetAfterConnect = false;
                notifyError("Connection failed: " + t.getMessage());
            }
            
            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                DebugLogger.log("WebSocket closed: " + code + " " + reason);
                isConnected = false;
                pendingResetReqId = null;
                pendingResetAfterConnect = false;
            }
        });
    }
    
    private void sendConnectRequest(WebSocket ws) {
        try {
            JSONObject connectMsg = new JSONObject();
            connectMsg.put("type", "req");
            connectMsg.put("method", "connect");
            connectMsg.put("id", UUID.randomUUID().toString());
            
            JSONObject params = new JSONObject();
            params.put("minProtocol", PROTOCOL_VERSION);
            params.put("maxProtocol", PROTOCOL_VERSION);
            
            JSONObject clientInfo = new JSONObject();
            clientInfo.put("id", "cli");
            clientInfo.put("version", "2.0.0");
            clientInfo.put("platform", "android");
            clientInfo.put("mode", "cli");
            params.put("client", clientInfo);

            params.put("role", "operator");

            JSONArray scopes = new JSONArray();
            scopes.put("operator.read");
            scopes.put("operator.write");
            params.put("scopes", scopes);
            
            JSONObject auth = new JSONObject();
            auth.put("token", authToken);
            params.put("auth", auth);
            
            // Device identity with signed challenge
            // Payload format: v2|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce
            if (deviceIdentity != null && pendingNonce != null && !pendingNonce.isEmpty()) {
                long signedAt = System.currentTimeMillis();
                String scopesStr = "operator.read,operator.write";

                JSONObject device = new JSONObject();
                device.put("id", deviceIdentity.getDeviceId());
                device.put("publicKey", deviceIdentity.getPublicKeyBase64Url());
                device.put("signature", deviceIdentity.signPayload(
                    "cli",               // clientId
                    "cli",               // clientMode
                    "operator",          // role
                    scopesStr,           // scopes
                    signedAt,            // signedAtMs
                    authToken,           // token
                    pendingNonce         // nonce
                ));
                device.put("signedAt", signedAt);
                device.put("nonce", pendingNonce);
                params.put("device", device);
                DebugLogger.log("Added device identity with signed payload (v2)");
            }
            
            params.put("locale", "es-MX");
            params.put("userAgent", "VoxNova/2.0.0 Android");
            
            connectMsg.put("params", params);
            DebugLogger.log("Sending connect request with device identity");
            ws.send(connectMsg.toString());
            
        } catch (JSONException e) {
            DebugLogger.error("Connect error: " + e.getMessage());
            notifyError("Connect failed: " + e.getMessage());
        }
    }
    
    private void sendChatMessage(String text) {
        if (webSocket == null || !isConnected) {
            DebugLogger.error("sendChatMessage: not connected!");
            notifyError("Not connected");
            return;
        }
        
        try {
            JSONObject chatMsg = new JSONObject();
            chatMsg.put("type", "req");
            chatMsg.put("method", "chat.send");
            chatMsg.put("id", currentReqId);
            
            JSONObject params = new JSONObject();
            params.put("message", text);
            params.put("sessionKey", sessionKey);
            params.put("idempotencyKey", currentReqId);
            chatMsg.put("params", params);
            
            DebugLogger.log("Sending chat.send: " + text);
            webSocket.send(chatMsg.toString());
            
        } catch (JSONException e) {
            DebugLogger.error("sendChatMessage error: " + e.getMessage());
            notifyError("Send failed: " + e.getMessage());
        }
    }
    
    private void notifySuccess(String response) {
        if (pendingCallback != null) {
            final ResponseCallback cb = pendingCallback;
            pendingCallback = null;
            mainHandler.post(() -> cb.onSuccess(response));
        }
    }
    
    private void notifyError(String error) {
        if (pendingCallback != null) {
            final ResponseCallback cb = pendingCallback;
            pendingCallback = null;
            mainHandler.post(() -> cb.onError(error));
        }
    }
    
    public String getSessionKey() {
        return sessionKey;
    }
    
    public void disconnect() {
        DebugLogger.log("disconnect()");
        if (webSocket != null) {
            webSocket.close(1000, "Bye");
            webSocket = null;
            isConnected = false;
        }
    }

    /**
     * Reset the conversation context using the sessions.reset RPC method.
     * This creates a new sessionId on the OpenClaw gateway, effectively
     * starting a fresh conversation without any previous context.
     */
    public void resetSession(ResponseCallback callback) {
        DebugLogger.log("resetSession - using sessions.reset RPC");
        this.pendingCallback = callback;
        this.responseBuffer = new StringBuilder();
        this.pendingMessageToSend = null;
        
        if (webSocket != null && isConnected) {
            DebugLogger.log("Already connected, sending reset directly");
            sendResetRequest();
        } else {
            DebugLogger.log("Not connected, will reset after connect");
            pendingResetAfterConnect = true;
            connect();
        }
    }
    
    private void sendResetRequest() {
        if (webSocket == null || !isConnected) {
            DebugLogger.error("sendResetRequest: not connected!");
            notifyError("Not connected");
            return;
        }
        
        try {
            String reqId = UUID.randomUUID().toString();
            pendingResetReqId = reqId;
            
            JSONObject resetMsg = new JSONObject();
            resetMsg.put("type", "req");
            resetMsg.put("method", "sessions.reset");
            resetMsg.put("id", reqId);
            
            JSONObject params = new JSONObject();
            params.put("key", sessionKey);
            resetMsg.put("params", params);
            
            DebugLogger.log(">>> Sending sessions.reset for key: " + sessionKey + " reqId: " + reqId);
            webSocket.send(resetMsg.toString());
            
        } catch (JSONException e) {
            DebugLogger.error("sendResetRequest error: " + e.getMessage());
            pendingResetReqId = null;
            notifyError("Reset failed: " + e.getMessage());
        }
    }
}
