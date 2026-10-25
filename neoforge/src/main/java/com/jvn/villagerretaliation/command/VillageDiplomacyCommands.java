package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyApi;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageRelation;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

final class VillageDiplomacyCommands {
    private VillageDiplomacyCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("diplomacy").requires(source -> source.hasPermission(2))
                .then(literal("get").then(village("villageA").then(village("villageB")
                        .executes(VillageDiplomacyCommands::describe))))
                .then(literal("set").then(village("villageA").then(village("villageB")
                        .then(literal("neutral").executes(context -> set(context, VillageRelation.NEUTRAL)))
                        .then(literal("hostile").executes(context -> set(context, VillageRelation.HOSTILE))))))
                .then(literal("truce").then(village("villageA").then(village("villageB")
                        .executes(context -> changed(context, VillageDiplomacyApi.truce(context.getSource().getLevel(),
                                id(context, "villageA"), id(context, "villageB"))))
                        .then(literal("indefinite").executes(context -> truce(context, 0)))
                        .then(argument("ticks", LongArgumentType.longArg(1))
                                .executes(context -> truce(context, LongArgumentType.getLong(context, "ticks")))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, UUID> village(String name) {
        return argument(name, UuidArgument.uuid()).suggests((context, builder) -> {
            var registry = VillageAllegianceRegistrySavedData.get(context.getSource().getLevel());
            for (var village : registry.activeRecords(null)) {
                String id = village.id().toString();
                if (id.startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(id, Component.literal(village.displayName()));
                }
            }
            return builder.buildFuture();
        });
    }

    private static VillageAllegianceId id(CommandContext<CommandSourceStack> context, String name) {
        return new VillageAllegianceId(UuidArgument.getUuid(context, name));
    }

    private static int set(CommandContext<CommandSourceStack> context, VillageRelation relation) {
        return changed(context, VillageDiplomacyApi.set(context.getSource().getLevel(),
                id(context, "villageA"), id(context, "villageB"), relation));
    }

    private static int truce(CommandContext<CommandSourceStack> context, long ticks) {
        return changed(context, VillageDiplomacyApi.truce(context.getSource().getLevel(),
                id(context, "villageA"), id(context, "villageB"), ticks));
    }

    private static int changed(CommandContext<CommandSourceStack> context, VillageDiplomacyApi.ChangeResult result) {
        if (result != VillageDiplomacyApi.ChangeResult.APPLIED) {
            context.getSource().sendFailure(Component.literal("Diplomacy change rejected: " + result));
            return 0;
        }
        return describe(context);
    }

    private static int describe(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var level = source.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var first = registry.canonicalRecord(id(context, "villageA")).filter(record -> !record.archived()).orElse(null);
        var second = registry.canonicalRecord(id(context, "villageB")).filter(record -> !record.archived()).orElse(null);
        if (first == null || second == null || first.id().equals(second.id())) {
            source.sendFailure(Component.literal("Diplomacy requires two distinct active village identities"));
            return 0;
        }
        var relation = VillageDiplomacyApi.query(level, first.id(), second.id());
        String expiry = relation.relation() != VillageRelation.TRUCE ? ""
                : relation.expiresAt().isEmpty() ? " | indefinite | resumes " + relation.resumesAs()
                : " | remaining " + Math.max(0, relation.expiresAt().getAsLong() - level.getServer().overworld().getGameTime())
                        + " ticks | expires at " + relation.expiresAt().getAsLong() + " | resumes " + relation.resumesAs();
        source.sendSuccess(() -> Component.literal(first.displayName() + " [" + first.id() + "] <-> "
                + second.displayName() + " [" + second.id() + "]: " + relation.relation() + expiry), false);
        var data = VillageDiplomacySavedData.get(level);
        if (data.readOnly()) {
            source.sendFailure(Component.literal("Diplomacy storage is read-only"));
        }
        data.diagnostics().forEach(message -> source.sendFailure(Component.literal(message)));
        return 1;
    }
}
