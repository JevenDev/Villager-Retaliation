package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.campaign.CampaignCharacterBinding;
import com.jvn.villagerretaliation.campaign.CampaignCharacterRepairs;
import com.jvn.villagerretaliation.campaign.CampaignCharacterSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class CampaignCharacterBindingCommands {
    private CampaignCharacterBindingCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("character").requires(source -> source.hasPermission(2)).then(campaign().then(role().executes(context -> inspect(context, 1))
                .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> inspect(context, IntegerArgumentType.getInteger(context, "page"))))
                .then(literal("repair").then(argument("expected", UuidArgument.uuid()).then(argument("operation", UuidArgument.uuid())
                        .then(VillagerRetaliationCommands.namedVillagerArgument("target").then(argument("reason", StringArgumentType.greedyString())
                                .executes(CampaignCharacterBindingCommands::repair))))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> campaign() {
        return argument("campaign", ResourceLocationArgument.id()).suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                CampaignResources.current(context.getSource().getServer()).campaigns().keySet(), builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> role() {
        return argument("role", StringArgumentType.word()).suggests((context, builder) -> {
            var content = CampaignResources.current(context.getSource().getServer()).campaigns().get(ResourceLocationArgument.getId(context, "campaign"));
            return SharedSuggestionProvider.suggest(content == null ? java.util.Set.<String>of() : content.definition().characters().keySet(), builder);
        });
    }

    private static int inspect(CommandContext<CommandSourceStack> context, int page) {
        var source = context.getSource();
        var data = CampaignCharacterSavedData.get(source.getLevel());
        if (!data.diagnostic().isBlank()) { source.sendFailure(Component.literal(data.diagnostic())); return 0; }
        var key = new CampaignCharacterBinding.Key(ResourceLocationArgument.getId(context, "campaign"), StringArgumentType.getString(context, "role"));
        var binding = data.bindings().get(key);
        source.sendSuccess(() -> Component.literal("Character " + key.campaign() + " / " + key.role() + ", token " + data.token(key)
                + (binding == null ? ", unbound" : ", entity " + binding.entityId() + ", " + binding.source() + ", lost: " + binding.lost()
                        + ", bound at " + binding.dimension() + " " + binding.position().toShortString())), false);
        source.sendSuccess(() -> Component.literal("Repair accepts a nearby villager name, selector or UUID. Existing-villager roles retain the selected villager's personal state. "
                + "Campaign-owned roles recover their original spawn identity. Saved outcomes, participation, rewards, and authored successor decisions retain their history."), false);
        var successor = data.replacements().get(key);
        if (successor != null) source.sendSuccess(() -> Component.literal("Authored successor: " + successor.replacementRole() + ". Inspect and repair that role."), false);
        var history = data.repairs().values().stream().filter(repair -> repair.after().key().equals(key)).toList();
        int pages = Math.max(1, (history.size() + 19) / 20);
        if (page > pages) { source.sendFailure(Component.literal("Choose a history page between 1 and " + pages + ".")); return 0; }
        source.sendSuccess(() -> Component.literal("Saved repairs: " + history.size() + ", page " + page + "/" + pages + ", newest first."), false);
        history.reversed().stream().skip((long) (page - 1) * 20).limit(20).forEach(repair -> source.sendSuccess(() -> Component.literal(
                repair.operation() + ", operator " + repair.operator() + ", time " + repair.time() + ", " + repair.reason()
                        + ", " + (repair.before() == null ? "unbound" : repair.before().entityId()) + " -> " + repair.after().entityId()), false));
        return 1;
    }

    private static int repair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        try {
            var request = new CampaignCharacterRepairs.Request(ResourceLocationArgument.getId(context, "campaign"),
                    StringArgumentType.getString(context, "role"), VillagerRetaliationCommands.namedEntityId(context, "target"),
                    UuidArgument.getUuid(context, "expected"), UuidArgument.getUuid(context, "operation"),
                    CampaignBindingCommands.operator(source), StringArgumentType.getString(context, "reason"));
            var result = CampaignCharacterRepairs.repair(source.getServer(), request);
            if (!result.accepted()) { source.sendFailure(Component.literal(result.diagnostic())); return 0; }
            source.sendSuccess(() -> Component.literal("Character binding saved for " + result.binding().entityId()
                    + ", current token " + CampaignCharacterSavedData.get(source.getLevel()).token(result.binding().key())
                    + (result.receipt() == null ? ". This identity was already bound." : ", audit " + result.receipt().operation() + ".")), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }
}
