package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.combat.PacifyPaymentOffer;
import com.jvn.villagerretaliation.combat.VillagerPacificationAttempt;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.combat.VillagerPacificationResult;
import com.jvn.villagerretaliation.combat.VillagerPacifyPaymentResources;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.debug.VillagerRetaliationDebugItems;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.scene.SceneRuntime;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.interaction.VillagerCombatSurvivalService;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.loot.VillagerLootHandler;
import com.jvn.villagerretaliation.loot.WanderingTraderLootHandler;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.party.PartyActionHandler;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartySyncService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationEvents;
import com.jvn.toucanlib.util.ToucanHazardAttribution;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.trade.VillagerTradeMemory;
import com.jvn.villagerretaliation.trade.VillagerTradeUseTracker;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerDataWarmup;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerFleeBehaviorHandler;
import com.jvn.villagerretaliation.villager.VillagerContainerClimbGuard;
import com.jvn.villagerretaliation.villager.VillagerNaturalJobArmor;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillagerRetaliationEvents {
    private static final AtomicBoolean BUILDER_CATALOG_SYNC_DIRTY = new AtomicBoolean();

    private VillagerRetaliationEvents() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        VillagerDataWarmup.warm(event.getServer());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SceneLifecycleIntegration.onPlayerConnection(player);
            VillagerQuestService.clearRuntimeState(player);
            VillagerQuestService.attachPendingPartyQuests(player);
            VillagerReputationNetworking.sendServerConfig(player);
            PartySyncService.sendTo(player);
            PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID())
                    .ifPresent(party -> PartySyncService.syncParty(player.getServer(), party.id()));
            PartyActionHandler.sendPendingInvitation(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID())
                    .ifPresent(party -> PartySyncService.syncPartyWithOfflinePlayer(
                            player.getServer(),
                            party.id(),
                            player.getUUID()));
            VillagerQuestService.clearRuntimeState(player);
            HiredDebugPreviewService.clearRuntimeState(player);
        }
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VillagerQuestService.clearRuntimeState(player);
            PartySyncService.sendTo(player);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        VillagerDataWarmup.clearCaches();
        VillagerTaskNavigationUtil.clearRuntimeState();
        VillagerRetaliationVillagerRules.clearCachedChecks();
        VillagerGossipHooks.clear();
        VillagerReputationManager.clearSyncState();
        VillagerReputationEvents.clearRuntimeState();
        VillagerCombatSurvivalService.clearRuntimeState();
        VillagerDownedService.clearRuntimeState();
        VillagerConversationService.clearRuntimeState();
        VillagerRecruitmentService.clearRuntimeState();
        PartyVillagerContractService.clearRuntimeState();
        HiredVillagerWorkService.clearRuntimeState();
        HiredVillagerIndex.clearRuntimeState();
        HiredJobInventory.clearRuntimeState();
        HiredOreBlockTracker.clearRuntimeState();
        VillagerTradeMemory.clearRuntimeState();
        VillagerSocialGraphService.clearRuntimeState();
        ForcedDialogueService.clearRuntimeState();
        DialogueTreeService.clearRuntimeState();
        VillagerQuestService.clearRuntimeState();
        HiredDebugPreviewService.clearRuntimeState();
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener((barrier, resourceManager, preparationProfiler, reloadProfiler, backgroundExecutor, gameExecutor) ->
                java.util.concurrent.CompletableFuture
                        .runAsync(() -> {
                            VillagerDataWarmup.clearResourceCaches();
                            BUILDER_CATALOG_SYNC_DIRTY.set(true);
                        }, backgroundExecutor)
                        .thenCompose(barrier::wait));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        PartyVillagerContractService.onServerTick(event.getServer());
        SceneRuntime.tick(event.getServer());
        if (!BUILDER_CATALOG_SYNC_DIRTY.compareAndSet(true, false)) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            VillagerReputationNetworking.sendBuilderStructureCatalog(player);
        }
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        VillagerRetaliationHandler.onEntityAttributeModification(event);
        WanderingTraderRetaliationHandler.onEntityAttributeModification(event);
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            VillagerLootHandler.addDrops(villager, event);
        } else if (event.getEntity() instanceof WanderingTrader wanderingTrader) {
            WanderingTraderLootHandler.addDrops(wanderingTrader, event);
        }
        com.jvn.villagerretaliation.party.PartyVillagerDropCollection.markSlainEntityDrops(event);
        EncounterService.onDrops(event);
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof AbstractVillager villager) {
            VillagerEquipmentDurability.hurtArmor(villager, event.getSource(), event.getOriginalDamage());
        }
        if (event.getEntity() instanceof Villager villager && event.getNewDamage() > 0.0F) {
            VillagerRecruitmentService.rememberFollowerDamage(villager);
        }
        if (event.getEntity() instanceof Villager villager && event.getSource().getEntity() instanceof Player attacker) {
            VillagerRecruitmentService.stopFollowingIfFollowingAttacker(villager, attacker);
        }
        VillagerRetaliationHandler.onLivingDamage(event);
        WanderingTraderRetaliationHandler.onLivingDamage(event);
        rememberVillagerAttackLanded(event);
        rememberVillageDamageEvent(event);
        if (event.getEntity() instanceof Villager villager && event.getNewDamage() > 0.0F) {
            VillagerConversationService.endForVillager(villager, true);
        }
        if (event.getEntity() instanceof AbstractVillager villager && event.getNewDamage() > 0.0F && villager.level() instanceof ServerLevel level) {
            VillagerAmbientIndicatorService.onVillagerDamaged(level, villager, event.getSource().getEntity());
            VillagerMoodService.recordVillagerDamaged(level, villager, event.getSource().getEntity());
        }
        VillagerDownedService.onLivingDamagePost(event);
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        if (EncounterService.shouldCancelFriendlyDamage(event.getEntity(), event.getSource().getEntity(), event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return;
        }
        if (shouldCancelVillagerGolemDamage(event.getEntity(), event.getSource().getEntity(), event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return;
        }
        VillagerRetaliationHandler.onLivingDamagePre(event);
    }

    public static void onLivingDamageFinalPre(LivingDamageEvent.Pre event) {
        VillagerDownedService.onLivingDamagePre(event);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        EncounterService.onDeath(event.getEntity());
        SceneLifecycleIntegration.onActorDeath(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            VillagerRetaliationVillagerCombatUtil.resolveAttacker(player, event.getSource())
                    .filter(AbstractVillager.class::isInstance)
                    .map(AbstractVillager.class::cast)
                    .ifPresent(killer -> VillagerAmbientIndicatorService.onPlayerKilled(level, killer, player));
        }
        if (event.getEntity() instanceof Villager villager) {
            broadcastVillagerDeathMessage(villager, event.getSource());
            VillagerCombatSurvivalService.onVillagerDeath(villager);
            VillagerRecruitmentService.notifyRecruitmentDeath(villager, event.getSource().getEntity());
            PartyVillagerContractService.onVillagerDeath(villager);
            VillagerQuestService.onVillagerDeath(villager);
            if (villager.level() instanceof ServerLevel level) {
                HiredVillagerContractService.onVillagerDeath(level, villager);
            }
        }
        VillagerRetaliationHandler.onLivingDeath(event);
        WanderingTraderRetaliationHandler.onLivingDeath(event);
        VillagerQuestService.onEntityKilled(event.getEntity(), event.getSource().getEntity());
        rememberVillageDeathEvent(event);
        if (event.getEntity() instanceof Villager villager) {
            VillagerConversationService.endForVillager(villager, true);
        }
        if (event.getEntity() instanceof AbstractVillager villager && villager.level() instanceof ServerLevel level) {
            VillagerAmbientIndicatorService.onVillagerKilled(level, villager, event.getSource().getEntity());
            VillagerMoodService.recordVillagerDeath(level, villager, event.getSource().getEntity(), VillagerRetaliationConfig.WITNESS_RADIUS.get());
        }
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity instanceof Villager villager) {
            VillagerDownedService.onVillagerTickPre(villager);
            if (VillagerDownedService.isDowned(villager)) {
                return;
            }
            VillagerRecruitmentService.onVillagerTickPre(villager);
            HiredVillagerFocusService.onVillagerTickPre(villager);
            if (villager.level() instanceof ServerLevel level) {
                VillagerAmbientIndicatorService.maybeEmitSleepIndicator(level, villager);
            }
            VillagerFleeBehaviorHandler.onEntityTickPre(event);
            VillagerRetaliationHandler.onEntityTickPre(event);
        } else if (entity instanceof WanderingTrader) {
            WanderingTraderRetaliationHandler.onEntityTickPre(event);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            VillagerReputationAdvancements.onPlayerTick(player);
            VillagerRecruitmentService.onPlayerTick(player);
            VillagerQuestService.onPlayerTick(player);
            HiredDebugPreviewService.onPlayerTick(player);
            return;
        }
        if (entity instanceof Villager villager) {
            if (VillagerDownedService.isDowned(villager)) {
                return;
            }
            VillagerNaturalJobArmor.maybeRoll(villager);
            VillagerConversationService.tickVillager(villager);
            HiredVillagerContractService.onVillagerTickPost(villager);
            if (villager.level() instanceof ServerLevel level) {
                HiredVillagerIndex.update(level, villager);
            }
            VillagerRecruitmentService.onVillagerTickPost(villager);
            HiredVillagerWorkService.onVillagerTickPost(villager);
            if (villager.level() instanceof ServerLevel level) {
                VillagerTaskNavigationUtil.tickVillagerWaterSafety(level, villager);
                VillagerTaskNavigationUtil.tickPathDoors(level, villager);
                VillagerTaskNavigationUtil.tickPathLadders(level, villager);
                VillagerContainerClimbGuard.tick(villager);
            }
            rememberWeatherEventNearVillager(villager);
            if (villager.level() instanceof ServerLevel level) {
                VillagerTradeMemory.ensureProfessionPoolIfNeeded(level, villager);
                ForcedDialogueService.tickSharedForcedDialogueParticipant(level, villager);
                ForcedDialogueService.maybeTriggerTradeRefreshReadyProximity(level, villager);
                ForcedDialogueService.maybeTriggerPlayerItemProximity(level, villager);
            }
            VillagerRetaliationHandler.onEntityTickPost(event);
            VillagerFleeBehaviorHandler.onEntityTickPost(event);
            VillagerCombatSurvivalService.onVillagerTickPost(villager);
            VillagerInventoryAccess.maybeOffloadInventoryOverflow(villager);
            VillagerWalletService.tickWallet(villager);
            VillagerSocialGraphService.onEntityTickPost(event);
            VillagerReputationEvents.onEntityTickPost(event);
            return;
        }
        if (entity instanceof AbstractVillager villager
                && entity instanceof VillagerDataHolder) {
            VillagerNaturalJobArmor.maybeRoll(villager);
            return;
        }
        if (entity instanceof WanderingTrader) {
            WanderingTraderRetaliationHandler.onEntityTickPost(event);
            VillagerReputationEvents.onEntityTickPost(event);
            return;
        }
        if (entity instanceof IronGolem) {
            clearIronGolemTargetingVillagers(entity);
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            EncounterService.onEntityJoin(event.getEntity());
            SceneLifecycleIntegration.onEntityReturn(event.getEntity());
        }
        VillagerNaturalJobArmor.onEntityJoinLevel(event);
        VillagerRetaliationHandler.onEntityJoinLevel(event);
        if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity
                && !event.getLevel().isClientSide()) {
            com.jvn.villagerretaliation.party.PartyVillagerDropCollection.onItemEntityLoaded(itemEntity);
        }
        if (event.getEntity() instanceof Villager villager && !event.getLevel().isClientSide()) {
            VillagerDownedService.onVillagerLoaded(villager);
            PartyVillagerContractService.onVillagerLoaded(villager);
        }
    }

    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getTarget() instanceof AbstractVillager villager) {
            VillagerProfileManager.getOrCreateProfile(player.serverLevel(), villager);
        }
        if (event.getTarget() instanceof Villager villager) {
            VillagerReputationNetworking.sendDownedState(player, villager, VillagerDownedService.isDowned(villager));
        }
        if (VillagerPresetNameRegistry.isVillagerForm(event.getTarget())) {
            VillagerReputationNetworking.sendName(player, event.getTarget());
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && VillagerDownedService.isDowned(villager)) {
            if (player instanceof ServerPlayer serverPlayer) {
                VillagerConversationService.endForVillager(villager, true);
                VillagerInteractionService.sendVillagerNotice(serverPlayer, villager, "interaction.incapacitated");
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (event.getTarget() instanceof Villager && player instanceof ServerPlayer serverPlayer) {
            VillagerReputationAdvancements.onVillagerInteraction(serverPlayer);
        }

        if (event.getTarget() instanceof ServerPlayer target
                && player instanceof ServerPlayer serverPlayer
                && !target.getUUID().equals(serverPlayer.getUUID())) {
            PartyActionHandler.openPlayerMenu(serverPlayer, target);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        ItemStack interactionStack = player.getItemInHand(event.getHand());

        if (event.getTarget() instanceof Villager villager
                && !(player instanceof ServerPlayer)
                && VillagerInteractionService.shouldSuppressClientVanillaInteraction(villager, player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && player instanceof ServerPlayer
                && VillagerRetaliationItems.isClipboard(interactionStack)
                && !HiredStorageClipboardItem.mode(interactionStack).isStorageAssignmentMode()) {
            InteractionResult result = interactionStack.interactLivingEntity(player, villager, event.getHand());
            if (result.consumesAction()) {
                event.setCanceled(true);
                event.setCancellationResult(result);
                return;
            }
        }

        if (event.getTarget() instanceof Villager villager
                && player instanceof ServerPlayer serverPlayer
                && VillagerInteractionService.shouldHandleClipboardInteraction(villager, serverPlayer, event.getHand())) {
            event.setCancellationResult(VillagerInteractionService.handleClipboardVillagerRightClick(villager, serverPlayer));
            event.setCanceled(true);
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && player instanceof ServerPlayer serverPlayer
                && VillagerInteractionService.shouldHandleConstructionBlueprintInteraction(villager, serverPlayer, event.getHand())) {
            event.setCancellationResult(VillagerInteractionService.handleConstructionBlueprintVillagerRightClick(villager, serverPlayer));
            event.setCanceled(true);
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && player instanceof ServerPlayer serverPlayer
                && VillagerInteractionService.shouldHandleItemFilterInteraction(villager, serverPlayer, event.getHand())) {
            event.setCancellationResult(VillagerInteractionService.handleItemFilterVillagerRightClick(villager, serverPlayer));
            event.setCanceled(true);
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && player instanceof ServerPlayer
                && VillagerRetaliationDebugItems.isDebugVillagerTool(interactionStack.getItem())) {
            InteractionResult result = interactionStack.interactLivingEntity(player, villager, event.getHand());
            if (result.consumesAction()) {
                event.setCanceled(true);
                event.setCancellationResult(result);
                return;
            }
        }

        if (event.getTarget() instanceof Villager villager && player instanceof ServerPlayer serverPlayer) {
            ItemStack pacifyStack = selectPacifyPaymentStack(villager, player, interactionStack);
            VillagerPacificationAttempt pacificationAttempt =
                    VillagerRetaliationHandler.pacifyWithPayment(villager, player, pacifyStack);
            if (pacificationAttempt.handled()) {
                sendPacificationDialogue(serverPlayer, villager, pacificationAttempt.result(), pacificationAttempt.payment());
                if (pacificationAttempt.result() == VillagerPacificationResult.SUCCESS) {
                    VillagerReputationAdvancements.onVillagerPacified(serverPlayer);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }

        if (event.getTarget() instanceof Villager villager) {
            tryGiveHighReputationGift(villager, player, event.getHand());
        }

        if (event.getTarget() instanceof Villager villager
                && player instanceof ServerPlayer serverPlayer
                && VillagerInteractionService.shouldHandleInteraction(villager, serverPlayer, event.getHand())) {
            event.setCancellationResult(VillagerInteractionService.handleVillagerRightClick(villager, serverPlayer));
            event.setCanceled(true);
            return;
        }

        if (event.getTarget() instanceof Villager villager
                && VillagerRetaliationHandler.blockTradingIfHostile(villager, player)) {
            if (player instanceof ServerPlayer serverPlayer
                    && villager.level() instanceof ServerLevel level
                    && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                    <= VillagerReputationLevel.HOSTILE.trustRank()) {
                VillagerReputationAdvancements.onTradeRefusedDueToLowReputation(serverPlayer);
            }
            VillagerAmbientIndicatorService.onTradeRefused(villager);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (event.getTarget() instanceof WanderingTrader trader
                && WanderingTraderRetaliationHandler.tryPacifyWithPayment(
                trader,
                player,
                selectPacifyPaymentStack(trader, player, interactionStack))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (event.getTarget() instanceof WanderingTrader trader
                && WanderingTraderRetaliationHandler.blockTradingIfHostile(trader, player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void sendPacificationDialogue(
            ServerPlayer player,
            Villager villager,
            VillagerPacificationResult result,
            PacifyPaymentOffer payment) {
        if (payment == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        String text = VillagerDialogueService.selectPacifyLine(
                VillagerInteractionService.createDialogueContext(level, player, villager),
                result,
                payment
        );
        if (!text.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, text);
        }
    }

    private static ItemStack selectPacifyPaymentStack(AbstractVillager villager, Player player, ItemStack interactionStack) {
        return VillagerPacifyPaymentResources.isEligiblePayment(villager, interactionStack)
                ? interactionStack
                : player.getOffhandItem();
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof Villager villager
                && event.getEntity() instanceof ServerPlayer serverPlayer
                && VillagerInteractionService.shouldHandleSleepingInteraction(villager, serverPlayer, event.getHand())) {
            event.setCancellationResult(VillagerInteractionService.handleSleepingVillagerInteraction(villager, serverPlayer));
            event.setCanceled(true);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer
                && event.getLevel() instanceof ServerLevel level
                && VillagerRetaliationItems.isClipboard(event.getItemStack())) {
            InteractionResult result = HiredStorageClipboardItem.handleRightClickBlock(
                    level,
                    serverPlayer,
                    event.getItemStack(),
                    event.getPos()
            );
            event.setCanceled(true);
            event.setCancellationResult(result.consumesAction() ? result : InteractionResult.SUCCESS);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer serverPlayer && event.getLevel() instanceof ServerLevel level) {
            InteractionResult sleepingResult = VillagerInteractionService.handleSleepingVillagerBedInteraction(
                    level,
                    serverPlayer,
                    event.getPos(),
                    event.getHand()
            );
            if (sleepingResult.consumesAction()) {
                event.setCanceled(true);
                event.setCancellationResult(sleepingResult);
                return;
            }
        }

        if (!event.isCanceled()
                && event.getEntity() instanceof ServerPlayer serverPlayer
                && event.getLevel() instanceof ServerLevel level) {
            BlockState state = level.getBlockState(event.getPos());
            VillagerQuestService.onBlockInteracted(level, serverPlayer, event.getPos(), state);
        }

        ForcedDialogueService.rememberPotentialContainerOpen(event);
        ToucanHazardAttribution.rememberPlayerPlacedVanillaHazard(
                event.getEntity(),
                event.getLevel(),
                event.getPos(),
                event.getFace(),
                event.getItemStack()
        );
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer
                && event.getLevel() instanceof ServerLevel level
                && VillagerRetaliationItems.isClipboard(event.getItemStack())) {
            InteractionResult result = HiredStorageClipboardItem.handleLeftClickBlock(
                    level,
                    serverPlayer,
                    event.getItemStack(),
                    event.getPos()
            );
            if (result.consumesAction()) {
                event.setCanceled(true);
            }
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer
                && event.getLevel() instanceof ServerLevel level
                && event.getState().is(BlockTags.BEDS)) {
            VillagerInteractionService.handleSleepingVillagerBedBroken(level, serverPlayer, event.getPos());
        }
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            HiredPathMemory.onBlockChanged(level, event.getPos());
            if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
                VillagerQuestService.onBlockBroken(level, serverPlayer, event.getPos(), event.getState());
            }
            HiredOreBlockTracker.onBlockBreak(event);
            AssignedStorageService.removeAssignedContainer(level, event.getPos());
        }
        ForcedDialogueService.onContainerBreak(event);
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        HiredPathMemory.onBlockPlace(event);
        if (!event.isCanceled()
                && event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof ServerPlayer serverPlayer) {
            VillagerQuestService.onBlockPlaced(level, serverPlayer, event.getPos(), event.getPlacedBlock());
        }
    }

    private static void tryGiveHighReputationGift(Villager villager, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || !(villager.level() instanceof ServerLevel level)
                || !VillagerRetaliationConfig.ENABLE_HIGH_REPUTATION_GIFTS.get()
                || !villager.isAlive()
                || villager.isBaby()
                || !VillagerReputationManager.canGiveHighReputationGift(level, villager, player)) {
            return;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        ItemStack gift = VillagerGiftPreferences.highReputationReward(level, villager, reputationLevel);
        if (gift.isEmpty()) {
            return;
        }

        ItemStack remainder = gift.copy();
        if (!player.addItem(remainder) && !remainder.isEmpty()) {
            player.drop(remainder, false);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            VillagerInteractionService.sendReceivedItemNotice(serverPlayer, villager, gift);
            VillagerInteractionService.sendHighReputationGiftDialogue(serverPlayer, villager, gift);
        }

        VillagerReputationManager.markHighReputationGiftGiven(level, villager, player);
        VillagerAmbientIndicatorService.onHighReputationGift(villager);
        villager.playSound(SoundEvents.VILLAGER_YES, 0.8F, 0.85F + villager.getRandom().nextFloat() * 0.25F);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                villager.getX(),
                villager.getY() + villager.getBbHeight() + 0.25D,
                villager.getZ(),
                7,
                0.3D,
                0.2D,
                0.3D,
                0.02D);
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Villager villager && !event.getLevel().isClientSide()) {
            VillagerDownedService.onVillagerUnloaded(villager);
            Entity.RemovalReason reason = villager.getRemovalReason();
            if (reason == Entity.RemovalReason.DISCARDED || reason == Entity.RemovalReason.KILLED) {
                PartyVillagerContractService.onVillagerPermanentlyRemoved(villager);
            } else {
                PartyVillagerContractService.onVillagerUnloaded(villager);
            }
        }
        VillagerRetaliationHandler.onEntityLeaveLevel(event);
        WanderingTraderRetaliationHandler.onEntityLeaveLevel(event);
        VillagerSocialGraphService.onEntityLeaveLevel(event.getEntity());
        VillagerConversationService.endForEntityLeaving(event.getEntity(), true);
        if (event.getEntity() instanceof AbstractVillager villager) {
            VillagerTradeUseTracker.forget(villager);
        }
        if (event.getEntity() instanceof Villager villager) {
            HiredVillagerIndex.remove(villager);
            if (villager.level() instanceof ServerLevel level) {
                HiredVillagerWorkService.onVillagerLeaveLevel(level, villager);
            }
            VillagerCombatSurvivalService.onVillagerLeaveLevel(villager);
            HiredJobInventory.clearRuntimeState(villager);
            VillagerTaskNavigationUtil.clearRuntimeState(villager);
            VillagerRetaliationVillagerRules.clearCachedChecks(villager);
            VillagerTradeMemory.clearRuntimeState(villager);
        }
    }

    private static void rememberVillageDamageEvent(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof AbstractVillager villager)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        VillageEventMemory.remember(level, VillageEventMemory.EventTag.VILLAGER_ATTACKED, villager.blockPosition(), villager, attacker);
        recordMoodVillageEvent(level, villager, VillageEventMemory.EventTag.VILLAGER_ATTACKED, attacker);
        if (villager.isBaby()) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.BABY_VILLAGER_ATTACKED, villager.blockPosition(), villager, attacker);
            recordMoodVillageEvent(level, villager, VillageEventMemory.EventTag.BABY_VILLAGER_ATTACKED, attacker);
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.VILLAGE_FIRE, villager.blockPosition(), villager, attacker);
            recordMoodVillageEvent(level, villager, VillageEventMemory.EventTag.VILLAGE_FIRE, attacker);
        }
        if (attacker instanceof Enemy && isNight(level)) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.NIGHT_ATTACK, villager.blockPosition(), villager, attacker);
            recordMoodVillageEvent(level, villager, VillageEventMemory.EventTag.NIGHT_ATTACK, attacker);
        }
        if (attacker instanceof Raider && isActiveRaidAt(level, attacker)) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.RAID, villager.blockPosition(), villager, attacker);
            recordMoodVillageEvent(level, villager, VillageEventMemory.EventTag.RAID, attacker);
        }
        if (attacker instanceof Player) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER, villager.blockPosition(), villager, attacker);
            recordMoodVillageEvent(level, villager, VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER, attacker);
        }
    }

    private static void rememberVillagerAttackLanded(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        VillagerRetaliationVillagerCombatUtil.resolveAttacker(target, event.getSource())
                .filter(AbstractVillager.class::isInstance)
                .map(AbstractVillager.class::cast)
                .filter(attacker -> attacker != target)
                .ifPresent(attacker -> VillagerAmbientIndicatorService.onAttackLanded(level, attacker, target));
    }

    private static void rememberVillageDeathEvent(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        Entity deceased = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (deceased instanceof AbstractVillager villager) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.VILLAGER_DEATH, villager.blockPosition(), villager, attacker);
            rememberWitnessedPlayerVillagerKill(level, villager, event);
        } else if (deceased instanceof IronGolem golem) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.GOLEM_KILLED, golem.blockPosition(), golem, attacker);
        } else if (deceased instanceof Enemy) {
            if (isNight(level)) {
                VillageEventMemory.remember(level, VillageEventMemory.EventTag.NIGHT_ATTACK, deceased.blockPosition(), deceased, attacker);
            }
            if (attacker instanceof IronGolem) {
                VillageEventMemory.remember(level, VillageEventMemory.EventTag.IRON_GOLEM_DEFEATED_MOB, deceased.blockPosition(), deceased, attacker);
            } else if (attacker instanceof Player) {
                VillageEventMemory.remember(level, VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE, deceased.blockPosition(), deceased, attacker);
                if (deceased instanceof Raider && isActiveRaidAt(level, deceased)) {
                    VillageEventMemory.remember(level, VillageEventMemory.EventTag.RAID, deceased.blockPosition(), deceased, attacker);
                    VillageEventMemory.remember(level, VillageEventMemory.EventTag.PLAYER_DEFENDED_RAID, deceased.blockPosition(), deceased, attacker);
                }
            }
        }
    }

    private static void rememberWitnessedPlayerVillagerKill(
            ServerLevel level,
            AbstractVillager killed,
            LivingDeathEvent event) {
        Player player = playerResponsibleForVillagerDeath(killed, event);
        if (player == null) {
            return;
        }
        AABB area = killed.getBoundingBox().inflate(VillagerRetaliationConfig.WITNESS_RADIUS.get());
        for (AbstractVillager witness : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            if (witness == killed || !witness.isAlive()) {
                continue;
            }
            if (VillagerRetaliationConfig.VANILLA_GOSSIP_REQUIRES_LINE_OF_SIGHT.get()
                    && !witness.hasLineOfSight(killed)) {
                continue;
            }
            VillageEventMemory.rememberPlayerKilledVillager(
                    level,
                    killed.blockPosition(),
                    witness,
                    player,
                    VillagerPresetNameRegistry.resolveDisplayName(killed).getString());
        }
    }

    private static Player playerResponsibleForVillagerDeath(AbstractVillager killed, LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            return player;
        }
        if (ToucanHazardAttribution.resolveVanillaHazardOwner(killed, event.getSource()).orElse(null) instanceof Player player) {
            return player;
        }
        return killed.getKillCredit() instanceof Player player ? player : null;
    }

    private static void rememberWeatherEventNearVillager(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !level.isThundering()) {
            return;
        }
        if (TickThrottle.isSpreadTick(villager.getUUID(), level.getGameTime(), 200L)) {
            VillageEventMemory.EventTag weatherTag = weatherEventTag(level, villager.blockPosition());
            if (VillageEventMemory.remember(level, weatherTag, villager.blockPosition(), villager, null)) {
                VillagerMoodService.recordVillageEvent(level, villager, weatherTag, null);
            }
        }
    }

    private static void recordMoodVillageEvent(ServerLevel level, AbstractVillager villager, VillageEventMemory.EventTag tag, Entity source) {
        if (villager instanceof Villager villageResident) {
            VillagerMoodService.recordVillageEvent(level, villageResident, tag, source);
        }
    }

    private static VillageEventMemory.EventTag weatherEventTag(ServerLevel level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.biome.Biome biome = level.getBiome(pos).value();
        if (!biome.hasPrecipitation()) {
            return VillageEventMemory.EventTag.SANDSTORM;
        }
        if (biome.coldEnoughToSnow(pos)) {
            return VillageEventMemory.EventTag.SNOWSTORM;
        }
        return VillageEventMemory.EventTag.THUNDERSTORM;
    }

    private static boolean isNight(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return time >= 12542L && time < 23460L;
    }

    private static boolean isActiveRaidAt(ServerLevel level, Entity entity) {
        Raid raid = level.getRaidAt(entity.blockPosition());
        return raid != null && raid.isActive() && !raid.isVictory() && !raid.isLoss();
    }

    private static void broadcastVillagerDeathMessage(Villager villager, DamageSource source) {
        if (!(villager.level() instanceof ServerLevel level)
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_DEATH_MESSAGES.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            return;
        }

        level.getServer().getPlayerList().broadcastSystemMessage(villagerDeathMessage(villager, source), false);
    }

    private static Component villagerDeathMessage(Villager villager, DamageSource source) {
        Component villagerName = VillagerPresetNameRegistry.resolveDisplayName(villager);
        Player hazardOwner = ToucanHazardAttribution.resolveVanillaHazardOwner(villager, source)
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .orElse(null);
        if (hazardOwner != null) {
            return attributedHazardDeathMessage(villagerName, hazardOwner, source);
        }

        String messageId = "death.attack." + source.getMsgId();
        Entity attacker = source.getEntity();
        if (attacker == null || attacker == villager) {
            return Component.translatable(messageId, villagerName);
        }

        return Component.translatable(messageId, villagerName, attacker.getDisplayName());
    }

    private static Component attributedHazardDeathMessage(Component villagerName, Player player, DamageSource source) {
        return Component.literal("")
                .append(villagerName)
                .append(" ")
                .append(attributedHazardDeathPhrase(source))
                .append(" by ")
                .append(player.getDisplayName())
                .append(" using ")
                .append(attributedHazardTool(source));
    }

    private static String attributedHazardDeathPhrase(DamageSource source) {
        String messageId = source.getMsgId();
        if (messageId.equals("lava")
                || messageId.equals("inFire")
                || messageId.equals("onFire")
                || messageId.equals("hotFloor")
                || messageId.equals("fireball")
                || messageId.equals("unattributedFireball")) {
            return "burned to death";
        }
        return "died";
    }

    private static String attributedHazardTool(DamageSource source) {
        String messageId = source.getMsgId();
        if (messageId.equals("lava")) {
            return "a lava bucket";
        }
        if (messageId.equals("fireball") || messageId.equals("unattributedFireball")) {
            return "a fire charge";
        }
        if (messageId.equals("inFire") || messageId.equals("onFire")) {
            return "flint and steel";
        }
        return "a hazard";
    }

    private static boolean shouldCancelVillagerGolemDamage(Entity victim, Entity attacker, Entity directAttacker) {
        return VillagerRetaliationVillagerCombatUtil.isVillagerGolemConflict(victim, attacker)
                || VillagerRetaliationVillagerCombatUtil.isVillagerGolemConflict(victim, directAttacker);
    }

    private static void clearIronGolemTargetingVillagers(Entity entity) {
        if (!(entity instanceof IronGolem ironGolem)) {
            return;
        }

        LivingEntity target = ironGolem.getTarget();
        if (target == null || !VillagerRetaliationVillagerCombatUtil.isVillagerGolemConflict(ironGolem, target)) {
            return;
        }

        ironGolem.setTarget(null);
        ironGolem.setLastHurtByMob(null);
        ironGolem.setPersistentAngerTarget(null);
        ironGolem.stopBeingAngry();
    }
}
