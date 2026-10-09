package com.jvn.villagerretaliation.campaign;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignLocationRepair(UUID operation, UUID expected, CampaignLocationBinding before, CampaignLocationBinding after,
        UUID operator, String reason, long time) {
    public CampaignLocationRepair {
        CampaignStateCodec.require(operation != null && expected != null && after != null && operator != null && reason != null
                && !reason.isBlank() && reason.length() <= 256 && time >= 0 && after.boundAt() == time
                && (before == null || before.sameOwner(after) && before.boundAt() <= time),
                "invalid campaign location repair");
    }

    public static UUID token(CampaignLocationBinding binding) {
        return binding == null ? new UUID(0, 0) : UUID.nameUUIDFromBytes(binding.save().toString().getBytes(StandardCharsets.UTF_8));
    }

    public UUID token() {
        return UUID.nameUUIDFromBytes((operation + "/" + token(after)).getBytes(StandardCharsets.UTF_8));
    }

    public boolean sameRequest(UUID expected, CampaignLocationBinding candidate, UUID requestedOperator, String requestedReason) {
        return this.expected.equals(expected) && after.sameOwner(candidate) && after.position().equals(candidate.position())
                && after.targetIdentity().equals(candidate.targetIdentity()) && operator.equals(requestedOperator) && reason.equals(requestedReason);
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

    static CampaignLocationRepair load(CompoundTag tag) {
        CampaignStateCodec.require(tag.hasUUID("Operation") && tag.hasUUID("Expected") && tag.hasUUID("Operator") && tag.contains("Reason", Tag.TAG_STRING)
                && tag.contains("Time", Tag.TAG_LONG) && tag.contains("After", Tag.TAG_COMPOUND)
                && (!tag.contains("Before") || tag.contains("Before", Tag.TAG_COMPOUND)), "invalid location repair fields");
        return new CampaignLocationRepair(tag.getUUID("Operation"), tag.getUUID("Expected"), tag.contains("Before") ? CampaignLocationBinding.load(tag.getCompound("Before")) : null,
                CampaignLocationBinding.load(tag.getCompound("After")), tag.getUUID("Operator"), tag.getString("Reason"), tag.getLong("Time"));
    }
}
