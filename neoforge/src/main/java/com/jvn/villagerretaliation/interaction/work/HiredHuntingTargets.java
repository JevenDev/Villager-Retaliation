package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredHuntingMode;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class HiredHuntingTargets {
    public static final String HUNT_ANIMALS_TAG = "HuntAnimals";
    public static final String HUNT_HOSTILES_TAG = "HuntHostiles";
    public static final String HUNT_PLAYERS_TAG = "HuntPlayers";

    public static final String ANIMALS = "animals";
    public static final String HOSTILES = "hostiles";
    public static final String PLAYERS = "players";
    public static final String ALL = "all";

    private HiredHuntingTargets() {
    }

    public static void initializeDefaults(CompoundTag state) {
        if (hasTargetToggles(state)) {
            return;
        }

        Selection selection = legacySelection(state);
        state.putBoolean(HUNT_ANIMALS_TAG, selection.animals());
        state.putBoolean(HUNT_HOSTILES_TAG, selection.hostiles());
        state.putBoolean(HUNT_PLAYERS_TAG, selection.players());
    }

    public static Selection fromState(CompoundTag state) {
        initializeDefaults(state);
        return new Selection(
                state.getBoolean(HUNT_ANIMALS_TAG),
                state.getBoolean(HUNT_HOSTILES_TAG),
                state.getBoolean(HUNT_PLAYERS_TAG));
    }

    public static boolean enabled(CompoundTag state, String optionId) {
        Selection selection = fromState(state);
        return switch (normalize(optionId)) {
            case ANIMALS -> selection.animals();
            case HOSTILES -> selection.hostiles();
            case PLAYERS -> selection.players();
            case ALL -> selection.huntsAllNonPlayers();
            default -> false;
        };
    }

    public static ToggleResult toggle(CompoundTag state, String optionId) {
        initializeDefaults(state);
        String normalized = normalize(optionId);
        if (normalized.isBlank()) {
            return ToggleResult.invalidResult();
        }

        if (ALL.equals(normalized)) {
            boolean enabled = !fromState(state).huntsAllNonPlayers();
            state.putBoolean(HUNT_ANIMALS_TAG, enabled);
            state.putBoolean(HUNT_HOSTILES_TAG, enabled);
            return new ToggleResult(normalized, enabled, false);
        }

        String tag = tagFor(normalized);
        if (tag.isBlank()) {
            return ToggleResult.invalidResult();
        }
        boolean enabled = !state.getBoolean(tag);
        state.putBoolean(tag, enabled);
        return new ToggleResult(normalized, enabled, false);
    }

    public static String label(String optionId) {
        return switch (normalize(optionId)) {
            case ANIMALS -> "Hunt animals";
            case HOSTILES -> "Hunt hostiles";
            case PLAYERS -> "Hunt players";
            case ALL -> "Hunt all";
            default -> "Hunting target";
        };
    }

    public static String selectionLabel(CompoundTag state) {
        return fromState(state).label();
    }

    private static boolean hasTargetToggles(CompoundTag state) {
        return state.contains(HUNT_ANIMALS_TAG, Tag.TAG_BYTE)
                || state.contains(HUNT_HOSTILES_TAG, Tag.TAG_BYTE)
                || state.contains(HUNT_PLAYERS_TAG, Tag.TAG_BYTE);
    }

    private static Selection legacySelection(CompoundTag state) {
        HiredHuntingMode huntingMode = HiredHuntingMode.fromState(state);
        return switch (huntingMode) {
            case ANIMALS -> new Selection(true, false, false);
            case HOSTILES -> new Selection(false, true, false);
            case ALL -> new Selection(true, true, false);
        };
    }

    private static String tagFor(String optionId) {
        return switch (optionId) {
            case ANIMALS -> HUNT_ANIMALS_TAG;
            case HOSTILES -> HUNT_HOSTILES_TAG;
            case PLAYERS -> HUNT_PLAYERS_TAG;
            default -> "";
        };
    }

    private static String normalize(String optionId) {
        return optionId == null ? "" : optionId.trim().toLowerCase(Locale.ROOT);
    }

    public record Selection(boolean animals, boolean hostiles, boolean players) {
        public boolean huntsAllNonPlayers() {
            return this.animals && this.hostiles;
        }

        public boolean none() {
            return !this.animals && !this.hostiles && !this.players;
        }

        public String label() {
            if (this.animals && this.hostiles && this.players) {
                return "Hunt all + players";
            }
            if (this.animals && this.hostiles) {
                return "Hunt all";
            }
            if (this.animals && this.players && !this.hostiles) {
                return "Hunt animals + players";
            }
            if (this.hostiles && this.players && !this.animals) {
                return "Hunt hostiles + players";
            }
            if (this.animals) {
                return "Hunt animals";
            }
            if (this.hostiles) {
                return "Hunt hostiles";
            }
            if (this.players) {
                return "Hunt players";
            }
            return "No hunting targets";
        }
    }

    public record ToggleResult(String optionId, boolean enabled, boolean invalid) {
        private static ToggleResult invalidResult() {
            return new ToggleResult("", false, true);
        }
    }
}
