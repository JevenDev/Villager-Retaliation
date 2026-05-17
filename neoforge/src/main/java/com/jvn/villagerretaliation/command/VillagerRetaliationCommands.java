package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class VillagerRetaliationCommands {
    private VillagerRetaliationCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal(VillagerRetaliation.MOD_ID)
                        .requires(source -> source.hasPermission(2))
                        .then(literal("setNearbyReputation")
                                .then(argument("integer", IntegerArgumentType.integer())
                                        .executes(context -> setNearbyReputation(
                                                context,
                                                IntegerArgumentType.getInteger(context, "integer")
                                        ))))
        );
    }

    private static int setNearbyReputation(CommandContext<CommandSourceStack> context, int reputation) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double radius = VillagerRetaliationConfig.WITNESS_RADIUS.get();
        AABB area = player.getBoundingBox().inflate(radius);
        int changedCount = 0;
        int foundCount = 0;

        for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, area, AbstractVillager::isAlive)) {
            foundCount++;
            if (VillagerReputationManager.setReputationForDebug(level, villager, player.getUUID(), reputation)) {
                changedCount++;
            }
        }

        final int totalFound = foundCount;
        final int totalChanged = changedCount;
        source.sendSuccess(
            () -> Component.literal("Set nearby villager/trader reputation to " + reputation
                + " for " + totalFound + " merchants (" + totalChanged + " changed)."),
                true
        );
        return changedCount;
    }
}
