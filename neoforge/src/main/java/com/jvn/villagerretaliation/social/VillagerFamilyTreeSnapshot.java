package com.jvn.villagerretaliation.social;

import java.util.List;

public record VillagerFamilyTreeSnapshot(
        List<String> parents,
        List<String> siblings,
        List<String> spouses,
        List<String> children,
        List<String> friends,
        List<String> rivals
) {
    public static final VillagerFamilyTreeSnapshot EMPTY = new VillagerFamilyTreeSnapshot(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
    );

    public boolean hasFamily() {
        return !this.parents.isEmpty()
                || !this.siblings.isEmpty()
                || !this.spouses.isEmpty()
                || !this.children.isEmpty();
    }

    public boolean hasParent() {
        return !this.parents.isEmpty();
    }

    public boolean hasSibling() {
        return !this.siblings.isEmpty();
    }

    public boolean hasSpouse() {
        return !this.spouses.isEmpty();
    }

    public boolean hasChild() {
        return !this.children.isEmpty();
    }

    public int relationshipCount() {
        return this.parents.size()
                + this.siblings.size()
                + this.spouses.size()
                + this.children.size()
                + this.friends.size()
                + this.rivals.size();
    }

    public String firstParent() {
        return firstOrFallback(this.parents, "my parent");
    }

    public String firstSibling() {
        return firstOrFallback(this.siblings, "my sibling");
    }

    public String firstSpouse() {
        return firstOrFallback(this.spouses, "my spouse");
    }

    public String firstChild() {
        return firstOrFallback(this.children, "my child");
    }

    public String firstRelative() {
        if (hasParent()) {
            return firstParent();
        }
        if (hasSibling()) {
            return firstSibling();
        }
        if (hasSpouse()) {
            return firstSpouse();
        }
        return firstChild();
    }

    private static String firstOrFallback(List<String> values, String fallback) {
        return values.isEmpty() ? fallback : values.getFirst();
    }
}
