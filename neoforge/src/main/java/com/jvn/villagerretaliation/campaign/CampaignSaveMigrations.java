package com.jvn.villagerretaliation.campaign;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class CampaignSaveMigrations {
    private CampaignSaveMigrations() {
    }

    static CompoundTag migrate(CompoundTag input) {
        var tag = input.copy();
        if (tag.isEmpty()) {
            tag.putInt("DataVersion", CampaignSavedData.CURRENT_DATA_VERSION);
            tag.put("Campaigns", new ListTag());
            return tag;
        }
        CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT), "missing campaign save version");
        int version = tag.getInt("DataVersion");
        CampaignStateCodec.require(version >= 1 && version <= CampaignSavedData.CURRENT_DATA_VERSION, "unsupported campaign save version");
        if (version == 1) {
            for (Tag raw : CampaignStateCodec.list(tag, "Campaigns", Tag.TAG_COMPOUND)) {
                for (Tag entry : CampaignStateCodec.list((CompoundTag) raw, "Players", Tag.TAG_COMPOUND)) {
                    var player = (CompoundTag) entry;
                    if (!player.contains("Milestones")) player.put("Milestones", new ListTag());
                    if (!player.contains("CompletedQuests")) player.put("CompletedQuests", new ListTag());
                }
            }
            tag.putInt("DataVersion", 2);
        }
        return tag;
    }
}
