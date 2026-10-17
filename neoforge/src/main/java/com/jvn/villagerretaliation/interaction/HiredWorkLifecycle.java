package com.jvn.villagerretaliation.interaction;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class HiredWorkLifecycle {
    private HiredWorkLifecycle() {
    }

    public static void pauseWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, Lifecycle.PAUSE);
    }

    public static void cancelWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, Lifecycle.CANCEL);
    }

    public static void finishWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, Lifecycle.COMPLETE);
    }

    public static void transferWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, Lifecycle.TRANSFER);
    }

    private enum Lifecycle {
        PAUSE, CANCEL, COMPLETE, TRANSFER
    }

    private static void updateWorkLifecycle(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements,
            Lifecycle lifecycle) {
        HiredWorkSession session = HiredWorkSession.create(level, villager, role);
        if (session.worker() != null) {
            switch (lifecycle) {
                case PAUSE -> session.worker().pause(level, villager, session.context());
                case CANCEL -> session.worker().cancel(level, villager, session.context());
                case COMPLETE -> session.worker().complete(level, villager, session.context());
                case TRANSFER -> session.worker().transfer(level, villager, session.context());
            }
        } else {
            session.context().setProgressTicks(0);
        }
        com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService.releasePlanned(level, villager);
        if (lifecycle == Lifecycle.CANCEL || lifecycle == Lifecycle.TRANSFER) {
            HiredTemporaryWorkService.clearOrders(session.state());
            HiredWorkSession.invalidate(villager);
        }
        session.state().remove("NextWorkGameTime");
        HiredWorkStateStore.setStatus(session.state(), status, replacements);
    }

}
