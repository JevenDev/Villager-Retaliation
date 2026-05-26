package com.jvn.villagerretaliation.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class VillagerSkillSet {
    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 100;
    public static final VillagerSkillSet EMPTY = new VillagerSkillSet(new EnumMap<>(VillagerSkill.class));
    public static final VillagerSkillSet DEFAULT = filled(50);

    private final EnumMap<VillagerSkill, Integer> values;

    private VillagerSkillSet(EnumMap<VillagerSkill, Integer> values) {
        this.values = new EnumMap<>(VillagerSkill.class);
        for (Map.Entry<VillagerSkill, Integer> entry : values.entrySet()) {
            if (entry.getKey() != null) {
                this.values.put(entry.getKey(), clamp(entry.getValue()));
            }
        }
    }

    public static VillagerSkillSet of(Map<VillagerSkill, Integer> values) {
        EnumMap<VillagerSkill, Integer> copy = new EnumMap<>(VillagerSkill.class);
        if (values != null) {
            for (Map.Entry<VillagerSkill, Integer> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), clamp(entry.getValue()));
                }
            }
        }
        return new VillagerSkillSet(copy);
    }

    public static VillagerSkillSet filled(int value) {
        EnumMap<VillagerSkill, Integer> values = new EnumMap<>(VillagerSkill.class);
        for (VillagerSkill skill : VillagerSkill.values()) {
            values.put(skill, clamp(value));
        }
        return new VillagerSkillSet(values);
    }

    public int get(VillagerSkill skill) {
        return this.values.getOrDefault(skill, MIN_VALUE);
    }

    public boolean has(VillagerSkill skill) {
        return this.values.containsKey(skill);
    }

    public boolean hasAllSkills() {
        for (VillagerSkill skill : VillagerSkill.values()) {
            if (!has(skill)) {
                return false;
            }
        }
        return true;
    }

    public VillagerSkillRank rank(VillagerSkill skill) {
        return VillagerSkillRank.fromValue(get(skill));
    }

    public VillagerSkillSet with(VillagerSkill skill, int value) {
        EnumMap<VillagerSkill, Integer> copy = asMutableMap();
        copy.put(skill, clamp(value));
        return new VillagerSkillSet(copy);
    }

    public VillagerSkillSet completeWith(VillagerSkillSet fallback) {
        EnumMap<VillagerSkill, Integer> copy = asMutableMap();
        VillagerSkillSet safeFallback = fallback == null ? DEFAULT : fallback;
        for (VillagerSkill skill : VillagerSkill.values()) {
            copy.putIfAbsent(skill, safeFallback.get(skill));
        }
        return new VillagerSkillSet(copy);
    }

    public Map<VillagerSkill, Integer> asMap() {
        return Map.copyOf(this.values);
    }

    public List<VillagerSkillValue> sortedDescending() {
        List<VillagerSkillValue> sorted = new ArrayList<>();
        for (VillagerSkill skill : VillagerSkill.values()) {
            sorted.add(new VillagerSkillValue(skill, get(skill)));
        }
        sorted.sort(Comparator
                .comparingInt(VillagerSkillValue::value)
                .reversed()
                .thenComparing(value -> value.skill().serializedName()));
        return List.copyOf(sorted);
    }

    public List<VillagerSkillValue> best(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<VillagerSkillValue> sorted = sortedDescending();
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (VillagerSkill skill : VillagerSkill.values()) {
            if (this.values.containsKey(skill)) {
                tag.putInt(skill.serializedName(), get(skill));
            }
        }
        return tag;
    }

    public static VillagerSkillSet load(CompoundTag tag) {
        if (tag == null) {
            return EMPTY;
        }

        EnumMap<VillagerSkill, Integer> values = new EnumMap<>(VillagerSkill.class);
        for (VillagerSkill skill : VillagerSkill.values()) {
            if (tag.contains(skill.serializedName(), Tag.TAG_INT)) {
                values.put(skill, tag.getInt(skill.serializedName()));
            } else if (tag.contains(skill.name(), Tag.TAG_INT)) {
                values.put(skill, tag.getInt(skill.name()));
            }
        }
        return new VillagerSkillSet(values);
    }

    public static int clamp(int value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    private EnumMap<VillagerSkill, Integer> asMutableMap() {
        EnumMap<VillagerSkill, Integer> copy = new EnumMap<>(VillagerSkill.class);
        copy.putAll(this.values);
        return copy;
    }
}
