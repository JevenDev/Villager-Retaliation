package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.VillagerInWorldDialogueFocusService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookControl.class)
public abstract class LookControlMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Inject(method = "tick", at = @At("HEAD"))
    private void villagerretaliation$keepActiveWorkerFocusedOnTask(CallbackInfo callback) {
        if (!(this.mob instanceof Villager villager)) {
            return;
        }
        if (com.jvn.villagerretaliation.repair.SmithRepairAnimationState.isActive(villager)) {
            var state = (com.jvn.villagerretaliation.repair.SmithRepairAnimationState) villager;
            if (state.villagerretaliation$repairStarted() != com.jvn.villagerretaliation.repair.SmithRepairAnimationState.RETURNING) {
                Vec3 item = com.jvn.villagerretaliation.repair.SmithRepairWorkstation.itemPosition(villager, state.villagerretaliation$repairStation());
                ((LookControl) (Object) this).setLookAt(item.x, item.y, item.z, 30, 30);
            } else if (villager.getTradingPlayer() != null) {
                ((LookControl) (Object) this).setLookAt(villager.getTradingPlayer(), 30, 30);
            }
            return;
        }
        BlockPos target = HiredVillagerFocusService.activeWorkLookTarget(villager);
        if (target != null) {
            Vec3 center = Vec3.atCenterOf(target);
            ((LookControl) (Object) this).setLookAt(center.x, center.y, center.z, 60.0F, 60.0F);
            return;
        }
        ServerPlayer dialogueTarget = VillagerInWorldDialogueFocusService.activeFocusTarget(villager);
        if (dialogueTarget != null) {
            ((LookControl) (Object) this).setLookAt(dialogueTarget, 30.0F, 30.0F);
        }
    }
}
