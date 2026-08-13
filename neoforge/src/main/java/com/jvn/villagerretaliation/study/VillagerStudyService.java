package com.jvn.villagerretaliation.study;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerAiArbitration;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative lifecycle for player-directed villager study sessions.
 */
public final class VillagerStudyService {
    private static final int MAX_CONFIG_TICKS = 72_000;

    private VillagerStudyService() {
    }

    public static StartResult start(ServerLevel level, Villager villager, @Nullable VillagerSkill skill) {
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        Eligibility eligibility = eligibility(level, villager, profile, skill);
        if (eligibility != Eligibility.ELIGIBLE) {
            return new StartResult(false, eligibility, profile.studyState());
        }

        VillagerStudyState started = profile.studyState().start(skill);
        if (profile.setStudyState(started, level.getGameTime())) {
            VillagerProfileSavedData.get(level).setDirty();
        }
        return new StartResult(true, Eligibility.ELIGIBLE, started);
    }

    public static Eligibility eligibility(ServerLevel level, Villager villager, @Nullable VillagerSkill skill) {
        if (level == null || villager == null) {
            return Eligibility.UNAVAILABLE;
        }
        return eligibility(level, villager, VillagerProfileManager.getOrCreateProfile(level, villager), skill);
    }

    private static Eligibility eligibility(
            ServerLevel level,
            Villager villager,
            VillagerProfile profile,
            @Nullable VillagerSkill skill
    ) {
        return evaluateEligibility(
                VillagerRetaliationConfig.ENABLE_VILLAGER_STUDYING.get(),
                villager.isAlive() && !villager.isRemoved() && villager.level() == level,
                villager.isBaby(),
                HiredVillagerContractService.hasActiveOrPendingContract(villager),
                PartyService.isRecruitedPartyVillager(level, villager.getUUID()),
                profile.studyState(),
                skill,
                skill == null ? VillagerSkillSet.MIN_VALUE : profile.skills().get(skill),
                worldGameTime(level));
    }

    static Eligibility evaluateEligibility(
            boolean enabled,
            boolean available,
            boolean baby,
            boolean hired,
            boolean recruited,
            VillagerStudyState state,
            @Nullable VillagerSkill skill,
            int skillValue,
            long gameTime
    ) {
        if (!enabled) return Eligibility.DISABLED;
        if (!available) return Eligibility.UNAVAILABLE;
        if (baby) return Eligibility.BABY;
        if (hired) return Eligibility.HIRED;
        if (recruited) return Eligibility.RECRUITED;
        VillagerStudyState safeState = state == null ? VillagerStudyState.NONE : state;
        if (safeState.studying()) return Eligibility.ALREADY_STUDYING;
        if (safeState.onCooldown(gameTime)) return Eligibility.COOLDOWN;
        if (skill == null) return Eligibility.INVALID_SKILL;
        if (skillValue >= VillagerSkillSet.MAX_VALUE) return Eligibility.SKILL_MAXED;
        return Eligibility.ELIGIBLE;
    }

    static int appliedReward(int currentValue, int rolledReward) {
        int current = VillagerSkillSet.clamp(currentValue);
        return Math.max(0, Math.min(Math.max(0, rolledReward), VillagerSkillSet.MAX_VALUE - current));
    }

    public static TickResult tick(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return TickResult.NONE;
        }
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        VillagerStudyState previous = profile.studyState();
        if (!previous.studying()) {
            return TickResult.NONE;
        }

        boolean paused = shouldPause(level, villager);
        VillagerStudyState current = previous.withPaused(paused);
        if (paused) {
            saveState(level, profile, current);
            return current.equals(previous) ? TickResult.NONE : TickResult.PAUSED;
        }

        suppressOrdinaryActivity(villager);
        current = current.advance();
        int duration = configuredDurationTicks();
        if (current.activeTicks() < duration) {
            saveState(level, profile, current);
            return current.paused() != previous.paused() ? TickResult.RESUMED : TickResult.PROGRESSED;
        }

