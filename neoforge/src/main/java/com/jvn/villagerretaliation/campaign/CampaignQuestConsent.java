package com.jvn.villagerretaliation.campaign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignQuestConsent(UUID playerId, UUID providerId, Map<ResourceLocation, Entry> quests) {
    public CampaignQuestConsent {
        quests = Map.copyOf(quests);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Player", playerId);
        tag.putUUID("Provider", providerId);
        var entries = new CompoundTag();
        quests.forEach((id, entry) -> {
            var value = new CompoundTag();
            value.put("Acceptance", entry.acceptance().save());
            value.put("Progress", entry.progress());
            entries.put(id.toString(), value);
        });
        tag.put("Quests", entries);
        return tag;
    }

    public static CampaignQuestConsent load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !tag.hasUUID("Player") || !tag.hasUUID("Provider")
                || !tag.contains("Quests", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Campaign consent is unreadable or uses an unsupported version.");
        }
        var entries = new LinkedHashMap<ResourceLocation, Entry>();
        var quests = tag.getCompound("Quests");
        for (String key : quests.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            var value = quests.getCompound(key);
            var acceptance = CampaignQuestAcceptance.load(value.getCompound("Acceptance"));
            if (id == null || acceptance == null || !value.contains("Progress", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Campaign consent has an invalid quest entry: " + key);
            }
            entries.put(id, new Entry(acceptance, value.getCompound("Progress")));
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("Campaign consent has no accepted quests.");
        return new CampaignQuestConsent(tag.getUUID("Player"), tag.getUUID("Provider"), entries);
    }

    public record Entry(CampaignQuestAcceptance acceptance, CompoundTag progress) {
        public Entry {
            progress = progress.copy();
        }

        @Override
        public CompoundTag progress() {
            return progress.copy();
        }
    }
}
