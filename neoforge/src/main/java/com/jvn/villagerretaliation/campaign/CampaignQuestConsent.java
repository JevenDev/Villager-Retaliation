package com.jvn.villagerretaliation.campaign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignQuestConsent(UUID playerId, UUID providerId, Map<ResourceLocation, Entry> quests, Map<UUID, CampaignOutcomeConsent> decisions) {
    public CampaignQuestConsent {
        quests = Map.copyOf(quests);
        decisions = Map.copyOf(decisions);
    }

    public CampaignQuestConsent(UUID playerId, UUID providerId, Map<ResourceLocation, Entry> quests) {
        this(playerId, providerId, quests, Map.of());
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", providerId == null ? 3 : 2);
        tag.putUUID("Player", playerId);
        if (providerId != null) tag.putUUID("Provider", providerId);
        var entries = new CompoundTag();
        quests.forEach((id, entry) -> {
            var value = new CompoundTag();
            value.put("Acceptance", entry.acceptance().save());
            value.put("Progress", entry.progress());
            entries.put(id.toString(), value);
        });
        tag.put("Quests", entries);
        var outcomes = new net.minecraft.nbt.ListTag();
        decisions.values().forEach(value -> outcomes.add(value.save()));
        tag.put("Decisions", outcomes);
        return tag;
    }

    public static CampaignQuestConsent load(CompoundTag tag) {
        if ((tag.getInt("Version") < 1 || tag.getInt("Version") > 3) || !tag.hasUUID("Player")
                || tag.getInt("Version") < 3 && !tag.hasUUID("Provider")
                || tag.contains("Provider") && !tag.hasUUID("Provider")
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
        var decisions = new LinkedHashMap<UUID, CampaignOutcomeConsent>();
        if (tag.getInt("Version") >= 2) {
            if (!(tag.get("Decisions") instanceof net.minecraft.nbt.ListTag list)
                    || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Campaign decision approvals are unreadable.");
            }
            for (Tag raw : list) {
                var decision = CampaignOutcomeConsent.load((CompoundTag) raw);
                if (decisions.putIfAbsent(decision.operationId(), decision) != null) {
                    throw new IllegalArgumentException("Campaign decision approval has a duplicate operation.");
                }
            }
        }
        if (entries.isEmpty() && decisions.isEmpty()) throw new IllegalArgumentException("Campaign consent has no accepted quests or decisions.");
        return new CampaignQuestConsent(tag.getUUID("Player"), tag.hasUUID("Provider") ? tag.getUUID("Provider") : null, entries, decisions);
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
