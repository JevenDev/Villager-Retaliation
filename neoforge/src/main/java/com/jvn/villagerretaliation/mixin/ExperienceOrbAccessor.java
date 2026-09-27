package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
    @Accessor("age")
    int villagerretaliation$getAge();

    @Accessor("age")
    void villagerretaliation$setAge(int age);

    @Accessor("count")
    int villagerretaliation$getCount();

    @Accessor("count")
    void villagerretaliation$setCount(int count);

    @Accessor("followingPlayer")
    Player villagerretaliation$getFollowingPlayer();

    @Accessor("followingPlayer")
    void villagerretaliation$setFollowingPlayer(Player player);
}
