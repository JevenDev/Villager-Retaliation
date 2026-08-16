package com.jvn.villagerretaliation.profile;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.skill.VillagerSkillValue;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class VillagerProfileManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DAY_TICKS = 24_000L;
    private static final long RETIREMENT_PRUNE_INTERVAL_TICKS = 1_200L;
    private static final Set<UUID> VANILLA_DESPAWN_ELIGIBLE_TRADERS = new HashSet<>();

    private VillagerProfileManager() {
    }

    public static VillagerProfile getOrCreateProfile(ServerLevel level, AbstractVillager villager) {
        VillagerProfileSavedData data = VillagerProfileSavedData.get(level);
        rememberWanderingTraderDespawnEligibility(villager);
        data.reactivate(villager.getUUID());
        VillagerProfile profile = data.get(villager.getUUID());
        if (profile == null) {
            profile = VillagerProfileGenerator.generate(level, villager);
            data.put(profile);
            return profile;
        }

        String previousProfessionKey = profile.lastKnownProfession();
        String professionKey = VillagerProfileGenerator.professionKey(villager);
        boolean changed = regenerateUntouchedSkillsForFirstProfession(
                profile, previousProfessionKey, professionKey, level.getGameTime());
        changed |= profile.updateLastKnownProfession(professionKey, level.getGameTime());
        changed |= ensureSkills(profile, professionKey, level.getGameTime());
        if (changed) {
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

    public static boolean adjustAttribute(
            ServerLevel level,
            AbstractVillager villager,
            VillagerSocialAttribute attribute,
            int change) {
        return adjustAttribute(level, getOrCreateProfile(level, villager), attribute, change);
    }

    public static boolean adjustAttribute(
            ServerLevel level,
            UUID villagerUuid,
            VillagerSocialAttribute attribute,
            int change) {
        return adjustAttribute(level, VillagerProfileSavedData.get(level).get(villagerUuid), attribute, change);
    }

    private static boolean adjustAttribute(
            ServerLevel level, VillagerProfile profile, VillagerSocialAttribute attribute, int change) {
        if (profile == null || change == 0) return false;
        boolean changed = profile.setSocialAttribute(attribute, profile.socialAttributes().get(attribute) + change, level.getGameTime());
        if (changed) VillagerProfileSavedData.get(level).setDirty();
        return changed;
    }

    public static int getSkill(ServerLevel level, AbstractVillager villager, VillagerSkill skill) {
        return getOrCreateProfile(level, villager).skills().get(skill);
    }

    public static VillagerSkillRank getSkillRank(ServerLevel level, AbstractVillager villager, VillagerSkill skill) {
        return getOrCreateProfile(level, villager).skills().rank(skill);
    }

    public static VillagerSkillSet getSkills(ServerLevel level, AbstractVillager villager) {
        return getOrCreateProfile(level, villager).skills();
    }

    public static List<VillagerSkillValue> getBestSkills(ServerLevel level, AbstractVillager villager, int limit) {
        return getSkills(level, villager).best(limit);
    }

    public static VillagerSkill getProfessionPrimarySkill(AbstractVillager villager) {
        return VillagerProfessionSkills.primarySkill(villager);
    }

    public static List<VillagerSkill> getProfessionTradeSkills(AbstractVillager villager) {
        return VillagerProfessionSkills.tradeSkills(villager);
    }

    public static boolean hasSkillAtLeast(ServerLevel level, AbstractVillager villager, VillagerSkill skill, int value) {
        return getSkill(level, villager, skill) >= VillagerSkillSet.clamp(value);
    }

    public static boolean hasSkillRankAtLeast(ServerLevel level, AbstractVillager villager, VillagerSkill skill, VillagerSkillRank rank) {
        return rank != null && getSkill(level, villager, skill) >= rank.minInclusive();
    }

    public static boolean setSkill(ServerLevel level, AbstractVillager villager, VillagerSkill skill, int value) {
        VillagerProfile profile = getOrCreateProfile(level, villager);
        boolean changed = profile.setSkill(skill, value, level.getGameTime());
        if (changed) {
            VillagerProfileSavedData.get(level).setDirty();
        }
        return changed;
    }

    public static VillagerProfile rerollSkills(ServerLevel level, AbstractVillager villager) {
        VillagerProfile profile = getOrCreateProfile(level, villager);
        String professionKey = VillagerProfileGenerator.professionKey(villager);
        VillagerSkillSet skills = VillagerSkillGenerator.generate(professionKey, profile.socialAttributes(), profile.seed());
        profile.replaceSkills(skills, VillagerSkillGenerator.CURRENT_GENERATION_VERSION, level.getGameTime());
        VillagerProfileSavedData.get(level).setDirty();
        return profile;
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isProfileCarrier(event.getEntity())) {
            return;
        }

        Entity entity = event.getEntity();
        rememberWanderingTraderDespawnEligibility(entity);
        VillagerProfileSavedData data = VillagerProfileSavedData.get(level);
        if (data.hasProfile(entity.getUUID())) {
            data.reactivate(entity.getUUID());
        }
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isProfileCarrier(event.getEntity())) {
            return;
        }

        Entity entity = event.getEntity();
        // Unloads, dimension changes, and generic discards can represent transport or mod storage,
        // so only the terminal removal signals below may retire a profile.
        boolean vanillaDespawnEligible = VANILLA_DESPAWN_ELIGIBLE_TRADERS.remove(entity.getUUID());
        Entity.RemovalReason removalReason = entity.getRemovalReason();
        VillagerProfileSavedData.RetirementReason retirementReason = null;
        if (removalReason == Entity.RemovalReason.KILLED) {
            retirementReason = VillagerProfileSavedData.RetirementReason.DEATH;
        } else if (entity instanceof WanderingTrader trader
                && removalReason == Entity.RemovalReason.DISCARDED
                && trader.getDespawnDelay() == 0
                && vanillaDespawnEligible) {
            retirementReason = VillagerProfileSavedData.RetirementReason.NATURAL_DESPAWN;
        }

        if (retirementReason != null) {
            VillagerProfileSavedData.get(level).retire(
                    entity.getUUID(),
                    worldGameTime(level),
                    retirementReason
            );
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % RETIREMENT_PRUNE_INTERVAL_TICKS != 0L) {
            return;
        }

        int retentionDays = VillagerRetaliationConfig.RETIRED_VILLAGER_PROFILE_RETENTION_DAYS.get();
        if (retentionDays <= 0) {
            return;
        }
        long retentionTicks = retentionDays * DAY_TICKS;

        int removed = VillagerProfileSavedData.get(event.getServer().overworld())
                .pruneRetiredProfiles(gameTime - retentionTicks);
        if (removed > 0) {
            LOGGER.debug("Pruned {} retired villager profile(s).", removed);
        }
    }

    public static void clearRuntimeState() {
        VANILLA_DESPAWN_ELIGIBLE_TRADERS.clear();
    }

    private static boolean isProfileCarrier(Entity entity) {
        return entity instanceof AbstractVillager || entity instanceof ZombieVillager;
    }

    private static void rememberWanderingTraderDespawnEligibility(Entity entity) {
        if (entity instanceof WanderingTrader trader && trader.getDespawnDelay() > 0) {
            VANILLA_DESPAWN_ELIGIBLE_TRADERS.add(trader.getUUID());
        }
    }

    private static long worldGameTime(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    public static String exportProfile(VillagerProfile profile) {
        VillagerSocialAttributes attributes = profile.socialAttributes();
        VillagerSkillSet skills = profile.skills();
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"villagerUuid\":\"").append(profile.villagerUuid()).append("\",");
        builder.append("\"generatedVersion\":").append(profile.generatedVersion()).append(",");
        builder.append("\"seed\":").append(profile.seed()).append(",");
        builder.append("\"lastKnownProfession\":\"").append(escape(profile.lastKnownProfession())).append("\",");
        builder.append("\"socialAttributes\":{");
        builder.append("\"knowledge\":").append(attributes.knowledge()).append(",");
        builder.append("\"guts\":").append(attributes.guts()).append(",");
        builder.append("\"proficiency\":").append(attributes.proficiency()).append(",");
        builder.append("\"kindness\":").append(attributes.kindness()).append(",");
        builder.append("\"charm\":").append(attributes.charm());
        builder.append("},");
        builder.append("\"skillGeneratedVersion\":").append(profile.skillGeneratedVersion()).append(",");
        builder.append("\"skills\":{");
        boolean first = true;
        for (VillagerSkill skill : VillagerSkill.values()) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("\"").append(skill.serializedName()).append("\":").append(skills.get(skill));
        }
        builder.append("},");
        builder.append("\"highestSkillGrowthTradeLevelAwarded\":").append(profile.highestSkillGrowthTradeLevelAwarded()).append(",");
        builder.append("\"regularTradeSkillGrowthProgress\":{");
        boolean firstProgress = true;
        for (VillagerSkill skill : VillagerSkill.values()) {
            double progress = profile.regularTradeSkillGrowthProgress(skill);
            if (progress <= 0.0D) {
                continue;
            }
            if (!firstProgress) {
                builder.append(",");
            }
            firstProgress = false;
            builder.append("\"").append(skill.serializedName()).append("\":").append(progress);
        }
        builder.append("},");
        builder.append("\"skillPracticeXp\":{");
        boolean firstPracticeXp = true;
        for (VillagerSkill skill : VillagerSkill.values()) {
            double xp = profile.skillPracticeXp(skill);
            if (xp <= 0.0D) {
                continue;
            }
            if (!firstPracticeXp) {
                builder.append(",");
            }
            firstPracticeXp = false;
            builder.append("\"").append(skill.serializedName()).append("\":").append(xp);
        }
        builder.append("},");
        builder.append("\"skillPracticeDailyState\":{");
        boolean firstDailyState = true;
        for (VillagerSkill skill : VillagerSkill.values()) {
            if (!profile.hasPracticeDailyState(skill)) {
                continue;
            }
            if (!firstDailyState) {
                builder.append(",");
            }
            firstDailyState = false;
            builder.append("\"").append(skill.serializedName()).append("\":{")
                    .append("\"day\":").append(profile.practiceDayIndex(skill)).append(",")
                    .append("\"earnedXp\":").append(profile.practiceEarnedOnStoredDay(skill)).append(",")
                    .append("\"repetitionKeys\":").append(profile.storedRepetitionKeyCount(skill))
                    .append("}");
        }
        builder.append("},");
        builder.append("\"tradeLevelSkillAdjustedXpProgress\":").append(profile.tradeLevelSkillAdjustedXpProgress()).append(",");
        builder.append("\"createdGameTime\":").append(profile.createdGameTime()).append(",");
        builder.append("\"updatedGameTime\":").append(profile.updatedGameTime());
        builder.append("}");
        return builder.toString();
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

    public static String skillDisplayLine(VillagerProfile profile, boolean exactValues) {
        StringBuilder builder = new StringBuilder();
        builder.append("Skills ")
                .append(profile.villagerUuid())
                .append(" [")
                .append(profile.lastKnownProfession())
                .append("]");
        for (VillagerSkillValue skillValue : profile.skills().best(8)) {
            VillagerSkillRank rank = skillValue.rank();
            builder.append(" ")
                    .append(titleCase(skillValue.skill().serializedName()))
                    .append("=")
                    .append(titleCase(rank.serializedName()));
            if (exactValues) {
                builder.append("(").append(skillValue.value()).append(")");
            }
        }
        return builder.toString();
    }

    public static String skillDisplayLine(VillagerProfile profile, VillagerSkill skill, boolean exactValues) {
        int value = profile.skills().get(skill);
        VillagerSkillRank rank = VillagerSkillRank.fromValue(value);
        StringBuilder builder = new StringBuilder();
        builder.append(titleCase(skill.serializedName()))
                .append("=")
                .append(titleCase(rank.serializedName()));
        if (exactValues) {
            builder.append("(").append(value).append(")");
        }
        return builder.toString();
    }

    private static boolean regenerateUntouchedSkillsForFirstProfession(
            VillagerProfile profile,
            String previousProfessionKey,
            String professionKey,
            long gameTime) {
        if (!isProfessionless(previousProfessionKey)
                || isProfessionless(professionKey)
                || profile.skillGeneratedVersion() != VillagerSkillGenerator.CURRENT_GENERATION_VERSION) {
            return false;
        }
        VillagerSkillSet originalSkills = VillagerSkillGenerator.generate(
                previousProfessionKey, profile.socialAttributes(), profile.seed());
        if (!profile.skills().asMap().equals(originalSkills.asMap())) {
            return false;
        }
        VillagerSkillSet professionSkills = VillagerSkillGenerator.generate(
                professionKey, profile.socialAttributes(), profile.seed());
        return profile.replaceSkills(
                professionSkills, VillagerSkillGenerator.CURRENT_GENERATION_VERSION, gameTime);
    }

    private static boolean isProfessionless(String professionKey) {
        return professionKey == null
                || professionKey.isBlank()
                || professionKey.equals("none")
                || professionKey.equals("minecraft:none")
                || professionKey.equals("unemployed")
                || professionKey.equals("minecraft:unemployed");
    }

    private static boolean ensureSkills(VillagerProfile profile, String professionKey, long gameTime) {
        if (!profile.needsSkillGeneration()) {
            return false;
        }
        VillagerSkillSet generated = VillagerSkillGenerator.generate(professionKey, profile.socialAttributes(), profile.seed());
        return profile.replaceSkills(generated, VillagerSkillGenerator.CURRENT_GENERATION_VERSION, gameTime);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (String part : lower.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
