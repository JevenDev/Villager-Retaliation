package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.quest.VillagerQuestItemHighlightClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class QuestItemEntityOutlineColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$questItemOutlineColor(CallbackInfoReturnable<Integer> callbackInfo) {
        if ((Object) this instanceof ItemEntity itemEntity
                && VillagerQuestItemHighlightClient.shouldOutlineDroppedQuestItem(itemEntity)) {
            callbackInfo.setReturnValue(VillagerQuestItemHighlightClient.QUEST_OUTLINE_RGB);
        }
    }
}
