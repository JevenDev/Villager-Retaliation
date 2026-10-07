package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignCharacterReplacement(CampaignCharacterBinding.Key key, UUID operation, UUID originalEntity,
        String replacementRole, UUID replacementEntity, long preparedAt, long committedAt) {
    public CampaignCharacterReplacement {
        CampaignStateCodec.require(key != null && key.campaign() != null && localId(key.role()) && operation != null && originalEntity != null
                && localId(replacementRole) && !key.role().equals(replacementRole) && preparedAt >= 0L
                && (replacementEntity == null ? committedAt == -1L : committedAt >= preparedAt && !replacementEntity.equals(originalEntity)),
                "invalid campaign character replacement");
    }

    public boolean committed() { return replacementEntity != null; }
    public CampaignCharacterBinding.Key target() { return new CampaignCharacterBinding.Key(key.campaign(), replacementRole); }

    static CampaignCharacterReplacement prepare(CampaignCharacterBinding original, String replacementRole, long time) {
        return new CampaignCharacterReplacement(original.key(), operation(original), original.entityId(), replacementRole, null, time, -1L);
    }

    CampaignCharacterReplacement commit(UUID entity, long time) {
        return new CampaignCharacterReplacement(key, operation, originalEntity, replacementRole, entity, preparedAt, time);
    }

    void validate(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings) {
        var original = bindings.get(key);
        CampaignStateCodec.require(original != null && original.lost() && original.lossPolicy() == CampaignCharacterDefinition.LossPolicy.REPLACEMENT
                && original.entityId().equals(originalEntity) && operation(original).equals(operation), "replacement has no matching lost character");
        var replacement = bindings.get(target());
        CampaignStateCodec.require(!committed() || replacement != null && replacement.entityId().equals(replacementEntity)
                && replacement.instance().equals(original.instance()) && replacement.definitionRevision() == original.definitionRevision()
                && replacement.fingerprint().equals(original.fingerprint()), "replacement has no matching successor binding");
    }

    private static UUID operation(CampaignCharacterBinding original) {
        return UUID.nameUUIDFromBytes((original.instance() + "/character-replacement/" + original.role()).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean localId(String id) { return id != null && id.length() <= 256 && id.matches("[a-z0-9][a-z0-9_./-]*"); }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Campaign", key.campaign().toString()); tag.putString("Role", key.role());
        tag.putUUID("Operation", operation); tag.putUUID("OriginalEntity", originalEntity);
        tag.putString("ReplacementRole", replacementRole);
        if (replacementEntity != null) tag.putUUID("ReplacementEntity", replacementEntity);
        tag.putLong("PreparedAt", preparedAt); tag.putLong("CommittedAt", committedAt);
        return tag;
    }

    static CampaignCharacterReplacement load(CompoundTag tag) {
        for (String field : new String[]{"Campaign", "Role", "ReplacementRole"}) {
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_STRING), "invalid replacement field " + field);
        }
        CampaignStateCodec.require(tag.hasUUID("Operation") && tag.hasUUID("OriginalEntity")
                && (!tag.contains("ReplacementEntity") || tag.hasUUID("ReplacementEntity"))
                && tag.contains("PreparedAt", Tag.TAG_LONG) && tag.contains("CommittedAt", Tag.TAG_LONG), "invalid replacement receipt identity or time");
        return new CampaignCharacterReplacement(new CampaignCharacterBinding.Key(ResourceLocation.tryParse(tag.getString("Campaign")), tag.getString("Role")),
                tag.getUUID("Operation"), tag.getUUID("OriginalEntity"), tag.getString("ReplacementRole"),
                tag.hasUUID("ReplacementEntity") ? tag.getUUID("ReplacementEntity") : null, tag.getLong("PreparedAt"), tag.getLong("CommittedAt"));
    }
}
