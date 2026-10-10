package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.quest.discovery.QuestDiscoveryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class QuestBookOpenMixin {
    @Inject(method = "openItemGui", at = @At("TAIL"))
    private void villagerretaliation$discoverQuestBook(ItemStack stack, InteractionHand hand, CallbackInfo callback) {
        QuestDiscoveryService.bookOpened((ServerPlayer) (Object) this, stack);
    }
}
