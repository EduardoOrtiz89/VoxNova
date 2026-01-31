package com.voxnova;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DebugLogger {
    private static final String TAG = "VoxNova";
    private static final int MAX_LOGS = 100;
    private static final List<String> logs = new ArrayList<>();
    private static LogListener listener;
    
    public interface LogListener {
        void onLogAdded(String log);
    }
    
    public static void setListener(LogListener l) {
        listener = l;
    }
    
    public static void initRemote(android.content.Context context) {
        // Remote logging disabled - was causing AI to respond to debug messages
    }
    
    public static void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String entry = timestamp + " " + message;
        
        Log.d(TAG, message);
        
        synchronized (logs) {
            logs.add(entry);
            if (logs.size() > MAX_LOGS) logs.remove(0);
        }
        
        if (listener != null) {
            listener.onLogAdded(entry);
        }
    }
    
    public static void error(String message) { log("❌ " + message); }
    public static void success(String message) { log("✓ " + message); }
    
    public static String getLogsAsString() {
        StringBuilder sb = new StringBuilder();
        synchronized (logs) {
            for (String log : logs) sb.append(log).append("\n");
        }
        return sb.toString();
    }
    
    public static void clear() {
        synchronized (logs) { logs.clear(); }
    }
    
    public static void closeRemote() { }
}
