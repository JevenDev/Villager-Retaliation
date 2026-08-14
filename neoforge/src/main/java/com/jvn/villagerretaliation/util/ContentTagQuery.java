package com.jvn.villagerretaliation.util;

import java.util.Set;

/** Immutable any/all/not query within one explicit content-tag domain. */
public record ContentTagQuery(
        ContentTagDomain domain,
        Set<String> any,
        Set<String> all,
        Set<String> not
) {
    public static final ContentTagQuery ANY = new ContentTagQuery(
            ContentTagDomain.CLASSIFICATION, Set.of(), Set.of(), Set.of());

    public ContentTagQuery {
        domain = domain == null ? ContentTagDomain.CLASSIFICATION : domain;
        any = ContentTags.normalizeAll(any);
        all = ContentTags.normalizeAll(all);
        not = ContentTags.normalizeAll(not);
    }

    public boolean matches(Set<String> values) {
        Set<String> normalized = ContentTags.normalizeAll(values);
        return normalized.stream().noneMatch(this.not::contains)
                && (this.any.isEmpty() || normalized.stream().anyMatch(this.any::contains))
                && (this.all.isEmpty() || normalized.containsAll(this.all));
    }

    public boolean isEmpty() {
        return this.any.isEmpty() && this.all.isEmpty() && this.not.isEmpty();
    }
}
