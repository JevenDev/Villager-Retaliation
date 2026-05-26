package com.jvn.villagerretaliation.profile;

import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
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
    private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
    private static final String TAG_UPDATED_GAME_TIME = "UpdatedGameTime";

    private final UUID villagerUuid;
    private int generatedVersion;
    private long seed;
    private VillagerSocialAttributes socialAttributes;
    private int skillGeneratedVersion;
    private VillagerSkillSet skills;
    private String lastKnownProfession;
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
            long createdGameTime,
            long updatedGameTime) {
        this.villagerUuid = villagerUuid;
        this.generatedVersion = generatedVersion;
        this.seed = seed;
        this.socialAttributes = socialAttributes == null ? VillagerSocialAttributes.DEFAULT : socialAttributes;
        this.skillGeneratedVersion = skillGeneratedVersion;
        this.skills = skills == null ? VillagerSkillSet.EMPTY : skills;
        this.lastKnownProfession = lastKnownProfession == null ? "" : lastKnownProfession;
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
        tag.putLong(TAG_CREATED_GAME_TIME, this.createdGameTime);
        tag.putLong(TAG_UPDATED_GAME_TIME, this.updatedGameTime);
        return tag;
    }

    public UUID villagerUuid() {
        return this.villagerUuid;
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
}
