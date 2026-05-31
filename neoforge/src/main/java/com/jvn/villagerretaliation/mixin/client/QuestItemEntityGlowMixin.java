package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.quest.VillagerQuestItemHighlightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class QuestItemEntityGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$questItemEntitiesAppearGlowing(
            Entity entity,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        if (entity instanceof ItemEntity itemEntity
                && VillagerQuestItemHighlightClient.shouldOutlineDroppedQuestItem(itemEntity)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
