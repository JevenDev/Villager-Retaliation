package com.jvn.villagerretaliation.interaction;

import static com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice;

import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import com.jvn.villagerretaliation.network.RecruitmentResultPayload.FailureReason;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

final class HiredWorkRequestHandler {
    private HiredWorkRequestHandler() { }

    static Optional<FailureReason> handleHiredRoleRequest(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action) {
        HiredVillagerRole role = RecruitmentActionMappings.role(action);
        if (role == null) {
            return Optional.empty();
        }
        if (!HiredVillagerContractService.isHiredBy(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.role_requires_hire");
            return Optional.of(FailureReason.NOT_OWNER);
        }
        if (!HiredVillagerContractService.setActiveRole(level, villager, role)) {
            sendVillagerNotice(
                    player,
                    villager,
                    "interaction.role_not_suitable",
                    Map.of("role", role.label())
            );
            return Optional.of(FailureReason.INVALID_ROLE);
        }
        sendVillagerNotice(
                player,
                villager,
                "interaction.role_assigned",
                Map.of("role", role.label())
        );
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
        return Optional.of(FailureReason.NONE);
    }

    static Optional<FailureReason> handleHiredWorkRequest(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            VillagerRecruitRequestPayload.Action action) {
        HiredVillagerRole configureRole = switch (action) {
            case CONFIGURE_COMBAT -> HiredVillagerRole.COMBAT;
            case CONFIGURE_HUNTING -> HiredVillagerRole.HUNTING;
            case CONFIGURE_MINING -> HiredVillagerRole.MINING;
            case CONFIGURE_LOGGING -> HiredVillagerRole.LOGGING;
            case CONFIGURE_FARMING -> HiredVillagerRole.FARMING;
            case CONFIGURE_FISHING -> HiredVillagerRole.FISHING;
            case CONFIGURE_BREWING -> HiredVillagerRole.BREWING;
            case CONFIGURE_BUILDER -> HiredVillagerRole.BUILDER;
            case CONFIGURE_ANIMAL_HANDLING -> HiredVillagerRole.ANIMAL_HANDLING;
            case CONFIGURE_NITWIT -> HiredVillagerRole.NITWIT;
            default -> null;
        };
        boolean workAction = configureRole != null
                || action == VillagerRecruitRequestPayload.Action.VIEW_WORK_STATUS
                || action == VillagerRecruitRequestPayload.Action.TOGGLE_WORK_ENABLED
                || action == VillagerRecruitRequestPayload.Action.TOGGLE_USE_ASSIGNED_SUPPLIES
                || action == VillagerRecruitRequestPayload.Action.TOGGLE_AUTO_DEPOSIT_OUTPUTS
                || action == VillagerRecruitRequestPayload.Action.TOGGLE_HORIZONTAL_MINING_FLOOR_PATCHING
                || action == VillagerRecruitRequestPayload.Action.CYCLE_CRAFTSMAN_MODE
                || action == VillagerRecruitRequestPayload.Action.STOP_BREWING
                || action == VillagerRecruitRequestPayload.Action.STOP_BUILDER_BUILD;
        if (!workAction) {
            return Optional.empty();
        }
        if (!HiredVillagerWorkService.canManageWork(level, villager, player)) {
            sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return Optional.of(FailureReason.NOT_OWNER);
        }
        if (configureRole != null && HiredVillagerContractService.activeRole(level, villager) != configureRole) {
            sendVillagerNotice(player, villager, "interaction.work.configure.requires_role", Map.of("role", configureRole.label()));
            return Optional.of(FailureReason.INVALID_ROLE);
        }
        if (action == VillagerRecruitRequestPayload.Action.CYCLE_CRAFTSMAN_MODE
                && HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.CRAFTSMAN) {
            return Optional.of(FailureReason.INVALID_ROLE);
        }
        switch (action) {
            case VIEW_WORK_STATUS -> HiredVillagerWorkService.sendStatus(player, level, villager);
            case TOGGLE_WORK_ENABLED -> HiredVillagerWorkService.toggleEnabled(player, level, villager);
            case TOGGLE_USE_ASSIGNED_SUPPLIES -> HiredVillagerWorkService.toggleAssignedSupplies(player, level, villager);
            case TOGGLE_AUTO_DEPOSIT_OUTPUTS -> HiredVillagerWorkService.toggleAutoDeposit(player, level, villager);
            case TOGGLE_HORIZONTAL_MINING_FLOOR_PATCHING -> HiredVillagerWorkService.toggleHorizontalMiningFloorPatching(player, level, villager);
            case CYCLE_CRAFTSMAN_MODE -> {
                var mode = com.jvn.villagerretaliation.interaction.work.CraftsmanWorker
                        .cycleMode(HiredWorkStateStore.state(villager));
                sendVillagerNotice(player, villager, "interaction.work.craftsman.mode_changed", Map.of("mode", mode.label()));
            }
            case STOP_BREWING -> { return Optional.of(stopBrewingOrder(player, level, villager)); }
            case STOP_BUILDER_BUILD -> { return Optional.of(VillagerInteractionService.cancelBuilderOrder(player, level, villager, HiredWorkStateStore.state(villager))); }
            default -> HiredVillagerWorkService.configureRole(player, level, villager, configureRole);
        }
        return Optional.of(FailureReason.NONE);
    }

    private static FailureReason stopBrewingOrder(ServerPlayer player, ServerLevel level, Villager villager) {
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.BREWING) {
            sendVillagerNotice(player, villager, "interaction.work.brewing.requires_role_change");
            return FailureReason.INVALID_ROLE;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        BrewingWorker.clearOrder(state);
        HiredVillagerWorkService.stopWork(level, villager, HiredVillagerRole.BREWING, "interaction.work.brewing.stopped");
        sendVillagerNotice(player, villager, "interaction.work.brewing.stopped");
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
        return FailureReason.NONE;
    }
}
