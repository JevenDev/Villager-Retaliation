package com.jvn.villagerretaliation.campaign;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignInvestigationReceipt(UUID operation, Key key, UUID source, String title, String text, long observedAt) {
    public CampaignInvestigationReceipt {
        CampaignStateCodec.require(operation != null && key != null && operation.equals(key.operation()) && source != null
                && title != null && !title.isBlank() && text != null && !text.isBlank() && observedAt >= 0L, "invalid investigation receipt");
    }

    static CampaignInvestigationReceipt create(Key key, UUID source, String title, String text, long observedAt) {
        return new CampaignInvestigationReceipt(key.operation(), key, source, title, text, observedAt);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Operation", operation); tag.putString("Campaign", key.campaign.toString()); tag.putUUID("Instance", key.instance);
        tag.putString("Fingerprint", key.fingerprint); tag.putUUID("Player", key.player); tag.putString("Quest", key.quest.toString());
        tag.putUUID("QuestRun", key.questRun); tag.putString("Objective", key.objective); tag.putString("Evidence", key.evidence);
        tag.putUUID("Source", source); tag.putString("Title", title); tag.putString("Text", text); tag.putLong("ObservedAt", observedAt);
        return tag;
    }

    static CampaignInvestigationReceipt load(CompoundTag tag) {
        for (String field : new String[]{"Campaign", "Fingerprint", "Quest", "Objective", "Evidence", "Title", "Text"})
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_STRING), "invalid investigation text " + field);
        for (String field : new String[]{"Operation", "Instance", "Player", "QuestRun", "Source"})
            CampaignStateCodec.require(tag.hasUUID(field), "invalid investigation identity " + field);
        CampaignStateCodec.require(tag.contains("ObservedAt", Tag.TAG_LONG), "invalid investigation time");
        var key = new Key(ResourceLocation.tryParse(tag.getString("Campaign")), tag.getUUID("Instance"), tag.getString("Fingerprint"),
                tag.getUUID("Player"), ResourceLocation.tryParse(tag.getString("Quest")), tag.getUUID("QuestRun"),
                tag.getString("Objective"), tag.getString("Evidence"));
        return new CampaignInvestigationReceipt(tag.getUUID("Operation"), key, tag.getUUID("Source"),
                tag.getString("Title"), tag.getString("Text"), tag.getLong("ObservedAt"));
    }

    public record Key(ResourceLocation campaign, UUID instance, String fingerprint, UUID player,
            ResourceLocation quest, UUID questRun, String objective, String evidence) {
        public Key {
            CampaignStateCodec.require(campaign != null && instance != null && fingerprint != null && !fingerprint.isBlank()
                    && fingerprint.length() <= 256 && player != null && quest != null && questRun != null
                    && objective != null && !objective.isBlank() && CampaignFactFields.validId(evidence), "invalid investigation ownership");
        }

        UUID operation() {
            var identity = new StringBuilder("campaign_investigation");
            for (String part : new String[]{campaign.toString(), instance.toString(), fingerprint, player.toString(), quest.toString(),
                    questRun.toString(), objective, evidence}) identity.append(part.length()).append(':').append(part);
            return UUID.nameUUIDFromBytes(identity.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
