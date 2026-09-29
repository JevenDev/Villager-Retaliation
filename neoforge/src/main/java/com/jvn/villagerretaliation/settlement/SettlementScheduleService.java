package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;

public final class SettlementScheduleService {
    private SettlementScheduleService() {
    }

    static void reconcile(Villager villager) {
        reconcile(villager, villager.getBrain());
    }

    public static void reconcile(Villager villager, Brain<Villager> brain) {
        var schedule = brain.getSchedule();
        if (nightGuard(villager)) {
            if (!(schedule instanceof NightGuardSchedule)) brain.setSchedule(new NightGuardSchedule(schedule));
        } else if (schedule instanceof NightGuardSchedule previous) {
            brain.setSchedule(previous.base);
        }
    }

    static boolean resting(ServerLevel level, Villager villager) {
        reconcile(villager);
        return villager.getBrain().getSchedule().getActivityAt((int) Math.floorMod(level.getDayTime(), 24_000L)) == Activity.REST;
    }

    private static boolean nightGuard(Villager villager) {
        if (villager.isBaby() || !villager.isAlive() || (villager.getUUID().getLeastSignificantBits() & 1L) == 0L) return false;
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null || duty.area().type().role() != HiredVillagerRole.COMBAT || !duty.area().enabled()) return false;
        var settings = duty.town().effectiveWorkerSettings(villager.getUUID());
        if (settings != null) return settings.enabled();
        var state = HiredWorkStateStore.state(villager);
        return !state.contains("Enabled") || state.getBoolean("Enabled");
    }

    private static final class NightGuardSchedule extends Schedule {
        private final Schedule base;

        private NightGuardSchedule(Schedule base) {
            this.base = base;
        }

        @Override
        public Activity getActivityAt(int dayTime) {
            int time = Math.floorMod(dayTime, 24_000);
            return time >= 2_000 && time < 9_000 ? Activity.REST : this.base.getActivityAt(time);
        }
    }
}
