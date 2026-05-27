package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger;
import com.jvn.villagerretaliation.config.ContainerWatchMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueItemDestination;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueOutputMode;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueStolenItemReturn;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueTrigger;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.GeneratedContainerTooltipPayload;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager.ReputationSnapshot;
import com.jvn.villagerretaliation.trade.VillagerSpecialOrderService;
import com.jvn.villagerretaliation.trade.VillagerTradeRefreshService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.entity.LivingEntity;
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
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class ForcedDialogueService {
    private static final String LEAVE_OPTION_ID = "leave";
    private static final String TRADE_REFRESH_DEFINITION_ID = "trade_refresh";
    private static final String TRADE_REFRESH_TRADE_OPTION_ID = "trade_refresh.trade";
    private static final String TRADE_REFRESH_REVERED_OPTIONS_ID = "trade_refresh.revered_options";
    private static final String TRADE_REFRESH_SPECIAL_ORDER_SELECT_OPTIONS_ID = "trade_refresh.special_order_select_options";
    private static final String TRADE_REFRESH_SPECIAL_ORDER_CONFIRM_OPTIONS_ID = "trade_refresh.special_order_confirm_options";
    private static final String TRADE_REFRESH_SURPRISE_OPTION_ID = "trade_refresh.surprise_me";
    private static final String TRADE_REFRESH_SPECIAL_ORDER_OPTION_ID = "trade_refresh.special_order";
    private static final String TRADE_REFRESH_CONFIRM_SPECIAL_ORDER_OPTION_ID = "trade_refresh.confirm_special_order";
    private static final long RECENT_CONTAINER_CLICK_TICKS = 8L;
    private static final long FORCED_SESSION_TIMEOUT_TICKS = 20L * 60L;
    private static final long PLAYER_ITEM_PROXIMITY_SCAN_INTERVAL_TICKS = 80L;
    private static final long PLAYER_ITEM_PROXIMITY_COOLDOWN_TICKS = 20L * 30L;
    private static final Map<UUID, RecentContainerClick> RECENT_CONTAINER_CLICKS = new HashMap<>();
    private static final Map<UUID, ContainerSnapshot> OPEN_CONTAINER_SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, ForcedDialogueSession> FORCED_SESSIONS = new HashMap<>();
    private static final Map<PlayerItemProximityKey, Long> NEXT_PLAYER_ITEM_PROXIMITY_TICK = new HashMap<>();

    private ForcedDialogueService() {
    }

    public static void rememberPotentialContainerOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !containerForcedDialogueEnabled()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation lootTable = generatedContainerLootTable(level, pos);
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
                || !(player.level() instanceof ServerLevel level)
                || !containerForcedDialogueEnabled()) {
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
        PacketDistributor.sendToPlayer(player, new GeneratedContainerTooltipPayload(event.getContainer().containerId, true));

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
        OPEN_CONTAINER_SNAPSHOTS.put(
                player.getUUID(),
                snapshot
        );
        if (VillagerRetaliationConfig.CONTAINER_FORCED_DIALOGUE_TRIGGER.get() == ContainerForcedDialogueTrigger.OPENING) {
            triggerContainerOpened(level, player, snapshot);
            return;
        }
    }

    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || event.getEntity().getItem().isEmpty()
                || !containerForcedDialogueEnabled()) {
            return;
        }

        ContainerSnapshot snapshot = OPEN_CONTAINER_SNAPSHOTS.get(player.getUUID());
        if (snapshot == null || snapshot.dimension() != level.dimension()) {
            return;
        }

        ItemStack tossedStack = event.getEntity().getItem();
        boolean removedFromContainer = removedContainerStacks(snapshot.itemStacks(), player.containerMenu).stream()
                .anyMatch(removedStack -> ItemStack.isSameItemSameComponents(removedStack, tossedStack)
                        && removedStack.getCount() >= tossedStack.getCount());
        if (!removedFromContainer) {
            return;
        }

        if (restoreToOpenContainer(player.containerMenu, tossedStack.copy()).isEmpty()) {
            event.setCanceled(true);
        }
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !containerForcedDialogueEnabled()) {
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

    public static void onContainerBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !containerForcedDialogueEnabled()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ResourceLocation lootTable = generatedContainerLootTable(level, pos);
        if (!isEligibleWatchedContainer(state, lootTable)) {
            return;
        }
        unpackContainerLootTable(level, pos, player);

        ContainerSnapshot snapshot = new ContainerSnapshot(
                level.dimension(),
                pos.immutable(),
                state.getBlock().getName(),
                lootTable,
                countContainerItems(level, pos),
                snapshotContainerItems(level, pos),
                level.getGameTime()
        );
        OPEN_CONTAINER_SNAPSHOTS.remove(player.getUUID());
        triggerContainerBroken(level, player, snapshot);
    }

    public static boolean handleDialogueRequest(ServerPlayer player, Villager villager, String optionId) {
        ForcedDialogueSession session = FORCED_SESSIONS.get(player.getUUID());
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || session == null
                || !session.villagerId().equals(villager.getUUID())) {
            return false;
        }
        if (player.serverLevel().getGameTime() - session.startedGameTime() > FORCED_SESSION_TIMEOUT_TICKS) {
            FORCED_SESSIONS.remove(player.getUUID());
            return false;
        }
        if (LEAVE_OPTION_ID.equals(optionId)) {
            return handleLeaveRequest(player, villager, false);
        }

        Optional<ForcedDialogueOption> selected = session.definition().options().stream()
                .filter(option -> option.id().equals(optionId))
                .filter(option -> optionMatchesReputation(player.serverLevel(), villager, player, option))
                .findFirst();
        if (selected.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.unknown_dialogue_option");
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }

        return handleSelectedOption(player, villager, session, selected.get(), false);
    }

    public static boolean hasSession(ServerPlayer player, Villager villager) {
        ForcedDialogueSession session = FORCED_SESSIONS.get(player.getUUID());
        return session != null && session.villagerId().equals(villager.getUUID());
    }

    public static void openTradeRefreshDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String messageKey,
            Map<String, String> replacements) {
        String line = tradeRefreshLine(level, villager, player, messageKey, replacements);
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
            return;
        }

        player.closeContainer();
        Optional<ForcedDialogueDefinition> optionDefinition = tradeRefreshOptionDefinition(level, villager, player, messageKey);
        if (optionDefinition.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
            return;
        }
        ForcedDialogueDefinition definition = tradeRefreshDefinition(line, optionDefinition.get());
        ForcedDialogueContext context = tradeRefreshContext(level, villager, player, replacements);
        if (VillagerInteractionService.openForcedDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                true)) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    villager.getUUID(),
                    definition,
                    context,
                    level.dimension(),
                    villager.blockPosition().immutable(),
                    List.of(),
                    level.getGameTime()
            ));
        } else {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
        }
    }

    private static String tradeRefreshLine(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String messageKey,
            Map<String, String> replacements) {
        return VillagerDialogueResources
                .message(VillagerInteractionService.createDialogueContext(level, player, villager), messageKey, replacements)
                .orElse(messageKey);
    }

    public static void openTradeRefreshChoiceDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int offerIndex,
            String tradeItem) {
        Map<String, String> replacements = Map.of("trade_item", tradeItem);
        String line = tradeRefreshLine(level, villager, player, "trade_refresh.revered_prompt", replacements);
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
            return;
        }

        player.closeContainer();
        Optional<ForcedDialogueDefinition> optionDefinition = tradeRefreshOptionDefinitionById(
                level,
                villager,
                player,
                TRADE_REFRESH_REVERED_OPTIONS_ID);
        if (optionDefinition.isEmpty()) {
            VillagerTradeRefreshService.handleSurpriseRequest(player, villager, offerIndex);
            return;
        }
        ForcedDialogueDefinition definition = tradeRefreshDefinition(line, optionDefinition.get());
        ForcedDialogueContext context = tradeRefreshContext(level, villager, player, replacements);
        if (VillagerInteractionService.openForcedDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                true)) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    villager.getUUID(),
                    definition,
                    context,
                    level.dimension(),
                    villager.blockPosition().immutable(),
                    List.of(),
                    level.getGameTime(),
                    offerIndex,
                    ""));
        } else {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
        }
    }

    public static boolean handleLeaveRequest(ServerPlayer player, Villager villager, boolean forceEndConversation) {
        ForcedDialogueSession session = FORCED_SESSIONS.get(player.getUUID());
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || session == null
                || !session.villagerId().equals(villager.getUUID())) {
            return false;
        }
        Optional<ForcedDialogueOption> selected = selectLeaveOption(player, villager, session);
        return handleSelectedOption(player, villager, session, selected.get(), forceEndConversation);
    }

    private static Optional<ForcedDialogueOption> selectLeaveOption(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(player.serverLevel(), villager, player.getUUID());
        return session.definition().options().stream()
                .filter(option -> option.id().equals(LEAVE_OPTION_ID))
                .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                .findFirst()
                .or(() -> session.definition().leaveOptions().stream()
                        .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                        .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                        .findFirst())
                .or(() -> session.definition().options().stream()
                        .filter(option -> option.id().equals(LEAVE_OPTION_ID))
                        .findFirst())
                .or(() -> Optional.of(session.definition().leaveOption()));
    }

    private static boolean handleSelectedOption(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
        ForcedDialogueOption option,
        boolean forceEndConversation) {
        ForcedDialogueStolenItemReturn stolenItemReturn = option.stolenItemReturn();
        boolean returnedStolenItems = false;
        if (!stolenItemReturn.isEmpty()) {
            Optional<List<ItemStack>> returnedStacks = executeStolenItemReturn(player, villager, session, stolenItemReturn);
            if (returnedStacks.isEmpty()) {
                handleFailedStolenItemReturn(player, villager, session, stolenItemReturn, forceEndConversation);
                return true;
            }
            returnedStolenItems = true;
            if (stolenItemReturn.successReputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
                VillagerReputationManager.addDialogueReputation(player.serverLevel(), villager, player, stolenItemReturn.successReputationDelta());
            }
        }
        ForcedDialogueItemPayment itemPayment = option.itemPayment();
        if (!itemPayment.isEmpty() && !executeItemPayment(player, villager, session, itemPayment)) {
            handleFailedItemPayment(player, villager, session, itemPayment, forceEndConversation);
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
        if (isTradeRefreshConfirmSpecialOrderOption(session, option)) {
            ResourceLocation definitionId = ResourceLocation.tryParse(session.tradeRefreshDefinitionId());
            VillagerSpecialOrderService.QueueResult result = VillagerTradeRefreshService.queueSpecialOrder(
                    player,
                    villager,
                    session.tradeRefreshOfferIndex(),
                    definitionId);
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            openTradeRefreshDialogue(player.serverLevel(), villager, player, result.messageKey(), result.replacements());
            return true;
        }
        String responseText = option.selectResponse(player.serverLevel().getRandom());
        String stolenItemReturnResponse = stolenItemReturn.selectSuccessResponse(player.serverLevel().getRandom());
        if (returnedStolenItems && !stolenItemReturnResponse.isBlank()) {
            responseText = stolenItemReturnResponse;
        }
        String itemPaymentResponse = itemPayment.selectSuccessResponse(player.serverLevel().getRandom());
        if (!itemPayment.isEmpty() && !itemPaymentResponse.isBlank()) {
            responseText = itemPaymentResponse;
        }
        String response = ForcedDialogueResources.resolveTemplate(responseText, session.context(), itemPayment.removal().replacements());
        if (!response.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    player.serverLevel(),
                    villager,
                    response,
                    VillagerInteractionService.villagerSpeakerLabel(villager)
            );
        }
        if (isTradeRefreshSurpriseOption(session, option)) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            VillagerTradeRefreshService.handleSurpriseRequest(player, villager, session.tradeRefreshOfferIndex());
            return true;
        }
        if (isTradeRefreshSpecialOrderOption(session, option)) {
            openSpecialOrderSelectionDialogue(player, villager, session);
            return true;
        }
        Optional<ResourceLocation> selectedSpecialOrder = VillagerSpecialOrderService.selectedDefinitionId(option.id());
        if (selectedSpecialOrder.isPresent() && isTradeRefreshDefinition(session)) {
            openSpecialOrderConfirmDialogue(player, villager, session, selectedSpecialOrder.get());
            return true;
        }
        if (isTradeRefreshTradeOption(session, option)) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            VillagerInteractionService.openTrading(player, villager, true);
            return true;
        }
        boolean aggro = option.aggro() || rollChance(player.serverLevel(), option.aggroChance());
        if (option.endConversation() || forceEndConversation) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
        } else {
            VillagerInteractionService.sendForcedDialogueReputation(
                    player,
                    villager,
                    forcedOptions(session.definition(), player.serverLevel(), villager, player),
                    session.definition().forceCameraTowardsVillager());
        }
        if (aggro) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            VillagerRetaliationHandler.forceAnger(villager, player);
        }
        return true;
    }

    private static boolean rollChance(ServerLevel level, double chance) {
        return chance > 0.0D && level.getRandom().nextDouble() < chance;
    }

    private static boolean isTradeRefreshTradeOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return TRADE_REFRESH_DEFINITION_ID.equals(session.definition().id())
                && TRADE_REFRESH_TRADE_OPTION_ID.equals(option.id());
    }

    private static boolean isTradeRefreshSurpriseOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return isTradeRefreshDefinition(session)
                && TRADE_REFRESH_SURPRISE_OPTION_ID.equals(option.id())
                && session.tradeRefreshOfferIndex() >= 0;
    }

    private static boolean isTradeRefreshSpecialOrderOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return isTradeRefreshDefinition(session)
                && TRADE_REFRESH_SPECIAL_ORDER_OPTION_ID.equals(option.id())
                && session.tradeRefreshOfferIndex() >= 0;
    }

    private static boolean isTradeRefreshConfirmSpecialOrderOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return isTradeRefreshDefinition(session)
                && TRADE_REFRESH_CONFIRM_SPECIAL_ORDER_OPTION_ID.equals(option.id())
                && session.tradeRefreshOfferIndex() >= 0
                && !session.tradeRefreshDefinitionId().isBlank();
    }

    private static boolean isTradeRefreshDefinition(ForcedDialogueSession session) {
        return TRADE_REFRESH_DEFINITION_ID.equals(session.definition().id());
    }

    private static boolean optionMatchesReputation(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueOption option) {
        ReputationSnapshot reputation = VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return option.reputationCondition().matches(reputation.value(), reputation.level());
    }

    private static ForcedDialogueDefinition tradeRefreshDefinition(String line, ForcedDialogueDefinition optionDefinition) {
        return tradeRefreshDefinition(
                line,
                optionDefinition,
                optionDefinition.options(),
                optionDefinition.leaveOption(),
                optionDefinition.leaveOptions());
    }

    private static ForcedDialogueDefinition tradeRefreshDefinition(
            String line,
            ForcedDialogueDefinition optionDefinition,
            List<ForcedDialogueOption> options,
            ForcedDialogueOption leaveOption,
            List<ForcedDialogueOption> leaveOptions) {
        return new ForcedDialogueDefinition(
                TRADE_REFRESH_DEFINITION_ID,
                optionDefinition.trigger(),
                optionDefinition.output(),
                line.isBlank() ? List.of() : List.of(line),
                optionDefinition.initiateDialogue(),
                optionDefinition.aggroImmediately(),
                optionDefinition.forceCameraTowardsVillager(),
                optionDefinition.requiresLineOfSight(),
                optionDefinition.witnessRadius(),
                optionDefinition.chance(),
                optionDefinition.reputationDelta(),
                optionDefinition.priority(),
                optionDefinition.minRecentContainerThefts(),
                optionDefinition.maxRecentContainerThefts(),
                optionDefinition.minRecentRetaliations(),
                optionDefinition.maxRecentRetaliations(),
                optionDefinition.lootTables(),
                optionDefinition.targetEntityTypes(),
                optionDefinition.witnessProfessions(),
                optionDefinition.witnessEquipmentCondition(),
                optionDefinition.playerItemCondition(),
                optionDefinition.reputationCondition(),
                options,
                leaveOption,
                leaveOptions);
    }

    private static Optional<ForcedDialogueDefinition> tradeRefreshOptionDefinition(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String messageKey) {
        String definitionId = isTradeRefreshUnavailable(messageKey)
                ? "trade_refresh.unavailable_options"
                : "trade_refresh.available_options";
        return tradeRefreshOptionDefinitionById(level, villager, player, definitionId);
    }

    private static Optional<ForcedDialogueDefinition> tradeRefreshOptionDefinitionById(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String definitionId) {
        return ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.TRADE_REFRESH, null)
                .stream()
                .filter(definition -> definition.id().equals(definitionId))
                .filter(definition -> definition.matchesWitness(villager))
                .filter(definition -> definitionMatchesReputation(level, villager, player, definition))
                .findFirst();
    }

    private static void openSpecialOrderSelectionDialogue(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session) {
        ServerLevel level = player.serverLevel();
        List<VillagerSpecialOrderService.SpecialOrderOption> specialOrders =
                VillagerSpecialOrderService.availableOptions(level, villager, player, session.tradeRefreshOfferIndex());
        if (specialOrders.isEmpty()) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            openTradeRefreshDialogue(level, villager, player, "trade_refresh.special_order_unavailable", Map.of());
            return;
        }

        ForcedDialogueDefinition optionDefinition = tradeRefreshOptionDefinitionById(
                level,
                villager,
                player,
                TRADE_REFRESH_SPECIAL_ORDER_SELECT_OPTIONS_ID)
                .orElse(session.definition());
        List<ForcedDialogueOption> options = new ArrayList<>();
        int order = 0;
        for (VillagerSpecialOrderService.SpecialOrderOption specialOrder : specialOrders) {
            options.add(dynamicTradeRefreshOption(
                    VillagerSpecialOrderService.selectionOptionId(specialOrder.definition().id()),
                    specialOrder.label(),
                    order++));
        }
        options.addAll(optionDefinition.options());

        String line = tradeRefreshLine(level, villager, player, "trade_refresh.special_order_select", Map.of());
        ForcedDialogueDefinition definition = tradeRefreshDefinition(
                line,
                optionDefinition,
                List.copyOf(options),
                optionDefinition.leaveOption(),
                optionDefinition.leaveOptions());
        ForcedDialogueContext context = tradeRefreshContext(level, villager, player, Map.of());
        updateTradeRefreshSession(player, villager, definition, context, session.tradeRefreshOfferIndex(), "");
    }

    private static void openSpecialOrderConfirmDialogue(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ResourceLocation definitionId) {
        ServerLevel level = player.serverLevel();
        VillagerSpecialOrderService.SpecialOrderOption specialOrder = VillagerSpecialOrderService
                .availableOptions(level, villager, player, session.tradeRefreshOfferIndex())
                .stream()
                .filter(candidate -> candidate.definition().id().equals(definitionId))
                .findFirst()
                .orElse(null);
        if (specialOrder == null) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            openTradeRefreshDialogue(level, villager, player, "trade_refresh.special_order_unavailable", Map.of());
            return;
        }

        Map<String, String> replacements = Map.of(
                "trade_item", specialOrder.tradeItem(),
                "trade_definition", definitionId.toString(),
                "wait_days", Integer.toString(specialOrder.waitDays()),
                "cooldown_days", Integer.toString(specialOrder.cooldownDays()));
        ForcedDialogueDefinition optionDefinition = tradeRefreshOptionDefinitionById(
                level,
                villager,
                player,
                TRADE_REFRESH_SPECIAL_ORDER_CONFIRM_OPTIONS_ID)
                .orElse(session.definition());
        String line = tradeRefreshLine(level, villager, player, "trade_refresh.special_order_confirm", replacements);
        ForcedDialogueDefinition definition = tradeRefreshDefinition(line, optionDefinition);
        ForcedDialogueContext context = tradeRefreshContext(level, villager, player, replacements);
        updateTradeRefreshSession(player, villager, definition, context, session.tradeRefreshOfferIndex(), definitionId.toString());
    }

    private static ForcedDialogueOption dynamicTradeRefreshOption(String id, String label, int order) {
        return new ForcedDialogueOption(
                id,
                label,
                List.of(),
                0,
                false,
                0.0D,
                false,
                order,
                ForcedDialogueStolenItemReturn.empty(),
                ForcedDialogueItemPayment.empty(),
                VillagerReputationCondition.empty());
    }

    private static void updateTradeRefreshSession(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueDefinition definition,
            ForcedDialogueContext context,
            int offerIndex,
            String definitionId) {
        ServerLevel level = player.serverLevel();
        FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                villager.getUUID(),
                definition,
                context,
                level.dimension(),
                villager.blockPosition().immutable(),
                List.of(),
                level.getGameTime(),
                offerIndex,
                definitionId));
        String line = definition.selectLine(level.getRandom());
        if (!line.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    level,
                    villager,
                    line,
                    VillagerInteractionService.villagerSpeakerLabel(villager));
        }
        VillagerInteractionService.sendForcedDialogueReputation(
                player,
                villager,
                forcedOptions(definition, level, villager, player),
                definition.forceCameraTowardsVillager());
    }

    private static boolean isTradeRefreshUnavailable(String messageKey) {
        return "trade_refresh.not_ready".equals(messageKey)
                || "trade_refresh.unavailable".equals(messageKey)
                || "trade_refresh.special_order_unavailable".equals(messageKey)
                || "trade_refresh.special_order_pending".equals(messageKey)
                || "trade_refresh.special_order_cooldown".equals(messageKey)
                || "trade_refresh.special_order_payment_missing".equals(messageKey);
    }

    private static ForcedDialogueContext tradeRefreshContext(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            Map<String, String> replacements) {
        String tradeItem = replacements.getOrDefault("trade_item", "");
        BlockPos pos = villager.blockPosition();
        return new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                player.getDisplayName().getString(),
                "",
                "",
                "",
                tradeItem,
                "",
                1,
                tradeItem,
                tradeItem,
                "",
                "",
                0,
                0,
                pos.getX(),
                pos.getY(),
                pos.getZ());
    }

    private static void handleFailedStolenItemReturn(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueStolenItemReturn stolenItemReturn,
            boolean forceEndConversation) {
        if (stolenItemReturn.failureReputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(player.serverLevel(), villager, player, stolenItemReturn.failureReputationDelta());
        }

        String response = ForcedDialogueResources.resolveTemplate(
                stolenItemReturn.selectFailureResponse(player.serverLevel().getRandom()),
                session.context());
        if (!response.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    player.serverLevel(),
                    villager,
                    response,
                    VillagerInteractionService.villagerSpeakerLabel(villager)
            );
        }

        if (stolenItemReturn.failureEndConversation() || stolenItemReturn.failureAggro() || forceEndConversation) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
        } else {
            VillagerInteractionService.sendForcedDialogueReputation(
                    player,
                    villager,
                    forcedOptions(session.definition(), player.serverLevel(), villager, player),
                    session.definition().forceCameraTowardsVillager());
        }
        if (stolenItemReturn.failureAggro()) {
            VillagerRetaliationHandler.forceAnger(villager, player);
        }
    }

    private static void handleFailedItemPayment(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueItemPayment itemPayment,
            boolean forceEndConversation) {
        if (itemPayment.failureReputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addDialogueReputation(player.serverLevel(), villager, player, itemPayment.failureReputationDelta());
        }

        String response = ForcedDialogueResources.resolveTemplate(
                itemPayment.selectFailureResponse(player.serverLevel().getRandom()),
                session.context(),
                itemPayment.removal().replacements());
        if (!response.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    player.serverLevel(),
                    villager,
                    response,
                    VillagerInteractionService.villagerSpeakerLabel(villager)
            );
        }

        if (itemPayment.failureEndConversation() || itemPayment.failureAggro() || forceEndConversation) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
        } else {
            VillagerInteractionService.sendForcedDialogueReputation(
                    player,
                    villager,
                    forcedOptions(session.definition(), player.serverLevel(), villager, player),
                    session.definition().forceCameraTowardsVillager());
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

    private static Optional<List<ItemStack>> executeStolenItemReturn(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueStolenItemReturn stolenItemReturn) {
        if (session.removedStacks().isEmpty()) {
            return Optional.empty();
        }

        Optional<ItemTransferTarget> target = transferTarget(player, villager, session, stolenItemReturn.destination());
        if (target.isEmpty()) {
            return Optional.empty();
        }

        Optional<ItemTransferTarget> overflowTarget = Optional.ofNullable(stolenItemReturn.overflowDestination())
                .flatMap(destination -> transferTarget(player, villager, session, destination));
        boolean targetFits = target.get().canAccept(session.removedStacks());
        if (stolenItemReturn.requireSpace() && !targetFits && overflowTarget.isEmpty()) {
            return Optional.empty();
        }
        if (overflowTarget.isPresent() && !overflowTarget.get().canAccept(session.removedStacks())) {
            return Optional.empty();
        }

        Optional<List<ItemStack>> removedStacks = removeSpecificStacks(player, session.removedStacks());
        if (removedStacks.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> remainder = target.get().accept(removedStacks.get());
        if (!remainder.isEmpty() && overflowTarget.isPresent()) {
            remainder = overflowTarget.get().accept(remainder);
        }
        return !stolenItemReturn.requireSpace() || remainder.isEmpty()
                ? Optional.of(removedStacks.get())
                : Optional.empty();
    }

    private static Optional<List<ItemStack>> removeSpecificStacks(ServerPlayer player, List<ItemStack> targets) {
        if (!canRemoveSpecificStacks(player, targets)) {
            return Optional.empty();
        }

        List<ItemStack> removedStacks = new ArrayList<>();
        for (ItemStack target : targets) {
            int remaining = target.getCount();
            for (ItemStack stack : removablePlayerStacks(player)) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target)) {
                    continue;
                }

                int removed = Math.min(remaining, stack.getCount());
                removedStacks.add(stack.copyWithCount(removed));
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
        return Optional.of(List.copyOf(removedStacks));
    }

    private static boolean canRemoveSpecificStacks(ServerPlayer player, List<ItemStack> targets) {
        List<ItemStack> availableStacks = removablePlayerStacks(player).stream()
                .map(ItemStack::copy)
                .toList();
        for (ItemStack target : targets) {
            int remaining = target.getCount();
            for (ItemStack stack : availableStacks) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target)) {
                    continue;
                }

                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> removablePlayerStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static Optional<ItemTransferTarget> transferTarget(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueItemDestination destination) {
        return switch (destination) {
            case DISCARD -> Optional.of(discardTransferTarget());
            case VILLAGER_INVENTORY -> Optional.of(villagerInventoryTransferTarget(villager));
            case VILLAGER_INVENTORY_THEN_SOURCE_CONTAINER -> sourceContainer(player, session)
                    .map(container -> chainedTransferTarget(villagerInventoryTransferTarget(villager), containerTransferTarget(container)));
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

    private static ItemTransferTarget chainedTransferTarget(ItemTransferTarget primary, ItemTransferTarget fallback) {
        return new ItemTransferTarget() {
            @Override
            public boolean canAccept(List<ItemStack> stacks) {
                return primary.canAccept(stacks) || fallback.canAccept(stacks);
            }

            @Override
            public List<ItemStack> accept(List<ItemStack> stacks) {
                List<ItemStack> remainder = primary.accept(stacks);
                return remainder.isEmpty() ? List.of() : fallback.accept(remainder);
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

    public static void maybeTriggerPlayerItemProximity(ServerLevel level, Villager villager) {
        if (!playerItemProximityForcedDialogueEnabled()
                || !villager.isAlive()
                || villager.isBaby()
                || villager.isTrading()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % PLAYER_ITEM_PROXIMITY_SCAN_INTERVAL_TICKS
                != Math.floorMod(villager.getUUID().getLeastSignificantBits(), PLAYER_ITEM_PROXIMITY_SCAN_INTERVAL_TICKS)) {
            return;
        }

        List<ForcedDialogueDefinition> definitions = ForcedDialogueResources.playerItemProximityCandidates(level.getServer());
        if (definitions.isEmpty()) {
            return;
        }

        List<ForcedDialogueDefinition> witnessDefinitions = new ArrayList<>();
        double maxRadius = 0.0D;
        for (ForcedDialogueDefinition definition : definitions) {
            if (!definition.matchesWitness(villager)) {
                continue;
            }
            witnessDefinitions.add(definition);
            maxRadius = Math.max(maxRadius, definition.witnessRadius());
        }
        if (maxRadius <= 0.0D) {
            return;
        }

        AABB area = villager.getBoundingBox().inflate(maxRadius);
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (player.isAlive()
                    && !player.isSpectator()
                    && !player.isCreative()
                    && !FORCED_SESSIONS.containsKey(player.getUUID())) {
                players.add(player);
            }
        }
        if (players.size() > 1) {
            players.sort(Comparator.comparingDouble(player -> villager.distanceToSqr(player)));
        }
        for (ServerPlayer player : players) {
            if (tryPlayerItemProximityDefinitions(level, villager, player, witnessDefinitions, gameTime, true)
                    || tryPlayerItemProximityDefinitions(level, villager, player, witnessDefinitions, gameTime, false)) {
                prunePlayerItemProximityCooldowns(gameTime);
                return;
            }
        }
    }

    private static boolean tryPlayerItemProximityDefinitions(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            List<ForcedDialogueDefinition> definitions,
            long gameTime,
            boolean chatOutput) {
        for (ForcedDialogueDefinition definition : definitions) {
            if (isChatOutput(definition) != chatOutput
                    || !definition.matchesPlayerItem(player)
                    || !definitionMatchesReputation(level, villager, player, definition)
                    || villager.distanceToSqr(player) > definition.witnessRadius() * definition.witnessRadius()
                    || (definition.requiresLineOfSight() && !villager.hasLineOfSight(player))
                    || !playerItemProximityReady(gameTime, villager, player, definition)) {
                continue;
            }

            boolean triggered = chatOutput
                    ? triggerPlayerItemProximityChat(level, villager, player, definition)
                    : triggerPlayerItemProximity(level, villager, player, definition);
            if (triggered) {
                markPlayerItemProximityUsed(gameTime, villager, player, definition);
                return true;
            }
        }
        return false;
    }

    public static boolean triggerRetaliationStarted(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!retaliationForcedDialogueEnabled()) {
            return false;
        }
        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
        int priorRetaliations = VillageEventMemory.countForPlayer(
                VillageEventMemory.recentForVillage(level, villager),
                player.getUUID(),
                VillageEventMemory.EventTag.VILLAGER_RETALIATION_STARTED
        );
        triggerRetaliationChat(level, villager, player, priorRetaliations, targetTypeId);

        List<ForcedDialogueDefinition> candidates = ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.RETALIATION_STARTED, null, targetTypeId)
                .stream()
                .filter(definition -> !isChatOutput(definition))
                .filter(definition -> definition.matchesWitness(villager))
                .filter(definition -> definitionMatchesReputation(level, villager, player, definition))
                .filter(definition -> villager.distanceToSqr(player) <= definition.witnessRadius() * definition.witnessRadius())
                .filter(definition -> !definition.requiresLineOfSight() || villager.hasLineOfSight(player))
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }

        for (ForcedDialogueDefinition definition : candidates) {
            if (!definition.matchesRecentRetaliations(priorRetaliations)) {
                continue;
            }
            if (triggerRetaliation(level, villager, player, definition, priorRetaliations, targetTypeId)) {
                return true;
            }
        }
        return false;
    }

    public static void triggerRetaliationChat(ServerLevel level, Villager villager, LivingEntity target) {
        if (target instanceof ServerPlayer || !retaliationForcedDialogueEnabled()) {
            return;
        }
        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.RETALIATION_STARTED, null, targetTypeId)
                .stream()
                .filter(ForcedDialogueService::isChatOutput)
                .filter(definition -> definition.matchesWitness(villager))
                .filter(definition -> villager.distanceToSqr(target) <= definition.witnessRadius() * definition.witnessRadius())
                .filter(definition -> !definition.requiresLineOfSight() || villager.hasLineOfSight(target))
                .filter(definition -> definition.matchesRecentRetaliations(0))
                .anyMatch(definition -> triggerRetaliationChat(level, villager, target, definition, targetTypeId));
    }

    private static boolean triggerRetaliationChat(
            ServerLevel level,
            Villager villager,
            LivingEntity target,
            ForcedDialogueDefinition definition,
            ResourceLocation targetTypeId) {
        if (!rollChance(level, definition.chance())) {
            return true;
        }
        String villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        String targetName = target.getDisplayName().getString();
        String targetKind = target.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        ForcedDialogueContext context = new ForcedDialogueContext(
                villagerName,
                "",
                targetName,
                targetKind,
                targetTypeId == null ? "" : targetTypeId.toString(),
                targetName,
                targetTypeId == null ? "" : targetTypeId.toString(),
                1,
                targetName,
                targetName,
                "",
                "",
                0,
                0,
                villager.blockPosition().getX(),
                villager.blockPosition().getY(),
                villager.blockPosition().getZ()
        );
        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (!line.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    level,
                    villager,
                    line,
                    VillagerInteractionService.villagerSpeakerLabel(villager),
                    outputRadius(definition)
            );
        }
        return true;
    }

    private static void triggerContainerTheft(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
            List<ItemStack> removedStacks) {
        triggerContainerChat(level, player, snapshot, removedCount, removedStacks, ForcedDialogueTrigger.CONTAINER_THEFT);
        ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.CONTAINER_THEFT, snapshot.lootTable())
                .stream()
                .filter(definition -> !isChatOutput(definition))
                .anyMatch(definition -> trigger(level, player, snapshot, removedCount, removedStacks, definition));
    }

    private static void triggerContainerOpened(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot) {
        triggerContainerChat(level, player, snapshot, 0, List.of(), ForcedDialogueTrigger.CONTAINER_OPENED);
        ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.CONTAINER_OPENED, snapshot.lootTable())
                .stream()
                .filter(definition -> !isChatOutput(definition))
                .anyMatch(definition -> trigger(level, player, snapshot, 0, List.of(), definition));
    }

    private static void triggerContainerBroken(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot) {
        triggerContainerChat(level, player, snapshot, snapshot.itemCount(), snapshot.itemStacks(), ForcedDialogueTrigger.CONTAINER_BROKEN);
        ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.CONTAINER_BROKEN, snapshot.lootTable())
                .stream()
                .filter(definition -> !isChatOutput(definition))
                .anyMatch(definition -> trigger(level, player, snapshot, snapshot.itemCount(), snapshot.itemStacks(), definition));
    }

    private static void triggerContainerChat(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
            List<ItemStack> removedStacks,
            ForcedDialogueTrigger trigger) {
        ForcedDialogueResources
                .selectCandidates(level.getServer(), trigger, snapshot.lootTable())
                .stream()
                .filter(ForcedDialogueService::isChatOutput)
                .anyMatch(definition -> triggerContainerChat(level, player, snapshot, removedCount, removedStacks, definition));
    }

    private static boolean triggerContainerChat(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
            List<ItemStack> removedStacks,
            ForcedDialogueDefinition definition) {
        if (!rollChance(level, definition.chance())) {
            return true;
        }
        Villager witness = findWitness(level, player, snapshot.pos(), definition).orElse(null);
        if (witness == null) {
            return false;
        }
        int priorContainerThefts = VillageEventMemory.countForPlayer(
                VillageEventMemory.recentForVillage(level, witness),
                player.getUUID(),
                VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT);
        if (!definition.matchesRecentContainerThefts(priorContainerThefts)) {
            return false;
        }
        if (!definitionMatchesReputation(level, witness, player, definition)) {
            return false;
        }

        ItemStack representativeStack = representativeRemovedStack(removedStacks);
        ForcedDialogueContext context = new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(witness).getString(),
                player.getDisplayName().getString(),
                player.getDisplayName().getString(),
                "player",
                BuiltInRegistries.ENTITY_TYPE.getKey(player.getType()).toString(),
                representativeStack.isEmpty() ? "items" : representativeStack.getHoverName().getString(),
                representativeStack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(representativeStack.getItem()).toString(),
                representativeStack.isEmpty() ? removedCount : representativeStack.getCount(),
                representativeStack.isEmpty() ? "items" : itemStackName(representativeStack),
                removedStacks.isEmpty() ? "items" : itemListName(removedStacks),
                snapshot.containerName().getString(),
                snapshot.lootTable() == null ? "" : snapshot.lootTable().toString(),
                priorContainerThefts,
                0,
                snapshot.pos().getX(),
                snapshot.pos().getY(),
                snapshot.pos().getZ()
        );
        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (!line.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    level,
                    witness,
                    line,
                    VillagerInteractionService.villagerSpeakerLabel(witness),
                    outputRadius(definition)
            );
        }
        return true;
    }

    private static boolean trigger(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
            List<ItemStack> removedStacks,
            ForcedDialogueDefinition definition) {
        Villager witness = findWitness(level, player, snapshot.pos(), definition).orElse(null);
        if (witness == null) {
            return false;
        }
        int priorContainerThefts = VillageEventMemory.countForPlayer(
                VillageEventMemory.recentForVillage(level, witness),
                player.getUUID(),
                VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT);
        if (!definition.matchesRecentContainerThefts(priorContainerThefts)) {
            return false;
        }
        if (!definitionMatchesReputation(level, witness, player, definition)) {
            return false;
        }

        ItemStack representativeStack = representativeRemovedStack(removedStacks);
        ForcedDialogueContext context = new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(witness).getString(),
                player.getDisplayName().getString(),
                player.getDisplayName().getString(),
                "player",
                BuiltInRegistries.ENTITY_TYPE.getKey(player.getType()).toString(),
                representativeStack.isEmpty() ? "items" : representativeStack.getHoverName().getString(),
                representativeStack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(representativeStack.getItem()).toString(),
                representativeStack.isEmpty() ? removedCount : representativeStack.getCount(),
                representativeStack.isEmpty() ? "items" : itemStackName(representativeStack),
                removedStacks.isEmpty() ? "items" : itemListName(removedStacks),
                snapshot.containerName().getString(),
                snapshot.lootTable() == null ? "" : snapshot.lootTable().toString(),
                priorContainerThefts,
                0,
                snapshot.pos().getX(),
                snapshot.pos().getY(),
                snapshot.pos().getZ()
        );
        int reputationDelta = definition.reputationDelta();
        if (definition.trigger() == ForcedDialogueTrigger.CONTAINER_BROKEN) {
            reputationDelta += containerBreakReputationDelta(snapshot);
        }
        if (reputationDelta != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            if (definition.trigger() == ForcedDialogueTrigger.CONTAINER_BROKEN) {
                VillagerReputationManager.addContainerBreakReputation(level, witness, player.getUUID(), reputationDelta, snapshot.pos());
            } else {
                VillagerReputationManager.addWitnessedReputation(level, witness, player.getUUID(), reputationDelta, snapshot.pos());
            }
            VillagerGossipHooks.spreadReputation(level, witness, player.getUUID(), reputationDelta);
        }
        if (definition.trigger() == ForcedDialogueTrigger.CONTAINER_THEFT) {
            rememberContainerTheft(level, witness, player, snapshot, removedStacks, removedCount);
        }

        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, witness, line);
            }
            VillagerRetaliationHandler.forceAnger(witness, player);
            return true;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, witness, line);
            }
            return true;
        }

        if (VillagerInteractionService.openForcedDialogue(
                player,
                witness,
                line,
                forcedOptions(definition, level, witness, player),
                definition.forceCameraTowardsVillager())) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    witness.getUUID(),
                    definition,
                    context,
                    snapshot.dimension(),
                    snapshot.pos(),
                    removedStacks,
                    level.getGameTime()
            ));
        } else if (!line.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, witness, line);
        }
        return true;
    }

    private static boolean triggerPlayerItemProximityChat(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        if (!rollChance(level, definition.chance())) {
            return true;
        }
        ForcedDialogueContext context = playerItemProximityContext(villager, player, definition);
        String line = ForcedDialogueResources.resolveTemplate(
                definition.selectLine(level.getRandom()),
                context,
                definition.playerItemReplacements(player));
        if (!line.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    level,
                    villager,
                    line,
                    VillagerInteractionService.villagerSpeakerLabel(villager),
                    outputRadius(definition)
            );
        }
        return true;
    }

    private static boolean triggerPlayerItemProximity(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        if (!rollChance(level, definition.chance())) {
            return true;
        }
        ForcedDialogueContext context = playerItemProximityContext(villager, player, definition);
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, villager, player.getUUID(), definition.reputationDelta(), villager.blockPosition());
            VillagerGossipHooks.spreadReputation(level, villager, player.getUUID(), definition.reputationDelta());
        }

        String line = ForcedDialogueResources.resolveTemplate(
                definition.selectLine(level.getRandom()),
                context,
                definition.playerItemReplacements(player));
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, villager, line);
            }
            VillagerRetaliationHandler.forceAnger(villager, player);
            return true;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, villager, line);
            }
            return true;
        }

        if (VillagerInteractionService.openForcedDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                definition.forceCameraTowardsVillager())) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    villager.getUUID(),
                    definition,
                    context,
                    level.dimension(),
                    villager.blockPosition().immutable(),
                    List.of(),
                    level.getGameTime()
            ));
        } else if (!line.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
        }
        return true;
    }

    private static ForcedDialogueContext playerItemProximityContext(
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
        Map<String, String> itemReplacements = definition.playerItemReplacements(player);
        String itemName = itemReplacements.getOrDefault("player_item", "");
        String itemId = itemReplacements.getOrDefault("player_item_id", "");
        String playerName = player.getDisplayName().getString();
        return new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                playerName,
                playerName,
                "player",
                targetTypeId == null ? "" : targetTypeId.toString(),
                itemName,
                itemId,
                1,
                itemName,
                itemName,
                "",
                "",
                0,
                0,
                villager.blockPosition().getX(),
                villager.blockPosition().getY(),
                villager.blockPosition().getZ()
        );
    }

    private static boolean playerItemProximityReady(
            long gameTime,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        return gameTime >= NEXT_PLAYER_ITEM_PROXIMITY_TICK.getOrDefault(
                new PlayerItemProximityKey(villager.getUUID(), player.getUUID(), definition.id()),
                0L);
    }

    private static void markPlayerItemProximityUsed(
            long gameTime,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        NEXT_PLAYER_ITEM_PROXIMITY_TICK.put(
                new PlayerItemProximityKey(villager.getUUID(), player.getUUID(), definition.id()),
                gameTime + PLAYER_ITEM_PROXIMITY_COOLDOWN_TICKS);
    }

    private static void prunePlayerItemProximityCooldowns(long gameTime) {
        NEXT_PLAYER_ITEM_PROXIMITY_TICK.entrySet().removeIf(entry -> entry.getValue() + PLAYER_ITEM_PROXIMITY_COOLDOWN_TICKS < gameTime);
    }

    private static boolean definitionMatchesReputation(
            ServerLevel level,
            Villager witness,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return true;
        }
        ReputationSnapshot reputation = VillagerReputationManager.getReputationSnapshot(level, witness, player.getUUID());
        return definition.matchesReputation(reputation.value(), reputation.level());
    }

    private static boolean triggerRetaliation(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            int priorRetaliations,
            ResourceLocation targetTypeId) {
        if (!rollChance(level, definition.chance())) {
            return true;
        }
        String villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        String targetName = player.getDisplayName().getString();
        String targetKind = player.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        ForcedDialogueContext context = new ForcedDialogueContext(
                villagerName,
                player.getDisplayName().getString(),
                targetName,
                targetKind,
                targetTypeId == null ? "" : targetTypeId.toString(),
                targetName,
                targetTypeId == null ? "" : targetTypeId.toString(),
                1,
                targetName,
                targetName,
                "",
                "",
                0,
                priorRetaliations,
                villager.blockPosition().getX(),
                villager.blockPosition().getY(),
                villager.blockPosition().getZ()
        );
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, villager, player.getUUID(), definition.reputationDelta(), villager.blockPosition());
            VillagerGossipHooks.spreadReputation(level, villager, player.getUUID(), definition.reputationDelta());
        }
        VillageEventMemory.rememberRetaliation(level, villager.blockPosition(), villager, player, villagerName);

        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, villager, line);
            }
            VillagerRetaliationHandler.forceAngerSilently(villager, player);
            return true;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                VillagerInteractionService.sendVillagerNotice(player, villager, line);
            }
            return true;
        }

        if (VillagerInteractionService.openForcedDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                definition.forceCameraTowardsVillager())) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    villager.getUUID(),
                    definition,
                    context,
                    level.dimension(),
                    villager.blockPosition().immutable(),
                    List.of(),
                    level.getGameTime()
            ));
        } else if (!line.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
        }
        return true;
    }

    private static void triggerRetaliationChat(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            int priorRetaliations,
            ResourceLocation targetTypeId) {
        ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.RETALIATION_STARTED, null, targetTypeId)
                .stream()
                .filter(ForcedDialogueService::isChatOutput)
                .filter(definition -> definition.matchesWitness(villager))
                .filter(definition -> villager.distanceToSqr(player) <= definition.witnessRadius() * definition.witnessRadius())
                .filter(definition -> !definition.requiresLineOfSight() || villager.hasLineOfSight(player))
                .filter(definition -> definition.matchesRecentRetaliations(priorRetaliations))
                .anyMatch(definition -> triggerRetaliationChat(level, villager, player, definition, priorRetaliations, targetTypeId));
    }

    private static boolean triggerRetaliationChat(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            int priorRetaliations,
            ResourceLocation targetTypeId) {
        if (!rollChance(level, definition.chance())) {
            return true;
        }
        String villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        String targetName = player.getDisplayName().getString();
        String targetKind = player.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        ForcedDialogueContext context = new ForcedDialogueContext(
                villagerName,
                player.getDisplayName().getString(),
                targetName,
                targetKind,
                targetTypeId == null ? "" : targetTypeId.toString(),
                targetName,
                targetTypeId == null ? "" : targetTypeId.toString(),
                1,
                targetName,
                targetName,
                "",
                "",
                0,
                priorRetaliations,
                villager.blockPosition().getX(),
                villager.blockPosition().getY(),
                villager.blockPosition().getZ()
        );
        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (!line.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    level,
                    villager,
                    line,
                    VillagerInteractionService.villagerSpeakerLabel(villager),
                    outputRadius(definition)
            );
        }
        return true;
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
                .map(option -> DialogueOptionDefinition.simple(option.id(), option.label(), DialogueRequestType.QUESTION, option.order()))
                .toList();
    }

    private static boolean isChatOutput(ForcedDialogueDefinition definition) {
        return definition.output().mode() == ForcedDialogueOutputMode.CHAT;
    }

    private static double outputRadius(ForcedDialogueDefinition definition) {
        return definition.output().radius() > 0.0D
                ? definition.output().radius()
                : VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get();
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
                .filter(definition::matchesWitness)
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
        return lootTable != null
                || VillagerRetaliationConfig.CONTAINER_WATCH_MODE.get() == ContainerWatchMode.ALL_WATCHED_CONTAINERS;
    }

    private static boolean containerForcedDialogueEnabled() {
        return VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                && VillagerRetaliationConfig.ENABLE_CONTAINER_FORCED_DIALOGUE.get();
    }

    private static boolean retaliationForcedDialogueEnabled() {
        return VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                && VillagerRetaliationConfig.ENABLE_RETALIATION_FORCED_DIALOGUE.get();
    }

    private static boolean playerItemProximityForcedDialogueEnabled() {
        return VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                && VillagerRetaliationConfig.ENABLE_PLAYER_ITEM_PROXIMITY_FORCED_DIALOGUE.get();
    }

    private static ResourceLocation generatedContainerLootTable(ServerLevel level, BlockPos pos) {
        ResourceLocation liveLootTable = liveContainerLootTable(level, pos);
        if (liveLootTable != null) {
            GeneratedContainerSavedData.get(level).remember(level.dimension(), pos, liveLootTable);
            return liveLootTable;
        }
        return GeneratedContainerSavedData.get(level)
                .lootTable(level.dimension(), pos)
                .orElse(null);
    }

    private static ResourceLocation liveContainerLootTable(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RandomizableContainer container) {
            ResourceKey<LootTable> lootTable = container.getLootTable();
            return lootTable == null ? null : lootTable.location();
        }
        return null;
    }

    private static void unpackContainerLootTable(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RandomizableContainer container) {
            container.unpackLootTable(player);
        }
    }

    private static int containerBreakReputationDelta(ContainerSnapshot snapshot) {
        int delta = VillagerRetaliationConfig.CONTAINER_BREAK_REPUTATION_LOSS.get();
        if (snapshot.lootTable() != null && snapshot.itemCount() > 0) {
            delta += snapshot.itemCount() * VillagerRetaliationConfig.GENERATED_CONTAINER_BREAK_ITEM_REPUTATION_LOSS.get();
        }
        return delta;
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

    private static int countContainerItems(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static List<ItemStack> snapshotContainerItems(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }

    private static ItemStack restoreToOpenContainer(AbstractContainerMenu menu, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (Slot slot : menu.slots) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !slot.mayPlace(remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), slot.getMaxStackSize(remainder));
            int moveCount = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (moveCount > 0) {
                existing.grow(moveCount);
                remainder.shrink(moveCount);
                slot.setChanged();
            }
        }

        for (Slot slot : menu.slots) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot.container instanceof Inventory || !slot.getItem().isEmpty() || !slot.mayPlace(remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), slot.getMaxStackSize(remainder)));
            slot.set(remainder.copyWithCount(moveCount));
            slot.setChanged();
            remainder.shrink(moveCount);
        }
        return remainder;
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

    private static String itemStackName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        return stack.getCount() > 1 ? stack.getCount() + "x " + name : name;
    }

    private static String itemListName(List<ItemStack> stacks) {
        return stacks.stream()
                .map(ForcedDialogueService::itemStackName)
                .reduce((left, right) -> left + ", " + right)
                .orElse("items");
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
            List<ItemStack> removedStacks,
            long startedGameTime,
            int tradeRefreshOfferIndex,
            String tradeRefreshDefinitionId) {
        private ForcedDialogueSession(
                UUID villagerId,
                ForcedDialogueDefinition definition,
                ForcedDialogueContext context,
                net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
                BlockPos sourceContainerPos,
                List<ItemStack> removedStacks,
                long startedGameTime) {
            this(
                    villagerId,
                    definition,
                    context,
                    sourceContainerDimension,
                    sourceContainerPos,
                    removedStacks,
                    startedGameTime,
                    -1,
                    "");
        }
    }

    private record PlayerItemProximityKey(
            UUID villagerId,
            UUID playerId,
            String definitionId) {
    }

    private interface ItemTransferTarget {
        boolean canAccept(List<ItemStack> stacks);

        List<ItemStack> accept(List<ItemStack> stacks);
    }
}
