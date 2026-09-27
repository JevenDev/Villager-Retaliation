package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Accessor("inGroundTime")
    int villagerretaliation$inGroundTime();

    @Accessor("inGroundTime")
    void villagerretaliation$setInGroundTime(int ticks);

    @org.spongepowered.asm.mixin.gen.Invoker("tickDespawn")
    void villagerretaliation$tickDespawn();

    @org.spongepowered.asm.mixin.gen.Invoker("startFalling")
    void villagerretaliation$startFalling();

    @Accessor("lastState")
    net.minecraft.world.level.block.state.BlockState villagerretaliation$lastState();

    @Accessor("piercingIgnoreEntityIds")
    it.unimi.dsi.fastutil.ints.IntOpenHashSet villagerretaliation$piercingIgnoreEntityIds();

    @Accessor("piercingIgnoreEntityIds")
    void villagerretaliation$setPiercingIgnoreEntityIds(it.unimi.dsi.fastutil.ints.IntOpenHashSet ids);

    @Accessor("inGround")
    boolean villagerretaliation$isInGround();

    @Accessor("inGround")
    void villagerretaliation$setInGround(boolean inGround);
}
