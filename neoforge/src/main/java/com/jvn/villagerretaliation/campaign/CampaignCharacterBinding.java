package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignCharacterBinding(ResourceLocation campaign, UUID instance, int definitionRevision, String fingerprint,
        String role, CampaignCharacterDefinition.Source source, CampaignCharacterDefinition.LossPolicy lossPolicy,
        UUID entityId, ResourceLocation dimension, BlockPos position, long boundAt, boolean lost) {
    public CampaignCharacterBinding {
        CampaignStateCodec.require(campaign != null && instance != null && definitionRevision > 0
                && fingerprint != null && !fingerprint.isBlank() && fingerprint.length() <= 256
                && role != null && role.matches("[a-z0-9][a-z0-9_./-]*") && role.length() <= 256
                && source != null && lossPolicy != null && entityId != null && dimension != null && position != null
                && boundAt >= 0L, "invalid campaign character binding");
        position = position.immutable();
        CampaignStateCodec.require(Math.abs((long) position.getX()) <= 30_000_000L
                && Math.abs((long) position.getY()) <= 30_000_000L && Math.abs((long) position.getZ()) <= 30_000_000L,
                "campaign character is outside world bounds");
    }

    public Key key() { return new Key(campaign, role); }

    boolean sameOwner(CampaignCharacterBinding other) {
        return key().equals(other.key()) && instance.equals(other.instance) && definitionRevision == other.definitionRevision
                && fingerprint.equals(other.fingerprint) && source == other.source && lossPolicy == other.lossPolicy;
    }

    CampaignCharacterBinding withLoss(boolean value) {
        return new CampaignCharacterBinding(campaign, instance, definitionRevision, fingerprint, role, source, lossPolicy,
                entityId, dimension, position, boundAt, value);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Campaign", campaign.toString());
        tag.putUUID("Instance", instance);
        tag.putInt("DefinitionRevision", definitionRevision);
        tag.putString("Fingerprint", fingerprint);
        tag.putString("Role", role);
        tag.putString("Source", source.name());
        tag.putString("LossPolicy", lossPolicy.name());
        tag.putUUID("Entity", entityId);
        tag.putString("Dimension", dimension.toString());
        tag.putInt("X", position.getX());
        tag.putInt("Y", position.getY());
        tag.putInt("Z", position.getZ());
        tag.putLong("BoundAt", boundAt);
        tag.putBoolean("Lost", lost);
        return tag;
    }

    static CampaignCharacterBinding load(CompoundTag tag) {
        for (String field : new String[]{"Campaign", "Fingerprint", "Role", "Source", "LossPolicy", "Dimension"}) {
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_STRING), "invalid campaign character field " + field);
        }
        for (String field : new String[]{"DefinitionRevision", "X", "Y", "Z"}) {
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_INT), "invalid campaign character field " + field);
        }
        CampaignStateCodec.require(tag.hasUUID("Instance") && tag.hasUUID("Entity") && tag.contains("BoundAt", Tag.TAG_LONG)
                && tag.contains("Lost", Tag.TAG_BYTE) && (tag.getByte("Lost") == 0 || tag.getByte("Lost") == 1),
                "invalid campaign character identity, loss or time");
        return new CampaignCharacterBinding(ResourceLocation.tryParse(tag.getString("Campaign")), tag.getUUID("Instance"),
                tag.getInt("DefinitionRevision"), tag.getString("Fingerprint"), tag.getString("Role"),
                CampaignCharacterDefinition.Source.valueOf(tag.getString("Source")), CampaignCharacterDefinition.LossPolicy.valueOf(tag.getString("LossPolicy")),
                tag.getUUID("Entity"), ResourceLocation.tryParse(tag.getString("Dimension")),
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")), tag.getLong("BoundAt"), tag.getBoolean("Lost"));
    }

    public record Key(ResourceLocation campaign, String role) { }
}
