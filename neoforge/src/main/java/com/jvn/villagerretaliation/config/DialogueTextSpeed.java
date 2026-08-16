package com.jvn.villagerretaliation.config;

public enum DialogueTextSpeed {
    SLOW("Slow", 60L),
    MEDIUM("Medium", 35L),
    FAST("Fast", 15L),
    INSTANT("Instant", 0L);

    private final String label;
    private final long millisPerCharacter;

    DialogueTextSpeed(String label, long millisPerCharacter) {
        this.label = label;
        this.millisPerCharacter = millisPerCharacter;
    }

    public long millisPerCharacter() {
        return this.millisPerCharacter;
    }

    public boolean instant() {
        return this.millisPerCharacter <= 0L;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
