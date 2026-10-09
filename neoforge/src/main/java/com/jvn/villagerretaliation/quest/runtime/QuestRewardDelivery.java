package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record QuestRewardDelivery(QuestDefinition.Rewards rewards, int nextAction) {
    public QuestRewardDelivery {
        if (rewards == null || nextAction < 0 || nextAction > 5) {
            throw new IllegalArgumentException("Invalid quest reward delivery checkpoint");
        }
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("NextAction", nextAction);
        tag.putInt("Experience", rewards.experience());
        tag.putInt("Reputation", rewards.reputation());
        tag.putInt("Gossip", rewards.gossipReputation());
        if (rewards.lootTable() != null) tag.putString("Loot", rewards.lootTable().toString());
        if (rewards.memoryEvent() != null) tag.putString("Memory", rewards.memoryEvent().toString());
        tag.putString("MemoryScope", rewards.memoryScope().name());
        return tag;
    }

    public static QuestRewardDelivery load(CompoundTag tag) {
        for (String key : java.util.List.of("NextAction", "Experience", "Reputation", "Gossip")) {
            if (!tag.contains(key, Tag.TAG_INT)) throw new IllegalArgumentException("Invalid saved reward field: " + key);
        }
        if (!tag.contains("MemoryScope", Tag.TAG_STRING)
                || tag.contains("Loot") && !tag.contains("Loot", Tag.TAG_STRING)
                || tag.contains("Memory") && !tag.contains("Memory", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Invalid saved reward plan");
        }
        if (tag.contains("Loot") && ResourceLocation.tryParse(tag.getString("Loot")) == null
                || tag.contains("Memory") && ResourceLocation.tryParse(tag.getString("Memory")) == null) {
            throw new IllegalArgumentException("Invalid saved reward resource ID");
        }
        return new QuestRewardDelivery(new QuestDefinition.Rewards(
                tag.getInt("Experience"), tag.getInt("Reputation"), tag.getInt("Gossip"),
                tag.contains("Loot") ? ResourceLocation.parse(tag.getString("Loot")) : null,
                tag.contains("Memory") ? ResourceLocation.parse(tag.getString("Memory")) : null,
                VillageEventMemory.MemoryScope.valueOf(tag.getString("MemoryScope"))), tag.getInt("NextAction"));
    }
}
