package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredRouteNavigator;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;

final class HiredWorkConfigurationService {

    private HiredWorkConfigurationService() {
    }

    public static void toggleEnabled(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        state.putBoolean("Enabled", !state.getBoolean("Enabled"));
        HiredWorkStateStore.setStatus(state, state.getBoolean("Enabled")
                ? "interaction.work.status.enabled"
                : "interaction.work.status.paused");
        sendStatusNotice(player, villager, state);
    }

    public static void changeRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int max = HiredWorkParameters.maxWorkRadius(level, villager, role);
        HiredWorkArea area = HiredWorkStateStore.workAreaWithinMax(state, villager, max);
        int radius = HiredWorkArea.clampRadius(area.horizontalRadius() + delta, HiredWorkParameters.MIN_WORK_RADIUS, max);
        HiredWorkArea.fromCenter(area.center(), radius, area.verticalRadius(), true).clampedTo(max).save(state);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.horizontal_radius", Map.of(
                "radius", Integer.toString(radius),
                "max", Integer.toString(max)));
        sendStatusNotice(player, villager, state);
    }

    public static void changeVerticalRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int max = HiredWorkParameters.maxWorkRadius(level, villager, role);
        HiredWorkArea area = HiredWorkStateStore.workAreaWithinMax(state, villager, max);
        int verticalRadius = HiredWorkArea.clampRadius(area.verticalRadius() + delta, 1, max);
        HiredWorkArea.fromCenter(area.center(), area.horizontalRadius(), verticalRadius, true).clampedTo(max).save(state);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.vertical_radius", Map.of(
                "radius", Integer.toString(verticalRadius),
                "max", Integer.toString(max)));
        sendStatusNotice(player, villager, state);
    }

    public static void changeBounds(ServerPlayer player, ServerLevel level, Villager villager, Direction direction, int delta) {
        if (direction == null || delta == 0) {
            return;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int max = HiredWorkParameters.maxWorkRadius(level, villager, role);
        HiredWorkArea area = HiredWorkStateStore.workAreaWithinMax(state, villager, max);
        if (role == HiredVillagerRole.HUNTING) {
            changeCenteredHuntingRadius(player, villager, state, area, max, direction, delta);
            return;
        }
        BlockPos center = area.center();
        BlockPos min = area.min();
        BlockPos maxPos = area.max();
        switch (direction) {
            case NORTH -> min = new BlockPos(min.getX(), min.getY(), Mth.clamp(min.getZ() - delta, center.getZ() - max, center.getZ()));
            case SOUTH -> maxPos = new BlockPos(maxPos.getX(), maxPos.getY(), Mth.clamp(maxPos.getZ() + delta, center.getZ(), center.getZ() + max));
            case WEST -> min = new BlockPos(Mth.clamp(min.getX() - delta, center.getX() - max, center.getX()), min.getY(), min.getZ());
            case EAST -> maxPos = new BlockPos(Mth.clamp(maxPos.getX() + delta, center.getX(), center.getX() + max), maxPos.getY(), maxPos.getZ());
            case DOWN -> min = new BlockPos(min.getX(), Mth.clamp(min.getY() - delta, center.getY() - max, center.getY()), min.getZ());
            case UP -> maxPos = new BlockPos(maxPos.getX(), Mth.clamp(maxPos.getY() + delta, center.getY(), center.getY() + max), maxPos.getZ());
        }
        HiredWorkArea.fromBounds(min, maxPos, true).clampedTo(max).save(state);
        HiredWorkArea updated = HiredWorkStateStore.workArea(state, villager);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.bounds", Map.of(
                "dimensions", dimensions(updated),
                "bounds", updated.boundsDescription()));
        sendStatusNotice(player, villager, state);
    }

    private static void changeCenteredHuntingRadius(
            ServerPlayer player,
            Villager villager,
            CompoundTag state,
            HiredWorkArea area,
            int max,
            Direction direction,
            int delta) {
        boolean vertical = direction.getAxis() == Direction.Axis.Y;
        int horizontalRadius = area.horizontalRadius();
        int verticalRadius = area.verticalRadius();
        if (vertical) {
            verticalRadius = HiredWorkArea.clampRadius(verticalRadius + delta, 1, max);
        } else {
            horizontalRadius = HiredWorkArea.clampRadius(horizontalRadius + delta, HiredWorkParameters.MIN_WORK_RADIUS, max);
        }
        HiredWorkArea.fromCenter(area.center(), horizontalRadius, verticalRadius, true).clampedTo(max).save(state);
        HiredWorkStateStore.setStatus(state, vertical
                ? "interaction.work.status.vertical_radius"
                : "interaction.work.status.horizontal_radius", Map.of(
                "radius", Integer.toString(vertical ? verticalRadius : horizontalRadius),
                "max", Integer.toString(max)));
        sendStatusNotice(player, villager, state);
    }

    public static void setWorkCenterHere(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, player.blockPosition(), false);
    }

    public static void resetWorkCenterToVillager(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, villager.blockPosition(), true);
    }

    public static void previewWorkArea(ServerPlayer player, ServerLevel level, Villager villager) {
        if (builderIgnoresWorkArea(player, level, villager, HiredVillagerContractService.activeRole(level, villager))) {
            return;
        }
        HiredWorkArea area = workArea(level, villager);
        if (!area.usable()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.status.no_work_area");
            return;
        }
        com.jvn.villagerretaliation.item.HiredStorageClipboardItem.sendWorkAreaOutline(player, level, villager);
    }

    public static void toggleAssignedSupplies(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        state.putBoolean("UseAssignedStorageForSupplies", !state.getBoolean("UseAssignedStorageForSupplies"));
        HiredWorkStateStore.setStatus(state, state.getBoolean("UseAssignedStorageForSupplies")
                ? "interaction.work.status.assigned_supplies_enabled"
                : "interaction.work.status.assigned_supplies_disabled");
        sendStatusNotice(player, villager, state);
    }

    public static void toggleAutoDeposit(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        state.putBoolean("AutoDepositOutputs", !state.getBoolean("AutoDepositOutputs"));
        HiredWorkStateStore.setStatus(state, state.getBoolean("AutoDepositOutputs")
                ? "interaction.work.status.auto_deposit_enabled"
                : "interaction.work.status.auto_deposit_disabled");
        sendStatusNotice(player, villager, state);
    }

    public static void configureRole(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        switch (role) {
            case COMBAT -> {
                HiredCombatMode current = HiredCombatMode.fromState(state);
                HiredCombatMode next = current.next();
                state.putString(HiredCombatMode.STATE_TAG, next.serializedName());
                HiredWorkStateStore.setStatus(state, "interaction.work.status.combat_orders", Map.of("mode", next.label()));
            }
            case MINING -> {
                HiredMiningMode current = HiredMiningMode.fromState(state);
                HiredMiningMode next = current.next();
                state.putString(HiredMiningMode.STATE_TAG, next.serializedName());
                HiredWorkSession session = HiredWorkSession.active(level, villager);
                MiningWorker.resetForModeChange(level, villager, session.context(), next);
                HiredWorkStateStore.setStatus(state, "interaction.work.status.mining_orders", Map.of("mode", next.label()));
            }
            case HUNTING -> {
                HiredHuntingTargets.initializeDefaults(state);
                HiredWorkSession session = HiredWorkSession.active(level, villager);
                HiredWorkPlan.clear(session.context());
                session.context().setProgressTicks(0);
                HiredWorkStateStore.setStatus(state, "interaction.work.status.hunting_orders", Map.of("mode", HiredHuntingTargets.selectionLabel(state)));
            }
            case LOGGING -> {
                HiredWorkStateStore.setStatus(state, "interaction.work.status.logging_filter", Map.of("filter", HiredLoggingFilters.selectionLabel(state)));
            }
            case FARMING -> HiredWorkStateStore.setStatus(state, "interaction.work.status.farming_fields", Map.of(
                    "till_soil", HiredFarmingOptions.tillSoil(state) ? "enabled" : "disabled"));
            case BREWING -> HiredWorkStateStore.setStatus(
                    state,
                    BrewingWorker.orderSummaryKey(level, state),
                    BrewingWorker.orderSummaryReplacements(level, VillagerLocale.locale(player), state));
            case BUILDER -> {
                if (BuilderTaskState.hasTask(state)) {
                    HiredWorkStateStore.setStatus(state, "interaction.work.builder.status_summary", BuilderTaskState.replacements(state));
                } else {
                    HiredWorkStateStore.setStatus(state, "interaction.work.builder.choose_structure");
                }
            }
            case ANIMAL_HANDLING -> HiredWorkStateStore.setStatus(state, "interaction.work.status.animal_handling_config", animalHandlingReplacements(state));
            case NITWIT -> HiredWorkStateStore.setStatus(state, "interaction.work.status.nitwit_focus");
            default -> HiredWorkStateStore.setStatus(state, "interaction.work.status.no_extra_setup", Map.of("role", role.label()));
        }
        sendStatusNotice(player, villager, state);
    }

    public static void toggleLoggingFilter(ServerPlayer player, ServerLevel level, Villager villager, String filterId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.LOGGING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.LOGGING.label()));
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredLoggingFilters.toggleFilter(state, filterId);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.logging_filter", Map.of("filter", HiredLoggingFilters.selectionLabel(state)));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleLoggingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.LOGGING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.LOGGING.label()));
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredLoggingOptions.ToggleResult result = HiredLoggingOptions.toggle(state, optionId);
        if (result.invalid()) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.logging_option", Map.of(
                "option", HiredLoggingOptions.label(result.optionId()),
                "state", result.enabled() ? "enabled" : "disabled"));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleHorizontalMiningFloorPatching(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        boolean enabled = MiningHorizontalOptions.togglePatchFloor(state);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        MiningWorker.resetForOptionChange(level, villager, session.context(), HiredMiningMode.fromState(state));
        HiredWorkStateStore.setStatus(state, "interaction.work.status.mining_orders", Map.of(
                "mode", "Horizontal floor patching " + (enabled ? "enabled" : "disabled")));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleFarmingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.FARMING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.FARMING.label()));
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredFarmingOptions.ToggleResult result = HiredFarmingOptions.toggle(state, optionId);
        if (result.invalid()) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.farming_option", Map.of(
                "option", HiredFarmingOptions.label(result.optionId()),
                "state", HiredFarmingOptions.CROP.equals(result.optionId()) ? HiredFarmingOptions.crop(state).label()
                        : result.enabled() ? "enabled" : "disabled"));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleHuntingTarget(ServerPlayer player, ServerLevel level, Villager villager, String targetId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.HUNTING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.HUNTING.label()));
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredHuntingTargets.ToggleResult result = HiredHuntingTargets.toggle(state, targetId);
        if (result.invalid()) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.hunting_target", Map.of(
                "target", HiredHuntingTargets.label(result.optionId()),
                "state", result.enabled() ? "enabled" : "disabled",
                "mode", HiredHuntingTargets.selectionLabel(state)));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleAnimalBreedingTarget(ServerPlayer player, ServerLevel level, Villager villager, String targetId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.ANIMAL_HANDLING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.ANIMAL_HANDLING.label()));
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredAnimalBreedingTargets.toggleTarget(state, targetId);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.animal_handling_config", animalHandlingReplacements(state));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleAnimalHandlingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.ANIMAL_HANDLING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.ANIMAL_HANDLING.label()));
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredAnimalHandlingOptions.ToggleResult result = HiredAnimalHandlingOptions.toggle(state, optionId);
        if (result.invalid()) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.animal_handling_option", Map.of(
                "option", HiredAnimalHandlingOptions.label(result.optionId()), "state", result.enabled() ? "enabled" : "disabled"));
        sendStatusNotice(player, villager, state);
    }

    public static void setAnimalCullCap(ServerPlayer player, ServerLevel level, Villager villager, int cap) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.ANIMAL_HANDLING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.ANIMAL_HANDLING.label()));
            return;
        }
        if (cap != HiredAnimalCullSettings.DISABLED_CAP && !HiredAnimalCullSettings.isValidCap(cap)) {
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredAnimalCullSettings.setCap(state, cap);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        HiredWorkStateStore.setStatus(state, "interaction.work.status.animal_handling_config", animalHandlingReplacements(state));
        sendStatusNotice(player, villager, state);
    }

    public static boolean canManageWork(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredVillagerContractService.isHiredBy(level, villager, player);
    }

    private static Map<String, String> animalHandlingReplacements(CompoundTag state) {
        return Map.of(
                "target", HiredAnimalBreedingTargets.selectionLabel(state),
                "cap", HiredAnimalCullSettings.selectionLabel(state),
                "shearing", HiredAnimalHandlingOptions.shearSheep(state) ? "enabled" : "disabled");
    }

    public static void initializeWorkArea(ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = VillagerWorkAuthority.activeRole(level, villager);
        HiredWorkStateStore.workAreaWithinMax(state, villager, HiredWorkParameters.maxWorkRadius(level, villager, role));
    }

    public static HiredWorkArea workArea(ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = VillagerWorkAuthority.activeRole(level, villager);
        return HiredWorkParameters.jobSite(level, villager, role, state, HiredWorkParameters.maxWorkRadius(level, villager, role)).workArea();
    }

    public static HiredJobSite jobSite(ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = VillagerWorkAuthority.activeRole(level, villager);
        return HiredWorkParameters.jobSite(level, villager, role, state, HiredWorkParameters.maxWorkRadius(level, villager, role));
    }

    public static HiredRoute route(ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        return HiredRoute.fromState(state);
    }

    public static boolean setRoute(ServerPlayer player, ServerLevel level, Villager villager, HiredRoute route) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (!HiredVillagerRoleSettings.supportsRoutes(role)) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_unsupported_job",
                    Component.translatable("villagerretaliation.gui.job_stats.role." + role.serializedName())), true);
            return false;
        }
        HiredRoute safeRoute = route == null ? HiredRoute.empty() : route.validatedChain();
        if (safeRoute.isEmpty()) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.build_route_first"), true);
            return false;
        }
        safeRoute.save(state);
        com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.setupCommitted(villager,player.getUUID(),"work_route_committed",Map.of("nodes",Integer.toString(safeRoute.nodes().size()),"loop",Boolean.toString(safeRoute.loop())));
        HiredRouteNavigator.clearProgress(state);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, safeRoute.first());
        HiredWorkStateStore.setStatus(state, "interaction.work.status.waiting_tick");
        return true;
    }

    public static boolean clearRoute(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        if (HiredRoute.fromState(state).isEmpty()) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.no_route_assigned"), true);
            return false;
        }
        HiredRoute.clear(state);
        HiredRouteNavigator.clearProgress(state);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        return true;
    }

    public static boolean setWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos first, BlockPos second) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return false;
        }
        int maxRadius = HiredWorkParameters.maxWorkRadius(level, villager, role);
        HiredWorkArea requested = HiredWorkArea.fromBounds(first, second, true);
        if (role == HiredVillagerRole.HUNTING) {
            HiredWorkArea current = HiredWorkStateStore.workAreaWithinMax(state, villager, maxRadius);
            int horizontalRadius = current.explicitlyAssigned()
                    ? current.horizontalRadius()
                    : HiredVillagerRoleSettings.defaultHorizontalRadius(role, HiredWorkParameters.MIN_WORK_RADIUS, maxRadius);
            int verticalRadius = current.explicitlyAssigned()
                    ? current.verticalRadius()
                    : HiredVillagerRoleSettings.defaultVerticalRadius(role, maxRadius);
            HiredWorkArea.fromCenter(requested.center(), horizontalRadius, verticalRadius, true)
                    .clampedTo(maxRadius)
                    .save(state);
            HiredWorkStateStore.setStatus(state, "interaction.work.status.center_set_here", Map.of("range", HiredWorkStateStore.workArea(state, villager).rangeDescription()));
            sendStatusNotice(player, villager, state);
            com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.setupCommitted(villager, player.getUUID(), "work_area_committed", Map.of());
            return true;
        }
        HiredWorkArea area = requested.clampedTo(maxRadius);
        boolean capped = requested.horizontalRadius() > area.horizontalRadius()
                || requested.verticalRadius() > area.verticalRadius();
        area.save(state);
        if (role == HiredVillagerRole.MINING) {
            HiredWorkSession session = HiredWorkSession.active(level, villager);
            MiningWorker.resetForWorkAreaChange(
                    level,
                    villager,
                    session.context(),
                    HiredMiningMode.fromState(state));
        }
        HiredWorkStateStore.setStatus(state, capped
                ? "interaction.work.status.custom_box_capped"
                : "interaction.work.status.custom_box", Map.of("dimensions", dimensions(area)));
        sendStatusNotice(player, villager, state);
        com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.setupCommitted(villager, player.getUUID(), "work_area_committed", Map.of());
        return true;
    }

    private static void assignCenteredWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos center, boolean villagerCenter) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int maxRadius = HiredWorkParameters.maxWorkRadius(level, villager, role);
        HiredWorkArea current = HiredWorkStateStore.workAreaWithinMax(state, villager, maxRadius);
        int horizontalRadius = current.explicitlyAssigned()
                ? current.horizontalRadius()
                : HiredVillagerRoleSettings.defaultHorizontalRadius(role, HiredWorkParameters.MIN_WORK_RADIUS, maxRadius);
        int verticalRadius = current.explicitlyAssigned()
                ? current.verticalRadius()
                : HiredVillagerRoleSettings.defaultVerticalRadius(role, maxRadius);
        HiredWorkArea.fromCenter(center, horizontalRadius, verticalRadius, true).clampedTo(maxRadius).save(state);
        if (role == HiredVillagerRole.MINING) {
            HiredWorkSession session = HiredWorkSession.active(level, villager);
            MiningWorker.resetForWorkAreaChange(
                    level,
                    villager,
                    session.context(),
                    HiredMiningMode.fromState(state));
        }
        HiredWorkStateStore.setStatus(state, villagerCenter
                ? "interaction.work.status.center_reset_to_villager"
                : "interaction.work.status.center_set_here", Map.of("range", HiredWorkStateStore.workArea(state, villager).rangeDescription()));
        sendStatusNotice(player, villager, state);
        com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.setupCommitted(villager, player.getUUID(), "work_area_committed", Map.of());
    }

    private static boolean builderIgnoresWorkArea(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        if (role != HiredVillagerRole.BUILDER) {
            return false;
        }
        VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.builder.no_job_site");
        return true;
    }

    private static String dimensions(HiredWorkArea area) {
        return (area.max().getX() - area.min().getX() + 1)
                + "x" + (area.max().getY() - area.min().getY() + 1)
                + "x" + (area.max().getZ() - area.min().getZ() + 1);
    }

    private static void sendStatusNotice(ServerPlayer player, Villager villager, CompoundTag state) {
        com.jvn.villagerretaliation.quest.tutorial.TutorialSetupObserver.settings(villager,player.getUUID(),state);
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"), HiredWorkStateStore.statusReplacements(state));
    }
}
