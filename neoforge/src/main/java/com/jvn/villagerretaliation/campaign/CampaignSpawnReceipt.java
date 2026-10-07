package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CampaignSpawnReceipt(CampaignCharacterBinding binding, UUID operation, CompoundTag entity, long committedAt) {
    public CampaignSpawnReceipt {
        CampaignStateCodec.require(binding != null && binding.source() == CampaignCharacterDefinition.Source.OWNED_SPAWN
                && !binding.lost() && operation != null && entity != null && entity.hasUUID("UUID")
                && entity.getUUID("UUID").equals(binding.entityId()) && "minecraft:villager".equals(entity.getString("id"))
                && (committedAt == -1L || committedAt >= binding.boundAt()), "invalid campaign spawn receipt");
        var position = entity.getList("Pos", Tag.TAG_DOUBLE);
        CampaignStateCodec.require(position.size() == 3 && position.getDouble(0) == binding.position().getX() + 0.5
                && position.getDouble(1) == binding.position().getY() && position.getDouble(2) == binding.position().getZ() + 0.5,
                "campaign birth snapshot does not match its reserved position");
        var owner = entity.getCompound("NeoForgeData").getCompound("VillagerRetaliationCampaignOwner");
        CampaignStateCodec.require(owner.hasUUID("Instance") && owner.getUUID("Instance").equals(binding.instance())
                && owner.hasUUID("Operation") && owner.getUUID("Operation").equals(operation)
                && owner.getString("Campaign").equals(binding.campaign().toString()) && owner.getString("Role").equals(binding.role()),
                "campaign birth snapshot does not match its reserved ownership");
        entity = entity.copy();
    }

    @Override public CompoundTag entity() { return entity.copy(); }
    public boolean committed() { return committedAt >= 0L; }
    public CampaignCharacterBinding.Key key() { return binding.key(); }

    CampaignSpawnReceipt commit(long time) {
        return committed() ? this : new CampaignSpawnReceipt(binding, operation, entity, Math.max(time, binding.boundAt()));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Binding", binding.save());
        tag.putUUID("Operation", operation);
        tag.put("Entity", entity.copy());
        tag.putLong("CommittedAt", committedAt);
        return tag;
    }

    static CampaignSpawnReceipt load(CompoundTag tag) {
        CampaignStateCodec.require(tag.contains("Binding", Tag.TAG_COMPOUND) && tag.contains("Entity", Tag.TAG_COMPOUND)
                && tag.hasUUID("Operation") && tag.contains("CommittedAt", Tag.TAG_LONG), "invalid campaign spawn receipt fields");
        return new CampaignSpawnReceipt(CampaignCharacterBinding.load(tag.getCompound("Binding")), tag.getUUID("Operation"),
                tag.getCompound("Entity"), tag.getLong("CommittedAt"));
    }
}
