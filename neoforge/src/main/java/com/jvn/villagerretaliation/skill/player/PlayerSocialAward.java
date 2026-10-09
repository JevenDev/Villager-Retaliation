package com.jvn.villagerretaliation.skill.player;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record PlayerSocialAward(UUID session, String check, UUID request, UUID player, SocialCheckBreakdown breakdown) {
    public PlayerSocialAward {
        if (session == null || request == null || player == null || breakdown == null
                || check == null || check.length() > 256 || !check.matches("[a-z0-9][a-z0-9_./-]*"))
            throw new IllegalArgumentException("Invalid social skill award identity");
    }

    public Identity identity() { return new Identity(session, check); }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Session", session);
        tag.putString("Check", check);
        tag.putUUID("Request", request);
        tag.putUUID("Player", player);
        tag.put("Breakdown", breakdown.save());
        return tag;
    }

    static PlayerSocialAward load(CompoundTag tag) {
        if (!tag.hasUUID("Session") || !tag.hasUUID("Request") || !tag.hasUUID("Player")
                || !tag.contains("Check", Tag.TAG_STRING) || !tag.contains("Breakdown", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Incomplete social skill award");
        return new PlayerSocialAward(tag.getUUID("Session"), tag.getString("Check"), tag.getUUID("Request"),
                tag.getUUID("Player"), SocialCheckBreakdown.load(tag.getCompound("Breakdown")));
    }

    public record Identity(UUID session, String check) { }
}
