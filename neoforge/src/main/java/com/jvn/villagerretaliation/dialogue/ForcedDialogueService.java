package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueTrigger;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
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

        BlockState state = level.getBlockState(event.getPos());
        if (!isWatchedContainer(state)) {
            return;
        }

        RECENT_CONTAINER_CLICKS.put(
                player.getUUID(),
                new RecentContainerClick(level.dimension(), event.getPos().immutable(), level.getGameTime(), state.getBlock().getName())
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

        int itemCount = countContainerItems(event.getContainer());
        OPEN_CONTAINER_SNAPSHOTS.put(
                player.getUUID(),
                new ContainerSnapshot(click.dimension(), click.pos(), click.containerName(), itemCount, level.getGameTime())
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

        int currentItemCount = countContainerItems(event.getContainer());
        int removedCount = snapshot.itemCount() - currentItemCount;
        if (removedCount <= 0) {
            return;
        }

        triggerContainerTheft(level, player, snapshot, removedCount);
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
        if (option.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(player.serverLevel(), villager, player, option.reputationDelta());
        }
        String response = ForcedDialogueResources.resolveTemplate(option.response(), session.context());
        if (!response.isBlank()) {
            VillagerInteractionService.broadcastVillagerChat(player.serverLevel(), villager, response);
        }
        if (option.endConversation()) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
        } else {
            VillagerInteractionService.sendForcedDialogueReputation(player, villager, forcedOptions(session.definition()));
        }
        if (option.aggro()) {
            VillagerConversationService.endForPlayer(player, true);
            VillagerRetaliationHandler.forceAnger(villager, player);
        }
        return true;
    }

    public static void endForPlayer(ServerPlayer player) {
        FORCED_SESSIONS.remove(player.getUUID());
    }

    private static void triggerContainerTheft(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount) {
        ForcedDialogueResources
                .select(level.getServer(), ForcedDialogueTrigger.CONTAINER_THEFT)
                .ifPresent(definition -> trigger(level, player, snapshot, removedCount, definition));
    }

    private static void trigger(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
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
                snapshot.pos().getX(),
                snapshot.pos().getY(),
                snapshot.pos().getZ()
        );
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, witness, player.getUUID(), definition.reputationDelta(), snapshot.pos());
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

        if (VillagerInteractionService.openForcedDialogue(player, witness, line, forcedOptions(definition))) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    witness.getUUID(),
                    definition,
                    context,
                    level.getGameTime()
            ));
        } else if (!line.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, witness, line);
        }
    }

    private static List<DialogueOptionDefinition> forcedOptions(ForcedDialogueDefinition definition) {
        return definition.options().stream()
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

    private record RecentContainerClick(
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            long gameTime,
            Component containerName) {
    }

    private record ContainerSnapshot(
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            Component containerName,
            int itemCount,
            long gameTime) {
    }

    private record ForcedDialogueSession(
            UUID villagerId,
            ForcedDialogueDefinition definition,
            ForcedDialogueContext context,
            long startedGameTime) {
    }
}
