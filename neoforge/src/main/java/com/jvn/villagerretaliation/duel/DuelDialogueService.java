package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DuelDialogueService {
    private static final String GLOAT = "duel_reaction_gloat";
    private static final String SULK = "duel_reaction_sulk";
    private static final String STORY = "duel_village_story";
    private static final String[] GLOATS = {
            "Try not to blink next time, %s.", "That makes the score %s to %s. Not that I am counting.",
            "You brought courage. I brought results.", "A fine attempt, %s. Almost convincing.",
            "The whole village saw that one.", "I could offer lessons, but they would cost extra.",
            "Still feeling gutsy, %s?", "I won the wager and the bragging rights.",
            "You move well?for someone I just beat.", "Remember this score: %s wins for me, %s for you.",
            "That arena suited me nicely.", "No hard feelings. Just a very clear winner.",
            "I expected a challenge and received a warm-up.", "Tell your friends I fought bravely. Tell them I won, too.",
            "Whenever you are ready for another lesson, wait out the cooldown."
    };
    private static final String[] SULKS = {
            "You won. There, I said it.", "Do not look so pleased with yourself, %s.",
            "The score is %s to %s. I can still turn it around.", "I had you exactly where I wanted you. Briefly.",
            "The sun was in my eyes. Somehow.", "Enjoy the wager. I will earn it back.",
            "I have fought better days.", "Everyone saw that, did they? Wonderful.",
            "Next time I am bringing a better plan.", "A loss is practice wearing an ugly hat.",
            "You were faster than I expected, %s.", "I am not sulking. I am reviewing tactics.",
            "One bout does not settle everything.", "Fine work. Do not make me compliment you twice.",
            "I will remember that strike. Mostly because it still hurts."
    };

    private DuelDialogueService() {}

    public static List<DialogueOptionDefinition> addAvailableOptions(ServerLevel level, ServerPlayer player,
                                                                      Villager villager, List<DialogueOptionDefinition> original) {
        List<DialogueOptionDefinition> options = new ArrayList<>(original);
        DuelSavedData data = DuelSavedData.get(level);
        DuelSavedData.DuelRecord record = data.record(villager.getUUID(), player.getUUID());
        if (record.pendingGloats() > 0)
            options.add(DialogueOptionDefinition.transmitted(GLOAT, "About your duel victory...", DialogueRequestType.QUESTION, true, 850));
        if (record.pendingSulks() > 0)
            options.add(DialogueOptionDefinition.transmitted(SULK, "About your duel loss...", DialogueRequestType.QUESTION, true, 851));
        if (findStory(level, player, villager).isPresent())
            options.add(DialogueOptionDefinition.transmitted(STORY, "Tell me about that duel.", DialogueRequestType.STORY, true, 852));
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
            line = storyLine(memory);
        } else {
            DuelSavedData.Reaction expected = GLOAT.equals(optionId) ? DuelSavedData.Reaction.GLOAT : DuelSavedData.Reaction.SULK;
            DuelSavedData.Reaction reaction = data.consumeReaction(villager.getUUID(), player.getUUID(), expected);
            if (reaction != expected) return true;
            DuelSavedData.DuelRecord record = data.record(villager.getUUID(), player.getUUID());
            String[] choices = reaction == DuelSavedData.Reaction.GLOAT ? GLOATS : SULKS;
            line = choices[player.getRandom().nextInt(choices.length)].formatted(
                    player.getGameProfile().getName(), record.villagerWins(), record.villagerLosses());
        }
        PacketDistributor.sendToPlayer(player, new VillagerInteractionNoticePayload(
                villager.getId(), line, VillagerPresetNameRegistry.resolveDisplayName(villager).getString()));
        return true;
    }

    private static Optional<DuelSavedData.DuelMemory> findStory(ServerLevel level, ServerPlayer player, Villager speaker) {
        UUID village = VillageEventMemory.villageForVillager(level, speaker).map(value -> value.value()).orElse(null);
        if (village == null) return Optional.empty();
        DuelSavedData data = DuelSavedData.get(level);
        List<DuelSavedData.DuelMemory> history = data.history();
        for (int i = history.size() - 1; i >= 0; i--) {
            DuelSavedData.DuelMemory memory = history.get(i);
            if (village.equals(memory.villageId()) && !speaker.getUUID().equals(memory.villagerId())
                    && !data.storyAcknowledged(speaker.getUUID(), player.getUUID(), memory.id())) return Optional.of(memory);
        }
        return Optional.empty();
    }

    private static String storyLine(DuelSavedData.DuelMemory memory) {
        return switch (memory.result()) {
            case PLAYER_WIN -> "%s challenged %s and won a duel worth %s each. The score now stands %s to %s."
                    .formatted(memory.playerName(), memory.villagerName(), memory.wager(), memory.villagerWins(), memory.villagerLosses());
            case VILLAGER_WIN -> "%s bested %s in a wagered duel. The score now stands %s to %s."
                    .formatted(memory.villagerName(), memory.playerName(), memory.villagerWins(), memory.villagerLosses());
            case DRAW -> "%s and %s fought until the duel was called a draw."
                    .formatted(memory.villagerName(), memory.playerName());
            case CANCELLED -> "";
        };
    }
}
