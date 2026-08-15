package com.jvn.villagerretaliation.quest.content.reward;

import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Immutable bundled reward index owned by one quest-content snapshot. */
public record QuestRewardCatalog(Map<ResourceLocation, BundledQuestReward> bundled) {
    public QuestRewardCatalog {
        bundled = bundled == null || bundled.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(bundled));
    }

    public static QuestRewardCatalog empty() {
        return new QuestRewardCatalog(Map.of());
    }

    public static QuestRewardCatalog fromBundles(
            Map<QuestBundlePath.Owner, QuestBundleTransactions.EffectiveBundle> bundles) {
        Map<ResourceLocation, BundledQuestReward> rewards = new LinkedHashMap<>();
        if (bundles != null) {
            bundles.values().forEach(bundle -> rewards.putAll(bundle.rewards()));
        }
        return new QuestRewardCatalog(rewards);
    }

    public Optional<BundledQuestReward> bundled(ResourceLocation id) {
        return Optional.ofNullable(id == null ? null : this.bundled.get(id));
    }
}
