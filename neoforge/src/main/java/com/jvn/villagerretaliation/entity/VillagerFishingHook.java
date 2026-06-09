package com.jvn.villagerretaliation.entity;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class VillagerFishingHook extends Projectile {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY =
            SynchedEntityData.defineId(VillagerFishingHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BITING =
            SynchedEntityData.defineId(VillagerFishingHook.class, EntityDataSerializers.BOOLEAN);
    private static final int MAX_OUT_OF_WATER_TIME = 10;

    private final RandomSource synchronizedRandom = RandomSource.create();
    private boolean biting;
    private int outOfWaterTime;
    private int life;
    private int nibble;
    private int timeUntilLured;
    private int timeUntilHooked;
    private float fishAngle;
    private boolean openWater = true;
    @Nullable
    private Entity hookedIn;
    private FishHookState currentState = FishHookState.FLYING;
    private int luck;
    private int lureSpeed;

    public VillagerFishingHook(EntityType<VillagerFishingHook> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
    }

    public VillagerFishingHook(LivingEntity owner, Level level, Vec3 target, int luck, int lureSpeed) {
        this(VillagerRetaliationEntityTypes.VILLAGER_FISHING_HOOK.get(), level);
        this.setOwner(owner);
        this.luck = Math.max(0, luck);
        this.lureSpeed = Math.max(0, lureSpeed);
        Vec3 start = owner.getEyePosition().subtract(0.0D, 0.1D, 0.0D);
        Vec3 delta = target.subtract(start);
        double distance = Math.max(0.001D, delta.length());
        Vec3 motion = delta.normalize()
                .scale(Math.min(1.25D, 0.55D + distance * 0.08D))
                .add(
                        this.random.triangle(0.0D, 0.005D),
                        this.random.triangle(0.04D, 0.01D),
                        this.random.triangle(0.0D, 0.005D));
        this.moveTo(start.x, start.y, start.z, owner.getYRot(), owner.getXRot());
        this.setDeltaMovement(motion);
        this.setYRot((float)(Mth.atan2(motion.x, motion.z) * 180.0F / (float)Math.PI));
        this.setXRot((float)(Mth.atan2(motion.y, motion.horizontalDistance()) * 180.0F / (float)Math.PI));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HOOKED_ENTITY, 0);
        builder.define(DATA_BITING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_HOOKED_ENTITY.equals(key)) {
            int id = this.getEntityData().get(DATA_HOOKED_ENTITY);
            this.hookedIn = id > 0 ? this.level().getEntity(id - 1) : null;
        }
        if (DATA_BITING.equals(key)) {
            this.biting = this.getEntityData().get(DATA_BITING);
            if (this.biting) {
                this.setDeltaMovement(
                        this.getDeltaMovement().x,
                        -0.4F * Mth.nextFloat(this.synchronizedRandom, 0.6F, 1.0F),
                        this.getDeltaMovement().z);
            }
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    public void tick() {
        this.synchronizedRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level().getGameTime());
        super.tick();
        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity livingOwner) || owner.isRemoved() || !livingOwner.isAlive() || this.distanceToSqr(owner) > 1024.0D) {
            this.discard();
            return;
        }

        if (this.onGround()) {
            this.life++;
            if (this.life >= 1200) {
                this.discard();
                return;
            }
        } else {
            this.life = 0;
        }

        float waterHeight = 0.0F;
        BlockPos blockPos = this.blockPosition();
        FluidState fluidState = this.level().getFluidState(blockPos);
        if (fluidState.is(FluidTags.WATER)) {
            waterHeight = fluidState.getHeight(this.level(), blockPos);
        }

        boolean inWater = waterHeight > 0.0F;
        if (this.currentState == FishHookState.FLYING) {
            if (this.hookedIn != null) {
                this.setDeltaMovement(Vec3.ZERO);
                this.currentState = FishHookState.HOOKED_IN_ENTITY;
                return;
            }
            if (inWater) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.3D, 0.2D, 0.3D));
                this.currentState = FishHookState.BOBBING;
                return;
            }
            this.checkCollision();
        } else if (this.currentState == FishHookState.HOOKED_IN_ENTITY) {
            if (this.hookedIn != null) {
                if (!this.hookedIn.isRemoved() && this.hookedIn.level().dimension() == this.level().dimension()) {
                    this.setPos(this.hookedIn.getX(), this.hookedIn.getY(0.8D), this.hookedIn.getZ());
                } else {
                    this.setHookedEntity(null);
                    this.currentState = FishHookState.FLYING;
                }
            }
            return;
        } else if (this.currentState == FishHookState.BOBBING) {
            bobInWater(blockPos, waterHeight, inWater);
        }

        if (!fluidState.is(FluidTags.WATER)) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.updateRotation();
        if (this.currentState == FishHookState.FLYING && (this.onGround() || this.horizontalCollision)) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92D));
        this.reapplyPosition();
    }

    private void bobInWater(BlockPos blockPos, float waterHeight, boolean inWater) {
        Vec3 motion = this.getDeltaMovement();
        double surfaceOffset = this.getY() + motion.y - blockPos.getY() - waterHeight;
        if (Math.abs(surfaceOffset) < 0.01D) {
            surfaceOffset += Math.signum(surfaceOffset) * 0.1D;
        }
        this.setDeltaMovement(motion.x * 0.9D, motion.y - surfaceOffset * this.random.nextFloat() * 0.2D, motion.z * 0.9D);
        if (this.nibble <= 0 && this.timeUntilHooked <= 0) {
            this.openWater = true;
        } else {
            this.openWater = this.openWater && this.outOfWaterTime < MAX_OUT_OF_WATER_TIME && isOpenWater(this.level(), blockPos);
        }
        if (inWater) {
            this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
            if (this.biting) {
                this.setDeltaMovement(this.getDeltaMovement().add(
                        0.0D,
                        -0.1D * this.synchronizedRandom.nextFloat() * this.synchronizedRandom.nextFloat(),
                        0.0D));
            }
            if (!this.level().isClientSide) {
                this.catchingFish(blockPos);
            }
        } else {
            this.outOfWaterTime = Math.min(MAX_OUT_OF_WATER_TIME, this.outOfWaterTime + 1);
        }
    }

    private void checkCollision() {
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() == HitResult.Type.MISS || !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hitResult)) {
            this.onHit(hitResult);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) || target.isAlive() && target instanceof ItemEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            this.setHookedEntity(result.getEntity());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.setDeltaMovement(this.getDeltaMovement().normalize().scale(result.distanceTo(this)));
    }

    private void setHookedEntity(@Nullable Entity hookedEntity) {
        this.hookedIn = hookedEntity;
        this.getEntityData().set(DATA_HOOKED_ENTITY, hookedEntity == null ? 0 : hookedEntity.getId() + 1);
    }

    private void catchingFish(BlockPos pos) {
        ServerLevel serverLevel = (ServerLevel)this.level();
        int tickSpeed = 1;
        BlockPos above = pos.above();
        if (this.random.nextFloat() < 0.25F && this.level().isRainingAt(above)) {
            tickSpeed++;
        }
        if (this.random.nextFloat() < 0.5F && !this.level().canSeeSky(above)) {
            tickSpeed--;
        }

        if (this.nibble > 0) {
            this.nibble--;
            if (this.nibble <= 0) {
                this.timeUntilLured = 0;
                this.timeUntilHooked = 0;
                this.getEntityData().set(DATA_BITING, false);
            }
        } else if (this.timeUntilHooked > 0) {
            this.timeUntilHooked -= tickSpeed;
            if (this.timeUntilHooked > 0) {
                spawnApproachParticles(serverLevel);
            } else {
                splashBite(serverLevel);
            }
        } else if (this.timeUntilLured > 0) {
            lureFish(serverLevel);
        } else {
            this.timeUntilLured = Mth.nextInt(this.random, 100, 600) - this.lureSpeed;
        }
    }

    private void spawnApproachParticles(ServerLevel serverLevel) {
        this.fishAngle += (float)this.random.triangle(0.0D, 9.188D);
        float radians = this.fishAngle * (float)(Math.PI / 180.0D);
        float sin = Mth.sin(radians);
        float cos = Mth.cos(radians);
        double x = this.getX() + sin * this.timeUntilHooked * 0.1F;
        double y = Mth.floor(this.getY()) + 1.0F;
        double z = this.getZ() + cos * this.timeUntilHooked * 0.1F;
        if (serverLevel.getBlockState(BlockPos.containing(x, y - 1.0D, z)).is(Blocks.WATER)) {
            if (this.random.nextFloat() < 0.15F) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1F, z, 1, sin, 0.1D, cos, 0.0D);
            }
            float xMotion = sin * 0.04F;
            float zMotion = cos * 0.04F;
            serverLevel.sendParticles(ParticleTypes.FISHING, x, y, z, 0, zMotion, 0.01D, -xMotion, 1.0D);
            serverLevel.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -zMotion, 0.01D, xMotion, 1.0D);
        }
    }

    private void splashBite(ServerLevel serverLevel) {
        this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        double y = this.getY() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.BUBBLE, this.getX(), y, this.getZ(), (int)(1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0D, this.getBbWidth(), 0.2F);
        serverLevel.sendParticles(ParticleTypes.FISHING, this.getX(), y, this.getZ(), (int)(1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0D, this.getBbWidth(), 0.2F);
        this.nibble = Mth.nextInt(this.random, 20, 40);
        this.getEntityData().set(DATA_BITING, true);
    }

    private void lureFish(ServerLevel serverLevel) {
        this.timeUntilLured--;
        float chance = 0.15F;
        if (this.timeUntilLured < 20) {
            chance += (20 - this.timeUntilLured) * 0.05F;
        } else if (this.timeUntilLured < 40) {
            chance += (40 - this.timeUntilLured) * 0.02F;
        } else if (this.timeUntilLured < 60) {
            chance += (60 - this.timeUntilLured) * 0.01F;
        }
        if (this.random.nextFloat() < chance) {
            float angle = Mth.nextFloat(this.random, 0.0F, 360.0F) * (float)(Math.PI / 180.0D);
            float distance = Mth.nextFloat(this.random, 25.0F, 60.0F);
            double x = this.getX() + Mth.sin(angle) * distance * 0.1D;
            double y = Mth.floor(this.getY()) + 1.0F;
            double z = this.getZ() + Mth.cos(angle) * distance * 0.1D;
            if (serverLevel.getBlockState(BlockPos.containing(x, y - 1.0D, z)).is(Blocks.WATER)) {
                serverLevel.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + this.random.nextInt(2), 0.1F, 0.0D, 0.1F, 0.0D);
            }
        }
        if (this.timeUntilLured <= 0) {
            this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
            this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
        }
    }

    public CatchResult retrieve(ItemStack stack) {
        Entity owner = this.getOwner();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel) || owner == null) {
            return CatchResult.EMPTY;
        }
        int rodDamage = 0;
        List<ItemStack> loot = List.of();
        int experience = 0;
        if (this.hookedIn != null) {
            this.pullEntity(this.hookedIn);
            this.level().broadcastEntityEvent(this, (byte)31);
            rodDamage = this.hookedIn instanceof ItemEntity ? 3 : 5;
        } else if (this.nibble > 0) {
            LootParams lootParams = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, this.position())
                    .withParameter(LootContextParams.TOOL, stack)
                    .withParameter(LootContextParams.THIS_ENTITY, this)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, owner)
                    .withLuck(this.luck)
                    .create(LootContextParamSets.FISHING);
            ResourceKey<LootTable> lootTableKey = this.selectFishingLootTable(lootParams.getLuck());
            LootTable lootTable = this.level().getServer().reloadableRegistries().getLootTable(lootTableKey);
            loot = lootTable.getRandomItems(lootParams);
            experience = this.random.nextInt(6) + 1;
            rodDamage = this.onGround() ? 2 : 1;
        }
        if (this.onGround()) {
            rodDamage = 2;
        }
        this.discard();
        return new CatchResult(loot, rodDamage, experience);
    }

    private ResourceKey<LootTable> selectFishingLootTable(float luck) {
        int fishWeight = fishingLootWeight(85, -1, luck);
        int junkWeight = fishingLootWeight(10, -2, luck);
        int treasureWeight = this.openWater ? fishingLootWeight(5, 2, luck) : 0;
        int totalWeight = fishWeight + junkWeight + treasureWeight;
        if (totalWeight <= 0) {
            return BuiltInLootTables.FISHING_FISH;
        }
        int roll = this.random.nextInt(totalWeight);
        if (roll < fishWeight) {
            return BuiltInLootTables.FISHING_FISH;
        }
        roll -= fishWeight;
        if (roll < junkWeight) {
            return BuiltInLootTables.FISHING_JUNK;
        }
        return BuiltInLootTables.FISHING_TREASURE;
    }

    private static int fishingLootWeight(int weight, int quality, float luck) {
        return Math.max(Mth.floor(weight + quality * luck), 0);
    }

    public boolean isBiting() {
        return this.biting || this.getEntityData().get(DATA_BITING);
    }

    public boolean isOpenWaterFishing() {
        return this.openWater;
    }

    public static boolean isOpenWater(Level level, BlockPos pos) {
        OpenWaterType current = OpenWaterType.INVALID;
        for (int yOffset = -1; yOffset <= 2; yOffset++) {
            OpenWaterType area = getOpenWaterTypeForArea(level, pos.offset(-2, yOffset, -2), pos.offset(2, yOffset, 2));
            switch (area) {
                case ABOVE_WATER:
                    if (current == OpenWaterType.INVALID) {
                        return false;
                    }
                    break;
                case INSIDE_WATER:
                    if (current == OpenWaterType.ABOVE_WATER) {
                        return false;
                    }
                    break;
                case INVALID:
                    return false;
            }
            current = area;
        }
        return true;
    }

    private static OpenWaterType getOpenWaterTypeForArea(Level level, BlockPos firstPos, BlockPos secondPos) {
        return BlockPos.betweenClosedStream(firstPos, secondPos)
                .map(pos -> getOpenWaterTypeForBlock(level, pos))
                .reduce((first, second) -> first == second ? first : OpenWaterType.INVALID)
                .orElse(OpenWaterType.INVALID);
    }

    private static OpenWaterType getOpenWaterTypeForBlock(Level level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (!blockState.isAir() && !blockState.is(Blocks.LILY_PAD)) {
            FluidState fluidState = blockState.getFluidState();
            return fluidState.is(FluidTags.WATER) && fluidState.isSource() && blockState.getCollisionShape(level, pos).isEmpty()
                    ? OpenWaterType.INSIDE_WATER
                    : OpenWaterType.INVALID;
        }
        return OpenWaterType.ABOVE_WATER;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Luck", this.luck);
        compound.putInt("LureSpeed", this.lureSpeed);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.luck = compound.getInt("Luck");
        this.lureSpeed = compound.getInt("LureSpeed");
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 31 && this.level().isClientSide && this.hookedIn != null) {
            this.pullEntity(this.hookedIn);
        }
        super.handleEntityEvent(id);
    }

    protected void pullEntity(Entity entity) {
        Entity owner = this.getOwner();
        if (owner != null) {
            Vec3 pull = new Vec3(owner.getX() - this.getX(), owner.getY() - this.getY(), owner.getZ() - this.getZ()).scale(0.1D);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pull));
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Nullable
    public Entity getHookedIn() {
        return this.hookedIn;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        if (!(this.getOwner() instanceof LivingEntity)) {
            LOGGER.error("Failed to recreate villager fishing hook. {} (id: {}) is not a valid owner.", this.level().getEntity(packet.getData()), packet.getData());
            this.kill();
        }
    }

    private enum FishHookState {
        FLYING,
        HOOKED_IN_ENTITY,
        BOBBING
    }

    private enum OpenWaterType {
        ABOVE_WATER,
        INSIDE_WATER,
        INVALID
    }

    public record CatchResult(List<ItemStack> items, int rodDamage, int experience) {
        private static final CatchResult EMPTY = new CatchResult(List.of(), 0, 0);

        public boolean hasFish() {
            for (ItemStack item : this.items) {
                if (item.is(ItemTags.FISHES)) {
                    return true;
                }
            }
            return false;
        }

        public void spawnExperience(Level level, Entity owner) {
            if (this.experience > 0) {
                level.addFreshEntity(new ExperienceOrb(level, owner.getX(), owner.getY() + 0.5D, owner.getZ() + 0.5D, this.experience));
            }
        }
    }
}
