package com.jvn.villagerretaliation.storage;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("bank_storage")
@PrefixGameTestTemplate(false)
public final class BankStorageGameTests {
    public static StorageBlockEntity place(GameTestHelper h,BlockPos relative,Block block) {
        if(block==StorageRegistry.CONTROLLER.get()) return vault(h,relative,Direction.NORTH);
        return placeRaw(h,relative,block);
    }
    private static StorageBlockEntity placeRaw(GameTestHelper h,BlockPos relative,Block block) {
        var pos=h.absolutePos(relative); h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        var bank=(StorageBlockEntity)h.getLevel().getBlockEntity(pos); VaultConnections.merge(bank); return bank;
    }
    public static StorageBlockEntity vault(GameTestHelper h,BlockPos relative,Direction facing) {
        var controller=h.absolutePos(relative); var center=controller.relative(facing.getOpposite());
        for(var pos:BlockPos.betweenClosed(center.offset(-1,-1,-1),center.offset(1,1,1)))
            h.getLevel().setBlockAndUpdate(pos,(pos.equals(controller)?StorageRegistry.CONTROLLER.get():StorageRegistry.VAULT.get())
                    .defaultBlockState().setValue(StorageBlock.FACING,facing));
        var bank=(StorageBlockEntity)h.getLevel().getBlockEntity(controller); VaultConnections.merge(bank); return bank;
    }
    public static Container display(StorageBlockEntity bank) {
        var client=new StorageBlockEntity(bank.getBlockPos(),bank.getBlockState());
        client.handleUpdateTag(bank.getUpdateTag(bank.getLevel().registryAccess()),bank.getLevel().registryAccess());
        return client.vaultDisplay();
    }
    public static ServerPlayer player(GameTestHelper h,StorageBlockEntity bank) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BankTest")); p.setPos(bank.getBlockPos().getCenter()); return p;
    }
    @GameTest(template="empty") public static void safeDoorTracksAllViewersAndRecoversAbandonedMenus(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get());
        var first=player(h,bank); var second=player(h,bank); var registries=h.getLevel().registryAccess();
        h.assertTrue(bank.createMenu(1,first.getInventory(),first)==null,"locked safe refuses a viewer");
        h.assertFalse(bank.getUpdateTag(registries).getBoolean("DoorOpen"),"denied access leaves door shut");
        bank.unlocked=true;
        var firstMenu=(StorageMenu)bank.createMenu(1,first.getInventory(),first); first.containerMenu=firstMenu;
        var secondMenu=(StorageMenu)bank.createMenu(2,second.getInventory(),second); second.containerMenu=secondMenu;
        h.assertTrue(bank.getUpdateTag(registries).getBoolean("DoorOpen"),"successful menu opens the door");
        firstMenu.removed(first); first.containerMenu=first.inventoryMenu;
        h.assertTrue(bank.getUpdateTag(registries).getBoolean("DoorOpen"),"second viewer keeps the door open");
        secondMenu.removed(second); second.containerMenu=second.inventoryMenu;
        h.assertFalse(bank.getUpdateTag(registries).getBoolean("DoorOpen"),"last viewer closes the door");
        first.containerMenu=new StorageMenu(3,first.getInventory(),bank);
        bank.recheckOpen();
        h.assertFalse(bank.getUpdateTag(registries).getBoolean("DoorOpen"),"viewer absent from the level cannot leave door stuck open");
        first.containerMenu=first.inventoryMenu; h.succeed();
    }
    @GameTest(template="empty") public static void safeDoorAnimationInterpolatesAndChunkSyncPreservesInventory(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get());
        bank.inventory().setItem(53,new ItemStack(Items.DIAMOND,7)); var identity=bank.identity;
        var update=new net.minecraft.nbt.CompoundTag(); update.putBoolean("DoorOpen",true);
        bank.handleUpdateTag(update,h.getLevel().registryAccess());
        StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertTrue(bank.getOpenNess(1)>0 && bank.getOpenNess(1)<1,"opening advances gradually");
        h.assertTrue(bank.getOpenNess(0.5F)>bank.getOpenNess(0) && bank.getOpenNess(0.5F)<bank.getOpenNess(1),"partial ticks interpolate the swing");
        for(int i=0;i<10;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getOpenNess(1),1F,"door reaches fully open");
        bank.triggerEvent(1,0);
        for(int i=0;i<11;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getOpenNess(1),0F,"door returns fully shut");
        h.assertValueEqual(bank.identity,identity,"visual update preserves safe identity");
        h.assertValueEqual(bank.inventory().getItem(53).getCount(),7,"visual update preserves stored items"); h.succeed();
    }
    @GameTest(template="empty") public static void vaultDisplayAggregatesMembersWithoutChangingStoredStacks(GameTestHelper h) {
        var bank=vault(h,new BlockPos(2,3,1),Direction.NORTH); var members=bank.group();
        members.getFirst().inventory().setItem(26,new ItemStack(Items.DIAMOND,3));
        members.getLast().inventory().setItem(26,new ItemStack(Items.DIAMOND,7));
        bank.inventory().setItem(0,new ItemStack(Items.GOLD_BLOCK,64));
        var named=new ItemStack(Items.DIAMOND,2); named.set(DataComponents.CUSTOM_NAME,Component.literal("Keepsake"));
        bank.inventory().setItem(1,named);
        var registries=h.getLevel().registryAccess(); var tag=bank.getUpdateTag(registries);
        h.assertFalse(tag.contains("Items") || tag.contains("Identity") || tag.contains("Members"),"display sync excludes persistent storage and lock data");
        var client=new StorageBlockEntity(bank.getBlockPos(),bank.getBlockState());
        client.localInventory().setItem(26,new ItemStack(Items.EMERALD,11)); var identity=client.identity;
        client.onDataPacket(null,bank.getUpdatePacket(),registries);
        h.assertValueEqual(client.vaultDisplay().getItem(0).getCount(),10,"matching stacks across first and last members share a pile");
        h.assertTrue(client.vaultDisplay().getItem(1).is(Items.GOLD_BLOCK),"block items keep their actual model");
        h.assertTrue(ItemStack.matches(client.vaultDisplay().getItem(2),named),"components remain distinct and survive packet sync");
        h.assertValueEqual(client.localInventory().getItem(26).getCount(),11,"display packets preserve local inventory");
        h.assertValueEqual(client.identity,identity,"display packets preserve key identity");
        h.assertFalse(bank.saveWithoutMetadata(registries).contains("VaultDisplay"),"display summaries are never saved as inventory");
        h.assertValueEqual(members.getFirst().inventory().getItem(26).getCount(),3,"aggregation leaves original counts untouched");
        h.runAfterDelay(6,()->{
            h.assertValueEqual(bank.vaultDisplay().getItem(0).getCount(),10,"server ticks publish contents without opening a menu");
            members.getFirst().inventory().clearContent(); members.getLast().inventory().clearContent(); bank.inventory().clearContent();
            bank.getUpdateTag(registries);
            h.runAfterDelay(6,()->{h.assertTrue(bank.vaultDisplay().isEmpty(),"chunk snapshots do not suppress periodic display updates"); h.succeed();});
        });
    }
    @GameTest(template="empty") public static void vaultDisplayBoundsDistinctPilesAndClearsIncompleteVaults(GameTestHelper h) {
        var bank=vault(h,new BlockPos(2,3,1),Direction.WEST); var first=bank.group().getFirst();
        var items=List.of(Items.DIAMOND,Items.EMERALD,Items.GOLD_INGOT,Items.IRON_INGOT,Items.COPPER_INGOT,
                Items.GOLD_BLOCK,Items.IRON_BLOCK,Items.DIAMOND_BLOCK,Items.COAL,Items.REDSTONE,Items.LAPIS_LAZULI);
        for(int i=0;i<items.size();i++) first.inventory().setItem(i,new ItemStack(items.get(i),64));
        bank.group().getLast().inventory().setItem(26,new ItemStack(Items.DIAMOND,64));
        var registries=h.getLevel().registryAccess(); var tag=bank.getUpdateTag(registries);
        h.assertValueEqual(tag.getList("VaultDisplay",net.minecraft.nbt.Tag.TAG_COMPOUND).size(),9,"summary is bounded to nine representative piles");
        h.assertValueEqual(display(bank).getItem(0).getCount(),64,"large quantities cap visual density");
        h.getLevel().destroyBlock(bank.group().getLast().getBlockPos(),false);
        h.assertTrue(display(bank).isEmpty(),"incomplete vault has no display contents"); h.succeed();
    }
    @GameTest(template="empty") public static void mountedChargeLeavesSafeBreachedUntilRepair(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get());
        bank.inventory().setItem(53,new ItemStack(Items.DIAMOND,4)); var identity=bank.identity;
        h.getLevel().setBlockAndUpdate(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.CHARGE,true));
        bank.fuse=1; StorageBlockEntity.tick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertTrue(h.getLevel().getBlockState(bank.getBlockPos()).is(StorageRegistry.SAFE.get()),"charge leaves safe and door in place");
        h.assertTrue(bank.breached() && bank.getBlockState().getValue(StorageBlock.DAMAGE)==1,"charge synchronizes breached block state");
        h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"detonated charge is removed");
        for(int i=0;i<11;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getBreachProgress(1),1F,"breached pose reaches fully open without a viewer");
        bank.triggerEvent(1,0);
        for(int i=0;i<11;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getBreachProgress(1),1F,"closing a menu cannot close a breached door");
        var saved=bank.saveWithoutMetadata(h.getLevel().registryAccess()); bank.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(bank.breached(),"breach survives save and reload");
        h.assertFalse(bank.repair(),"visual damage does not bypass repair cooldown");
        bank.repairAfter=bank.now(); h.assertTrue(bank.repair(),"repair succeeds after cooldown");
        for(int i=0;i<11;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getBreachProgress(1),0F,"repair restores intact pose");
        h.assertTrue(bank.locked(),"repair restores the safe lock");
        h.assertValueEqual(bank.inventory().getItem(53).getCount(),4,"blast and repair preserve stored items");
        h.assertValueEqual(bank.identity,identity,"blast and repair preserve the key identity"); h.succeed();
    }
    @GameTest(template="empty") public static void commandAndBuilderPlacementsAutomaticallyForm(GameTestHelper h) {
        var start=h.absolutePos(new BlockPos(1,2,1));
        for(int y=0;y<3;y++) for(int z=0;z<3;z++) for(int x=0;x<3;x++)
            h.getLevel().setBlockAndUpdate(start.offset(x,y,z),(x==1 && y==1 && z==0?StorageRegistry.CONTROLLER.get():StorageRegistry.VAULT.get()).defaultBlockState());
        h.runAfterDelay(3,()->{
            var bank=(StorageBlockEntity)h.getLevel().getBlockEntity(start);
            h.assertValueEqual(bank.group().size(),27,"full command/builder placement forms without a player hook"); h.succeed();
        });
    }
    @GameTest(template="empty") public static void rectangularCapacityAndBounds(GameTestHelper h) {
        var bank=vault(h,new BlockPos(2,3,1),Direction.NORTH);
        h.assertValueEqual(bank.group().size(),27,"full cube has 27 blocks");
        h.assertValueEqual(bank.group().stream().mapToInt(b->b.inventory().getContainerSize()).sum(),729,"controller contributes 27 slots");
        var extra=place(h,new BlockPos(4,2,1),StorageRegistry.VAULT.get());
        h.assertValueEqual(extra.group().size(),1,"fourth dimension block stays separate");
        h.assertValueEqual(bank.group().size(),27,"full cube remains stable"); h.succeed();
    }
    @GameTest(template="empty") public static void controlledNeighborsNeverMerge(GameTestHelper h) {
        var a=placeRaw(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get());
        var b=placeRaw(h,new BlockPos(2,2,1),StorageRegistry.CONTROLLER.get());
        a.inventory().setItem(0,new ItemStack(Items.DIAMOND,2)); b.inventory().setItem(0,new ItemStack(Items.GOLD_INGOT,3));
        h.assertValueEqual(a.group().size(),1,"touching controllers stay separate");
        h.assertTrue(!a.identity.equals(b.identity),"independent keys");
        var p=player(h,b); a.issue(p); h.assertFalse(b.allowed(p),"neighbor key never opens second vault"); h.succeed();
    }
    @GameTest(template="empty") public static void splitRetainsPerBlockSlotsAndDropsOnlyRemovedMember(GameTestHelper h) {
        var a=vault(h,new BlockPos(2,3,1),Direction.NORTH);
        var b=(StorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1,2,1)));
        var c=(StorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3,4,3)));
        a.inventory().setItem(26,new ItemStack(Items.DIAMOND,2)); b.inventory().setItem(13,new ItemStack(Items.GOLD_INGOT,3)); c.inventory().setItem(26,new ItemStack(Items.EMERALD,4));
        h.getLevel().destroyBlock(b.getBlockPos(),true);
        h.assertValueEqual(a.group().size(),1,"controller side reforms"); h.assertValueEqual(c.group().size(),1,"detached side reforms");
        h.assertFalse(c.allowed(player(h,c)),"incomplete vault cannot open");
        h.assertValueEqual(a.inventory().getItem(26).getCount(),2,"first block unchanged"); h.assertValueEqual(c.inventory().getItem(26).getCount(),4,"last block unchanged");
        int gold=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(b.getBlockPos()).inflate(2)).stream().filter(e->e.getItem().is(Items.GOLD_INGOT)).mapToInt(e->e.getItem().getCount()).sum();
        h.assertValueEqual(gold,3,"only middle contents are dropped once"); h.succeed();
    }
    @GameTest(template="empty") public static void identityIssuanceAndSlotGapsSurvivePickup(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get()); var p=player(h,bank);
        bank.issue(p); bank.issue(p);
        h.assertValueEqual(p.getInventory().countItem(StorageRegistry.KEY.get()),1,"original issued once");
        bank.inventory().setItem(53,new ItemStack(Items.DIAMOND,11)); bank.hit();
        var pickup=bank.pickup(h.getLevel().registryAccess());
        var replacement=place(h,new BlockPos(3,2,1),StorageRegistry.SAFE.get()); replacement.place(pickup,h.getLevel().registryAccess()); replacement.issue(p);
        h.assertValueEqual(p.getInventory().countItem(StorageRegistry.KEY.get()),1,"replacement issues no duplicate");
        h.assertValueEqual(replacement.identity,bank.identity,"same key"); h.assertValueEqual(replacement.damage,1,"damage persists");
        h.assertFalse(replacement.cooling(),"pickup explicitly clears cooldown"); h.assertTrue(replacement.inventory().getItem(0).isEmpty(),"empty gaps persist");
        h.assertValueEqual(replacement.inventory().getItem(53).getCount(),11,"last safe slot persists"); h.succeed();
    }
    @GameTest(template="empty") public static void creativeBreakDiscardsEmptySafe(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get()); var p=player(h,bank);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(p.gameMode.destroyBlock(bank.getBlockPos()),"creative player breaks the empty safe");
        h.assertTrue(h.getLevel().getBlockState(bank.getBlockPos()).isAir(),"empty safe is removed");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(bank.getBlockPos()).inflate(2)).stream()
                .noneMatch(e->e.getItem().is(StorageRegistry.SAFE.get().asItem())),"empty creative safe drops no safe item"); h.succeed();
    }
    @GameTest(template="empty") public static void creativeBreakDropsOneFilledSafe(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get()); var p=player(h,bank);
        bank.inventory().setItem(53,new ItemStack(Items.DIAMOND,7)); var identity=bank.identity;
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(p.gameMode.destroyBlock(bank.getBlockPos()),"creative player breaks the filled safe");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(bank.getBlockPos()).inflate(2));
        var safes=drops.stream().filter(e->e.getItem().is(StorageRegistry.SAFE.get().asItem())).toList();
        h.assertValueEqual(safes.size(),1,"filled creative safe drops exactly once");
        h.assertTrue(drops.stream().noneMatch(e->e.getItem().is(Items.DIAMOND)),"safe contents never spill separately");
        var replacement=place(h,new BlockPos(3,2,1),StorageRegistry.SAFE.get());
        replacement.place(safes.getFirst().getItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("BankStorage"),h.getLevel().registryAccess());
        h.assertValueEqual(replacement.inventory().getItem(53).getCount(),7,"creative pickup retains the last inventory slot");
        h.assertValueEqual(replacement.identity,identity,"creative pickup preserves the safe identity"); h.succeed();
    }
    @GameTest(template="empty") public static void survivalBreakStillDropsEmptySafe(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get()); var p=player(h,bank);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));
        h.assertTrue(p.gameMode.destroyBlock(bank.getBlockPos()),"survival player breaks the empty safe with a valid tool");
        h.assertValueEqual(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(bank.getBlockPos()).inflate(2)).stream()
                .filter(e->e.getItem().is(StorageRegistry.SAFE.get().asItem())).count(),1L,"empty survival safe still drops once"); h.succeed();
    }
    @GameTest(template="empty") public static void realSafeBreakDropsOneFilledItem(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get()); bank.inventory().setItem(40,new ItemStack(Items.STONE,32));
        h.getLevel().destroyBlock(bank.getBlockPos(),true);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(bank.getBlockPos()).inflate(2));
        h.assertValueEqual(drops.stream().filter(e->e.getItem().is(StorageRegistry.SAFE.get().asItem())).count(),1L,"one safe item");
        h.assertTrue(drops.stream().noneMatch(e->e.getItem().is(Items.STONE)),"no loose duplicate contents");
        var item=drops.stream().filter(e->e.getItem().is(StorageRegistry.SAFE.get().asItem())).findFirst().orElseThrow().getItem();
        var next=place(h,new BlockPos(3,2,1),StorageRegistry.SAFE.get());
        next.place(item.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("BankStorage"),h.getLevel().registryAccess());
        h.assertValueEqual(next.inventory().getItem(40).getCount(),32,"filled pickup restores exact assigned slot"); h.succeed();
    }
    @GameTest(template="empty") public static void keyRecipeIdentityAndRemainders(GameTestHelper h) {
        UUID id=UUID.randomUUID(); var original=StorageKeys.create(StorageRegistry.KEY.get(),id);
        original.set(DataComponents.CUSTOM_NAME,Component.literal("Anything"));
        var recipe=new KeyCopyRecipe(CraftingBookCategory.MISC);
        var input=CraftingInput.of(2,1,List.of(original,new ItemStack(Items.CLAY_BALL)));
        h.assertTrue(recipe.matches(input,h.getLevel()),"impression recipe matches");
        var mold=recipe.assemble(input,h.getLevel().registryAccess());
        h.assertValueEqual(StorageKeys.identity(mold),id,"mold inherits identity");
        h.assertTrue(ItemStack.matches(recipe.getRemainingItems(input).get(0),original),"original survives impression with name");
        input=CraftingInput.of(2,1,List.of(mold,new ItemStack(Items.GOLD_INGOT)));
        var cast=recipe.assemble(input,h.getLevel().registryAccess());
        h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),"mold consumed");
        var smelt=new KeySmeltingRecipe("",CookingBookCategory.MISC,Ingredient.of(StorageRegistry.CAST.get()),new ItemStack(StorageRegistry.KEY.get()),0,200);
        h.assertValueEqual(StorageKeys.identity(smelt.assemble(new SingleRecipeInput(cast),h.getLevel().registryAccess())),id,"furnace recipe preserves identity");
        h.assertFalse(smelt.matches(new SingleRecipeInput(new ItemStack(StorageRegistry.CAST.get())),h.getLevel()),"blank cast cannot fabricate identity");
        h.assertTrue(StorageKeys.identity(new ItemStack(StorageRegistry.KEY.get()))==null,"renaming blank keys grants nothing"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=260) public static void nativeFurnaceCopiesMatchingKey(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,2,1)); h.getLevel().setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());
        var furnace=(FurnaceBlockEntity)h.getLevel().getBlockEntity(pos); var id=UUID.randomUUID();
        furnace.setItem(0,StorageKeys.create(StorageRegistry.CAST.get(),id)); furnace.setItem(1,new ItemStack(Items.COAL));
        h.succeedWhen(()->{ h.assertTrue(furnace.getItem(2).is(StorageRegistry.KEY.get()),"native furnace output is a key");
            h.assertValueEqual(StorageKeys.identity(furnace.getItem(2)),id,"native furnace assembles identity dynamically"); h.assertTrue(furnace.getItem(0).isEmpty(),"cast consumed"); });
    }
    @GameTest(template="empty") public static void breachRepairCooldownAndCachedAutomation(GameTestHelper h) {
        var lock=place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get()); var member=place(h,new BlockPos(2,2,1),StorageRegistry.VAULT.get());
        member.inventory().setItem(0,new ItemStack(Items.DIAMOND,5)); var handle=member.automation(); int slot=member.group().indexOf(member)*27;
        h.assertTrue(handle.extractItem(slot,1,false).isEmpty(),"locked cached capability refuses");
        lock.hit(); h.assertFalse(lock.breached(),"first charge damages"); long deadline=lock.repairAfter;
        lock.hit(); h.assertFalse(lock.breached(),"second charge heavily damages"); lock.hit();
        h.assertTrue(lock.breached(),"third charge breaches"); h.assertFalse(lock.repair(),"cooldown rejects repair");
        h.assertValueEqual(handle.extractItem(slot,1,false).getCount(),1,"same cached handle sees breach");
        h.assertTrue(member.getBlockState().getValue(StorageBlock.PROTECTED),"breached member retains slow mining during cooldown");
        var saved=lock.saveWithoutMetadata(h.getLevel().registryAccess()); lock.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(lock.repairAfter>=deadline && lock.cooling(),"save/reload retains server-time deadline");
        lock.repairAfter=lock.now(); h.assertTrue(lock.repair(),"repair after expiry");
        h.assertTrue(lock.locked(),"first repair restores same lock"); h.assertTrue(handle.extractItem(slot,1,false).isEmpty(),"cached handle revoked after repair");
        h.assertTrue(lock.repair() && lock.repair(),"remaining repairs consecutive"); h.assertValueEqual(lock.damage,0,"fully repaired"); h.succeed();
    }
    @GameTest(template="empty") public static void slot729ShiftClickAndStaleMenus(GameTestHelper h) {
        var bank=vault(h,new BlockPos(2,3,1),Direction.NORTH); bank.unlocked=true; bank.changedLock();
        var p=player(h,bank); var menu=new StorageMenu(1,p.getInventory(),bank);
        menu.slots.get(728).set(new ItemStack(Items.DIAMOND,19));
        menu.clicked(728,0,ClickType.QUICK_MOVE,p);
        h.assertTrue(menu.slots.get(728).getItem().isEmpty(),"last server slot extracted"); h.assertValueEqual(p.getInventory().countItem(Items.DIAMOND),19,"shift click conserves");
        p.getInventory().setItem(9,new ItemStack(Items.EMERALD,64)); menu.quickMoveStack(p,729);
        h.assertValueEqual(bank.group().stream().mapToInt(b->b.inventory().countItem(Items.EMERALD)).sum(),64,"shift click inserts across full inventory");
        var removed=bank.group().getFirst(); h.getLevel().destroyBlock(removed.getBlockPos(),true);
        h.assertFalse(menu.stillValid(p),"reshape invalidates old membership mapping"); h.succeed();
    }
    @GameTest(template="empty") public static void controllerRemovalClearsProtectionWithoutChangingContents(GameTestHelper h) {
        var lock=place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get()); var member=place(h,new BlockPos(2,2,1),StorageRegistry.VAULT.get());
        lock.hit(); member.inventory().setItem(24,new ItemStack(Items.APPLE,17));
        h.getLevel().destroyBlock(lock.getBlockPos(),true);
        h.assertTrue(member.lock()==null,"removed lock not inherited"); h.assertFalse(member.publicAutomation(),"incomplete vault automation stays closed");
        h.assertTrue(member.getBlockState().getValue(StorageBlock.PROTECTED),"detached world blocks retain the previous mining deadline without retaining a lock");
        member.miningAfter=member.now(); member.refreshProtection();
        h.assertFalse(member.getBlockState().getValue(StorageBlock.PROTECTED),"transient mining protection expires");
        h.assertValueEqual(member.inventory().getItem(24).getCount(),17,"member keeps assigned contents"); h.succeed();
    }
    @GameTest(template="empty") public static void frontMountedChargesAreLocalizedAndConsumptive(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get()); var p=player(h,bank);
        var pos=bank.getBlockPos(); var block=(StorageBlock)bank.getBlockState().getBlock();
        h.getLevel().setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState());
        var front=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.NORTH,pos,false);
        var back=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.SOUTH,pos,false);
        var tnt=new ItemStack(Items.TNT,3);
        block.useItemOn(tnt,bank.getBlockState(),h.getLevel(),pos,p,InteractionHand.MAIN_HAND,back);
        h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"back face cannot mount a charge");
        for(int stage=1;stage<=3;stage++) {
            block.useItemOn(tnt,bank.getBlockState(),h.getLevel(),pos,p,InteractionHand.MAIN_HAND,front);
            h.assertTrue(bank.getBlockState().getValue(StorageBlock.CHARGE),"front mounted TNT visible in block state");
            var flint=new ItemStack(Items.FLINT_AND_STEEL); p.setItemInHand(InteractionHand.MAIN_HAND,flint);
            block.useItemOn(flint,bank.getBlockState(),h.getLevel(),pos,p,InteractionHand.MAIN_HAND,front);
            h.assertValueEqual(bank.fuse,80,"ignition starts four second fuse");
            var sync=bank.getUpdateTag(h.getLevel().registryAccess());
            h.assertValueEqual(sync.getInt("Fuse"),80,"lit fuse is available to players loading the chunk");
            h.assertFalse(sync.contains("Items") || sync.contains("Identity"),"fuse synchronization does not expose storage contents or keys");
            for(int tick=0;tick<80;tick++) StorageBlockEntity.tick(h.getLevel(),pos,bank.getBlockState(),bank);
            h.assertValueEqual(bank.damage,stage,"only one controller stage consumed per charge");
            h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"charge consumed after detonation");
            h.assertTrue(h.getLevel().getBlockState(pos.north()).is(Blocks.STONE),"localized damage never destroys adjacent terrain");
        }
        h.assertTrue(tnt.isEmpty(),"three physical TNT consumed"); h.succeed();
    }
    @GameTest(template="empty") public static void breachedStorageRejectsChargesUntilRepaired(GameTestHelper h) {
        for(int i=0;i<2;i++) {
            var bank=place(h,new BlockPos(1+i*3,2,1),i==0?StorageRegistry.SAFE.get():StorageRegistry.CONTROLLER.get());
            var p=player(h,bank); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for(int damage=0;damage<bank.maxDamage();damage++) bank.hit();
            var front=new net.minecraft.world.phys.BlockHitResult(bank.getBlockPos().getCenter(),Direction.NORTH,bank.getBlockPos(),false);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TNT,2));
            p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
            h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"breached storage rejects mounted TNT");
            h.assertValueEqual(p.getMainHandItem().getCount(),2,"rejected charge consumes no TNT");
            h.assertTrue(p.containerMenu==p.inventoryMenu,"rejected charge does not open the inventory");
            h.getLevel().setBlockAndUpdate(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.CHARGE,true));
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FIRE_CHARGE,2));
            p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
            h.assertValueEqual(bank.fuse,-1,"legacy charge on breached storage cannot be ignited");
            h.assertValueEqual(p.getMainHandItem().getCount(),2,"rejected ignition consumes no fire charge");
            p.setShiftKeyDown(true); p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front); p.setShiftKeyDown(false);
            h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"legacy unlit charge can still be recovered");
            bank.repairAfter=bank.now(); h.assertTrue(bank.repair(),"repair restores charge eligibility");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TNT,2));
            p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
            h.assertTrue(bank.getBlockState().getValue(StorageBlock.CHARGE),"repaired safe or partially damaged vault accepts a charge");
            h.assertValueEqual(p.getMainHandItem().getCount(),1,"accepted charge consumes one TNT");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void shiftClickReturnsUnlitChargesWithHeldOrEmptyHand(GameTestHelper h) {
        for(int i=0;i<2;i++) {
            var bank=place(h,new BlockPos(1+i*3,2,1),i==0?StorageRegistry.SAFE.get():StorageRegistry.CONTROLLER.get());
            var front=new net.minecraft.world.phys.BlockHitResult(bank.getBlockPos().getCenter(),Direction.NORTH,bank.getBlockPos(),false);
            var back=new net.minecraft.world.phys.BlockHitResult(bank.getBlockPos().getCenter(),Direction.SOUTH,bank.getBlockPos(),false);
            for(boolean emptyHand:List.of(false,true)) {
                var p=player(h,bank); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TNT,2));
                p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
                h.assertTrue(bank.getBlockState().getValue(StorageBlock.CHARGE),"charge is mounted before removal");
                if(emptyHand) {
                    p.getInventory().setItem(9,p.getMainHandItem()); p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.STICK));
                }
                var backState=h.getLevel().getBlockState(bank.getBlockPos().south());
                p.setShiftKeyDown(true); p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,back);
                h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"shift click removes mounted TNT from any face");
                h.assertValueEqual(p.getInventory().countItem(Items.TNT),2,"exactly one consumed TNT is returned");
                h.assertValueEqual(bank.fuse,-1,"removal leaves no active fuse");
                h.assertTrue(p.containerMenu==p.inventoryMenu,"removing TNT does not open storage");
                h.assertValueEqual(h.getLevel().getBlockState(bank.getBlockPos().south()),backState,"held TNT does not replace the block behind storage while sneaking");
                if(!emptyHand) { p.getInventory().setItem(10,p.getMainHandItem()); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK)); }
                p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,back);
                h.assertValueEqual(p.getInventory().countItem(Items.TNT),2,"repeat click cannot refund TNT twice");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void shiftClickCannotRecoverBurningCharges(GameTestHelper h) {
        for(int i=0;i<2;i++) {
            var bank=place(h,new BlockPos(1+i*3,2,1),i==0?StorageRegistry.SAFE.get():StorageRegistry.CONTROLLER.get());
            var p=player(h,bank); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var front=new net.minecraft.world.phys.BlockHitResult(bank.getBlockPos().getCenter(),Direction.NORTH,bank.getBlockPos(),false);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TNT));
            p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FIRE_CHARGE,2));
            p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
            p.setShiftKeyDown(true); p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
            h.assertTrue(bank.getBlockState().getValue(StorageBlock.CHARGE),"burning charge stays attached");
            h.assertValueEqual(bank.fuse,80,"shift click does not cancel or restart the fuse");
            h.assertValueEqual(p.getInventory().countItem(Items.TNT),0,"burning TNT is not refunded");
            h.assertValueEqual(p.getMainHandItem().getCount(),1,"refused removal consumes no held fire charge");
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
            h.assertValueEqual(bank.fuse,80,"empty hand cannot disarm a burning charge");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void chargeRemovalRespectsDeniedBlockInteractions(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get()); var p=player(h,bank);
        h.getLevel().setBlockAndUpdate(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.CHARGE,true));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK)); p.setShiftKeyDown(true);
        var front=new net.minecraft.world.phys.BlockHitResult(bank.getBlockPos().getCenter(),Direction.NORTH,bank.getBlockPos(),false);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny=event->{
            if(event.getEntity()==p && event.getPos().equals(bank.getBlockPos())) {
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,deny);
        try { p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        h.assertTrue(bank.getBlockState().getValue(StorageBlock.CHARGE),"denied interaction cannot remove mounted TNT");
        h.assertValueEqual(p.getInventory().countItem(Items.TNT),0,"denied interaction grants no refund"); h.succeed();
    }
    @GameTest(template="empty") public static void fullInventoryChargeRemovalDropsOneRefund(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get()); var p=player(h,bank);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var front=new net.minecraft.world.phys.BlockHitResult(bank.getBlockPos().getCenter(),Direction.NORTH,bank.getBlockPos(),false);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TNT));
        p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
        for(int slot=0;slot<p.getInventory().getContainerSize();slot++) p.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
        p.setShiftKeyDown(true); p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,front);
        h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"full inventory still removes the charge");
        h.assertValueEqual(p.getInventory().countItem(Items.TNT),0,"full inventory accepts no refund");
        h.getLevel().destroyBlock(bank.getBlockPos(),true);
        h.assertValueEqual(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(bank.getBlockPos()).inflate(2)).stream()
                .filter(e->e.getItem().is(Items.TNT)).mapToInt(e->e.getItem().getCount()).sum(),1,"refund drops once and breaking storage cannot duplicate it"); h.succeed();
    }
    @GameTest(template="empty") public static void toolSpeedAndCachedCapabilityRespectLockTransitions(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get()); var p=player(h,bank); bank.issue(p);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(p.hasCorrectToolForDrops(bank.getBlockState()),"iron pick is sufficient");
        var reinforced=Blocks.REINFORCED_DEEPSLATE.defaultBlockState().getDestroyProgress(p,h.getLevel(),bank.getBlockPos());
        h.assertValueEqual(bank.getBlockState().getDestroyProgress(p,h.getLevel(),bank.getBlockPos()),reinforced,"locked mining exactly follows reinforced deepslate");
        bank.unlocked=true; bank.changedLock();
        var handler=bank.automation(); h.assertTrue(handler.insertItem(0,new ItemStack(Items.APPLE,5),false).isEmpty(),"unlocked accepts arbitrary items");
        h.assertTrue(bank.getBlockState().getDestroyProgress(p,h.getLevel(),bank.getBlockPos())>reinforced,"unlocked iron mining is faster");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE_PICKAXE));
        h.assertFalse(p.hasCorrectToolForDrops(bank.getBlockState()),"stone pick is insufficient");
        bank.unlocked=false; bank.changedLock();
        h.assertValueEqual(handler.insertItem(1,new ItemStack(Items.APPLE,4),false).getCount(),4,"cached capability cannot insert after relock");
        h.assertTrue(handler.extractItem(0,5,false).isEmpty(),"cached capability cannot extract after relock"); h.succeed();
    }
    @GameTest(template="empty") public static void unavailableMembershipFailsClosedAndKeepsAssignedInventory(GameTestHelper h) {
        var bank=place(h,new BlockPos(1,2,1),StorageRegistry.VAULT.get()); var p=player(h,bank);
        bank.inventory().setItem(20,new ItemStack(Items.GOLD_INGOT,7)); var old=bank.members;
        bank.members=List.of(bank.getBlockPos(),bank.getBlockPos().offset(100000,0,0));
        h.assertTrue(bank.group().isEmpty(),"unavailable member cannot be treated as a split");
        h.assertFalse(bank.allowed(p),"unavailable inventory cannot open"); h.assertTrue(bank.automation().extractItem(20,7,false).isEmpty(),"cached topology fails closed");
        VaultConnections.repairMembership(bank); h.assertValueEqual(bank.members.size(),2,"unloaded membership is not discarded");
        bank.members=old;
        h.assertValueEqual(bank.inventory().getItem(20).getCount(),7,"returning member retains every item"); h.succeed();
    }
    @GameTest(template="empty") public static void fixedVaultFormsOnEveryHorizontalFace(GameTestHelper h) {
        int index=0;
        for(var facing:Direction.Plane.HORIZONTAL) {
            var bank=vault(h,new BlockPos(2+index++*6,3,5),facing);
            h.assertTrue(bank.vaultFormed(),"controller facing "+facing+" forms the cube behind its door");
            h.assertValueEqual(bank.group().size(),27,"exactly 27 members");
            h.assertTrue(bank.group().stream().allMatch(StorageBlockEntity::vaultFormed),"all member models are replaced together");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void incompleteAndMisplacedControllersCannotOpen(GameTestHelper h) {
        var start=h.absolutePos(new BlockPos(1,2,1));
        for(int y=0;y<3;y++) for(int z=0;z<3;z++) for(int x=0;x<3;x++)
            h.getLevel().setBlockAndUpdate(start.offset(x,y,z),StorageRegistry.VAULT.get().defaultBlockState());
        var corner=placeRaw(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get());
        corner.unlocked=true;
        h.assertFalse(corner.vaultFormed(),"corner controller cannot form a vault");
        h.assertFalse(corner.allowed(player(h,corner)),"incomplete controller cannot open even unlocked");
        h.assertFalse(corner.publicAutomation(),"incomplete cube rejects hoppers");
        h.getLevel().setBlockAndUpdate(corner.getBlockPos(),StorageRegistry.VAULT.get().defaultBlockState());
        var center=placeRaw(h,new BlockPos(2,3,1),StorageRegistry.CONTROLLER.get());
        h.assertTrue(center.vaultFormed(),"centered controller completes existing cube");
        var removed=center.group().getFirst();
        h.getLevel().destroyBlock(removed.getBlockPos(),true);
        h.assertFalse(center.vaultFormed(),"missing member immediately disassembles the model");
        h.assertFalse(center.allowed(player(h,center)),"missing member closes menus");
        h.getLevel().setBlockAndUpdate(removed.getBlockPos(),StorageRegistry.VAULT.get().defaultBlockState());
        VaultConnections.merge((StorageBlockEntity)h.getLevel().getBlockEntity(removed.getBlockPos()));
        h.assertTrue(center.vaultFormed(),"replacement member reforms the same vault"); h.succeed();
    }
    @GameTest(template="empty") public static void oldRectanglesRetainContentsAndKeysDuringMigration(GameTestHelper h) {
        var controller=placeRaw(h,new BlockPos(2,3,1),StorageRegistry.CONTROLLER.get());
        var member=placeRaw(h,new BlockPos(3,3,1),StorageRegistry.VAULT.get());
        var identity=controller.identity;
        controller.inventory().setItem(26,new ItemStack(Items.DIAMOND,9));
        member.inventory().setItem(13,new ItemStack(Items.GOLD_INGOT,7));
        var old=List.of(controller.getBlockPos(),member.getBlockPos()); controller.assign(old); member.assign(old);
        var registries=h.getLevel().registryAccess();
        controller.loadWithComponents(controller.saveWithoutMetadata(registries),registries);
        member.loadWithComponents(member.saveWithoutMetadata(registries),registries);
        VaultConnections.repairMembership(controller);
        h.assertValueEqual(controller.group().size(),1,"legacy rectangle becomes an incomplete vault");
        for(var pos:VaultConnections.positions(controller)) if(VaultConnections.at(h.getLevel(),pos)==null)
            h.getLevel().setBlockAndUpdate(pos,StorageRegistry.VAULT.get().defaultBlockState());
        VaultConnections.merge(controller);
        h.assertTrue(controller.vaultFormed(),"legacy blocks form after completing the cube");
        h.assertValueEqual(controller.identity,identity,"original key identity remains authoritative");
        h.assertValueEqual(controller.inventory().getItem(26).getCount(),9,"controller slot preserved");
        h.assertValueEqual(member.inventory().getItem(13).getCount(),7,"member slot preserved"); h.succeed();
    }
    @GameTest(template="empty") public static void memberViewersAnimateOneVaultController(GameTestHelper h) {
        var bank=vault(h,new BlockPos(2,3,1),Direction.NORTH); bank.unlocked=true; bank.changedLock();
        var member=bank.group().getFirst(); var viewer=player(h,member);
        var menu=(StorageMenu)member.createMenu(1,viewer.getInventory(),viewer); viewer.containerMenu=menu;
        var registries=h.getLevel().registryAccess();
        h.assertTrue(bank.getUpdateTag(registries).getBoolean("DoorOpen"),"member menu opens the controller door");
        h.assertTrue(menu.isContainer(bank),"controller tracks member viewers");
        bank.handleUpdateTag(bank.getUpdateTag(registries),registries);
        for(int i=0;i<8;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getOpenNess(1),0.25F,"cog gets the first quarter of the 32 tick animation");
        for(int i=0;i<24;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getOpenNess(1),1F,"vault finishes opening after 1.6 seconds");
        menu.removed(viewer); viewer.containerMenu=viewer.inventoryMenu;
        h.assertFalse(bank.getUpdateTag(registries).getBoolean("DoorOpen"),"last viewer closes the controller");
        bank.triggerEvent(1,0);
        for(int i=0;i<32;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getOpenNess(1),0F,"vault returns fully closed"); h.succeed();
    }
    @GameTest(template="empty") public static void thirdChargeForcesVaultOpenAndRepairRestoresIt(GameTestHelper h) {
        var bank=vault(h,new BlockPos(2,3,1),Direction.NORTH);
        bank.inventory().setItem(26,new ItemStack(Items.DIAMOND,11)); var identity=bank.identity;
        for(int stage=1;stage<=3;stage++) {
            bank.fuse=1;
            h.getLevel().setBlockAndUpdate(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.CHARGE,true));
            StorageBlockEntity.tick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
            for(int i=0;i<11;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
            h.assertValueEqual(bank.getBreachProgress(1),stage==3?1F:0F,"only a breached vault hangs open");
            h.assertTrue(bank.vaultFormed(),"localized blast preserves all structural blocks");
        }
        bank.triggerEvent(1,0);
        StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getBreachProgress(1),1F,"closing a menu cannot close the breached door");
        bank.repairAfter=bank.now(); h.assertTrue(bank.repair(),"repair after cooldown restores the lock");
        for(int i=0;i<11;i++) StorageBlockEntity.doorAnimateTick(h.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
        h.assertValueEqual(bank.getBreachProgress(1),0F,"repaired vault leaves breached pose");
        h.assertTrue(bank.locked(),"repair relocks the vault");
        h.assertValueEqual(bank.identity,identity,"breach and repair preserve original key");
        h.assertValueEqual(bank.inventory().getItem(26).getCount(),11,"stored contents survive breach and repair"); h.succeed();
    }
}
