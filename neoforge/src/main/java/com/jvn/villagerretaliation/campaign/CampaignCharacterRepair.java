package com.jvn.villagerretaliation.campaign;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignCharacterRepair(UUID operation, UUID expected, CampaignCharacterBinding before, CampaignCharacterBinding after,
        UUID operator, String reason, long time) {
    public CampaignCharacterRepair {
        CampaignStateCodec.require(operation != null && expected != null && after != null && operator != null && reason != null
                && !reason.isBlank() && reason.length() <= 256 && time >= 0 && after.boundAt() <= time && !after.lost()
                && (before == null || before.sameOwner(after) && before.boundAt() <= time), "invalid campaign character repair");
    }

    static UUID token(CampaignCharacterBinding binding, UUID repair) {
        UUID state = binding == null ? new UUID(0, 0) : UUID.nameUUIDFromBytes(binding.save().toString().getBytes(StandardCharsets.UTF_8));
        return repair == null ? state : UUID.nameUUIDFromBytes((repair + "/" + state).getBytes(StandardCharsets.UTF_8));
    }

    boolean sameRequest(UUID expected, CampaignCharacterBinding candidate, UUID operator, String reason) {
        return this.expected.equals(expected) && after.sameOwner(candidate) && after.entityId().equals(candidate.entityId())
                && this.operator.equals(operator) && this.reason.equals(reason);
    }

    static boolean sameIdentity(CampaignCharacterBinding first, CampaignCharacterBinding second) {
        return first == null ? second == null : second != null && first.withLoss(false).equals(second.withLoss(false));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Operation", operation);
        tag.putUUID("Expected", expected);
        if (before != null) tag.put("Before", before.save());
        tag.put("After", after.save());
        tag.putUUID("Operator", operator);
        tag.putString("Reason", reason);
        tag.putLong("Time", time);
        return tag;
    }

    static CampaignCharacterRepair load(CompoundTag tag) {
        CampaignStateCodec.require(tag.hasUUID("Operation") && tag.hasUUID("Expected") && tag.hasUUID("Operator")
                && tag.contains("Reason", Tag.TAG_STRING) && tag.contains("Time", Tag.TAG_LONG) && tag.contains("After", Tag.TAG_COMPOUND)
                && (!tag.contains("Before") || tag.contains("Before", Tag.TAG_COMPOUND)), "invalid character repair fields");
        return new CampaignCharacterRepair(tag.getUUID("Operation"), tag.getUUID("Expected"),
                tag.contains("Before") ? CampaignCharacterBinding.load(tag.getCompound("Before")) : null,
                CampaignCharacterBinding.load(tag.getCompound("After")), tag.getUUID("Operator"), tag.getString("Reason"), tag.getLong("Time"));
    }
}
