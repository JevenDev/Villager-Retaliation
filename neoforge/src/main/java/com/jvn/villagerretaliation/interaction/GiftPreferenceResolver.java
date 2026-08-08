package com.jvn.villagerretaliation.interaction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

public final class GiftPreferenceResolver {
    private static final Comparator<Match> ORDER = Comparator
            .comparingInt((Match match) -> match.definition().priority()).reversed()
            .thenComparing(match -> match.matcher().exact(), Comparator.reverseOrder())
            .thenComparing(match -> match.definition().id().toString())
            .thenComparing(match -> match.matcher().value().toString());

    private GiftPreferenceResolver() {
    }

    public static Optional<ResolvedGiftPreference> resolve(
            List<GiftPreferenceDefinition> definitions,
            Villager villager,
            VillagerProfession profession,
            ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        List<Match> matches = definitions.stream()
                .filter(definition -> definition.appliesToProfession(profession))
                .filter(definition -> definition.appliesToVillager(villager))
                .map(definition -> definition.bestMatcher(stack)
                        .map(matcher -> new Match(definition, matcher))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean professionSpecific = matches.stream().anyMatch(match -> match.definition().professionSpecific());
        return matches.stream()
                .filter(match -> !professionSpecific || match.definition().professionSpecific())
                .sorted(ORDER)
                .findFirst()
                .map(GiftPreferenceResolver::resolved);
    }

    private static ResolvedGiftPreference resolved(Match match) {
        GiftPreferenceDefinition definition = match.definition();
        GiftPreferenceDefinition.ItemMatcher matcher = match.matcher();
        return new ResolvedGiftPreference(
                definition.id(),
                definition.rating(),
                VillagerGiftPreferences.GiftReaction.fromRating(definition.rating()),
                definition.professionSpecific(),
                0,
                definition.perItemReputation(),
                definition.responseKey(),
                definition.name(),
                matcher.source(),
                matcher.value());
    }

    private record Match(GiftPreferenceDefinition definition, GiftPreferenceDefinition.ItemMatcher matcher) {
    }
}
