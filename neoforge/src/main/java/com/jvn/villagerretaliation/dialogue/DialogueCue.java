package com.jvn.villagerretaliation.dialogue;

import java.util.Locale;
import java.util.Set;

public record DialogueCue(Kind kind, double value) {
    public static final int MAX_CUES = 64;
    private static final Set<String> TAGS = Set.of("pause", "agree", "disagree", "look", "reset", "speed");

    public enum Kind { PAUSE, AGREE, DISAGREE, LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN, LOOK_PLAYER, RESET, SPEED }

    public DialogueCue {
        java.util.Objects.requireNonNull(kind);
        if (!Double.isFinite(value) || switch (kind) {
            case PAUSE -> value < 0 || value > 60_000 || value != Math.rint(value);
            case SPEED -> value < 0.1 || value > 10;
            default -> value != 0;
        }) throw new IllegalArgumentException("Invalid dialogue cue value");
    }

    public record At(int offset, DialogueCue cue) {
        public At {
            if (offset < 0) throw new IllegalArgumentException("Negative dialogue cue offset");
            java.util.Objects.requireNonNull(cue);
        }
    }

    public static boolean isTag(String raw) {
        return TAGS.contains(raw.trim().toLowerCase(Locale.ROOT).split("[=:]", 2)[0].trim());
    }

    public static DialogueCue parse(String raw) {
        String[] parts = raw.trim().toLowerCase(Locale.ROOT).split("[=:]", -1);
        String name = parts[0].trim();
        if (!TAGS.contains(name)) return null;
        if (Set.of("agree", "disagree", "reset").contains(name)) {
            if (parts.length != 1) return null;
            return new DialogueCue(Kind.valueOf(name.toUpperCase(Locale.ROOT)), 0);
        }
        if (parts.length != 2) return null;
        String argument = parts[1].trim();
        if (name.equals("look")) {
            Kind kind = switch (argument) {
                case "left" -> Kind.LOOK_LEFT;
                case "right" -> Kind.LOOK_RIGHT;
                case "up" -> Kind.LOOK_UP;
                case "down" -> Kind.LOOK_DOWN;
                case "player" -> Kind.LOOK_PLAYER;
                default -> null;
            };
            return kind == null ? null : new DialogueCue(kind, 0);
        }
        if (!argument.matches("[0-9]+(?:\\.[0-9]+)?")) return null;
        try {
            return new DialogueCue(name.equals("pause") ? Kind.PAUSE : Kind.SPEED, Double.parseDouble(argument));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
