package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignRewardDefinition;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignRewardReceipt(UUID operation, UUID token, ResourceLocation campaign, UUID instance,
        int definitionRevision, String fingerprint, UUID player, ResourceLocation quest, UUID run, String reward,
        String authoredOperation, CampaignRewardDefinition.Scope scope, String milestone, int experience,
        List<CompoundTag> items, long preparedAt, boolean delivered) {
    public CampaignRewardReceipt {
        if (operation == null || token == null || campaign == null || instance == null || definitionRevision < 1
                || fingerprint == null || fingerprint.isBlank() || player == null || quest == null || run == null
                || !CampaignFactFields.validId(reward) || !CampaignFactFields.validId(authoredOperation) || scope == null
                || milestone == null || (scope == CampaignRewardDefinition.Scope.PERSONAL ? !milestone.isEmpty() : !CampaignFactFields.validId(milestone))
                || experience < 0 || items == null || items.size() > 256 || preparedAt < 0L) {
            throw new IllegalArgumentException("Invalid campaign reward receipt.");
        }
        items = items.stream().map(CompoundTag::copy).toList();
    }

    @Override
    public List<CompoundTag> items() { return items.stream().map(CompoundTag::copy).toList(); }

    public boolean sameGrant(CampaignRewardReceipt other) {
        return campaign.equals(other.campaign) && instance.equals(other.instance) && quest.equals(other.quest) && reward.equals(other.reward)
                && (scope == CampaignRewardDefinition.Scope.SHARED || other.scope == CampaignRewardDefinition.Scope.SHARED || player.equals(other.player));
    }

    public boolean sameIntent(CampaignRewardReceipt other) {
        return campaign.equals(other.campaign) && instance.equals(other.instance) && definitionRevision == other.definitionRevision
                && fingerprint.equals(other.fingerprint) && quest.equals(other.quest) && reward.equals(other.reward)
                && authoredOperation.equals(other.authoredOperation) && scope == other.scope && milestone.equals(other.milestone)
                && experience == other.experience
                && (scope != CampaignRewardDefinition.Scope.PERSONAL || player.equals(other.player))
                && (scope != CampaignRewardDefinition.Scope.PARTICIPANTS || player.equals(other.player));
    }

    public CampaignRewardReceipt finish() {
        return new CampaignRewardReceipt(operation, token, campaign, instance, definitionRevision, fingerprint, player, quest,
                run, reward, authoredOperation, scope, milestone, experience, items, preparedAt, true);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Operation", operation);
        tag.putUUID("Token", token);
        tag.putString("Campaign", campaign.toString());
        tag.putUUID("Instance", instance);
        tag.putInt("DefinitionRevision", definitionRevision);
        tag.putString("Fingerprint", fingerprint);
        tag.putUUID("Player", player);
        tag.putString("Quest", quest.toString());
        tag.putUUID("Run", run);
        tag.putString("Reward", reward);
        tag.putString("AuthoredOperation", authoredOperation);
        tag.putString("Scope", scope.name());
        tag.putString("Milestone", milestone);
        tag.putInt("Experience", experience);
        var list = new ListTag();
        items.forEach(item -> list.add(item.copy()));
        tag.put("Items", list);
        tag.putLong("PreparedAt", preparedAt);
        tag.putBoolean("Delivered", delivered);
        return tag;
    }

    public static CampaignRewardReceipt load(CompoundTag tag) {
        for (String key : List.of("Operation", "Token", "Instance", "Player", "Run")) CampaignStateCodec.require(tag.hasUUID(key), "missing reward identity " + key);
        for (String key : List.of("Campaign", "Fingerprint", "Quest", "Reward", "AuthoredOperation", "Scope", "Milestone")) CampaignStateCodec.require(tag.contains(key, Tag.TAG_STRING), "missing reward text " + key);
        CampaignStateCodec.require(tag.contains("DefinitionRevision", Tag.TAG_INT) && tag.contains("Experience", Tag.TAG_INT)
                && tag.contains("PreparedAt", Tag.TAG_LONG) && tag.contains("Delivered", Tag.TAG_BYTE)
                && (tag.getByte("Delivered") == 0 || tag.getByte("Delivered") == 1), "invalid reward receipt fields");
        var items = CampaignStateCodec.list(tag, "Items", Tag.TAG_COMPOUND).stream().map(value -> (CompoundTag) value).toList();
        return new CampaignRewardReceipt(tag.getUUID("Operation"), tag.getUUID("Token"), ResourceLocation.tryParse(tag.getString("Campaign")),
                tag.getUUID("Instance"), tag.getInt("DefinitionRevision"), tag.getString("Fingerprint"), tag.getUUID("Player"),
                ResourceLocation.tryParse(tag.getString("Quest")), tag.getUUID("Run"), tag.getString("Reward"), tag.getString("AuthoredOperation"),
                CampaignRewardDefinition.Scope.valueOf(tag.getString("Scope")), tag.getString("Milestone"), tag.getInt("Experience"),
                items, tag.getLong("PreparedAt"), tag.getBoolean("Delivered"));
    }
}
