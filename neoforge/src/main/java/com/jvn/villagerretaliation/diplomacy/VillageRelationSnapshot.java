package com.jvn.villagerretaliation.diplomacy;

import java.util.OptionalLong;

public record VillageRelationSnapshot(
        VillageRelation relation,
        OptionalLong expiresAt,
        VillageRelation resumesAs) {
    public static final VillageRelationSnapshot NEUTRAL = permanent(VillageRelation.NEUTRAL);

    static VillageRelationSnapshot permanent(VillageRelation relation) {
        return new VillageRelationSnapshot(relation, OptionalLong.empty(), relation);
    }
}
