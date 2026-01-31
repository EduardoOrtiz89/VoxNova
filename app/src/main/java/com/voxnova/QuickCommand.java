package com.voxnova;

public class QuickCommand {
    public final int iconRes;
    public final String label;
    public final String command;

    public QuickCommand(int iconRes, String label, String command) {
        this.iconRes = iconRes;
        this.label = label;
        this.command = command;
    }

    // Built-in commands + common skills
    public static QuickCommand[] getCommands() {
        return new QuickCommand[] {
            // Built-in
            new QuickCommand(R.drawable.ic_cmd_status, "Status", "/status"),
            new QuickCommand(R.drawable.ic_cmd_help, "Help", "/help"),

            // Skills
            new QuickCommand(R.drawable.ic_cmd_morning, "Morning briefing", "/skill morning-briefing"),
            new QuickCommand(R.drawable.ic_cmd_sleep, "How did I sleep?", "How did I sleep last night?"),
            new QuickCommand(R.drawable.ic_cmd_food, "Calories today", "How many calories do I have today?"),
            new QuickCommand(R.drawable.ic_cmd_fitness, "Weekly review", "/skill weekly-review"),
            new QuickCommand(R.drawable.ic_cmd_coffee, "Log coffee", "Log a coffee"),
        };
    }
}
