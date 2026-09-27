package com.jvn.villagerretaliation.mixin;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityCollisionAccessor {
    @Accessor("onGround")
    void villagerretaliation$setOnGround(boolean onGround);

    @Invoker("collideWithShapes")
    static Vec3 villagerretaliation$collide(Vec3 movement, AABB bounds, List<VoxelShape> shapes) {
        throw new AssertionError();
    }
}
