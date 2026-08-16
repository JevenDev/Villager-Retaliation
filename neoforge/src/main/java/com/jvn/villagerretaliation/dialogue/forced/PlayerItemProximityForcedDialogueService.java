package com.jvn.villagerretaliation.dialogue.forced;

import com.jvn.villagerretaliation.combat.VillagerWeaponDrawService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.forced.container.ForcedDialogueContainers;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class PlayerItemProximityForcedDialogueService {
    private static final long PROXIMITY_SCAN_INTERVAL_TICKS = 80L;
    private static final long AIM_SCAN_INTERVAL_TICKS = 5L;
    private static final long COOLDOWN_TICKS = 20L * 30L;
    private static final Map<CooldownKey, Long> NEXT_TRIGGER_TICK = new HashMap<>();
    private static final Map<UUID, AimState> ACTIVE_AIMS = new HashMap<>();
    private static final Map<UUID, Boolean> AIM_DELAY_PREFERENCES = new HashMap<>();

    private PlayerItemProximityForcedDialogueService() {
    }

    static void clearRuntimeState() {
        NEXT_TRIGGER_TICK.clear();
        ACTIVE_AIMS.clear();
    }

    static void maybeTrigger(ServerLevel level, Villager villager, Delegate delegate) {
        if (!ForcedDialogueTriggerGates.playerItemProximityEnabled()
                || !villager.isAlive()
                || villager.isBaby()
                || villager.isTrading()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (!TickThrottle.isSpreadTick(villager.getUUID(), gameTime, PROXIMITY_SCAN_INTERVAL_TICKS)) {
            return;
        }

        List<ForcedDialogueDefinition> definitions = ForcedDialogueResources.playerItemProximityCandidates(level.getServer());
        if (definitions.isEmpty()) {
            return;
        }

        List<ForcedDialogueDefinition> witnessDefinitions = new ArrayList<>();
        double maxRadius = 0.0D;
        for (ForcedDialogueDefinition definition : definitions) {
            if (definition.requiresPlayerAimingAtWitness()
                    || !definition.matchesWitness(villager)) {
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
                    && !delegate.hasForcedSession(player)) {
                players.add(player);
            }
        }
        if (players.size() > 1) {
            players.sort(Comparator.comparingDouble(player -> villager.distanceToSqr(player)));
        }

        List<ForcedDialogueDefinition> orderedDefinitions = chatFirst(
                witnessDefinitions,
                ForcedDialogueTriggerGates::isChatOutput);
        for (ServerPlayer player : players) {
            if (tryDefinitions(level, villager, player, orderedDefinitions, gameTime, delegate)) {
                pruneCooldowns(gameTime);
                return;
            }
        }
    }

    static void maybeTriggerAiming(MinecraftServer server, Delegate delegate) {
        AIM_DELAY_PREFERENCES.keySet().removeIf(
                playerId -> server.getPlayerList().getPlayer(playerId) == null);
        if (!ForcedDialogueTriggerGates.playerItemProximityEnabled()) {
            ACTIVE_AIMS.clear();
            return;
        }
        ACTIVE_AIMS.keySet().removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);

        List<ForcedDialogueDefinition> definitions = new ArrayList<>();
        for (ForcedDialogueDefinition definition : ForcedDialogueResources.playerItemProximityCandidates(server)) {
            if (definition.requiresPlayerAimingAtWitness()) {
                definitions.add(definition);
            }
        }
        if (definitions.isEmpty()) {
            ACTIVE_AIMS.clear();
            return;
        }
        List<ForcedDialogueDefinition> orderedDefinitions = chatFirst(
                definitions,
                ForcedDialogueTriggerGates::isChatOutput);

        for (ServerLevel level : server.getAllLevels()) {
            long gameTime = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive()
                        || player.isSpectator()
                        || delegate.hasForcedSession(player)) {
                    clearAim(player.getUUID());
                    continue;
                }
                if (!TickThrottle.isSpreadTick(player.getUUID(), gameTime, AIM_SCAN_INTERVAL_TICKS)) {
                    continue;
                }

                List<ForcedDialogueDefinition> playerDefinitions = new ArrayList<>();
                double maxRadius = 0.0D;
                for (ForcedDialogueDefinition definition : orderedDefinitions) {
                    if (!definition.matchesPlayerItem(player)) {
                        continue;
                    }
                    playerDefinitions.add(definition);
                    maxRadius = Math.max(maxRadius, definition.witnessRadius());
                }
                if (maxRadius <= 0.0D) {
                    clearAim(player.getUUID());
                    continue;
                }

                Optional<Villager> target = aimedAtVillager(player, maxRadius);
                if (target.isEmpty()) {
                    clearAim(player.getUUID());
                    continue;
                }
                Villager villager = target.get();
                if (!villager.isAlive() || villager.isBaby() || villager.isTrading()) {
                    clearAim(player.getUUID());
                    continue;
                }

                List<ForcedDialogueDefinition> targetDefinitions = new ArrayList<>();
                for (ForcedDialogueDefinition definition : playerDefinitions) {
                    if (definition.matchesWitness(villager)) {
                        targetDefinitions.add(definition);
                    }
                }
                if (targetDefinitions.isEmpty()) {
                    clearAim(player.getUUID());
                    continue;
                }
                long continuousAimTicks = continuousAimTicks(player.getUUID(), villager.getUUID(), gameTime);
                if (tryDefinitions(
                        level,
                        villager,
                        player,
                        targetDefinitions,
                        gameTime,
                        true,
                        continuousAimTicks,
                        aimingDialogueDelayEnabled(player),
                        delegate)) {
                    pruneCooldowns(gameTime);
                }
            }
        }
    }

    static boolean tryDefinitions(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            List<ForcedDialogueDefinition> definitions,
            long gameTime,
            Delegate delegate) {
        return tryDefinitions(level, villager, player, definitions, gameTime, false, Long.MAX_VALUE, false, delegate);
    }

    static boolean tryDefinitions(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            List<ForcedDialogueDefinition> definitions,
            long gameTime,
            boolean aimingAlreadyConfirmed,
            long continuousAimTicks,
            boolean applyAimingDialogueDelay,
            Delegate delegate) {
        for (ForcedDialogueDefinition definition : definitions) {
            Optional<TradeItemMatch> tradeItemMatch = definition.requiresHeldTradeItem()
                    ? matchingHeldTradeItem(villager, player, delegate)
                    : Optional.empty();
            if ((definition.requiresHeldTradeItem() && tradeItemMatch.isEmpty())
                    || !definition.matchesPlayerItem(player)
                    || !delegate.matchesReputation(level, villager, player, definition)
                    || (definition.requiresSameParty() && !PartyService.areInSameParty(villager, player))
                    || villager.distanceToSqr(player) > definition.witnessRadius() * definition.witnessRadius()
                    || (definition.requiresLineOfSight() && !villager.hasLineOfSight(player))
                    || (definition.requiresPlayerAimingAtWitness()
                    && !aimingAlreadyConfirmed && !isAimingAtWitness(player, villager, definition.witnessRadius()))) {
                continue;
            }

            if (definition.drawWeaponTicks() > 0) {
                VillagerWeaponDrawService.draw(villager, definition.drawWeaponTicks());
            }
            if (definition.requiresPlayerAimingAtWitness()
                    && applyAimingDialogueDelay
                    && continuousAimTicks < definition.requiredAimDurationTicks()) {
                continue;
            }
            if (!cooldownReady(gameTime, villager.getUUID(), player.getUUID(), definition.id())) {
                continue;
            }

            if (delegate.trigger(level, villager, player, definition, tradeItemMatch)) {
                markCooldownUsed(gameTime, villager.getUUID(), player.getUUID(), definition.id());
                return true;
            }
        }
        return false;
    }

    static long continuousAimTicks(UUID playerId, UUID villagerId, long gameTime) {
        AimState previous = ACTIVE_AIMS.get(playerId);
        if (previous == null
                || !previous.villagerId().equals(villagerId)
                || gameTime < previous.lastObservedTick()
                || gameTime - previous.lastObservedTick() > AIM_SCAN_INTERVAL_TICKS) {
            ACTIVE_AIMS.put(playerId, new AimState(villagerId, gameTime, gameTime));
            return 0L;
        }

        ACTIVE_AIMS.put(playerId, new AimState(villagerId, previous.startedTick(), gameTime));
        return gameTime - previous.startedTick();
    }

    static void clearAim(UUID playerId) {
        ACTIVE_AIMS.remove(playerId);
    }

    static void setAimingDialogueDelayEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return;
        }
        AIM_DELAY_PREFERENCES.put(player.getUUID(), enabled);
        clearAim(player.getUUID());
    }

    static boolean aimingDialogueDelayEnabled(ServerPlayer player) {
        return AIM_DELAY_PREFERENCES.getOrDefault(
                player.getUUID(),
                VillagerRetaliationConfig.ENABLE_WEAPON_AIMING_DIALOGUE_DELAY.get());
    }

    static boolean isAimingAtWitness(ServerPlayer player, Villager witness, double maxDistance) {
        if (witness == null || player == null || player.level() != witness.level()) {
            return false;
        }
        return aimedAtVillager(player, maxDistance)
                .filter(target -> target == witness)
                .isPresent();
    }

    static Optional<Villager> aimedAtVillager(ServerPlayer player, double maxDistance) {
        if (player == null || maxDistance <= 0.0D) {
            return Optional.empty();
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 rayEnd = eye.add(player.getViewVector(1.0F).scale(maxDistance));
        HitResult blockHit = level.clip(new ClipContext(
                eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 visibleEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : rayEnd;

        Entity closestEntity = null;
        double closestDistanceSqr = Double.MAX_VALUE;
        AABB search = player.getBoundingBox().expandTowards(visibleEnd.subtract(eye)).inflate(1.0D);
        for (Entity entity : level.getEntities(player, search, candidate ->
                candidate.isAlive() && candidate.isPickable())) {
            AABB bounds = entity instanceof Villager
                    ? entity.getBoundingBox().inflate(0.1D)
                    : entity.getBoundingBox();
            Optional<Vec3> intersection = bounds.contains(eye) ? Optional.of(eye) : bounds.clip(eye, visibleEnd);
            if (intersection.isEmpty()) {
                continue;
            }
            double distanceSqr = eye.distanceToSqr(intersection.get());
            if (distanceSqr + 1.0E-6D < closestDistanceSqr) {
                closestEntity = entity;
                closestDistanceSqr = distanceSqr;
            }
        }
        return closestEntity instanceof Villager villager ? Optional.of(villager) : Optional.empty();
    }

    private static Optional<TradeItemMatch> matchingHeldTradeItem(
            Villager villager,
            ServerPlayer player,
            Delegate delegate) {
        if (!canReactToHeldTradeItem(villager, player, delegate)) {
            return Optional.empty();
        }

        MinecraftServer server = villager.level().getServer();
        String locale = VillagerLocale.locale(player);
        Optional<TradeItemMatch> mainHandMatch = matchingHeldTradeItem(
                server, locale, villager.getOffers(), player.getMainHandItem(), "main_hand");
        if (mainHandMatch.isPresent()) {
            return mainHandMatch;
        }
        return matchingHeldTradeItem(
                server, locale, villager.getOffers(), player.getOffhandItem(), "off_hand");
    }

    private static boolean canReactToHeldTradeItem(Villager villager, ServerPlayer player, Delegate delegate) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession != VillagerProfession.NONE
                && profession != VillagerProfession.NITWIT
                && !villager.getOffers().isEmpty()
                && delegate.canUseForcedInteractionSystem(player, villager);
    }

    static Optional<TradeItemMatch> matchingHeldTradeItem(
            MinecraftServer server,
            String locale,
            Iterable<MerchantOffer> offers,
            ItemStack heldStack,
            String slot) {
        if (heldStack.isEmpty()) {
            return Optional.empty();
        }
        int offerIndex = 0;
        for (MerchantOffer offer : offers) {
            offerIndex++;
            if (offer.isOutOfStock()) {
                continue;
            }
            ItemStack costA = offer.getCostA();
            if (isHeldTradeCost(heldStack, costA)) {
                return Optional.of(new TradeItemMatch(
                        server, locale, heldStack, costA, offer.getResult(), slot, offerIndex));
            }
            ItemStack costB = offer.getCostB();
            if (isHeldTradeCost(heldStack, costB)) {
                return Optional.of(new TradeItemMatch(
                        server, locale, heldStack, costB, offer.getResult(), slot, offerIndex));
            }
        }
        return Optional.empty();
    }

    private static boolean isHeldTradeCost(ItemStack heldStack, ItemStack costStack) {
        return !costStack.isEmpty() && ItemStack.isSameItem(heldStack, costStack);
    }

    static <T> List<T> chatFirst(List<T> candidates, Predicate<T> isChatOutput) {
        List<T> ordered = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (isChatOutput.test(candidate)) {
                ordered.add(candidate);
            }
        }
        for (T candidate : candidates) {
            if (!isChatOutput.test(candidate)) {
                ordered.add(candidate);
            }
        }
        return List.copyOf(ordered);
    }

    static boolean cooldownReady(
            long gameTime,
            UUID villagerId,
            UUID playerId,
            String definitionId) {
        return gameTime >= NEXT_TRIGGER_TICK.getOrDefault(
                new CooldownKey(villagerId, playerId, definitionId),
                0L);
    }

    static void markCooldownUsed(
            long gameTime,
            UUID villagerId,
            UUID playerId,
            String definitionId) {
        NEXT_TRIGGER_TICK.put(
                new CooldownKey(villagerId, playerId, definitionId),
                gameTime + COOLDOWN_TICKS);
    }

    static void pruneCooldowns(long gameTime) {
        NEXT_TRIGGER_TICK.entrySet().removeIf(entry -> entry.getValue() + COOLDOWN_TICKS < gameTime);
    }

    static int cooldownEntryCount() {
        return NEXT_TRIGGER_TICK.size();
    }

    interface Delegate {
        boolean canUseForcedInteractionSystem(ServerPlayer player, Villager villager);

        boolean hasForcedSession(ServerPlayer player);

        boolean matchesReputation(
                ServerLevel level,
                Villager villager,
                ServerPlayer player,
                ForcedDialogueDefinition definition);

        boolean trigger(
                ServerLevel level,
                Villager villager,
                ServerPlayer player,
                ForcedDialogueDefinition definition,
                Optional<TradeItemMatch> tradeItemMatch);
    }

    record TradeItemMatch(
            MinecraftServer server,
            String locale,
            ItemStack heldStack,
            ItemStack costStack,
            ItemStack resultStack,
            String slot,
            int offerIndex) {
        Map<String, String> replacements() {
            String heldItemName = this.heldStack.getHoverName().getString();
            String heldItemId = BuiltInRegistries.ITEM.getKey(this.heldStack.getItem()).toString();
            String costItemName = this.costStack.getHoverName().getString();
            String costStackName = ForcedDialogueContainers.stackName(this.server, this.locale, this.costStack);
            String resultItemName = this.resultStack.isEmpty()
                    ? "something"
                    : this.resultStack.getHoverName().getString();
            String resultStackName = this.resultStack.isEmpty()
                    ? "something"
                    : ForcedDialogueContainers.stackName(this.server, this.locale, this.resultStack);
            Map<String, String> replacements = new HashMap<>();
            replacements.put("player_item", heldItemName);
            replacements.put("held_item", heldItemName);
            replacements.put("player_item_id", heldItemId);
            replacements.put("held_item_id", heldItemId);
            replacements.put("player_item_slot", this.slot);
            replacements.put("held_item_slot", this.slot);
            replacements.put("player_item_count", Integer.toString(this.heldStack.getCount()));
            replacements.put("held_item_count", Integer.toString(this.heldStack.getCount()));
            replacements.put("trade_cost_item", costItemName);
            replacements.put("trade_cost", costStackName);
            replacements.put("trade_cost_count", Integer.toString(this.costStack.getCount()));
            replacements.put("trade_result", resultItemName);
            replacements.put("trade_item", resultItemName);
            replacements.put("trade_result_stack", resultStackName);
            replacements.put("trade_item_stack", resultStackName);
            replacements.put("trade_result_count", Integer.toString(this.resultStack.isEmpty() ? 0 : this.resultStack.getCount()));
            replacements.put("trade_offer_index", Integer.toString(this.offerIndex));
            return replacements;
        }
    }

    private record CooldownKey(
            UUID villagerId,
            UUID playerId,
            String definitionId) {
    }

    private record AimState(
            UUID villagerId,
            long startedTick,
            long lastObservedTick) {
    }
}
