package com.jvn.villagerretaliation.mixin;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AttributeMap.class)
public abstract class AttributeMapMixin implements AttributeMapExtension {
    @Shadow
    @Final
    private Map<Holder<Attribute>, AttributeInstance> attributes;

    @Shadow
    @Final
    private Set<AttributeInstance> attributesToSync;

    @Shadow
    @Final
    private Set<AttributeInstance> attributesToUpdate;

    @Override
    public void villagerretaliation$addAttribute(Holder<Attribute> attribute, double baseValue) {
        if (this.attributes.containsKey(attribute)) {
            return;
        }

        AttributeInstance instance = new AttributeInstance(attribute, modified -> {
            this.attributesToUpdate.add(modified);
            if (modified.getAttribute().value().isClientSyncable()) {
                this.attributesToSync.add(modified);
            }
        });
        instance.setBaseValue(baseValue);
        this.attributes.put(attribute, instance);
    }
}
