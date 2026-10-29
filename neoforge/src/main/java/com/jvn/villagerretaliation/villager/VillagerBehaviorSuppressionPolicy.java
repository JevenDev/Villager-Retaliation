package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
import com.jvn.villagerretaliation.study.VillagerStudyService;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * One authoritative policy for vanilla behavior that conflicts with a villager state owned by
 * Villager Retaliation. Mod systems should query a specific behavior instead of duplicating
 * downed/hired/party checks.
 */
public final class VillagerBehaviorSuppressionPolicy {
    private static final Map<ControlState, EnumSet<Behavior>> SUPPRESSED = suppressionRules();

    private VillagerBehaviorSuppressionPolicy() {
    }

    public static ControlState state(Villager villager) {
        if (villager == null) {
            return ControlState.NORMAL;
        }
        if (VillagerDownedService.isDowned(villager)) {
            return ControlState.DOWNED;
        }
        if (villager.level() instanceof ServerLevel level
                && PartyVillagerContractService.isActivePartyVillager(level, villager)) {
            var state = com.jvn.villagerretaliation.party.CompanionSavedData.get(level).find(villager.getUUID());
            if (state == null || !state.getString("Status").equals("At home")) return ControlState.PARTIED;
        }
        if (HiredVillagerContractService.hasActiveOrPendingContract(villager)) {
            return ControlState.HIRED;
        }
        if (com.jvn.villagerretaliation.settlement.SettlementWorkforceService.duty(villager).isPresent()) {
            return ControlState.CIVIC;
        }
        if (villager.level() instanceof ServerLevel level
                && VillagerStudyService.isActivelyStudying(level, villager)) {
            return ControlState.STUDYING;
        }
        return ControlState.NORMAL;
    }

    public static boolean suppresses(Villager villager, Behavior behavior) {
        return suppresses(villager, behavior, state(villager));
    }

    private static boolean suppresses(Villager villager, Behavior behavior, ControlState state) {
        if (villager != null && behavior == Behavior.BREEDING && VillagerBreedingPolicy.naturalFamiliesEnabled()
                && state == ControlState.HIRED && villager.level() instanceof ServerLevel level) {
            return !VillagerBreedingPolicy.evaluateParent(level, villager).allowed();
        }
        if (villager != null
                && behavior == Behavior.SLEEPING && villager.level() instanceof ServerLevel level
                && HiredVillagerFocusService.isOnDutyGuard(level, villager)) {
            return true;
        }
        return SUPPRESSED.get(state).contains(behavior);
    }

    /**
     * Manual settlement movement and concrete hired-role tasks can suppress the vanilla Brain.
     * Their controllers yield to danger, conversation, trading, and applicable rest schedules.
     */
    public static boolean shouldSuppressVanillaBrainTick(ServerLevel level, Villager villager) {
        if (com.jvn.villagerretaliation.settlement.SettlementCourierService.controls(villager)
                || com.jvn.villagerretaliation.settlement.SettlementMoveService.controls(villager)) return true;
        ControlState state = state(villager);
        if (state == ControlState.DOWNED || state == ControlState.PARTIED) {
            return true;
        }
        if (state != ControlState.HIRED && state != ControlState.CIVIC) {
            return false;
        }
        if (villager.isTrading() || VillagerConversationService.isConversing(villager)) {
            return false;
        }
        return HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager);
    }

    /** Applies transition-safe cleanup and is intentionally idempotent for tick/load enforcement. */
    public static void enforce(ServerLevel level, Villager villager) {
        ControlState state = state(villager);
        if (state == ControlState.NORMAL) {
            return;
        }

        if (suppresses(villager, Behavior.BREEDING, state)) {
            VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
        }
        if (suppresses(villager, Behavior.GOSSIPING, state)) {
            Brain<Villager> brain = villager.getBrain();
            if (!villager.isTrading() && !VillagerConversationService.isConversing(villager)) {
                brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
            brain.eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
        }
        if (suppresses(villager, Behavior.VANILLA_ITEM_PICKUP, state)) {
            villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        }
        if (suppresses(villager, Behavior.JOB_SITE_CLAIMING, state)) {
            releasePotentialJobSite(level, villager);
        }
        if (villager.isSleeping() && suppresses(villager, Behavior.SLEEPING, state)) {
            villager.stopSleeping();
        }
        if (villager.isTrading() && suppresses(villager, Behavior.TRADING, state)) {
            villager.setTradingPlayer(null);
        }
    }

    /** Wakes vanilla scheduling after a contract or party state has actually been removed. */
    public static void restoreAfterRelease(ServerLevel level, Villager villager) {
        if (state(villager) != ControlState.NORMAL || villager.isNoAi()) {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        brain.setDefaultActivity(Activity.IDLE);
        brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    private static void releasePotentialJobSite(ServerLevel level, Villager villager) {
        GlobalPos potential = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).orElse(null);
        if (potential == null) {
            return;
        }
        ServerLevel siteLevel = level.getServer().getLevel(potential.dimension());
        if (siteLevel != null && siteLevel.getPoiManager().exists(potential.pos(), poi -> true)) {
            siteLevel.getPoiManager().release(potential.pos());
        }
        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    private static Map<ControlState, EnumSet<Behavior>> suppressionRules() {
        EnumMap<ControlState, EnumSet<Behavior>> rules = new EnumMap<>(ControlState.class);
        rules.put(ControlState.NORMAL, EnumSet.noneOf(Behavior.class));
        rules.put(ControlState.DOWNED, EnumSet.allOf(Behavior.class));
        rules.put(ControlState.HIRED, EnumSet.of(
                Behavior.BREEDING,
                Behavior.GOSSIPING,
                Behavior.WANDERING,
                Behavior.JOB_SITE_CLAIMING,
                Behavior.VILLAGE_MIGRATION));
        rules.put(ControlState.CIVIC, EnumSet.of(
                Behavior.JOB_SITE_CLAIMING,
                Behavior.VILLAGE_MIGRATION));
        rules.put(ControlState.PARTIED, EnumSet.of(
                Behavior.TRADING,
                Behavior.BREEDING,
                Behavior.GOSSIPING,
                Behavior.VANILLA_WORKING,
                Behavior.SLEEPING,
                Behavior.WANDERING,
                Behavior.VANILLA_ITEM_PICKUP,
                Behavior.VANILLA_PANIC,
                Behavior.JOB_SITE_CLAIMING,
                Behavior.VILLAGE_MIGRATION));
        rules.put(ControlState.STUDYING, EnumSet.of(
                Behavior.BREEDING,
                Behavior.GOSSIPING,
                Behavior.VANILLA_WORKING,
                Behavior.WANDERING,
                Behavior.VANILLA_ITEM_PICKUP));
        return Map.copyOf(rules);
    }

    public enum ControlState {
        NORMAL,
        DOWNED,
        HIRED,
        CIVIC,
        PARTIED,
        STUDYING
    }

    public enum Behavior {
        TRADING,
        BREEDING,
        GOSSIPING,
        VANILLA_WORKING,
        SLEEPING,
        WANDERING,
        VANILLA_ITEM_PICKUP,
        VANILLA_PANIC,
        JOB_SITE_CLAIMING,
        VILLAGE_MIGRATION,
        COMBAT,
        INTERACTION_MENUS
    }
}
