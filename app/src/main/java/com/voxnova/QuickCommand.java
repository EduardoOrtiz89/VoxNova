package com.voxnova;

public class QuickCommand {
    public final String icon;
    public final String label;
    public final String command;

    public QuickCommand(String icon, String label, String command) {
        this.icon = icon;
        this.label = label;
        this.command = command;
    }

    // Built-in commands + Eduardo's common skills
    public static QuickCommand[] getCommands() {
        return new QuickCommand[] {
            // Built-in
            new QuickCommand("📊", "Status", "/status"),
            new QuickCommand("❓", "Ayuda", "/help"),
            
            // Eduardo's skills
            new QuickCommand("☀️", "Briefing matutino", "/skill morning-briefing"),
            new QuickCommand("😴", "¿Cómo dormí?", "¿Cómo dormí anoche?"),
            new QuickCommand("🍽️", "Calorías del día", "¿Cuántas calorías llevo hoy?"),
            new QuickCommand("💪", "Revisión semanal", "/skill weekly-review"),
            new QuickCommand("☕", "Registrar café", "Registra un café"),
        };
    }
}
