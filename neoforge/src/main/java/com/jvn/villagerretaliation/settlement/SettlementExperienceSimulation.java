package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.EntityCollisionAccessor;
import com.jvn.villagerretaliation.mixin.ExperienceOrbAccessor;
import com.jvn.villagerretaliation.villager.ExperienceOrbWorkTarget;
import com.jvn.villagerretaliation.villager.VillagerEquipmentMending;
import com.jvn.villagerretaliation.villager.VillagerWorkExperience;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.XpOrbTargetingEvent;

final class SettlementExperienceSimulation {
    private static final String MOTION_TAG = "VillagerRetaliationSettlementExperienceMotion";
    private static final ThreadLocal<ExperienceOrb> ACTIVE = new ThreadLocal<>();
    private final ServerLevel level;
    private final SettlementExperienceTerrain terrain;
    private final SettlementExperienceRecipients recipients;

    SettlementExperienceSimulation(ServerLevel level, SettlementRecord town) {
        this.level = level;
        this.terrain = new SettlementExperienceTerrain(level, town);
        this.recipients = new SettlementExperienceRecipients(level);
    }

    static boolean active(ExperienceOrb orb) {
        return ACTIVE.get() == orb;
    }

    static void remember(ExperienceOrb orb) {
        var motion = new CompoundTag();
        motion.putInt("Ticks", orb.tickCount);
        var villager = ((ExperienceOrbWorkTarget) orb).villagerretaliation$workExperienceTarget();
        var player = ((ExperienceOrbAccessor) orb).villagerretaliation$getFollowingPlayer();
        if (villager != null) motion.putUUID("Villager", villager.getUUID());
        if (player != null) motion.putUUID("Player", player.getUUID());
        orb.getPersistentData().put(MOTION_TAG, motion);
    }

    static void restore(ExperienceOrb orb) {
        var motion = orb.getPersistentData().getCompound(MOTION_TAG);
        orb.tickCount = motion.getInt("Ticks");
        var level = (ServerLevel) orb.level();
        var player = motion.hasUUID("Player") ? level.getEntity(motion.getUUID("Player")) : null;
        var villager = motion.hasUUID("Villager") ? level.getEntity(motion.getUUID("Villager")) : null;
        ((ExperienceOrbAccessor) orb).villagerretaliation$setFollowingPlayer(player instanceof Player target ? target : null);
        ((ExperienceOrbWorkTarget) orb).villagerretaliation$workExperienceTarget(villager instanceof Villager target ? target : null);
    }

