package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DuelDialogueService {
    private static final String GLOAT = "duel_reaction_gloat";
    private static final String SULK = "duel_reaction_sulk";
    private static final String STORY = "duel_village_story";
    private static final String PENDING_RECOVERY_DIALOGUE_TAG = "VillagerRetaliationPendingDuelDialogue";
    private static final String PENDING_PLAYER_KEY = "Player";
    private static final String PENDING_REACTION_KEY = "Reaction";
    private static final String POST_DUEL_DEFINITION_PREFIX = "villagerretaliation:duel/post_";
    private static final String GLOAT_MESSAGE_KEY = "duel.reaction.gloat";
    private static final String SULK_MESSAGE_KEY = "duel.reaction.sulk";
    private static final String GLOAT_OPTION_MESSAGE_KEY = "duel.option.reaction.gloat";
    private static final String SULK_OPTION_MESSAGE_KEY = "duel.option.reaction.sulk";
    private static final String STORY_OPTION_MESSAGE_KEY = "duel.option.story";
    private static final String STORY_PLAYER_WIN_MESSAGE_KEY = "duel.story.player_win";
    private static final String STORY_VILLAGER_WIN_MESSAGE_KEY = "duel.story.villager_win";
    private static final String STORY_DRAW_MESSAGE_KEY = "duel.story.draw";

    private DuelDialogueService() {}

    public static SetupDialogue setupDialogue(
            ServerPlayer player,
            Villager villager,
            DuelAvailability status,
            String currencyName) {
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
        Map<String, String> replacements = Map.of(
                "player", player.getName().getString(),
                "villager_wins", Integer.toString(status.villagerWins()),
                "villager_losses", Integer.toString(status.villagerLosses()),
                "maximum_wager", Integer.toString(Math.min(status.playerCurrency(), status.villagerCurrency())),
                "currency", currencyName);
        String openingKey;
        if (!status.available()) {
            openingKey = "duel.challenge.refusal." + status.reason().name().toLowerCase(java.util.Locale.ROOT);
        } else if (status.villagerWins() == 0 && status.villagerLosses() == 0) {
            openingKey = "duel.challenge.first";
        } else {
            openingKey = "duel.challenge.rematch";
        }
        String loadoutKey = VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.get()
                ? "duel.challenge.loadout"
                : "duel.challenge.loadout.standard";
        return new SetupDialogue(
                VillagerDialogueResources.message(context, openingKey, replacements).orElse(""),
                VillagerDialogueResources.message(context, loadoutKey, replacements).orElse(""),
                VillagerDialogueResources.message(context, "duel.challenge.wager", replacements).orElse(""),
                VillagerDialogueResources.message(context, "duel.challenge.confirm", replacements).orElse(""),
                VillagerDialogueResources.message(context, "duel.challenge.starting", replacements).orElse(""));
    }

    public record SetupDialogue(
            String opening,
            String loadout,
            String wager,
            String confirmation,
            String starting) {}

    /**
     * Has the duel winner or loser immediately address their opponent. The queued reaction remains available
     * through the normal interaction screen when a forced conversation cannot be started.
     */
    public static void startPostDuelDialogue(ServerPlayer player, Villager villager, DuelResult result) {
        DuelSavedData.Reaction reaction = reactionFor(result);
        if (player == null || villager == null || reaction == DuelSavedData.Reaction.NONE) return;
        openReaction(player, villager, reaction);
    }

    /** Stores a duel reaction until a knocked-out villager is able to stand and speak again. */
    public static void queuePostRecoveryDialogue(Villager villager, ServerPlayer player, DuelResult result) {
        DuelSavedData.Reaction reaction = reactionFor(result);
        if (villager == null || player == null || reaction == DuelSavedData.Reaction.NONE) return;
        CompoundTag pending = new CompoundTag();
        pending.putUUID(PENDING_PLAYER_KEY, player.getUUID());
        pending.putString(PENDING_REACTION_KEY, reaction.name());
        villager.getPersistentData().put(PENDING_RECOVERY_DIALOGUE_TAG, pending);
    }

    /** Opens a deferred post-duel scene once the downed villager has recovered. */
    public static void startQueuedPostRecoveryDialogue(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) return;
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(PENDING_RECOVERY_DIALOGUE_TAG)) return;
        CompoundTag pending = data.getCompound(PENDING_RECOVERY_DIALOGUE_TAG);
        data.remove(PENDING_RECOVERY_DIALOGUE_TAG);
        if (!pending.hasUUID(PENDING_PLAYER_KEY)) return;

        DuelSavedData.Reaction reaction;
        try {
            reaction = DuelSavedData.Reaction.valueOf(pending.getString(PENDING_REACTION_KEY));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (reaction == DuelSavedData.Reaction.NONE) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(pending.getUUID(PENDING_PLAYER_KEY));
        if (player != null && player.serverLevel() == level) {
            openReaction(player, villager, reaction);
        }
    }

    public static List<DialogueOptionDefinition> addAvailableOptions(ServerLevel level, ServerPlayer player,
                                                                      Villager villager, List<DialogueOptionDefinition> original) {
        List<DialogueOptionDefinition> options = new ArrayList<>(original);
        DuelSavedData data = DuelSavedData.get(level);
        DuelSavedData.DuelRecord record = data.record(villager.getUUID(), player.getUUID());
        var context = VillagerInteractionService.createDialogueContext(level, player, villager);
        if (record.pendingGloats() > 0)
            VillagerDialogueResources.message(context, GLOAT_OPTION_MESSAGE_KEY).ifPresent(label ->
                    options.add(DialogueOptionDefinition.transmitted(GLOAT, label, DialogueRequestType.QUESTION, true, 850)));
        if (record.pendingSulks() > 0)
            VillagerDialogueResources.message(context, SULK_OPTION_MESSAGE_KEY).ifPresent(label ->
                    options.add(DialogueOptionDefinition.transmitted(SULK, label, DialogueRequestType.QUESTION, true, 851)));
        if (findStory(level, player, villager).isPresent())
            VillagerDialogueResources.message(context, STORY_OPTION_MESSAGE_KEY).ifPresent(label ->
                    options.add(DialogueOptionDefinition.transmitted(STORY, label, DialogueRequestType.STORY, true, 852)));
        return List.copyOf(options);
    }

    public static boolean handle(ServerPlayer player, int entityId, String optionId) {
        if (!GLOAT.equals(optionId) && !SULK.equals(optionId) && !STORY.equals(optionId)) return false;
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Villager villager) || !VillagerConversationService.validate(player, villager)) return true;
        DuelSavedData data = DuelSavedData.get(player.serverLevel());
        String line;
        if (STORY.equals(optionId)) {
            Optional<DuelSavedData.DuelMemory> found = findStory(player.serverLevel(), player, villager);
            if (found.isEmpty()) return true;
            DuelSavedData.DuelMemory memory = found.get();
            data.acknowledgeStory(villager.getUUID(), player.getUUID(), memory.id());
            Optional<String> storyLine = storyLine(player, villager, memory);
            if (storyLine.isEmpty()) return true;
            line = storyLine.get();
        } else {
            DuelSavedData.Reaction expected = GLOAT.equals(optionId) ? DuelSavedData.Reaction.GLOAT : DuelSavedData.Reaction.SULK;
            DuelSavedData.DuelRecord record = data.record(villager.getUUID(), player.getUUID());
            Optional<String> reactionLine = reactionLine(player, villager, record, expected);
            if (reactionLine.isEmpty()) return true;
            DuelSavedData.Reaction reaction = data.consumeReaction(villager.getUUID(), player.getUUID(), expected);
            if (reaction != expected) return true;
            line = reactionLine.get();
        }
        PacketDistributor.sendToPlayer(player, new VillagerInteractionNoticePayload(
                villager.getId(), line, VillagerPresetNameRegistry.resolveDisplayName(villager).getString()));
        VillagerInteractionScreenOpener.refreshNormal(player, villager);
        return true;
    }

    private static boolean openReaction(ServerPlayer player, Villager villager, DuelSavedData.Reaction reaction) {
        DuelSavedData data = DuelSavedData.get(player.serverLevel());
        DuelSavedData.DuelRecord record = data.record(villager.getUUID(), player.getUUID());
        if (reaction == DuelSavedData.Reaction.NONE || !hasPendingReaction(record, reaction)) return false;

        Optional<String> line = reactionLine(player, villager, record, reaction);
        if (line.isEmpty()) return false;
        if (!ForcedDialogueService.openSimpleForcedDialogue(
                player,
                villager,
                POST_DUEL_DEFINITION_PREFIX + reaction.name().toLowerCase(),
                line.get())) {
            return false;
        }
        data.consumeReaction(villager.getUUID(), player.getUUID(), reaction);
        return true;
    }

    private static boolean hasPendingReaction(DuelSavedData.DuelRecord record, DuelSavedData.Reaction reaction) {
        return switch (reaction) {
            case GLOAT -> record.pendingGloats() > 0;
            case SULK -> record.pendingSulks() > 0;
            case NONE -> false;
        };
    }

    private static DuelSavedData.Reaction reactionFor(DuelResult result) {
        if (result == DuelResult.VILLAGER_WIN) return DuelSavedData.Reaction.GLOAT;
        if (result == DuelResult.PLAYER_WIN) return DuelSavedData.Reaction.SULK;
        return DuelSavedData.Reaction.NONE;
    }

    private static Optional<String> reactionLine(ServerPlayer player, Villager villager, DuelSavedData.DuelRecord record,
                                                 DuelSavedData.Reaction reaction) {
        if (reaction == DuelSavedData.Reaction.NONE) return Optional.empty();
        String messageKey = reaction == DuelSavedData.Reaction.GLOAT ? GLOAT_MESSAGE_KEY : SULK_MESSAGE_KEY;
        return VillagerDialogueResources.message(
                VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager),
                messageKey,
                Map.of(
                        "player", player.getName().getString(),
                        "villager_wins", Integer.toString(record.villagerWins()),
                        "villager_losses", Integer.toString(record.villagerLosses())));
    }

    private static Optional<DuelSavedData.DuelMemory> findStory(ServerLevel level, ServerPlayer player, Villager speaker) {
        UUID village = VillageEventMemory.villageForVillager(level, speaker).map(value -> value.value()).orElse(null);
        if (village == null) return Optional.empty();
        DuelSavedData data = DuelSavedData.get(level);
        List<DuelSavedData.DuelMemory> history = data.history();
        for (int i = history.size() - 1; i >= 0; i--) {
            DuelSavedData.DuelMemory memory = history.get(i);
            if (village.equals(memory.villageId()) && !speaker.getUUID().equals(memory.villagerId())
                    && memory.witnessIds().contains(speaker.getUUID())
                    && !data.storyAcknowledged(speaker.getUUID(), player.getUUID(), memory.id())) return Optional.of(memory);
        }
        return Optional.empty();
    }

    private static Optional<String> storyLine(ServerPlayer player, Villager villager, DuelSavedData.DuelMemory memory) {
        String messageKey = switch (memory.result()) {
            case PLAYER_WIN -> STORY_PLAYER_WIN_MESSAGE_KEY;
            case VILLAGER_WIN -> STORY_VILLAGER_WIN_MESSAGE_KEY;
            case DRAW -> STORY_DRAW_MESSAGE_KEY;
            case CANCELLED -> "";
        };
        if (messageKey.isBlank()) return Optional.empty();
        return VillagerDialogueResources.message(
                VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager),
                messageKey,
                Map.of(
                        "player", memory.playerName(),
                        "villager", memory.villagerName()));
    }
}
