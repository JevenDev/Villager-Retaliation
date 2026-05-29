package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.mojang.datafixers.util.Pair;
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

        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questAction.questId()).orElse(null);
        if (definition == null) {
            return Optional.of(result(
                    "quest_missing_" + questAction.questId(),
                    "I cannot find the notes for that quest.",
                    Map.of()));
        }

        return Optional.of(switch (questAction.action()) {
            case START -> startQuest(context, definition);
            case REMIND -> remindQuest(context, definition);
            case TURN_IN -> turnInQuest(context, definition);
            case NONE -> result("quest_no_action", "", Map.of());
        });
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
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (definition.target().hasProofItem() && hasRequiredProof(player, definition)) {
                changed |= progress.markHasProof();
            }
            if (!progress.visitedTarget() && isAtQuestTarget(level, player.blockPosition(), definition, progress)) {
                changed |= progress.markVisitedTarget();
            }
        }
        if (changed) {
            data.setDirty();
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

    private static VillagerDialogueService.DialogueResult startQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.COMPLETED) {
            return result(
                    lineId(definition, "already_completed"),
                    definition.dialogue().selectAlreadyCompleted(context.random()),
                    replacements(context, definition, progress));
        }
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return remindQuest(context, definition);
        }
        if (!definition.offer().matches(context)) {
            return result(
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }

        LocatedTarget target = locateTarget(context.level(), context.villager().blockPosition(), definition).orElse(null);
        if (target == null) {
            return result(
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

        return result(
                lineId(definition, "start"),
                definition.dialogue().selectStart(context.random()),
                replacements(context, definition, started));
    }

    private static VillagerDialogueService.DialogueResult remindQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return result(
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }
        return result(
                lineId(definition, "reminder"),
                definition.dialogue().selectReminder(context.random()),
                replacements(context, definition, progress));
    }

    private static VillagerDialogueService.DialogueResult turnInQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return result(
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }
        if (!progress.visitedTarget()) {
            return result(
                    lineId(definition, "missing_target"),
                    definition.dialogue().selectMissingTarget(context.random()),
                    replacements(context, definition, progress));
        }
        if (!hasRequiredProof(context.player(), definition)) {
            return result(
                    lineId(definition, "missing_proof"),
                    definition.dialogue().selectMissingProof(context.random()),
                    replacements(context, definition, progress));
        }

        progress.markHasProof();
        progress.complete(context.level().getGameTime());
        data.setDirty();
        awardRewards(context, definition);

        return result(
                lineId(definition, "turn_in"),
                definition.dialogue().selectTurnIn(context.random()),
                replacements(context, definition, progress));
    }

    private static boolean matchesState(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        boolean completed = progress != null && progress.state() == VillagerQuestSavedData.QuestState.COMPLETED;
        boolean active = progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE;
        boolean ready = isReadyToTurnIn(context, definition, progress);
        boolean notStarted = progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED;
        return switch (normalized) {
            case "available" -> notStarted && definition.offer().matches(context);
            case "not_started", "locked" -> notStarted;
            case "active", "started" -> active;
            case "in_progress", "incomplete" -> active && !ready;
            case "ready", "turn_in", "turnin", "completeable", "completable" -> ready;
            case "completed", "complete" -> completed;
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

    private static VillagerDialogueService.DialogueResult result(
            String lineId,
            String template,
            Map<String, String> replacements) {
        String text = VillagerDialogueResources.resolveTemplate(template, replacements);
        return new VillagerDialogueService.DialogueResult(
                lineId,
                text
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
}
