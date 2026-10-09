package com.jvn.villagerretaliation.skill.player;

import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record SocialProgressionDefinition(ResourceLocation id, int revision, String fingerprint,
        Map<PlayerSocialSkill, List<Level>> skills, QuestBundlePath.Owner owner,
        QuestBundleTransactions.DefinitionSource source) {
    public SocialProgressionDefinition {
        var copy = new LinkedHashMap<PlayerSocialSkill, List<Level>>();
        skills.forEach((skill, levels) -> copy.put(skill, List.copyOf(levels)));
        skills = Collections.unmodifiableMap(copy);
    }

    public Rank rank(PlayerSocialSkill skill, long experience) {
        if (experience < 0 || !skills.containsKey(skill)) throw new IllegalArgumentException("Invalid social skill progression query");
        var levels = skills.get(skill);
        int rank = 0;
        while (rank + 1 < levels.size() && experience >= levels.get(rank + 1).experience()) rank++;
        return new Rank(rank, levels.get(rank).experience(), levels.get(rank).bonus());
    }

    public record Level(int experience, int bonus) { }
    public record Rank(int level, int threshold, int bonus) { }
}