    void advance(ExperienceOrb orb, int elapsed) {
        var previous = ACTIVE.get();
        ACTIVE.set(orb);
        try {
            restore(orb);
            var motion = orb.getPersistentData().getCompound(MOTION_TAG);
            if (motion.hasUUID("Villager")) {
                ((ExperienceOrbWorkTarget) orb).villagerretaliation$workExperienceTarget(this.recipients.find(motion.getUUID("Villager")));
            }
            for (int tick = 0; tick < Math.clamp(elapsed, 0, 200) && orb.isAlive(); tick++) {
                orb.tickCount++;
                if (mend(orb)) continue;
                orb.checkBelowWorld();
                if (!orb.isAlive()) break;
                var position = orb.position();
                var velocity = orb.getDeltaMovement();
                boolean onGround = orb.onGround();
                try {
                    move(orb);
                    for (var player : this.level.players()) {
                        if (player.isAlive() && !player.isSpectator() && player.getBoundingBox().inflate(1.0D, 0.5D, 1.0D)
                                .intersects(orb.getBoundingBox())) orb.playerTouch(player);
                        if (!orb.isAlive()) break;
                    }
                } catch (SettlementExperienceTerrain.Unobserved | DeferredEnvironment unavailable) {
                    orb.setPos(position);
                    orb.setDeltaMovement(velocity);
                    ((EntityCollisionAccessor) orb).villagerretaliation$setOnGround(onGround);
                }
                var state = (ExperienceOrbAccessor) orb;
                state.villagerretaliation$setAge(state.villagerretaliation$getAge() + 1);
                if (state.villagerretaliation$getAge() >= 6000) orb.discard();
            }
            remember(orb);
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    private boolean mend(ExperienceOrb orb) {
        var target = (ExperienceOrbWorkTarget) orb;
        var villager = target.villagerretaliation$workExperienceTarget();
        boolean owned = villager != null && VillagerWorkExperience.belongsTo(orb, villager)
                && VillagerEquipmentMending.hasMendingEquipment(villager);
        if (villager == null || !villager.isAlive() || villager.level() != this.level || villager.distanceToSqr(orb) >= 64.0D
                || !VillagerEquipmentMending.canRepair(villager) && !owned) {
            target.villagerretaliation$workExperienceTarget(null);
            return false;
        }
        if (!this.recipients.mend(orb, villager)) return false;
        var state = (ExperienceOrbAccessor) orb;
        state.villagerretaliation$setCount(state.villagerretaliation$getCount() - 1);
        if (state.villagerretaliation$getCount() <= 0) orb.discard();
        return true;
    }

    private void move(ExperienceOrb orb) {
        ordinaryEnvironment(orb.getBoundingBox());
        orb.setDeltaMovement(orb.getDeltaMovement().add(0.0D, -orb.getGravity(), 0.0D));
        if (!this.terrain.noCollision(orb, orb.getBoundingBox())) escape(orb);
        var state = (ExperienceOrbAccessor) orb;
        var targets = (ExperienceOrbWorkTarget) orb;
        var player = state.villagerretaliation$getFollowingPlayer();
        if (orb.tickCount % 20 == 1) {
            if (player == null || player.distanceToSqr(orb) > 64.0D) {
                player = NeoForge.EVENT_BUS.post(new XpOrbTargetingEvent(orb, 8.0D)).getFollowingPlayer();
            }
            var villager = VillagerWorkExperience.mendingTarget(orb, player, this.recipients.nearby(orb));
            targets.villagerretaliation$workExperienceTarget(villager);
            if (villager != null) player = null;
        }
        if (player != null && (player.isSpectator() || player.isDeadOrDying())) player = null;
        state.villagerretaliation$setFollowingPlayer(player);
        if (player != null) attract(orb, player);
        if (targets.villagerretaliation$workExperienceTarget() != null) attract(orb, targets.villagerretaliation$workExperienceTarget());

        var requested = orb.getDeltaMovement();
        var bounds = orb.getBoundingBox().expandTowards(requested);
        var shapes = new ArrayList<VoxelShape>(this.terrain.getEntityCollisions(orb, bounds));
        this.terrain.getBlockCollisions(orb, bounds).forEach(shapes::add);
        var border = this.terrain.getWorldBorder();
        if (border.isInsideCloseToBorder(orb, bounds)) shapes.add(border.getCollisionShape());
        var resolved = EntityCollisionAccessor.villagerretaliation$collide(requested, orb.getBoundingBox(), shapes);
        var destination = orb.position().add(resolved);
        ordinaryEnvironment(orb.getBoundingBox().move(resolved));
        var below = BlockPos.containing(destination.x, destination.y - 0.2D, destination.z);
        var floor = this.terrain.getBlockState(below);
        var frictionPos = BlockPos.containing(destination.x, destination.y - 0.999999F, destination.z);
        boolean grounded = requested.y != resolved.y && requested.y < 0.0D;
        float friction = grounded ? this.terrain.getBlockState(frictionPos).getFriction(this.terrain, frictionPos, orb) * 0.98F : 0.98F;
        var destinationBlock = this.terrain.getBlockState(BlockPos.containing(destination));
        float speed = destinationBlock.getBlock().getSpeedFactor();
        if (!destinationBlock.is(Blocks.WATER) && !destinationBlock.is(Blocks.BUBBLE_COLUMN) && speed == 1.0F) {
            speed = this.terrain.getBlockState(frictionPos).getBlock().getSpeedFactor();
        }
        if (resolved.lengthSqr() > 1.0E-7D) orb.setPos(destination);
        ((EntityCollisionAccessor) orb).villagerretaliation$setOnGround(grounded);
        orb.horizontalCollision = !Mth.equal(requested.x, resolved.x) || !Mth.equal(requested.z, resolved.z);
        orb.verticalCollision = requested.y != resolved.y;
        orb.verticalCollisionBelow = grounded;
        orb.setDeltaMovement(Mth.equal(requested.x, resolved.x) ? requested.x : 0.0D, requested.y,
                Mth.equal(requested.z, resolved.z) ? requested.z : 0.0D);
        if (orb.verticalCollision) floor.getBlock().updateEntityAfterFallOn(this.terrain, orb);
        orb.setDeltaMovement(orb.getDeltaMovement().multiply(speed * friction, 0.98D, speed * friction));
        if (grounded) orb.setDeltaMovement(orb.getDeltaMovement().multiply(1.0D, -0.9D, 1.0D));
    }

    private void ordinaryEnvironment(net.minecraft.world.phys.AABB bounds) {
        var min = BlockPos.containing(bounds.minX + 1.0E-7D, bounds.minY + 1.0E-7D, bounds.minZ + 1.0E-7D);
        var max = BlockPos.containing(bounds.maxX - 1.0E-7D, bounds.maxY - 1.0E-7D, bounds.maxZ - 1.0E-7D);
        for (var position : BlockPos.betweenClosed(min, max)) {
            var state = this.terrain.getBlockState(position);
            if (!state.getFluidState().isEmpty() || !state.isAir() && state.getBlock().getClass() != Block.class) {
                throw new DeferredEnvironment();
            }
        }
    }

    private static final class DeferredEnvironment extends RuntimeException {
        private DeferredEnvironment() {
            super(null, null, false, false);
        }
    }

    private static void attract(ExperienceOrb orb, Entity target) {
        var offset = new Vec3(target.getX() - orb.getX(), target.getY() + target.getEyeHeight() / 2.0D - orb.getY(), target.getZ() - orb.getZ());
        double distance = offset.lengthSqr();
        if (distance >= 64.0D) return;
        double pull = 1.0D - Math.sqrt(distance) / 8.0D;
        orb.setDeltaMovement(orb.getDeltaMovement().add(offset.normalize().scale(pull * pull * 0.1D)));
    }

    private void escape(ExperienceOrb orb) {
        var center = new Vec3(orb.getX(), (orb.getBoundingBox().minY + orb.getBoundingBox().maxY) / 2.0D, orb.getZ());
        var block = BlockPos.containing(center);
        var offset = center.subtract(block.getX(), block.getY(), block.getZ());
        Direction closest = Direction.UP;
        double distance = Double.MAX_VALUE;
        for (var direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            var neighbor = block.relative(direction);
            if (this.terrain.getBlockState(neighbor).isCollisionShapeFullBlock(this.terrain, neighbor)) continue;
            double axis = offset.get(direction.getAxis());
            double candidate = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - axis : axis;
            if (candidate < distance) {
                distance = candidate;
                closest = direction;
            }
        }
        double push = (orb.getRandom().nextFloat() * 0.2F + 0.1F) * closest.getAxisDirection().getStep();
        var velocity = orb.getDeltaMovement().scale(0.75D);
        orb.setDeltaMovement(closest.getAxis() == Direction.Axis.X ? push : velocity.x,
                closest.getAxis() == Direction.Axis.Y ? push : velocity.y, closest.getAxis() == Direction.Axis.Z ? push : velocity.z);
    }
}
