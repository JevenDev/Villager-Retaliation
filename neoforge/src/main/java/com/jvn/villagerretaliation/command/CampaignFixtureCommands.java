package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.campaign.CampaignFixtureService;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class CampaignFixtureCommands {
    private CampaignFixtureCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        var operation = argument("operation", UuidArgument.uuid())
                .then(argument("reason", StringArgumentType.greedyString()).executes(CampaignFixtureCommands::enter));
        var enter = campaign().then(entry().then(player().then(argument("expected", UuidArgument.uuid()).then(operation))));
        return literal("fixture").requires(source -> source.hasPermission(2))
                .then(literal("inspect").then(campaign().then(entry().then(player().executes(context -> inspect(context, 1))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> inspect(context, page(context))))))))
                .then(literal("enter").then(enter))
                .then(literal("history").then(campaign().executes(context -> history(context, 1))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> history(context, page(context))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> campaign() {
        return argument("campaign", ResourceLocationArgument.id()).suggests((context, builder) -> {
            var server = context.getSource().getServer();
            var ids = new LinkedHashSet<>(CampaignResources.current(server).campaigns().keySet());
            ids.addAll(CampaignSavedData.get(server.overworld()).instances().keySet());
            return SharedSuggestionProvider.suggestResource(ids, builder);
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> entry() {
        return argument("entry", StringArgumentType.word()).suggests((context, builder) -> {
            var content = CampaignResources.current(context.getSource().getServer()).campaigns().get(ResourceLocationArgument.getId(context, "campaign"));
            return SharedSuggestionProvider.suggest(content == null ? java.util.Set.<String>of() : content.definition().fixtureEntries().keySet(), builder);
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, UUID> player() {
        return argument("player", UuidArgument.uuid()).suggests((context, builder) -> {
            var server = context.getSource().getServer();
            var ids = new LinkedHashSet<UUID>();
            server.getPlayerList().getPlayers().forEach(player -> ids.add(player.getUUID()));
            var instance = CampaignSavedData.get(server.overworld()).instances().get(ResourceLocationArgument.getId(context, "campaign"));
            if (instance != null) ids.addAll(instance.players().keySet());
            return SharedSuggestionProvider.suggest(ids.stream().map(UUID::toString), builder);
        });
    }

    private static int inspect(CommandContext<CommandSourceStack> context, int page) {
        var source = context.getSource();
        var preview = CampaignFixtureService.inspect(source.getServer(), ResourceLocationArgument.getId(context, "campaign"),
                StringArgumentType.getString(context, "entry"), UuidArgument.getUuid(context, "player"));
        var lines = new ArrayList<String>();
        lines.add("Target player: " + UuidArgument.getUuid(context, "player") + ". Expected token: " + preview.token());
        lines.addAll(preview.effects());
        int result = show(source, "Fixture preview", lines, page);
        if (!preview.diagnostic().isBlank()) { source.sendFailure(Component.literal(preview.diagnostic())); return 0; }
        return result;
    }

    private static int enter(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        try {
            var request = new CampaignFixtureService.Request(ResourceLocationArgument.getId(context, "campaign"),
                    StringArgumentType.getString(context, "entry"), UuidArgument.getUuid(context, "player"),
                    UuidArgument.getUuid(context, "expected"), UuidArgument.getUuid(context, "operation"),
                    CampaignBindingCommands.operator(source), StringArgumentType.getString(context, "reason"));
            var result = CampaignFixtureService.enter(source.getServer(), request);
            if (result.receipt() != null) source.sendSuccess(() -> Component.literal("Fixture " + result.receipt().entry()
                    + " saved for " + result.receipt().player() + ", chapter " + result.receipt().chapter() + ", audit " + result.receipt().operation() + "."), true);
            if (!result.ready()) { source.sendFailure(Component.literal(result.diagnostic())); return 0; }
            source.sendSuccess(() -> Component.literal("Fixture chapter is ready. Its quests use their ordinary offers and impact confirmations."), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int history(CommandContext<CommandSourceStack> context, int page) {
        var source = context.getSource();
        var data = CampaignSavedData.get(source.getLevel());
        if (!data.diagnostic().isBlank()) { source.sendFailure(Component.literal(data.diagnostic())); return 0; }
        var campaign = ResourceLocationArgument.getId(context, "campaign");
        var instance = data.instances().get(campaign);
        var lines = new ArrayList<String>();
        if (instance != null) {
            for (var receipt : new ArrayList<>(instance.fixtureEntries().values()).reversed()) {
                lines.add("Audit " + receipt.operation() + ": " + receipt.entry() + ", chapter " + receipt.chapter()
                        + ", player " + receipt.player() + ", operator " + receipt.operator() + ", time " + receipt.time() + ", " + receipt.reason());
                for (var effect : receipt.effects()) lines.add(effect.id() + ": " + effect.kind() + " " + effect.target()
                        + (effect.value().isEmpty() ? "" : " = " + effect.value()) + ", " + (effect.applied() ? "applied" : "already satisfied")
                        + ", receipt " + effect.operation());
            }
        }
        if (lines.isEmpty()) lines.add("No fixture entry audit is saved.");
        return show(source, campaign + " fixture history", lines, page);
    }

    private static int page(CommandContext<CommandSourceStack> context) { return IntegerArgumentType.getInteger(context, "page"); }

    private static int show(CommandSourceStack source, String title, List<String> lines, int page) {
        int pages = Math.max(1, (lines.size() + 19) / 20);
        if (page > pages) { source.sendFailure(Component.literal("Choose a page between 1 and " + pages + ".")); return 0; }
        source.sendSuccess(() -> Component.literal(title + ", page " + page + "/" + pages), false);
        lines.stream().skip((long) (page - 1) * 20).limit(20).forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }
}
