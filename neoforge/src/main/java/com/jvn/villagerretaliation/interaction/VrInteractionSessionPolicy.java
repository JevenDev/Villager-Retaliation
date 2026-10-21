package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

final class VrInteractionSessionPolicy implements InteractionSessionPolicy {
    static final VrInteractionSessionPolicy INSTANCE = new VrInteractionSessionPolicy();
    private static final int IDLE_TIMEOUT_TICKS = 20 * 60 * 2;
    private static final int FOREIGN_HIRED_WORKER_IDLE_TIMEOUT_TICKS = IDLE_TIMEOUT_TICKS / 2;
    private static final double FORCED_DIALOGUE_APPROACH_START_DISTANCE = 5.5D;
    private static final double FORCED_DIALOGUE_APPROACH_STOP_DISTANCE = 4.0D;
    private static final double FORCED_DIALOGUE_APPROACH_SPEED =
            com.jvn.villagerretaliation.villager.VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER;

    private VrInteractionSessionPolicy() { }

    @Override public boolean canStart(ServerPlayer player, LivingEntity actor, InteractionSessions.StartMode mode) {
        if (!(actor instanceof Villager villager)) return false;
        boolean canStart = switch (mode) {
            case NORMAL -> VillagerInteractionService.canUseInteractionSystem(player, villager);
            case FORCED -> VillagerInteractionService.canUseForcedInteractionSystem(player, villager);
            case AUTHORIZED_FORCED -> VillagerInteractionService.canUseForcedInteractionSystemIgnoringDisposition(player, villager);
        };
        return canStart && (mode != InteractionSessions.StartMode.NORMAL
                || VillagerInteractionService.canStartNormalConversation(player, villager));
    }

    @Override public void prepare(ServerPlayer player, LivingEntity actor) {
        VillagerInteractionService.prepareForInteractionSession(player, (Villager) actor);
    }

    @Override public boolean valid(ServerPlayer player, LivingEntity actor, InteractionSession session) {
        var villager = (Villager) actor;
        return session.forced() ? VillagerInteractionService.shouldStayForcedConversationSession(player, villager)
                : VillagerInteractionService.shouldStayConversable(player, villager, false)
                        && villager.getTarget() == null && villager.getLastHurtByMob() == null;
    }

    @Override public long idleTimeout(ServerPlayer player, LivingEntity actor, InteractionSession session) {
        return VillagerInteractionService.isForeignHiredWorker(player, (Villager) actor)
                ? FOREIGN_HIRED_WORKER_IDLE_TIMEOUT_TICKS : IDLE_TIMEOUT_TICKS;
    }

    @Override public void hold(ServerPlayer player, LivingEntity actor, InteractionSession session) {
        holdVillager((Villager) actor, player, session);
    }

    @Override public void tick(ServerPlayer player, LivingEntity actor, InteractionSession session) {
        if (actor.tickCount % 20 == 0) com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.tick(
                VillagerInteractionService.createDialogueContext(player.serverLevel(), player, (Villager) actor));
    }

    @Override public void ended(ServerPlayer player, InteractionSession session, boolean notifyClient, boolean finishExchanges) {
        if (notifyClient) VillagerConversationService.notifyEnded(player, session, finishExchanges);
    }

    public static void holdSharedForcedParticipant(Villager villager, ServerPlayer player) {
        if (!VillagerInteractionService.shouldStayForcedConversationSession(player, villager)) {
            return;
        }
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }
        facePlayer(villager, player);
        if (approachPlayerForForcedDialogue(villager, player)) {
            return;
        }
        if (VillagerRetaliationConfig.FREEZE_VILLAGER_DURING_DIALOGUE.get() && !villager.getNavigation().isDone()) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        }
    }

    private static void holdVillager(Villager villager, ServerPlayer player, InteractionSession session) {
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }
        facePlayer(villager, player);
        if (session.forced() && approachPlayerForForcedDialogue(villager, player)) {
            return;
        }
        if (VillagerRetaliationConfig.FREEZE_VILLAGER_DURING_DIALOGUE.get() && !villager.getNavigation().isDone()) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        }
    }

    private static void facePlayer(Villager villager, ServerPlayer player) {
        double dx = player.getX() - villager.getX();
        double dz = player.getZ() - villager.getZ();
        if (dx * dx + dz * dz < 1.0E-8) return;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(player.getEyeY() - villager.getEyeY(), Math.sqrt(dx * dx + dz * dz)));
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        villager.setYRot(Mth.approachDegrees(villager.getYRot(), yaw, 30.0F));
        villager.setYBodyRot(villager.getYRot());
        villager.setYHeadRot(Mth.approachDegrees(villager.getYHeadRot(), yaw, 30.0F));
        villager.setXRot(Mth.approachDegrees(villager.getXRot(), pitch, 30.0F));
    }

    private static boolean approachPlayerForForcedDialogue(Villager villager, ServerPlayer player) {
        double distanceSqr = villager.distanceToSqr(player);
        if (distanceSqr <= FORCED_DIALOGUE_APPROACH_STOP_DISTANCE * FORCED_DIALOGUE_APPROACH_STOP_DISTANCE) {
            if (!villager.getNavigation().isDone()) {
                VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            }
            return false;
        }
        if (distanceSqr <= FORCED_DIALOGUE_APPROACH_START_DISTANCE * FORCED_DIALOGUE_APPROACH_START_DISTANCE) {
            return !villager.getNavigation().isDone();
        }
        VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
        return villager.getNavigation().moveTo(player, FORCED_DIALOGUE_APPROACH_SPEED);
    }
}
