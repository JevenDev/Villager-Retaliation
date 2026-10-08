package com.jvn.villagerretaliation.scene.encounter;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record EncounterBlockOwnership(UUID operation, State state, String diagnostic, Tag preserved) {
    public EncounterBlockOwnership {
        if (state == null || diagnostic == null || diagnostic.length() > 512
                || (state == State.PENDING || state == State.READY) && operation == null)
            throw new IllegalArgumentException("Invalid encounter block ownership.");
        preserved = preserved == null ? null : preserved.copy();
    }

    @Override public Tag preserved() { return preserved == null ? null : preserved.copy(); }
    public static EncounterBlockOwnership legacy() { return new EncounterBlockOwnership(null, State.LEGACY, "", null); }
    public static EncounterBlockOwnership pending() { return new EncounterBlockOwnership(UUID.randomUUID(), State.PENDING, "", null); }
    public EncounterBlockOwnership ready() { return new EncounterBlockOwnership(operation, State.READY, "", null); }
    public EncounterBlockOwnership conflict(String issue) { return new EncounterBlockOwnership(operation, state, issue, preserved); }

    public String issue() {
        if (state == State.LEGACY) return "Legacy encounter block ownership cannot be verified. Use /vr admin scene encounterBlock inspect, then preserve it explicitly or restore matching ownership data.";
        if (state == State.UNREADABLE) return diagnostic;
        return "";
    }

    Tag save() {
        if (preserved != null) return preserved.copy();
        var tag = new CompoundTag();
        tag.putInt("DataVersion", 1);
        tag.putString("State", state.name());
        if (operation != null) tag.putUUID("Operation", operation);
        tag.putString("Diagnostic", diagnostic);
        return tag;
    }

    static EncounterBlockOwnership load(Tag raw) {
        try {
            if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid encounter block ownership fields.");
            if (!tag.contains("DataVersion", Tag.TAG_INT) || tag.getInt("DataVersion") != 1
                    || !tag.contains("State", Tag.TAG_STRING) || !tag.contains("Diagnostic", Tag.TAG_STRING))
                throw new IllegalArgumentException("Unsupported or incomplete encounter block ownership.");
            State state = State.valueOf(tag.getString("State"));
            if (!tag.getAllKeys().equals(state == State.LEGACY ? java.util.Set.of("DataVersion", "State", "Diagnostic")
                    : java.util.Set.of("DataVersion", "State", "Operation", "Diagnostic")) || state == State.UNREADABLE || state != State.LEGACY && !tag.hasUUID("Operation")
                    || state == State.LEGACY && tag.contains("Operation"))
                throw new IllegalArgumentException("Invalid encounter block ownership identity.");
            return new EncounterBlockOwnership(tag.hasUUID("Operation") ? tag.getUUID("Operation") : null, state, tag.getString("Diagnostic"), null);
        } catch (IllegalArgumentException exception) {
            return new EncounterBlockOwnership(null, State.UNREADABLE, "Encounter block ownership is unreadable. Restore compatible scene data.", raw);
        }
    }

    public enum State { LEGACY, PENDING, READY, UNREADABLE }
}
