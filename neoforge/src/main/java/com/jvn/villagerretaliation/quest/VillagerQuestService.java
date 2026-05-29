package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerQuestService {
    private static final int QUEST_PROGRESS_SCAN_INTERVAL_TICKS = 20;
    private static final int APPROXIMATE_COORDINATE_STEP = 50;
    private static final long QUEST_STORY_HINT_TICKS = 20L * 60L * 60L * 6L;

    private VillagerQuestService() {
    }

    public static boolean matchesState(DialogueContext context, ResourceLocation questId, Set<String> states) {
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return false;
        }
        if (states == null || states.isEmpty()) {
            return true;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), questId);
        for (String state : states) {
            if (matchesState(context, definition, progress, state)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<VillagerDialogueService.DialogueResult> handleDialogueOption(
            DialogueContext context,
            DialogueOptionDefinition option) {
        DialogueQuestAction questAction = option.questAction();
        if (questAction.isEmpty()) {
            return Optional.empty();
        }

        return performAction(context, questAction.questId(), questAction.action())
                .map(QuestActionOutcome::dialogueResult);
    }

    public static Optional<QuestActionOutcome> performAction(
            DialogueContext context,
            ResourceLocation questId,
            DialogueQuestAction.Action action) {
        if (questId == null || action == null || action == DialogueQuestAction.Action.NONE) {
            return Optional.empty();
        }

        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return Optional.of(result(
                    "missing",
                    "quest_missing_" + questId,
                    "I cannot find the notes for that quest.",
                    Map.of()));
        }

        return Optional.of(switch (action) {
            case START -> startQuest(context, definition);
            case REMIND -> remindQuest(context, definition);
            case TURN_IN -> turnInQuest(context, definition);
            case ABANDON -> abandonQuest(context, definition);
            case NONE -> result("none", "quest_no_action", "", Map.of());
        });
    }

    public static Map<String, String> replacementsFor(DialogueContext context, ResourceLocation questId) {
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return Map.of();
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), questId);
        return replacements(context, definition, progress);
    }


    public static void onPlayerTick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || player.tickCount % QUEST_PROGRESS_SCAN_INTERVAL_TICKS != 0
                || !player.isAlive()
                || player.isSpectator()) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        boolean changed = false;
        boolean progressNotice = false;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (definition.target().hasProofItem() && hasRequiredProof(player, definition) && progress.markHasProof()) {
                changed = true;
                progressNotice = true;
                sendQuestProgressNotification(
                        player,
                        definition,
                        progress,
                        "quest.updated",
                        "Quest updated: {quest}");
            }
            if (!progress.visitedTarget() && isAtQuestTarget(level, player.blockPosition(), definition, progress) && progress.markVisitedTarget()) {
                changed = true;
                progressNotice = true;
                sendQuestProgressNotification(
                        player,
                        definition,
                        progress,
                        "quest.location_reached",
                        "Quest location reached: {quest}");
            }
        }
        if (changed) {
            data.setDirty();
            sendTrackerSync(player, progressNotice);
        } else if (player.tickCount % (QUEST_PROGRESS_SCAN_INTERVAL_TICKS * 2) == 0) {
            sendTrackerSync(player, false);
        }
    }

    public static boolean isReadyToTurnIn(DialogueContext context, ResourceLocation questId) {
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return false;
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), questId);
        return isReadyToTurnIn(context, definition, progress);
    }

    private static QuestActionOutcome startQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return matchesVillagerLock(context, definition, progress)
                    ? remindQuest(context, definition)
                    : result(
                            "locked_to_villager",
                            lineId(definition, "unavailable"),
                            startBlockedLine(context, definition, progress),
                            replacements(context, definition, progress));
        }
        if (!canStart(context, definition, progress)) {
            return result(
                    startBlockedStatus(definition, progress),
                    lineId(definition, "unavailable"),
                    startBlockedLine(context, definition, progress),
                    replacements(context, definition, progress));
        }

        LocatedTarget target = locateTarget(context.level(), context.villager().blockPosition(), definition).orElse(null);
        if (target == null) {
            return result(
                    "locate_failed",
                    lineId(definition, "locate_failed"),
                    definition.dialogue().selectLocateFailed(context.random()),
                    replacements(context, definition, progress));
        }

        VillagerQuestSavedData.QuestProgress started = data.getOrCreate(context.player().getUUID(), definition.id());
        started.start(context.villager().getUUID(), context.level().dimension(), target.pos(), context.level().getGameTime());
        if (definition.target().hasProofItem() && hasRequiredProof(context.player(), definition)) {
            started.markHasProof();
        }
        data.setDirty();
        rememberQuestStoryHint(context, definition, target.pos());
        sendQuestNotification(context, "quest.started", definition, started, "Quest started: {quest}");
        sendTrackerSync(context.player(), true);

        return result(
                "started",
                lineId(definition, "start"),
                definition.dialogue().selectStart(context.random()),
                replacements(context, definition, started));
    }

    private static QuestActionOutcome remindQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }
        return result(
                "reminder",
                lineId(definition, "reminder"),
                definition.dialogue().selectReminder(context.random()),
                replacements(context, definition, progress));
    }

    private static QuestActionOutcome turnInQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }
        if (!progress.visitedTarget()) {
            return result(
                    "missing_target",
                    lineId(definition, "missing_target"),
                    definition.dialogue().selectMissingTarget(context.random()),
                    replacements(context, definition, progress));
        }
        if (!hasRequiredProof(context.player(), definition)) {
            return result(
                    "missing_proof",
                    lineId(definition, "missing_proof"),
                    definition.dialogue().selectMissingProof(context.random()),
                    replacements(context, definition, progress));
        }

        progress.markHasProof();
        progress.complete(context.level().getGameTime(), definition.rules().consumeOnCompletion());
        data.setDirty();
        awardRewards(context, definition);
        sendQuestNotification(context, "quest.completed", definition, progress, "Quest completed: {quest}");
        sendTrackerSync(context.player(), true);

        return result(
                "completed",
                lineId(definition, "turn_in"),
                definition.dialogue().selectTurnIn(context.random()),
                replacements(context, definition, progress));
    }

    private static QuestActionOutcome abandonQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }

        boolean consume = definition.rules().consumeOnAbandonment()
                || definition.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER;
        progress.abandon(context.level().getGameTime(), consume);
        data.setDirty();
        sendQuestNotification(context, "quest.abandoned", definition, progress, "Quest abandoned: {quest}");
        sendTrackerSync(context.player(), true);
        String status = consume
                ? "abandoned_forever"
                : definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                        ? "abandoned_cooldown"
                        : "abandoned";
        return result(
                status,
                lineId(definition, "abandoned"),
                consume
                        ? "I will close my notes on that journey."
                        : "I will fold the map away for now.",
                replacements(context, definition, progress));
    }

    private static boolean canStart(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!definition.offer().matches(context)) {
            return false;
        }
        if (progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED) {
            return withinStartLimit(definition, progress) && withinCompletionLimit(definition, progress);
        }
        if (!definition.rules().crossVillagerCompatible()
                && progress.startedVillagerId() != null
                && !progress.startedVillagerId().equals(context.villager().getUUID())) {
            return false;
        }
        if (!withinStartLimit(definition, progress) || !withinCompletionLimit(definition, progress)) {
            return false;
        }
        return switch (progress.state()) {
            case ACTIVE, CONSUMED -> false;
            case COMPLETED -> definition.rules().repeatable()
                    && cooldownElapsed(context.level().getGameTime(), progress.completedGameTime(), definition.rules().completionCooldownTicks());
            case ABANDONED -> switch (definition.rules().abandonment()) {
                case REMOVE_FOREVER -> false;
                case ALLOW_REPICKUP -> true;
                case COOLDOWN -> cooldownElapsed(
                        context.level().getGameTime(),
                        progress.abandonedGameTime(),
                        definition.rules().abandonmentCooldownTicks());
            };
            case NOT_STARTED -> true;
        };
    }

    private static boolean withinStartLimit(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        int maxStarts = definition.rules().maxStarts();
        return maxStarts <= 0 || progress == null || progress.startCount() < maxStarts;
    }

    private static boolean withinCompletionLimit(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        int maxCompletions = definition.rules().maxCompletions();
        return maxCompletions <= 0 || progress == null || progress.completionCount() < maxCompletions;
    }

    private static boolean cooldownElapsed(long gameTime, long eventTime, long cooldownTicks) {
        return cooldownTicks <= 0L || eventTime <= 0L || gameTime - eventTime >= cooldownTicks;
    }

    private static boolean matchesVillagerLock(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return progress == null
                || !definition.rules().lockedToVillager()
                || progress.startedVillagerId() == null
                || progress.startedVillagerId().equals(context.villager().getUUID());
    }

    private static String startBlockedStatus(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null) {
            return "unavailable";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
            return "consumed";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return "active";
        }
        if (!withinCompletionLimit(definition, progress)
                || (progress.completionCount() > 0 && !definition.rules().repeatable())) {
            return "already_completed";
        }
        if (!withinStartLimit(definition, progress)) {
            return "start_limit";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER) {
            return "abandoned_forever";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN) {
            return "abandoned_cooldown";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                && definition.rules().completionCooldownTicks() > 0L) {
            return "completion_cooldown";
        }
        return "unavailable";
    }

    private static String startBlockedLine(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && progress.completionCount() > 0) {
            return definition.dialogue().selectAlreadyCompleted(context.random());
        }
        if (progress != null
                && progress.startedVillagerId() != null
                && !definition.rules().crossVillagerCompatible()
                && !progress.startedVillagerId().equals(context.villager().getUUID())) {
            return "That quest belongs to another villager's ledger.";
        }
        if (progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                && !cooldownElapsed(context.level().getGameTime(), progress.abandonedGameTime(), definition.rules().abandonmentCooldownTicks())) {
            return "Give that path a little time before we unfold the map again.";
        }
        return definition.dialogue().selectUnavailable(context.random());
    }

    private static boolean matchesState(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        boolean completed = progress != null && progress.completionCount() > 0;
        boolean active = progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && matchesVillagerLock(context, definition, progress);
        boolean ready = active && isReadyToTurnIn(context, definition, progress);
        boolean notStarted = progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED;
        boolean abandoned = progress != null && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED;
        boolean consumed = progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED;
        return switch (normalized) {
            case "available" -> canStart(context, definition, progress);
            case "not_started", "locked" -> notStarted;
            case "active", "started" -> active;
            case "in_progress", "incomplete" -> active && !ready;
            case "ready", "turn_in", "turnin", "completeable", "completable" -> ready;
            case "completed", "complete" -> completed;
            case "abandoned", "dropped" -> abandoned;
            case "consumed", "removed", "removed_forever" -> consumed;
            case "unavailable" -> !canStart(context, definition, progress) && !active;
            case "not_completed" -> !completed;
            default -> false;
        };
    }

    private static boolean isReadyToTurnIn(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && progress.visitedTarget()
                && hasRequiredProof(context.player(), definition);
    }

    private static Optional<LocatedTarget> locateTarget(ServerLevel level, BlockPos origin, QuestDefinition definition) {
        if (!definition.target().hasStructureTarget() || !level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return Optional.empty();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, definition.target().structure());
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return Optional.empty();
        }

        Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
                level,
                HolderSet.direct(holder.get()),
                origin,
                definition.target().searchRadius(),
                false
        );
        return nearest == null ? Optional.empty() : Optional.of(new LocatedTarget(nearest.getFirst()));
    }

    private static boolean isAtQuestTarget(
            ServerLevel level,
            BlockPos playerPos,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress.targetDimension() == null
                || progress.targetPos() == null
                || level.dimension() != progress.targetDimension()
                || !definition.target().hasStructureTarget()) {
            return false;
        }
        double targetTolerance = Math.max(512.0D, (double) definition.target().discoveryRadius() * 4.0D);
        if (playerPos.distSqr(progress.targetPos()) > targetTolerance * targetTolerance) {
            return false;
        }

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, definition.target().structure());
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return false;
        }

        StructureStart start = level.structureManager().getStructureWithPieceAt(playerPos, HolderSet.direct(holder.get()));
        if (!start.isValid()) {
            return false;
        }
        if (definition.target().pieces().isEmpty()) {
            return true;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (!piece.getBoundingBox().isInside(playerPos)) {
                continue;
            }
            if (matchesStructurePiece(piece, definition.target().pieces())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStructurePiece(StructurePiece piece, List<String> expectedPieces) {
        String pieceDescription = piece instanceof PoolElementStructurePiece poolPiece
                ? poolPiece.getElement().toString()
                : piece.toString();
        for (String expectedPiece : expectedPieces) {
            if (!expectedPiece.isBlank() && pieceDescription.contains(expectedPiece)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRequiredProof(ServerPlayer player, QuestDefinition definition) {
        if (!definition.target().hasProofItem()) {
            return true;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(definition.target().proofItem());
        return item.isPresent() && player.getInventory().contains(new ItemStack(item.get()));
    }

    private static void awardRewards(DialogueContext context, QuestDefinition definition) {
        QuestDefinition.Rewards rewards = definition.rewards();
        if (rewards.experience() > 0) {
            context.player().giveExperiencePoints(rewards.experience());
        }
        if (rewards.reputation() != 0) {
            VillagerReputationManager.addDialogueReputation(context.level(), context.villager(), context.player(), rewards.reputation());
        }
        if (rewards.gossipReputation() != 0) {
            VillagerGossipHooks.spreadReputation(context.level(), context.villager(), context.player().getUUID(), rewards.gossipReputation());
        }
        if (rewards.memoryEvent() != null) {
            VillageEventMemory.remember(context.level(), rewards.memoryEvent(), context.villager().blockPosition(), context.villager(), context.player());
        }
        awardLoot(context, rewards.lootTable());
        context.villager().playSound(SoundEvents.PLAYER_LEVELUP, 0.55F, 1.1F);
    }

    private static void awardLoot(DialogueContext context, ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return;
        }
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
        LootTable table = context.level().getServer().reloadableRegistries().getLootTable(lootTableKey);
        if (table == LootTable.EMPTY) {
            return;
        }

        LootParams params = new LootParams.Builder(context.level())
                .withLuck(context.player().getLuck())
                .create(LootContextParamSets.EMPTY);
        for (ItemStack stack : table.getRandomItems(params, context.random())) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack reward = stack.copy();
            ItemStack noticeStack = reward.copy();
            if (!context.player().addItem(reward) && !reward.isEmpty()) {
                context.player().drop(reward, false);
            }
            VillagerInteractionService.sendReceivedItemNotice(context.player(), context.villager(), noticeStack);
        }
    }

    private static void rememberQuestStoryHint(DialogueContext context, QuestDefinition definition, BlockPos targetPos) {
        if (!definition.target().hasStructureTarget()) {
            return;
        }
        VillagerInteractionTracker.rememberStoryHint(
                context.level(),
                context.villager(),
                context.player(),
                VillagerInteractionTracker.StoryHintKind.STRUCTURE,
                definition.target().structure(),
                targetName(definition),
                targetPos,
                context.level().getGameTime() + QUEST_STORY_HINT_TICKS,
                definition.target().discoveryRadius()
        );
    }

    private static void sendQuestNotification(
            DialogueContext context,
            String trigger,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String fallbackText) {
        Map<String, String> replacements = replacements(context, definition, progress);
        VillagerNotifications.sendHud(
                context.player(),
                context.level(),
                context.villager(),
                trigger,
                replacements,
                VillagerDialogueResources.resolveTemplate(fallbackText, replacements),
                VillagerReputationNoticeKind.QUEST
        );
    }

    private static void sendQuestProgressNotification(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String trigger,
            String fallbackText) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Map<String, String> replacements = trackerReplacements(player, definition, progress);
        String fallback = VillagerDialogueResources.resolveTemplate(fallbackText, replacements);
        Villager villager = startedVillager(level, progress);
        if (villager == null) {
            VillagerReputationNetworking.sendNotice(player, fallback, VillagerReputationNoticeKind.QUEST);
            return;
        }
        VillagerNotifications.sendHud(
                player,
                level,
                villager,
                trigger,
                replacements,
                fallback,
                VillagerReputationNoticeKind.QUEST
        );
    }

    private static Villager startedVillager(ServerLevel level, VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.startedVillagerId() == null) {
            return null;
        }
        Entity entity = level.getEntity(progress.startedVillagerId());
        return entity instanceof Villager villager ? villager : null;
    }

    private static void sendTrackerSync(ServerPlayer player, boolean flash) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        List<Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress>> active =
                new ArrayList<>(data.activeProgress(player.getUUID()));
        active.sort(Comparator.comparingLong(entry -> entry.getValue().startedGameTime()));

        List<QuestTrackerSyncPayload.Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : active) {
            if (entries.size() >= QuestTrackerSyncPayload.MAX_ENTRIES) {
                break;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition != null) {
                entries.add(trackerEntry(player, definition, entry.getValue()));
            }
        }
        PacketDistributor.sendToPlayer(player, new QuestTrackerSyncPayload(entries, flash));
    }

    private static QuestTrackerSyncPayload.Entry trackerEntry(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        String stepKey = trackerStepKey(player, definition, progress);
        Map<String, String> replacements = trackerReplacements(player, definition, progress);
        QuestDefinition.Step fallback = new QuestDefinition.Step(
                trackerFallbackText(stepKey),
                true,
                trackerFallbackProgress(stepKey),
                Map.of()
        );
        QuestDefinition.Step step = definition.tracker().step(stepKey, fallback);
        boolean configuredStep = definition.tracker().steps().containsKey(stepKey);
        float progressValue = configuredStep && step.progress() > 0.0F ? step.progress() : trackerFallbackProgress(stepKey);
        boolean showProgress = configuredStep ? step.showProgress() : true;
        String title = definition.tracker().title().isBlank() ? definition.title() : definition.tracker().title();
        return new QuestTrackerSyncPayload.Entry(
                definition.id().toString(),
                VillagerDialogueResources.resolveTemplate(title, replacements),
                VillagerDialogueResources.resolveTemplate(step.text(), replacements),
                metadataText(definition.tracker().metadata(), step.metadata(), replacements),
                Mth.clamp(progressValue, 0.0F, 1.0F),
                showProgress
        );
    }

    private static String trackerStepKey(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || !progress.visitedTarget()) {
            return "travel";
        }
        if (definition.target().hasProofItem() && !hasRequiredProof(player, definition)) {
            return "proof";
        }
        return "return";
    }

    private static String trackerFallbackText(String stepKey) {
        return switch (stepKey) {
            case "proof" -> "Obtain {proof_item} as proof of the journey.";
            case "return" -> "Return to the villager who gave you the quest.";
            default -> "Reach the center of {target} near {target_x}, {target_z}.";
        };
    }

    private static float trackerFallbackProgress(String stepKey) {
        return switch (stepKey) {
            case "proof" -> 0.66F;
            case "return" -> 1.0F;
            default -> 0.25F;
        };
    }

    private static Map<String, String> trackerReplacements(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest", definition.title());
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", proofItemName(definition));
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof(player, definition) ? "yes" : "no");

        BlockPos targetPos = progress == null ? null : progress.targetPos();
        if (targetPos != null) {
            values.put("target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("direction", directionPhrase(player.blockPosition(), targetPos));
            values.put("distance", Integer.toString(roundDistance(player.blockPosition(), targetPos)));
        } else {
            values.put("target_x", "unknown");
            values.put("target_z", "unknown");
            values.put("direction", "somewhere beyond the map");
            values.put("distance", "unknown");
        }
        return Map.copyOf(values);
    }

    private static String metadataText(
            Map<String, String> questMetadata,
            Map<String, String> stepMetadata,
            Map<String, String> replacements) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (questMetadata != null) {
            merged.putAll(questMetadata);
        }
        if (stepMetadata != null) {
            merged.putAll(stepMetadata);
        }
        if (merged.isEmpty()) {
            return "";
        }
        return merged.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> VillagerDialogueResources.resolveTemplate(value, replacements))
                .reduce((left, right) -> left + " · " + right)
                .orElse("");
    }

    private static QuestActionOutcome result(
            String status,
            String lineId,
            String template,
            Map<String, String> replacements) {
        String text = VillagerDialogueResources.resolveTemplate(template, replacements);
        return new QuestActionOutcome(
                status,
                lineId,
                text,
                replacements
        );
    }

    private static Map<String, String> replacements(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest", definition.title());
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", proofItemName(definition));
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof(context.player(), definition) ? "yes" : "no");

        BlockPos targetPos = progress == null ? null : progress.targetPos();
        if (targetPos != null) {
            values.put("target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("direction", directionPhrase(context.villager().blockPosition(), targetPos));
            values.put("distance", Integer.toString(roundDistance(context.villager().blockPosition(), targetPos)));
        } else {
            values.put("target_x", "unknown");
            values.put("target_z", "unknown");
            values.put("direction", "somewhere beyond my maps");
            values.put("distance", "unknown");
        }
        return Map.copyOf(values);
    }

    private static String targetName(QuestDefinition definition) {
        return definition.target().structure() == null
                ? "the target"
                : VillagerInteractionTextUtil.resourcePathName(definition.target().structure());
    }

    private static String proofItemName(QuestDefinition definition) {
        ResourceLocation proofItem = definition.target().proofItem();
        if (proofItem == null) {
            return "proof";
        }
        return BuiltInRegistries.ITEM.getOptional(proofItem)
                .map(item -> new ItemStack(item).getHoverName().getString())
                .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(proofItem));
    }

    private static int roundCoordinate(int value) {
        return Math.round((float) value / APPROXIMATE_COORDINATE_STEP) * APPROXIMATE_COORDINATE_STEP;
    }

    private static int roundDistance(BlockPos origin, BlockPos target) {
        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();
        return Math.max(100, Math.round((float) Math.sqrt((double) dx * dx + (double) dz * dz) / 100.0F) * 100);
    }

    private static String directionPhrase(BlockPos origin, BlockPos target) {
        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();
        String northSouth = dz < -32 ? "north" : dz > 32 ? "south" : "";
        String eastWest = dx > 32 ? "east" : dx < -32 ? "west" : "";
        if (!northSouth.isBlank() && !eastWest.isBlank()) {
            return northSouth + "-" + eastWest;
        }
        if (!northSouth.isBlank()) {
            return northSouth;
        }
        if (!eastWest.isBlank()) {
            return eastWest;
        }
        return "nearby";
    }

    private static String lineId(QuestDefinition definition, String stage) {
        return "quest_" + definition.id().toString().replace(':', '_').replace('/', '_') + "_" + stage;
    }

    private record LocatedTarget(BlockPos pos) {
    }

    public record QuestActionOutcome(
            String status,
            String lineId,
            String text,
            Map<String, String> replacements) {
        public QuestActionOutcome {
            status = status == null ? "" : status;
            text = text == null ? "" : text;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }

        public VillagerDialogueService.DialogueResult dialogueResult() {
            return new VillagerDialogueService.DialogueResult(this.lineId, this.text);
        }
    }
}
