package com.jvn.villagerretaliation.study;

import com.jvn.villagerretaliation.skill.VillagerSkill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent study-session state stored as part of a villager profile.
 *
 * <p>Progress is active time rather than elapsed world time. Cooldown uses overworld game time so
 * unloads and restarts cannot bypass it.</p>
 */
public record VillagerStudyState(
        @Nullable VillagerSkill skill,
        int activeTicks,
        boolean paused,
        long cooldownUntilGameTime
) {
    private static final String TAG_SKILL = "Skill";
    private static final String TAG_ACTIVE_TICKS = "ActiveTicks";
    private static final String TAG_PAUSED = "Paused";
    private static final String TAG_COOLDOWN_UNTIL = "CooldownUntilGameTime";
    public static final VillagerStudyState NONE = new VillagerStudyState(null, 0, false, 0L);

    public VillagerStudyState {
        activeTicks = Math.max(0, activeTicks);
        cooldownUntilGameTime = Math.max(0L, cooldownUntilGameTime);
        if (skill == null) {
            activeTicks = 0;
            paused = false;
        }
    }

    public boolean studying() {
        return this.skill != null;
    }

    public boolean active() {
        return studying() && !this.paused;
    }

    public long cooldownRemaining(long gameTime) {
        return Math.max(0L, this.cooldownUntilGameTime - Math.max(0L, gameTime));
    }

    public boolean onCooldown(long gameTime) {
        return cooldownRemaining(gameTime) > 0L;
    }

    public VillagerStudyState start(VillagerSkill selectedSkill) {
        return selectedSkill == null
                ? this
                : new VillagerStudyState(selectedSkill, 0, false, this.cooldownUntilGameTime);
    }

    public VillagerStudyState withPaused(boolean paused) {
        return !studying() || this.paused == paused
                ? this
                : new VillagerStudyState(this.skill, this.activeTicks, paused, this.cooldownUntilGameTime);
    }

    public VillagerStudyState advance() {
        return active()
                ? new VillagerStudyState(this.skill, this.activeTicks + 1, false, this.cooldownUntilGameTime)
                : this;
    }

    public VillagerStudyState complete(long cooldownUntilGameTime) {
        return new VillagerStudyState(null, 0, false, cooldownUntilGameTime);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (this.skill != null) {
            tag.putString(TAG_SKILL, this.skill.serializedName());
            tag.putInt(TAG_ACTIVE_TICKS, this.activeTicks);
            tag.putBoolean(TAG_PAUSED, this.paused);
        }
        if (this.cooldownUntilGameTime > 0L) {
            tag.putLong(TAG_COOLDOWN_UNTIL, this.cooldownUntilGameTime);
        }
        return tag;
    }

    public static VillagerStudyState load(CompoundTag tag) {
        if (tag == null) {
            return NONE;
        }
        VillagerSkill skill = tag.contains(TAG_SKILL, Tag.TAG_STRING)
                ? VillagerSkill.bySerializedName(tag.getString(TAG_SKILL))
                : null;
        int activeTicks = skill == null ? 0 : tag.getInt(TAG_ACTIVE_TICKS);
        boolean paused = skill != null && tag.getBoolean(TAG_PAUSED);
        long cooldownUntil = tag.contains(TAG_COOLDOWN_UNTIL, Tag.TAG_LONG)
                ? tag.getLong(TAG_COOLDOWN_UNTIL)
                : 0L;
        return new VillagerStudyState(skill, activeTicks, paused, cooldownUntil);
    }
}
