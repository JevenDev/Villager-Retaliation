package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.settlement.SettlementDepotService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SettlementDepotBlock extends BaseEntityBlock {
    public static final MapCodec<SettlementDepotBlock> CODEC = simpleCodec(SettlementDepotBlock::new);

    public SettlementDepotBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SettlementDepotBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SettlementDepotBlockEntity depot) {
            if (depot.inventory() == null) {
                SettlementDepotService.register(serverPlayer, pos);
            }
            if (depot.stillValid(player)) {
                player.openMenu(depot);
            } else {
                player.displayClientMessage(Component.translatable("settlement.villagerretaliation.depot_unavailable"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (level instanceof ServerLevel serverLevel
                    && level.getBlockEntity(pos) instanceof SettlementDepotBlockEntity depot) {
                SettlementDepotService.destroy(serverLevel, depot);
            }
            super.onRemove(state, level, pos, replacement, moving);
        }
    }
}
