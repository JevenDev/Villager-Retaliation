package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.quest.follower.QuestFollowerService;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class QuestFollowerDespawnMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$retainQuestFollower(CallbackInfo callback) {
        if (QuestFollowerService.retained((Mob) (Object) this)) callback.cancel();
    }
}
