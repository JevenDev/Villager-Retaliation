package com.jvn.villagerretaliation.campaign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record CampaignQuestAcceptance(UUID requestId, UUID instanceId, String fingerprint, long worldRevision,
        long acceptedAt, Map<String, String> fixedOutcomes) {
    public CampaignQuestAcceptance {
        fixedOutcomes = Map.copyOf(fixedOutcomes);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Request", requestId);
        tag.putUUID("Instance", instanceId);
        tag.putString("Fingerprint", fingerprint);
        tag.putLong("WorldRevision", worldRevision);
        tag.putLong("AcceptedAt", acceptedAt);
        var fixed = new CompoundTag();
        fixedOutcomes.forEach(fixed::putString);
        tag.put("FixedOutcomes", fixed);
        return tag;
    }

    public static CampaignQuestAcceptance load(CompoundTag tag) {
        if (!tag.hasUUID("Request") || !tag.hasUUID("Instance")
                || !tag.contains("Fingerprint", net.minecraft.nbt.Tag.TAG_STRING)
                || !tag.contains("WorldRevision", net.minecraft.nbt.Tag.TAG_LONG)
                || !tag.contains("AcceptedAt", net.minecraft.nbt.Tag.TAG_LONG)
                || !tag.contains("FixedOutcomes", net.minecraft.nbt.Tag.TAG_COMPOUND)) return null;
        Map<String, String> fixed = new LinkedHashMap<>();
        var outcomes = tag.getCompound("FixedOutcomes");
        for (String key : outcomes.getAllKeys()) {
            if (!outcomes.contains(key, net.minecraft.nbt.Tag.TAG_STRING)) return null;
            fixed.put(key, outcomes.getString(key));
        }
        return new CampaignQuestAcceptance(tag.getUUID("Request"), tag.getUUID("Instance"), tag.getString("Fingerprint"),
                tag.getLong("WorldRevision"), tag.getLong("AcceptedAt"), fixed);
    }
}
