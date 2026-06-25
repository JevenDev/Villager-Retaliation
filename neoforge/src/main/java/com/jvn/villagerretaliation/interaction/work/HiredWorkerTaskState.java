package com.jvn.villagerretaliation.interaction.work;

import java.util.Locale;

public enum HiredWorkerTaskState {
    IDLE("idle"),
    SELECTING_TARGET("selecting_target"),
    MOVING_TO_TARGET("moving_to_target"),
    VALIDATING_TARGET("validating_target"),
    WORKING("working"),
    COLLECTING_OUTPUT("collecting_output"),
    FINDING_CHAIN_TARGET("finding_chain_target"),
    MOVING_TO_STORAGE("moving_to_storage"),
    RETURNING_TO_WORK_AREA("returning_to_work_area"),
    DEPOSITING("depositing"),
    WAITING_FOR_MATERIALS("waiting_for_materials"),
    PAUSED_FULL_INVENTORY("paused_full_inventory"),
    PAUSED_STORAGE_FULL("paused_storage_full"),
    PAUSED_NO_STORAGE("paused_no_storage"),
    FAILED_COOLDOWN("failed_cooldown"),
    AWAITING_INSTRUCTION("awaiting_instruction"),
    NO_WORK_AREA("no_work_area"),
    PAUSED_MISSING_TOOL("paused_missing_tool");

    private final String id;

    HiredWorkerTaskState(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String label() {
        String[] words = this.id.split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    public boolean keepsBlockTarget() {
        return switch (this) {
            case MOVING_TO_TARGET, VALIDATING_TARGET, WORKING, COLLECTING_OUTPUT, FINDING_CHAIN_TARGET, RETURNING_TO_WORK_AREA, FAILED_COOLDOWN -> true;
            default -> false;
        };
    }

    public boolean keepsStorageTarget() {
        return this == MOVING_TO_STORAGE || this == DEPOSITING || this == WAITING_FOR_MATERIALS || this == PAUSED_STORAGE_FULL;
    }

    public boolean isWaitingState() {
        return switch (this) {
            case IDLE, AWAITING_INSTRUCTION, NO_WORK_AREA, WAITING_FOR_MATERIALS,
                    PAUSED_FULL_INVENTORY, PAUSED_STORAGE_FULL, PAUSED_NO_STORAGE,
                    PAUSED_MISSING_TOOL, FAILED_COOLDOWN -> true;
            default -> false;
        };
    }

    public static HiredWorkerTaskState byId(String value) {
        if (value == null || value.isBlank()) {
            return IDLE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        HiredWorkerTaskState alias = alias(normalized);
        if (alias != null) {
            return alias;
        }
        for (HiredWorkerTaskState state : values()) {
            if (state.id.equals(normalized) || state.name().equalsIgnoreCase(normalized)) {
                return state;
            }
        }
        return IDLE;
    }

    private static HiredWorkerTaskState alias(String value) {
        return switch (value) {
            case "finding_work", "finding_target" -> SELECTING_TARGET;
            case "depositing_output", "storage_depositing" -> DEPOSITING;
            case "needs_tool", "missing_tool" -> PAUSED_MISSING_TOOL;
            case "storage_full" -> PAUSED_STORAGE_FULL;
            case "target_unreachable", "unreachable_target" -> FAILED_COOLDOWN;
            case "waiting_for_instruction", "waiting_instruction" -> AWAITING_INSTRUCTION;
            default -> null;
        };
    }
}
