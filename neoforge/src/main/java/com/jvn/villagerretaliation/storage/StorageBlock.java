package com.jvn.villagerretaliation.storage;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import com.jvn.villagerretaliation.settlement.BankTreasury;

public final class StorageBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty PROTECTED=BooleanProperty.create("protected"), CHARGE=BooleanProperty.create("charge");
    public static final IntegerProperty DAMAGE=IntegerProperty.create("damage",0,3);
    final int kind;
    public StorageBlock(Properties properties,int kind) {
        super(properties); this.kind=kind;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PROTECTED,kind!=0).setValue(CHARGE,false).setValue(DAMAGE,0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p->new StorageBlock(p,kind)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,PROTECTED,CHARGE,DAMAGE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new StorageBlockEntity(pos,state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide
                ?kind==2?createTickerHelper(type,StorageRegistry.ENTITY.get(),StorageBlockEntity::doorAnimateTick):null
                :createTickerHelper(type,StorageRegistry.ENTITY.get(),StorageBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack) {
        if(level.isClientSide || !(level.getBlockEntity(pos) instanceof StorageBlockEntity bank)) return;
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(data.contains("BankStorage")) bank.place(data.getCompound("BankStorage"),level.registryAccess());
        if(entity instanceof Player player) bank.issue(player);
        VaultConnections.merge(bank); bank.refreshProtection();
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(level.getBlockEntity(pos) instanceof StorageBlockEntity bank) { bank.recheckOpen(); VaultConnections.repairMembership(bank); bank.refreshProtection(); }
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(level.isClientSide) return ItemInteractionResult.SUCCESS;
        if(!(level.getBlockEntity(pos) instanceof StorageBlockEntity bank) || !(player instanceof ServerPlayer server)) return ItemInteractionResult.FAIL;
        bank.issue(player);
        if(bank.controller() && hit.getDirection()==state.getValue(FACING)) {
            if(stack.is(Items.TNT) && !state.getValue(CHARGE) && bank.fuse<0) {
                level.setBlock(pos,state.setValue(CHARGE,true),3); stack.consume(1,player); bank.setChanged(); return ItemInteractionResult.SUCCESS;
            }
            if((stack.is(Items.FLINT_AND_STEEL)||stack.is(Items.FIRE_CHARGE)) && state.getValue(CHARGE) && bank.fuse<0) {
                bank.fuse=80; bank.setChanged();
                if(stack.is(Items.FIRE_CHARGE)) stack.consume(1,player);
                else stack.hurtAndBreak(1,player,LivingEntity.getSlotForHand(hand));
                return ItemInteractionResult.SUCCESS;
            }
            if(stack.is(Items.IRON_BLOCK) && bank.damage>0) {
                if(bank.repair()) stack.consume(1,player);
                else player.displayClientMessage(net.minecraft.network.chat.Component.literal(bank.fuse>=0?"A charge is burning.":"Repair cooldown: "+Math.max(0,(bank.repairAfter-bank.now()+19)/20)+" seconds."),true);
                return ItemInteractionResult.SUCCESS;
            }
        }
        if(player.isShiftKeyDown() && stack.is(StorageRegistry.KEY.get()) && bank.controller() && bank.identity.equals(StorageKeys.identity(stack))) {
            if(!bank.breached() && (bank.treasury==null || BankTreasury.leader(bank,player.getUUID()))) { bank.unlocked=!bank.unlocked; bank.changedLock(); }
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(bank.locked()?"Locked":"Unlocked"),true);
            return ItemInteractionResult.SUCCESS;
        }
        if(player.isShiftKeyDown() && com.jvn.villagerretaliation.interaction.VillagerCurrencyResources.isCurrency(server.getServer(),stack) && bank.controller()) {
            boolean removing=bank.treasury!=null;
            boolean success=removing?BankTreasury.unregister(server,bank):BankTreasury.register(server,bank);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(success?(removing?"Village treasury unregistered":"Village treasury registered"):"Only the village leader with the matching key can register treasury storage here."),true);
            return ItemInteractionResult.SUCCESS;
        }
        bank.open(server); return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof StorageBlockEntity bank) { bank.issue(player); bank.open(server); }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected float getDestroyProgress(BlockState state,Player player,BlockGetter level,BlockPos pos) {
        return state.getValue(PROTECTED)?Blocks.REINFORCED_DEEPSLATE.defaultBlockState().getDestroyProgress(player,level,pos):super.getDestroyProgress(state,player,level,pos);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params) {
        if(kind==2) return List.of(); // Safe pickup is emitted once by onRemove, including non-loot removals.
        var stack=new ItemStack(this);
        if(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof StorageBlockEntity bank) {
            var tag=new net.minecraft.nbt.CompoundTag(); tag.put("BankStorage",bank.pickup(params.getLevel().registryAccess()));
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        }
        return List.of(stack);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving) {
        if(state.is(replacement.getBlock())) return;
        List<BlockPos> previous=List.of();
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof StorageBlockEntity bank) {
            previous=bank.members;
            if(!bank.safe()) Containers.dropContents(level,pos,bank.inventory());
            if(bank.safe()) {
                var pickup=new ItemStack(this);
                var tag=new net.minecraft.nbt.CompoundTag(); tag.put("BankStorage",bank.pickup(level.registryAccess()));
                pickup.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
                popResource(level,pos,pickup);
            }
            BankTreasury.remove(bank);
            if(state.getValue(CHARGE) && bank.fuse<0) popResource(level,pos,new ItemStack(Items.TNT));
        }
        super.onRemove(state,level,pos,replacement,moving);
        if(!level.isClientSide) VaultConnections.reform(level,previous);
    }
}
