package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerProfileSyncPayload(
        int entityId,
        UUID villagerId,
        String professionKey,
        int generatedVersion,
        int knowledge,
        int guts,
        int proficiency,
        int kindness,
        int charm,
        int skillGeneratedVersion,
        int farming,
        int fishing,
        int smithing,
        int crafting,
        int trading,
        int medicine,
        int archery,
        int guarding,
        int cooking,
        int animalHandling,
        int cartography,
        int scholarship,
        int gathering,
        int masonry,
        int mining,
        int leatherworking,
        int diplomacy,
        int survival) implements CustomPacketPayload {
    public static final Type<VillagerProfileSyncPayload> TYPE = VillagerPayloads.type("villager_profile_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerProfileSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerProfileSyncPayload::encode, VillagerProfileSyncPayload::decode);

    public VillagerSocialAttributes attributes() {
        return new VillagerSocialAttributes(this.knowledge, this.guts, this.proficiency, this.kindness, this.charm);
    }

    public VillagerSkillSet skills() {
        EnumMap<VillagerSkill, Integer> values = new EnumMap<>(VillagerSkill.class);
        values.put(VillagerSkill.FARMING, this.farming);
        values.put(VillagerSkill.FISHING, this.fishing);
        values.put(VillagerSkill.SMITHING, this.smithing);
        values.put(VillagerSkill.CRAFTING, this.crafting);
        values.put(VillagerSkill.TRADING, this.trading);
        values.put(VillagerSkill.MEDICINE, this.medicine);
        values.put(VillagerSkill.ARCHERY, this.archery);
        values.put(VillagerSkill.GUARDING, this.guarding);
        values.put(VillagerSkill.COOKING, this.cooking);
        values.put(VillagerSkill.ANIMAL_HANDLING, this.animalHandling);
        values.put(VillagerSkill.CARTOGRAPHY, this.cartography);
        values.put(VillagerSkill.SCHOLARSHIP, this.scholarship);
        values.put(VillagerSkill.GATHERING, this.gathering);
        values.put(VillagerSkill.MASONRY, this.masonry);
        values.put(VillagerSkill.MINING, this.mining);
        values.put(VillagerSkill.LEATHERWORKING, this.leatherworking);
        values.put(VillagerSkill.DIPLOMACY, this.diplomacy);
        values.put(VillagerSkill.SURVIVAL, this.survival);
        return VillagerSkillSet.of(values);
    }

    public static VillagerProfileSyncPayload create(
            int entityId,
            UUID villagerId,
            String professionKey,
            int generatedVersion,
            VillagerSocialAttributes attributes,
            int skillGeneratedVersion,
            VillagerSkillSet skills) {
        VillagerSocialAttributes safeAttributes = attributes == null ? VillagerSocialAttributes.DEFAULT : attributes;
        VillagerSkillSet safeSkills = skills == null ? VillagerSkillSet.DEFAULT : skills.completeWith(VillagerSkillSet.DEFAULT);
        Map<VillagerSkill, Integer> values = safeSkills.asMap();
        return new VillagerProfileSyncPayload(
                entityId,
                villagerId,
                professionKey,
                generatedVersion,
                safeAttributes.knowledge(),
                safeAttributes.guts(),
                safeAttributes.proficiency(),
                safeAttributes.kindness(),
                safeAttributes.charm(),
                skillGeneratedVersion,
                values.get(VillagerSkill.FARMING),
                values.get(VillagerSkill.FISHING),
                values.get(VillagerSkill.SMITHING),
                values.get(VillagerSkill.CRAFTING),
                values.get(VillagerSkill.TRADING),
                values.get(VillagerSkill.MEDICINE),
                values.get(VillagerSkill.ARCHERY),
                values.get(VillagerSkill.GUARDING),
                values.get(VillagerSkill.COOKING),
                values.get(VillagerSkill.ANIMAL_HANDLING),
                values.get(VillagerSkill.CARTOGRAPHY),
                values.get(VillagerSkill.SCHOLARSHIP),
                values.get(VillagerSkill.GATHERING),
                values.get(VillagerSkill.MASONRY),
                values.get(VillagerSkill.MINING),
                values.get(VillagerSkill.LEATHERWORKING),
                values.get(VillagerSkill.DIPLOMACY),
                values.get(VillagerSkill.SURVIVAL)
        );
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerProfileSyncPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.villagerId());
        buffer.writeUtf(payload.professionKey(), 128);
        buffer.writeVarInt(payload.generatedVersion());
        buffer.writeVarInt(payload.knowledge());
        buffer.writeVarInt(payload.guts());
        buffer.writeVarInt(payload.proficiency());
        buffer.writeVarInt(payload.kindness());
        buffer.writeVarInt(payload.charm());
        buffer.writeVarInt(payload.skillGeneratedVersion());
        buffer.writeVarInt(payload.farming());
        buffer.writeVarInt(payload.fishing());
        buffer.writeVarInt(payload.smithing());
        buffer.writeVarInt(payload.crafting());
        buffer.writeVarInt(payload.trading());
        buffer.writeVarInt(payload.medicine());
        buffer.writeVarInt(payload.archery());
        buffer.writeVarInt(payload.guarding());
        buffer.writeVarInt(payload.cooking());
        buffer.writeVarInt(payload.animalHandling());
        buffer.writeVarInt(payload.cartography());
        buffer.writeVarInt(payload.scholarship());
        buffer.writeVarInt(payload.gathering());
        buffer.writeVarInt(payload.masonry());
        buffer.writeVarInt(payload.mining());
        buffer.writeVarInt(payload.leatherworking());
        buffer.writeVarInt(payload.diplomacy());
        buffer.writeVarInt(payload.survival());
    }

    private static VillagerProfileSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerProfileSyncPayload(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readUtf(128),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
