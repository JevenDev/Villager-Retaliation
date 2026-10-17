package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorkerState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

enum SettlementWorkerHandoff {
    STANDARD(false),
    ENTITY(true),
    FISHING(true) {
        @Override
        CompoundTag capture(ServerLevel level, Villager villager) {
            var state = super.capture(level, villager);
            SettlementBackgroundFishing.capture(level, villager, state);
            return state;
        }
    },
    LOGGING(true) {
        @Override
        void restoreProgress(SettlementWorkerState worker, Villager villager) {
            LoggingHarvestPlan.copyState(worker.settings(), HiredWorkStateStore.state(villager));
        }
    },
    MINING(true) {
        @Override
        void restoreProgress(SettlementWorkerState worker, Villager villager) {
            MiningWorkerState.copyState(worker.settings(), HiredWorkStateStore.state(villager));
        }
    },
    HUNTING(true) {
        @Override
        void reconcile(SettlementWorkerState worker, Villager villager) {
            VillagerRetaliationHandler.restoreMeleeAttackTick(villager, worker.huntingAttackAt());
        }

        @Override
        void restoreProgress(SettlementWorkerState worker, Villager villager) {
            SettlementGuardService.copyProgress(worker.settings(), HiredWorkStateStore.state(villager));
        }
    },
    BREWING(false) {
        @Override
        void reconcile(SettlementWorkerState worker, Villager villager) {
            SettlementBrewingService.restore(worker, villager);
        }
    },
    BUILDER(true) {
        @Override
        CompoundTag capture(ServerLevel level, Villager villager) {
            var state = super.capture(level, villager);
            state.remove("SettlementConstructionPosition");
            return state;
        }
    };

    private final boolean capturesEntity;

    SettlementWorkerHandoff(boolean capturesEntity) {
        this.capturesEntity = capturesEntity;
    }

    static SettlementWorkerHandoff forRole(HiredVillagerRole role) {
        return switch (role) {
            case COMBAT, FARMING, ANIMAL_HANDLING -> ENTITY;
            case FISHING -> FISHING;
            case LOGGING -> LOGGING;
            case MINING -> MINING;
            case HUNTING -> HUNTING;
            case BREWING -> BREWING;
            case BUILDER -> BUILDER;
            case CRAFTSMAN, COOK, SMELTER, COURIER, NITWIT -> STANDARD;
        };
    }

    boolean capturesEntity() {
        return this.capturesEntity;
    }

    CompoundTag capture(ServerLevel level, Villager villager) {
        return HiredWorkStateStore.state(villager).copy();
    }

    void reconcile(SettlementWorkerState worker, Villager villager) {
    }

    void restoreProgress(SettlementWorkerState worker, Villager villager) {
    }
}
