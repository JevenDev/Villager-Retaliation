package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntitySectionManagerAccessor {
    @Accessor("permanentStorage")
    EntityPersistentStorage<Entity> villagerretaliation$permanentStorage();

    @Accessor("sectionStorage")
    EntitySectionStorage<Entity> villagerretaliation$sectionStorage();
}
