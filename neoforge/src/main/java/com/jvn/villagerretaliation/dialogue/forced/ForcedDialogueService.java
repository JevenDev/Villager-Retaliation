package com.jvn.villagerretaliation.dialogue.forced;

import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_DEFINITION_ID;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_READY_INTERJECTION_MESSAGE_KEY;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_READY_MESSAGE_KEY;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_READY_OPTIONS_ID;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_READY_THEFT_INTERJECTION_MESSAGE_KEY;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_REQUIREMENTS_OPTION_ID;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_REVERED_OPTIONS_ID;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_SPECIAL_ORDER_CONFIRM_OPTIONS_ID;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_SPECIAL_ORDER_SELECT_OPTIONS_ID;
import static com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue.TRADE_REFRESH_SPECIAL_ORDER_STATUS_OPTIONS_ID;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.forced.container.ContainerSnapshot;
import com.jvn.villagerretaliation.dialogue.forced.container.ForcedDialogueContainers;
import com.jvn.villagerretaliation.dialogue.forced.container.RecentContainerClick;
import com.jvn.villagerretaliation.dialogue.forced.trade.TradeRefreshForcedDialogue;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.dialogue.normal.SocialAttributeCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueFollowUp;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOutput;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOutputMode;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueStolenItemReturn;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueTrigger;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.LocalizedText;
import com.jvn.villagerretaliation.dialogue.forced.PlayerItemProximityForcedDialogueService.TradeItemMatch;
import com.jvn.villagerretaliation.event.VillagerEventTriggerSavedData;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerInWorldDialogueFocusService;
import com.jvn.villagerretaliation.network.GeneratedContainerTooltipPayload;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager.ReputationSnapshot;
import com.jvn.villagerretaliation.trade.VillagerSpecialOrderService;
import com.jvn.villagerretaliation.trade.VillagerTradeRefreshService;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
    private static final String EXPIRED_PARTY_DEFINITION_ID = "villagerretaliation:expired_party_contract";
    private static final String EXPIRED_PARTY_EXTEND_OPTION_ID = "expired_party.extend";
    private static final String EXPIRED_PARTY_INVENTORY_OPTION_ID = "expired_party.inventory";
    private static final String EXPIRED_PARTY_DURATION_PREFIX = "expired_party.extend.";
    private static final String CONTAINER_THEFT_BACKUP_MESSAGE_KEY = "container_theft.backup_interjection";
    private static final String CONTAINER_THEFT_BACKUP_DEFINITION_ID = "container_theft.backup_interjection";
    private static final String CONTAINER_OPENED_BACKUP_MESSAGE_KEY = "container_opened.backup_interjection";
    private static final String CONTAINER_OPENED_BACKUP_DEFINITION_ID = "container_opened.backup_interjection";
    private static final String CONTAINER_OPENED_VOUCH_ALLOW_MESSAGE_KEY = "container_opened.vouch.allow";
    private static final String CONTAINER_OPENED_VOUCH_DENY_MESSAGE_KEY = "container_opened.vouch.deny";
    private static final String CONTAINER_OPENED_VOUCH_ROYALTY_MESSAGE_KEY = "container_opened.vouch.royalty";
    private static final String CONTAINER_OPENED_VOUCH_ORIGINAL_RESPONSE_MESSAGE_KEY = "container_opened.vouch.original_response";
    private static final String CONTAINER_OPENED_VOUCH_ORIGINAL_ROYALTY_RESPONSE_MESSAGE_KEY = "container_opened.vouch.original_response.royalty";
    private static final String CONTAINER_OPENED_VOUCH_DEFINITION_ID = "container_opened.vouch";
    private static final String CONTAINER_OPENED_VOUCH_ALLOW_DEFINITION_ID = "container_opened.vouch.allow";
    private static final String CONTAINER_OPENED_VOUCH_ACCEPT_OPTION_ID = "container_opened.vouch.accept";
    private static final String CONTAINER_OPENED_VOUCH_RESPONDER_ID_KEY = "container_opened.vouch.responder_id";
    private static final String CONTAINER_OPENED_VOUCH_ROYALTY_KEY = "container_opened.vouch.royalty_flag";
    private static final String ROYALTY_AGGRO_BYPASS_MESSAGE_KEY = "retaliation.royalty_aggro_bypass";
    public static final String SPECIAL_ORDER_STATUS_ROOT_OPTION_ID = "trade_refresh.special_order.status";
    private static final ForcedDialogueOutput SIMPLE_FORCED_OUTPUT =
            new ForcedDialogueOutput(ForcedDialogueOutputMode.FORCED_DIALOGUE, 0.0D, false);
    private static final ForcedDialogueOption SIMPLE_LEAVE_OPTION = new ForcedDialogueOption(
            LEAVE_OPTION_ID,
            LocalizedText.inline("Leave"),
            List.of(),
            0,
            false,
            0.0D,
            true,
            1000,
            ForcedDialogueStolenItemReturn.empty(),
            ForcedDialogueItemPayment.empty(),
            VillagerReputationCondition.empty(),
            SocialAttributeCondition.EMPTY,
            List.of(),
            ForcedDialogueFollowUp.empty());
    private static final List<ForcedDialogueOption> SIMPLE_LEAVE_OPTIONS = List.of(SIMPLE_LEAVE_OPTION);
    private static final long RECENT_CONTAINER_CLICK_TICKS = 8L;
    private static final long FORCED_SESSION_TIMEOUT_TICKS = 20L * 60L;
    private static final long TRADE_REFRESH_READY_SCAN_INTERVAL_TICKS = 40L;
    private static final long SHARED_PARTICIPANT_SCAN_INTERVAL_TICKS = 20L;
    private static final long LOW_GUTS_CONFRONTATION_ESCALATION_WINDOW_TICKS = 20L * 15L;
    private static final int PLAYER_ITEM_PROXIMITY_LINE_SELECTION_ATTEMPTS = 5;
    private static final Map<UUID, RecentContainerClick> RECENT_CONTAINER_CLICKS = new HashMap<>();
    private static final Map<UUID, ContainerSnapshot> OPEN_CONTAINER_SNAPSHOTS = new HashMap<>();
    private static final ForcedDialogueRuntime<ForcedDialogueSession> FORCED_SESSIONS = new ForcedDialogueRuntime<>(
            FORCED_SESSION_TIMEOUT_TICKS, ForcedDialogueService::shouldOfferStolenItemReturnOption,
            ForcedDialogueService::handleRuntimeSelection);
    private static final Map<UUID, Long> NEXT_SHARED_PARTICIPANT_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, String> LAST_PLAYER_ITEM_PROXIMITY_CHAT_LINES = new HashMap<>();
    private static final PlayerItemProximityForcedDialogueService.Delegate PLAYER_ITEM_PROXIMITY_DELEGATE =
            new PlayerItemProximityForcedDialogueService.Delegate() {
                @Override
                public boolean canUseForcedInteractionSystem(ServerPlayer player, Villager villager) {
                    return VillagerInteractionService.canUseForcedInteractionSystem(player, villager);
                }

                @Override
                public boolean hasForcedSession(ServerPlayer player) {
                    return FORCED_SESSIONS.containsKey(player.getUUID());
                }

                @Override
                public boolean matchesReputation(
                        ServerLevel level,
                        Villager villager,
                        ServerPlayer player,
                        ForcedDialogueDefinition definition) {
                    return definitionMatchesReputation(level, villager, player, definition);
                }

                @Override
                public boolean trigger(
                        ServerLevel level,
                        Villager villager,
                        ServerPlayer player,
                        ForcedDialogueDefinition definition,
                        Optional<TradeItemMatch> tradeItemMatch) {
                    return ForcedDialogueTriggerGates.isChatOutput(definition)
                            ? triggerPlayerItemProximityChat(level, villager, player, definition, tradeItemMatch)
                            : triggerPlayerItemProximity(level, villager, player, definition, tradeItemMatch);
                }
            };

    private ForcedDialogueService() {
    }

    public static void setWeaponAimingDialogueDelayEnabled(ServerPlayer player, boolean enabled) {
        PlayerItemProximityForcedDialogueService.setAimingDialogueDelayEnabled(player, enabled);
    }

    public static void clearRuntimeState() {
        RECENT_CONTAINER_CLICKS.clear();
        OPEN_CONTAINER_SNAPSHOTS.clear();
        FORCED_SESSIONS.clear();
        NEXT_SHARED_PARTICIPANT_SCAN_TICKS.clear();
        LAST_PLAYER_ITEM_PROXIMITY_CHAT_LINES.clear();
        PlayerItemProximityForcedDialogueService.clearRuntimeState();
        VillagerInWorldDialogueFocusService.clearRuntimeState();
    }

    public static void rememberPotentialContainerOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !ForcedDialogueTriggerGates.containerEnabled()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation lootTable = ForcedDialogueContainers.generatedLootTable(level, pos);
        if (!ForcedDialogueContainers.isEligibleWatchedContainer(level, state, lootTable)) {
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
                || !ForcedDialogueTriggerGates.containerEnabled()) {
            return;
        }

        RecentContainerClick click = RECENT_CONTAINER_CLICKS.get(player.getUUID());
        if (click == null
                || click.dimension() != level.dimension()
                || level.getGameTime() - click.gameTime() > RECENT_CONTAINER_CLICK_TICKS) {
            return;
        }

        BlockState state = level.getBlockState(click.pos());
        if (!ForcedDialogueContainers.isEligibleWatchedContainer(level, state, click.lootTable())) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new GeneratedContainerTooltipPayload(
                        event.getContainer().containerId,
                        ForcedDialogueContainers.isVillagePropertyLootTable(level, click.lootTable())));

        int itemCount = ForcedDialogueContainers.countItems(event.getContainer());
        ContainerSnapshot snapshot = new ContainerSnapshot(
                click.dimension(),
                click.pos(),
                click.containerName(),
                click.lootTable(),
                itemCount,
                ForcedDialogueContainers.snapshotItems(event.getContainer()),
                level.getGameTime()
        );
        OPEN_CONTAINER_SNAPSHOTS.put(
                player.getUUID(),
                snapshot
        );
        if (VillagerRetaliationConfig.ENABLE_CONTAINER_OPEN_REACTION.get()
                && VillagerRetaliationConfig.CONTAINER_FORCED_DIALOGUE_TRIGGER.get() == ContainerForcedDialogueTrigger.OPENING) {
            triggerContainerOpened(level, player, snapshot);
            return;
        }
    }

    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || event.getEntity().getItem().isEmpty()
                || !ForcedDialogueTriggerGates.containerEnabled()) {
            return;
        }

        ContainerSnapshot snapshot = OPEN_CONTAINER_SNAPSHOTS.get(player.getUUID());
        if (snapshot == null || snapshot.dimension() != level.dimension()) {
            return;
        }

        ItemStack tossedStack = event.getEntity().getItem();
        boolean removedFromContainer = ForcedDialogueContainers.removedStacks(snapshot.itemStacks(), player.containerMenu).stream()
                .anyMatch(removedStack -> ItemStack.isSameItemSameComponents(removedStack, tossedStack)
                        && removedStack.getCount() >= tossedStack.getCount());
        if (!removedFromContainer) {
            return;
        }

        if (ForcedDialogueContainers.restoreToOpenMenu(player.containerMenu, tossedStack.copy()).isEmpty()) {
            event.setCanceled(true);
        }
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !ForcedDialogueTriggerGates.containerEnabled()) {
            return;
        }

        ContainerSnapshot snapshot = OPEN_CONTAINER_SNAPSHOTS.remove(player.getUUID());
        if (snapshot == null || snapshot.dimension() != level.dimension()) {
            return;
        }

        List<ItemStack> removedStacks = ForcedDialogueContainers.removedStacks(snapshot.itemStacks(), event.getContainer());
        int removedCount = removedStacks.stream().mapToInt(ItemStack::getCount).sum();
        if (removedCount <= 0) {
            return;
        }

        triggerContainerTheft(level, player, snapshot, removedCount, removedStacks);
    }

    public static void onContainerBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !ForcedDialogueTriggerGates.containerEnabled()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ResourceLocation lootTable = ForcedDialogueContainers.generatedLootTable(level, pos);
        if (!ForcedDialogueContainers.isEligibleWatchedContainer(level, state, lootTable)) {
            return;
        }
        ForcedDialogueContainers.unpackLootTable(level, pos, player);

        ContainerSnapshot snapshot = new ContainerSnapshot(
                level.dimension(),
                pos.immutable(),
                state.getBlock().getName(),
                lootTable,
                ForcedDialogueContainers.countItems(level, pos),
                ForcedDialogueContainers.snapshotItems(level, pos),
                level.getGameTime()
        );
        OPEN_CONTAINER_SNAPSHOTS.remove(player.getUUID());
        triggerContainerBroken(level, player, snapshot);
    }

    public static boolean handleDialogueRequest(ServerPlayer player, Villager villager, String optionId) {
        return FORCED_SESSIONS.handleRequest(player, villager, optionId);
    }

    public static boolean hasSession(ServerPlayer player, Villager villager) {
        return FORCED_SESSIONS.hasSession(player, villager);
    }

    private static boolean handleRuntimeSelection(ServerPlayer player, Villager villager,
            ForcedDialogueSession session, ForcedDialogueOption option, boolean forceEndConversation, boolean leaveRequest) {
        if (!leaveRequest && isAllowedContainerVouchResponse(session, option)) {
            return handleAllowedContainerVouchResponse(player, villager, session);
        }
        return handleSelectedOption(player, villager, session, option, forceEndConversation, leaveRequest);
    }

    public static boolean openSimpleForcedDialogue(ServerPlayer player, Villager villager, String definitionId, String line) {
        if (line == null || line.isBlank()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        ForcedDialogueDefinition definition = new ForcedDialogueDefinition(
                definitionId,
                null,
                ForcedDialogueTrigger.QUEST,
                SIMPLE_FORCED_OUTPUT,
                List.of(LocalizedText.inline(line)),
                true,
                false,
                true,
                false,
                0.0D,
                1.0D,
                0,
                0,
                1,
                0L,
                0,
                Integer.MAX_VALUE,
                0,
                Integer.MAX_VALUE,
                Set.of(),
                Set.of(),
                Set.of(),
                1,
                5,
                false,
                false,
                0,
                false,
                VillagerEquipmentCondition.empty(),
                VillagerPlayerItemCondition.empty(),
                VillagerReputationCondition.empty(),
                0,
                SIMPLE_LEAVE_OPTIONS,
                SIMPLE_LEAVE_OPTION,
                SIMPLE_LEAVE_OPTIONS,
                DialogueEntryMetadata.EMPTY,
                List.of());
        return openProgrammaticForcedDialogue(player, villager, line, definition);
    }

    public static boolean openExpiredPartyContractDialogue(
            ServerPlayer player, Villager villager, String line, boolean canExtend) {
        if (line == null || line.isBlank()) {
            return false;
        }
        List<ForcedDialogueOption> durations = List.of(
                expiredPartyDurationOption(1),
                expiredPartyDurationOption(3),
                expiredPartyDurationOption(5),
                expiredPartyDurationOption(7),
                expiredPartyDurationOption(15),
                expiredPartyDurationOption(30),
                SIMPLE_LEAVE_OPTION);
        ForcedDialogueFollowUp extensionFlow = new ForcedDialogueFollowUp(
                List.of(LocalizedText.inline("How long would you like to extend the contract?")),
                durations,
                SIMPLE_LEAVE_OPTION,
                List.of(SIMPLE_LEAVE_OPTION));
        ForcedDialogueOption extend = simpleOption(
                EXPIRED_PARTY_EXTEND_OPTION_ID, "Extend Contract", false, 100, extensionFlow);
        ForcedDialogueOption inventory = simpleOption(
                EXPIRED_PARTY_INVENTORY_OPTION_ID, "Open Party Inventory", true, 200,
                ForcedDialogueFollowUp.empty());
        List<ForcedDialogueOption> options = canExtend
                ? List.of(extend, inventory, SIMPLE_LEAVE_OPTION)
                : List.of(inventory, SIMPLE_LEAVE_OPTION);
        ForcedDialogueDefinition definition = new ForcedDialogueDefinition(
                EXPIRED_PARTY_DEFINITION_ID, null, ForcedDialogueTrigger.QUEST, SIMPLE_FORCED_OUTPUT,
                List.of(LocalizedText.inline(line)), true, false, true, false, 0.0D, 1.0D,
                0, 0, 1, 0L, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE,
                Set.of(), Set.of(), Set.of(), 1, 5, false, false, 0, false,
                VillagerEquipmentCondition.empty(), VillagerPlayerItemCondition.empty(),
                VillagerReputationCondition.empty(), 0, options, SIMPLE_LEAVE_OPTION, options,
                DialogueEntryMetadata.EMPTY, List.of());
        return openProgrammaticForcedDialogue(player, villager, line, definition);
    }

    private static ForcedDialogueOption expiredPartyDurationOption(int days) {
        int cost = days * com.jvn.villagerretaliation.party.PartyVillagerContractService.DAILY_EMERALD_COST;
        String dayLabel = days == 1 ? "Day" : "Days";
        return simpleOption(EXPIRED_PARTY_DURATION_PREFIX + days,
                days + " " + dayLabel + " - " + cost + " emeralds", true, days,
                ForcedDialogueFollowUp.empty());
    }

    private static ForcedDialogueOption simpleOption(
            String id, String label, boolean endConversation, int order, ForcedDialogueFollowUp followUp) {
        return new ForcedDialogueOption(
                id, LocalizedText.inline(label), List.of(), 0, false, 0.0D, endConversation, order,
                ForcedDialogueStolenItemReturn.empty(), ForcedDialogueItemPayment.empty(),
                VillagerReputationCondition.empty(), SocialAttributeCondition.EMPTY, List.of(), followUp);
    }

    private static boolean openProgrammaticForcedDialogue(
            ServerPlayer player,
            Villager villager,
            String line,
            ForcedDialogueDefinition definition) {
        ServerLevel level = player.serverLevel();
        ForcedDialogueContext context = new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                player.getName().getString(),
                "",
                "",
                "",
                "",
                "",
                0,
                "",
                "",
                "",
                "",
                0,
                0,
                villager.blockPosition().getX(),
                villager.blockPosition().getY(),
                villager.blockPosition().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player));
        if (!openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                true, definition, context)) {
            return false;
        }
        FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                villager.getUUID(),
                definition,
                context,
                level.dimension(),
                villager.blockPosition(),
                List.of(),
                level.getGameTime()));
        return true;
    }

    public static boolean isStaleConversationEndRequest(ServerPlayer player, int entityId) {
        return FORCED_SESSIONS.isStaleConversationEndRequest(player, entityId);
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
        if (openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                true, definition, context)) {
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
        if (openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                true, definition, context)) {
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
        if (openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                true, definition, context)) {
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
            String empty = tradeRefreshLine(level, villager, player, "trade_refresh.special_order_status_empty", Map.of());
            if (!com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.sendOrder(player, villager, "root_no_active_orders", Map.of(), empty))
                VillagerInteractionService.sendVillagerNotice(player, villager, empty);
            return true;
        }
        Optional<ForcedDialogueDefinition> optionDefinition = tradeRefreshOptionDefinitionById(
                level,
                villager,
                player,
                TRADE_REFRESH_SPECIAL_ORDER_STATUS_OPTIONS_ID);
        if (optionDefinition.isEmpty()) {
            String fallback = tradeRefreshLine(level, villager, player, "trade_refresh.special_order_status_select", Map.of());
            if (!com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.sendOrder(player, villager, "root_definition_missing", Map.of(), fallback))
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
        updateTradeRefreshSession(player, villager, definition, context, -1, "paired_status", "choose_active_order", Map.of());
        return true;
    }

    public static boolean handleLeaveRequest(ServerPlayer player, Villager villager, boolean forceEndConversation) {
        return FORCED_SESSIONS.handleLeaveRequest(player, villager, forceEndConversation);
    }

    private static boolean shouldOfferStolenItemReturnOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return !session.stolenItemsResolved() || option.stolenItemReturn().isEmpty();
    }

    private static boolean isAllowedContainerVouchResponse(ForcedDialogueSession session, ForcedDialogueOption option) {
        return CONTAINER_OPENED_VOUCH_ALLOW_DEFINITION_ID.equals(session.definition().id())
                && CONTAINER_OPENED_VOUCH_ACCEPT_OPTION_ID.equals(option.id());
    }

    private static boolean handleAllowedContainerVouchResponse(ServerPlayer player, Villager vouchingVillager, ForcedDialogueSession session) {
        FORCED_SESSIONS.remove(player.getUUID());
        VillagerConversationService.endForPlayer(player, true);
        sendAllowedContainerVouchOriginalResponse(player, vouchingVillager, session);
        openAllowedVouchContainer(player, session);
        return true;
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

        if (EXPIRED_PARTY_DEFINITION_ID.equals(session.definition().id())) {
            if (EXPIRED_PARTY_INVENTORY_OPTION_ID.equals(option.id())) {
                FORCED_SESSIONS.remove(player.getUUID());
                VillagerConversationService.endForPlayer(player, true);
                com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.openPreferred(player, villager);
                return true;
            }
            if (option.id().startsWith(EXPIRED_PARTY_DURATION_PREFIX)) {
                int days;
                try {
                    days = Integer.parseInt(option.id().substring(EXPIRED_PARTY_DURATION_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    days = 0;
                }
                com.jvn.villagerretaliation.party.PartyVillagerContractService.ContractResult result =
                        com.jvn.villagerretaliation.party.PartyVillagerContractService.renewExpired(
                                player, villager, days);
                VillagerInteractionService.sendPartyContractResult(player, villager, result);
                FORCED_SESSIONS.remove(player.getUUID());
                VillagerConversationService.endForPlayer(player, true);
                return true;
            }
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
        if (ForcedDialogueRestitutionHandler.handle(player, villager, session, option, generatedMenu(player, villager, session))) {
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
        boolean requestedAggro = option.aggro() || rollChance(player.serverLevel(), option.aggroChance());
        boolean aggro = requestedAggro && !session.royaltyAggroBypass();
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
        boolean responseReplaced = false;
        LocalizedText responseText = option.selectResponse(player.serverLevel().getRandom());
        LocalizedText stolenItemReturnResponse = stolenItemReturn.selectSuccessResponse(player.serverLevel().getRandom());
        if (returnedStolenItems && !stolenItemReturnResponse.isBlank()) {
            responseText = stolenItemReturnResponse;
            responseReplaced = true;
        }
        LocalizedText itemPaymentResponse = itemPayment.selectSuccessResponse(player.serverLevel().getRandom());
        if (!itemPayment.isEmpty() && !itemPaymentResponse.isBlank()) {
            responseText = itemPaymentResponse;
            responseReplaced = true;
        }
        String response = ForcedDialogueResources.resolveTemplate(
                responseText,
                session.context(),
                sessionResponseReplacements(session, itemPayment.removal().replacements()));
        var exchanges = resolveForcedExchanges(player, villager, option.exchanges(), session.context(),
                sessionResponseReplacements(session, itemPayment.removal().replacements()));
        if (exchanges.isEmpty() && option.camera() != null && !response.isBlank())
            exchanges = List.of(com.jvn.villagerretaliation.dialogue.DialogueSpeech.npc(villager,
                    com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(response,
                            com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE), option.camera()));
        if (responseReplaced && !exchanges.isEmpty()) exchanges = com.jvn.villagerretaliation.dialogue.DialogueSpeech.replaceSpeakerReply(
                exchanges, new com.jvn.villagerretaliation.dialogue.DialogueSpeech(villager.getUUID(), villager.getId(),
                        VillagerPresetNameRegistry.resolveDisplayName(villager).getString(), com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(response,
                                com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE)));
        if (exchanges.isEmpty() && !option.followUp().exchanges().isEmpty() && !response.isBlank())
            exchanges = List.of(new com.jvn.villagerretaliation.dialogue.DialogueSpeech(villager.getUUID(), villager.getId(),
                    VillagerPresetNameRegistry.resolveDisplayName(villager).getString(), com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(response,
                            com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE)));
        boolean pairedStatusLeave = "paired_status".equals(session.tradeRefreshDefinitionId()) && !aggro
                && option.endConversation() && !responseReplaced && exchanges.isEmpty()
                && com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.sendOrder(player, villager,
                        "leave_status_discussion", Map.of(), response);
        if (!pairedStatusLeave && (option.followUp().isEmpty() || aggro || exchanges.isEmpty())) {
            if (!exchanges.isEmpty()) VillagerInteractionService.sendDialogueExchanges(player, villager, exchanges);
            else if (!response.isBlank()) VillagerInteractionService.broadcastForcedVillagerChat(
                    player.serverLevel(), villager, response, VillagerInteractionService.villagerSpeakerLabel(villager));
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
            openFollowUp(player, villager, session, option, exchanges);
            return true;
        }
        if (option.endConversation() || forceEndConversation) {
            FORCED_SESSIONS.remove(player.getUUID());
            VillagerConversationService.endForPlayer(player, true, !aggro && (!exchanges.isEmpty() || pairedStatusLeave));
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
        } else if (requestedAggro && session.royaltyAggroBypass()) {
            sendRoyaltyAggroBypassNotice(player, villager);
        }
        return true;
    }

    private static ForcedDialogueRuntime.GeneratedMenu generatedMenu(
            ServerPlayer player, Villager villager, ForcedDialogueSession session) {
        return new ForcedDialogueRuntime.GeneratedMenu() {
            @Override
            public void open(ForcedDialogueDefinition definition) {
                openGeneratedOptionSet(player, villager, session, definition);
            }

            @Override
            public void disable(String optionId, String response) {
                ForcedDialogueSession updated = withDisabledOption(session, optionId);
                FORCED_SESSIONS.put(player.getUUID(), updated);
                VillagerInteractionService.broadcastForcedVillagerChat(
                        player.serverLevel(), villager, response, VillagerInteractionService.villagerSpeakerLabel(villager));
                VillagerInteractionService.sendForcedDialogueReputation(
                        player, villager, forcedOptions(updated.definition(), player.serverLevel(), villager, player, updated),
                        updated.definition().forceCameraTowardsVillager());
            }
        };
    }

    private static void openFollowUp(
            ServerPlayer player,
            Villager villager,
            ForcedDialogueSession session,
            ForcedDialogueOption option, List<com.jvn.villagerretaliation.dialogue.DialogueSpeech> leadingExchanges) {
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
        var exchanges = new ArrayList<>(leadingExchanges);
        exchanges.addAll(resolveForcedExchanges(player, villager, followUp.exchanges(), session.context(),
                option.itemPayment().removal().replacements()));
        if ((!exchanges.isEmpty() || followUp.camera() != null) && followUp.exchanges().isEmpty() && !line.isBlank())
            exchanges.add(new com.jvn.villagerretaliation.dialogue.DialogueSpeech(villager.getUUID(), villager.getId(),
                    VillagerPresetNameRegistry.resolveDisplayName(villager).getString(), com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(line,
                            com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE), followUp.camera()));
        openGeneratedOptionSet(player, villager, session, ForcedDialogueRuntime.definitionWithOptions(
                session.definition().id() + "." + option.id() + ".follow_up", session.definition(),
                exchanges.isEmpty() ? line : "", followUp.options(), followUp.leaveOption()));
        if (!exchanges.isEmpty()) VillagerInteractionService.sendDialogueExchanges(player, villager, exchanges);
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
                    ForcedDialogueResources.resolveTemplate(definition.lines().get(0), session.context()),
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

    private static boolean rollChance(ServerLevel level, double chance) {
        return chance > 0.0D && level.getRandom().nextDouble() < chance;
    }

    private static boolean isTradeRefreshTradeOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return TradeRefreshForcedDialogue.isTradeOption(session.definition().id(), option.id());
    }

    private static boolean isTradeRefreshSurpriseOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return TradeRefreshForcedDialogue.isSurpriseOption(
                session.definition().id(),
                option.id(),
                session.tradeRefreshOfferIndex());
    }

    private static boolean isTradeRefreshSpecialOrderOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return TradeRefreshForcedDialogue.isSpecialOrderOption(
                session.definition().id(),
                option.id(),
                session.tradeRefreshOfferIndex());
    }

    private static boolean isTradeRefreshConfirmSpecialOrderOption(ForcedDialogueSession session, ForcedDialogueOption option) {
        return TradeRefreshForcedDialogue.isConfirmSpecialOrderOption(
                session.definition().id(),
                option.id(),
                session.tradeRefreshOfferIndex(),
                session.tradeRefreshDefinitionId());
    }

    private static boolean isTradeRefreshDefinition(ForcedDialogueSession session) {
        return TradeRefreshForcedDialogue.isDefinition(session.definition().id());
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
                line.isBlank() ? List.of() : List.of(LocalizedText.inline(line)),
                optionDefinition.initiateDialogue(),
                optionDefinition.aggroImmediately(),
                optionDefinition.forceCameraTowardsVillager(),
                optionDefinition.requiresLineOfSight(),
                optionDefinition.witnessRadius(),
                optionDefinition.chance(),
                optionDefinition.reputationDelta(),
                optionDefinition.priority(),
                optionDefinition.weight(),
                optionDefinition.cooldownTicks(),
                optionDefinition.minRecentContainerThefts(),
                optionDefinition.maxRecentContainerThefts(),
                optionDefinition.minRecentRetaliations(),
                optionDefinition.maxRecentRetaliations(),
                optionDefinition.lootTables(),
                optionDefinition.targetEntityTypes(),
                optionDefinition.witnessProfessions(),
                optionDefinition.minTradeLevel(),
                optionDefinition.maxTradeLevel(),
                optionDefinition.requiresHeldTradeItem(),
                optionDefinition.requiresPlayerAimingAtWitness(),
                optionDefinition.requiredAimDurationTicks(),
                optionDefinition.requiresSameParty(),
                optionDefinition.witnessEquipmentCondition(),
                optionDefinition.playerItemCondition(),
                optionDefinition.reputationCondition(),
                optionDefinition.drawWeaponTicks(),
                options,
                leaveOption,
                leaveOptions,
                optionDefinition.metadata(),
                optionDefinition.conditions());
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
                ForcedDialogueResources.resolveTemplate(
                        optionDefinition.selectLine(level.getRandom()),
                        tradeRefreshContext(level, villager, player, replacements)),
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
                List.of(LocalizedText.inline(response)),
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
                option.followUp(), option.exchanges(), option.camera());
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
            String fallback=tradeRefreshLine(level,villager,player,"trade_refresh.special_order_status_empty",Map.of());
            if (com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.sendOrder(player,villager,
                    "selected_order_no_longer_active",Map.of(),fallback)) {
                VillagerConversationService.endForPlayer(player,true,true);
                return;
            }
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
            String fallback=tradeRefreshLine(level,villager,player,"trade_refresh.special_order_status",selectedStatus.get().replacements());
            if (com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.sendOrder(player,villager,
                    selectedStatus.get().daysRemaining()==0?"selected_order_zero_day_timing":"selected_order_wait_remaining",selectedStatus.get().replacements(),fallback)) {
                VillagerInteractionService.sendDialogueReputation(player,villager,level);
                return;
            }
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
        updateTradeRefreshSession(player, villager, definition, context, -1, "paired_status",
                selectedStatus.get().daysRemaining() == 0 ? "selected_order_zero_day_timing" : "selected_order_wait_remaining", selectedStatus.get().replacements());
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
                LocalizedText.inline(label),
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
        updateTradeRefreshSession(player, villager, definition, context, offerIndex, definitionId, "", Map.of());
    }

    private static void updateTradeRefreshSession(ServerPlayer player, Villager villager, ForcedDialogueDefinition definition,
            ForcedDialogueContext context, int offerIndex, String definitionId, String pairedBranch, Map<String, String> pairedTokens) {
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
        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        boolean paired = !pairedBranch.isEmpty() && com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.sendOrder(
                player, villager, pairedBranch, pairedTokens, line);
        if (!paired && !line.isBlank()) {
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
                pos.getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player));
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
        if (stolenItemReturn.failureAggro() && session.royaltyAggroBypass()) {
            sendRoyaltyAggroBypassNotice(player, villager);
        } else if (stolenItemReturn.failureAggro()) {
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
        if (itemPayment.failureAggro() && session.royaltyAggroBypass()) {
            sendRoyaltyAggroBypassNotice(player, villager);
        } else if (itemPayment.failureAggro()) {
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
        if (!TickThrottle.isSpreadTick(villager.getUUID(), gameTime, TRADE_REFRESH_READY_SCAN_INTERVAL_TICKS)) {
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
        if (FORCED_SESSIONS.isEmpty()) {
            NEXT_SHARED_PARTICIPANT_SCAN_TICKS.clear();
            return;
        }

        UUID villagerId = villager.getUUID();
        long gameTime = level.getGameTime();
        boolean scanForNewParticipation = consumeVillagerScanSlot(
                villagerId,
                NEXT_SHARED_PARTICIPANT_SCAN_TICKS,
                gameTime,
                SHARED_PARTICIPANT_SCAN_INTERVAL_TICKS);
        for (Map.Entry<UUID, ForcedDialogueSession> entry : FORCED_SESSIONS.snapshot().entrySet()) {
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

            if (!scanForNewParticipation) {
                continue;
            }
            if (isEligibleSharedForcedDialogueContinuation(level, player, villager, session)) {
                ForcedDialogueSession updated = withParticipant(session, villager);
                FORCED_SESSIONS.put(player.getUUID(), updated);
                VillagerConversationService.holdSharedForcedParticipant(villager, player);
            }
        }
    }

    private static boolean consumeVillagerScanSlot(
            UUID villagerId,
            Map<UUID, Long> nextScanTicks,
            long gameTime,
            long intervalTicks) {
        return TickThrottle.consume(villagerId, nextScanTicks, gameTime, intervalTicks);
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
        if (ForcedDialogueTriggerGates.isSharedContainerTrigger(session.definition().trigger())) {
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
        return ForcedDialogueTriggerGates.isSharedContainerTrigger(session.definition().trigger())
                && matchingAdditionalContainerWitnessDefinition(level, player, session, villager).isPresent();
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
        if (tryAdvanceHighReputationContainerVouch(level, player, currentVillager, session)) {
            return true;
        }
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
        if (openDialogue(
                player,
                witness,
                line,
                forcedOptions(definition, level, witness, player, session),
                definition.forceCameraTowardsVillager(), definition, context)) {
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
                    session.disabledOptionIds(),
                    session.royaltyAggroBypass()));
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
        if (!ForcedDialogueTriggerGates.isSharedContainerTrigger(session.definition().trigger())) {
            return List.of();
        }
        ResourceLocation lootTable = session.context().lootTable().isBlank()
                ? null
                : ResourceLocation.tryParse(session.context().lootTable());
        List<ForcedDialogueDefinition> definitions = ForcedDialogueResources
                .selectCandidates(level.getServer(), session.definition().trigger(), lootTable)
                .stream()
                .filter(definition -> !ForcedDialogueTriggerGates.isChatOutput(definition))
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
                        VillageEventMemory.recentForVillager(level, villager),
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
                VillageEventMemory.recentForVillager(level, witness),
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
                previous.z(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player));
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
                line.isBlank() ? List.of() : List.of(LocalizedText.inline(line)),
                true,
                false,
                source.forceCameraTowardsVillager(),
                source.requiresLineOfSight(),
                source.witnessRadius(),
                source.chance(),
                0,
                source.priority(),
                source.weight(),
                source.cooldownTicks(),
                source.minRecentContainerThefts(),
                source.maxRecentContainerThefts(),
                source.minRecentRetaliations(),
                source.maxRecentRetaliations(),
                source.lootTables(),
                source.targetEntityTypes(),
                source.witnessProfessions(),
                source.minTradeLevel(),
                source.maxTradeLevel(),
                source.requiresHeldTradeItem(),
                source.requiresPlayerAimingAtWitness(),
                source.requiredAimDurationTicks(),
                source.requiresSameParty(),
                source.witnessEquipmentCondition(),
                source.playerItemCondition(),
                source.reputationCondition(),
                source.drawWeaponTicks(),
                options,
                source.leaveOption(),
                source.leaveOptions(),
                source.metadata(),
                source.conditions());
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

    private static boolean openAllowedVouchContainer(ServerPlayer player, ForcedDialogueSession session) {
        ServerLevel level = player.getServer().getLevel(session.sourceContainerDimension());
        if (level == null || player.level().dimension() != session.sourceContainerDimension()) {
            return false;
        }

        BlockPos pos = session.sourceContainerPos();
        BlockState state = level.getBlockState(pos);
        MenuProvider menuProvider = state.getMenuProvider(level, pos);
        if (menuProvider == null) {
            return false;
        }

        RECENT_CONTAINER_CLICKS.remove(player.getUUID());
        OPEN_CONTAINER_SNAPSHOTS.remove(player.getUUID());
        return player.openMenu(menuProvider).isPresent();
    }

    private static void sendAllowedContainerVouchOriginalResponse(
            ServerPlayer player,
            Villager vouchingVillager,
            ForcedDialogueSession session) {
        Optional<UUID> responderId = parsedUuid(session.replacements().get(CONTAINER_OPENED_VOUCH_RESPONDER_ID_KEY));
        if (responderId.isEmpty()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(responderId.get());
        if (!(entity instanceof Villager responder) || !responder.isAlive()) {
            return;
        }

        ContainerSnapshot snapshot = containerSnapshot(session, level);
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, responder);
        String responderName = VillagerPresetNameRegistry.resolveDisplayName(responder).getString();
        String voucherName = VillagerPresetNameRegistry.resolveDisplayName(vouchingVillager).getString();
        Map<String, String> replacements = Map.of(
                "container", snapshot.containerName().getString(),
                "player", player.getDisplayName().getString(),
                "villager", responderName,
                "current_villager", responderName,
                "original_villager", responderName,
                "voucher", voucherName,
                "previous_villager", voucherName,
                "vouching_villager", voucherName);
        String messageKey = Boolean.parseBoolean(session.replacements().getOrDefault(CONTAINER_OPENED_VOUCH_ROYALTY_KEY, "false"))
                ? CONTAINER_OPENED_VOUCH_ORIGINAL_ROYALTY_RESPONSE_MESSAGE_KEY
                : CONTAINER_OPENED_VOUCH_ORIGINAL_RESPONSE_MESSAGE_KEY;
        String line = VillagerDialogueResources.message(context, messageKey, replacements).orElse("");
        line = VillagerDialogueResources.resolveTemplate(line, replacements);
        if (!line.isBlank()) {
            VillagerInteractionService.broadcastForcedVillagerChat(
                    level,
                    responder,
                    line,
                    VillagerInteractionService.villagerSpeakerLabel(responder));
        }
    }

    private static Optional<UUID> parsedUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
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
                session.disabledOptionIds(),
                session.royaltyAggroBypass());
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
                session.disabledOptionIds(),
                session.royaltyAggroBypass());
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
                List.of(),
                session.royaltyAggroBypass());
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
                List.copyOf(disabled),
                session.royaltyAggroBypass());
    }

    private static void forceAngerParticipants(ServerPlayer player, Villager currentVillager, ForcedDialogueSession session) {
        if (session.royaltyAggroBypass()) {
            sendRoyaltyAggroBypassNotice(player, currentVillager);
            return;
        }
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

    public static boolean tryTriggerQuestDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String forcedDialogueId,
            Map<String, String> replacements) {
        return triggerQuestDialogueResult(level, villager, player, forcedDialogueId, replacements).legacyResult().ran();
    }

    public static ActionResult triggerQuestDialogueResult(ServerLevel level, Villager villager, ServerPlayer player,
            String forcedDialogueId, Map<String, String> replacements) {
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                || villager == null
                || player == null
                || !villager.isAlive()
                || villager.isBaby()
                || !player.isAlive()
                || player.isSpectator()
                || FORCED_SESSIONS.containsKey(player.getUUID())
                || !VillagerInteractionService.canUseForcedInteractionSystem(player, villager)) {
            return ActionResult.unavailable("forced dialogue unavailable", Set.of(), VillagerActionResult.EMPTY);
        }

        for (ForcedDialogueDefinition definition : ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.QUEST, null)) {
            if (!matchesQuestForcedDialogueId(definition, forcedDialogueId)
                    || !definition.matchesWitness(villager)
                    || !definitionMatchesReputation(level, villager, player, definition)
                    || (definition.requiresLineOfSight() && !villager.hasLineOfSight(player))) {
                continue;
            }
            if (triggerQuestDialogue(level, villager, player, definition, replacements)) {
                return ActionResult.success(VillagerActionResult.success(), Set.of());
            }
        }
        return triggerQuestDialogueTree(level, villager, player, forcedDialogueId, replacements);
    }

    private static boolean matchesQuestForcedDialogueId(ForcedDialogueDefinition definition, String forcedDialogueId) {
        if (forcedDialogueId == null || forcedDialogueId.isBlank()) {
            return true;
        }
        String normalized = forcedDialogueId.trim();
        return definition.id().equals(normalized) || definition.source().toString().equals(normalized);
    }

    static List<com.jvn.villagerretaliation.dialogue.DialogueSpeech> resolveForcedExchanges(
            ServerPlayer player, Villager villager, List<com.jvn.villagerretaliation.dialogue.DialogueExchange> exchanges,
            ForcedDialogueContext context, Map<String, String> replacements) {
        return exchanges.stream().map(exchange -> {
            Entity speaker = switch (exchange.speaker()) {
                case ROLE -> villager;
                case PLAYER -> {
                    if (player == null) throw new IllegalArgumentException("Player dialogue requires a participating player");
                    yield player;
                }
                case NARRATOR -> null;
            };
            String text = ForcedDialogueResources.resolveTemplate(new LocalizedText(exchange.text(), exchange.textKey()), context, replacements);
            return new com.jvn.villagerretaliation.dialogue.DialogueSpeech(speaker == null ? null : speaker.getUUID(),
                    speaker == null ? -1 : speaker.getId(), speaker == null ? "" : VillagerPresetNameRegistry.resolveDisplayName(speaker).getString(),
                    com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(text,
                            com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE), exchange.camera());
        }).toList();
    }

    private static void sendDefinitionNotice(ServerPlayer player, Villager villager, ForcedDialogueDefinition definition,
            ForcedDialogueContext context, String line) {
        if (!definition.output().exchanges().isEmpty()) VillagerInteractionService.sendDialogueExchanges(player, villager,
                resolveForcedExchanges(player, villager, definition.output().exchanges(), context, Map.of()));
        else if (!line.isBlank() && definition.output().camera() != null)
            VillagerInteractionService.sendDialogueExchanges(player, villager, List.of(
                    com.jvn.villagerretaliation.dialogue.DialogueSpeech.npc(villager,
                            com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(line,
                                    com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE), definition.output().camera())));
        else if (!line.isBlank()) VillagerInteractionService.sendVillagerNotice(player, villager, line);
    }

    private static boolean openDialogue(ServerPlayer player, Villager villager, String line, List<DialogueOptionDefinition> options,
            boolean camera, ForcedDialogueDefinition definition, ForcedDialogueContext context) {
        if (!VillagerInteractionService.openForcedDialogue(player, villager, line, options, camera)) return false;
        if (!definition.output().exchanges().isEmpty() || definition.output().camera() != null)
            sendDefinitionNotice(player, villager, definition, context, line);
        return true;
    }

    private static boolean triggerQuestDialogue(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            Map<String, String> replacements) {
        if (!rollChance(level, definition.chance())) {
            return false;
        }
        Map<String, String> safeReplacements = replacements == null ? Map.of() : replacements;
        ForcedDialogueContext context = questDialogueContext(villager, player, safeReplacements);
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, villager, player.getUUID(), definition.reputationDelta(), villager.blockPosition());
            VillagerGossipHooks.spreadReputation(level, villager, player.getUUID(), definition.reputationDelta());
        }

        String line = ForcedDialogueResources.resolveTemplate(
                definition.selectLine(level.getRandom()),
                context,
                safeReplacements);
        if (ForcedDialogueTriggerGates.isChatOutput(definition)) {
            broadcastDataDrivenChat(level, villager, player, definition, line, context, safeReplacements);
            return true;
        }
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, villager, definition, context, line);
            }
            if (isRoyaltyFor(level, villager, player)) {
                sendRoyaltyAggroBypassNotice(player, villager);
                return true;
            }
            VillagerRetaliationHandler.forceAnger(villager, player);
            return true;
        }
        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, villager, definition, context, line);
            }
            return true;
        }

        ForcedDialogueSession session = new ForcedDialogueSession(
                villager.getUUID(),
                definition,
                context,
                level.dimension(),
                villager.blockPosition().immutable(),
                List.of(),
                level.getGameTime(),
                -1,
                "",
                false,
                safeReplacements,
                List.of(villager.getUUID()),
                List.of(villager.getUUID()),
                false,
                List.of(),
                isRoyaltyFor(level, villager, player)
        );
        if (openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player, session),
                definition.forceCameraTowardsVillager(), definition, context)) {
            FORCED_SESSIONS.put(player.getUUID(), session);
        } else if (!line.isBlank()) {
            sendDefinitionNotice(player, villager, definition, context, line);
        }
        return true;
    }

    private static ActionResult triggerQuestDialogueTree(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String forcedDialogueId,
            Map<String, String> replacements) {
        if (forcedDialogueId == null || forcedDialogueId.isBlank()) {
            return ActionResult.unavailable("forced dialogue tree unavailable", Set.of(), VillagerActionResult.EMPTY);
        }
        QuestDialogueCatalog.Binding binding = VillagerQuestResources
                .questDialogueCatalog(level.getServer())
                .forcedBinding(forcedDialogueId)
                .orElse(null);
        if (binding == null) {
            return ActionResult.unavailable("forced dialogue tree unavailable", Set.of(), VillagerActionResult.EMPTY);
        }

        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        DialogueTreeService.EntryResult entry = DialogueTreeService.startEntryResult(
                context,
                binding.treeId(),
                binding.entryId(),
                replacements == null ? Map.of() : replacements, true);
        if (!entry.outcome().success()) return entry.outcome();
        Optional<VillagerDialogueService.DialogueResult> result = entry.dialogue();
        if (result.isEmpty()) return entry.outcome();
        List<DialogueOptionDefinition> options = DialogueTreeService.activeOptions(context).orElse(List.of());
        if (options.stream().noneMatch(DialogueOptionDefinition::available)) {
            String text = result.get().text();
            if (!result.get().exchanges().isEmpty()) VillagerInteractionService.sendDialogueExchanges(player, villager, result.get().exchanges());
            else if (!text.isBlank()) VillagerInteractionService.sendVillagerNotice(player, villager, text);
            return ActionResult.success(VillagerActionResult.success(), Set.of());
        }
        if (VillagerInteractionService.openForcedDialogue(
                player,
                villager,
                result.get().text(),
                options,
                options.stream().anyMatch(DialogueOptionDefinition::forceCameraTowardsVillager))) {
            if (!result.get().exchanges().isEmpty()) VillagerInteractionService.sendDialogueExchanges(player, villager, result.get().exchanges());
            return ActionResult.success(VillagerActionResult.success(), Set.of());
        }
        DialogueTreeService.leaveActiveSession(context);
        return ActionResult.unavailable("forced dialogue tree unavailable", Set.of(), VillagerActionResult.EMPTY);
    }

    private static ForcedDialogueContext questDialogueContext(
            Villager villager,
            ServerPlayer player,
            Map<String, String> replacements) {
        String villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        String playerName = player.getDisplayName().getString();
        String quest = replacements.getOrDefault("quest", "quest");
        String questId = replacements.getOrDefault("quest_id", "");
        String target = replacements.getOrDefault("target", quest);
        String proofItem = replacements.getOrDefault("proof_item", target);
        ServerLevel level = player.serverLevel();
        return new ForcedDialogueContext(
                villagerName,
                playerName,
                target,
                "quest",
                questId,
                proofItem,
                questId,
                1,
                proofItem,
                proofItem,
                quest,
                "",
                0,
                0,
                villager.blockPosition().getX(),
                villager.blockPosition().getY(),
                villager.blockPosition().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player)
        );
    }

    public static void maybeTriggerPlayerAiming(MinecraftServer server) {
        PlayerItemProximityForcedDialogueService.maybeTriggerAiming(server, PLAYER_ITEM_PROXIMITY_DELEGATE);
    }

    public static void maybeTriggerPlayerItemProximity(ServerLevel level, Villager villager) {
        PlayerItemProximityForcedDialogueService.maybeTrigger(level, villager, PLAYER_ITEM_PROXIMITY_DELEGATE);
    }

    public static boolean triggerRetaliationStarted(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!ForcedDialogueTriggerGates.retaliationEnabled()) {
            return false;
        }
        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
        int priorRetaliations = VillageEventMemory.countForPlayer(
                VillageEventMemory.recentForVillager(level, villager),
                player.getUUID(),
                VillageEventMemory.EventTag.VILLAGER_RETALIATION_STARTED
        );
        triggerRetaliationChat(level, villager, player, priorRetaliations, targetTypeId);

        List<ForcedDialogueDefinition> candidates = ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.RETALIATION_STARTED, null, targetTypeId)
                .stream()
                .filter(definition -> !ForcedDialogueTriggerGates.isChatOutput(definition))
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

    public static boolean triggerLowGutsRetaliationRally(ServerLevel level, Villager victim, ServerPlayer player) {
        if (!ForcedDialogueTriggerGates.retaliationEnabled()) {
            return false;
        }

        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
        List<ForcedDialogueDefinition> candidates = ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.LOW_GUTS_RALLY, null, targetTypeId)
                .stream()
                .filter(definition -> !ForcedDialogueTriggerGates.isChatOutput(definition))
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        double confrontationRadius = candidates.stream()
                .mapToDouble(ForcedDialogueDefinition::witnessRadius)
                .max()
                .orElse(0.0D);
        if (isEscalatedLowGutsConflict(level, victim, player, confrontationRadius)) {
            return false;
        }

        for (ForcedDialogueDefinition definition : candidates) {
            Villager witness = findLowGutsRallyWitness(level, victim, player, definition).orElse(null);
            if (witness != null && triggerLowGutsRally(level, witness, victim, player, definition, targetTypeId)) {
                return true;
            }
        }
        return false;
    }

    public static void triggerRetaliationChat(ServerLevel level, Villager villager, LivingEntity target) {
        if (target instanceof ServerPlayer || !ForcedDialogueTriggerGates.retaliationEnabled()) {
            return;
        }
        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        ForcedDialogueResources
                .selectCandidates(level.getServer(), ForcedDialogueTrigger.RETALIATION_STARTED, null, targetTypeId)
                .stream()
                .filter(ForcedDialogueTriggerGates::isChatOutput)
                .filter(definition -> definition.matchesWitness(villager))
                .filter(definition -> villager.distanceToSqr(target) <= definition.witnessRadius() * definition.witnessRadius())
                .filter(definition -> !definition.requiresLineOfSight() || hasLoadedLineOfSight(level, villager, target))
                .filter(definition -> definition.matchesRecentRetaliations(0))
                .anyMatch(definition -> triggerRetaliationChat(level, villager, target, definition, targetTypeId));
    }

    private static boolean hasLoadedLineOfSight(ServerLevel level, Villager villager, LivingEntity target) {
        var from = BlockPos.containing(villager.getEyePosition());
        var to = BlockPos.containing(target.getEyePosition());
        return level.hasChunksAt(
                Math.min(from.getX(), to.getX()) - 1, Math.min(from.getZ(), to.getZ()) - 1,
                Math.max(from.getX(), to.getX()) + 1, Math.max(from.getZ(), to.getZ()) + 1)
                && villager.hasLineOfSight(target);
    }

    public static void triggerRetaliationDisengagementChat(
            ServerLevel level,
            Villager villager,
            LivingEntity target,
            ForcedDialogueTrigger trigger) {
        if (!(target instanceof ServerPlayer player)
                || !ForcedDialogueTriggerGates.retaliationEnabled()
                || !isRetaliationDisengagementTrigger(trigger)) {
            return;
        }

        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
        ForcedDialogueResources
                .selectCandidates(level.getServer(), trigger, null, targetTypeId)
                .stream()
                .filter(ForcedDialogueTriggerGates::isChatOutput)
                .filter(definition -> definition.matchesWitness(villager))
                .filter(definition -> definitionMatchesReputation(level, villager, player, definition))
                .filter(definition -> villager.distanceToSqr(player) <= definition.witnessRadius() * definition.witnessRadius())
                .filter(definition -> !definition.requiresLineOfSight() || villager.hasLineOfSight(player))
                .anyMatch(definition -> triggerRetaliationChat(level, villager, player, definition, 0, targetTypeId));
    }

    private static boolean isRetaliationDisengagementTrigger(ForcedDialogueTrigger trigger) {
        return trigger == ForcedDialogueTrigger.LOW_GUTS_PURSUIT_ABANDONED
                || trigger == ForcedDialogueTrigger.LOW_GUTS_COUNTER_COMPLETED
                || trigger == ForcedDialogueTrigger.RETALIATION_TARGET_ESCAPED
                || trigger == ForcedDialogueTrigger.RETALIATION_SEARCH_EXPIRED;
    }

    private static boolean triggerRetaliationChat(
            ServerLevel level,
            Villager villager,
            LivingEntity target,
            ForcedDialogueDefinition definition,
            ResourceLocation targetTypeId) {
        if (!rollChance(level, definition.chance())) {
            return false;
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
                villager.blockPosition().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.DEFAULT_LOCALE
        );
        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (!line.isBlank()) {
            broadcastDataDrivenChat(
                    level, villager, target instanceof ServerPlayer player ? player : null, definition, line, context, Map.of());
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
                .filter(definition -> !ForcedDialogueTriggerGates.isChatOutput(definition))
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
                .filter(definition -> !ForcedDialogueTriggerGates.isChatOutput(definition))
                .anyMatch(definition -> trigger(level, player, snapshot, 0, List.of(), definition));
    }

    private static boolean tryAdvanceHighReputationContainerVouch(
            ServerLevel level,
            ServerPlayer player,
            Villager currentVillager,
            ForcedDialogueSession session) {
        ContainerSnapshot snapshot = containerSnapshot(session, level);
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || session.definition().trigger() != ForcedDialogueTrigger.CONTAINER_OPENED
                || !ForcedDialogueContainers.isVillagePropertyLootTable(level, snapshot.lootTable())) {
            return false;
        }

        double radius = 12.0D;
        double radiusSqr = radius * radius;
        AABB area = AABB.ofSize(Vec3.atCenterOf(snapshot.pos()), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        Optional<ContainerVouchCandidate> voucher = level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby()).stream()
                .filter(villager -> !session.spokenVillagerIds().contains(villager.getUUID()))
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
        boolean royalty = reputation.level() == VillagerReputationLevel.ROYALTY;
        boolean allowed = royalty || rollChance(level, vouchChance);
        String messageKey = royalty
                ? CONTAINER_OPENED_VOUCH_ROYALTY_MESSAGE_KEY
                : allowed
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

        FORCED_SESSIONS.remove(player.getUUID());
        VillagerConversationService.endForPlayer(player, false);
        ForcedDialogueDefinition definition = forcedInterjectionDefinition(
                allowed ? CONTAINER_OPENED_VOUCH_ALLOW_DEFINITION_ID : CONTAINER_OPENED_VOUCH_DEFINITION_ID,
                line,
                session.definition(),
                allowed ? containerVouchAllowOptions() : containerWitnessInterjectionOptions(session.definition()));
        ForcedDialogueContext interjectionContext = containerWitnessContext(level, villager, player, session);
        if (openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player, session),
                definition.forceCameraTowardsVillager(), definition, interjectionContext)) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    villager.getUUID(),
                    definition,
                    interjectionContext,
                    session.sourceContainerDimension(),
                    session.sourceContainerPos(),
                    session.removedStacks(),
                    level.getGameTime(),
                    -1,
                    "",
                    false,
                    allowed ? containerVouchResponseSessionReplacements(currentVillager, royalty) : Map.of(),
                    appendedParticipantIds(session, villager),
                    appendedSpokenVillagerIds(session, villager),
                    session.stolenItemsResolved(),
                    session.disabledOptionIds(),
                    session.royaltyAggroBypass()));
        } else if (!line.isBlank()) {
            sendDefinitionNotice(player, villager, definition, interjectionContext, line);
        }
        return true;
    }

    private static Map<String, String> containerVouchResponseSessionReplacements(Villager currentVillager, boolean royalty) {
        return Map.of(
                CONTAINER_OPENED_VOUCH_RESPONDER_ID_KEY, currentVillager.getUUID().toString(),
                CONTAINER_OPENED_VOUCH_ROYALTY_KEY, Boolean.toString(royalty));
    }

    private static List<ForcedDialogueOption> containerVouchAllowOptions() {
        return List.of(new ForcedDialogueOption(
                CONTAINER_OPENED_VOUCH_ACCEPT_OPTION_ID,
                LocalizedText.inline("I'll be careful"),
                List.of(),
                0,
                false,
                0.0D,
                true,
                0,
                ForcedDialogueStolenItemReturn.empty(),
                ForcedDialogueItemPayment.empty(),
                VillagerReputationCondition.empty(),
                SocialAttributeCondition.EMPTY,
                List.of(),
                ForcedDialogueFollowUp.empty()));
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
                .filter(definition -> !ForcedDialogueTriggerGates.isChatOutput(definition))
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
                .filter(ForcedDialogueTriggerGates::isChatOutput)
                .anyMatch(definition -> triggerContainerChat(level, player, snapshot, removedCount, removedStacks, definition));
    }

    private static boolean triggerContainerChat(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            int removedCount,
            List<ItemStack> removedStacks,
            ForcedDialogueDefinition definition) {
        if (isContainerTheftCooldownActive(level, player, snapshot, definition)) {
            return false;
        }
        if (!rollChance(level, definition.chance())) {
            return false;
        }
        Villager witness = findWitness(level, player, snapshot.pos(), definition).orElse(null);
        if (witness == null) {
            return false;
        }
        int priorContainerThefts = VillageEventMemory.countForPlayer(
                VillageEventMemory.recentForVillager(level, witness),
                player.getUUID(),
                VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT);
        if (!definition.matchesRecentContainerThefts(priorContainerThefts)) {
            return false;
        }
        if (!definitionMatchesReputation(level, witness, player, definition)) {
            return false;
        }

        ItemStack representativeStack = ForcedDialogueContainers.representativeStack(removedStacks);
        ForcedDialogueContext context = new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(witness).getString(),
                player.getDisplayName().getString(),
                player.getDisplayName().getString(),
                "player",
                BuiltInRegistries.ENTITY_TYPE.getKey(player.getType()).toString(),
                representativeStack.isEmpty() ? "items" : representativeStack.getHoverName().getString(),
                representativeStack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(representativeStack.getItem()).toString(),
                representativeStack.isEmpty() ? removedCount : representativeStack.getCount(),
                representativeStack.isEmpty() ? "items" : ForcedDialogueContainers.stackName(
                        level.getServer(), VillagerLocale.locale(player), representativeStack),
                removedStacks.isEmpty() ? "items" : ForcedDialogueContainers.stackListName(
                        level.getServer(), VillagerLocale.locale(player), removedStacks),
                snapshot.containerName().getString(),
                snapshot.lootTable() == null ? "" : snapshot.lootTable().toString(),
                priorContainerThefts,
                0,
                snapshot.pos().getX(),
                snapshot.pos().getY(),
                snapshot.pos().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player)
        );
        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (!line.isBlank()) {
            broadcastDataDrivenChat(level, witness, player, definition, line, context, Map.of());
            markContainerTheftCooldown(level, player, snapshot, definition);
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
                VillageEventMemory.recentForVillager(level, witness),
                player.getUUID(),
                VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT);
        if (!definition.matchesRecentContainerThefts(priorContainerThefts)) {
            return false;
        }
        if (!definitionMatchesReputation(level, witness, player, definition)) {
            return false;
        }
        if (isContainerTheftCooldownActive(level, player, snapshot, definition)) {
            return false;
        }

        ItemStack representativeStack = ForcedDialogueContainers.representativeStack(removedStacks);
        ForcedDialogueContext context = new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(witness).getString(),
                player.getDisplayName().getString(),
                player.getDisplayName().getString(),
                "player",
                BuiltInRegistries.ENTITY_TYPE.getKey(player.getType()).toString(),
                representativeStack.isEmpty() ? "items" : representativeStack.getHoverName().getString(),
                representativeStack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(representativeStack.getItem()).toString(),
                representativeStack.isEmpty() ? removedCount : representativeStack.getCount(),
                representativeStack.isEmpty() ? "items" : ForcedDialogueContainers.stackName(
                        level.getServer(), VillagerLocale.locale(player), representativeStack),
                removedStacks.isEmpty() ? "items" : ForcedDialogueContainers.stackListName(
                        level.getServer(), VillagerLocale.locale(player), removedStacks),
                snapshot.containerName().getString(),
                snapshot.lootTable() == null ? "" : snapshot.lootTable().toString(),
                priorContainerThefts,
                0,
                snapshot.pos().getX(),
                snapshot.pos().getY(),
                snapshot.pos().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player)
        );
        boolean royaltyAggroBypass = isRoyaltyFor(level, witness, player);
        int reputationDelta = definition.reputationDelta();
        if (definition.trigger() == ForcedDialogueTrigger.CONTAINER_BROKEN) {
            reputationDelta += ForcedDialogueContainers.breakReputationDelta(snapshot);
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
                sendDefinitionNotice(player, witness, definition, context, line);
            }
            if (royaltyAggroBypass) {
                sendRoyaltyAggroBypassNotice(player, witness);
                markContainerTheftCooldown(level, player, snapshot, definition);
                return true;
            }
            VillagerRetaliationHandler.forceAnger(witness, player);
            markContainerTheftCooldown(level, player, snapshot, definition);
            return true;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, witness, definition, context, line);
            }
            markContainerTheftCooldown(level, player, snapshot, definition);
            return true;
        }

        if (openDialogue(
                player,
                witness,
                line,
                forcedOptions(definition, level, witness, player),
                definition.forceCameraTowardsVillager(), definition, context)) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    witness.getUUID(),
                    definition,
                    context,
                    snapshot.dimension(),
                    snapshot.pos(),
                    removedStacks,
                    level.getGameTime(),
                    -1,
                    "",
                    false,
                    Map.of(),
                    List.of(witness.getUUID()),
                    List.of(witness.getUUID()),
                    false,
                    List.of(),
                    royaltyAggroBypass
            ));
            markContainerTheftCooldown(level, player, snapshot, definition);
        } else {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, witness, definition, context, line);
            }
            markContainerTheftCooldown(level, player, snapshot, definition);
        }
        return true;
    }

    private static boolean isContainerTheftCooldownActive(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            ForcedDialogueDefinition definition) {
        if (definition.trigger() != ForcedDialogueTrigger.CONTAINER_THEFT || definition.cooldownTicks() <= 0L) {
            return false;
        }
        long lastRunTime = VillagerEventTriggerSavedData.get(level).lastRunGameTime(containerTheftCooldownKey(player, snapshot, definition));
        return lastRunTime > 0L && level.getGameTime() - lastRunTime < definition.cooldownTicks();
    }

    private static void markContainerTheftCooldown(
            ServerLevel level,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            ForcedDialogueDefinition definition) {
        if (definition.trigger() != ForcedDialogueTrigger.CONTAINER_THEFT || definition.cooldownTicks() <= 0L) {
            return;
        }
        VillagerEventTriggerSavedData.get(level).markRun(containerTheftCooldownKey(player, snapshot, definition), level.getGameTime());
    }

    private static String containerTheftCooldownKey(
            ServerPlayer player,
            ContainerSnapshot snapshot,
            ForcedDialogueDefinition definition) {
        return "forced_container_theft|"
                + definition.id()
                + "|player:" + player.getUUID()
                + "|dimension:" + snapshot.dimension().location()
                + "|pos:" + snapshot.pos().asLong();
    }

    private static boolean triggerPlayerItemProximityChat(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            Optional<TradeItemMatch> tradeItemMatch) {
        if (!rollChance(level, definition.chance())) {
            return false;
        }
        Map<String, String> replacements = playerItemProximityReplacements(definition, player, tradeItemMatch);
        ForcedDialogueContext context = playerItemProximityContext(villager, player, replacements);
        String line = resolvePlayerItemProximityLine(level, villager, definition, context, replacements);
        if (!line.isBlank()) {
            broadcastDataDrivenChat(level, villager, player, definition, line, context, replacements);
        }
        return true;
    }

    private static boolean triggerPlayerItemProximity(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            Optional<TradeItemMatch> tradeItemMatch) {
        if (!rollChance(level, definition.chance())) {
            return false;
        }
        Map<String, String> replacements = playerItemProximityReplacements(definition, player, tradeItemMatch);
        ForcedDialogueContext context = playerItemProximityContext(villager, player, replacements);
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, villager, player.getUUID(), definition.reputationDelta(), villager.blockPosition());
            VillagerGossipHooks.spreadReputation(level, villager, player.getUUID(), definition.reputationDelta());
        }

        String line = resolvePlayerItemProximityLine(level, villager, definition, context, replacements);
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, villager, definition, context, line);
            }
            if (isRoyaltyFor(level, villager, player)) {
                sendRoyaltyAggroBypassNotice(player, villager);
                return true;
            }
            VillagerRetaliationHandler.forceAnger(villager, player);
            return true;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, villager, definition, context, line);
            }
            return true;
        }

        if (openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                definition.forceCameraTowardsVillager(), definition, context)) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    villager.getUUID(),
                    definition,
                    context,
                    level.dimension(),
                    villager.blockPosition().immutable(),
                    List.of(),
                    level.getGameTime(),
                    -1,
                    "",
                    false,
                    Map.of(),
                    List.of(villager.getUUID()),
                    List.of(villager.getUUID()),
                    false,
                    List.of(),
                    isRoyaltyFor(level, villager, player)
            ));
        } else if (!line.isBlank()) {
            sendDefinitionNotice(player, villager, definition, context, line);
        }
        return true;
    }

    private static String resolvePlayerItemProximityLine(
            ServerLevel level,
            Villager villager,
            ForcedDialogueDefinition definition,
            ForcedDialogueContext context,
            Map<String, String> replacements) {
        String previous = LAST_PLAYER_ITEM_PROXIMITY_CHAT_LINES.get(villager.getUUID());
        String selected = "";
        for (int attempt = 0; attempt < PLAYER_ITEM_PROXIMITY_LINE_SELECTION_ATTEMPTS; attempt++) {
            selected = ForcedDialogueResources.resolveTemplate(
                    definition.selectLine(level.getRandom()),
                    context,
                    replacements);
            if (!selected.isBlank() && !selected.equals(previous)) {
                break;
            }
        }
        if (!selected.isBlank()) {
            LAST_PLAYER_ITEM_PROXIMITY_CHAT_LINES.put(villager.getUUID(), selected);
        }
        return selected;
    }

    private static ForcedDialogueContext playerItemProximityContext(
            Villager villager,
            ServerPlayer player,
            Map<String, String> itemReplacements) {
        ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
        String itemName = itemReplacements.getOrDefault("player_item", "");
        String itemId = itemReplacements.getOrDefault("player_item_id", "");
        int itemCount = parseInt(itemReplacements.get("trade_cost_count"), 1);
        String playerName = player.getDisplayName().getString();
        return new ForcedDialogueContext(
                VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                playerName,
                playerName,
                "player",
                targetTypeId == null ? "" : targetTypeId.toString(),
                itemName,
                itemId,
                itemCount,
                itemReplacements.getOrDefault("trade_cost", itemName),
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

    private static Map<String, String> playerItemProximityReplacements(
            ForcedDialogueDefinition definition,
            ServerPlayer player,
            Optional<TradeItemMatch> tradeItemMatch) {
        Map<String, String> replacements = new HashMap<>(definition.playerItemReplacements(player));
        tradeItemMatch.ifPresent(match -> replacements.putAll(match.replacements()));
        return replacements;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }


    private static boolean definitionMatchesReputation(
            ServerLevel level,
            Villager witness,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        if (VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            ReputationSnapshot reputation =
                    VillagerReputationManager.getReputationSnapshot(level, witness, player.getUUID());
            if (!definition.matchesReputation(reputation.value(), reputation.level())) {
                return false;
            }
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, witness);
        return DialogueCondition.matchesAll(context, definition.conditions());
    }

    private static boolean isRoyaltyFor(ServerLevel level, Villager villager, ServerPlayer player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && VillagerReputationManager.isRoyalty(level, villager, player);
    }

    private static void sendRoyaltyAggroBypassNotice(ServerPlayer player, Villager villager) {
        VillagerInteractionService.sendVillagerNotice(player, villager, ROYALTY_AGGRO_BYPASS_MESSAGE_KEY);
    }

    private static boolean triggerRetaliation(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            int priorRetaliations,
            ResourceLocation targetTypeId) {
        if (!rollChance(level, definition.chance())) {
            return false;
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
                villager.blockPosition().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player)
        );
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, villager, player.getUUID(), definition.reputationDelta(), villager.blockPosition());
            VillagerGossipHooks.spreadReputation(level, villager, player.getUUID(), definition.reputationDelta());
        }
        VillageEventMemory.rememberRetaliation(level, villager.blockPosition(), villager, player, villagerName);

        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, villager, definition, context, line);
            }
            VillagerRetaliationHandler.forceAngerSilently(villager, player);
            return true;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, villager, definition, context, line);
            }
            return true;
        }

        if (openDialogue(
                player,
                villager,
                line,
                forcedOptions(definition, level, villager, player),
                definition.forceCameraTowardsVillager(), definition, context)) {
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
            sendDefinitionNotice(player, villager, definition, context, line);
        }
        return true;
    }

    private static boolean triggerLowGutsRally(
            ServerLevel level,
            Villager witness,
            Villager victim,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            ResourceLocation targetTypeId) {
        if (!rollChance(level, definition.chance())) {
            return false;
        }

        String witnessName = VillagerPresetNameRegistry.resolveDisplayName(witness).getString();
        String victimName = VillagerPresetNameRegistry.resolveDisplayName(victim).getString();
        String targetName = player.getDisplayName().getString();
        String targetKind = player.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        Map<String, String> replacements = lowGutsRallyReplacements(victim, victimName);
        ForcedDialogueContext context = new ForcedDialogueContext(
                witnessName,
                player.getDisplayName().getString(),
                targetName,
                targetKind,
                targetTypeId == null ? "" : targetTypeId.toString(),
                victimName,
                "",
                1,
                victimName,
                victimName,
                victimProfessionName(victim),
                "",
                0,
                0,
                victim.blockPosition().getX(),
                victim.blockPosition().getY(),
                victim.blockPosition().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player)
        );
        if (definition.reputationDelta() != 0 && VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            VillagerReputationManager.addWitnessedReputation(level, witness, player.getUUID(), definition.reputationDelta(), victim.blockPosition());
            VillagerGossipHooks.spreadReputation(level, witness, player.getUUID(), definition.reputationDelta());
        }

        String line = ForcedDialogueResources.resolveTemplate(
                definition.selectLine(level.getRandom()),
                context,
                replacements);
        if (definition.aggroImmediately()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, witness, definition, context, line);
            }
            if (isRoyaltyFor(level, witness, player)) {
                sendRoyaltyAggroBypassNotice(player, witness);
                return true;
            }
            VillagerRetaliationHandler.forceAnger(witness, player);
            return true;
        }

        if (!definition.initiateDialogue()) {
            if (!line.isBlank()) {
                sendDefinitionNotice(player, witness, definition, context, line);
            }
            return true;
        }

        boolean royaltyAggroBypass = isRoyaltyFor(level, witness, player);
        if (openDialogue(
                player,
                witness,
                line,
                forcedOptions(definition, level, witness, player),
                definition.forceCameraTowardsVillager(), definition, context)) {
            FORCED_SESSIONS.put(player.getUUID(), new ForcedDialogueSession(
                    witness.getUUID(),
                    definition,
                    context,
                    level.dimension(),
                    victim.blockPosition().immutable(),
                    List.of(),
                    level.getGameTime(),
                    -1,
                    "",
                    false,
                    replacements,
                    List.of(witness.getUUID()),
                    List.of(witness.getUUID()),
                    false,
                    List.of(),
                    royaltyAggroBypass
            ));
        } else if (!line.isBlank()) {
            sendDefinitionNotice(player, witness, definition, context, line);
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
                .filter(ForcedDialogueTriggerGates::isChatOutput)
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
            return false;
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
                villager.blockPosition().getZ(),
                level.getServer(),
                level.getRandom(),
                VillagerLocale.locale(player)
        );
        String line = ForcedDialogueResources.resolveTemplate(definition.selectLine(level.getRandom()), context);
        if (!line.isBlank()) {
            broadcastDataDrivenChat(level, villager, player, definition, line, context, Map.of());
        }
        return true;
    }

    private static void broadcastDataDrivenChat(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ForcedDialogueDefinition definition,
            String line, ForcedDialogueContext context, Map<String, String> replacements) {
        if (player != null && definition.output().lookAtPlayer()) VillagerInWorldDialogueFocusService.requestFocus(villager, player);
        if (!definition.output().exchanges().isEmpty()) {
            if (player == null && definition.output().exchanges().stream().anyMatch(exchange ->
                    exchange.speaker() == com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind.PLAYER)) {
                com.jvn.villagerretaliation.util.DatapackDiagnostics.warnInvalidDialogueCondition(definition.source(), "forced dialogue",
                        "player exchanges require a participating player for this trigger");
                return;
            }
            var exchanges = resolveForcedExchanges(player, villager, definition.output().exchanges(), context, replacements);
            if (player != null) VillagerInteractionService.sendDialogueExchanges(player, villager, exchanges, outputRadius(definition));
            else for (var viewer : level.players()) {
                if (viewer.distanceToSqr(villager) <= outputRadius(definition) * outputRadius(definition))
                    VillagerInteractionService.sendDialogueExchanges(viewer, villager, exchanges, 0);
            }
            return;
        }
        if (line == null || line.isBlank()) return;
        VillagerInteractionService.broadcastForcedVillagerChat(
                level, villager, line, VillagerInteractionService.villagerSpeakerLabel(villager),
                outputRadius(definition));
    }

    private static List<DialogueOptionDefinition> forcedOptions(
            ForcedDialogueDefinition definition, ServerLevel level, Villager villager, ServerPlayer player) {
        return forcedOptions(definition, level, villager, player, null);
    }

    private static List<DialogueOptionDefinition> forcedOptions(
            ForcedDialogueDefinition definition, ServerLevel level, Villager villager,
            ServerPlayer player, ForcedDialogueSession session) {
        return FORCED_SESSIONS.options(definition, level, villager, player, session);
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

    private static Optional<Villager> findLowGutsRallyWitness(
            ServerLevel level,
            Villager victim,
            ServerPlayer player,
            ForcedDialogueDefinition definition) {
        double radius = definition.witnessRadius();
        double radiusSqr = radius * radius;
        AABB area = victim.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby()).stream()
                .filter(villager -> villager != victim)
                .filter(villager -> !PartyService.areInSameParty(villager, player))
                .filter(definition::matchesWitness)
                .filter(villager -> villager.distanceToSqr(player) <= radiusSqr || villager.distanceToSqr(victim) <= radiusSqr)
                .filter(villager -> VillagerInteractionService.canUseForcedInteractionSystem(player, villager))
                .filter(villager -> definitionMatchesReputation(level, villager, player, definition))
                .filter(villager -> !definition.requiresLineOfSight()
                        || villager.hasLineOfSight(player) && villager.hasLineOfSight(victim))
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)));
    }

    private static boolean isEscalatedLowGutsConflict(
            ServerLevel level,
            Villager victim,
            ServerPlayer player,
            double confrontationRadius) {
        long oldestEscalatingEvent = level.getGameTime() - LOW_GUTS_CONFRONTATION_ESCALATION_WINDOW_TICKS;
        boolean recentPlayerViolence = VillageEventMemory.recentForVillage(level, victim).stream()
                .anyMatch(event -> player.getUUID().equals(event.playerId())
                        && event.gameTime() >= oldestEscalatingEvent
                        && (event.tag() == VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER
                        || event.tag() == VillageEventMemory.EventTag.PLAYER_KILLED_VILLAGER
                        || event.tag() == VillageEventMemory.EventTag.VILLAGER_DEATH
                        || event.tag() == VillageEventMemory.EventTag.VILLAGER_RETALIATION_STARTED));
        if (recentPlayerViolence) {
            return true;
        }

        double radius = Math.max(confrontationRadius, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
        AABB area = victim.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby()).stream()
                .anyMatch(villager -> VillagerRetaliationHandler.isHostileTowards(villager, player));
    }

    private static Map<String, String> lowGutsRallyReplacements(Villager victim, String victimName) {
        return Map.of(
                "victim", victimName,
                "victim_name", victimName,
                "victim_profession", victimProfessionName(victim));
    }

    private static String victimProfessionName(Villager victim) {
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(victim.getVillagerData().getProfession());
        return professionId == null ? "villager" : professionId.getPath().replace('_', ' ');
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

    private static void rememberContainerTheft(
            ServerLevel level,
            Villager witness,
            ServerPlayer player,
            ContainerSnapshot snapshot,
            List<ItemStack> removedStacks,
            int removedCount) {
        ItemStack representative = ForcedDialogueContainers.representativeStack(removedStacks);
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
            List<String> disabledOptionIds,
            boolean royaltyAggroBypass) implements ForcedDialogueRuntime.Session {
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
                    List.of(),
                    false);
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
                    List.of(),
                    false);
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
                    List.of(),
                    false);
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
                    List.of(),
                    false);
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
                    List.of(),
                    false);
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
                    List.of(),
                    false);
        }
    }

}
