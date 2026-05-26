package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import java.util.EnumMap;
import java.util.Map;

public record SocialAttributeCondition(
        Map<VillagerSocialAttribute, Integer> minValues,
        Map<VillagerSocialAttribute, Integer> maxValues) {
    public static final SocialAttributeCondition EMPTY = new SocialAttributeCondition(Map.of(), Map.of());

    public SocialAttributeCondition {
        minValues = minValues == null ? Map.of() : Map.copyOf(minValues);
        maxValues = maxValues == null ? Map.of() : Map.copyOf(maxValues);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return this.minValues.isEmpty() && this.maxValues.isEmpty();
    }

    public boolean matches(DialogueContext context) {
        for (Map.Entry<VillagerSocialAttribute, Integer> entry : this.minValues.entrySet()) {
            if (context.socialAttributeValue(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        for (Map.Entry<VillagerSocialAttribute, Integer> entry : this.maxValues.entrySet()) {
            if (context.socialAttributeValue(entry.getKey()) > entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static final class Builder {
        private final Map<VillagerSocialAttribute, Integer> minValues = new EnumMap<>(VillagerSocialAttribute.class);
        private final Map<VillagerSocialAttribute, Integer> maxValues = new EnumMap<>(VillagerSocialAttribute.class);

        private Builder() {
        }

        public Builder min(VillagerSocialAttribute attribute, int value) {
            this.minValues.put(attribute, Math.clamp(value, 1, 100));
            return this;
        }

        public Builder max(VillagerSocialAttribute attribute, int value) {
            this.maxValues.put(attribute, Math.clamp(value, 1, 100));
            return this;
        }

        public SocialAttributeCondition build() {
            return new SocialAttributeCondition(this.minValues, this.maxValues);
        }
    }
}
