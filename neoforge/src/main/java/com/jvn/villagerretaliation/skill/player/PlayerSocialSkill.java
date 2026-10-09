package com.jvn.villagerretaliation.skill.player;

import java.util.Locale;

public enum PlayerSocialSkill {
    PERSUASION, INTIMIDATION, DECEPTION, INSIGHT;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static PlayerSocialSkill parse(String id) {
        for (var skill : values()) if (skill.id().equals(id)) return skill;
        throw new IllegalArgumentException("Unknown player social skill: " + id);
    }
}
