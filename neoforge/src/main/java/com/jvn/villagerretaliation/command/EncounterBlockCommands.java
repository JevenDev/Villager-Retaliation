package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.scene.encounter.EncounterBlockOperatorService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class EncounterBlockCommands {
    private EncounterBlockCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("encounterBlock").requires(source -> source.hasPermission(2))
                .then(literal("inspect").then(argument("encounter_id", StringArgumentType.word())
                        .then(argument("block_id", StringArgumentType.word()).executes(context -> run(context, false)))))
                .then(literal("preserve").then(argument("encounter_id", StringArgumentType.word())
                        .then(argument("block_id", StringArgumentType.word())
                                .then(argument("reason", StringArgumentType.greedyString()).executes(context -> run(context, true))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean preserve) {
        UUID encounter;
        try { encounter = UUID.fromString(StringArgumentType.getString(context, "encounter_id")); }
        catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("encounter_id must be a UUID."));
            return 0;
        }
        var source = context.getSource();
        var block = StringArgumentType.getString(context, "block_id");
        if (!preserve) {
            EncounterBlockOperatorService.inspect(source.getLevel(), encounter, block)
                    .forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
            return 1;
        }
        var result = EncounterBlockOperatorService.preserve(source.getLevel(), encounter, block,
                StringArgumentType.getString(context, "reason"), source.getTextName());
        if (result.success()) source.sendSuccess(() -> Component.literal(result.message()), true);
        else source.sendFailure(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }
}
