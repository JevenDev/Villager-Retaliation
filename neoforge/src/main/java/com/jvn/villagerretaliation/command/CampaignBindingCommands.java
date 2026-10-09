package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.campaign.CampaignLocationBinding;
import com.jvn.villagerretaliation.campaign.CampaignLocationRepairs;
import com.jvn.villagerretaliation.campaign.CampaignLocationSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class CampaignBindingCommands {
    private CampaignBindingCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("binding").requires(source -> source.hasPermission(2))
                .then(literal("location").then(campaign().then(alias().executes(context -> inspect(context, 1))
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> inspect(context, IntegerArgumentType.getInteger(context, "page"))))
                        .then(literal("repair").then(argument("expected", UuidArgument.uuid())
                                .then(argument("operation", UuidArgument.uuid())
                                        .then(argument("position", BlockPosArgument.blockPos())
                                                .then(argument("reason", StringArgumentType.greedyString()).executes(CampaignBindingCommands::repair)))))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> campaign() {
        return argument("campaign", ResourceLocationArgument.id()).suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                CampaignResources.current(context.getSource().getServer()).campaigns().keySet(), builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> alias() {
        return argument("location", StringArgumentType.word()).suggests((context, builder) -> {
            var content = CampaignResources.current(context.getSource().getServer()).campaigns().get(ResourceLocationArgument.getId(context, "campaign"));
            return SharedSuggestionProvider.suggest(content == null ? java.util.Set.<String>of() : content.definition().locations().keySet(), builder);
        });
    }

    private static int inspect(CommandContext<CommandSourceStack> context, int page) {
        var source = context.getSource();
        var data = CampaignLocationSavedData.get(source.getLevel());
        if (!data.diagnostic().isBlank()) { source.sendFailure(Component.literal(data.diagnostic())); return 0; }
        var key = new CampaignLocationBinding.Key(ResourceLocationArgument.getId(context, "campaign"), StringArgumentType.getString(context, "location"));
        var binding = data.bindings().get(key);
        source.sendSuccess(() -> Component.literal("Location " + key.campaign() + " / " + key.alias() + ", token " + data.token(key)
                + (binding == null ? ", unbound" : ", " + binding.dimension() + " " + binding.position().toShortString() + ", " + binding.targetIdentity())), false);
        source.sendSuccess(() -> Component.literal("Repair uses coordinates in your command dimension. It updates quest and scene destinations. "
                + "Planned terrain changes retain their original cells. Existing characters, evidence, rewards, and completed journeys retain their history."), false);
        var history = data.repairs().values().stream().filter(repair -> repair.after().key().equals(key)).toList();
        int pages = Math.max(1, (history.size() + 19) / 20);
        if (page > pages) { source.sendFailure(Component.literal("Choose a history page between 1 and " + pages + ".")); return 0; }
        source.sendSuccess(() -> Component.literal("Saved repairs: " + history.size() + ", page " + page + "/" + pages + ", newest first."), false);
        history.reversed().stream().skip((long) (page - 1) * 20).limit(20).forEach(repair -> source.sendSuccess(() -> Component.literal(
                repair.operation() + ", operator " + repair.operator() + ", time " + repair.time() + ", " + repair.reason()
                        + ", " + (repair.before() == null ? "unbound" : repair.before().position().toShortString())
                        + " -> " + repair.after().position().toShortString()), false));
        return 1;
    }

    private static int repair(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        UUID operator = source.getPlayer() == null ? UUID.nameUUIDFromBytes(("operator/" + source.getTextName()).getBytes(StandardCharsets.UTF_8))
                : source.getPlayer().getUUID();
        try {
            var request = new CampaignLocationRepairs.Request(ResourceLocationArgument.getId(context, "campaign"),
                    StringArgumentType.getString(context, "location"), source.getLevel().dimension().location(),
                    BlockPosArgument.getBlockPos(context, "position"), UuidArgument.getUuid(context, "expected"), UuidArgument.getUuid(context, "operation"),
                    operator, StringArgumentType.getString(context, "reason"));
            var result = CampaignLocationRepairs.repair(source.getServer(), request);
            if (!result.accepted()) { source.sendFailure(Component.literal(result.diagnostic())); return 0; }
            source.sendSuccess(() -> Component.literal("Location binding saved at " + result.binding().position().toShortString()
                    + ", current token " + CampaignLocationSavedData.get(source.getLevel()).token(result.binding().key())
                    + (result.receipt() == null ? ". The destination was already bound." : ", audit " + result.receipt().operation() + ".")), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }
}
