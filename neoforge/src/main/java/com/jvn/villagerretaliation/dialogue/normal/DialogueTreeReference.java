package com.jvn.villagerretaliation.dialogue.normal;

import net.minecraft.resources.ResourceLocation;

public record DialogueTreeReference(ResourceLocation treeId, String entryId, String responseId, Kind kind) {
    public static final DialogueTreeReference EMPTY = new DialogueTreeReference(null, "", "", Kind.NONE);

    public static DialogueTreeReference entry(ResourceLocation treeId, String entryId) {
        return new DialogueTreeReference(treeId, entryId == null ? "" : entryId, "", Kind.ENTRY);
    }

    public static DialogueTreeReference response(ResourceLocation treeId, String responseId) {
        return new DialogueTreeReference(treeId, "", responseId == null ? "" : responseId, Kind.RESPONSE);
    }

    public boolean isEmpty() {
        return this.treeId == null || this.kind == Kind.NONE;
    }

    public boolean isEntry() {
        return this.treeId != null && this.kind == Kind.ENTRY && !this.entryId.isBlank();
    }

    public boolean isResponse() {
        return this.treeId != null && this.kind == Kind.RESPONSE && !this.responseId.isBlank();
    }

    public enum Kind {
        NONE,
        ENTRY,
        RESPONSE
    }
}
