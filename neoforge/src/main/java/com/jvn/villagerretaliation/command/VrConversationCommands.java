package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

final class VrConversationCommands {
    private VrConversationCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("conversation")
                .then(literal("start").then(argument("conversation", ResourceLocationArgument.id())
                        .then(argument("topic", StringArgumentType.word()).executes(context -> show(context,
                                ConversationService.start(context.getSource().getPlayerOrException(), ResourceLocationArgument.getId(context, "conversation"),
                                        StringArgumentType.getString(context, "topic"), UUID.randomUUID()))))))
                .then(literal("show").then(argument("session", UuidArgument.uuid()).executes(context -> show(context,
                        ConversationService.view(context.getSource().getPlayerOrException(), UuidArgument.getUuid(context, "session"))))))
                .then(literal("request").then(argument("session", UuidArgument.uuid())
                        .then(argument("revision", LongArgumentType.longArg(1))
                                .then(argument("intent", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(ConversationSession.Intent.values())
                                                .map(value -> value.name().toLowerCase(java.util.Locale.ROOT)), builder))
                                        .executes(context -> request(context, ""))
                                        .then(argument("argument", StringArgumentType.word()).executes(context -> request(context, StringArgumentType.getString(context, "argument"))))))));
    }

    private static int request(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        ConversationSession.Intent intent;
        try { intent = ConversationSession.Intent.valueOf(StringArgumentType.getString(context, "intent").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Unknown conversation intent."));
            return 0;
        }
        return show(context, ConversationService.request(context.getSource().getPlayerOrException(), UuidArgument.getUuid(context, "session"),
                LongArgumentType.getLong(context, "revision"), UUID.randomUUID(), intent, argument));
    }

    private static int show(CommandContext<CommandSourceStack> context, ConversationService.Result result) {
        if (!result.diagnostic().isBlank()) context.getSource().sendFailure(Component.literal(result.diagnostic()));
        var session = result.session();
        if (session == null) return 0;
        context.getSource().sendSuccess(() -> Component.literal(session.identity().title() + " [" + session.identity().id()
                + "] revision " + session.revision() + ", " + session.phase().name().toLowerCase(java.util.Locale.ROOT)), false);
        if (!session.transcript().isEmpty()) {
            var line = session.transcript().getLast();
            context.getSource().sendSuccess(() -> Component.literal((line.name().isBlank() ? "" : line.name() + ": ") + line.fallback()), false);
        }
        if (session.phase() == ConversationSession.Phase.ROLLED) {
            var check = session.checks().get(session.check());
            context.getSource().sendSuccess(() -> Component.literal("D20 " + check.die() + " against " + check.difficulty()
                    + ": " + (check.success() ? "success" : "failure")), false);
        }
        result.responses().forEach(response -> context.getSource().sendSuccess(() -> Component.literal(response.id() + ": " + response.label()
                + (response.available() ? "" : " [unavailable]")), false));
        if (!session.outcome().isBlank()) context.getSource().sendSuccess(() -> Component.literal("Outcome: " + session.outcome()), false);
        return 1;
    }
}
