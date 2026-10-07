package com.jvn.villagerretaliation.campaign;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignOutcomeConsent(ResourceLocation questId, ResourceLocation campaignId, UUID instanceId,
        String fingerprint, long worldRevision, UUID questRunId, String stage, long stageRevision,
        UUID operationId, CampaignOutcomeAction action) {
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Quest", questId.toString());
        tag.putString("Campaign", campaignId.toString());
        tag.putUUID("Instance", instanceId);
        tag.putString("Fingerprint", fingerprint);
        tag.putLong("WorldRevision", worldRevision);
        tag.putUUID("QuestRun", questRunId);
        tag.putString("Stage", stage);
        tag.putLong("StageRevision", stageRevision);
        tag.putUUID("Operation", operationId);
        tag.put("Action", action.save());
        return tag;
    }

    public static CampaignOutcomeConsent load(CompoundTag tag) {
        var quest = ResourceLocation.tryParse(tag.getString("Quest"));
        var campaign = ResourceLocation.tryParse(tag.getString("Campaign"));
        if (quest == null || campaign == null || !tag.hasUUID("Instance") || !tag.hasUUID("QuestRun") || !tag.hasUUID("Operation")
                || !tag.contains("Fingerprint", Tag.TAG_STRING) || tag.getString("Fingerprint").isBlank()
                || !tag.contains("WorldRevision", Tag.TAG_LONG) || tag.getLong("WorldRevision") < 0
                || !tag.contains("Stage", Tag.TAG_STRING) || tag.getString("Stage").isBlank()
                || !tag.contains("StageRevision", Tag.TAG_LONG) || tag.getLong("StageRevision") < 0
                || !tag.contains("Action", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Campaign decision approval is unreadable.");
        return new CampaignOutcomeConsent(quest, campaign, tag.getUUID("Instance"), tag.getString("Fingerprint"),
                tag.getLong("WorldRevision"), tag.getUUID("QuestRun"), tag.getString("Stage"), tag.getLong("StageRevision"),
                tag.getUUID("Operation"), CampaignOutcomeAction.load(tag.getCompound("Action")));
    }
}
