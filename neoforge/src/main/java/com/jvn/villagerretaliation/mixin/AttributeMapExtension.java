package com.jvn.villagerretaliation.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

public interface AttributeMapExtension {
    void villagerretaliation$addAttribute(Holder<Attribute> attribute, double baseValue);
}
