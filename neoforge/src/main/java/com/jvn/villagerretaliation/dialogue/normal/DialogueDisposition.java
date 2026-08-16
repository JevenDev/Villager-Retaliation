package com.jvn.villagerretaliation.dialogue.normal;

public enum DialogueDisposition {
    FRIENDLY,
    RESPECTFUL,
    NEUTRAL,
    CAUTIOUS,
    RUDE,
    HOSTILE,
    FEARFUL;

    public String displayName() {
        String lowerName = this.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lowerName.charAt(0)) + lowerName.substring(1);
    }
}
