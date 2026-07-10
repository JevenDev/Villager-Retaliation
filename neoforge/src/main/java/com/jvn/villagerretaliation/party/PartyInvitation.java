package com.jvn.villagerretaliation.party;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record PartyInvitation(
        UUID id,
        UUID inviterId,
        UUID targetId,
        UUID expectedPartyId,
        long createdGameTime,
        long expiresGameTime) {
    private static final String TAG_ID = "Id";
    private static final String TAG_INVITER = "Inviter";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_EXPECTED_PARTY = "ExpectedParty";
    private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
    private static final String TAG_EXPIRES_GAME_TIME = "ExpiresGameTime";

    public boolean isExpired(long gameTime) {
        return gameTime >= this.expiresGameTime;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, this.id);
        tag.putUUID(TAG_INVITER, this.inviterId);
        tag.putUUID(TAG_TARGET, this.targetId);
        if (this.expectedPartyId != null) {
            tag.putUUID(TAG_EXPECTED_PARTY, this.expectedPartyId);
        }
        tag.putLong(TAG_CREATED_GAME_TIME, this.createdGameTime);
        tag.putLong(TAG_EXPIRES_GAME_TIME, this.expiresGameTime);
        return tag;
    }

    static PartyInvitation load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_INVITER) || !tag.hasUUID(TAG_TARGET)) {
            return null;
        }
        return new PartyInvitation(
                tag.getUUID(TAG_ID),
                tag.getUUID(TAG_INVITER),
                tag.getUUID(TAG_TARGET),
                tag.hasUUID(TAG_EXPECTED_PARTY) ? tag.getUUID(TAG_EXPECTED_PARTY) : null,
                tag.getLong(TAG_CREATED_GAME_TIME),
                tag.getLong(TAG_EXPIRES_GAME_TIME)
        );
    }
}
