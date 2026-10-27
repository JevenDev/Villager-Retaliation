package com.jvn.villagerretaliation.command;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerTargetArgument implements ArgumentType<String> {
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, VillagerRetaliation.MOD_ID);

    static {
        TYPES.register("villager_target", () -> ArgumentTypeInfos.registerByClass(
                VillagerTargetArgument.class, SingletonArgumentInfo.contextFree(VillagerTargetArgument::new)));
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            int start = reader.getCursor();
            EntityArgument.entities().parse(reader);
            return reader.getString().substring(start, reader.getCursor());
        }
        return reader.readString();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("Alice", "\"Alice Smith\"", "@e[type=minecraft:villager,limit=1]", "01234567-89ab-cdef-0123-456789abcdef");
    }
}
