package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredJobSite;
import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;

public final class HiredTemporaryEntities {
    private static final String HANDLED = "TemporaryEntityHandled";
    private static final String POSITION = "TemporaryEntityPosition";

    private HiredTemporaryEntities() {
    }

    public static HiredTemporaryTask current(CompoundTag state) {
        var queue = HiredTemporaryWorkService.queue(state);
        return queue == null ? null : queue.current();
    }

    public static boolean allows(CompoundTag state, Kind kind) {
        var task = current(state);
        return task == null || task.kind() == kind;
    }

    public static boolean matches(CompoundTag state, LivingEntity entity) {
        var task = current(state);
        return task == null || task.entity() == null || task.entity().equals(entity.getUUID());
    }

    public static boolean eligible(Villager worker, LivingEntity target, HiredTemporaryTask task) {
        if (!target.isAlive()) return false;
        var state = HiredWorkStateStore.state(worker);
        if (task.kind() == Kind.HUNT) return HuntingWorker.canOrderTarget(worker, target, HiredHuntingTargets.fromState(state));
        if (!(target instanceof Animal animal) || animal.isBaby()
                || !HiredAnimalBreedingTargets.matches(animal, HiredAnimalBreedingTargets.selectedTargetIds(state))) return false;
        return switch (task.kind()) {
            case BREED -> HiredAnimalHandlingOptions.breed(state);
            case SHEAR -> HiredAnimalHandlingOptions.shearSheep(state) && animal instanceof Sheep;
            case MILK -> HiredAnimalHandlingOptions.milk(state) && AnimalBreedingWorker.isMilkable(animal);
            case CULL -> HiredAnimalHandlingOptions.cull(state) && HiredAnimalCullSettings.cap(state) > HiredAnimalCullSettings.DISABLED_CAP
                    && !AnimalBreedingWorker.isProtectedCullAnimal(animal);
            default -> false;
        };
    }

    public static boolean authorized(Villager worker, BlockPos position) {
        var duty = SettlementWorkforceService.duty(worker).orElse(null);
        if (duty != null) return duty.town().contains(position);
        var home = HiredWorkArea.fromState(HiredWorkStateStore.state(worker), worker.blockPosition());
        return home.usable() && home.contains(position);
    }

    private static HiredWorkArea targetArea(ServerLevel level, Villager worker, BlockPos position) {
        var duty = SettlementWorkforceService.duty(worker).orElse(null);
        var home = HiredWorkArea.fromState(HiredWorkStateStore.state(worker), worker.blockPosition());
        for (int radius = 12; radius >= 0; radius--) {
            var min = position.offset(-radius, -Math.min(4, radius), -radius);
            var max = position.offset(radius, Math.min(4, radius), radius);
            min = new BlockPos(min.getX(), Math.max(level.getMinBuildHeight(), min.getY()), min.getZ());
            max = new BlockPos(max.getX(), Math.min(level.getMaxBuildHeight() - 1, max.getY()), max.getZ());
            if (duty == null) {
                min = HiredWorkArea.maxPos(min, home.min());
                max = HiredWorkArea.minPos(max, home.max());
            }
            if (level.getWorldBorder().isWithinBounds(min) && level.getWorldBorder().isWithinBounds(max)
                    && (duty == null || duty.town().contains(min, max))) return HiredWorkArea.fromBounds(min, max, true);
        }
        return HiredWorkArea.fromBounds(position, position, true);
    }

    public static HiredTemporaryTask normalize(ServerLevel level, Villager worker, HiredTemporaryTask task, LivingEntity target) {
        var area = task.kind() == Kind.HUNT || task.min().equals(task.max()) ? targetArea(level, worker, target.blockPosition()) : task.area();
        return new HiredTemporaryTask(task.id(), task.kind(), area.min(), area.max(), 1, null, target.getUUID());
    }

    public static HiredJobSite jobSite(ServerLevel level, Villager worker, CompoundTag state, HiredJobSite fallback) {
        var task = current(state);
        if (task == null || task.kind() != Kind.HUNT) return fallback;
        var entity = level.getEntity(task.entity());
        BlockPos position = entity instanceof LivingEntity living && authorized(worker, living.blockPosition())
                ? living.blockPosition() : state.contains(POSITION) ? BlockPos.of(state.getLong(POSITION)) : task.area().center();
        return HiredJobSite.fromWorkArea(targetArea(level, worker, position));
    }

    public static String blocker(ServerLevel level, Villager worker, CompoundTag state) {
        var task = current(state);
        if (task == null || !task.kind().entityTarget() || handled(state)) return null;
        var entity = level.getEntity(task.entity());
        if (!(entity instanceof LivingEntity living)) return "target_missing";
        if (!authorized(worker, living.blockPosition()) || task.kind() != Kind.HUNT && !task.area().contains(living.blockPosition())) return "target_outside";
        state.putLong(POSITION, living.blockPosition().asLong());
        if (task.kind() == Kind.HUNT && living.isDeadOrDying()) {
            markHandled(state, Kind.HUNT, living.getUUID());
            return null;
        }
        if (!eligible(worker, living, task)) return "target_changed";
        return HiredEntityClaims.available(level, worker, living) ? null : "target_claimed";
    }

    public static boolean handled(CompoundTag state) {
        return state.getBoolean(HANDLED);
    }

    public static void markHandled(CompoundTag state, Kind kind, UUID entity) {
        var task = current(state);
        if (task != null && task.kind() == kind && task.entity().equals(entity)) state.putBoolean(HANDLED, true);
    }

    public static void validate(CompoundTag state, HiredTemporaryTask task) {
        if (state.contains(HANDLED) && (!state.contains(HANDLED, net.minecraft.nbt.Tag.TAG_BYTE) || task == null
                || task.kind() != Kind.HUNT && task.kind() != Kind.CULL)
                || state.contains(POSITION) && (!state.contains(POSITION, net.minecraft.nbt.Tag.TAG_LONG) || task == null || !task.kind().entityTarget())) {
            throw new IllegalArgumentException("Invalid temporary entity progress");
        }
    }

    public static void clear(CompoundTag state) {
        state.remove(HANDLED);
        state.remove(POSITION);
    }

    public static void copy(CompoundTag source, CompoundTag destination) {
        for (String key : java.util.List.of(HANDLED, POSITION)) {
            destination.remove(key);
            if (source.contains(key)) destination.put(key, source.get(key).copy());
        }
    }
}
