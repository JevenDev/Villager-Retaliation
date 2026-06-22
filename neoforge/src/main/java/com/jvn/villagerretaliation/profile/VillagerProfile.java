package com.jvn.villagerretaliation.profile;

import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class VillagerProfile {
    public static final int CURRENT_GENERATION_VERSION = 1;

    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_GENERATED_VERSION = "GeneratedVersion";
    private static final String TAG_SEED = "Seed";
    private static final String TAG_SOCIAL_ATTRIBUTES = "SocialAttributes";
    private static final String TAG_SKILL_GENERATED_VERSION = "SkillGeneratedVersion";
    private static final String TAG_SKILLS = "Skills";
    private static final String TAG_LAST_KNOWN_PROFESSION = "LastKnownProfession";
    private static final String TAG_HIGHEST_SKILL_GROWTH_TRADE_LEVEL_AWARDED = "HighestSkillGrowthTradeLevelAwarded";
    private static final String TAG_REGULAR_TRADE_SKILL_GROWTH_PROGRESS = "RegularTradeSkillGrowthProgress";
    private static final String TAG_TRADE_LEVEL_SKILL_ADJUSTED_XP_PROGRESS = "TradeLevelSkillAdjustedXpProgress";
    private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
    private static final String TAG_UPDATED_GAME_TIME = "UpdatedGameTime";

    private final UUID villagerUuid;
    private int generatedVersion;
    private long seed;
    private VillagerSocialAttributes socialAttributes;
    private int skillGeneratedVersion;
    private VillagerSkillSet skills;
    private String lastKnownProfession;
    private int highestSkillGrowthTradeLevelAwarded;
    private final EnumMap<VillagerSkill, Double> regularTradeSkillGrowthProgress;
    private double tradeLevelSkillAdjustedXpProgress;
    private long createdGameTime;
    private long updatedGameTime;

    private VillagerProfile(
            UUID villagerUuid,
            int generatedVersion,
            long seed,
            VillagerSocialAttributes socialAttributes,
            int skillGeneratedVersion,
            VillagerSkillSet skills,
            String lastKnownProfession,
            int highestSkillGrowthTradeLevelAwarded,
            Map<VillagerSkill, Double> regularTradeSkillGrowthProgress,
            double tradeLevelSkillAdjustedXpProgress,
            long createdGameTime,
            long updatedGameTime) {
        this.villagerUuid = villagerUuid;
        this.generatedVersion = generatedVersion;
        this.seed = seed;
        this.socialAttributes = socialAttributes == null ? VillagerSocialAttributes.DEFAULT : socialAttributes;
        this.skillGeneratedVersion = skillGeneratedVersion;
        this.skills = skills == null ? VillagerSkillSet.EMPTY : skills;
        this.lastKnownProfession = lastKnownProfession == null ? "" : lastKnownProfession;
        this.highestSkillGrowthTradeLevelAwarded = Math.clamp(highestSkillGrowthTradeLevelAwarded, 1, 5);
        this.regularTradeSkillGrowthProgress = copyRegularTradeSkillGrowthProgress(regularTradeSkillGrowthProgress);
        this.tradeLevelSkillAdjustedXpProgress = clampFractionalProgress(tradeLevelSkillAdjustedXpProgress);
        this.createdGameTime = createdGameTime;
        this.updatedGameTime = updatedGameTime;
    }

    public static VillagerProfile create(
            UUID villagerUuid,
            int generatedVersion,
            long seed,
            VillagerSocialAttributes socialAttributes,
            int skillGeneratedVersion,
            VillagerSkillSet skills,
            String lastKnownProfession,
            long gameTime) {
        return new VillagerProfile(
                villagerUuid,
                generatedVersion,
                seed,
                socialAttributes,
                skillGeneratedVersion,
                skills,
                lastKnownProfession,
                1,
                Map.of(),
                0.0D,
                gameTime,
                gameTime
        );
    }

    public static VillagerProfile load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID(TAG_VILLAGER)) {
            return null;
        }

        VillagerSocialAttributes socialAttributes = tag.contains(TAG_SOCIAL_ATTRIBUTES, Tag.TAG_COMPOUND)
                ? VillagerSocialAttributes.load(tag.getCompound(TAG_SOCIAL_ATTRIBUTES))
                : VillagerSocialAttributes.DEFAULT;
        VillagerSkillSet skills = tag.contains(TAG_SKILLS, Tag.TAG_COMPOUND)
                ? VillagerSkillSet.load(tag.getCompound(TAG_SKILLS))
                : VillagerSkillSet.EMPTY;
        return new VillagerProfile(
                tag.getUUID(TAG_VILLAGER),
                tag.contains(TAG_GENERATED_VERSION, Tag.TAG_INT) ? tag.getInt(TAG_GENERATED_VERSION) : 0,
                tag.contains(TAG_SEED, Tag.TAG_LONG) ? tag.getLong(TAG_SEED) : 0L,
                socialAttributes,
                tag.contains(TAG_SKILL_GENERATED_VERSION, Tag.TAG_INT) ? tag.getInt(TAG_SKILL_GENERATED_VERSION) : 0,
                skills,
                tag.getString(TAG_LAST_KNOWN_PROFESSION),
                tag.contains(TAG_HIGHEST_SKILL_GROWTH_TRADE_LEVEL_AWARDED, Tag.TAG_INT)
                        ? tag.getInt(TAG_HIGHEST_SKILL_GROWTH_TRADE_LEVEL_AWARDED)
                        : 1,
                tag.contains(TAG_REGULAR_TRADE_SKILL_GROWTH_PROGRESS, Tag.TAG_COMPOUND)
                        ? loadRegularTradeSkillGrowthProgress(tag.getCompound(TAG_REGULAR_TRADE_SKILL_GROWTH_PROGRESS))
                        : Map.of(),
                tag.contains(TAG_TRADE_LEVEL_SKILL_ADJUSTED_XP_PROGRESS, Tag.TAG_DOUBLE)
                        ? tag.getDouble(TAG_TRADE_LEVEL_SKILL_ADJUSTED_XP_PROGRESS)
                        : 0.0D,
                tag.contains(TAG_CREATED_GAME_TIME, Tag.TAG_LONG) ? tag.getLong(TAG_CREATED_GAME_TIME) : 0L,
                tag.contains(TAG_UPDATED_GAME_TIME, Tag.TAG_LONG) ? tag.getLong(TAG_UPDATED_GAME_TIME) : 0L
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_VILLAGER, this.villagerUuid);
        tag.putInt(TAG_GENERATED_VERSION, this.generatedVersion);
        tag.putLong(TAG_SEED, this.seed);
        tag.put(TAG_SOCIAL_ATTRIBUTES, this.socialAttributes.save());
        tag.putInt(TAG_SKILL_GENERATED_VERSION, this.skillGeneratedVersion);
        tag.put(TAG_SKILLS, this.skills.save());
        tag.putString(TAG_LAST_KNOWN_PROFESSION, this.lastKnownProfession);
        tag.putInt(TAG_HIGHEST_SKILL_GROWTH_TRADE_LEVEL_AWARDED, this.highestSkillGrowthTradeLevelAwarded);
        CompoundTag regularTradeProgress = saveRegularTradeSkillGrowthProgress();
        if (!regularTradeProgress.isEmpty()) {
            tag.put(TAG_REGULAR_TRADE_SKILL_GROWTH_PROGRESS, regularTradeProgress);
        }
        if (this.tradeLevelSkillAdjustedXpProgress > 0.000_001D) {
            tag.putDouble(TAG_TRADE_LEVEL_SKILL_ADJUSTED_XP_PROGRESS, this.tradeLevelSkillAdjustedXpProgress);
        }
        tag.putLong(TAG_CREATED_GAME_TIME, this.createdGameTime);
        tag.putLong(TAG_UPDATED_GAME_TIME, this.updatedGameTime);
        return tag;
    }

    public UUID villagerUuid() {
        return this.villagerUuid;
    }

    public VillagerProfile copyFor(UUID villagerUuid) {
        return new VillagerProfile(
                villagerUuid,
                this.generatedVersion,
                this.seed,
                this.socialAttributes,
                this.skillGeneratedVersion,
                this.skills,
                this.lastKnownProfession,
                this.highestSkillGrowthTradeLevelAwarded,
                this.regularTradeSkillGrowthProgress,
                this.tradeLevelSkillAdjustedXpProgress,
                this.createdGameTime,
                this.updatedGameTime
        );
    }

    public int generatedVersion() {
        return this.generatedVersion;
    }

    public long seed() {
        return this.seed;
    }

    public VillagerSocialAttributes socialAttributes() {
        return this.socialAttributes;
    }

    public int skillGeneratedVersion() {
        return this.skillGeneratedVersion;
    }

    public VillagerSkillSet skills() {
        return this.skills;
    }

    public String lastKnownProfession() {
        return this.lastKnownProfession;
    }

    public int highestSkillGrowthTradeLevelAwarded() {
        return this.highestSkillGrowthTradeLevelAwarded;
    }

    public Map<VillagerSkill, Double> regularTradeSkillGrowthProgress() {
        return Map.copyOf(this.regularTradeSkillGrowthProgress);
    }

    public double regularTradeSkillGrowthProgress(VillagerSkill skill) {
        return this.regularTradeSkillGrowthProgress.getOrDefault(skill, 0.0D);
    }

    public double tradeLevelSkillAdjustedXpProgress() {
        return this.tradeLevelSkillAdjustedXpProgress;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public long updatedGameTime() {
        return this.updatedGameTime;
    }

    public boolean setSocialAttribute(VillagerSocialAttribute attribute, int value, long gameTime) {
        VillagerSocialAttributes updated = this.socialAttributes.with(attribute, value);
        if (updated.equals(this.socialAttributes)) {
            return false;
        }
        this.socialAttributes = updated;
        this.updatedGameTime = gameTime;
        return true;
    }

    public boolean setSkill(VillagerSkill skill, int value, long gameTime) {
        VillagerSkillSet updated = this.skills.with(skill, value);
        if (updated.asMap().equals(this.skills.asMap())) {
            return false;
        }
        this.skills = updated;
        this.updatedGameTime = gameTime;
        return true;
    }

    public boolean replaceSkills(VillagerSkillSet skills, int skillGeneratedVersion, long gameTime) {
        VillagerSkillSet safeSkills = skills == null ? VillagerSkillSet.DEFAULT : skills.completeWith(VillagerSkillSet.DEFAULT);
        if (this.skillGeneratedVersion == skillGeneratedVersion && safeSkills.asMap().equals(this.skills.asMap())) {
            return false;
        }
        this.skills = safeSkills;
        this.skillGeneratedVersion = skillGeneratedVersion;
        this.updatedGameTime = gameTime;
        return true;
    }

    public boolean markSkillGrowthTradeLevelAwarded(int tradeLevel, long gameTime) {
        int clamped = Math.clamp(tradeLevel, 1, 5);
        if (clamped <= this.highestSkillGrowthTradeLevelAwarded) {
            return false;
        }
        this.highestSkillGrowthTradeLevelAwarded = clamped;
        this.updatedGameTime = gameTime;
        return true;
    }

    public boolean setRegularTradeSkillGrowthProgress(VillagerSkill skill, double progress, long gameTime) {
        if (skill == null) {
            return false;
        }

        double clamped = clampFractionalProgress(progress);
        double current = regularTradeSkillGrowthProgress(skill);
        if (Math.abs(current - clamped) < 0.000_001D) {
            return false;
        }

        if (clamped <= 0.000_001D) {
            this.regularTradeSkillGrowthProgress.remove(skill);
        } else {
            this.regularTradeSkillGrowthProgress.put(skill, clamped);
        }
        this.updatedGameTime = gameTime;
        return true;
    }

    public boolean setTradeLevelSkillAdjustedXpProgress(double progress, long gameTime) {
        double clamped = clampFractionalProgress(progress);
        if (Math.abs(this.tradeLevelSkillAdjustedXpProgress - clamped) < 0.000_001D) {
            return false;
        }

        this.tradeLevelSkillAdjustedXpProgress = clamped <= 0.000_001D ? 0.0D : clamped;
        this.updatedGameTime = gameTime;
        return true;
    }

    public boolean needsSkillGeneration() {
        return this.skillGeneratedVersion < VillagerSkillGenerator.CURRENT_GENERATION_VERSION || !this.skills.hasAllSkills();
    }

    public boolean updateLastKnownProfession(String profession, long gameTime) {
        String safeProfession = profession == null ? "" : profession;
        if (this.lastKnownProfession.equals(safeProfession)) {
            return false;
        }
        this.lastKnownProfession = safeProfession;
        this.updatedGameTime = gameTime;
        return true;
    }

    public void replaceGeneratedProfile(
            int generatedVersion,
            long seed,
            VillagerSocialAttributes socialAttributes,
            int skillGeneratedVersion,
            VillagerSkillSet skills,
            String lastKnownProfession,
            long gameTime) {
        this.generatedVersion = generatedVersion;
        this.seed = seed;
        this.socialAttributes = socialAttributes == null ? VillagerSocialAttributes.DEFAULT : socialAttributes;
        this.skillGeneratedVersion = skillGeneratedVersion;
        this.skills = skills == null ? VillagerSkillSet.DEFAULT : skills.completeWith(VillagerSkillSet.DEFAULT);
        this.lastKnownProfession = lastKnownProfession == null ? "" : lastKnownProfession;
        if (this.createdGameTime == 0L) {
            this.createdGameTime = gameTime;
        }
        this.updatedGameTime = gameTime;
    }

    private static EnumMap<VillagerSkill, Double> copyRegularTradeSkillGrowthProgress(Map<VillagerSkill, Double> progress) {
        EnumMap<VillagerSkill, Double> copy = new EnumMap<>(VillagerSkill.class);
        if (progress == null) {
            return copy;
        }

        for (Map.Entry<VillagerSkill, Double> entry : progress.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                double value = clampFractionalProgress(entry.getValue());
                if (value > 0.000_001D) {
                    copy.put(entry.getKey(), value);
                }
            }
        }
        return copy;
    }

    private static EnumMap<VillagerSkill, Double> loadRegularTradeSkillGrowthProgress(CompoundTag tag) {
        EnumMap<VillagerSkill, Double> progress = new EnumMap<>(VillagerSkill.class);
        if (tag == null) {
            return progress;
        }

        for (VillagerSkill skill : VillagerSkill.values()) {
            double value = 0.0D;
            if (tag.contains(skill.serializedName(), Tag.TAG_DOUBLE)) {
                value = tag.getDouble(skill.serializedName());
            } else if (tag.contains(skill.name(), Tag.TAG_DOUBLE)) {
                value = tag.getDouble(skill.name());
            }

            value = clampFractionalProgress(value);
            if (value > 0.000_001D) {
                progress.put(skill, value);
            }
        }
        return progress;
    }

    private CompoundTag saveRegularTradeSkillGrowthProgress() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<VillagerSkill, Double> entry : this.regularTradeSkillGrowthProgress.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0.000_001D) {
                tag.putDouble(entry.getKey().serializedName(), clampFractionalProgress(entry.getValue()));
            }
        }
        return tag;
    }

    private static double clampFractionalProgress(double progress) {
        if (!Double.isFinite(progress)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(0.999_999D, progress));
    }
}
