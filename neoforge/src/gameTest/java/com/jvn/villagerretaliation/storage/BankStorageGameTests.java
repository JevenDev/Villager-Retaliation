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
        var pos=h.absolutePos(relative); h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        var bank=(StorageBlockEntity)h.getLevel().getBlockEntity(pos); VaultConnections.merge(bank); return bank;
    }
    public static ServerPlayer player(GameTestHelper h,StorageBlockEntity bank) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BankTest")); p.setPos(bank.getBlockPos().getCenter()); return p;
    }
    @GameTest(template="empty") public static void commandAndBuilderPlacementsAutomaticallyForm(GameTestHelper h) {
        var start=h.absolutePos(new BlockPos(1,2,1));
        for(int y=0;y<3;y++) for(int z=0;z<2;z++) for(int x=0;x<2;x++)
            h.getLevel().setBlockAndUpdate(start.offset(x,y,z),StorageRegistry.VAULT.get().defaultBlockState());
        h.runAfterDelay(3,()->{
            var bank=(StorageBlockEntity)h.getLevel().getBlockEntity(start);
            h.assertValueEqual(bank.group().size(),12,"2 by 2 by 3 command/builder placement reforms without a player hook"); h.succeed();
        });
    }
    @GameTest(template="empty") public static void rectangularCapacityAndBounds(GameTestHelper h) {
        StorageBlockEntity bank=null;
        for(int y=0;y<3;y++) for(int z=0;z<3;z++) for(int x=0;x<3;x++) bank=place(h,new BlockPos(1+x,2+y,1+z),x+y+z==0?StorageRegistry.CONTROLLER.get():StorageRegistry.VAULT.get());
        h.assertValueEqual(bank.group().size(),27,"full cube has 27 blocks");
        h.assertValueEqual(bank.group().stream().mapToInt(b->b.inventory().getContainerSize()).sum(),729,"controller contributes 27 slots");
        var extra=place(h,new BlockPos(4,2,1),StorageRegistry.VAULT.get());
        h.assertValueEqual(extra.group().size(),1,"fourth dimension block stays separate");
        h.assertValueEqual(bank.group().size(),27,"full cube remains stable"); h.succeed();
    }
    @GameTest(template="empty") public static void controlledNeighborsNeverMerge(GameTestHelper h) {
        var a=place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get());
        var b=place(h,new BlockPos(2,2,1),StorageRegistry.CONTROLLER.get());
        a.inventory().setItem(0,new ItemStack(Items.DIAMOND,2)); b.inventory().setItem(0,new ItemStack(Items.GOLD_INGOT,3));
        h.assertValueEqual(a.group().size(),1,"touching controllers stay separate");
        h.assertTrue(!a.identity.equals(b.identity),"independent keys");
        var p=player(h,b); a.issue(p); h.assertFalse(b.allowed(p),"neighbor key never opens second vault"); h.succeed();
    }
    @GameTest(template="empty") public static void splitRetainsPerBlockSlotsAndDropsOnlyRemovedMember(GameTestHelper h) {
        var a=place(h,new BlockPos(1,2,1),StorageRegistry.CONTROLLER.get());
        var b=place(h,new BlockPos(2,2,1),StorageRegistry.VAULT.get());
        var c=place(h,new BlockPos(3,2,1),StorageRegistry.VAULT.get());
        a.inventory().setItem(26,new ItemStack(Items.DIAMOND,2)); b.inventory().setItem(13,new ItemStack(Items.GOLD_INGOT,3)); c.inventory().setItem(26,new ItemStack(Items.EMERALD,4));
        h.getLevel().destroyBlock(b.getBlockPos(),true);
        h.assertValueEqual(a.group().size(),1,"controller side reforms"); h.assertValueEqual(c.group().size(),1,"detached side reforms");
        h.assertTrue(c.allowed(player(h,c)),"controllerless side opens");
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
        member.inventory().setItem(0,new ItemStack(Items.DIAMOND,5)); var handle=member.automation();
        h.assertTrue(handle.extractItem(27,1,false).isEmpty(),"locked cached capability refuses");
        lock.hit(); h.assertFalse(lock.breached(),"first charge damages"); long deadline=lock.repairAfter;
        lock.hit(); h.assertFalse(lock.breached(),"second charge heavily damages"); lock.hit();
        h.assertTrue(lock.breached(),"third charge breaches"); h.assertFalse(lock.repair(),"cooldown rejects repair");
        h.assertValueEqual(handle.extractItem(27,1,false).getCount(),1,"same cached handle sees breach");
        h.assertTrue(member.getBlockState().getValue(StorageBlock.PROTECTED),"breached member retains slow mining during cooldown");
        var saved=lock.saveWithoutMetadata(h.getLevel().registryAccess()); lock.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(lock.repairAfter>=deadline && lock.cooling(),"save/reload retains server-time deadline");
        lock.repairAfter=lock.now(); h.assertTrue(lock.repair(),"repair after expiry");
        h.assertTrue(lock.locked(),"first repair restores same lock"); h.assertTrue(handle.extractItem(27,1,false).isEmpty(),"cached handle revoked after repair");
        h.assertTrue(lock.repair() && lock.repair(),"remaining repairs consecutive"); h.assertValueEqual(lock.damage,0,"fully repaired"); h.succeed();
    }
    @GameTest(template="empty") public static void slot729ShiftClickAndStaleMenus(GameTestHelper h) {
        StorageBlockEntity bank=null;
        for(int y=0;y<3;y++) for(int z=0;z<3;z++) for(int x=0;x<3;x++) bank=place(h,new BlockPos(1+x,2+y,1+z),StorageRegistry.VAULT.get());
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
        h.assertTrue(member.lock()==null,"removed lock not inherited"); h.assertTrue(member.publicAutomation(),"controllerless automation opens");
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
            for(int tick=0;tick<80;tick++) StorageBlockEntity.tick(h.getLevel(),pos,bank.getBlockState(),bank);
            h.assertValueEqual(bank.damage,stage,"only one controller stage consumed per charge");
            h.assertFalse(bank.getBlockState().getValue(StorageBlock.CHARGE),"charge consumed after detonation");
            h.assertTrue(h.getLevel().getBlockState(pos.north()).is(Blocks.STONE),"localized damage never destroys adjacent terrain");
        }
        h.assertTrue(tnt.isEmpty(),"three physical TNT consumed"); h.succeed();
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
}
