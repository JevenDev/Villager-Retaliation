package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.CampaignStateInspection;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTopicInspection;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
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

final class CampaignCommands {
    private static final int PAGE_SIZE = 20;
    private CampaignCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("campaign").requires(source -> source.hasPermission(2))
                .then(CampaignReferenceCommands.root())
                .then(CampaignBindingCommands.root())
                .then(CampaignFixtureCommands.root())
                .then(literal("state").then(campaign().executes(context -> state(context, 1))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> state(context, page(context))))))
                .then(literal("player").then(campaign().then(player().executes(context -> playerState(context, 1))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> playerState(context, page(context)))))))
                .then(literal("topic").then(argument("conversation", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                QuestContentCatalogs.current(context.getSource().getServer()).conversations().keySet(), builder))
                        .then(argument("topic", StringArgumentType.string()).suggests((context, builder) -> {
                            var definition = ConversationResources.conversation(context.getSource().getServer(),
                                    ResourceLocationArgument.getId(context, "conversation")).orElse(null);
                            String prefix = builder.getRemaining();
                            if (prefix.startsWith("\"")) prefix = prefix.substring(1);
                            String match = prefix;
                            if (definition != null) definition.topics().keySet().stream().filter(value -> value.startsWith(match))
                                    .map(StringArgumentType::escapeIfRequired).forEach(builder::suggest);
                            return builder.buildFuture();
                        }).then(player().executes(context -> topic(context, 1))
                                .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> topic(context, page(context))))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> campaign() {
        return argument("campaign", ResourceLocationArgument.id()).suggests((context, builder) -> {
            var server = context.getSource().getServer();
            var ids = new LinkedHashSet<>(CampaignResources.current(server).campaigns().keySet());
            ids.addAll(CampaignSavedData.get(server.overworld()).instances().keySet());
            return SharedSuggestionProvider.suggestResource(ids, builder);
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, UUID> player() {
        return argument("player", UuidArgument.uuid()).suggests((context, builder) -> {
            var server = context.getSource().getServer();
            var ids = new LinkedHashSet<UUID>();
            server.getPlayerList().getPlayers().forEach(player -> ids.add(player.getUUID()));
            CampaignSavedData.get(server.overworld()).instances().values().forEach(instance -> {
                ids.addAll(instance.players().keySet());
                ids.addAll(instance.facts().players().keySet());
            });
            com.jvn.villagerretaliation.campaign.CampaignStandingSavedData.get(server.overworld()).values().keySet().forEach(key -> ids.add(key.player()));
            return SharedSuggestionProvider.suggest(ids.stream().map(UUID::toString), builder);
        });
    }

    private static int page(CommandContext<CommandSourceStack> context) { return IntegerArgumentType.getInteger(context, "page"); }
    private static CampaignStateInspection.Snapshot campaign(CommandContext<CommandSourceStack> context) {
        return CampaignStateInspection.inspect(context.getSource().getServer(), ResourceLocationArgument.getId(context, "campaign"));
    }

    private static List<String> summary(CampaignStateInspection.Snapshot report) {
        var lines = new ArrayList<String>();
        lines.add("World mode: " + report.mode() + " (selected by " + report.selectionSource() + ")");
        report.diagnostics().forEach(issue -> lines.add("Diagnostic: " + issue));
        if (report.definition() != null) lines.add("Loaded definition: revision " + report.definition().revision()
                + ", fingerprint " + report.definition().fingerprint() + ", source " + report.definition().source());
        if (report.instance() != null) {
            var saved = report.instance();
            lines.add("Saved instance: " + saved.instanceId() + ", " + saved.title() + ", world revision " + saved.worldRevision());
            lines.add("Saved definition: revision " + saved.definitionRevision() + ", fingerprint " + saved.fingerprint());
        } else lines.add("No saved campaign instance.");
        return lines;
    }

    private static int state(CommandContext<CommandSourceStack> context, int page) {
        var report = campaign(context);
        var lines = summary(report);
        if (report.instance() != null) {
            var saved = report.instance();
            lines.add("Shared outcomes: " + saved.outcomes().size() + ", recorded players: " + saved.players().size());
            saved.outcomes().forEach((id, outcome) -> lines.add("Outcome " + id + " = " + outcome.outcome()
                    + ", origin " + outcome.origin() + ", revision " + outcome.revision() + ", operation " + outcome.operationId()));
            saved.facts().world().forEach((id, value) -> lines.add("World fact " + id + " = " + value.text() + " (" + value.kind() + ")"));
            lines.add("Fact receipts: " + saved.facts().receipts().size());
        }
        for (var character : report.characters()) {
            lines.add("Character " + character.role() + ": " + character.status());
            if (character.saved() != null) lines.add("Saved identity: " + character.saved().entityId() + ", "
                    + character.saved().dimension() + " " + character.saved().position().toShortString() + ", loss recorded: " + character.saved().lost());
            if (character.effective() != null && !character.effective().equals(character.saved())) {
                lines.add("Effective replacement: " + character.effective().role() + " = " + character.effective().entityId());
            }
            if (!character.diagnostic().isBlank()) lines.add("Character diagnostic: " + character.diagnostic());
        }
        for (var location : report.locations()) {
            lines.add("Location " + location.alias() + ": " + (location.saved() == null ? "unbound"
                    : location.saved().dimension() + " " + location.saved().position().toShortString() + ", target " + location.saved().targetIdentity()));
            if (!location.diagnostic().isBlank()) lines.add("Location diagnostic: " + location.diagnostic());
        }
        report.conversations().forEach(session -> session(lines, session));
        return show(context.getSource(), report.campaign() + " " + report.status(), lines, page);
    }

    private static int playerState(CommandContext<CommandSourceStack> context, int page) {
        var report = campaign(context);
        var player = report.player(UuidArgument.getUuid(context, "player"));
        var lines = summary(report);
        boolean online = context.getSource().getServer().getPlayerList().getPlayer(player.id()) != null;
        lines.add("Player " + player.id() + " (" + (online ? "online" : "offline") + "), recorded: " + player.recorded()
                + ", personal revision " + player.progress().revision());
        lines.add("Participation: " + player.progress().participation().stream().sorted().toList());
        player.progress().discoveredOutcomes().forEach((id, outcome) -> lines.add("Discovered " + id + " = " + outcome));
        player.progress().milestones().forEach((id, progress) -> {
            lines.add("Milestone " + id + ": " + progress.route() + ", outcome " + progress.outcome()
                    + ", completed: " + progress.completed() + ", world operation " + progress.worldOperation());
            var entry = player.entries().get(id);
            if (entry != null) lines.add("Personal objective entry: " + entry.quest() + " / " + entry.stage());
        });
        player.progress().completedQuests().stream().sorted().forEach(quest -> lines.add("Completed quest: " + quest));
        player.facts().forEach((id, value) -> lines.add("Personal fact " + id + " = " + value.text() + " (" + value.kind() + ")"));
        player.chapters().forEach((id, chapter) -> lines.add("Chapter " + id + ": prerequisites satisfied: " + chapter.available()
                + ", completed: " + chapter.completed() + (chapter.blockers().isEmpty() ? "" : ", " + String.join(", ", chapter.blockers()))));
        if (player.chapters().isEmpty() && report.definition() != null) lines.add("Chapter prerequisites were not evaluated against incompatible or unreadable state.");
        var standingData = com.jvn.villagerretaliation.campaign.CampaignStandingSavedData.get(context.getSource().getServer().overworld());
        if (report.definition() != null) com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.all(report.definition()).forEach(standing -> {
            var reading = com.jvn.villagerretaliation.campaign.CampaignStandingService.read(context.getSource().getServer(),
                    report.campaign(), player.id(), standing.scope(), standing.id());
            lines.add("Current " + standing.scope() + " standing " + standing.id() + ": " + (reading.diagnostic().isBlank()
                    ? reading.value() + ", reaction " + reading.reaction().id() : reading.diagnostic()));
        });
        standingData.values().forEach((key, value) -> {
            if (!key.campaign().equals(report.campaign()) || !key.player().equals(player.id())) return;
            lines.add("Saved " + key.scope() + " standing " + key.subject() + " (" + value.subject().title() + ") = " + value.amount()
                    + ", revision " + value.revision() + ", instance " + key.instance()
                    + (key.character() == null ? "" : ", character " + key.character()));
        });
        standingData.receipts().values().stream().filter(receipt -> receipt.change().campaign().equals(report.campaign())
                && receipt.change().player().equals(player.id())).forEach(receipt -> lines.add("Standing operation " + receipt.operation()
                + ": " + receipt.change().scope() + "/" + receipt.change().subject() + ", " + receipt.before() + " -> " + receipt.after()
                + ", quest " + receipt.change().quest() + ", run " + receipt.change().questRun()));
        return show(context.getSource(), report.campaign() + " personal progress", lines, page);
    }

    private static int topic(CommandContext<CommandSourceStack> context, int page) {
        var report = ConversationTopicInspection.inspect(context.getSource().getServer(), ResourceLocationArgument.getId(context, "conversation"),
                StringArgumentType.getString(context, "topic"), UuidArgument.getUuid(context, "player"));
        var lines = new ArrayList<String>();
        lines.add("Player: " + report.player());
        if (report.definition() != null) {
            lines.add("Source: " + report.definition().source().resource() + " [" + report.definition().source().pack() + "]");
            lines.add("Campaign: " + report.definition().campaign() + ", role: " + report.definition().character());
        }
        report.gates().forEach(gate -> lines.add(gate.id() + ": " + gate.state() + " - " + gate.message()));
        for (var condition : report.conditions()) {
            lines.add(condition.pointer());
            trace(lines, condition.trace(), 0);
        }
        if (report.session() != null) session(lines, report.session());
        return show(context.getSource(), report.conversation() + " / " + report.topic() + ": " + report.status(), lines, page);
    }

    private static void trace(List<String> lines, DialogueCondition.ConditionEvaluationTrace trace, int depth) {
        lines.add("  ".repeat(depth) + trace.canonicalTypeId() + ": " + trace.outcome() + " - " + trace.message());
        trace.children().forEach(child -> trace(lines, child, depth + 1));
    }

    private static void session(List<String> lines, ConversationSession.Snapshot session) {
        lines.add("Conversation " + session.identity().id() + ": " + session.identity().conversation() + ", topic " + session.identity().topic()
                + ", node " + session.node() + ", " + session.phase() + ", revision " + session.revision());
        lines.add("Controller: " + session.controller() + ", participants: " + session.participants().stream().sorted().toList()
                + ", choices: " + session.choices().size() + ", receipts: " + session.receipts().size());
        session.checks().forEach((id, check) -> {
            lines.add("Saved check " + id + ": die " + check.die() + ", modifier " + check.modifier()
                    + ", total " + check.total() + ", difficulty " + check.difficulty() + ", success: " + check.success() + ", request " + check.request());
            if (check.social() != null) lines.add("  Skill " + check.social().skill().id() + ": " + check.social().bonus());
            check.modifiers().forEach(modifier -> lines.add("  Modifier " + modifier.id() + " (" + modifier.label() + "): "
                    + modifier.amount() + ", applied: " + modifier.applied()));
        });
        if (!session.diagnostic().isBlank()) lines.add("Conversation diagnostic: " + session.diagnostic());
    }

    private static int show(CommandSourceStack source, String title, List<String> lines, int page) {
        int pages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) { source.sendFailure(Component.literal("Choose a page between 1 and " + pages + ".")); return 0; }
        source.sendSuccess(() -> Component.literal(title + ", page " + page + "/" + pages), false);
        lines.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE)
                .forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }
}
