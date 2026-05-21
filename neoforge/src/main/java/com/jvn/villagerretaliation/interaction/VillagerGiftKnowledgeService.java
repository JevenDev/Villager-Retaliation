package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.GiftAdviceKind;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class VillagerGiftKnowledgeService {
    private static final String GLOBAL_PROFESSION_KEY = "*";

    private VillagerGiftKnowledgeService() {
    }

    public static GiftKnowledgeSnapshot knownGifts(ServerLevel level, ServerPlayer player, VillagerProfession profession) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        String professionKey = professionKey(profession);
        Set<String> likedNames = new LinkedHashSet<>();
        Set<String> dislikedNames = new LinkedHashSet<>();

        for (VillagerGiftPreferences.GiftCandidate candidate : VillagerGiftPreferences.giftCandidates(level, profession)) {
            if (!appliesToProfession(level, candidate, profession)) {
                continue;
            }
            String itemId = itemId(candidate.item());
            boolean liked = candidate.positive();
            if (knowsGift(data, player, professionKey, itemId, liked)) {
                if (liked) {
                    likedNames.add(itemName(candidate.item()));
                } else {
                    dislikedNames.add(itemName(candidate.item()));
                }
            }
        }

        return new GiftKnowledgeSnapshot(new ArrayList<>(likedNames), new ArrayList<>(dislikedNames));
    }

    public static Optional<GiftKnowledgeDiscovery> discoverFromGiftQuestion(DialogueContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        VillagerProfession profession = context.profession();
        String professionKey = professionKey(profession);
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        List<VillagerGiftPreferences.GiftCandidate> unknownCandidates = unknownCandidates(level, data, player, professionKey, profession);
        if (unknownCandidates.isEmpty()) {
            return Optional.empty();
        }

        VillagerGiftPreferences.GiftCandidate discovered = unknownCandidates.get(context.random().nextInt(unknownCandidates.size()));
        String itemId = itemId(discovered.item());
        boolean liked = discovered.positive();
        String knowledgeKey = discovered.professionSpecific() ? professionKey : GLOBAL_PROFESSION_KEY;
        data.rememberGiftKnowledge(player.getUUID(), knowledgeKey, itemId, liked);
        data.setDirty();

        return Optional.of(new GiftKnowledgeDiscovery(
                giftAdviceKind(liked, discovered.professionSpecific()),
                itemName(discovered.item()),
                giftSubject(profession)
        ));
    }

    private static List<VillagerGiftPreferences.GiftCandidate> unknownCandidates(
            ServerLevel level,
            VillagerInteractionSavedData data,
            ServerPlayer player,
            String professionKey,
            VillagerProfession profession) {
        List<VillagerGiftPreferences.GiftCandidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (VillagerGiftPreferences.GiftCandidate candidate : VillagerGiftPreferences.giftCandidates(level, profession)) {
            if (!appliesToProfession(level, candidate, profession)) {
                continue;
            }
            String itemId = itemId(candidate.item());
            boolean liked = candidate.positive();
            String seenKey = (liked ? "liked:" : "disliked:") + itemId;
            if (!seen.add(seenKey) || knowsGift(data, player, professionKey, itemId, liked)) {
                continue;
            }
            candidates.add(candidate);
        }
        return candidates;
    }

    private static boolean knowsGift(
            VillagerInteractionSavedData data,
            ServerPlayer player,
            String professionKey,
            String itemId,
            boolean liked) {
        return data.knowsGift(player.getUUID(), GLOBAL_PROFESSION_KEY, itemId, liked)
                || data.knowsGift(player.getUUID(), professionKey, itemId, liked);
    }

    private static boolean appliesToProfession(ServerLevel level, VillagerGiftPreferences.GiftCandidate candidate, VillagerProfession profession) {
        return VillagerGiftPreferences.evaluate(level, profession, new ItemStack(candidate.item())).reaction() == candidate.reaction();
    }

    private static String giftSubject(VillagerProfession profession) {
        String professionName = VillagerInteractionTextUtil.professionName(profession, "villager").toLowerCase(Locale.ROOT);
        return VillagerInteractionTextUtil.withIndefiniteArticle(professionName);
    }

    private static GiftAdviceKind giftAdviceKind(boolean liked, boolean professionSpecific) {
        if (professionSpecific) {
            return liked ? GiftAdviceKind.PROFESSION_LIKED : GiftAdviceKind.PROFESSION_DISLIKED;
        }
        return liked ? GiftAdviceKind.GLOBAL_LIKED : GiftAdviceKind.GLOBAL_DISLIKED;
    }

    private static String professionKey(VillagerProfession profession) {
        if (profession == null) {
            return "none";
        }
        String name = profession.name();
        return name == null || name.isBlank() ? "none" : name;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static String itemName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    public record GiftKnowledgeSnapshot(List<String> likedGiftNames, List<String> dislikedGiftNames) {
    }

    public record GiftKnowledgeDiscovery(GiftAdviceKind adviceKind, String itemName, String subject) {
    }
}
