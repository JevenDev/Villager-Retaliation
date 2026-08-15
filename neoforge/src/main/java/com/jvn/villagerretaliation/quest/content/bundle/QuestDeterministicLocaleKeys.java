package com.jvn.villagerretaliation.quest.content.bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Generates relative keys from stable structural IDs and semantic roles only. */
public final class QuestDeterministicLocaleKeys {
    private QuestDeterministicLocaleKeys() {
    }

    public static String relativeKey(Address address) {
        List<String> parts = new ArrayList<>();
        for (String stableId : address.stableIds()) {
            String normalized = normalize(stableId);
            if (!normalized.isBlank()) {
                parts.add(normalized);
            }
        }
        parts.add(normalize(address.semanticRole()));
        return "#" + String.join(".", parts);
    }

    public static FreezeResult freeze(Collection<Address> addresses) {
        Map<GeneratedKey, Address> owners = new LinkedHashMap<>();
        List<Collision> collisions = new ArrayList<>();
        if (addresses != null) {
            for (Address address : addresses) {
                if (address == null) {
                    continue;
                }
                GeneratedKey key = new GeneratedKey(address.questId(), relativeKey(address));
                Address previous = owners.putIfAbsent(key, address);
                if (previous != null && !previous.equals(address)) {
                    collisions.add(new Collision(key.relativeKey(), previous, address));
                }
            }
        }
        return new FreezeResult(Collections.unmodifiableMap(owners), List.copyOf(collisions));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");
    }

    public record Address(ResourceLocation questId, List<String> stableIds, String semanticRole) {
        public Address {
            if (questId == null) {
                throw new IllegalArgumentException("quest ID is required");
            }
            stableIds = stableIds == null ? List.of() : List.copyOf(stableIds);
            semanticRole = semanticRole == null ? "" : semanticRole;
            if (semanticRole.isBlank()) {
                throw new IllegalArgumentException("semantic role is required");
            }
        }
    }

    public record Collision(String key, Address first, Address second) {
    }

    public record GeneratedKey(ResourceLocation questId, String relativeKey) {
    }

    public record FreezeResult(Map<GeneratedKey, Address> keys, List<Collision> collisions) {
        public boolean valid() {
            return this.collisions.isEmpty();
        }
    }
}
