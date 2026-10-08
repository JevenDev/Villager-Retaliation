package com.jvn.villagerretaliation.campaign;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignEvidenceBinding(ResourceLocation campaign, UUID instance, int definitionRevision, String fingerprint,
        String evidence, UUID entity, ResourceLocation dimension, BlockPos position, long boundAt) {
    public CampaignEvidenceBinding {
        CampaignStateCodec.require(campaign != null && instance != null && definitionRevision > 0
                && fingerprint != null && !fingerprint.isBlank() && fingerprint.length() <= 256
                && CampaignFactFields.validId(evidence) && entity != null && dimension != null && position != null && boundAt >= 0L,
                "invalid campaign evidence binding");
        position = position.immutable();
        CampaignStateCodec.require(Math.abs((long) position.getX()) <= 30_000_000L && Math.abs((long) position.getY()) <= 30_000_000L
                && Math.abs((long) position.getZ()) <= 30_000_000L, "campaign evidence is outside world bounds");
    }

    public Key key() { return new Key(campaign, evidence); }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Campaign", campaign.toString()); tag.putUUID("Instance", instance);
        tag.putInt("DefinitionRevision", definitionRevision); tag.putString("Fingerprint", fingerprint);
        tag.putString("Evidence", evidence); tag.putUUID("Entity", entity); tag.putString("Dimension", dimension.toString());
        tag.putInt("X", position.getX()); tag.putInt("Y", position.getY()); tag.putInt("Z", position.getZ()); tag.putLong("BoundAt", boundAt);
        return tag;
    }

    static CampaignEvidenceBinding load(CompoundTag tag) {
        for (String field : new String[]{"Campaign", "Fingerprint", "Evidence", "Dimension"})
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_STRING), "invalid evidence text " + field);
        for (String field : new String[]{"DefinitionRevision", "X", "Y", "Z"})
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_INT), "invalid evidence number " + field);
        CampaignStateCodec.require(tag.hasUUID("Instance") && tag.hasUUID("Entity") && tag.contains("BoundAt", Tag.TAG_LONG),
                "invalid evidence identity or time");
        return new CampaignEvidenceBinding(ResourceLocation.tryParse(tag.getString("Campaign")), tag.getUUID("Instance"),
                tag.getInt("DefinitionRevision"), tag.getString("Fingerprint"), tag.getString("Evidence"), tag.getUUID("Entity"),
                ResourceLocation.tryParse(tag.getString("Dimension")), new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")), tag.getLong("BoundAt"));
    }

    public record Key(ResourceLocation campaign, String evidence) { }
}
