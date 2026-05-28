package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.villager.VillagerGender;
import java.util.List;

public record VillagerFamilyTreeSnapshot(
        List<FamilyMember> parents,
        List<FamilyMember> birthParents,
        List<FamilyMember> adoptiveParents,
        List<FamilyMember> stepParents,
        List<FamilyMember> siblings,
        List<FamilyMember> spouses,
        List<FamilyMember> children,
        List<FamilyMember> auntsUncles,
        List<FamilyMember> cousins,
        List<FamilyMember> niecesNephews,
        List<FamilyMember> friends,
        List<FamilyMember> rivals,
        List<AncestorGeneration> ancestry,
        List<DescendantGeneration> descendants
) {
    public static final VillagerFamilyTreeSnapshot EMPTY = new VillagerFamilyTreeSnapshot(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
    );

    public boolean hasFamily() {
        return !this.parents.isEmpty()
                || !this.birthParents.isEmpty()
                || !this.adoptiveParents.isEmpty()
                || !this.stepParents.isEmpty()
                || !this.siblings.isEmpty()
                || !this.spouses.isEmpty()
                || !this.children.isEmpty()
                || !this.auntsUncles.isEmpty()
                || !this.cousins.isEmpty()
                || !this.niecesNephews.isEmpty()
                || !this.ancestry.isEmpty()
                || !this.descendants.isEmpty();
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

    public boolean hasAncestry() {
        return !this.ancestry.isEmpty();
    }

    public boolean hasDescendants() {
        return !this.descendants.isEmpty();
    }

    public boolean hasGrandparent() {
        return this.ancestry.stream().anyMatch(generation -> generation.generation() == 2 && !generation.ancestors().isEmpty());
    }

    public boolean hasGrandchild() {
        return this.descendants.stream().anyMatch(generation -> generation.generation() == 2 && !generation.descendants().isEmpty());
    }

    public boolean hasDescendant() {
        return hasChild() || hasDescendants();
    }

    public boolean hasAuntUncle() {
        return !this.auntsUncles.isEmpty();
    }

    public boolean hasCousin() {
        return !this.cousins.isEmpty();
    }

    public boolean hasNieceNephew() {
        return !this.niecesNephews.isEmpty();
    }

    public boolean hasExtendedFamily() {
        return hasAncestry() || hasDescendants() || hasAuntUncle() || hasCousin() || hasNieceNephew();
    }

    public boolean hasDeceasedFamily() {
        return allKnownMembers().stream().anyMatch(member -> !member.alive());
    }

    public boolean hasDeceasedFamilyNamed(String name) {
        if (name == null || name.isBlank()) {
            return hasDeceasedFamily();
        }
        String normalized = name.trim();
        return allKnownMembers().stream()
                .anyMatch(member -> !member.alive() && member.name().equalsIgnoreCase(normalized));
    }

    public int relationshipCount() {
        int count = this.parents.size()
                + this.stepParents.size()
                + this.siblings.size()
                + this.spouses.size()
                + this.children.size()
                + this.auntsUncles.size()
                + this.cousins.size()
                + this.niecesNephews.size()
                + this.friends.size()
                + this.rivals.size();
        for (AncestorGeneration generation : this.ancestry) {
            count += generation.ancestors().size();
        }
        for (DescendantGeneration generation : this.descendants) {
            count += generation.descendants().size();
        }
        return count;
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

    public String firstGrandparent() {
        return firstGenerationMember(this.ancestry, 2, "my grandparent");
    }

    public String firstAncestor() {
        return this.ancestry.stream()
                .filter(generation -> !generation.ancestors().isEmpty())
                .findFirst()
                .map(generation -> generation.ancestors().getFirst().name())
                .orElse("my ancestor");
    }

    public String firstGrandchild() {
        return firstDescendantGenerationMember(2, "my grandchild");
    }

    public String firstDescendant() {
        if (hasChild()) {
            return firstChild();
        }
        return this.descendants.stream()
                .filter(generation -> !generation.descendants().isEmpty())
                .findFirst()
                .map(generation -> generation.descendants().getFirst().name())
                .orElse("my descendant");
    }

    public String firstAuntUncle() {
        return firstOrFallback(this.auntsUncles, "my aunt or uncle");
    }

    public String firstCousin() {
        return firstOrFallback(this.cousins, "my cousin");
    }

    public String firstNieceNephew() {
        return firstOrFallback(this.niecesNephews, "my niece or nephew");
    }

    public String firstDeceasedFamily() {
        return allKnownMembers().stream()
                .filter(member -> !member.alive())
                .findFirst()
                .map(FamilyMember::name)
                .orElse("someone from my family");
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
        if (hasChild()) {
            return firstChild();
        }
        if (hasExtendedFamily()) {
            return firstExtendedRelative();
        }
        return firstChild();
    }

    public String firstExtendedRelative() {
        if (hasGrandparent()) {
            return firstGrandparent();
        }
        if (hasAncestry()) {
            return firstAncestor();
        }
        if (hasAuntUncle()) {
            return firstAuntUncle();
        }
        if (hasCousin()) {
            return firstCousin();
        }
        if (hasNieceNephew()) {
            return firstNieceNephew();
        }
        if (hasDescendants()) {
            return firstDescendant();
        }
        return firstGrandchild();
    }

    public List<FamilyMember> maleParents() {
        return membersByGender(this.parents, VillagerGender.MALE);
    }

    public List<FamilyMember> femaleParents() {
        return membersByGender(this.parents, VillagerGender.FEMALE);
    }

    public List<FamilyMember> maleBirthParents() {
        return membersByGender(this.birthParents, VillagerGender.MALE);
    }

    public List<FamilyMember> femaleBirthParents() {
        return membersByGender(this.birthParents, VillagerGender.FEMALE);
    }

    public List<FamilyMember> maleAdoptiveParents() {
        return membersByGender(this.adoptiveParents, VillagerGender.MALE);
    }

    public List<FamilyMember> femaleAdoptiveParents() {
        return membersByGender(this.adoptiveParents, VillagerGender.FEMALE);
    }

    public List<FamilyMember> maleStepParents() {
        return membersByGender(this.stepParents, VillagerGender.MALE);
    }

    public List<FamilyMember> femaleStepParents() {
        return membersByGender(this.stepParents, VillagerGender.FEMALE);
    }

    public List<FamilyMember> brothers() {
        return membersByGender(this.siblings, VillagerGender.MALE);
    }

    public List<FamilyMember> sisters() {
        return membersByGender(this.siblings, VillagerGender.FEMALE);
    }

    public List<FamilyMember> uncles() {
        return membersByGender(this.auntsUncles, VillagerGender.MALE);
    }

    public List<FamilyMember> aunts() {
        return membersByGender(this.auntsUncles, VillagerGender.FEMALE);
    }

    public List<FamilyMember> maleCousins() {
        return membersByGender(this.cousins, VillagerGender.MALE);
    }

    public List<FamilyMember> femaleCousins() {
        return membersByGender(this.cousins, VillagerGender.FEMALE);
    }

    public List<FamilyMember> nephews() {
        return membersByGender(this.niecesNephews, VillagerGender.MALE);
    }

    public List<FamilyMember> nieces() {
        return membersByGender(this.niecesNephews, VillagerGender.FEMALE);
    }

    public static List<FamilyMember> membersByGender(List<FamilyMember> members, VillagerGender gender) {
        return members.stream()
                .filter(member -> member.gender() == gender)
                .toList();
    }

    private static String firstOrFallback(List<FamilyMember> values, String fallback) {
        return values.isEmpty() ? fallback : values.getFirst().name();
    }

    private static String firstGenerationMember(List<AncestorGeneration> generations, int generation, String fallback) {
        return generations.stream()
                .filter(candidate -> candidate.generation() == generation)
                .filter(candidate -> !candidate.ancestors().isEmpty())
                .findFirst()
                .map(candidate -> candidate.ancestors().getFirst().name())
                .orElse(fallback);
    }

    private String firstDescendantGenerationMember(int generation, String fallback) {
        return this.descendants.stream()
                .filter(candidate -> candidate.generation() == generation)
                .filter(candidate -> !candidate.descendants().isEmpty())
                .findFirst()
                .map(candidate -> candidate.descendants().getFirst().name())
                .orElse(fallback);
    }

    private List<FamilyMember> allKnownMembers() {
        java.util.ArrayList<FamilyMember> members = new java.util.ArrayList<>();
        members.addAll(this.parents);
        members.addAll(this.birthParents);
        members.addAll(this.adoptiveParents);
        members.addAll(this.stepParents);
        members.addAll(this.siblings);
        members.addAll(this.spouses);
        members.addAll(this.children);
        members.addAll(this.auntsUncles);
        members.addAll(this.cousins);
        members.addAll(this.niecesNephews);
        members.addAll(this.friends);
        members.addAll(this.rivals);
        for (AncestorGeneration generation : this.ancestry) {
            members.addAll(generation.ancestors());
        }
        for (DescendantGeneration generation : this.descendants) {
            members.addAll(generation.descendants());
        }
        return List.copyOf(members);
    }

    public record FamilyMember(String name, VillagerGender gender, boolean alive) {
        public FamilyMember {
            name = name == null ? "" : name;
            gender = gender == null ? VillagerGender.MALE : gender;
        }

        public String statusLabel() {
            return this.alive ? "alive" : "deceased";
        }

        public String displayLabel() {
            return this.alive ? this.name : this.name + " (" + statusLabel() + ")";
        }
    }

    public record AncestorGeneration(int generation, List<FamilyMember> ancestors) {
        public boolean isGrandparentGeneration() {
            return this.generation == 2;
        }
    }

    public record DescendantGeneration(int generation, List<FamilyMember> descendants) {
        public boolean isGrandchildGeneration() {
            return this.generation == 2;
        }
    }
}
