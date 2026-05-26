package com.jvn.villagerretaliation.profile;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerProfileManager {
    private VillagerProfileManager() {
    }

    public static VillagerProfile getOrCreateProfile(ServerLevel level, AbstractVillager villager) {
        VillagerProfileSavedData data = VillagerProfileSavedData.get(level);
        VillagerProfile profile = data.get(villager.getUUID());
        if (profile == null) {
            profile = VillagerProfileGenerator.generate(level, villager);
            data.put(profile);
            return profile;
        }

        String professionKey = VillagerProfileGenerator.professionKey(villager);
        if (profile.updateLastKnownProfession(professionKey, level.getGameTime())) {
            data.setDirty();
        }
        return profile;
    }

    public static Optional<VillagerProfile> getProfile(ServerLevel level, UUID villagerUuid) {
        return Optional.ofNullable(VillagerProfileSavedData.get(level).get(villagerUuid));
    }

    public static VillagerProfile rerollProfile(ServerLevel level, AbstractVillager villager) {
        VillagerProfile profile = VillagerProfileGenerator.generate(level, villager);
        VillagerProfileSavedData.get(level).put(profile);
        return profile;
    }

    public static boolean setAttribute(
            ServerLevel level,
            AbstractVillager villager,
            VillagerSocialAttribute attribute,
            int value) {
        VillagerProfile profile = getOrCreateProfile(level, villager);
        boolean changed = profile.setSocialAttribute(attribute, value, level.getGameTime());
        if (changed) {
            VillagerProfileSavedData.get(level).setDirty();
        }
        return changed;
    }

    public static String exportProfile(VillagerProfile profile) {
        VillagerSocialAttributes attributes = profile.socialAttributes();
        return "{"
                + "\"villagerUuid\":\"" + profile.villagerUuid() + "\","
                + "\"generatedVersion\":" + profile.generatedVersion() + ","
                + "\"seed\":" + profile.seed() + ","
                + "\"lastKnownProfession\":\"" + escape(profile.lastKnownProfession()) + "\","
                + "\"socialAttributes\":{"
                + "\"knowledge\":" + attributes.knowledge() + ","
                + "\"guts\":" + attributes.guts() + ","
                + "\"proficiency\":" + attributes.proficiency() + ","
                + "\"kindness\":" + attributes.kindness() + ","
                + "\"charm\":" + attributes.charm()
                + "},"
                + "\"createdGameTime\":" + profile.createdGameTime() + ","
                + "\"updatedGameTime\":" + profile.updatedGameTime()
                + "}";
    }

    public static String displayLine(VillagerProfile profile, boolean exactValues) {
        StringBuilder builder = new StringBuilder();
        builder.append("Profile ")
                .append(profile.villagerUuid())
                .append(" [")
                .append(profile.lastKnownProfession())
                .append("]");
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            int value = profile.socialAttributes().get(attribute);
            VillagerSocialAttributeRank rank = VillagerSocialAttributeRank.fromValue(value);
            builder.append(" ")
                    .append(titleCase(attribute.serializedName()))
                    .append("=")
                    .append(titleCase(rank.serializedName()));
            if (exactValues) {
                builder.append("(").append(value).append(")");
            }
        }
        return builder.toString();
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
