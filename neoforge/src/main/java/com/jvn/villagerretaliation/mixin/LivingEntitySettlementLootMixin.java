package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementDeathCapture;
import java.util.Collection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySettlementLootMixin {
    @Shadow protected boolean dead;

    @Inject(method = "die", at = @At("RETURN"))
    private void villagerretaliation$recordAcceptedBackgroundDeath(DamageSource source, CallbackInfo callback) {
        if (this.dead) SettlementDeathCapture.accepted((LivingEntity) (Object) this);
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDrops(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Ljava/util/Collection;Z)Z"))
    private boolean villagerretaliation$captureApprovedBackgroundDrops(LivingEntity victim, DamageSource source,
                                                                      Collection<ItemEntity> drops, boolean playerHit) {
        return CommonHooks.onLivingDrops(victim, source, drops, playerHit) || SettlementDeathCapture.drops(victim, drops);
    }

    @Redirect(method = "dropExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void villagerretaliation$captureBackgroundExperience(ServerLevel level, Vec3 position, int value) {
        if (!SettlementDeathCapture.experience((LivingEntity) (Object) this, position, value, null)) {
            ExperienceOrb.award(level, position, value);
        }
    }
}
