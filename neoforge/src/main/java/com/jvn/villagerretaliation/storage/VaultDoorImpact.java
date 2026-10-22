package com.jvn.villagerretaliation.storage;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VaultDoorImpact {
    private static final ResourceKey<DamageType> DAMAGE_TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,VillagerRetaliation.id("vault_door"));
    private static final Vec3[] WORLD_AXES={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
    private final Set<UUID> hit=new HashSet<>();
    private int ticks;

    public static float doorAngle(float openness,float breachProgress) {
        float closed=1-Mth.clamp((openness-0.25F)/0.75F,0,1);
        float intact=1-breachProgress;
        return Mth.lerp(1-intact*intact*intact*intact,-100*(1-closed*closed*closed),-115);
    }

    boolean tick(StorageBlockEntity bank) {
        var level=bank.getLevel();
        var source=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE),bank.getBlockPos().getCenter());
        var facing=bank.getBlockState().getValue(StorageBlock.FACING);
        for(var entity:level.getEntities((Entity)null,new AABB(bank.getBlockPos()).inflate(4),e->e.isAlive() && !e.isSpectator())) {
            if(hit.contains(entity.getUUID())) continue;
            // sample between animation ticks so the fast opening cannot skip an entity
            for(int sample=0;sample<=20;sample++) {
                float progress=(ticks+sample/20F)/10F;
                if(!intersects(bank,entity.getBoundingBox(),bank.getOpenNess(sample/20F),progress)) continue;
                hit.add(entity.getUUID());
                if(entity.hurt(source,12F)) {
                    if(entity instanceof LivingEntity living) living.knockback(0.8,-facing.getStepX(),-facing.getStepZ());
                    if(entity instanceof ServerPlayer player) VillagerReputationAdvancements.onVaultDoorHit(player);
                }
                break;
            }
        }
        return ++ticks>=10;
    }

    static boolean intersects(StorageBlockEntity bank,AABB target,float openness,float progress) {
        var center=transform(bank,new Vec3(0.5,0.5,0.1875),openness,progress);
        var axes=new Vec3[]{
                transform(bank,new Vec3(2,0.5,0.1875),openness,progress).subtract(center),
                transform(bank,new Vec3(0.5,2,0.1875),openness,progress).subtract(center),
                transform(bank,new Vec3(0.5,0.5,0.375),openness,progress).subtract(center)};
        var delta=target.getCenter().subtract(center);
        var half=new Vec3(target.getXsize()/2,target.getYsize()/2,target.getZsize()/2);
        // separating axes keep the rotated door's empty bounding-box corners harmless
        for(var axis:WORLD_AXES) if(separated(axis,delta,half,axes)) return false;
        for(var axis:axes) {
            if(separated(axis,delta,half,axes)) return false;
            for(var worldAxis:WORLD_AXES) if(separated(axis.cross(worldAxis),delta,half,axes)) return false;
        }
        return true;
    }

    private static boolean separated(Vec3 axis,Vec3 delta,Vec3 half,Vec3[] doorAxes) {
        double radius=Math.abs(axis.x)*half.x+Math.abs(axis.y)*half.y+Math.abs(axis.z)*half.z;
        for(var doorAxis:doorAxes) radius+=Math.abs(axis.dot(doorAxis));
        return Math.abs(axis.dot(delta))>radius+1.0E-7;
    }

    private static Vec3 transform(StorageBlockEntity bank,Vec3 point,float openness,float progress) {
        // mirror VaultRenderer's model offsets, sag, upper hinge tilt and outward swing
        double sag=progress*progress, impulse=4*progress*(1-progress);
        double tilt=Math.toRadians(6*sag), angle=Math.toRadians(doorAngle(openness,progress)-25*impulse);
        double x=point.x-1.9375, y=point.y-sag/16-1.4375;
        double tiltedX=x*Math.cos(tilt)-y*Math.sin(tilt);
        double tiltedY=x*Math.sin(tilt)+y*Math.cos(tilt)+1.4375;
        double z=point.z-0.1875;
        double swungX=tiltedX*Math.cos(angle)+z*Math.sin(angle)+1.9375;
        double swungZ=z*Math.cos(angle)-tiltedX*Math.sin(angle)+0.1875-impulse/4;
        var facing=bank.getBlockState().getValue(StorageBlock.FACING);
        var right=facing.getClockWise();
        var pos=bank.getBlockPos();
        return new Vec3(pos.getX()+0.5+(swungX-0.5)*right.getStepX()-(swungZ-0.5)*facing.getStepX(),
                pos.getY()+tiltedY,pos.getZ()+0.5+(swungX-0.5)*right.getStepZ()-(swungZ-0.5)*facing.getStepZ());
    }
}
