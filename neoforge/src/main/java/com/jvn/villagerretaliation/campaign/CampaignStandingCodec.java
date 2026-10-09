package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class CampaignStandingCodec {
    private CampaignStandingCodec() { }

    static CompoundTag save(CampaignStanding.Receipt receipt) {
        var tag = new CompoundTag();
        var change = receipt.change();
        var subject = receipt.subject();
        var key = subject.key();
        tag.putUUID("Operation", receipt.operation());
        tag.putString("Campaign", key.campaign().toString());
        tag.putUUID("Instance", key.instance());
        tag.putUUID("Player", key.player());
        tag.putString("Scope", key.scope().name());
        tag.putString("Subject", key.subject());
        if (key.character() != null) tag.putUUID("Character", key.character());
        tag.putString("Quest", change.quest().toString());
        tag.putUUID("QuestRun", change.questRun());
        tag.putString("OperationId", change.operationId());
        tag.putInt("Amount", change.amount());
        tag.putInt("DefinitionRevision", subject.definitionRevision());
        tag.putString("Fingerprint", subject.fingerprint());
        tag.putString("Title", subject.title());
        tag.putInt("Minimum", subject.minimum());
        tag.putInt("Maximum", subject.maximum());
        tag.putInt("Initial", subject.initial());
        tag.putInt("Before", receipt.before());
        tag.putInt("After", receipt.after());
        tag.putLong("Revision", receipt.revision());
        tag.putLong("CommittedAt", receipt.committedAt());
        return tag;
    }

    static CampaignStanding.Receipt load(CompoundTag tag) {
        for (String field : java.util.List.of("Operation", "Instance", "Player", "QuestRun"))
            CampaignStanding.require(tag.hasUUID(field), "Missing campaign standing identity: " + field);
        if (tag.contains("Character")) CampaignStanding.require(tag.hasUUID("Character"), "Invalid standing character identity");
        var key = new CampaignStanding.Key(ResourceLocation.tryParse(text(tag, "Campaign")), tag.getUUID("Instance"), tag.getUUID("Player"),
                Scope.valueOf(text(tag, "Scope")), text(tag, "Subject"), tag.hasUUID("Character") ? tag.getUUID("Character") : null);
        var subject = new CampaignStanding.Subject(key, integer(tag, "DefinitionRevision"), text(tag, "Fingerprint"), text(tag, "Title"),
                integer(tag, "Minimum"), integer(tag, "Maximum"), integer(tag, "Initial"));
        var change = new CampaignStanding.Change(key.campaign(), key.instance(), key.player(), ResourceLocation.tryParse(text(tag, "Quest")),
                tag.getUUID("QuestRun"), key.scope(), key.subject(), integer(tag, "Amount"), text(tag, "OperationId"));
        CampaignStanding.require(change.operation().equals(tag.getUUID("Operation")), "Standing operation differs from its authored identity");
        return new CampaignStanding.Receipt(change, subject, integer(tag, "Before"), integer(tag, "After"),
                number(tag, "Revision"), number(tag, "CommittedAt"));
    }

    private static String text(CompoundTag tag, String field) {
        CampaignStanding.require(tag.contains(field, Tag.TAG_STRING), "Missing campaign standing text: " + field);
        return tag.getString(field);
    }
    private static int integer(CompoundTag tag, String field) {
        CampaignStanding.require(tag.contains(field, Tag.TAG_INT), "Missing campaign standing number: " + field);
        return tag.getInt(field);
    }
    private static long number(CompoundTag tag, String field) {
        CampaignStanding.require(tag.contains(field, Tag.TAG_LONG), "Missing campaign standing revision or time: " + field);
        return tag.getLong(field);
    }
}
