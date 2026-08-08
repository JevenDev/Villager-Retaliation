package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record GiftPreferenceDefinition(
        ResourceLocation id,
        Set<VillagerProfession> professions,
        int rating,
        int perItemReputation,
        String responseKey,
        int priority,
        VillagerEquipmentCondition equipmentCondition,
        GiftCategoryName name,
        List<ItemMatcher> matchers) {
    public GiftPreferenceDefinition {
        professions = professions == null ? Set.of() : Set.copyOf(professions);
        if (rating < -3 || rating > 3) {
            throw new IllegalArgumentException("gift preference rating must be between -3 and 3");
        }
        responseKey = responseKey == null ? "" : responseKey;
        name = name == null ? GiftCategoryName.EMPTY : name;
        matchers = matchers == null ? List.of() : List.copyOf(matchers);
    }

    public boolean appliesToProfession(VillagerProfession profession) {
        return this.professions.isEmpty() || this.professions.contains(profession);
    }

    public boolean professionSpecific() {
        return !this.professions.isEmpty();
    }

    public boolean appliesToVillager(Villager villager) {
        return this.equipmentCondition == null || this.equipmentCondition.matches(villager);
    }

    public Optional<ItemMatcher> bestMatcher(ItemStack stack) {
        return this.matchers.stream()
                .filter(matcher -> matcher.matches(stack))
                .sorted(ItemMatcher.ORDER)
                .findFirst();
    }

    public record ItemMatcher(MatchSource source, ResourceLocation value) {
        private static final java.util.Comparator<ItemMatcher> ORDER = java.util.Comparator
                .comparing(ItemMatcher::exact).reversed()
                .thenComparing(matcher -> matcher.value().toString());

        public static ItemMatcher item(ResourceLocation itemId) {
            return new ItemMatcher(MatchSource.ITEM, itemId);
        }

        public static ItemMatcher tag(ResourceLocation tagId) {
            return new ItemMatcher(MatchSource.TAG, tagId);
        }

        public boolean exact() {
            return this.source == MatchSource.ITEM;
        }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty() || this.value == null) {
                return false;
            }
            if (this.source == MatchSource.ITEM) {
                return this.value.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
            return stack.is(TagKey.create(Registries.ITEM, this.value));
        }

        public List<Item> items() {
            if (this.value == null) {
                return List.of();
            }
            if (this.source == MatchSource.ITEM) {
                return BuiltInRegistries.ITEM.getOptional(this.value).map(List::of).orElse(List.of());
            }
            TagKey<Item> tag = TagKey.create(Registries.ITEM, this.value);
            return BuiltInRegistries.ITEM.stream()
                    .filter(item -> new ItemStack(item).is(tag))
                    .toList();
        }
    }

    public enum MatchSource {
        ITEM,
        TAG,
        NONE
    }
}
