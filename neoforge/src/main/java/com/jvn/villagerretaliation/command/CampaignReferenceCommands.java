package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

final class CampaignReferenceCommands {
    private static final int PAGE_SIZE = 20;
    private CampaignReferenceCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("references")
                .then(literal("list").executes(context -> list(context, "all", 1))
                        .then(argument("kind", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),
                                                java.util.Arrays.stream(CampaignReferenceReport.Kind.values()).map(value -> value.name().toLowerCase(Locale.ROOT))), builder))
                                .executes(context -> list(context, StringArgumentType.getString(context, "kind"), 1))
                                .then(argument("page", IntegerArgumentType.integer(1)).executes(context ->
                                        list(context, StringArgumentType.getString(context, "kind"), IntegerArgumentType.getInteger(context, "page"))))))
                .then(literal("inspect").then(reference().executes(CampaignReferenceCommands::inspect)))
                .then(query("dependencies", CampaignReferenceReport.Direction.DEPENDENCIES))
                .then(query("dependents", CampaignReferenceReport.Direction.DEPENDENTS));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> reference() {
        return argument("reference", StringArgumentType.string()).suggests((context, builder) -> {
            String prefix = builder.getRemaining();
            if (prefix.startsWith("\"")) prefix = prefix.substring(1);
            String match = prefix;
            report(context.getSource()).nodes().keySet().stream().map(CampaignReferenceReport.Key::text)
                    .filter(value -> value.startsWith(match)).map(StringArgumentType::escapeIfRequired).forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> query(String name, CampaignReferenceReport.Direction direction) {
        return literal(name).then(reference().executes(context -> trace(context, direction, 1, 1))
                .then(argument("depth", IntegerArgumentType.integer(1, 16)).executes(context ->
                        trace(context, direction, IntegerArgumentType.getInteger(context, "depth"), 1))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context ->
                                trace(context, direction, IntegerArgumentType.getInteger(context, "depth"), IntegerArgumentType.getInteger(context, "page"))))));
    }

    private static CampaignReferenceReport report(CommandSourceStack source) {
        return CampaignReferenceReport.build(CampaignResources.current(source.getServer()));
    }

    private static CampaignReferenceReport.Key selected(CommandContext<CommandSourceStack> context, CampaignReferenceReport report) {
        String text = StringArgumentType.getString(context, "reference");
        var key = report.nodes().keySet().stream().filter(value -> value.text().equals(text)).findFirst().orElse(null);
        if (key == null) context.getSource().sendFailure(Component.literal("Unknown story reference. Use campaign references list or command suggestions."));
        return key;
    }

    private static int list(CommandContext<CommandSourceStack> context, String kind, int page) {
        var report = report(context.getSource());
        if (!kind.equals("all") && java.util.Arrays.stream(CampaignReferenceReport.Kind.values()).noneMatch(value -> value.name().equalsIgnoreCase(kind))) {
            context.getSource().sendFailure(Component.literal("Unknown story reference kind."));
            return 0;
        }
        var lines = report.nodes().values().stream().filter(node -> kind.equals("all") || node.key().kind().name().equalsIgnoreCase(kind))
                .map(node -> node.key().text() + (node.missing() ? " [missing content]" : "")).toList();
        return page(context.getSource(), "Story references at content generation " + report.generation(), lines, page);
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        var report = report(context.getSource());
        var key = selected(context, report);
        if (key == null) return 0;
        var node = report.nodes().get(key);
        context.getSource().sendSuccess(() -> Component.literal(key.text()), false);
        if (node.missing()) context.getSource().sendSuccess(() -> Component.literal(
                "No loaded definition. Inspect dependents to find the references, then check datapack diagnostics."), false);
        else node.declarations().stream().limit(PAGE_SIZE).forEach(source ->
                context.getSource().sendSuccess(() -> Component.literal(source.text()), false));
        context.getSource().sendSuccess(() -> Component.literal("Direct dependencies: "
                + report.trace(key, CampaignReferenceReport.Direction.DEPENDENCIES, 1).size() + ", direct dependents: "
                + report.trace(key, CampaignReferenceReport.Direction.DEPENDENTS, 1).size()), false);
        return 1;
    }

    private static int trace(CommandContext<CommandSourceStack> context, CampaignReferenceReport.Direction direction, int depth, int page) {
        var report = report(context.getSource());
        var key = selected(context, report);
        if (key == null) return 0;
        var lines = report.trace(key, direction, depth).stream().map(match -> {
            var reference = match.reference();
            var target = report.nodes().get(reference.to());
            return match.depth() + ": " + reference.from().text() + " " + reference.relation().name().toLowerCase(Locale.ROOT)
                    + " " + reference.to().text() + (target.missing() ? " [missing content]" : "") + "\n" + reference.source().text();
        }).toList();
        return page(context.getSource(), direction.name().toLowerCase(Locale.ROOT) + " of " + key.text(), lines, page);
    }

    private static int page(CommandSourceStack source, String title, List<String> lines, int page) {
        int pages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) { source.sendFailure(Component.literal("Choose a page between 1 and " + pages + ".")); return 0; }
        source.sendSuccess(() -> Component.literal(title + ": " + lines.size() + ", page " + page + "/" + pages), false);
        lines.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(line ->
                source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }
}
