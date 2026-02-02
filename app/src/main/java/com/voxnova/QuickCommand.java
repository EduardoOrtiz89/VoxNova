package com.voxnova;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class QuickCommand {
    public final int iconRes;
    public final String label;
    public final String command;

    public QuickCommand(int iconRes, String label, String command) {
        this.iconRes = iconRes;
        this.label = label;
        this.command = command;
    }

    public QuickCommand(String label, String command) {
        this(R.drawable.ic_cmd_status, label, command);
    }

    /**
     * Load commands from user preferences
     */
    public static QuickCommand[] getCommands(Context context) {
        PreferencesManager prefs = new PreferencesManager(context);
        return fromJson(prefs.getQuickCommandsJson());
    }

    /**
     * Parse JSON array into QuickCommand array
     */
    public static QuickCommand[] fromJson(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            QuickCommand[] commands = new QuickCommand[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String label = obj.getString("label");
                String command = obj.getString("command");
                commands[i] = new QuickCommand(label, command);
            }
            return commands;
        } catch (JSONException e) {
            return new QuickCommand[0];
        }
    }

    /**
     * Serialize QuickCommand array to JSON
     */
    public static String toJson(QuickCommand[] commands) {
        try {
            JSONArray arr = new JSONArray();
            for (QuickCommand cmd : commands) {
                JSONObject obj = new JSONObject();
                obj.put("label", cmd.label);
                obj.put("command", cmd.command);
                arr.put(obj);
            }
            return arr.toString();
        } catch (JSONException e) {
            return "[]";
        }
    }
}
