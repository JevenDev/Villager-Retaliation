package com.jvn.villagerretaliation.social;

public enum VillagerRelationshipStage {
    CRUSH("crush", "Crush", true, false),
    DATING("dating", "Dating", true, true),
    ENGAGED("engaged", "Engaged", true, true),
    MARRIED("married", "Married", true, true),
    SEPARATED("separated", "Separated", false, false),
    WIDOWED("widowed", "Widowed", false, false);

    private final String serializedName;
    private final String displayName;
    private final boolean active;
    private final boolean exclusive;

    VillagerRelationshipStage(String serializedName, String displayName, boolean active, boolean exclusive) {
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.active = active;
        this.exclusive = exclusive;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String displayName() {
        return this.displayName;
    }

    public boolean active() {
        return this.active;
    }

    public boolean exclusive() {
        return this.exclusive;
    }

    public static VillagerRelationshipStage bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (VillagerRelationshipStage stage : values()) {
            if (stage.serializedName.equals(value) || stage.name().equalsIgnoreCase(value)) {
                return stage;
            }
        }
        return null;
    }
}
