package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationCheckCodec;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import static com.jvn.villagerretaliation.campaign.CampaignTransaction.require;

final class CampaignTransactionCodec {
    private CampaignTransactionCodec() { }

    static CompoundTag save(CampaignTransaction.Receipt receipt) {
        var tag = new CompoundTag();
        var terms = receipt.terms();
        var key = terms.key();
        tag.putUUID("Operation", receipt.operation());
        tag.putUUID("Token", receipt.token());
        tag.putString("Campaign", key.campaign().toString());
        tag.putUUID("Instance", key.instance());
        tag.putUUID("Player", key.player());
        tag.putString("Quest", key.quest().toString());
        tag.putUUID("QuestRun", key.questRun());
        tag.putString("Transaction", key.transaction());
        tag.putInt("DefinitionRevision", terms.definitionRevision());
        tag.putString("Fingerprint", terms.fingerprint());
        tag.putString("TitleKey", terms.titleKey());
        tag.putString("Title", terms.title());
        tag.putString("Character", terms.character());
        tag.putUUID("Seller", terms.seller());
        tag.putString("Dimension", terms.dimension().toString());
        tag.putString("Item", terms.item().toString());
        tag.put("Unit", terms.unit());
        tag.putInt("StackSize", terms.stackSize());
        tag.putInt("Count", terms.count());
        if (terms.bargainedCount() != null) tag.putInt("BargainedCount", terms.bargainedCount());
        tag.putLong("CapturedAt", terms.capturedAt());
        tag.putString("Phase", receipt.phase().name());
        tag.putLong("UpdatedAt", receipt.updatedAt());
        if (receipt.negotiation() != null) {
            var negotiation = new CompoundTag();
            var source = receipt.negotiation().source();
            negotiation.putUUID("Session", source.session());
            negotiation.putString("Conversation", source.conversation().toString());
            negotiation.putInt("Revision", source.revision());
            negotiation.putString("Fingerprint", source.fingerprint());
            negotiation.putString("Check", source.check());
            negotiation.put("Result", ConversationCheckCodec.save(receipt.negotiation().check()));
            tag.put("Negotiation", negotiation);
        }
        return tag;
    }

    static CampaignTransaction.Receipt load(CompoundTag tag) {
        var key = new CampaignTransaction.Key(resource(tag, "Campaign"), uuid(tag, "Instance"), uuid(tag, "Player"),
                resource(tag, "Quest"), uuid(tag, "QuestRun"), text(tag, "Transaction"));
        require(key.operation().equals(uuid(tag, "Operation")), "Campaign transaction operation differs from its identity");
        var terms = new CampaignTransaction.Terms(key, integer(tag, "DefinitionRevision"), text(tag, "Fingerprint"), text(tag, "TitleKey"),
                text(tag, "Title"), text(tag, "Character"), uuid(tag, "Seller"), resource(tag, "Dimension"), resource(tag, "Item"),
                compound(tag, "Unit"), integer(tag, "StackSize"), integer(tag, "Count"),
                tag.contains("BargainedCount") ? integer(tag, "BargainedCount") : null, number(tag, "CapturedAt"));
        CampaignTransaction.Negotiation negotiation = null;
        if (tag.contains("Negotiation")) {
            var value = compound(tag, "Negotiation");
            var source = new CampaignTransaction.Source(uuid(value, "Session"), resource(value, "Conversation"), integer(value, "Revision"),
                    text(value, "Fingerprint"), text(value, "Check"));
            negotiation = new CampaignTransaction.Negotiation(source, ConversationCheckCodec.load(compound(value, "Result")));
        }
        return new CampaignTransaction.Receipt(terms, uuid(tag, "Token"), negotiation,
                CampaignTransaction.Phase.valueOf(text(tag, "Phase")), number(tag, "UpdatedAt"));
    }

    private static UUID uuid(CompoundTag tag, String field) {
        require(tag.hasUUID(field), "Missing campaign transaction identity: " + field);
        return tag.getUUID(field);
    }

    private static String text(CompoundTag tag, String field) {
        require(tag.contains(field, Tag.TAG_STRING), "Missing campaign transaction text: " + field);
        return tag.getString(field);
    }

    private static ResourceLocation resource(CompoundTag tag, String field) {
        var value = ResourceLocation.tryParse(text(tag, field));
        require(value != null, "Invalid campaign transaction resource: " + field);
        return value;
    }

    private static int integer(CompoundTag tag, String field) {
        require(tag.contains(field, Tag.TAG_INT), "Missing campaign transaction number: " + field);
        return tag.getInt(field);
    }

    private static long number(CompoundTag tag, String field) {
        require(tag.contains(field, Tag.TAG_LONG), "Missing campaign transaction time: " + field);
        return tag.getLong(field);
    }

    private static CompoundTag compound(CompoundTag tag, String field) {
        require(tag.contains(field, Tag.TAG_COMPOUND), "Missing campaign transaction data: " + field);
        return tag.getCompound(field);
    }
}
