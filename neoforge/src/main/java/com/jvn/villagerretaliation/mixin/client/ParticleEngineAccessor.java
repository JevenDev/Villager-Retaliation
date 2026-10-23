package com.jvn.villagerretaliation.mixin.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Invoker("makeParticle")
    Particle villagerretaliation$makeParticle(ParticleOptions type, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed);
}
