package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;

public final class VillageRoutineSchedule extends Schedule {
    private final Schedule base;
    private long day = Long.MIN_VALUE;
    private int offset;

    private VillageRoutineSchedule(Schedule base) {
        this.base = base;
    }

    public static Schedule reconcile(Villager villager, Schedule schedule) {
        boolean enabled = VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get()
                && villager.isAlive() && !VillagerWorkAuthority.hasWork(villager)
                && villager.level() instanceof ServerLevel;
        if (!enabled) return schedule instanceof VillageRoutineSchedule routine ? routine.base : schedule;
        VillageRoutineSchedule routine = schedule instanceof VillageRoutineSchedule current
                ? current : new VillageRoutineSchedule(schedule);
        ServerLevel level = (ServerLevel) villager.level();
        long day = Math.floorDiv(level.getDayTime(), 24_000L);
        if (routine.day != day) {
            var attributes = VillagerProfileManager.getProfile(level, villager.getUUID())
                    .map(profile -> profile.socialAttributes()).orElse(VillagerSocialAttributes.DEFAULT);
            routine.offset = timingOffset(villager.getUUID(), attributes, day);
            routine.day = day;
        }
        return routine;
    }

    public static Schedule original(Schedule schedule) {
        return schedule instanceof VillageRoutineSchedule routine ? routine.base : schedule;
    }

    public static int timingOffset(UUID id, VillagerSocialAttributes attributes, long day) {
        int preference = Math.floorMod(id.hashCode(), 301) - 150;
        int personality = attributes.proficiency() - attributes.charm();
        int variation = Math.floorMod(Long.hashCode(day ^ id.getMostSignificantBits()), 81) - 40;
        return Math.clamp(preference + personality + variation, -240, 240);
    }

    @Override
    public Activity getActivityAt(int dayTime) {
        return this.base.getActivityAt(Math.floorMod(dayTime + this.offset, 24_000));
    }
}
