package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
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
            return ControlState.PARTIED;
        }
        if (HiredVillagerContractService.hasActiveOrPendingContract(villager)) {
            return ControlState.HIRED;
        }
        return ControlState.NORMAL;
    }

    public static boolean suppresses(Villager villager, Behavior behavior) {
        return SUPPRESSED.get(state(villager)).contains(behavior);
    }

    /**
     * Vanilla rest and the hired-farmer bridge are deliberate compatibility exceptions. Everything
     * else for a controlled villager is driven by the mod's work, follow, combat, or recovery loop.
     */
    public static boolean shouldSuppressVanillaBrainTick(ServerLevel level, Villager villager) {
        ControlState state = state(villager);
        if (state == ControlState.DOWNED || state == ControlState.PARTIED) {
            return true;
        }
        if (state != ControlState.HIRED) {
            return false;
        }
        if (villager.isTrading() || VillagerConversationService.isConversing(villager)) {
            return false;
        }
        Brain<Villager> brain = villager.getBrain();
        Activity scheduled = brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L));
        if (villager.isSleeping() || brain.isActive(Activity.REST) || scheduled == Activity.REST) {
            return false;
        }
        return !HiredVillagerFocusService.shouldAllowCompatibleVanillaFarmerBrain(level, villager);
    }

    /** Applies transition-safe cleanup and is intentionally idempotent for tick/load enforcement. */
    public static void enforce(ServerLevel level, Villager villager) {
        ControlState state = state(villager);
        if (state == ControlState.NORMAL) {
            return;
        }

        if (suppresses(villager, Behavior.BREEDING)) {
            VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
        }
        if (suppresses(villager, Behavior.GOSSIPING)) {
            Brain<Villager> brain = villager.getBrain();
            if (!villager.isTrading() && !VillagerConversationService.isConversing(villager)) {
                brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
            brain.eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
        }
        if (suppresses(villager, Behavior.VANILLA_ITEM_PICKUP)) {
            villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        }
        if (suppresses(villager, Behavior.JOB_SITE_CLAIMING)) {
            releasePotentialJobSite(level, villager);
        }
        if (suppresses(villager, Behavior.SLEEPING) && villager.isSleeping()) {
            villager.stopSleeping();
        }
        if (suppresses(villager, Behavior.TRADING) && villager.isTrading()) {
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
                Behavior.TRADING,
                Behavior.BREEDING,
                Behavior.GOSSIPING,
                Behavior.VANILLA_WORKING,
                Behavior.WANDERING,
                Behavior.VANILLA_ITEM_PICKUP,
                Behavior.VANILLA_PANIC,
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
        return Map.copyOf(rules);
    }

    public enum ControlState {
        NORMAL,
        DOWNED,
        HIRED,
        PARTIED
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
