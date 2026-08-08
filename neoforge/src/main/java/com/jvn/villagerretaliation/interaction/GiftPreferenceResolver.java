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

    public static Optional<GiftPreferenceView> resolveView(
            List<GiftPreferenceView> views,
            ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        List<ViewMatch> matches = views.stream()
                .flatMap(view -> view.matchers().stream()
                        .filter(matcher -> matches(matcher, stack))
                        .map(matcher -> new ViewMatch(view, matcher)))
                .toList();
        boolean professionSpecific = matches.stream().anyMatch(match -> match.view().professionSpecific());
        return matches.stream()
                .filter(match -> !professionSpecific || match.view().professionSpecific())
                .sorted(Comparator
                        .comparingInt((ViewMatch match) -> match.view().priority()).reversed()
                        .thenComparing(match -> match.matcher().exact(), Comparator.reverseOrder())
                        .thenComparing(match -> match.view().categoryId().toString())
                        .thenComparing(match -> match.matcher().value().toString()))
                .map(ViewMatch::view)
                .findFirst();
    }

    private static boolean matches(GiftPreferenceView.Matcher matcher, ItemStack stack) {
        if (matcher.source() == GiftPreferenceDefinition.MatchSource.ITEM) {
            return matcher.value().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        if (matcher.source() == GiftPreferenceDefinition.MatchSource.TAG) {
            return stack.is(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    matcher.value()));
        }
        return false;
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

    private record ViewMatch(GiftPreferenceView view, GiftPreferenceView.Matcher matcher) {
    }
}
