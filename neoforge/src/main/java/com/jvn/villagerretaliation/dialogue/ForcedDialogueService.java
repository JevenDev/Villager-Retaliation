package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger;
import com.jvn.villagerretaliation.config.ContainerWatchMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueFollowUp;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueOutputMode;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueStolenItemReturn;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources.ForcedDialogueTrigger;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.GeneratedContainerTooltipPayload;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager.ReputationSnapshot;
import com.jvn.villagerretaliation.trade.VillagerSpecialOrderService;
import com.jvn.villagerretaliation.trade.VillagerTradeRefreshService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval;
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
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final String TRADE_REFRESH_READY_MESSAGE_KEY = "trade_refresh.ready";
    private static final String TRADE_REFRESH_READY_INTERJECTION_MESSAGE_KEY = "trade_refresh.ready_interjection";
    private static final String TRADE_REFRESH_READY_THEFT_INTERJECTION_MESSAGE_KEY = "trade_refresh.ready_theft_interjection";
    private static final String TRADE_REFRESH_READY_OPTIONS_ID = "trade_refresh.ready_options";
    private static final String TRADE_REFRESH_REVERED_OPTIONS_ID = "trade_refresh.revered_options";
    private static final String TRADE_REFRESH_SPECIAL_ORDER_SELECT_OPTIONS_ID = "trade_refresh.special_order_select_options";
    private static final String TRADE_REFRESH_SPECIAL_ORDER_CONFIRM_OPTIONS_ID = "trade_refresh.special_order_confirm_options";
    private static final String TRADE_REFRESH_SPECIAL_ORDER_STATUS_OPTIONS_ID = "trade_refresh.special_order_status_options";
    private static final String TRADE_REFRESH_SURPRISE_OPTION_ID = "trade_refresh.surprise_me";
    private static final String TRADE_REFRESH_SPECIAL_ORDER_OPTION_ID = "trade_refresh.special_order";
    private static final String TRADE_REFRESH_CONFIRM_SPECIAL_ORDER_OPTION_ID = "trade_refresh.confirm_special_order";
    private static final String TRADE_REFRESH_REQUIREMENTS_OPTION_ID = "trade_refresh.requirements";
    private static final String CONTAINER_THEFT_BACKUP_MESSAGE_KEY = "container_theft.backup_interjection";
    private static final String CONTAINER_THEFT_BACKUP_DEFINITION_ID = "container_theft.backup_interjection";
    private static final String CONTAINER_OPENED_BACKUP_MESSAGE_KEY = "container_opened.backup_interjection";
    private static final String CONTAINER_OPENED_BACKUP_DEFINITION_ID = "container_opened.backup_interjection";
    private static final String CONTAINER_OPENED_VOUCH_ALLOW_MESSAGE_KEY = "container_opened.vouch.allow";
    private static final String CONTAINER_OPENED_VOUCH_DENY_MESSAGE_KEY = "container_opened.vouch.deny";
    private static final String RESTITUTION_DEFINITION_SUFFIX = ".restitution_options";
    private static final String RESTITUTION_PAY_SUFFIX = ".pay";
    private static final String RESTITUTION_HAGGLE_SUFFIX = ".haggle";
    private static final String RESTITUTION_REFUSE_SUFFIX = ".refuse";
    private static final String RESTITUTION_THREATEN_SUFFIX = ".threaten";
    private static final String RESTITUTION_PROMPT_MESSAGE_KEY = "forced.restitution.prompt";
    private static final String RESTITUTION_HAGGLE_ACCEPT_MESSAGE_KEY = "forced.restitution.haggle.accept";
    private static final String RESTITUTION_HAGGLE_DENY_MESSAGE_KEY = "forced.restitution.haggle.deny";
    private static final String RESTITUTION_PAY_LABEL_MESSAGE_KEY = "forced.restitution.option.pay.label";
    private static final String RESTITUTION_HAGGLE_LABEL_MESSAGE_KEY = "forced.restitution.option.haggle.label";
    private static final String RESTITUTION_REFUSE_LABEL_MESSAGE_KEY = "forced.restitution.option.refuse.label";
    private static final String RESTITUTION_THREATEN_LABEL_MESSAGE_KEY = "forced.restitution.option.threaten.label";
    private static final String RESTITUTION_PAY_SUCCESS_MESSAGE_KEY = "forced.restitution.pay.success";
    private static final String RESTITUTION_PAY_FAILURE_MESSAGE_KEY = "forced.restitution.pay.failure";
    private static final String RESTITUTION_REFUSE_RESPONSE_MESSAGE_KEY = "forced.restitution.refuse.response";
    private static final String RESTITUTION_THREATEN_RESPONSE_MESSAGE_KEY = "forced.restitution.threaten.response";
    private static final String RESTITUTION_REDUCED_PAY_SUCCESS_MESSAGE_KEY = "forced.restitution.reduced_pay.success";
    public static final String SPECIAL_ORDER_STATUS_ROOT_OPTION_ID = "trade_refresh.special_order.status";
    private static final long RECENT_CONTAINER_CLICK_TICKS = 8L;
    private static final long FORCED_SESSION_TIMEOUT_TICKS = 20L * 60L;
    private static final long TRADE_REFRESH_READY_SCAN_INTERVAL_TICKS = 40L;
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
                .filter(option -> !session.disabledOptionIds().contains(option.id()))
                .filter(option -> optionMatches(player.serverLevel(), villager, player, option))
                .findFirst();
        if (selected.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.unknown_dialogue_option");
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }

        return handleSelectedOption(player, villager, session, selected.get(), false, false);
    }

    public static boolean hasSession(ServerPlayer player, Villager villager) {
        ForcedDialogueSession session = FORCED_SESSIONS.get(player.getUUID());
        return session != null && session.villagerId().equals(villager.getUUID());
    }

    public static boolean isStaleConversationEndRequest(ServerPlayer player, int entityId) {
        ForcedDialogueSession session = FORCED_SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        Entity entity = player.serverLevel().getEntity(entityId);
        return entity instanceof Villager villager && !session.villagerId().equals(villager.getUUID());
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
        ForcedDialogueDefinition options = tradeRefreshRequirementOptions(
                level,
                villager,
                player,
                optionDefinition.get(),
                messageKey,
                replacements);
        ForcedDialogueDefinition definition = tradeRefreshDefinition(line, options);
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
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        Optional<String> line = VillagerDialogueResources.message(context, messageKey, replacements);
        if (line.isEmpty()) {
            String fallbackMessageKey = baseInterjectionMessageKey(messageKey);
            if (!fallbackMessageKey.equals(messageKey)) {
                line = VillagerDialogueResources.message(context, fallbackMessageKey, replacements);
            }
        }
        return line.orElse(messageKey);
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
            VillagerTradeRefreshService.handleSurpriseRequest(player, villager, offerIndex);
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
            VillagerTradeRefreshService.handleSurpriseRequest(player, villager, offerIndex);
        }
    }

    public static void openTradeRefreshReadyDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            VillagerTradeRefreshService.ReadyRefreshResult readyRefreshes) {
        openTradeRefreshReadyDialogue(level, villager, player, readyRefreshes, TRADE_REFRESH_READY_MESSAGE_KEY, Map.of());
    }

    private static boolean openTradeRefreshReadyDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            VillagerTradeRefreshService.ReadyRefreshResult readyRefreshes,
            String messageKey,
            Map<String, String> extraReplacements) {
        return openTradeRefreshReadyDialogue(
                level,
                villager,
                player,
                readyRefreshes,
                messageKey,
                extraReplacements,
                List.of(villager.getUUID()));
    }

    private static boolean openTradeRefreshReadyDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            VillagerTradeRefreshService.ReadyRefreshResult readyRefreshes,
            String messageKey,
            Map<String, String> extraReplacements,
            List<UUID> participantVillagerIds) {
        return openTradeRefreshReadyDialogue(
                level,
                villager,
                player,
                readyRefreshes,
                messageKey,
                extraReplacements,
                participantVillagerIds,
                List.of(villager.getUUID()));
    }

    private static boolean openTradeRefreshReadyDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            VillagerTradeRefreshService.ReadyRefreshResult readyRefreshes,
            String messageKey,
            Map<String, String> extraReplacements,
            List<UUID> participantVillagerIds,
            List<UUID> spokenVillagerIds) {
        if (!readyRefreshes.hasPlayerReadyTrades()) {
            return false;
        }

        Map<String, String> replacements = new HashMap<>(readyRefreshes.replacements());
        replacements.putAll(extraReplacements);
        String line = tradeRefreshLine(level, villager, player, messageKey, replacements);
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
            return true;
        }

        player.closeContainer();
        Optional<ForcedDialogueDefinition> optionDefinition = tradeRefreshOptionDefinitionById(
                level,
                villager,
                player,
                TRADE_REFRESH_READY_OPTIONS_ID);
        if (optionDefinition.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
            return true;
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
                    true,
                    replacements,
                    participantVillagerIds,
                    spokenVillagerIds
            ));
            return true;
        } else {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
            return true;
        }
    }

    public static boolean tryOpenTradeRefreshReadyDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player) {
        if (FORCED_SESSIONS.containsKey(player.getUUID())
                || VillagerConversationService.isConversing(player)
                || VillagerConversationService.isConversing(villager)
                || (VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                        && !VillagerInteractionService.canUseForcedInteractionSystem(player, villager))
                || !VillagerTradeRefreshService.hasReadyRefreshesForPlayer(level, villager, player)) {
            return false;
        }

        VillagerTradeRefreshService.ReadyRefreshResult readyRefreshes =
                VillagerTradeRefreshService.applyReadyRefreshesDetailed(level, villager, player);
        VillagerTradeRefreshService.sendState(player, villager);
        if (!readyRefreshes.hasPlayerReadyTrades()) {
            return false;
        }

        openTradeRefreshReadyDialogue(level, villager, player, readyRefreshes);
        return true;
    }

    public static boolean openSpecialOrderStatusDialogue(ServerPlayer player, Villager villager) {
        ServerLevel level = player.serverLevel();
        List<VillagerSpecialOrderService.ActiveOrderStatus> activeOrders =
                VillagerSpecialOrderService.activeOrderStatuses(level, villager, player.getUUID());
        if (activeOrders.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "trade_refresh.special_order_status_empty");
            return true;
        }
        Optional<ForcedDialogueDefinition> optionDefinition = tradeRefreshOptionDefinitionById(
                level,
                villager,
                player,
                TRADE_REFRESH_SPECIAL_ORDER_STATUS_OPTIONS_ID);
        if (optionDefinition.isEmpty()) {
            String fallback = tradeRefreshLine(level, villager, player, "trade_refresh.special_order_status_select", Map.of());
            VillagerInteractionService.sendVillagerNotice(player, villager, fallback);
            return true;
        }

        ForcedDialogueDefinition definition = specialOrderStatusDefinition(
                level,
                villager,
                player,
                "trade_refresh.special_order_status_select",
                Map.of(),
                optionDefinition.get(),
                activeOrders);
        ForcedDialogueContext context = tradeRefreshContext(level, villager, player, Map.of());
        updateTradeRefreshSession(player, villager, definition, context, -1, "");
        return true;
    }

    public static boolean handleLeaveRequest(ServerPlayer player, Villager villager, boolean forceEndConversation) {
        ForcedDialogueSession session = FORCED_SESSIONS.get(player.getUUID());
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || session == null
                || !session.villagerId().equals(villager.getUUID())) {
            return false;
        }
        Optional<ForcedDialogueOption> selected = selectLeaveOption(player, villager, session);
        if (selected.isEmpty()) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            return true;
        }
        return handleSelectedOption(player, villager, session, selected.get(), forceEndConversation, true);
    }

    private static Optional<ForcedDialogueOption> selectLeaveOption(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(player.serverLevel(), villager, player.getUUID());
        return session.definition().options().stream()
                .filter(option -> shouldOfferStolenItemReturnOption(session, option))
                .filter(option -> option.id().equals(LEAVE_OPTION_ID))
                .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                .findFirst()
                .or(() -> session.definition().leaveOptions().stream()
                        .filter(option -> shouldOfferStolenItemReturnOption(session, option))
                        .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                        .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                        .findFirst())
                .or(() -> session.definition().options().stream()
                        .filter(option -> shouldOfferStolenItemReturnOption(session, option))
                        .filter(option -> option.id().equals(LEAVE_OPTION_ID))
                        .findFirst())
                .or(() -> shouldOfferStolenItemReturnOption(session, session.definition().leaveOption())
                        ? Optional.of(session.definition().leaveOption())
                        : Optional.empty());
    }

    private static boolean shouldOfferStolenItemReturnOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return !session.stolenItemsResolved() || option.stolenItemReturn().isEmpty();
    }

    private static boolean handleSelectedOption(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueOption option,
            boolean forceEndConversation) {
        return handleSelectedOption(player, villager, session, option, forceEndConversation, false);
    }

    private static boolean handleSelectedOption(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueOption option,
            boolean forceEndConversation,
            boolean leaveRequest) {
        if (leaveRequest && tryAdvanceDynamicForcedDialogueGroup(player, villager, session)) {
            return true;
        }

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
            session = withStolenItemsResolved(session);
            FORCED_SESSIONS.put(player.getUUID(), session);
        }
        if (isRestitutionEntryOption(option)) {
            openRestitutionOptions(player, villager, session, option);
            return true;
        }
        if (isRestitutionHaggleOption(option)) {
            handleRestitutionHaggle(player, villager, session, option);
            return true;
        }
        ForcedDialogueItemPayment itemPayment = option.itemPayment();
        if (!itemPayment.isEmpty() && !executeItemPayment(player, villager, session, itemPayment)) {
            handleFailedItemPayment(player, villager, session, option, itemPayment, forceEndConversation);
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
            if (result.queued()) {
                String response = ForcedDialogueResources.resolveTemplate(
                        option.selectResponse(player.serverLevel().getRandom()),
                        tradeRefreshContext(player.serverLevel(), villager, player, result.replacements()),
                        result.replacements());
                if (!response.isBlank()) {
                    VillagerInteractionService.broadcastForcedVillagerChat(
                            player.serverLevel(),
                            villager,
                            response,
                            VillagerInteractionService.villagerSpeakerLabel(villager)
                    );
                }
                FORCED_SESSIONS.remove(player.getUUID());
                VillagerConversationService.endForPlayer(player, true);
                openTradeRefreshDialogue(player.serverLevel(), villager, player, result.messageKey(), result.replacements());
            } else {
                updateTradeRefreshFailureSession(player, villager, session, result.messageKey(), result.replacements());
            }
            return true;
        }
        if (isTradeRefreshSurpriseOption(session, option)) {
            Optional<VillagerSpecialOrderService.QueueResult> limitResult =
                    VillagerTradeRefreshService.activeRequestLimitReached(villager, player.getUUID());
            if (limitResult.isPresent()) {
                VillagerTradeRefreshService.sendState(player, villager);
                updateTradeRefreshFailureSession(player, villager, session, limitResult.get().messageKey(), limitResult.get().replacements());
                return true;
            }
        }
        if (isTradeRefreshSpecialOrderOption(session, option)) {
            Optional<VillagerSpecialOrderService.QueueResult> blocker =
                    specialOrderSelectionBlocker(player.serverLevel(), villager, player);
            if (blocker.isPresent()) {
                VillagerTradeRefreshService.sendState(player, villager);
                updateTradeRefreshFailureSession(player, villager, session, blocker.get().messageKey(), blocker.get().replacements());
                return true;
            }
        }
        boolean aggro = option.aggro() || rollChance(player.serverLevel(), option.aggroChance());
        Optional<Integer> selectedSpecialOrderStatus = VillagerSpecialOrderService.selectedStatusOfferIndex(option.id());
        Optional<ResourceLocation> selectedSpecialOrder = VillagerSpecialOrderService.selectedDefinitionId(option.id());
        boolean opensFollowUpTradeRefreshDialogue = isTradeRefreshSurpriseOption(session, option)
                || isTradeRefreshSpecialOrderOption(session, option)
                || selectedSpecialOrderStatus.isPresent()
                || (selectedSpecialOrder.isPresent() && isTradeRefreshDefinition(session));
        if (!opensFollowUpTradeRefreshDialogue
                && !leaveRequest
                && !aggro
                && tryAdvanceDynamicForcedDialogueGroup(player, villager, session)) {
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
        String response = ForcedDialogueResources.resolveTemplate(
                responseText,
                session.context(),
                sessionResponseReplacements(session, itemPayment.removal().replacements()));
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
        if (selectedSpecialOrderStatus.isPresent()) {
            openSpecialOrderStatusResponseDialogue(player, villager, selectedSpecialOrderStatus.get());
            return true;
        }
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
        if (!option.followUp().isEmpty() && !aggro) {
            openFollowUp(player, villager, session, option);
            return true;
        }
        if (option.endConversation() || forceEndConversation) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
        } else {
            VillagerInteractionService.sendForcedDialogueReputation(
                    player,
                    villager,
                    forcedOptions(session.definition(), player.serverLevel(), villager, player, session),
                    session.definition().forceCameraTowardsVillager());
        }
        if (aggro) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            forceAngerParticipants(player, villager, session);
        }
        return true;
    }

    private static boolean isRestitutionEntryOption(ForcedDialogueOption option) {
        return !option.itemPayment().isEmpty()
                && !isRestitutionPaymentOption(option)
                && (option.id().toLowerCase(Locale.ROOT).contains("restitution")
                || option.label().toLowerCase(Locale.ROOT).contains("restitution"));
    }

    private static boolean isRestitutionPaymentOption(ForcedDialogueOption option) {
        return option.id().endsWith(RESTITUTION_PAY_SUFFIX);
    }

    private static boolean isRestitutionHaggleOption(ForcedDialogueOption option) {
        return option.id().endsWith(RESTITUTION_HAGGLE_SUFFIX);
    }

    private static void openRestitutionOptions(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueOption sourceOption) {
        String line = ForcedDialogueResources.resolveTemplate(
                sourceOption.selectResponse(player.serverLevel().getRandom()),
                session.context(),
                sourceOption.itemPayment().removal().replacements());
        if (line.isBlank()) {
            line = forcedMessage(
                    player,
                    villager,
                    RESTITUTION_PROMPT_MESSAGE_KEY,
                    sourceOption.itemPayment().removal().replacements());
        }
        openGeneratedOptionSet(
                player,
                villager,
                session,
                restitutionDefinition(player, villager, session, sourceOption, line, sourceOption.itemPayment(), false));
    }

    private static void handleRestitutionHaggle(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueOption haggleOption) {
        if (rollChance(player.serverLevel(), restitutionHaggleChance(player.serverLevel(), villager, player))) {
            ForcedDialogueItemPayment reducedPayment = reducedPayment(player, villager, haggleOption.itemPayment());
            String line = forcedMessage(
                    player,
                    villager,
                    RESTITUTION_HAGGLE_ACCEPT_MESSAGE_KEY,
                    reducedPayment.removal().replacements());
            openGeneratedOptionSet(
                    player,
                    villager,
                    session,
                    restitutionDefinition(player, villager, session, haggleOption, line, reducedPayment, true));
            return;
        }

        ForcedDialogueSession updated = withDisabledOption(session, haggleOption.id());
        FORCED_SESSIONS.put(player.getUUID(), updated);
        String response = forcedMessage(
                player,
                villager,
                RESTITUTION_HAGGLE_DENY_MESSAGE_KEY,
                haggleOption.itemPayment().removal().replacements());
        VillagerInteractionService.broadcastForcedVillagerChat(
                player.serverLevel(),
                villager,
                response,
                VillagerInteractionService.villagerSpeakerLabel(villager)
        );
        VillagerInteractionService.sendForcedDialogueReputation(
                player,
                villager,
                forcedOptions(updated.definition(), player.serverLevel(), villager, player, updated),
                updated.definition().forceCameraTowardsVillager());
    }

    private static ForcedDialogueDefinition restitutionDefinition(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueOption sourceOption,
            String line,
            ForcedDialogueItemPayment payment,
            boolean reduced) {
        String baseId = sourceOption.id();
        if (baseId.endsWith(RESTITUTION_HAGGLE_SUFFIX)) {
            baseId = baseId.substring(0, baseId.length() - RESTITUTION_HAGGLE_SUFFIX.length());
        }
        if (baseId.endsWith(RESTITUTION_PAY_SUFFIX)) {
            baseId = baseId.substring(0, baseId.length() - RESTITUTION_PAY_SUFFIX.length());
        }

        List<ForcedDialogueOption> options = new ArrayList<>();
        options.add(dynamicOption(
                baseId + RESTITUTION_PAY_SUFFIX,
                forcedMessage(player, villager, RESTITUTION_PAY_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                payment.successResponses().isEmpty()
                        ? forcedMessageList(player, villager, RESTITUTION_PAY_SUCCESS_MESSAGE_KEY, payment.removal().replacements())
                        : payment.successResponses(),
                sourceOption.reputationDelta(),
                false,
                0.0D,
                true,
                0,
                ForcedDialogueStolenItemReturn.empty(),
                paymentWithFailureMenu(player, villager, payment),
                sourceOption.reputationCondition()));
        if (!reduced) {
            options.add(dynamicOption(
                    baseId + RESTITUTION_HAGGLE_SUFFIX,
                    forcedMessage(player, villager, RESTITUTION_HAGGLE_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                    List.of(),
                    0,
                    false,
                    0.0D,
                    false,
                    1,
                    ForcedDialogueStolenItemReturn.empty(),
                    payment,
                    sourceOption.reputationCondition()));
        }
        options.add(dynamicOption(
                baseId + RESTITUTION_REFUSE_SUFFIX,
                forcedMessage(player, villager, RESTITUTION_REFUSE_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                forcedMessageList(player, villager, RESTITUTION_REFUSE_RESPONSE_MESSAGE_KEY, payment.removal().replacements()),
                -5,
                false,
                0.0D,
                true,
                2,
                ForcedDialogueStolenItemReturn.empty(),
                ForcedDialogueItemPayment.empty(),
                sourceOption.reputationCondition()));
        options.add(dynamicOption(
                baseId + RESTITUTION_THREATEN_SUFFIX,
                forcedMessage(player, villager, RESTITUTION_THREATEN_LABEL_MESSAGE_KEY, payment.removal().replacements()),
                forcedMessageList(player, villager, RESTITUTION_THREATEN_RESPONSE_MESSAGE_KEY, payment.removal().replacements()),
                -10,
                true,
                0.0D,
                true,
                3,
                ForcedDialogueStolenItemReturn.empty(),
                ForcedDialogueItemPayment.empty(),
                sourceOption.reputationCondition()));

        ForcedDialogueOption leaveOption = session.definition().leaveOption();
        options.add(leaveOption);
        return definitionWithOptions(session.definition().id() + RESTITUTION_DEFINITION_SUFFIX, session.definition(), line, options, leaveOption);
    }

    private static ForcedDialogueOption dynamicOption(
            String id,
            String label,
            List<String> responses,
            int reputationDelta,
            boolean aggro,
            double aggroChance,
            boolean endConversation,
            int order,
            ForcedDialogueStolenItemReturn stolenItemReturn,
            ForcedDialogueItemPayment itemPayment,
            VillagerReputationCondition reputationCondition) {
        return new ForcedDialogueOption(
                id,
                label,
                responses,
                reputationDelta,
                aggro,
                aggroChance,
                endConversation,
                order,
                stolenItemReturn,
                itemPayment,
                reputationCondition,
                SocialAttributeCondition.EMPTY,
                List.of(),
                ForcedDialogueFollowUp.empty());
    }

    private static List<String> forcedMessageList(
            ServerPlayer player,
            Villager villager,
            String key,
            Map<String, String> replacements) {
        return List.of(forcedMessage(player, villager, key, replacements));
    }

    private static String forcedMessage(
            ServerPlayer player,
            Villager villager,
            String key,
            Map<String, String> replacements) {
        DialogueContext context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
        return VillagerDialogueResources.message(context, key, replacements).orElse(key);
    }

    private static ForcedDialogueItemPayment paymentWithFailureMenu(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueItemPayment payment) {
        return new ForcedDialogueItemPayment(
                payment.removal(),
                payment.successResponses(),
                payment.failureResponses().isEmpty()
                        ? forcedMessageList(player, villager, RESTITUTION_PAY_FAILURE_MESSAGE_KEY, payment.removal().replacements())
                        : payment.failureResponses(),
                payment.successReputationDelta(),
                payment.failureReputationDelta(),
                false,
                false,
                payment.destination(),
                payment.overflowDestination(),
                payment.requireSpace());
    }

    private static ForcedDialogueItemPayment reducedPayment(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueItemPayment payment) {
        int reducedCount = Math.max(1, (int) Math.ceil(payment.removal().count() * 0.6D));
        VillagerInventoryItemRemoval removal =
                new VillagerInventoryItemRemoval(payment.removal().selectors(), reducedCount);
        return new ForcedDialogueItemPayment(
                removal,
                forcedMessageList(player, villager, RESTITUTION_REDUCED_PAY_SUCCESS_MESSAGE_KEY, removal.replacements()),
                payment.failureResponses(),
                payment.successReputationDelta(),
                payment.failureReputationDelta(),
                false,
                false,
                payment.destination(),
                payment.overflowDestination(),
                payment.requireSpace());
    }

    private static double restitutionHaggleChance(ServerLevel level, Villager villager, ServerPlayer player) {
        ReputationSnapshot reputation = VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        int baseChance = switch (reputation.level()) {
            case ROYALTY -> 90;
            case REVERED -> 78;
            case RESPECTED -> 62;
            case TRUSTED -> 45;
            case NEUTRAL -> 24;
            case SUSPICIOUS -> 16;
            case HOSTILE -> 8;
            case DESPISED, FEARED -> 3;
        };
        if (VillagerSocialAttributeBehavior.enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS)) {
            int charm = VillagerSocialAttributeBehavior.value(level, villager, VillagerSocialAttribute.CHARM);
            int kindness = VillagerSocialAttributeBehavior.value(level, villager, VillagerSocialAttribute.KINDNESS);
            baseChance += (charm - 50) / 3;
            baseChance += (kindness - 50) / 5;
        }
        return Math.clamp(baseChance, 3, 95) / 100.0D;
    }

    private static void openFollowUp(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueOption option) {
        ForcedDialogueFollowUp followUp = option.followUp();
        String line = ForcedDialogueResources.resolveTemplate(
                followUp.selectLine(player.serverLevel().getRandom()),
                session.context(),
                option.itemPayment().removal().replacements());
        if (line.isBlank()) {
            line = ForcedDialogueResources.resolveTemplate(
                    option.selectResponse(player.serverLevel().getRandom()),
                    session.context(),
                    option.itemPayment().removal().replacements());
        }
        openGeneratedOptionSet(
                player,
                villager,
                session,
                definitionWithOptions(
                        session.definition().id() + "." + option.id() + ".follow_up",
                        session.definition(),
                        line,
                        followUp.options(),
                        followUp.leaveOption()));
    }

    private static void openGeneratedOptionSet(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueDefinition definition) {
        if (!definition.lines().isEmpty() && !definition.lines().get(0).isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    player.serverLevel(),
                    villager,
                    definition.lines().get(0),
                    VillagerInteractionService.villagerSpeakerLabel(villager)
            );
        }
        ForcedDialogueSession updated = withDefinition(session, definition);
        FORCED_SESSIONS.put(player.getUUID(), updated);
        VillagerInteractionService.sendForcedDialogueReputation(
                player,
                villager,
                forcedOptions(definition, player.serverLevel(), villager, player, updated),
                definition.forceCameraTowardsVillager());
    }

    private static ForcedDialogueDefinition definitionWithOptions(
            String id,
            ForcedDialogueDefinition source,
            String line,
            List<ForcedDialogueOption> options,
            ForcedDialogueOption leaveOption) {
        return new ForcedDialogueDefinition(
                id,
                source.source(),
                source.trigger(),
                source.output(),
                line.isBlank() ? List.of() : List.of(line),
                true,
                false,
                source.forceCameraTowardsVillager(),
                source.requiresLineOfSight(),
                source.witnessRadius(),
                source.chance(),
                0,
                source.priority(),
                source.minRecentContainerThefts(),
                source.maxRecentContainerThefts(),
                source.minRecentRetaliations(),
                source.maxRecentRetaliations(),
                source.lootTables(),
                source.targetEntityTypes(),
                source.witnessProfessions(),
                source.witnessEquipmentCondition(),
                source.playerItemCondition(),
                source.reputationCondition(),
                List.copyOf(options),
                leaveOption,
                List.of(leaveOption));
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

    private static boolean optionMatches(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueOption option) {
        ReputationSnapshot reputation = VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        if (!option.reputationCondition().matches(reputation.value(), reputation.level())) {
            return false;
        }
        return optionConditionsMatch(level, villager, player, option);
    }

    private static boolean optionConditionsMatch(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueResources.ForcedDialogueOption option) {
        if (option.socialAttributeCondition().isEmpty() && option.conditions().isEmpty()) {
            return true;
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        if (!option.socialAttributeCondition().isEmpty()
                && (!VillagerSocialAttributeBehavior.enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS)
                || !option.socialAttributeCondition().matches(context))) {
            return false;
        }
        return option.conditions().stream().allMatch(condition -> condition.matches(context));
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
                optionDefinition.source(),
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

    private static ForcedDialogueDefinition tradeRefreshRequirementOptions(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition optionDefinition,
            String messageKey,
            Map<String, String> replacements) {
        Optional<String> requirementMessageKey = tradeRefreshRequirementMessageKey(messageKey);
        if (requirementMessageKey.isEmpty()) {
            return optionDefinition;
        }
        String response = tradeRefreshLine(level, villager, player, requirementMessageKey.get(), replacements);
        if (response.isBlank() || response.equals(requirementMessageKey.get())) {
            return optionDefinition;
        }
        List<ForcedDialogueOption> options = optionDefinition.options()
                .stream()
                .map(option -> withRequirementResponse(option, response))
                .toList();
        return tradeRefreshDefinition(
                optionDefinition.selectLine(level.getRandom()),
                optionDefinition,
                options,
                optionDefinition.leaveOption(),
                optionDefinition.leaveOptions());
    }

    private static ForcedDialogueOption withRequirementResponse(ForcedDialogueOption option, String response) {
        if (!TRADE_REFRESH_REQUIREMENTS_OPTION_ID.equals(option.id())) {
            return option;
        }
        return new ForcedDialogueOption(
                option.id(),
                option.label(),
                List.of(response),
                option.reputationDelta(),
                option.aggro(),
                option.aggroChance(),
                option.endConversation(),
                option.order(),
                option.stolenItemReturn(),
                option.itemPayment(),
                option.reputationCondition(),
                option.socialAttributeCondition(),
                option.conditions(),
                option.followUp());
    }

    private static Optional<VillagerSpecialOrderService.QueueResult> specialOrderSelectionBlocker(
            ServerLevel level,
            Villager villager,
            ServerPlayer player) {
        if (VillagerSpecialOrderService.hasReachedActiveOrderLimit(villager, player.getUUID())) {
            return Optional.of(VillagerSpecialOrderService.activeOrderLimitReached(villager, player.getUUID()));
        }
        return VillagerSpecialOrderService.activeCooldown(level, villager, player.getUUID());
    }

    private static void openSpecialOrderSelectionDialogue(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session) {
        ServerLevel level = player.serverLevel();
        Optional<VillagerSpecialOrderService.QueueResult> blocker =
                specialOrderSelectionBlocker(level, villager, player);
        if (blocker.isPresent()) {
            VillagerTradeRefreshService.sendState(player, villager);
            updateTradeRefreshFailureSession(player, villager, session, blocker.get().messageKey(), blocker.get().replacements());
            return;
        }

        List<VillagerSpecialOrderService.SpecialOrderOption> specialOrders =
                VillagerSpecialOrderService.availableOptions(level, villager, player, session.tradeRefreshOfferIndex());
        if (specialOrders.isEmpty()) {
            updateTradeRefreshFailureSession(player, villager, session, "trade_refresh.special_order_unavailable", Map.of());
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
        Optional<VillagerSpecialOrderService.QueueResult> blocker =
                specialOrderSelectionBlocker(level, villager, player);
        if (blocker.isPresent()) {
            VillagerTradeRefreshService.sendState(player, villager);
            updateTradeRefreshFailureSession(player, villager, session, blocker.get().messageKey(), blocker.get().replacements());
            return;
        }

        VillagerSpecialOrderService.SpecialOrderOption specialOrder = VillagerSpecialOrderService
                .availableOptions(level, villager, player, session.tradeRefreshOfferIndex())
                .stream()
                .filter(candidate -> candidate.definition().id().equals(definitionId))
                .findFirst()
                .orElse(null);
        if (specialOrder == null) {
            updateTradeRefreshFailureSession(player, villager, session, "trade_refresh.special_order_unavailable", Map.of());
            return;
        }

        Map<String, String> replacements = Map.of(
                "trade_item", specialOrder.tradeItem(),
                "trade_definition", definitionId.toString(),
                "wait_days", Integer.toString(specialOrder.waitDays()),
                "wait_day_word", VillagerSpecialOrderService.pluralWord(specialOrder.waitDays(), "day", "days"),
                "cooldown_days", Integer.toString(specialOrder.cooldownDays()),
                "cooldown_day_word", VillagerSpecialOrderService.pluralWord(specialOrder.cooldownDays(), "day", "days"));
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

    private static void openSpecialOrderStatusResponseDialogue(
            ServerPlayer player,
            Villager villager,
            int offerIndex) {
        ServerLevel level = player.serverLevel();
        Optional<VillagerSpecialOrderService.ActiveOrderStatus> selectedStatus =
                VillagerSpecialOrderService.activeOrderStatus(level, villager, player.getUUID(), offerIndex);
        if (selectedStatus.isEmpty()) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true);
            openTradeRefreshDialogue(level, villager, player, "trade_refresh.special_order_status_empty", Map.of());
            return;
        }

        List<VillagerSpecialOrderService.ActiveOrderStatus> activeOrders =
                VillagerSpecialOrderService.activeOrderStatuses(level, villager, player.getUUID());
        Optional<ForcedDialogueDefinition> optionDefinition = tradeRefreshOptionDefinitionById(
                level,
                villager,
                player,
                TRADE_REFRESH_SPECIAL_ORDER_STATUS_OPTIONS_ID);
        if (optionDefinition.isEmpty()) {
            openTradeRefreshDialogue(level, villager, player, "trade_refresh.special_order_status", selectedStatus.get().replacements());
            return;
        }
        ForcedDialogueDefinition definition = specialOrderStatusDefinition(
                level,
                villager,
                player,
                "trade_refresh.special_order_status",
                selectedStatus.get().replacements(),
                optionDefinition.get(),
                activeOrders);
        ForcedDialogueContext context = tradeRefreshContext(level, villager, player, selectedStatus.get().replacements());
        updateTradeRefreshSession(player, villager, definition, context, -1, "");
    }

    private static ForcedDialogueDefinition specialOrderStatusDefinition(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String messageKey,
            Map<String, String> replacements,
            ForcedDialogueDefinition optionDefinition,
            List<VillagerSpecialOrderService.ActiveOrderStatus> activeOrders) {
        List<ForcedDialogueOption> options = new ArrayList<>();
        int order = 0;
        for (VillagerSpecialOrderService.ActiveOrderStatus activeOrder : activeOrders) {
            options.add(dynamicTradeRefreshOption(
                    VillagerSpecialOrderService.statusOptionId(activeOrder.offerIndex()),
                    activeOrder.label(),
                    order++));
        }
        options.addAll(optionDefinition.options());
        String line = tradeRefreshLine(level, villager, player, messageKey, replacements);
        return tradeRefreshDefinition(
                line,
                optionDefinition,
                List.copyOf(options),
                optionDefinition.leaveOption(),
                optionDefinition.leaveOptions());
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
                VillagerReputationCondition.empty(),
                SocialAttributeCondition.EMPTY,
                List.of(),
                ForcedDialogueFollowUp.empty());
    }

    private static void updateTradeRefreshFailureSession(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            String messageKey,
            Map<String, String> replacements) {
        ServerLevel level = player.serverLevel();
        String line = tradeRefreshLine(level, villager, player, messageKey, replacements);
        Optional<ForcedDialogueDefinition> optionDefinition = tradeRefreshOptionDefinition(level, villager, player, messageKey);
        if (optionDefinition.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, line);
            return;
        }
        ForcedDialogueDefinition options = tradeRefreshRequirementOptions(
                level,
                villager,
                player,
                optionDefinition.get(),
                messageKey,
                replacements);
        ForcedDialogueDefinition definition = tradeRefreshDefinition(line, options);
        ForcedDialogueContext context = tradeRefreshContext(level, villager, player, replacements);
        updateTradeRefreshSession(
                player,
                villager,
                definition,
                context,
                session.tradeRefreshOfferIndex(),
                session.tradeRefreshDefinitionId());
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
                || "trade_refresh.special_order_limit_reached".equals(messageKey)
                || "trade_refresh.request_limit_reached".equals(messageKey)
                || "trade_refresh.special_order_cooldown".equals(messageKey)
                || "trade_refresh.special_order_payment_missing".equals(messageKey)
                || "trade_refresh.special_order_status_empty".equals(messageKey);
    }

    private static Optional<String> tradeRefreshRequirementMessageKey(String messageKey) {
        return switch (messageKey) {
            case "trade_refresh.not_ready" -> Optional.of("trade_refresh.requirements.not_ready");
            case "trade_refresh.unavailable" -> Optional.of("trade_refresh.requirements.unavailable");
            case "trade_refresh.special_order_unavailable" -> Optional.of("trade_refresh.requirements.special_order_unavailable");
            case "trade_refresh.special_order_pending" -> Optional.of("trade_refresh.requirements.special_order_pending");
            case "trade_refresh.special_order_limit_reached" -> Optional.of("trade_refresh.requirements.special_order_limit_reached");
            case "trade_refresh.request_limit_reached" -> Optional.of("trade_refresh.requirements.request_limit_reached");
            case "trade_refresh.special_order_cooldown" -> Optional.of("trade_refresh.requirements.special_order_cooldown");
            case "trade_refresh.special_order_payment_missing" -> Optional.of("trade_refresh.requirements.special_order_payment_missing");
            case "trade_refresh.special_order_status_empty" -> Optional.of("trade_refresh.requirements.special_order_status_empty");
            default -> Optional.empty();
        };
    }

    private static Map<String, String> sessionResponseReplacements(
            ForcedDialogueSession session,
            Map<String, String> extraReplacements) {
        if (session.replacements().isEmpty()) {
            return extraReplacements;
        }
        if (extraReplacements.isEmpty()) {
            return session.replacements();
        }
        Map<String, String> replacements = new HashMap<>(session.replacements());
        replacements.putAll(extraReplacements);
        return replacements;
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
                    forcedOptions(session.definition(), player.serverLevel(), villager, player, session),
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
            ForcedDialogueOption option,
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
            session = withDisabledOption(session, option.id());
            FORCED_SESSIONS.put(player.getUUID(), session);
            VillagerInteractionService.sendForcedDialogueReputation(
                    player,
                    villager,
                    forcedOptions(session.definition(), player.serverLevel(), villager, player, session),
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
        return ForcedDialogueItemTransfers.executeItemPayment(player, villager, transferSource(session), itemPayment);
    }

    private static Optional<List<ItemStack>> executeStolenItemReturn(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueStolenItemReturn stolenItemReturn) {
        return ForcedDialogueItemTransfers.executeStolenItemReturn(
                player,
                villager,
                transferSource(session),
                session.removedStacks(),
                stolenItemReturn);
    }

    private static ForcedDialogueItemTransfers.SourceContainer transferSource(ForcedDialogueSession session) {
        return new ForcedDialogueItemTransfers.SourceContainer(session.sourceContainerDimension(), session.sourceContainerPos());
    }

    public static void endForPlayer(ServerPlayer player) {
        FORCED_SESSIONS.remove(player.getUUID());
    }

    public static void maybeTriggerTradeRefreshReadyProximity(ServerLevel level, Villager villager) {
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || !VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                || !villager.isAlive()
                || villager.isBaby()
                || villager.isTrading()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % TRADE_REFRESH_READY_SCAN_INTERVAL_TICKS
                != Math.floorMod(villager.getUUID().getLeastSignificantBits(), TRADE_REFRESH_READY_SCAN_INTERVAL_TICKS)) {
            return;
        }

        double radius = VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get();
        AABB area = villager.getBoundingBox().inflate(radius);
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (player.isAlive()
                    && !player.isSpectator()
                    && !FORCED_SESSIONS.containsKey(player.getUUID())
                    && !VillagerConversationService.isConversing(player)
                    && VillagerInteractionService.canUseForcedInteractionSystem(player, villager)
                    && VillagerTradeRefreshService.hasReadyRefreshesForPlayer(level, villager, player)) {
                players.add(player);
            }
        }
        if (players.size() > 1) {
            players.sort(Comparator.comparingDouble(player -> villager.distanceToSqr(player)));
        }
        for (ServerPlayer player : players) {
            if (tryOpenTradeRefreshReadyDialogue(level, villager, player)) {
                return;
            }
        }
    }

    public static void tickSharedForcedDialogueParticipant(ServerLevel level, Villager villager) {
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || !villager.isAlive()
                || villager.isBaby()
                || villager.isTrading()) {
            return;
        }

        UUID villagerId = villager.getUUID();
        for (Map.Entry<UUID, ForcedDialogueSession> entry : List.copyOf(FORCED_SESSIONS.entrySet())) {
            if (!(level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer player)
                    || !player.isAlive()
                    || player.isSpectator()
                    || !VillagerConversationService.isConversing(player)) {
                continue;
            }

            ForcedDialogueSession session = FORCED_SESSIONS.get(entry.getKey());
            if (session == null) {
                continue;
            }
            if (session.villagerId().equals(villagerId)) {
                continue;
            }

            if (session.participantVillagerIds().contains(villagerId)) {
                VillagerConversationService.holdSharedForcedParticipant(villager, player);
                continue;
            }

            if (isEligibleSharedForcedDialogueContinuation(level, player, villager, session)) {
                ForcedDialogueSession updated = withParticipant(session, villager);
                FORCED_SESSIONS.put(player.getUUID(), updated);
                VillagerConversationService.holdSharedForcedParticipant(villager, player);
            }
        }
    }

    private static boolean tryAdvanceDynamicForcedDialogueGroup(
            ServerPlayer player,
            Villager currentVillager,
            ForcedDialogueSession session) {
        if (session.tradeRefreshReady()) {
            return tryAdvanceTradeRefreshReadyGroup(
                    player,
                    currentVillager,
                    session,
                    TRADE_REFRESH_READY_INTERJECTION_MESSAGE_KEY);
        }
        if (isSharedContainerDialogueTrigger(session.definition().trigger())) {
            return tryAdvanceTradeRefreshReadyGroup(
                    player,
                    currentVillager,
                    session,
                    TRADE_REFRESH_READY_THEFT_INTERJECTION_MESSAGE_KEY)
                    || tryAdvanceContainerWitnessInterjection(player, currentVillager, session);
        }
        return false;
    }

    private static boolean tryAdvanceTradeRefreshReadyGroup(
            ServerPlayer player,
            Villager currentVillager,
            ForcedDialogueSession session,
            String messageKey) {
        ServerLevel level = player.serverLevel();
        for (Villager nextVillager : nearbyReadyTradeRefreshVillagers(level, currentVillager, player)) {
            VillagerTradeRefreshService.ReadyRefreshResult readyRefreshes =
                    VillagerTradeRefreshService.applyReadyRefreshesDetailed(level, nextVillager, player);
            VillagerTradeRefreshService.sendState(player, nextVillager);
            if (!readyRefreshes.hasPlayerReadyTrades()) {
                continue;
            }

            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, false);
            int speakerIndex = nextInterjectionSpeakerIndex(session);
            Map<String, String> replacements = tradeRefreshInterjectionReplacements(
                    currentVillager,
                    nextVillager,
                    player,
                    speakerIndex);
            openTradeRefreshReadyDialogue(
                    level,
                    nextVillager,
                    player,
                    readyRefreshes,
                    interjectionMessageKey(messageKey, speakerIndex),
                    replacements,
                    appendedParticipantIds(session, nextVillager),
                    appendedSpokenVillagerIds(session, nextVillager));
            return true;
        }
        return false;
    }

    private static boolean isEligibleSharedForcedDialogueContinuation(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session) {
        if (VillagerConversationService.isConversing(villager)
                || !VillagerInteractionService.canUseForcedInteractionSystem(player, villager)) {
            return false;
        }
        if (VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                && VillagerTradeRefreshService.hasReadyRefreshesForPlayer(level, villager, player)) {
            return true;
        }
        return isSharedContainerDialogueTrigger(session.definition().trigger())
                && matchingAdditionalContainerWitnessDefinition(level, player, session, villager).isPresent();
    }

    private static boolean isSharedContainerDialogueTrigger(ForcedDialogueTrigger trigger) {
        return trigger == ForcedDialogueTrigger.CONTAINER_THEFT
                || trigger == ForcedDialogueTrigger.CONTAINER_OPENED;
    }

    private static int nextInterjectionSpeakerIndex(ForcedDialogueSession session) {
        return session.spokenVillagerIds().size() + 1;
    }

    private static String interjectionMessageKey(String baseKey, int speakerIndex) {
        if (speakerIndex == 2) {
            return baseKey + ".second";
        }
        if (speakerIndex == 3) {
            return baseKey + ".third";
        }
        return baseKey;
    }

    private static String baseInterjectionMessageKey(String messageKey) {
        if (messageKey.endsWith(".second")) {
            return messageKey.substring(0, messageKey.length() - ".second".length());
        }
        if (messageKey.endsWith(".third")) {
            return messageKey.substring(0, messageKey.length() - ".third".length());
        }
        return messageKey;
    }

    private static String interjectionOrdinal(int speakerIndex) {
        return switch (speakerIndex) {
            case 1 -> "first";
            case 2 -> "second";
            case 3 -> "third";
            case 4 -> "fourth";
            case 5 -> "fifth";
            default -> Integer.toString(speakerIndex);
        };
    }

    private static List<ForcedDialogueOption> containerWitnessInterjectionOptions(ForcedDialogueDefinition definition) {
        if (!definition.options().isEmpty()) {
            return definition.options();
        }
        return List.of(definition.leaveOption());
    }

    private static List<Villager> nearbyReadyTradeRefreshVillagers(
            ServerLevel level,
            Villager currentVillager,
            ServerPlayer player) {
        double radius = VillagerRetaliationConfig.MAX_FORCED_DIALOGUE_DISTANCE.get();
        AABB area = player.getBoundingBox().inflate(radius);
        List<Villager> villagers = new ArrayList<>();
        for (Villager candidate : level.getEntitiesOfClass(Villager.class, area)) {
            if (candidate.getUUID().equals(currentVillager.getUUID())
                    || VillagerConversationService.isConversing(candidate)
                    || !VillagerInteractionService.canUseForcedInteractionSystem(player, candidate)
                    || !VillagerTradeRefreshService.hasReadyRefreshesForPlayer(level, candidate, player)) {
                continue;
            }
            villagers.add(candidate);
        }
        villagers.sort(Comparator
                .comparingDouble((Villager candidate) -> candidate.distanceToSqr(player))
                .thenComparing(candidate -> VillagerPresetNameRegistry.resolveDisplayName(candidate).getString()));
        return List.copyOf(villagers);
    }

    private static Map<String, String> tradeRefreshInterjectionReplacements(
            Villager interruptedVillager,
            Villager nextVillager,
            ServerPlayer player,
            int speakerIndex) {
        return Map.of(
                "interrupted_villager", VillagerPresetNameRegistry.resolveDisplayName(interruptedVillager).getString(),
                "previous_villager", VillagerPresetNameRegistry.resolveDisplayName(interruptedVillager).getString(),
                "current_villager", VillagerPresetNameRegistry.resolveDisplayName(nextVillager).getString(),
                "player", player.getDisplayName().getString(),
                "interjection_index", Integer.toString(speakerIndex),
                "interjection_ordinal", interjectionOrdinal(speakerIndex),
                "speaker_index", Integer.toString(speakerIndex),
                "speaker_ordinal", interjectionOrdinal(speakerIndex));
    }

    private static boolean tryAdvanceContainerWitnessInterjection(
            ServerPlayer player,
            Villager currentVillager,
            ForcedDialogueSession session) {
        ServerLevel level = player.serverLevel();
        Optional<ContainerWitnessCandidate> nextWitness = findAdditionalContainerWitness(level, player, session);
        if (nextWitness.isEmpty()) {
            return false;
        }

        ContainerWitnessCandidate candidate = nextWitness.get();
        Villager witness = candidate.villager();
        ForcedDialogueDefinition witnessDefinition = candidate.definition();
        ForcedDialogueContext context = containerWitnessContext(level, witness, player, session);
        applyContainerWitnessConsequences(level, witness, player, session, witnessDefinition);

        String line = containerWitnessInterjectionLine(
                level,
                witness,
                player,
                currentVillager,
                witnessDefinition,
                context,
                nextInterjectionSpeakerIndex(session));
        ForcedDialogueDefinition definition = forcedInterjectionDefinition(
                containerInterjectionDefinitionId(witnessDefinition.trigger()),
                line,
                witnessDefinition,
                containerWitnessInterjectionOptions(witnessDefinition));

        FORCED_SESSIONS.remove(player.getUUID());
        VillagerConversationService.endForPlayer(player, false);
        if (VillagerInteractionService.openForcedDialogue(
                player,
                witness,
                line,
                forcedOptions(definition, level, witness, player, session),
                definition.forceCameraTowardsVillager())) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    witness.getUUID(),
                    definition,
                    context,
                    session.sourceContainerDimension(),
                    session.sourceContainerPos(),
                    session.removedStacks(),
                    level.getGameTime(),
                    -1,
                    "",
                    false,
                    Map.of(),
                    appendedParticipantIds(session, witness),
                    appendedSpokenVillagerIds(session, witness),
                    session.stolenItemsResolved(),
                    session.disabledOptionIds()));
        } else if (!line.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, witness, line);
        }
        return true;
    }

    private static Optional<ContainerWitnessCandidate> findAdditionalContainerWitness(
            ServerLevel level,
            ServerPlayer player,
            ForcedDialogueSession session) {
        if (session.sourceContainerDimension() != level.dimension()) {
            return Optional.empty();
        }
        List<ForcedDialogueDefinition> definitions = sharedContainerWitnessDefinitions(level, session);
        if (definitions.isEmpty()) {
            return Optional.empty();
        }

        BlockPos pos = session.sourceContainerPos();
        double radius = definitions.stream()
                .mapToDouble(ForcedDialogueDefinition::witnessRadius)
                .max()
                .orElse(session.definition().witnessRadius());
        AABB area = AABB.ofSize(Vec3.atCenterOf(pos), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby()).stream()
                .filter(villager -> !session.spokenVillagerIds().contains(villager.getUUID()))
                .map(villager -> matchingAdditionalContainerWitnessDefinition(level, player, session, villager, definitions)
                        .map(definition -> new ContainerWitnessCandidate(villager, definition)))
                .flatMap(Optional::stream)
                .min(Comparator
                        .comparing((ContainerWitnessCandidate candidate) -> !session.participantVillagerIds().contains(candidate.villager().getUUID()))
                        .thenComparingDouble(candidate -> candidate.villager().distanceToSqr(player)));
    }

    private static Optional<ForcedDialogueDefinition> matchingAdditionalContainerWitnessDefinition(
            ServerLevel level,
            ServerPlayer player,
            ForcedDialogueSession session,
            Villager villager) {
        return matchingAdditionalContainerWitnessDefinition(level, player, session, villager, sharedContainerWitnessDefinitions(level, session));
    }

    private static Optional<ForcedDialogueDefinition> matchingAdditionalContainerWitnessDefinition(
            ServerLevel level,
            ServerPlayer player,
            ForcedDialogueSession session,
            Villager villager,
            List<ForcedDialogueDefinition> definitions) {
        return definitions.stream()
                .filter(definition -> isAdditionalContainerWitnessCandidate(level, player, session, villager, definition))
                .findFirst();
    }

    private static List<ForcedDialogueDefinition> sharedContainerWitnessDefinitions(
            ServerLevel level,
            ForcedDialogueSession session) {
        if (!isSharedContainerDialogueTrigger(session.definition().trigger())) {
            return List.of();
        }
        ResourceLocation lootTable = session.context().lootTable().isBlank()
                ? null
                : ResourceLocation.tryParse(session.context().lootTable());
        List<ForcedDialogueDefinition> definitions = ForcedDialogueResources
                .selectCandidates(level.getServer(), session.definition().trigger(), lootTable)
                .stream()
                .filter(definition -> !isChatOutput(definition))
                .filter(ForcedDialogueDefinition::initiateDialogue)
                .toList();
        return definitions.isEmpty() ? List.of(session.definition()) : definitions;
    }

    private static boolean isAdditionalContainerWitnessCandidate(
            ServerLevel level,
            ServerPlayer player,
            ForcedDialogueSession session,
            Villager villager,
            ForcedDialogueDefinition definition) {
        if (session.sourceContainerDimension() != level.dimension()
                || session.spokenVillagerIds().contains(villager.getUUID())
                || !villager.isAlive()
                || villager.isBaby()
                || VillagerConversationService.isConversing(villager)
                || !VillagerInteractionService.canUseForcedInteractionSystem(player, villager)) {
            return false;
        }
        BlockPos pos = session.sourceContainerPos();
        double radius = definition.witnessRadius();
        double radiusSqr = radius * radius;
        boolean closeEnough = villager.distanceToSqr(player) <= radiusSqr || villager.blockPosition().distSqr(pos) <= radiusSqr;
        return closeEnough
                && definition.matchesWitness(villager)
                && (!definition.requiresLineOfSight() || hasTheftLineOfSight(level, villager, player, pos))
                && definitionMatchesReputation(level, villager, player, definition)
                && definition.matchesRecentContainerThefts(VillageEventMemory.countForPlayer(
                        VillageEventMemory.recentForVillage(level, villager),
                        player.getUUID(),
                        VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT));
    }

    private static void applyContainerWitnessConsequences(
            ServerLevel level,
            Villager witness,
            ServerPlayer player,
            ForcedDialogueSession session,
            ForcedDialogueDefinition definition) {
        if (definition.trigger() == ForcedDialogueTrigger.CONTAINER_THEFT) {
            rememberContainerTheft(level, witness, player, containerSnapshot(session, level), session.removedStacks(), stolenItemCount(session));
        }
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(
                    level,
                    witness,
                    player.getUUID(),
                    definition.reputationDelta(),
                    session.sourceContainerPos());
            VillagerGossipHooks.spreadReputation(level, witness, player.getUUID(), definition.reputationDelta());
        }
    }

    private static String containerWitnessInterjectionLine(
            ServerLevel level,
            Villager witness,
            ServerPlayer player,
            Villager previousWitness,
            ForcedDialogueDefinition definition,
            ForcedDialogueContext context,
            int speakerIndex) {
        String fallback = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        String baseMessageKey = definition.trigger() == ForcedDialogueTrigger.CONTAINER_THEFT
                ? CONTAINER_THEFT_BACKUP_MESSAGE_KEY
                : CONTAINER_OPENED_BACKUP_MESSAGE_KEY;
        Map<String, String> replacements = definition.trigger() == ForcedDialogueTrigger.CONTAINER_THEFT
                ? containerTheftBackupReplacements(previousWitness, witness, player, context, speakerIndex)
                : containerOpenedBackupReplacements(previousWitness, witness, player, context, speakerIndex);
        DialogueContext dialogueContext = VillagerInteractionService.createDialogueContext(level, player, witness);
        String messageKey = interjectionMessageKey(baseMessageKey, speakerIndex);
        return VillagerDialogueResources
                .message(dialogueContext, messageKey, replacements)
                .or(() -> VillagerDialogueResources.message(dialogueContext, baseMessageKey, replacements))
                .orElse(fallback);
    }

    private static String containerInterjectionDefinitionId(ForcedDialogueTrigger trigger) {
        return trigger == ForcedDialogueTrigger.CONTAINER_THEFT
                ? CONTAINER_THEFT_BACKUP_DEFINITION_ID
                : CONTAINER_OPENED_BACKUP_DEFINITION_ID;
    }

    private static ForcedDialogueContext containerWitnessContext(
            ServerLevel level,
            Villager witness,
            ServerPlayer player,
            ForcedDialogueSession session) {
        int priorContainerThefts = VillageEventMemory.countForPlayer(
                VillageEventMemory.recentForVillage(level, witness),
                player.getUUID(),
                VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT);
        ForcedDialogueContext previous = session.context();
        return new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(witness).getString(),
                player.getDisplayName().getString(),
                previous.targetName(),
                previous.targetKind(),
                previous.targetType(),
                previous.itemName(),
                previous.itemId(),
                previous.itemCount(),
                previous.itemStack(),
                previous.itemList(),
                previous.containerName(),
                previous.lootTable(),
                priorContainerThefts,
                previous.priorRetaliations(),
                previous.x(),
                previous.y(),
                previous.z());
    }

    private static Map<String, String> containerTheftBackupReplacements(
            Villager previousWitness,
            Villager currentWitness,
            ServerPlayer player,
            ForcedDialogueContext context,
            int speakerIndex) {
        int priorThefts = Math.max(0, context.priorContainerThefts());
        int witnessedThefts = priorThefts + 1;
        boolean singleItem = context.itemCount() == 1;
        return Map.ofEntries(
                Map.entry("interrupted_villager", VillagerPresetNameRegistry.resolveDisplayName(previousWitness).getString()),
                Map.entry("previous_villager", VillagerPresetNameRegistry.resolveDisplayName(previousWitness).getString()),
                Map.entry("current_villager", VillagerPresetNameRegistry.resolveDisplayName(currentWitness).getString()),
                Map.entry("player", player.getDisplayName().getString()),
                Map.entry("container_theft_again_phrase", containerTheftAgainPhrase(priorThefts)),
                Map.entry("container_theft_time_word", witnessedThefts == 1 ? "time" : "times"),
                Map.entry("prior_container_thefts", Integer.toString(priorThefts)),
                Map.entry("stolen_container", context.containerName()),
                Map.entry("stolen_count", Integer.toString(context.itemCount())),
                Map.entry("stolen_count_word", singleItem ? "item" : "items"),
                Map.entry("stolen_item", context.itemName()),
                Map.entry("stolen_item_reference", singleItem ? "that" : "those"),
                Map.entry("stolen_item_pronoun", singleItem ? "it" : "them"),
                Map.entry("stolen_items", context.itemList()),
                Map.entry("stolen_stack", context.itemStack()),
                Map.entry("stolen_loot_table", context.lootTable()),
                Map.entry("interjection_index", Integer.toString(speakerIndex)),
                Map.entry("interjection_ordinal", interjectionOrdinal(speakerIndex)),
                Map.entry("speaker_index", Integer.toString(speakerIndex)),
                Map.entry("speaker_ordinal", interjectionOrdinal(speakerIndex)),
                Map.entry("witnessed_container_thefts", Integer.toString(witnessedThefts)));
    }

    private static Map<String, String> containerOpenedBackupReplacements(
            Villager previousWitness,
            Villager currentWitness,
            ServerPlayer player,
            ForcedDialogueContext context,
            int speakerIndex) {
        return Map.ofEntries(
                Map.entry("interrupted_villager", VillagerPresetNameRegistry.resolveDisplayName(previousWitness).getString()),
                Map.entry("previous_villager", VillagerPresetNameRegistry.resolveDisplayName(previousWitness).getString()),
                Map.entry("current_villager", VillagerPresetNameRegistry.resolveDisplayName(currentWitness).getString()),
                Map.entry("player", player.getDisplayName().getString()),
                Map.entry("container", context.containerName()),
                Map.entry("container_loot_table", context.lootTable()),
                Map.entry("interjection_index", Integer.toString(speakerIndex)),
                Map.entry("interjection_ordinal", interjectionOrdinal(speakerIndex)),
                Map.entry("speaker_index", Integer.toString(speakerIndex)),
                Map.entry("speaker_ordinal", interjectionOrdinal(speakerIndex)));
    }

    private static String containerTheftAgainPhrase(int priorThefts) {
        if (priorThefts <= 0) {
            return "this time";
        }
        if (priorThefts == 1) {
            return "again";
        }
        return "yet again";
    }

    private static ForcedDialogueDefinition forcedInterjectionDefinition(
            String id,
            String line,
            ForcedDialogueDefinition source,
            List<ForcedDialogueOption> options) {
        return new ForcedDialogueDefinition(
                id,
                source.source(),
                source.trigger(),
                source.output(),
                line.isBlank() ? List.of() : List.of(line),
                true,
                false,
                source.forceCameraTowardsVillager(),
                source.requiresLineOfSight(),
                source.witnessRadius(),
                source.chance(),
                0,
                source.priority(),
                source.minRecentContainerThefts(),
                source.maxRecentContainerThefts(),
                source.minRecentRetaliations(),
                source.maxRecentRetaliations(),
                source.lootTables(),
                source.targetEntityTypes(),
                source.witnessProfessions(),
                source.witnessEquipmentCondition(),
                source.playerItemCondition(),
                source.reputationCondition(),
                options,
                source.leaveOption(),
                source.leaveOptions());
    }

    private static ContainerSnapshot containerSnapshot(ForcedDialogueSession session, ServerLevel level) {
        ResourceLocation lootTable = session.context().lootTable().isBlank()
                ? null
                : ResourceLocation.tryParse(session.context().lootTable());
        return new ContainerSnapshot(
                session.sourceContainerDimension(),
                session.sourceContainerPos(),
                Component.literal(session.context().containerName()),
                lootTable,
                stolenItemCount(session),
                session.removedStacks(),
                level.getGameTime());
    }

    private static int stolenItemCount(ForcedDialogueSession session) {
        int removedCount = session.removedStacks().stream().mapToInt(ItemStack::getCount).sum();
        return removedCount > 0 ? removedCount : session.context().itemCount();
    }

    private static List<UUID> appendedParticipantIds(ForcedDialogueSession session, Villager villager) {
        List<UUID> participantIds = new ArrayList<>(session.participantVillagerIds());
        if (!participantIds.contains(villager.getUUID())) {
            participantIds.add(villager.getUUID());
        }
        return List.copyOf(participantIds);
    }

    private static List<UUID> appendedSpokenVillagerIds(ForcedDialogueSession session, Villager villager) {
        List<UUID> spokenVillagerIds = new ArrayList<>(session.spokenVillagerIds());
        if (!spokenVillagerIds.contains(villager.getUUID())) {
            spokenVillagerIds.add(villager.getUUID());
        }
        return List.copyOf(spokenVillagerIds);
    }

    private static ForcedDialogueSession withParticipant(ForcedDialogueSession session, Villager villager) {
        List<UUID> participantIds = appendedParticipantIds(session, villager);
        if (participantIds.equals(session.participantVillagerIds())) {
            return session;
        }
        return new ForcedDialogueSession(
                session.villagerId(),
                session.definition(),
                session.context(),
                session.sourceContainerDimension(),
                session.sourceContainerPos(),
                session.removedStacks(),
                session.startedGameTime(),
                session.tradeRefreshOfferIndex(),
                session.tradeRefreshDefinitionId(),
                session.tradeRefreshReady(),
                session.replacements(),
                participantIds,
                session.spokenVillagerIds(),
                session.stolenItemsResolved(),
                session.disabledOptionIds());
    }

    private static ForcedDialogueSession withStolenItemsResolved(ForcedDialogueSession session) {
        if (session.stolenItemsResolved()) {
            return session;
        }
        return new ForcedDialogueSession(
                session.villagerId(),
                session.definition(),
                session.context(),
                session.sourceContainerDimension(),
                session.sourceContainerPos(),
                session.removedStacks(),
                session.startedGameTime(),
                session.tradeRefreshOfferIndex(),
                session.tradeRefreshDefinitionId(),
                session.tradeRefreshReady(),
                session.replacements(),
                session.participantVillagerIds(),
                session.spokenVillagerIds(),
                true,
                session.disabledOptionIds());
    }

    private static ForcedDialogueSession withDefinition(ForcedDialogueSession session, ForcedDialogueDefinition definition) {
        return new ForcedDialogueSession(
                session.villagerId(),
                definition,
                session.context(),
                session.sourceContainerDimension(),
                session.sourceContainerPos(),
                session.removedStacks(),
                session.startedGameTime(),
                session.tradeRefreshOfferIndex(),
                session.tradeRefreshDefinitionId(),
                session.tradeRefreshReady(),
                session.replacements(),
                session.participantVillagerIds(),
                session.spokenVillagerIds(),
                session.stolenItemsResolved(),
                List.of());
    }

    private static ForcedDialogueSession withDisabledOption(ForcedDialogueSession session, String optionId) {
        if (optionId == null || optionId.isBlank() || session.disabledOptionIds().contains(optionId)) {
            return session;
        }
        List<String> disabled = new ArrayList<>(session.disabledOptionIds());
        disabled.add(optionId);
        return new ForcedDialogueSession(
                session.villagerId(),
                session.definition(),
                session.context(),
                session.sourceContainerDimension(),
                session.sourceContainerPos(),
                session.removedStacks(),
                session.startedGameTime(),
                session.tradeRefreshOfferIndex(),
                session.tradeRefreshDefinitionId(),
                session.tradeRefreshReady(),
                session.replacements(),
                session.participantVillagerIds(),
                session.spokenVillagerIds(),
                session.stolenItemsResolved(),
                List.copyOf(disabled));
    }

    private static void forceAngerParticipants(ServerPlayer player, Villager currentVillager, ForcedDialogueSession session) {
        ServerLevel level = player.serverLevel();
        boolean angeredCurrent = false;
        for (UUID villagerId : session.participantVillagerIds()) {
            Entity entity = level.getEntity(villagerId);
            if (entity instanceof Villager participant
                    && participant.isAlive()
                    && VillagerInteractionService.canUseForcedInteractionSystem(player, participant)) {
                VillagerRetaliationHandler.forceAnger(participant, player);
                angeredCurrent |= participant.getUUID().equals(currentVillager.getUUID());
            }
        }
        if (!angeredCurrent) {
            VillagerRetaliationHandler.forceAnger(currentVillager, player);
        }
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
        if (tryHighReputationContainerVouch(level, player, snapshot)) {
            return;
        }
        triggerContainerChat(level, player, snapshot, 0, List.of(), ForcedDialogueTrigger.CONTAINER_OPENED);
        ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.CONTAINER_OPENED, snapshot.lootTable())
                .stream()
                .filter(definition -> !isChatOutput(definition))
                .anyMatch(definition -> trigger(level, player, snapshot, 0, List.of(), definition));
    }

    private static boolean tryHighReputationContainerVouch(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || snapshot.lootTable() == null
                || !snapshot.lootTable().getPath().startsWith("chests/village/")) {
            return false;
        }

        double radius = 12.0D;
        double radiusSqr = radius * radius;
        AABB area = AABB.ofSize(Vec3.atCenterOf(snapshot.pos()), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        Optional<ContainerVouchCandidate> voucher = level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby()).stream()
                .filter(villager -> villager.distanceToSqr(player) <= radiusSqr || villager.blockPosition().distSqr(snapshot.pos()) <= radiusSqr)
                .filter(villager -> VillagerInteractionService.canUseForcedInteractionSystem(player, villager))
                .filter(villager -> hasTheftLineOfSight(level, villager, player, snapshot.pos()))
                .map(villager -> new ContainerVouchCandidate(
                        villager,
                        VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID())))
                .filter(candidate -> candidate.reputation().level().trustRank() >= VillagerReputationLevel.TRUSTED.trustRank())
                .max(Comparator
                        .comparingInt((ContainerVouchCandidate candidate) -> candidate.reputation().value())
                        .thenComparingInt(candidate -> candidate.reputation().level().trustRank())
                        .thenComparingDouble(candidate -> -candidate.villager().distanceToSqr(player)));
        if (voucher.isEmpty()) {
            return false;
        }

        Villager villager = voucher.get().villager();
        ReputationSnapshot reputation = voucher.get().reputation();
        double vouchChance = containerVouchChance(reputation);
        boolean allowed = rollChance(level, vouchChance);
        String messageKey = allowed
                ? CONTAINER_OPENED_VOUCH_ALLOW_MESSAGE_KEY
                : CONTAINER_OPENED_VOUCH_DENY_MESSAGE_KEY;
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        Map<String, String> replacements = Map.of(
                "container", snapshot.containerName().getString(),
                "loot_table", snapshot.lootTable().toString(),
                "player", player.getDisplayName().getString(),
                "villager", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                "vouch_chance", Integer.toString((int) Math.round(vouchChance * 100.0D)),
                "vouch_outcome", allowed ? "allow" : "deny",
                "reputation", Integer.toString(reputation.value()),
                "reputation_level", reputation.level().name().toLowerCase(Locale.ROOT));
        String line = VillagerDialogueResources.message(context, messageKey, replacements)
                .orElse(messageKey);
        line = VillagerDialogueResources.resolveTemplate(line, replacements);
        VillagerInteractionService.broadcastForcedVillagerChat(
                level,
                villager,
                line,
                VillagerInteractionService.villagerSpeakerLabel(villager));
        if (!allowed) {
            player.closeContainer();
        }
        return true;
    }

    private static double containerVouchChance(ReputationSnapshot reputation) {
        return switch (reputation.level()) {
            case ROYALTY -> reputationScaledChance(
                    reputation.value(),
                    VillagerRetaliationConfig.ROYALTY_THRESHOLD.get(),
                    VillagerRetaliationConfig.ROYALTY_THRESHOLD.get()
                            + Math.max(1, VillagerRetaliationConfig.ROYALTY_THRESHOLD.get() - VillagerRetaliationConfig.REVERED_THRESHOLD.get()),
                    0.90D,
                    0.98D);
            case REVERED -> reputationScaledChance(
                    reputation.value(),
                    VillagerRetaliationConfig.REVERED_THRESHOLD.get(),
                    VillagerRetaliationConfig.ROYALTY_THRESHOLD.get(),
                    0.78D,
                    0.90D);
            case RESPECTED -> reputationScaledChance(
                    reputation.value(),
                    VillagerRetaliationConfig.RESPECTED_THRESHOLD.get(),
                    VillagerRetaliationConfig.REVERED_THRESHOLD.get(),
                    0.62D,
                    0.78D);
            case TRUSTED -> reputationScaledChance(
                    reputation.value(),
                    VillagerRetaliationConfig.TRUSTED_THRESHOLD.get(),
                    VillagerRetaliationConfig.RESPECTED_THRESHOLD.get(),
                    0.45D,
                    0.62D);
            default -> 0.0D;
        };
    }

    private static double reputationScaledChance(
            int reputation,
            int minReputation,
            int maxReputation,
            double minChance,
            double maxChance) {
        if (maxReputation <= minReputation) {
            return maxChance;
        }
        double progress = (reputation - minReputation) / (double) (maxReputation - minReputation);
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        return minChance + (maxChance - minChance) * progress;
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
        return forcedOptions(definition, level, villager, player, null);
    }

    private static List<DialogueOptionDefinition> forcedOptions(
            ForcedDialogueDefinition definition,
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueSession session) {
        VillagerReputationManager.ReputationSnapshot reputation =
                VillagerReputationManager.getReputationSnapshot(level, villager, player.getUUID());
        return definition.options().stream()
                .filter(option -> session == null || shouldOfferStolenItemReturnOption(session, option))
                .filter(option -> session == null || !session.disabledOptionIds().contains(option.id()))
                .filter(option -> option.reputationCondition().matches(reputation.value(), reputation.level()))
                .filter(option -> optionConditionsMatch(level, villager, player, option))
                .sorted(Comparator.comparingInt(ForcedDialogueOption::order).thenComparing(ForcedDialogueOption::id))
                .map(option -> DialogueOptionDefinition.simple(
                        option.id(),
                        forcedOptionLabel(option, session),
                        DialogueRequestType.QUESTION,
                        option.order()))
                .toList();
    }

    private static String forcedOptionLabel(ForcedDialogueOption option, ForcedDialogueSession session) {
        if (session == null) {
            return option.label();
        }
        return ForcedDialogueResources.resolveTemplate(
                option.label(),
                session.context(),
                option.itemPayment().removal().replacements());
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
        return GeneratedContainerSavedData.generatedContainerLootTable(level, pos).orElse(null);
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

    private record ContainerWitnessCandidate(
            Villager villager,
            ForcedDialogueDefinition definition) {
    }

    private record ContainerVouchCandidate(
            Villager villager,
            ReputationSnapshot reputation) {
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
            String tradeRefreshDefinitionId,
            boolean tradeRefreshReady,
            Map<String, String> replacements,
            List<UUID> participantVillagerIds,
            List<UUID> spokenVillagerIds,
            boolean stolenItemsResolved,
            List<String> disabledOptionIds) {
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
                    "",
                    false,
                    Map.of(),
                    List.of(villagerId),
                    List.of(villagerId),
                    false,
                    List.of());
        }

        private ForcedDialogueSession(
                UUID villagerId,
                ForcedDialogueDefinition definition,
                ForcedDialogueContext context,
                net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
                BlockPos sourceContainerPos,
                List<ItemStack> removedStacks,
                long startedGameTime,
                boolean tradeRefreshReady) {
            this(
                    villagerId,
                    definition,
                    context,
                    sourceContainerDimension,
                    sourceContainerPos,
                    removedStacks,
                    startedGameTime,
                    -1,
                    "",
                    tradeRefreshReady,
                    Map.of(),
                    List.of(villagerId),
                    List.of(villagerId),
                    false,
                    List.of());
        }

        private ForcedDialogueSession(
                UUID villagerId,
                ForcedDialogueDefinition definition,
                ForcedDialogueContext context,
                net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
                BlockPos sourceContainerPos,
                List<ItemStack> removedStacks,
                long startedGameTime,
                boolean tradeRefreshReady,
                Map<String, String> replacements) {
            this(
                    villagerId,
                    definition,
                    context,
                    sourceContainerDimension,
                    sourceContainerPos,
                    removedStacks,
                    startedGameTime,
                    -1,
                    "",
                    tradeRefreshReady,
                    Map.copyOf(replacements),
                    List.of(villagerId),
                    List.of(villagerId),
                    false,
                    List.of());
        }

        private ForcedDialogueSession(
                UUID villagerId,
                ForcedDialogueDefinition definition,
                ForcedDialogueContext context,
                net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
                BlockPos sourceContainerPos,
                List<ItemStack> removedStacks,
                long startedGameTime,
                boolean tradeRefreshReady,
                Map<String, String> replacements,
                List<UUID> participantVillagerIds,
                List<UUID> spokenVillagerIds) {
            this(
                    villagerId,
                    definition,
                    context,
                    sourceContainerDimension,
                    sourceContainerPos,
                    removedStacks,
                    startedGameTime,
                    -1,
                    "",
                    tradeRefreshReady,
                    Map.copyOf(replacements),
                    List.copyOf(participantVillagerIds),
                    List.copyOf(spokenVillagerIds),
                    false,
                    List.of());
        }

        private ForcedDialogueSession(
                UUID villagerId,
                ForcedDialogueDefinition definition,
                ForcedDialogueContext context,
                net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
                BlockPos sourceContainerPos,
                List<ItemStack> removedStacks,
                long startedGameTime,
                boolean tradeRefreshReady,
                List<UUID> participantVillagerIds) {
            this(
                    villagerId,
                    definition,
                    context,
                    sourceContainerDimension,
                    sourceContainerPos,
                    removedStacks,
                    startedGameTime,
                    tradeRefreshReady,
                    participantVillagerIds,
                    participantVillagerIds);
        }

        private ForcedDialogueSession(
                UUID villagerId,
                ForcedDialogueDefinition definition,
                ForcedDialogueContext context,
                net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
                BlockPos sourceContainerPos,
                List<ItemStack> removedStacks,
                long startedGameTime,
                boolean tradeRefreshReady,
                List<UUID> participantVillagerIds,
                List<UUID> spokenVillagerIds) {
            this(
                    villagerId,
                    definition,
                    context,
                    sourceContainerDimension,
                    sourceContainerPos,
                    removedStacks,
                    startedGameTime,
                    -1,
                    "",
                    tradeRefreshReady,
                    Map.of(),
                    List.copyOf(participantVillagerIds),
                    List.copyOf(spokenVillagerIds),
                    false,
                    List.of());
        }

        private ForcedDialogueSession(
                UUID villagerId,
                ForcedDialogueDefinition definition,
                ForcedDialogueContext context,
                net.minecraft.resources.ResourceKey<Level> sourceContainerDimension,
                BlockPos sourceContainerPos,
                List<ItemStack> removedStacks,
                long startedGameTime,
                int tradeRefreshOfferIndex,
                String tradeRefreshDefinitionId) {
            this(
                    villagerId,
                    definition,
                    context,
                    sourceContainerDimension,
                    sourceContainerPos,
                    removedStacks,
                    startedGameTime,
                    tradeRefreshOfferIndex,
                    tradeRefreshDefinitionId,
                    false,
                    Map.of(),
                    List.of(villagerId),
                    List.of(villagerId),
                    false,
                    List.of());
        }
    }

    private record PlayerItemProximityKey(
            UUID villagerId,
            UUID playerId,
            String definitionId) {
    }
}
