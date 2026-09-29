package com.jvn.villagerretaliation.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.settlement.SettlementOwnershipService;
import com.jvn.villagerretaliation.settlement.SettlementLifecycleService;
import com.jvn.villagerretaliation.settlement.SettlementLifecycleService.Operation;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

final class SettlementCommands {
    private SettlementCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("settlement").executes(context -> {
            var source = context.getSource();
            var player = source.getPlayerOrException();
            var data = SettlementSavedData.get(source.getLevel());
            for (var town : data.accessibleTo(player.getUUID())) {
                source.sendSuccess(() -> Component.literal(town.id().value() + " owner " + town.leader()), false);
            }
            for (var town : data.settlements()) {
                var offer = town.leadershipOffer();
                if (offer != null && offer.successor().equals(player.getUUID()) && source.getLevel().getGameTime() < offer.expiresAt()) {
                    source.sendSuccess(() -> Component.literal(SettlementOwnershipService.instruction(town)), false);
                }
            }
            source.sendSuccess(() -> Component.literal("Use /vr settlement <town UUID> offer <successor UUID>, accept <offer UUID>, cancel <offer UUID>, dissolve, releasePlot <chunkX> <chunkZ>, relocate <x> <y> <z>, or merge <target UUID>. Territory changes return a preview and confirmation token."), false);
            return 1;
        }).then(literal("confirm").then(argument("confirmation", UuidArgument.uuid()).executes(context -> {
            String failure = SettlementLifecycleService.confirm(context.getSource(), UuidArgument.getUuid(context, "confirmation"));
            if (!failure.isEmpty()) {
                context.getSource().sendFailure(Component.literal(failure));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal("Settlement lifecycle change committed."), true);
            return 1;
        }))).then(argument("town", UuidArgument.uuid())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        SettlementSavedData.get(context.getSource().getLevel()).settlements().stream()
                                .filter(town -> context.getSource().hasPermission(2) || context.getSource().getEntity() != null
                                        && (town.leader().equals(context.getSource().getEntity().getUUID())
                                        || town.leadershipOffer() != null && town.leadershipOffer().successor().equals(context.getSource().getEntity().getUUID())))
                                .map(town -> town.id().value().toString()), builder))
                .then(literal("dissolve").executes(context -> lifecycle(context, Operation.DISSOLVE, null, null)))
                .then(literal("abandon").executes(context -> lifecycle(context, Operation.DISSOLVE, null, null)))
                .then(literal("releasePlot").then(argument("chunkX", IntegerArgumentType.integer(-1_874_999, 1_874_999))
                        .then(argument("chunkZ", IntegerArgumentType.integer(-1_874_999, 1_874_999)).executes(context ->
                                lifecycle(context, Operation.RELEASE, null, new BlockPos(
                                        IntegerArgumentType.getInteger(context, "chunkX") * 16, 0,
                                        IntegerArgumentType.getInteger(context, "chunkZ") * 16))))))
                .then(literal("relocate").then(argument("position", BlockPosArgument.blockPos()).executes(context ->
                        lifecycle(context, Operation.RELOCATE, null, BlockPosArgument.getBlockPos(context, "position")))))
                .then(literal("merge").then(argument("target", UuidArgument.uuid()).executes(context ->
                        lifecycle(context, Operation.MERGE, new VillageAllegianceId(UuidArgument.getUuid(context, "target")), null))))
                .then(literal("offer").then(argument("successor", UuidArgument.uuid()).executes(context ->
                        result(context.getSource(), SettlementOwnershipService.offer(context.getSource().getPlayerOrException(),
                                town(context), UuidArgument.getUuid(context, "successor")), "Leadership offer created."))))
                .then(literal("accept").then(argument("offer", UuidArgument.uuid()).executes(context ->
                        result(context.getSource(), SettlementOwnershipService.accept(context.getSource().getPlayerOrException(),
                                town(context), UuidArgument.getUuid(context, "offer")), "Leadership accepted."))))
                .then(literal("cancel").then(argument("offer", UuidArgument.uuid()).executes(context -> {
                    var town = town(context);
                    return result(context.getSource(), town != null && town.cancelLeadership(
                            context.getSource().getPlayerOrException().getUUID(), UuidArgument.getUuid(context, "offer")),
                            "Leadership offer cancelled.");
                }))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return literal("settlement").requires(source -> source.hasPermission(2))
                .then(literal("performance").executes(context -> {
                    var source = context.getSource();
                    source.sendSuccess(() -> Component.literal("Simulation: "
                            + com.jvn.villagerretaliation.settlement.SettlementSimulationService.metrics(source.getLevel())), false);
                    source.sendSuccess(() -> Component.literal("Remote views: "
                            + com.jvn.villagerretaliation.settlement.SettlementViewService.metrics(source.getServer())), false);
                    source.sendSuccess(() -> Component.literal("Times are nanoseconds. The 3 ms simulation target yields between atomic operations."
                            + " Synchronous observations include required unload handoffs and explicit management refreshes."
                            + " Chunk counts are shared explicit tickets, Minecraft may load neighboring dependencies."), false);
                    return 1;
                }))
                .then(literal("recoverOwner").then(argument("town", UuidArgument.uuid())
                        .then(argument("successor", UuidArgument.uuid()).executes(context ->
                                result(context.getSource(), SettlementOwnershipService.previewRecovery(context.getSource(),
                                        town(context), UuidArgument.getUuid(context, "successor")) != null, "Recovery preview ready.")))))
                .then(literal("confirmOwner").then(argument("confirmation", UuidArgument.uuid()).executes(context ->
                        result(context.getSource(), SettlementOwnershipService.confirmRecovery(context.getSource(),
                                UuidArgument.getUuid(context, "confirmation")), "Ownership recovery complete."))));
    }

    private static int lifecycle(CommandContext<CommandSourceStack> context, Operation operation, VillageAllegianceId target, BlockPos position) {
        var source = context.getSource();
        var preview = SettlementLifecycleService.preview(source, new VillageAllegianceId(UuidArgument.getUuid(context, "town")),
                operation, target, position);
        if (!preview.summary().isEmpty()) source.sendSuccess(() -> Component.literal(preview.summary()), false);
        for (String blocker : preview.blockers()) source.sendFailure(Component.literal(blocker));
        if (preview.token() == null) return 0;
        source.sendSuccess(() -> Component.literal("Confirm within 60 seconds with /vr settlement confirm " + preview.token()
                + ". Nothing changes unless you confirm."), false);
        return 1;
    }

    private static SettlementRecord town(CommandContext<CommandSourceStack> context) {
        var id = new VillageAllegianceId(UuidArgument.getUuid(context, "town"));
        return SettlementSavedData.get(context.getSource().getLevel()).get(id).filter(town -> town.id().equals(id)).orElse(null);
    }

    private static int result(CommandSourceStack source, boolean success, String message) {
        if (success) source.sendSuccess(() -> Component.literal(message), false);
        else source.sendFailure(Component.literal("Settlement action refused. Check ownership, successor, expiry and confirmation token. Use /vr admin settlement recovery if data is unavailable."));
        return success ? 1 : 0;
    }
}
