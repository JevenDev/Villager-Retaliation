package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.campaign.CampaignLocationVariants;
import com.jvn.villagerretaliation.campaign.CampaignVariantSavedData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class CampaignLocationCommands {
    private CampaignLocationCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("campaignLocation").requires(source -> source.hasPermission(2))
                .then(literal("list").executes(context -> list(context.getSource(), 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
                .then(literal("inspect").then(argument("operation", StringArgumentType.word()).executes(context -> run(context, false))))
                .then(literal("preserve").then(argument("operation", StringArgumentType.word())
                        .then(argument("reason", StringArgumentType.greedyString()).executes(context -> run(context, true)))));
    }

    private static int list(CommandSourceStack source, int page) {
        var data = CampaignVariantSavedData.get(source.getLevel());
        if (!data.diagnostic().isBlank()) { source.sendFailure(Component.literal(data.diagnostic())); return 0; }
        int pages = Math.max(1, (data.receipts().size() + 19) / 20);
        if (page > pages) { source.sendFailure(Component.literal("Choose a page between 1 and " + pages + ".")); return 0; }
        var receipts = data.receipts().values().stream().skip((long) (page - 1) * 20).limit(20).toList();
        source.sendSuccess(() -> Component.literal("Campaign location changes: " + data.receipts().size() + ", page " + page + "/" + pages), false);
        receipts.forEach(receipt -> source.sendSuccess(() -> Component.literal(
                receipt.operation() + " " + receipt.campaign() + " " + receipt.variant() + " "
                        + (receipt.complete() ? "complete" : "pending") + " " + receipt.diagnostic()), false));
        return receipts.size();
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean preserve) {
        var source = context.getSource();
        UUID operation;
        try { operation = UUID.fromString(StringArgumentType.getString(context, "operation")); }
        catch (IllegalArgumentException exception) { source.sendFailure(Component.literal("operation must be a UUID.")); return 0; }
        var data = CampaignVariantSavedData.get(source.getLevel());
        if (!data.diagnostic().isBlank()) { source.sendFailure(Component.literal(data.diagnostic())); return 0; }
        var receipt = data.receipts().get(operation);
        if (receipt == null) { source.sendFailure(Component.literal("No saved location change has that operation ID.")); return 0; }
        if (!preserve) {
            source.sendSuccess(() -> Component.literal(receipt.campaign() + " / " + receipt.milestone() + " / " + receipt.variant()
                    + "\nApplied cells: " + receipt.cursor() + "/" + receipt.cells().size() + "\n" + receipt.diagnostic()), false);
            receipt.cells().forEach(cell -> source.sendSuccess(() -> Component.literal(cell.generated().cell() + " at "
                    + cell.generated().position().toShortString() + ": " + cell.requested().original().state() + " -> " + cell.requested().placed().state()), false));
            if (receipt.audit() != null) source.sendSuccess(() -> Component.literal("Preserved by " + receipt.audit().operator()
                    + " at " + receipt.audit().time() + ": " + receipt.audit().reason()), false);
            return 1;
        }
        UUID operator = source.getPlayer() == null ? UUID.nameUUIDFromBytes(("operator/" + source.getTextName()).getBytes(StandardCharsets.UTF_8))
                : source.getPlayer().getUUID();
        String issue = CampaignLocationVariants.preserve(source.getServer(), operation, operator, StringArgumentType.getString(context, "reason"));
        if (!issue.isBlank()) { source.sendFailure(Component.literal(issue)); return 0; }
        source.sendSuccess(() -> Component.literal("Preserved the current terrain and saved the location repair audit."), true);
        return 1;
    }
}
