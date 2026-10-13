package com.jvn.villagerretaliation.party;

import java.util.UUID;

public final class CompanionAuthority {
    private CompanionAuthority() {
    }

    public static boolean canManage(UUID playerId, PartyRecord party, PartyVillagerRecord record) {
        return member(playerId, party, record) && record.recruiterId().equals(playerId);
    }

    public static boolean canCommand(UUID playerId, PartyRecord party, PartyVillagerRecord record) {
        return member(playerId, party, record)
                && (record.recruiterId().equals(playerId) || record.commanderId().equals(playerId));
    }

    public static boolean canEquip(UUID playerId, PartyRecord party, PartyVillagerRecord record) {
        return canManage(playerId, party, record);
    }

    private static boolean member(UUID playerId, PartyRecord party, PartyVillagerRecord record) {
        return playerId != null && party != null && record != null && party.playerIds().contains(playerId);
    }
}
