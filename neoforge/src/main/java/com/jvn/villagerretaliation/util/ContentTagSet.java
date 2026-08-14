package com.jvn.villagerretaliation.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record ContentTagSet(Map<ContentTagDomain, Set<String>> domains) {
    public ContentTagSet {
        EnumMap<ContentTagDomain, Set<String>> copy = new EnumMap<>(ContentTagDomain.class);
        if (domains != null) {
            domains.forEach((domain, values) -> {
                if (domain != null) {
                    copy.put(domain, ContentTags.normalizeAll(values));
                }
            });
        }
        domains = Map.copyOf(copy);
    }

    public static ContentTagSet dialogue(
            Set<String> classification,
            Set<String> routing,
            Set<String> antiRepeat) {
        return new ContentTagSet(Map.of(
                ContentTagDomain.CLASSIFICATION, classification == null ? Set.of() : classification,
                ContentTagDomain.ROUTING, routing == null ? Set.of() : routing,
                ContentTagDomain.ANTI_REPEAT, antiRepeat == null ? Set.of() : antiRepeat));
    }

    public Set<String> values(ContentTagDomain domain) {
        return this.domains.getOrDefault(domain, Set.of());
    }

    public boolean matches(ContentTagQuery query) {
        return query == null || query.matches(values(query.domain()));
    }
}
