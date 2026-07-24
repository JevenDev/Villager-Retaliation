package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Pure authorization boundary for recruitment actions. */
public final class RecruitmentPolicy {
    private RecruitmentPolicy() {
    }

    public static Decision mayHire(ServerLevel level, Villager villager, ServerPlayer player) {
        Decision inputs = validInputs(level, villager, player);
        if (!inputs.allowed()) return inputs;
        if (villager.isBaby()) return Decision.denied(DenialReason.BABY);
        if (!hasTrust(level, villager, player, VillagerReputationLevel.NEUTRAL)) {
            return Decision.denied(DenialReason.INSUFFICIENT_REPUTATION);
        }
        VillagerAssignmentSnapshot assignment = VillagerAssignmentStore.snapshot(villager);
        if (assignment.state() == VillagerAssignmentState.HIRED && !assignment.ownedBy(player.getUUID())) {
            return Decision.denied(DenialReason.OWNED_BY_ANOTHER_PLAYER);
        }
        return Decision.allowedDecision();
    }

    public static Decision mayCommand(
            ServerLevel level, Villager villager, ServerPlayer player, VillagerAssignmentCommand command) {
        Decision inputs = validInputs(level, villager, player);
        if (!inputs.allowed()) return inputs;
        if (command == null) return Decision.denied(DenialReason.UNSUPPORTED_COMMAND);
        VillagerAssignmentSnapshot assignment = VillagerAssignmentStore.snapshot(villager);
        if (PartyVillagerContractService.hasPartyEntityReference(villager)) {
            return Decision.denied(DenialReason.PARTY_CONTROLLED);
        }
        if (assignment.state() == VillagerAssignmentState.HIRED) {
            return assignment.ownedBy(player.getUUID())
                    ? Decision.allowedDecision()
                    : Decision.denied(DenialReason.OWNED_BY_ANOTHER_PLAYER);
        }
        if (command != VillagerAssignmentCommand.FOLLOW) {
            return Decision.denied(DenialReason.NOT_HIRED);
        }
        return VillagerAssignmentStore.commandOwner(villager)
                .filter(owner -> !owner.equals(player.getUUID()))
                .map(owner -> Decision.denied(DenialReason.COMMANDED_BY_ANOTHER_PLAYER))
                .orElseGet(() -> hasTrust(level, villager, player, VillagerReputationLevel.NEUTRAL)
                        ? Decision.allowedDecision()
                        : Decision.denied(DenialReason.INSUFFICIENT_REPUTATION));
    }

    public static Decision mayEquip(ServerLevel level, Villager villager, ServerPlayer player) {
        Decision inputs = validInputs(level, villager, player);
        if (!inputs.allowed()) return inputs;
        return VillagerAssignmentStore.snapshot(villager).ownedBy(player.getUUID())
                ? Decision.allowedDecision()
                : Decision.denied(DenialReason.NOT_OWNER);
    }

    public static Decision mayDismiss(ServerLevel level, Villager villager, ServerPlayer player) {
        Decision inputs = validInputs(level, villager, player);
        if (!inputs.allowed()) return inputs;
        VillagerAssignmentSnapshot assignment = VillagerAssignmentStore.snapshot(villager);
        if (assignment.state() != VillagerAssignmentState.HIRED) return Decision.denied(DenialReason.NOT_HIRED);
        return assignment.ownedBy(player.getUUID())
                ? Decision.allowedDecision()
                : Decision.denied(DenialReason.NOT_OWNER);
    }

    private static Decision validInputs(ServerLevel level, Villager villager, ServerPlayer player) {
        if (level == null || villager == null || player == null) return Decision.denied(DenialReason.INVALID_CONTEXT);
        if (!villager.isAlive()) return Decision.denied(DenialReason.VILLAGER_UNAVAILABLE);
        if (!player.isAlive() || player.isSpectator()) return Decision.denied(DenialReason.PLAYER_UNAVAILABLE);
        if (villager.level() != level || player.level() != level) return Decision.denied(DenialReason.DIFFERENT_DIMENSION);
        return Decision.allowedDecision();
    }

    private static boolean hasTrust(
            ServerLevel level, Villager villager, ServerPlayer player, VillagerReputationLevel required) {
        return VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= required.trustRank();
    }

    public enum DenialReason {
        NONE,
        INVALID_CONTEXT,
        BABY,
        VILLAGER_UNAVAILABLE,
        PLAYER_UNAVAILABLE,
        DIFFERENT_DIMENSION,
        INSUFFICIENT_REPUTATION,
        NOT_HIRED,
        NOT_OWNER,
        OWNED_BY_ANOTHER_PLAYER,
        COMMANDED_BY_ANOTHER_PLAYER,
        PARTY_CONTROLLED,
        UNSUPPORTED_COMMAND
    }

    public record Decision(boolean allowed, DenialReason reason) {
        public Decision {
            if (reason == null) reason = DenialReason.INVALID_CONTEXT;
            if (allowed && reason != DenialReason.NONE) throw new IllegalArgumentException("Allowed decisions have no denial reason");
            if (!allowed && reason == DenialReason.NONE) throw new IllegalArgumentException("Denied decisions require a reason");
        }

        public static Decision allowedDecision() {
            return new Decision(true, DenialReason.NONE);
        }

        public static Decision denied(DenialReason reason) {
            return new Decision(false, reason);
        }
    }
}