        VillagerSkill skill = current.skill();
        if (skill == null) {
            saveState(level, profile, VillagerStudyState.NONE);
            return TickResult.CANCELLED_INVALID;
        }
        int oldValue = profile.skills().get(skill);
        int rolled = rollReward(villager);
        int applied = appliedReward(oldValue, rolled);
        long now = worldGameTime(level);
        if (applied > 0) {
            profile.setSkill(skill, oldValue + applied, level.getGameTime());
        }
        VillagerStudyState completed = current.complete(saturatedAdd(now, configuredCooldownTicks()));
        profile.setStudyState(completed, level.getGameTime());
        VillagerProfileSavedData.get(level).setDirty();
        syncCompletedProfile(level, villager, profile);
        VillagerBehaviorSuppressionPolicy.restoreAfterRelease(level, villager);
        return new TickResult(true, skill, applied, oldValue + applied);
    }

    public static VillagerStudyState state(ServerLevel level, Villager villager) {
        return VillagerProfileManager.getOrCreateProfile(level, villager).studyState();
    }

    public static boolean isStudying(ServerLevel level, Villager villager) {
        return VillagerProfileManager.getProfile(level, villager.getUUID())
                .map(VillagerProfile::studyState)
                .map(VillagerStudyState::studying)
                .orElse(false);
    }

    public static boolean isActivelyStudying(ServerLevel level, Villager villager) {
        return VillagerProfileManager.getProfile(level, villager.getUUID())
                .map(VillagerProfile::studyState)
                .map(VillagerStudyState::active)
                .orElse(false);
    }

    public static int configuredDurationTicks() {
        return Math.clamp(VillagerRetaliationConfig.STUDY_DURATION_TICKS.get(), 1, MAX_CONFIG_TICKS);
    }

    public static int configuredCooldownTicks() {
        return Math.clamp(VillagerRetaliationConfig.STUDY_COOLDOWN_TICKS.get(), 0, MAX_CONFIG_TICKS);
    }

    public static RewardRange configuredRewardRange() {
        int configuredMin = Math.clamp(
                VillagerRetaliationConfig.STUDY_MINIMUM_REWARD.get(), 1, VillagerSkillSet.MAX_VALUE);
        int configuredMax = Math.clamp(
                VillagerRetaliationConfig.STUDY_MAXIMUM_REWARD.get(), 1, VillagerSkillSet.MAX_VALUE);
        return new RewardRange(Math.min(configuredMin, configuredMax), Math.max(configuredMin, configuredMax));
    }

    private static boolean shouldPause(ServerLevel level, Villager villager) {
        return !VillagerRetaliationConfig.ENABLE_VILLAGER_STUDYING.get()
                || !villager.isAlive()
                || villager.isRemoved()
                || villager.isBaby()
                || VillagerDownedService.isDowned(villager)
                || HiredVillagerContractService.hasActiveOrPendingContract(villager)
                || PartyService.isRecruitedPartyVillager(level, villager.getUUID())
                || VillagerAiArbitration.interruptsStudy(level, villager);
    }

    private static void suppressOrdinaryActivity(Villager villager) {
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static void saveState(ServerLevel level, VillagerProfile profile, VillagerStudyState state) {
        if (profile.setStudyState(state, level.getGameTime())) {
            VillagerProfileSavedData.get(level).setDirty();
        }
    }

    private static int rollReward(Villager villager) {
        RewardRange range = configuredRewardRange();
        return villager.getRandom().nextIntBetweenInclusive(range.minimum(), range.maximum());
    }

    private static void syncCompletedProfile(ServerLevel level, Villager villager, VillagerProfile profile) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(villager) <= 128.0D * 128.0D) {
                VillagerReputationNetworking.sendProfile(player, villager, profile);
            }
        }
    }

    private static long worldGameTime(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    private static long saturatedAdd(long value, long increment) {
        return increment > 0L && value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    public enum Eligibility {
        ELIGIBLE,
        DISABLED,
        UNAVAILABLE,
        BABY,
        HIRED,
        RECRUITED,
        ALREADY_STUDYING,
        COOLDOWN,
        INVALID_SKILL,
        SKILL_MAXED
    }

    public record StartResult(boolean started, Eligibility eligibility, VillagerStudyState state) {
    }

    public record RewardRange(int minimum, int maximum) {
    }

    public record TickResult(boolean completed, @Nullable VillagerSkill skill, int appliedPoints, int newValue) {
        public static final TickResult NONE = new TickResult(false, null, 0, 0);
        public static final TickResult PAUSED = new TickResult(false, null, 0, 0);
        public static final TickResult RESUMED = new TickResult(false, null, 0, 0);
        public static final TickResult PROGRESSED = new TickResult(false, null, 0, 0);
        public static final TickResult CANCELLED_INVALID = new TickResult(false, null, 0, 0);
    }
}
