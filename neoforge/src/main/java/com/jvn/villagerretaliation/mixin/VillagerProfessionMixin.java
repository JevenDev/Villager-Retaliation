package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerProfessionMixin {
    @Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$preventPartyJobSiteProfession(
            VillagerData newData,
            CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (!(villager.level() instanceof ServerLevel level)
                || villager.getVillagerData().getProfession() != VillagerProfession.NONE
                || newData.getProfession() == VillagerProfession.NONE
                || newData.getProfession() == VillagerProfession.NITWIT
                || !villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)
                || !VillagerBehaviorSuppressionPolicy.suppresses(
                        villager, VillagerBehaviorSuppressionPolicy.Behavior.JOB_SITE_CLAIMING)) {
            return;
        }

        GlobalPos jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (jobSite != null) {
            ServerLevel jobSiteLevel = level.getServer().getLevel(jobSite.dimension());
            if (jobSiteLevel != null
                    && jobSiteLevel.getPoiManager().exists(jobSite.pos(), poiType -> true)) {
                jobSiteLevel.getPoiManager().release(jobSite.pos());
            }
        }
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        ci.cancel();
    }
}
