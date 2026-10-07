package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.settlement.SettlementScheduleService;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class VillageRoutineGameTests {
    private VillageRoutineGameTests() {
    }

    @GameTest(template = "empty", batch = "village_routines")
    public static void preferencesAreStableVariedAndBounded(GameTestHelper helper) {
        var offsets = new HashSet<Integer>();
        for (int index = 0; index < 100; index++) {
            var id = new UUID(index * 83L, index * 101L);
            int offset = VillageRoutineSchedule.timingOffset(id, VillagerSocialAttributes.DEFAULT, 4L);
            helper.assertValueEqual(offset, VillageRoutineSchedule.timingOffset(id, VillagerSocialAttributes.DEFAULT, 4L),
                    "the same villager and day have the same routine");
            helper.assertTrue(Math.abs(offset) <= 240, "variation stays within twelve seconds");
            offsets.add(offset);
        }
        helper.assertTrue(offsets.size() > 20, "villagers have distinct timing preferences");
        helper.assertTrue(VillageRoutineSchedule.timingOffset(new UUID(8, 9), VillagerSocialAttributes.DEFAULT, 1)
                != VillageRoutineSchedule.timingOffset(new UUID(8, 9), VillagerSocialAttributes.DEFAULT, 2),
                "a later day introduces a small variation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "village_routines")
    public static void wrappingPreservesWorkDurationAndRestoresTheOriginalSchedule(GameTestHelper helper) {
        boolean previous = VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get();
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        var base = new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE)
                .changeActivityAt(2_000, Activity.WORK).changeActivityAt(9_000, Activity.MEET)
                .changeActivityAt(12_000, Activity.REST).build();
        try {
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(true);
            villager.getBrain().setSchedule(base);
            SettlementScheduleService.reconcile(villager, villager.getBrain());
            var wrapped = villager.getBrain().getSchedule();
            helper.assertTrue(wrapped != base, "ordinary villagers receive a routine");
            int work = 0;
            int rest = 0;
            for (int time = 0; time < 24_000; time++) {
                if (wrapped.getActivityAt(time) == Activity.WORK) work++;
                if (wrapped.getActivityAt(time) == Activity.REST) rest++;
            }
            helper.assertValueEqual(work, 7_000, "routine variation preserves the full work and restocking window");
            helper.assertValueEqual(rest, 12_000, "routine variation preserves rest duration");
            SettlementScheduleService.reconcile(villager, villager.getBrain());
            helper.assertTrue(villager.getBrain().getSchedule() == wrapped, "reconciliation never stacks wrappers");
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(false);
            SettlementScheduleService.reconcile(villager, villager.getBrain());
            helper.assertTrue(villager.getBrain().getSchedule() == base, "disabling restores the exact prior schedule");
        } finally {
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(previous);
            villager.discard();
        }
        helper.succeed();
    }
}
