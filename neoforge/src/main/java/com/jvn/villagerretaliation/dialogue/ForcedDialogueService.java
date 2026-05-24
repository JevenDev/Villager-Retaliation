package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.config.WatchedContainerScope;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueItemDestination;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueTrigger;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ForcedDialogueService {
    private static final long RECENT_CONTAINER_CLICK_TICKS = 8L;
    private static final long FORCED_SESSION_TIMEOUT_TICKS = 20L * 60L;
    private static final Map<UUID, RecentContainerClick> RECENT_CONTAINER_CLICKS = new HashMap<>();
    private static final Map<UUID, ContainerSnapshot> OPEN_CONTAINER_SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, ForcedDialogueSession> FORCED_SESSIONS = new HashMap<>();

    private ForcedDialogueService() {
    }

    public static void rememberPotentialContainerOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation lootTable = containerLootTable(level, pos);
        if (!isEligibleWatchedContainer(state, lootTable)) {
            return;
        }

        RECENT_CONTAINER_CLICKS.put(
                player.getUUID(),
                new RecentContainerClick(level.dimension(), pos.immutable(), level.getGameTime(), state.getBlock().getName(), lootTable)
        );
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        RecentContainerClick click = RECENT_CONTAINER_CLICKS.get(player.getUUID());
        if (click == null
                || click.dimension() != level.dimension()
                || level.getGameTime() - click.gameTime() > RECENT_CONTAINER_CLICK_TICKS) {
            return;
        }

        BlockState state = level.getBlockState(click.pos());
        if (!isEligibleWatchedContainer(state, click.lootTable())) {
            return;
        }

        int itemCount = countContainerItems(event.getContainer());
        ContainerSnapshot snapshot = new ContainerSnapshot(
                click.dimension(),
                click.pos(),
                click.containerName(),
                click.lootTable(),
                itemCount,
                snapshotContainerItems(event.getContainer()),
                level.getGameTime()
        );
        if (VillagerRetaliationConfig.CONTAINER_FORCED_DIALOGUE_TRIGGER.get() == ContainerForcedDialogueTrigger.OPENING) {
            triggerContainerOpened(level, player, snapshot);
            return;
        }

        OPEN_CONTAINER_SNAPSHOTS.put(
                player.getUUID(),
                snapshot
        );
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        ContainerSnapshot snapshot = OPEN_CONTAINER_SNAPSHOTS.remove(player.getUUID());
        if (snapshot == null || snapshot.dimension() != level.dimension()) {
            return;
        }

        List<ItemStack> removedStacks = removedContainerStacks(snapshot.itemStacks(), event.getContainer());
        int removedCount = removedStacks.stream().mapToInt(ItemStack::getCount).sum();
        if (removedCount <= 0) {
            return;
        }

        triggerContainerTheft(level, player, snapshot, removedCount, removedStacks);
    }

    public static boolean handleDialogueRequest(ServerPlayer player, Villager villager, String optionId) {
        ForcedDialogueSession session = FORCED_SESSIONS.get(player.getUUID());
        if (session == null || !session.villagerId().equals(villager.getUUID())) {
            return false;
        }
        if (player.serverLevel().getGameTime() - session.startedGameTime() > FORCED_SESSION_TIMEOUT_TICKS) {
            FORCED_SESSIONS.remove(player.getUUID());
            return false;
        }

        Optional<ForcedDialogueOption> selected = session.definition().options().stream()
                .filter(option -> option.id().equals(optionId))
                .findFirst();
        if (selected.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.unknown_dialogue_option");
            return true;
        }

        ForcedDialogueOption option = selected.get();
        ForcedDialogueItemPayment itemPayment = option.itemPayment();
        if (!itemPayment.isEmpty() && !executeItemPayment(player, villager, session, itemPayment)) {
            handleFailedItemPayment(player, villager, session, itemPayment);
            return true;
        }

        if (option.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(player.serverLevel(), villager, player, option.reputationDelta());
        }
        if (!itemPayment.isEmpty()
                && itemPayment.successReputationDelta() != 0
                && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(player.serverLevel(), villager, player, itemPayment.successReputationDelta());
        }
        String responseText = !itemPayment.isEmpty() && !itemPayment.successResponse().isBlank()
                ? itemPayment.successResponse()
                : option.response();
        String response = ForcedDialogueResources.resolveTemplate(responseText, session.context(), itemPayment.removal().replacements());
        if (!response.isBlank()) {
            VillagerInteractionService.broadcastVillagerChat(
                    player.serverLevel(),
                    villager,
                    response,
                    VillagerInteractionService.villagerSpeakerLabel(villager)
            );
        }
        if (option.endConversation()) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
        } else {
            VillagerInteractionService.sendForcedDialogueReputation(player, villager, forcedOptions(session.definition(), player.serverLevel(), villager, player));
        }
        if (option.aggro()) {
            VillagerConversationService.endForPlayer(player, true);
            VillagerRetaliationHandler.forceAnger(villager, player);
        }
        return true;
    }

    private static void handleFailedItemPayment(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueItemPayment itemPayment) {
        if (itemPayment.failureReputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(player.serverLevel(), villager, player, itemPayment.failureReputationDelta());
        }

        String response = ForcedDialogueResources.resolveTemplate(
                itemPayment.failureResponse(),
                session.context(),
                itemPayment.removal().replacements());
        if (!response.isBlank()) {
            VillagerInteractionService.broadcastVillagerChat(
                    player.serverLevel(),
                    villager,
                    response,
                    VillagerInteractionService.villagerSpeakerLabel(villager)
            );
        }

        if (itemPayment.failureEndConversation() || itemPayment.failureAggro()) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
        } else {
            VillagerInteractionService.sendForcedDialogueReputation(player, villager, forcedOptions(session.definition(), player.serverLevel(), villager, player));
        }
        if (itemPayment.failureAggro()) {
            VillagerRetaliationHandler.forceAnger(villager, player);
        }
    }

    private static boolean executeItemPayment(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueItemPayment itemPayment) {
        List<ItemStack> previewStacks = itemPayment.removal().previewRemovedStacks(player);
        if (previewStacks.isEmpty()) {
            return false;
        }

        Optional<ItemTransferTarget> primaryTarget = transferTarget(player, villager, session, itemPayment.destination());
        if (primaryTarget.isEmpty()) {
            return false;
        }

        Optional<ItemTransferTarget> overflowTarget = Optional.ofNullable(itemPayment.overflowDestination())
                .flatMap(destination -> transferTarget(player, villager, session, destination));
        boolean primaryFits = primaryTarget.get().canAccept(previewStacks);
        if (itemPayment.requireSpace() && !primaryFits && overflowTarget.isEmpty()) {
            return false;
        }
        if (overflowTarget.isPresent() && !overflowTarget.get().canAccept(previewStacks)) {
            return false;
        }

        Optional<List<ItemStack>> removedStacks = itemPayment.removal().removeStacks(player);
        if (removedStacks.isEmpty()) {
            return false;
        }

        List<ItemStack> remainder = primaryTarget.get().accept(removedStacks.get());
        if (!remainder.isEmpty() && overflowTarget.isPresent()) {
            remainder = overflowTarget.get().accept(remainder);
        }
        return !itemPayment.requireSpace() || remainder.isEmpty();
    }

    private static Optional<ItemTransferTarget> transferTarget(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueItemDestination destination) {
        return switch (destination) {
            case DISCARD -> Optional.of(discardTransferTarget());
            case VILLAGER_INVENTORY -> Optional.of(villagerInventoryTransferTarget(villager));
            case SOURCE_CONTAINER -> sourceContainer(player, session).map(ForcedDialogueService::containerTransferTarget);
            case DROP_AT_VILLAGER -> Optional.of(dropAtVillagerTransferTarget(villager));
            case DROP_AT_CONTAINER -> sourceContainerLevel(player, session)
                    .map(level -> dropAtContainerTransferTarget(level, session.sourceContainerPos()));
        };
    }

    private static Optional<Container> sourceContainer(ServerPlayer player, ForcedDialogueSession session) {
        Optional<ServerLevel> level = sourceContainerLevel(player, session);
        if (level.isEmpty()) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.get().getBlockEntity(session.sourceContainerPos());
        return blockEntity instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    private static Optional<ServerLevel> sourceContainerLevel(ServerPlayer player, ForcedDialogueSession session) {
        ServerLevel level = player.getServer().getLevel(session.sourceContainerDimension());
        return Optional.ofNullable(level);
    }

    private static ItemTransferTarget discardTransferTarget() {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                return List.of();
            }
        };
    }

    private static ItemTransferTarget villagerInventoryTransferTarget(Villager villager) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return VillagerInventoryAccess.canAddItems(villager, stacks);
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                return stacks.stream()
                        .map(stack -> VillagerInventoryAccess.addItem(villager, stack))
                        .filter(stack -> !stack.isEmpty())
                        .toList();
            }
        };
    }

    private static ItemTransferTarget containerTransferTarget(Container container) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return canInsertAll(container, stacks);
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                return insertAll(container, stacks);
            }
        };
    }

    private static ItemTransferTarget dropAtVillagerTransferTarget(Villager villager) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                for (ItemStack stack : stacks) {
                    villager.spawnAtLocation(stack.copy());
                }
                return List.of();
            }
        };
    }

    private static ItemTransferTarget dropAtContainerTransferTarget(ServerLevel level, BlockPos pos) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                for (ItemStack stack : stacks) {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, stack.copy());
                }
                return List.of();
            }
        };
    }

    public static void endForPlayer(ServerPlayer player) {
        FORCED_SESSIONS.remove(player.getUUID());
    }

    private static void triggerContainerTheft(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
            List<ItemStack> removedStacks) {
        ForcedDialogueResources
                .select(level.getServer(), ForcedDialogueTrigger.CONTAINER_THEFT, snapshot.lootTable())
                .ifPresent(definition -> trigger(level, player, snapshot, removedCount, removedStacks, definition));
    }

    private static void triggerContainerOpened(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot) {
        ForcedDialogueResources
                .select(level.getServer(), ForcedDialogueTrigger.CONTAINER_OPENED, snapshot.lootTable())
                .ifPresent(definition -> trigger(level, player, snapshot, 0, List.of(), definition));
    }

    private static void trigger(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
            List<ItemStack> removedStacks,
            ForcedDialogueDefinition definition) {
        Villager witness = findWitness(level, player, snapshot.pos(), definition).orElse(null);
        if (witness == null) {
            return;
        }

        ForcedDialogueContext context = new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(witness).getString(),
                player.getDisplayName().getString(),
                "items",
                removedCount,
                snapshot.containerName().getString(),
                snapshot.lootTable() == null ? "" : snapshot.lootTable().toString(),
                snapshot.pos().getX(),
                snapshot.pos().getY(),
                snapshot.pos().getZ()
        );
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, witness, player.getUUID(), definition.reputationDelta(), snapshot.pos());
            VillagerGossipHooks.spreadReputation(level, witness, player.getUUID(), definition.reputationDelta());
        }
        if (definition.trigger() == ForcedDialogueTrigger.CONTAINER_THEFT) {
            rememberContainerTheft(level, witness, player, snapshot, removedStacks, removedCount);
        }

        String line = ForcedDialogueResources.resolveTemplate(definition.line(), context);
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, witness, line);
            }
            VillagerRetaliationHandler.forceAnger(witness, player);
            return;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, witness, line);
            }
            return;
        }

        if (VillagerInteractionService.openForcedDialogue(player, witness, line, forcedOptions(definition, level, witness, player))) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    witness.getUUID(),
                    definition,
                    context,
                    snapshot.dimension(),
                    snapshot.pos(),
                    level.getGameTime()
            ));
        } else if (!line.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, witness, line);
        }
    }

    private static List<DialogueOptionDefinition> forcedOptions(
            ForcedDialogueDefinition definition,
            ServerLevel level,
            Villager villager,
            ServerPlayer player) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return definition.options().stream()
                .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                .map(option -> DialogueOptionDefinition.simple(option.id(), option.label(), DialogueRequestType.CHAT, option.order()))
                .toList();
    }

    private static Optional<Villager> findWitness(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            ForcedDialogueDefinition definition) {
        double radius = definition.witnessRadius();
        double radiusSqr = radius * radius;
        AABB area = AABB.ofSize(Vec3.atCenterOf(pos), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby()).stream()
                .filter(villager -> villager.distanceToSqr(player) <= radiusSqr || villager.blockPosition().distSqr(pos) <= radiusSqr)
                .filter(villager -> !definition.requiresLineOfSight() || hasTheftLineOfSight(level, villager, player, pos))
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)));
    }

    private static boolean hasTheftLineOfSight(ServerLevel level, Villager villager, ServerPlayer player, BlockPos pos) {
        return villager.hasLineOfSight(player) && hasBlockLineOfSight(level, villager, pos);
    }

    private static boolean hasBlockLineOfSight(ServerLevel level, Villager villager, BlockPos pos) {
        Vec3 from = villager.getEyePosition();
        Vec3 to = Vec3.atCenterOf(pos);
        BlockHitResult result = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, villager));
        return result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos);
    }

    private static boolean isWatchedContainer(BlockState state) {
        return state.getBlock() instanceof AbstractChestBlock<?>
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isEligibleWatchedContainer(BlockState state, ResourceLocation lootTable) {
        if (!isWatchedContainer(state) && lootTable == null) {
            return false;
        }
        return VillagerRetaliationConfig.WATCHED_CONTAINER_SCOPE.get() != WatchedContainerScope.WORLD_GENERATED_ONLY
                || lootTable != null;
    }

    private static ResourceLocation containerLootTable(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RandomizableContainer container) {
            ResourceKey<LootTable> lootTable = container.getLootTable();
            return lootTable == null ? null : lootTable.location();
        }
        return null;
    }

    private static int countContainerItems(AbstractContainerMenu menu) {
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static List<ItemStack> snapshotContainerItems(AbstractContainerMenu menu) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }

    private static List<ItemStack> removedContainerStacks(List<ItemStack> beforeStacks, AbstractContainerMenu menu) {
        List<ItemStack> remainingCurrent = new ArrayList<>(snapshotContainerItems(menu));
        List<ItemStack> removed = new ArrayList<>();
        for (ItemStack beforeStack : beforeStacks) {
            int missingCount = beforeStack.getCount();
            for (ItemStack currentStack : remainingCurrent) {
                if (missingCount <= 0) {
                    break;
                }
                if (currentStack.isEmpty() || !ItemStack.isSameItemSameComponents(beforeStack, currentStack)) {
                    continue;
                }

                int matched = Math.min(missingCount, currentStack.getCount());
                currentStack.shrink(matched);
                missingCount -= matched;
            }
            if (missingCount > 0) {
                removed.add(beforeStack.copyWithCount(missingCount));
            }
        }
        return List.copyOf(removed);
    }

    private static void rememberContainerTheft(
            ServerLevel level,
            Villager witness,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            List<ItemStack> removedStacks,
            int removedCount) {
        ItemStack representative = representativeRemovedStack(removedStacks);
        String itemName = representative.isEmpty() ? "items" : representative.getHoverName().getString();
        String itemId = representative.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(representative.getItem()).toString();
        int itemCount = representative.isEmpty() ? removedCount : representative.getCount();
        VillageEventMemory.rememberContainerTheft(
                level,
                snapshot.pos(),
                witness,
                player,
                VillagerPresetNameRegistry.resolveDisplayName(witness).getString(),
                itemName,
                itemId,
                itemCount,
                snapshot.containerName().getString(),
                snapshot.lootTable() == null ? "" : snapshot.lootTable().toString()
        );
    }

    private static ItemStack representativeRemovedStack(List<ItemStack> removedStacks) {
        return removedStacks.stream()
                .max(Comparator.comparingInt(ItemStack::getCount))
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
    }

    private static boolean canInsertAll(Container container, List<ItemStack> stacks) {
        List<ItemStack> simulatedSlots = new java.util.ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            simulatedSlots.add(container.getItem(slot).copy());
        }

        for (ItemStack stack : stacks) {
            ItemStack remainder = insertIntoSimulatedSlots(container, simulatedSlots, stack.copy());
            if (!remainder.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> insertAll(Container container, List<ItemStack> stacks) {
        List<ItemStack> remainders = new java.util.ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack remainder = insertIntoContainer(container, stack.copy());
            if (!remainder.isEmpty()) {
                remainders.add(remainder);
            }
        }
        container.setChanged();
        return List.copyOf(remainders);
    }

    private static ItemStack insertIntoContainer(Container container, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int moveCount = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (moveCount > 0) {
                existing.grow(moveCount);
                remainder.shrink(moveCount);
            }
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), container.getMaxStackSize()));
            container.setItem(slot, remainder.copyWithCount(moveCount));
            remainder.shrink(moveCount);
        }
        return remainder;
    }

    private static ItemStack insertIntoSimulatedSlots(Container container, List<ItemStack> slots, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < slots.size(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = slots.get(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int moveCount = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (moveCount > 0) {
                existing.grow(moveCount);
                remainder.shrink(moveCount);
            }
        }

        for (int slot = 0; slot < slots.size(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!slots.get(slot).isEmpty() || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), container.getMaxStackSize()));
            slots.set(slot, remainder.copyWithCount(moveCount));
            remainder.shrink(moveCount);
        }
        return remainder;
    }

    private record RecentContainerClick(
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            long gameTime,
            Component containerName,
            ResourceLocation lootTable) {
    }

    private record ContainerSnapshot(
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            Component containerName,
            ResourceLocation lootTable,
            int itemCount,
            List<ItemStack> itemStacks,
            long gameTime) {
    }

    private record ForcedDialogueSession(
            UUID villagerId,
            ForcedDialogueDefinition definition,
            ForcedDialogueContext context,
            net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
            BlockPos sourceContainerPos,
            long startedGameTime) {
    }

    private interface ItemTransferTarget {
        boolean canAccept(List<ItemStack> stacks);

        List<ItemStack> accept(List<ItemStack> stacks);
    }
}
