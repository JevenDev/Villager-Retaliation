package com.jvn.villagerretaliation.mixin;

import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(IOWorker.class)
public interface IOWorkerAccessor {
    @Accessor("storage")
    RegionFileStorage villagerretaliation$storage();

    @Invoker("submitTask")
    <T> CompletableFuture<T> villagerretaliation$submitTask(Supplier<Either<T, Exception>> task);
}
