package io.github.sh4peshifting.panelawake;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses observed system state, never inferring physical power from logical display state. */
public final class StatusParser {
    private StatusParser() {}

    public record State(String wakefulness, Boolean powered, int staySetting,
                        String physicalId, int panelMode) {
        public boolean awake() { return "Awake".equals(wakefulness); }
    }

    public static State parse(String power, String surface, int stay) {
        String wake = value(power, "(?m)^\\s*mWakefulness=(\\w+)");
        String powered = value(power, "(?m)^\\s*mIsPowered=(true|false)");
        List<Panel> panels = new ArrayList<>();
        Panel current = null;
        for (String line : surface.split("\\R")) {
            Matcher header = Pattern.compile("DisplayDevice\\{(\\d+),([^}]*)}").matcher(line);
            if (header.find()) {
                current = null;
                String flags = header.group(2).toLowerCase(java.util.Locale.ROOT);
                if (flags.contains("internal")) {
                    current = new Panel(header.group(1), flags.contains("primary"));
                    panels.add(current);
                }
            } else if (line.trim().startsWith("+ ")) {
                current = null;
            }
            if (current != null && line.contains("powerMode=")) {
                current.mode = parseMode(line);
            }
        }
        Panel selected = null;
        for (Panel p : panels) {
            if (p.primary) {
                if (selected != null) { selected = null; break; }
                selected = p;
            }
        }
        if (selected == null && panels.size() == 1) selected = panels.get(0);
        return new State(wake.isEmpty() ? "Unknown" : wake,
                powered.isEmpty() ? null : Boolean.valueOf(powered), stay,
                selected == null ? "" : selected.id, selected == null ? -1 : selected.mode);
    }

    private static int parseMode(String line) {
        String value = line.substring(line.indexOf("powerMode=") + 10).trim();
        Matcher number = Pattern.compile("^(?:[A-Za-z_]+\\s*\\()?([0-4])(?:\\)|\\s|$)").matcher(value);
        if (number.find()) return Integer.parseInt(number.group(1));
        return switch (value.split("[\\s(]", 2)[0].toUpperCase(java.util.Locale.ROOT)) {
            case "OFF" -> 0;
            case "DOZE" -> 1;
            case "ON", "NORMAL" -> 2;
            case "DOZE_SUSPEND" -> 3;
            case "ON_SUSPEND" -> 4;
            default -> -1;
        };
    }

    private static String value(String input, String regex) {
        Matcher m = Pattern.compile(regex).matcher(input);
        return m.find() ? m.group(1) : "";
    }

    private static final class Panel {
        final String id;
        final boolean primary;
        int mode = -1;
        Panel(String id, boolean primary) { this.id = id; this.primary = primary; }
    }
}
