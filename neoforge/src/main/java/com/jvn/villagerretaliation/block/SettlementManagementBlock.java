package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.settlement.SettlementManagementService;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.settlement.SettlementLeadershipService;
import com.jvn.villagerretaliation.settlement.SettlementFoundingService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SettlementManagementBlock extends Block {
    public SettlementManagementBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player) {
            report(player, SettlementFoundingService.found(player, pos));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            var result = SettlementFoundingService.found(serverPlayer, pos);
            if (result.town().isPresent()) {
                SettlementManagementService.open(serverPlayer, result.town().orElseThrow().id());
            } else if (result.status() == SettlementFoundingService.Status.INDEPENDENT_VILLAGE) {
                var registry = VillageAllegianceRegistrySavedData.get(serverPlayer.serverLevel());
                var village = registry.discoverAt(serverPlayer.serverLevel(), pos).orElse(null);
                if (serverPlayer.isShiftKeyDown() && village != null
                        && SettlementLeadershipService.accept(serverPlayer, village, pos).isPresent()) {
                    serverPlayer.displayClientMessage(Component.translatable("settlement.villagerretaliation.leadership_accepted"), false);
                    SettlementManagementService.open(serverPlayer, village);
                } else {
                    SettlementLeadershipService.status(serverPlayer, village).ifPresent(status ->
                            serverPlayer.displayClientMessage(Component.translatable("settlement.villagerretaliation.leadership_requirements",
                                    status.reveredAdults(), status.adults(), status.support().housing(), status.support().population(),
                                    Math.min(100L, status.support().sustainedTicks() * 100L / status.requiredTicks())), false));
                }
            } else {
                report(serverPlayer, result);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void report(ServerPlayer player, SettlementFoundingService.Result result) {
        String key = switch (result.status()) {
            case FOUNDED -> "settlement.villagerretaliation.founded";
            case EXISTING_TOWN -> "settlement.villagerretaliation.existing";
            case INDEPENDENT_VILLAGE -> "settlement.villagerretaliation.independent";
            case TOO_CLOSE -> "settlement.villagerretaliation.too_close";
            case UNAVAILABLE -> "settlement.villagerretaliation.unavailable";
        };
        player.displayClientMessage(Component.translatable(key), false);
    }
}
