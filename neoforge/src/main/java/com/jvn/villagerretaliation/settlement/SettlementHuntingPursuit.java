package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.mixin.EntityCollisionAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class SettlementHuntingPursuit {
    private final ServerLevel level;
    private final SettlementArea area;
    private final SettlementFamilyRoutes routes;
    private final SettlementExperienceTerrain terrain;
    private List<BlockPos> path;
    private BlockPos targetPosition;
    private int cursor;

    SettlementHuntingPursuit(ServerLevel level, SettlementRecord town, SettlementWorkerState worker) {
        this.level = level;
        this.area = town.areas().get(worker.area());
        this.routes = new SettlementFamilyRoutes(level, town);
        this.terrain = new SettlementExperienceTerrain(level, town);
    }

    boolean step(Villager owner, LivingEntity target) {
        return step(owner, target, VillagerRetaliationHandler.observedMeleeMovementSpeed(owner, target));
    }

    boolean step(Villager owner, LivingEntity target, double movementSpeed) {
        if (this.area == null || !this.area.contains(owner.blockPosition()) || !this.area.contains(target.blockPosition())
                || !VillagerRetaliationRetaliationUtil.isWithinRetaliationPursuitRange(owner, target)) return false;
        if (this.path == null || this.path.isEmpty() || !target.blockPosition().equals(this.targetPosition)) {
            this.targetPosition = target.blockPosition().immutable();
            this.path = this.routes.route(owner.blockPosition(), this.targetPosition, this.area::contains);
            this.cursor = 0;
        }
        if (this.path.isEmpty()) return coast(owner);
        while (this.cursor < this.path.size() - 1
                && owner.position().distanceToSqr(this.path.get(this.cursor).getBottomCenter()) < 0.25D) this.cursor++;
        var destination = this.path.get(this.cursor).getBottomCenter();
        if (this.cursor == this.path.size() - 1 && owner.position().distanceToSqr(destination) < 0.25D) {
            destination = target.position();
        }
        return move(owner, destination.subtract(owner.position()), target, movementSpeed);
    }

    boolean coast(Villager owner) {
        return this.area != null && this.area.contains(owner.blockPosition()) && move(owner, Vec3.ZERO, null, 0.0D);
    }

    private boolean move(Villager owner, Vec3 offset, LivingEntity target, double movementSpeed) {
        if (!ordinary(owner.getBoundingBox())) return false;
        var floorPosition = BlockPos.containing(owner.getX(), owner.getY() - 0.500001D, owner.getZ());
        var floor = this.terrain.getBlockState(floorPosition);
        if (com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isDamagingBlock(floor)
                || floor.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK)) return false;
        double friction = floor.getFriction(this.terrain, floorPosition, owner);
        if (!Double.isFinite(friction) || friction <= 0.0D) return false;
        boolean grounded = this.terrain.getBlockCollisions(owner, owner.getBoundingBox().move(0.0D, -0.01D, 0.0D)).iterator().hasNext();
        double speed = target == null ? 0.0D
                : movementSpeed * owner.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (!Double.isFinite(speed) || target != null && speed <= 0.0D) return false;
        double acceleration = target == null ? 0.0D : grounded ? speed * 0.21600002D / (friction * friction * friction) : 0.02D;
        var velocity = owner.getDeltaMovement();
        velocity = new Vec3(small(velocity.x), small(velocity.y), small(velocity.z));
        if (grounded && offset.y > owner.maxUpStep() && offset.horizontalDistanceSqr() < 2.0D) {
            double jump = owner.getAttributeValue(Attributes.JUMP_STRENGTH) * floor.getBlock().getJumpFactor() + owner.getJumpBoostPower();
            velocity = new Vec3(velocity.x, Math.max(velocity.y, jump), velocity.z);
        }
        var direction = new Vec3(offset.x, 0.0D, offset.z).normalize();
        var requested = velocity.add(direction.scale(acceleration * 0.98D));
        var bounds = owner.getBoundingBox().expandTowards(requested);
        var nativeBody = this.level.getEntity(owner.getUUID());
        var shapes = new ArrayList<VoxelShape>(this.terrain.getEntityCollisions(nativeBody == null ? owner : nativeBody, bounds));
        this.terrain.getBlockCollisions(owner, bounds).forEach(shapes::add);
        if (this.terrain.getWorldBorder().isInsideCloseToBorder(owner, bounds)) shapes.add(this.terrain.getWorldBorder().getCollisionShape());
        var resolved = EntityCollisionAccessor.villagerretaliation$collide(requested, owner.getBoundingBox(), shapes);
        var position = owner.position().add(resolved);
        var moved = owner.getBoundingBox().move(resolved);
        if (!this.area.contains(BlockPos.containing(position)) || !ordinary(moved)) return false;
        if (resolved.lengthSqr() < 1.0E-10D) return false;
        boolean landed = requested.y < 0.0D && requested.y != resolved.y;
        double vertical = requested.y == resolved.y ? requested.y : 0.0D;
        if (owner.hasEffect(MobEffects.LEVITATION)) {
            vertical += (0.05D * (owner.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - vertical) * 0.2D;
        } else {
            double gravity = owner.getGravity();
            if (vertical <= 0.0D && owner.hasEffect(MobEffects.SLOW_FALLING)) gravity = Math.min(gravity, 0.01D);
            vertical -= gravity;
        }
        double drag = grounded ? friction * 0.91D : 0.91D;
        var destinationFloor = BlockPos.containing(position.x, position.y - 0.500001D, position.z);
        float blockSpeed = this.terrain.getBlockState(BlockPos.containing(position)).getBlock().getSpeedFactor();
        if (blockSpeed == 1.0F) blockSpeed = this.terrain.getBlockState(destinationFloor).getBlock().getSpeedFactor();
        var nextVelocity = new Vec3(Mth.equal(requested.x, resolved.x) ? requested.x * drag * blockSpeed : 0.0D,
                vertical * 0.98D, Mth.equal(requested.z, resolved.z) ? requested.z * drag * blockSpeed : 0.0D);
        owner.setPos(position);
        owner.setDeltaMovement(nextVelocity);
        ((EntityCollisionAccessor) owner).villagerretaliation$setOnGround(landed || grounded && Math.abs(resolved.y) < 1.0E-7D);
        owner.horizontalCollision = !Mth.equal(requested.x, resolved.x) || !Mth.equal(requested.z, resolved.z);
        owner.verticalCollision = requested.y != resolved.y;
        owner.verticalCollisionBelow = landed;
        if (target != null) owner.setYRot((float) (Mth.atan2(offset.z, offset.x) * 180.0D / Math.PI) - 90.0F);
        return true;
    }

    private boolean ordinary(AABB bounds) {
        var min = BlockPos.containing(bounds.minX + 1.0E-7D, bounds.minY + 1.0E-7D, bounds.minZ + 1.0E-7D);
        var max = BlockPos.containing(bounds.maxX - 1.0E-7D, bounds.maxY - 1.0E-7D, bounds.maxZ - 1.0E-7D);
        for (var position : BlockPos.betweenClosed(min, max)) {
            var state = this.terrain.getBlockState(position);
            if (!state.getFluidState().isEmpty()
                    || com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isDamagingBlock(state)
                    || state.is(net.minecraft.tags.BlockTags.CLIMBABLE)
                    || state.is(net.minecraft.world.level.block.Blocks.COBWEB)
                    || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return false;
        }
        return true;
    }

    private static double small(double value) {
        return Math.abs(value) < 0.003D ? 0.0D : value;
    }
}
