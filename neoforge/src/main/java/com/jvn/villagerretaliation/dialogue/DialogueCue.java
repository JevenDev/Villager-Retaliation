package com.jvn.villagerretaliation.dialogue;

import java.util.Locale;
import java.util.Set;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public record DialogueCue(Kind kind, double value, ResourceLocation particle, Sound sound, String target) {
    public static final int MAX_CUES = 64;
    public static final int MAX_PARTICLE_AMOUNT = 64;
    private static final Set<String> TAGS = Set.of("pause", "agree", "disagree", "look", "reset", "speed", "particle", "sound");

    public enum Kind { PAUSE, AGREE, DISAGREE, LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN, LOOK_PLAYER, RESET, SPEED, PARTICLE, SOUND, LOOK_TARGET }

    public DialogueCue {
        java.util.Objects.requireNonNull(kind);
        if (!Double.isFinite(value) || switch (kind) {
            case PAUSE -> value < 0 || value > 60_000 || value != Math.rint(value);
            case SPEED -> value < 0.1 || value > 10;
            case PARTICLE -> value < 1 || value > MAX_PARTICLE_AMOUNT || value != Math.rint(value);
            default -> value != 0;
        }) throw new IllegalArgumentException("Invalid dialogue cue value");
        if (kind == Kind.LOOK_TARGET ? !validTarget(target) : target != null)
            throw new IllegalArgumentException("Invalid dialogue gaze target");
        if ((kind == Kind.PARTICLE) != (particle != null))
            throw new IllegalArgumentException("Particle resource must match dialogue cue kind");
        if ((kind == Kind.SOUND) != (sound != null))
            throw new IllegalArgumentException("Sound parameters must match dialogue cue kind");
    }

    public DialogueCue(Kind kind, double value, ResourceLocation particle, Sound sound) {
        this(kind, value, particle, sound, null);
    }

    public static boolean validTarget(String target) {
        return target != null && (target.equals("nearest_participant")
                || target.matches("role:[a-z0-9][a-z0-9_./-]{0,255}"));
    }

    public DialogueCue(Kind kind, double value, ResourceLocation particle) {
        this(kind, value, particle, null);
    }

    public DialogueCue(Kind kind, double value) {
        this(kind, value, null, null);
    }

    public record Sound(ResourceLocation id, float volume, float pitch) {
        public Sound {
            java.util.Objects.requireNonNull(id);
            if (!Float.isFinite(volume) || volume < 0 || volume > 1
                    || !Float.isFinite(pitch) || pitch < 0.5F || pitch > 2)
                throw new IllegalArgumentException("Invalid dialogue sound volume or pitch");
        }
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

    private static DialogueCue parseParticle(String raw) {
        var matcher = java.util.regex.Pattern.compile(
                "particle[=:]([a-z0-9_.-]+(?::[a-z0-9/._-]+)?)(?:\\s+amount[=:]([0-9]+))?").matcher(raw);
        if (!matcher.matches()) return null;
        var resource = ResourceLocation.tryParse(matcher.group(1));
        if (resource == null || !(BuiltInRegistries.PARTICLE_TYPE.get(resource) instanceof SimpleParticleType)) return null;
        try {
            int amount = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            return new DialogueCue(Kind.PARTICLE, amount, resource);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static DialogueCue parseSound(String raw) {
        String[] tokens = raw.split("\\s+");
        String[] resource = tokens[0].split("[=:]", 2);
        if (resource.length != 2 || !resource[1].matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+")) return null;
        var id = ResourceLocation.tryParse(resource[1]);
        if (id == null) return null;
        float volume = 1;
        float pitch = 1;
        var seen = new java.util.HashSet<String>();
        try {
            for (int index = 1; index < tokens.length; index++) {
                String[] argument = tokens[index].split("[=:]", -1);
                if (argument.length != 2 || !seen.add(argument[0])
                        || !argument[1].matches("[0-9]+(?:\\.[0-9]+)?")) return null;
                switch (argument[0]) {
                    case "volume" -> volume = Float.parseFloat(argument[1]);
                    case "pitch" -> pitch = Float.parseFloat(argument[1]);
                    default -> { return null; }
                }
            }
            return new DialogueCue(Kind.SOUND, 0, null, new Sound(id, volume, pitch));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static DialogueCue parse(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.split("[=:]", 2)[0].trim().equals("particle")) return parseParticle(normalized);
        if (normalized.split("[=:]", 2)[0].trim().equals("sound")) return parseSound(normalized);
        if (normalized.matches("look[=:].*") && validTarget(normalized.substring(5).trim()))
            return new DialogueCue(Kind.LOOK_TARGET, 0, null, null, normalized.substring(5).trim());
        String[] parts = normalized.split("[=:]", -1);
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
