package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.campaign.CampaignPlayerRewards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class CampaignPlayerLoadMixin {
    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;getLoadedPlayerTag()Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag villagerretaliation$restoreCampaignRewards(WorldData data, ServerPlayer player) {
        return CampaignPlayerRewards.loadedOwnerTag(player, data.getLoadedPlayerTag());
    }
}
