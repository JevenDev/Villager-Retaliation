package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record GiftPreferenceView(
        ResourceLocation categoryId,
        int rating,
        boolean known,
        int priority,
        boolean professionSpecific,
        GiftCategoryName name,
        List<Matcher> matchers) {
    public GiftPreferenceView {
        name = name == null ? GiftCategoryName.EMPTY : name;
        matchers = matchers == null ? List.of() : List.copyOf(matchers);
    }

    public Component displayName() {
        return this.name.component(this.categoryId);
    }

    public record Matcher(
            GiftPreferenceDefinition.MatchSource source,
            ResourceLocation value,
            ItemStackPredicate stackPredicate) {
        public Matcher {
            stackPredicate = stackPredicate == null ? ItemStackPredicate.ANY : stackPredicate;
        }

        public Matcher(GiftPreferenceDefinition.MatchSource source, ResourceLocation value) {
            this(source, value, ItemStackPredicate.ANY);
        }

        public boolean exact() {
            return this.source == GiftPreferenceDefinition.MatchSource.ITEM;
        }

        public int specificity() {
            return this.stackPredicate.specificity();
        }
    }
}
