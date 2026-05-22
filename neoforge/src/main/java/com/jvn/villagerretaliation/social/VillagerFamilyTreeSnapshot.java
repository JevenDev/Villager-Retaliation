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

    public record FamilyMember(String name, VillagerGender gender, boolean alive) {
        public FamilyMember {
            name = name == null ? "" : name;
            gender = gender == null ? VillagerGender.MALE : gender;
        }

        public String statusLabel() {
            return this.alive ? "alive" : "deceased";
        }

        public String displayLabel() {
            return this.name + " (" + statusLabel() + ")";
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
