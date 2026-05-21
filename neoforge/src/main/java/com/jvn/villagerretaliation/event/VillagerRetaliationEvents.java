package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.combat.VillagerPacificationResult;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.interaction.VillagerCombatSurvivalService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.loot.VillagerLootHandler;
import com.jvn.villagerretaliation.loot.WanderingTraderLootHandler;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.toucanlib.util.ToucanHazardAttribution;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerFleeBehaviorHandler;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
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
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationEvents {
    private VillagerRetaliationEvents() {
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
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && event.getNewDamage() > 0.0F) {
            VillagerRecruitmentService.rememberFollowerDamage(villager);
        }
        if (event.getEntity() instanceof Villager villager && event.getSource().getEntity() instanceof Player attacker) {
            VillagerRecruitmentService.stopFollowingIfFollowingAttacker(villager, attacker);
        }
        VillagerRetaliationHandler.onLivingDamage(event);
        WanderingTraderRetaliationHandler.onLivingDamage(event);
        rememberVillageDamageEvent(event);
        if (event.getEntity() instanceof Villager villager && event.getNewDamage() > 0.0F) {
            VillagerConversationService.endForVillager(villager, true);
        }
        if (event.getEntity() instanceof AbstractVillager villager && event.getNewDamage() > 0.0F && villager.level() instanceof ServerLevel level) {
            VillagerAmbientIndicatorService.onVillagerDamaged(level, villager, event.getSource().getEntity());
        }
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        if (shouldCancelVillagerGolemDamage(event.getEntity(), event.getSource().getEntity(), event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return;
        }
        VillagerRetaliationHandler.onLivingDamagePre(event);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            broadcastVillagerDeathMessage(villager, event.getSource());
            VillagerCombatSurvivalService.onVillagerDeath(villager);
            VillagerRecruitmentService.notifyRecruitmentDeath(villager, event.getSource().getEntity());
        }
        VillagerRetaliationHandler.onLivingDeath(event);
        WanderingTraderRetaliationHandler.onLivingDeath(event);
        rememberVillageDeathEvent(event);
        if (event.getEntity() instanceof Villager villager) {
            VillagerConversationService.endForVillager(villager, true);
        }
        if (event.getEntity() instanceof AbstractVillager villager && villager.level() instanceof ServerLevel level) {
            VillagerAmbientIndicatorService.onVillagerKilled(level, villager, event.getSource().getEntity());
        }
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Villager villager) {
            VillagerConversationService.tickVillager(villager);
            VillagerRecruitmentService.onVillagerTickPre(villager);
            if (villager.level() instanceof ServerLevel level) {
                VillagerAmbientIndicatorService.maybeEmitSleepIndicator(level, villager);
            }
        }
        VillagerFleeBehaviorHandler.onEntityTickPre(event);
        VillagerRetaliationHandler.onEntityTickPre(event);
        WanderingTraderRetaliationHandler.onEntityTickPre(event);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VillagerReputationAdvancements.onPlayerTick(player);
        }
        if (event.getEntity() instanceof Villager villager) {
            VillagerConversationService.tickVillager(villager);
            VillagerRecruitmentService.onVillagerTickPost(villager);
            rememberWeatherEventNearVillager(villager);
        }
        clearIronGolemTargetingVillagers(event.getEntity());
        VillagerRetaliationHandler.onEntityTickPost(event);
        WanderingTraderRetaliationHandler.onEntityTickPost(event);
        VillagerFleeBehaviorHandler.onEntityTickPost(event);
        if (event.getEntity() instanceof Villager villager) {
            VillagerCombatSurvivalService.onVillagerTickPost(villager);
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        VillagerRetaliationHandler.onEntityJoinLevel(event);
        if (event.getEntity() instanceof AbstractVillager villager && villager.level() instanceof ServerLevel) {
            VillagerPresetNameRegistry.ensurePresetNameAssigned(villager);
        }
    }

    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof AbstractVillager villager) {
            VillagerReputationNetworking.sendName(player, villager);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getTarget() instanceof Villager && player instanceof ServerPlayer serverPlayer) {
            VillagerReputationAdvancements.onVillagerInteraction(serverPlayer);
        }

        ItemStack interactionStack = player.getItemInHand(event.getHand());
        ItemStack pacifyStack = interactionStack.is(Items.EMERALD) ? interactionStack : player.getOffhandItem();

        if (event.getTarget() instanceof Villager villager && player instanceof ServerPlayer serverPlayer) {
            int requiredEmeralds = VillagerRetaliationRetaliationUtil.pacifyEmeraldCost(villager);
            VillagerPacificationResult pacificationResult =
                    VillagerRetaliationHandler.pacifyWithEmeralds(villager, player, pacifyStack);
            if (pacificationResult.handled()) {
                sendPacificationDialogue(serverPlayer, villager, pacificationResult, requiredEmeralds);
                if (pacificationResult == VillagerPacificationResult.SUCCESS) {
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
                && WanderingTraderRetaliationHandler.tryPacifyWithEmeralds(trader, player, pacifyStack)) {
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
            int requiredEmeralds) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        String text = VillagerDialogueService.selectPacifyLine(
                VillagerInteractionService.createDialogueContext(level, player, villager),
                result,
                requiredEmeralds
        );
        if (!text.isBlank()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, text);
        }
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

        ToucanHazardAttribution.rememberPlayerPlacedVanillaHazard(
                event.getEntity(),
                event.getLevel(),
                event.getPos(),
                event.getFace(),
                event.getItemStack()
        );
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer
                && event.getLevel() instanceof ServerLevel level
                && event.getState().is(BlockTags.BEDS)) {
            VillagerInteractionService.handleSleepingVillagerBedBroken(level, serverPlayer, event.getPos());
        }
    }

    private static void tryGiveHighReputationGift(Villager villager, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || !(villager.level() instanceof ServerLevel level)
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
        VillagerRetaliationHandler.onEntityLeaveLevel(event);
        WanderingTraderRetaliationHandler.onEntityLeaveLevel(event);
        VillagerConversationService.endForEntityLeaving(event.getEntity(), true);
    }

    private static void rememberVillageDamageEvent(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof AbstractVillager villager)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        VillageEventMemory.remember(level, VillageEventMemory.EventTag.VILLAGER_ATTACKED, villager.blockPosition(), villager, attacker);
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.VILLAGE_FIRE, villager.blockPosition(), villager, attacker);
        }
        if (attacker instanceof Enemy && isNight(level)) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.NIGHT_ATTACK, villager.blockPosition(), villager, attacker);
        }
        if (attacker instanceof Raider && isActiveRaidAt(level, attacker)) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.RAID, villager.blockPosition(), villager, attacker);
        }
        if (attacker instanceof Player) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER, villager.blockPosition(), villager, attacker);
        }
    }

    private static void rememberVillageDeathEvent(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        Entity deceased = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (deceased instanceof AbstractVillager villager) {
            VillageEventMemory.remember(level, VillageEventMemory.EventTag.VILLAGER_DEATH, villager.blockPosition(), villager, attacker);
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

    private static void rememberWeatherEventNearVillager(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !level.isThundering()) {
            return;
        }
        if (level.getGameTime() % 200L == Math.floorMod(villager.getUUID().getLeastSignificantBits(), 200L)) {
            VillageEventMemory.remember(level, weatherEventTag(level, villager.blockPosition()), villager.blockPosition(), villager, null);
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
                || !level.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)
                || villager.hasCustomName()) {
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
