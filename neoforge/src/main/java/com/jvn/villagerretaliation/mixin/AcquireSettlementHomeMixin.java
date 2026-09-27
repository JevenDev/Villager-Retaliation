package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementHousingService;
import com.mojang.datafixers.util.Pair;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AcquirePoi.class)
public abstract class AcquireSettlementHomeMixin {
    @Inject(method = "findPathToPois", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$respectAssignedHomes(Mob mob, Set<Pair<Holder<PoiType>, BlockPos>> positions,
                                                                CallbackInfoReturnable<Path> callback) {
        if (!(mob instanceof Villager) || !(mob.level() instanceof ServerLevel level)) return;
        var allowed = positions.stream().filter(poi -> !poi.getFirst().is(PoiTypes.HOME)
                || SettlementHousingService.canAcquire(level, poi.getSecond(), mob.getUUID())).toList();
        if (allowed.size() == positions.size()) return;
        var targets = new HashSet<BlockPos>();
        int range = 1;
        for (var poi : allowed) {
            targets.add(poi.getSecond());
            range = Math.max(range, poi.getFirst().value().validRange());
        }
        callback.setReturnValue(targets.isEmpty() ? null : mob.getNavigation().createPath(targets, range));
    }
}
