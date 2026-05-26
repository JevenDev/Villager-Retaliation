package com.jvn.villagerretaliation.profile;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record VillagerSocialAttributes(
        int knowledge,
        int guts,
        int proficiency,
        int kindness,
        int charm) {
    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 100;
    public static final VillagerSocialAttributes DEFAULT = new VillagerSocialAttributes(50, 50, 50, 50, 50);

    private static final String TAG_KNOWLEDGE = "Knowledge";
    private static final String TAG_GUTS = "Guts";
    private static final String TAG_PROFICIENCY = "Proficiency";
    private static final String TAG_KINDNESS = "Kindness";
    private static final String TAG_CHARM = "Charm";

    public VillagerSocialAttributes {
        knowledge = clamp(knowledge);
        guts = clamp(guts);
        proficiency = clamp(proficiency);
        kindness = clamp(kindness);
        charm = clamp(charm);
    }

    public int get(VillagerSocialAttribute attribute) {
        return switch (attribute) {
            case KNOWLEDGE -> this.knowledge;
            case GUTS -> this.guts;
            case PROFICIENCY -> this.proficiency;
            case KINDNESS -> this.kindness;
            case CHARM -> this.charm;
        };
    }

    public VillagerSocialAttributeRank rank(VillagerSocialAttribute attribute) {
        return VillagerSocialAttributeRank.fromValue(get(attribute));
    }

    public VillagerSocialAttributes with(VillagerSocialAttribute attribute, int value) {
        int clamped = clamp(value);
        return switch (attribute) {
            case KNOWLEDGE -> new VillagerSocialAttributes(clamped, this.guts, this.proficiency, this.kindness, this.charm);
            case GUTS -> new VillagerSocialAttributes(this.knowledge, clamped, this.proficiency, this.kindness, this.charm);
            case PROFICIENCY -> new VillagerSocialAttributes(this.knowledge, this.guts, clamped, this.kindness, this.charm);
            case KINDNESS -> new VillagerSocialAttributes(this.knowledge, this.guts, this.proficiency, clamped, this.charm);
            case CHARM -> new VillagerSocialAttributes(this.knowledge, this.guts, this.proficiency, this.kindness, clamped);
        };
    }

    public Map<VillagerSocialAttribute, Integer> asMap() {
        EnumMap<VillagerSocialAttribute, Integer> values = new EnumMap<>(VillagerSocialAttribute.class);
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            values.put(attribute, get(attribute));
        }
        return values;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_KNOWLEDGE, this.knowledge);
        tag.putInt(TAG_GUTS, this.guts);
        tag.putInt(TAG_PROFICIENCY, this.proficiency);
        tag.putInt(TAG_KINDNESS, this.kindness);
        tag.putInt(TAG_CHARM, this.charm);
        return tag;
    }

    public static VillagerSocialAttributes load(CompoundTag tag) {
        if (tag == null) {
            return DEFAULT;
        }
        return new VillagerSocialAttributes(
                getOrDefault(tag, TAG_KNOWLEDGE, DEFAULT.knowledge()),
                getOrDefault(tag, TAG_GUTS, DEFAULT.guts()),
                getOrDefault(tag, TAG_PROFICIENCY, DEFAULT.proficiency()),
                getOrDefault(tag, TAG_KINDNESS, DEFAULT.kindness()),
                getOrDefault(tag, TAG_CHARM, DEFAULT.charm())
        );
    }

    private static int getOrDefault(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
    }

    public static int clamp(int value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }
}
