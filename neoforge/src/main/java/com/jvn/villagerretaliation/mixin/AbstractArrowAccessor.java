package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Accessor("inGround")
    boolean villagerretaliation$isInGround();

    @Accessor("inGround")
    void villagerretaliation$setInGround(boolean inGround);
}
