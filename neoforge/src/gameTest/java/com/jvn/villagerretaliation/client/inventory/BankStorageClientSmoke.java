package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.storage.*;
import com.jvn.villagerretaliation.client.renderer.SafeRenderer;
import com.jvn.villagerretaliation.client.renderer.VaultRenderer;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Isolated native client/server check. No existing user world is loaded. */
@EventBusSubscriber(modid=VillagerRetaliation.MOD_ID,value=Dist.CLIENT)
public final class BankStorageClientSmoke {
    private static final String OUTPUT="villagerretaliation.bankStorageClientSmoke";
    private static long started;
    private static int phase,ticks;
    private static String worldCapture;
    private static int primedSounds;
    private static final boolean[][] chargeFrames=new boolean[2][2];
    private static final float[][] chargeBrightness=new float[2][2];
    private static CompletableFuture<?> pending;
    private static final BlockPos CENTER=new BlockPos(1024,-60,1024);
    private static final BlockPos VAULT_CONTROLLER=CENTER.offset(1,1,0);
    private BankStorageClientSmoke() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(System.getProperty(OUTPUT)==null || phase==99) return;
        var mc=Minecraft.getInstance(); mc.setWindowActive(true); mc.options.pauseOnLostFocus=false;
        if(started==0) started=Util.getMillis(); require(Util.getMillis()-started<180000,"Native bank check timed out at phase "+phase);
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null || ++ticks<15) return;
        var server=mc.getSingleplayerServer(); var uuid=mc.player.getUUID();
        switch(phase) {
            case 0 -> {
                if(mc.screen!=null) return;
                mc.options.guiScale().set(2); mc.resizeDisplay();
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid); var level=p.serverLevel();
                    level.setDayTime(6000); level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                    for(var pos:BlockPos.betweenClosed(CENTER.offset(-7,-1,-7),CENTER.offset(10,5,8)))
                        level.setBlockAndUpdate(pos,pos.getY()==CENTER.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
                    p.teleportTo(CENTER.getX()+1.5,CENTER.getY(),CENTER.getZ()-3); p.setYRot(0); p.setXRot(15);
                    for(int y=0;y<3;y++) for(int z=0;z<3;z++) for(int x=0;x<3;x++) {
                        var pos=CENTER.offset(x,y,z); var block=x==1 && y==1 && z==0?StorageRegistry.CONTROLLER.get():StorageRegistry.VAULT.get();
                        level.setBlockAndUpdate(pos,block.defaultBlockState());
                        VaultConnections.merge((StorageBlockEntity)level.getBlockEntity(pos));
                    }
                    var bank=(StorageBlockEntity)level.getBlockEntity(VAULT_CONTROLLER); bank.issue(p);
                    int index=0;
                    for(var member:bank.group()) for(int i=0;i<27;i++,index++) member.inventory().setItem(i,new ItemStack(index%2==0?Items.COPPER_INGOT:Items.IRON_INGOT,index%64+1));
                    var last=bank.group().getLast(); last.inventory().setItem(26,new ItemStack(Items.DIAMOND,31));
                    level.setBlockAndUpdate(CENTER.east(5),StorageRegistry.SAFE.get().defaultBlockState().setValue(StorageBlock.CHARGE,true));
                    level.setBlockAndUpdate(VAULT_CONTROLLER,bank.getBlockState().setValue(StorageBlock.CHARGE,true));
                    bank.open(p); return null;
                }); next();
            }
            case 1 -> {
                if(!pending.isDone() || !(mc.screen instanceof StorageScreen screen)) return; pending.join();
                require(screen.getMenu().capacity==729,"Native GUI exposes all 729 fixed server slots");
                require(screen.getXSize()==208,"Multi-page vault keeps its scrollbar panel"); capture("vault-first-page");
                screen.mouseScrolled(5,5,0,-1); require(screen.getMenu().page==1,"Wheel scroll works outside slot area"); next();
            }
            case 2 -> {
                var screen=(StorageScreen)mc.screen;
                for(int i=0;i<20;i++) screen.mouseScrolled(5,5,0,-1);
                require(screen.getMenu().page==13,"Scroll clamps to last page"); next();
            }
            case 3 -> {
                var screen=(StorageScreen)mc.screen; capture("vault-last-page");
                require(screen.getMenu().slots.get(728).getItem().is(Items.DIAMOND),"Last slot is synchronized");
                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,728,0,ClickType.QUICK_MOVE,mc.player); next();
            }
            case 4 -> {
                var screen=(StorageScreen)mc.screen;
                if(!screen.getMenu().slots.get(728).getItem().isEmpty()) return;
                require(mc.player.getInventory().countItem(Items.DIAMOND)==31,"Native shift transfer conserves exactly 31 diamonds");
                int left=(screen.width-208)/2,top=(screen.height-222)/2;
                screen.mouseClicked(left+184,top+20,0); require(screen.getMenu().page==0,"Scrollbar click returns to first page");
                screen.mouseDragged(left+184,top+119,0,0,99); require(screen.getMenu().page==13,"Scrollbar drag selects last page");
                screen.mouseReleased(left+184,top+119,0);
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid);
                    for(int i=0;i<p.getInventory().getContainerSize();i++) if(p.getInventory().getItem(i).is(StorageRegistry.KEY.get())) p.getInventory().setItem(i,ItemStack.EMPTY);
                    return null;
                }); next();
            }
            case 5 -> {
                if(!pending.isDone() || mc.screen instanceof StorageScreen) return;
                pending.join(); require(mc.screen==null,"Losing the key closes the authoritative menu");
                mc.player.setYRot(0); mc.player.setXRot(12); next();
            }
            case 6 -> {
                capture("mounted-charge-placeholders");
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid); var level=p.serverLevel();
                    var facings=net.minecraft.core.Direction.Plane.HORIZONTAL.stream().toList();
                    for(int i=0;i<4;i++) level.setBlockAndUpdate(CENTER.offset(5+i%2*3,0,i/2*3),
                            StorageRegistry.SAFE.get().defaultBlockState().setValue(StorageBlock.FACING,facings.get(i)));
                    var bank=(StorageBlockEntity)level.getBlockEntity(CENTER.east(5));
                    level.setBlockAndUpdate(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.FACING,net.minecraft.core.Direction.NORTH));
                    bank.issue(p); p.teleportTo(CENTER.getX()+5.5,CENTER.getY(),CENTER.getZ()-2.8); p.setYRot(0); p.setXRot(22);
                    return null;
                }); next();
            }
            case 7 -> {
                if(!pending.isDone()) return; pending.join();
                var manager=mc.getModelManager();
                var door=manager.getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(VillagerRetaliation.id("block/safe_door")));
                require(door!=manager.getMissingModel(),"Safe door model bakes successfully");
                var front=door.getQuads(null,null,net.minecraft.util.RandomSource.create()).stream()
                        .filter(q->q.getDirection()==net.minecraft.core.Direction.NORTH).findFirst().orElseThrow();
                require(front.getSprite().contents().name().equals(VillagerRetaliation.id("block/safe_block")),"Safe door uses the combined atlas");
                var handle=manager.getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(VillagerRetaliation.id("block/safe_handle")));
                require(handle!=manager.getMissingModel(),"Safe handle model bakes successfully");
                var handleQuads=handle.getQuads(null,null,net.minecraft.util.RandomSource.create());
                var mount=manager.getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(VillagerRetaliation.id("block/safe_handle_mount")));
                var mountQuads=mount.getQuads(null,null,net.minecraft.util.RandomSource.create());
                require(handleQuads.size()==6 && mountQuads.size()==12,"Only the horizontal grip rotates, with two fixed mounting cubes");
                require(java.util.stream.Stream.concat(handleQuads.stream(),mountQuads.stream()).allMatch(q->q.getSprite().contents().name().equals(front.getSprite().contents().name())),"Grip and mount share the door atlas");
                require(SafeRenderer.doorAngle(0)==0 && SafeRenderer.handleAngle(0)==0,"Closed safe has a released handle");
                require(SafeRenderer.doorAngle(0.125F)==0 && SafeRenderer.handleAngle(0.125F)<0 && SafeRenderer.handleAngle(0.125F)>-3,"Grip begins with a gentle downward press");
                require(SafeRenderer.handleAngle(0.25F)>-10 && SafeRenderer.doorAngle(0.25F)==0,"Grip is still pressing when the door starts its swing");
                require(SafeRenderer.handleAngle(0.5F)<SafeRenderer.handleAngle(0.25F) && SafeRenderer.handleAngle(0.5F)>-20,"Grip presses smoothly across most of the opening motion");
                require(SafeRenderer.handleAngle(1)==-20 && SafeRenderer.doorAngle(1)==-90,"Open door carries its grip with a restrained 20 degree turn");
                capture("safe-closed");
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid);
                    ((StorageBlockEntity)p.serverLevel().getBlockEntity(CENTER.east(5))).open(p); return null;
                }); next();
            }
            case 8 -> {
                if(!pending.isDone() || !(mc.screen instanceof StorageScreen screen)) return; pending.join();
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(CENTER.east(5));
                if(bank.getOpenNess(1)<1) return;
                require(screen.getMenu().capacity==54,"Safe menu retains all 54 slots");
                require(screen.getXSize()==176 && screen.getMenu().pages()==1,"Safe uses standard chest width without a scrollbar panel");
                require(!screen.mouseScrolled(5,5,0,-1) && screen.getMenu().page==0,"Single-page safe ignores page scrolling");
                if(!Files.exists(Path.of(System.getProperty(OUTPUT),"safe-open-world.png"))) { worldCapture="safe-open-world"; return; }
                capture("safe-open");
                mc.player.closeContainer(); next();
            }
            case 9 -> {
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(CENTER.east(5));
                if(mc.screen!=null || bank.getOpenNess(1)>0) return;
                capture("safe-closed-after-use");
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid);
                    ((StorageBlockEntity)p.serverLevel().getBlockEntity(CENTER.east(5))).open(p); return null;
                }); next();
            }
            case 10 -> {
                if(!pending.isDone() || !(mc.screen instanceof StorageScreen)) return; pending.join();
                if(((StorageBlockEntity)mc.level.getBlockEntity(CENTER.east(5))).getOpenNess(1)<1) return;
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid);
                    for(int i=0;i<p.getInventory().getContainerSize();i++) if(p.getInventory().getItem(i).is(StorageRegistry.KEY.get())) p.getInventory().setItem(i,ItemStack.EMPTY);
                    return null;
                }); next();
            }
            case 11 -> {
                if(!pending.isDone() || mc.screen!=null || ((StorageBlockEntity)mc.level.getBlockEntity(CENTER.east(5))).getOpenNess(1)>0) return;
                pending.join(); capture("safe-closed-after-key-removal");
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid); var level=p.serverLevel();
                    var bank=(StorageBlockEntity)level.getBlockEntity(CENTER.east(5));
                    bank.inventory().setItem(53,new ItemStack(Items.DIAMOND,5));
                    level.setBlockAndUpdate(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.CHARGE,true)); bank.fuse=1;
                    p.setYRot(35); p.setXRot(20); p.teleportTo(CENTER.getX()+7.8,CENTER.getY(),CENTER.getZ()-2.8);
                    return null;
                }); next();
            }
            case 12 -> {
                if(!pending.isDone()) return; pending.join();
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(CENTER.east(5));
                if(bank.getBlockState().getValue(StorageBlock.DAMAGE)!=1 || bank.getBreachProgress(1)<1) return;
                require(mc.screen==null && bank.getOpenNess(1)==0,"Breached safe stays open without an inventory viewer");
                require(SafeRenderer.doorAngle(0,1)==-115 && SafeRenderer.doorAngle(0,0.1F)<-30,"Blast flings the door past its normal opening angle");
                require(SafeRenderer.blastImpulse(0)==0 && SafeRenderer.blastImpulse(0.5F)==1 && SafeRenderer.blastImpulse(1)==0,"Outward blast kick settles without closing the breached door");
                require(SafeRenderer.doorAngle(0,0.5F)-25*SafeRenderer.blastImpulse(0.5F)<-130,"Pressure wave makes the door overshoot before settling");
                if(!Files.exists(Path.of(System.getProperty(OUTPUT),"safe-breached-world.png"))) { worldCapture="safe-breached-world"; return; }
                if(ticks<100) return;
                var loaded=new StorageBlockEntity(bank.getBlockPos(),bank.getBlockState()); loaded.setLevel(mc.level); loaded.onLoad();
                require(loaded.getBreachProgress(0)==1,"Previously breached safe loads directly into its damaged pose");
                loaded.setBlockState(loaded.getBlockState().setValue(StorageBlock.DAMAGE,0));
                for(int i=0;i<11;i++) StorageBlockEntity.doorAnimateTick(mc.level,loaded.getBlockPos(),loaded.getBlockState(),loaded);
                require(loaded.getBreachProgress(1)==0,"Repair state restores the intact pose");
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid); var safe=(StorageBlockEntity)p.serverLevel().getBlockEntity(CENTER.east(5));
                    require(safe.inventory().getItem(53).getCount()==5 && safe.breached(),"Explosion preserves safe contents and remains breached"); return null;
                }); next();
            }
            case 13 -> {
                if(!pending.isDone()) return; pending.join();
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(CENTER.east(5));
                require(bank.getBlockState().getValue(StorageBlock.DAMAGE)==1 && bank.getBreachProgress(1)==1,"Exploded door remains open until explicitly repaired");
                capture("safe-breached-held-open");
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid); var level=p.serverLevel();
                    p.teleportTo(CENTER.getX()-2.5,CENTER.getY(),CENTER.getZ()-3); p.setYRot(0); p.setXRot(18);
                    for(int i=0;i<2;i++) {
                        var pos=CENTER.west(4-i*2);
                        if(i==0) buildVault(level,pos);
                        else level.setBlockAndUpdate(pos,StorageRegistry.SAFE.get().defaultBlockState());
                        level.setBlockAndUpdate(pos,level.getBlockState(pos).setValue(StorageBlock.CHARGE,true));
                        var charge=(StorageBlockEntity)level.getBlockEntity(pos);
                        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(i==0?Items.FLINT_AND_STEEL:Items.FIRE_CHARGE));
                        var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.NORTH,pos,false);
                        p.gameMode.useItemOn(p,level,p.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,hit);
                        require(charge.fuse==80,"Native ignition starts the mounted fuse on safe and vault");
                        var tag=charge.getUpdateTag(level.registryAccess());
                        require(tag.getInt("Fuse")==80 && !tag.contains("Items") && !tag.contains("Identity"),"Chunk synchronization includes the fuse without storage data");
                        var loaded=new StorageBlockEntity(pos,charge.getBlockState()); loaded.handleUpdateTag(tag,level.registryAccess());
                        require(loaded.fuse==80,"Loading a lit charge restores its remaining countdown");
                    }
                    return null;
                }); next();
            }
            case 14 -> {
                if(!pending.isDone()) return; pending.join();
                mc.player.setYRot(0); mc.player.setXRot(18);
                for(int i=0;i<2;i++) {
                    var bank=(StorageBlockEntity)mc.level.getBlockEntity(CENTER.west(4-i*2));
                    if(bank==null || bank.getBlockState().getValue(StorageBlock.DAMAGE)==0) return;
                    require(!bank.getBlockState().getValue(StorageBlock.CHARGE),"Detonation clears the mounted charge");
                    require(chargeFrames[i][0] && chargeFrames[i][1],"Safe and vault display both white and textured fuse frames");
                    require(chargeBrightness[i][1]>chargeBrightness[i][0]+30,"Mounted charge visibly brightens during its white flash");
                }
                require(primedSounds==2,"Safe and vault each deliver one native TNT priming sound");
                next();
            }
            case 15 -> {
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid); var level=p.serverLevel(); p.closeContainer();
                    var bank=(StorageBlockEntity)level.getBlockEntity(VAULT_CONTROLLER);
                    bank.unlocked=true; bank.changedLock();
                    for(var member:bank.group()) member.inventory().clearContent();
                    bank.inventory().setItem(26,new ItemStack(Items.DIAMOND,5));
                    var first=bank.group().getFirst(); var last=bank.group().getLast();
                    first.inventory().setItem(0,new ItemStack(Items.DIAMOND,64));
                    first.inventory().setItem(1,new ItemStack(Items.EMERALD,64));
                    first.inventory().setItem(2,new ItemStack(Items.GOLD_INGOT,64));
                    last.inventory().setItem(0,new ItemStack(Items.GOLD_BLOCK,64));
                    last.inventory().setItem(1,new ItemStack(Items.DIAMOND_BLOCK,64));
                    last.inventory().setItem(2,new ItemStack(Items.IRON_BLOCK,64));
                    level.setBlockAndUpdate(VAULT_CONTROLLER,bank.getBlockState().setValue(StorageBlock.CHARGE,false));
                    p.teleportTo(VAULT_CONTROLLER.getX()+.5,VAULT_CONTROLLER.getY()-1,VAULT_CONTROLLER.getZ()-4.5); p.setYRot(0); p.setXRot(12);
                    return null;
                }); next();
            }
            case 16 -> {
                if(!pending.isDone()) return; pending.join();
                mc.player.setYRot(0); mc.player.setXRot(12);
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(VAULT_CONTROLLER);
                if(bank.getOpenNess(1)>0 || bank.vaultDisplay().countItem(Items.DIAMOND_BLOCK)!=64) return;
                require(bank.vaultDisplay().countItem(Items.DIAMOND)==64 && bank.vaultDisplay().countItem(Items.EMERALD)==64,
                        "World display aggregates actual member inventories into bounded piles");
                require(bank.vaultFormed(),"Client receives the assembled multiblock state");
                var manager=mc.getModelManager();
                for(var part:java.util.List.of("vault_body","vault_door","vault_cog","vault_hinge_lower","vault_hinge_upper","vault_hinge_mounts")) {
                    var model=manager.getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(VillagerRetaliation.id("block/"+part)));
                    require(model!=manager.getMissingModel(),part+" bakes successfully");
                    var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create());
                    require(!quads.isEmpty() && quads.stream().allMatch(q->q.getSprite().contents().name().equals(VillagerRetaliation.id("block/vault"))),part+" uses the corrected atlas");
                }
                require(VaultRenderer.doorAngle(.125F,0)==0 && VaultRenderer.cogAngle(.125F)<0,"Cog turns before the vault door swings");
                require(VaultRenderer.doorAngle(1,0)==-100 && VaultRenderer.cogAngle(1)==-45,"Vault reaches authored door and cog rotations");
                worldCapture="vault-closed-world";
                pending=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid); ((StorageBlockEntity)p.serverLevel().getBlockEntity(VAULT_CONTROLLER)).open(p); return null;}); next();
            }
            case 17 -> {
                if(!pending.isDone() || !(mc.screen instanceof StorageScreen)) return; pending.join();
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(VAULT_CONTROLLER);
                if(bank.getOpenNess(1)<1) return;
                if(!Files.exists(Path.of(System.getProperty(OUTPUT),"vault-open-world.png"))) { worldCapture="vault-open-world"; return; }
                if(ticks<60) return;
                pending=server.submit(()->{
                    var vault=(StorageBlockEntity)server.getPlayerList().getPlayer(uuid).serverLevel().getBlockEntity(VAULT_CONTROLLER);
                    vault.group().getFirst().inventory().setItem(1,ItemStack.EMPTY);
                    server.getPlayerList().getPlayer(uuid).closeContainer(); return null;
                }); next();
            }
            case 18 -> {
                if(!pending.isDone()) return; pending.join();
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(VAULT_CONTROLLER);
                if(bank.getOpenNess(1)>0) return;
                if(bank.vaultDisplay().countItem(Items.EMERALD)!=0) return;
                require(bank.vaultDisplay().countItem(Items.GOLD_BLOCK)==64,"withdrawals remove only their corresponding pile");
                worldCapture="vault-closed-after-viewer";
                pending=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(uuid); var level=p.serverLevel(); var vault=(StorageBlockEntity)level.getBlockEntity(VAULT_CONTROLLER);
                    vault.hit(); vault.hit();
                    require(!vault.breached(),"Two charges damage the vault without breaching it");
                    level.setBlockAndUpdate(VAULT_CONTROLLER,vault.getBlockState().setValue(StorageBlock.CHARGE,true));
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
                    var hit=new net.minecraft.world.phys.BlockHitResult(VAULT_CONTROLLER.getCenter(),net.minecraft.core.Direction.NORTH,VAULT_CONTROLLER,false);
                    p.gameMode.useItemOn(p,level,p.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,hit); return null;
                }); next();
            }
            case 19 -> {
                if(!pending.isDone()) return; pending.join();
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(VAULT_CONTROLLER);
                if(bank.getBlockState().getValue(StorageBlock.DAMAGE)<3 || bank.getBreachProgress(1)<1) return;
                require(VaultRenderer.doorAngle(0,1)==-115,"Third charge leaves the vault door hanging open");
                if(!Files.exists(Path.of(System.getProperty(OUTPUT),"vault-breached-world.png"))) { worldCapture="vault-breached-world"; return; }
                if(ticks<150) return;
                var loaded=new StorageBlockEntity(bank.getBlockPos(),bank.getBlockState()); loaded.setLevel(mc.level); loaded.onLoad();
                require(loaded.getBreachProgress(0)==1,"Previously breached vault loads into the damaged pose");
                pending=server.submit(()->{
                    var vault=(StorageBlockEntity)server.getPlayerList().getPlayer(uuid).serverLevel().getBlockEntity(VAULT_CONTROLLER);
                    require(vault.group().size()==27 && vault.inventory().getItem(26).getCount()==5,"Breach preserves structure and contents");
                    vault.repairAfter=vault.now(); require(vault.repair(),"Vault repairs after cooldown"); return null;
                }); next();
            }
            case 20 -> {
                if(!pending.isDone()) return; pending.join();
                var bank=(StorageBlockEntity)mc.level.getBlockEntity(VAULT_CONTROLLER);
                if(bank.getBreachProgress(1)>0) return;
                if(!Files.exists(Path.of(System.getProperty(OUTPUT),"vault-repaired-world.png"))) { worldCapture="vault-repaired-world"; return; }
                try { Files.writeString(Path.of(System.getProperty(OUTPUT),"result.txt"),"Bank storage native smoke passed\n729 slots, native transfers and scrollbar, key revocation, combined body/door/handle atlas, 54 safe slots without scrollbar, multi-page vault keeps scrollbar, slow 20 degree grip turn, synchronized opening and closing, key revocation closes safe, mounted pressure wave forces door open with an outward kick and overshoot, breached door remains open, damaged chunk load pose, stored contents preserved, repair state restores normal pose, safe and vault fuse synchronization, native priming sound, white flash frames, lit chunk loading, fixed 3x3x3 vault, corrected atlas model baking, synchronized vault door and cog, last viewer closes vault, three-charge vault breach, damaged vault chunk pose, preserved contents and repair.\n"); }
                catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                phase=99; mc.stop();
            }
        }
    }
    @SubscribeEvent public static void renderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if(System.getProperty(OUTPUT)==null || event.getStage()!=net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc=Minecraft.getInstance();
        if(phase>=7 && phase<=8 && mc.screen instanceof StorageScreen && mc.level.getBlockEntity(CENTER.east(5)) instanceof StorageBlockEntity bank
                && bank.getOpenNess(1)>0 && bank.getOpenNess(1)<0.25F
                && !Files.exists(Path.of(System.getProperty(OUTPUT),"safe-handle-pressed.png"))) capture("safe-handle-pressed");
        if(phase==12 && mc.level.getBlockEntity(CENTER.east(5)) instanceof StorageBlockEntity bank
                && bank.getBreachProgress(1)>0 && bank.getBreachProgress(1)<1
                && !Files.exists(Path.of(System.getProperty(OUTPUT),"safe-blast-opening.png"))) capture("safe-blast-opening");
        if(phase==14 && ticks>=15) for(int i=0;i<2;i++) {
            if(!(mc.level.getBlockEntity(CENTER.west(4-i*2)) instanceof StorageBlockEntity bank)
                    || !bank.getBlockState().getValue(StorageBlock.CHARGE) || bank.fuse<0) continue;
            int frame=SafeRenderer.chargeFlashes(bank.fuse)?1:0;
            if(!chargeFrames[i][frame]) {
                captureCharge(i,frame);
                chargeFrames[i][frame]=true;
            }
        }
        if(phase==17 && mc.level.getBlockEntity(VAULT_CONTROLLER) instanceof StorageBlockEntity bank) {
            String name=bank.getOpenNess(1)<.25F?"vault-cog-turn":"vault-door-opening";
            if(bank.getOpenNess(1)>0 && bank.getOpenNess(1)<1 && !Files.exists(Path.of(System.getProperty(OUTPUT),name+".png"))) capture(name);
        }
        if(phase==19 && mc.level.getBlockEntity(VAULT_CONTROLLER) instanceof StorageBlockEntity bank
                && bank.getBreachProgress(1)>0 && bank.getBreachProgress(1)<1 && !Files.exists(Path.of(System.getProperty(OUTPUT),"vault-blast-opening.png"))) capture("vault-blast-opening");
        if(worldCapture!=null) { capture(worldCapture); worldCapture=null; }
    }
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        if(System.getProperty(OUTPUT)!=null && phase==14
                && event.getOriginalSound().getLocation().equals(net.minecraft.sounds.SoundEvents.TNT_PRIMED.getLocation())) primedSounds++;
    }
    private static void captureCharge(int index,int frame) {
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            int centerX=image.getWidth()*(index==0?5:3)/8, centerY=image.getHeight()*53/100;
            int brightness=0;
            for(int y=centerY-8;y<centerY+8;y++) for(int x=centerX-8;x<centerX+8;x++) {
                int pixel=image.getPixelRGBA(x,y);
                brightness+=(pixel&255)+((pixel>>8)&255)+((pixel>>16)&255);
            }
            chargeBrightness[index][frame]=brightness/(256F*3);
            image.writeToFile(Path.of(System.getProperty(OUTPUT),(index==0?"vault":"safe")+(frame==1?"-fuse-white.png":"-fuse-textured.png")));
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static void buildVault(net.minecraft.server.level.ServerLevel level,BlockPos controller) {
        var center=controller.south();
        for(var pos:BlockPos.betweenClosed(center.offset(-1,-1,-1),center.offset(1,1,1)))
            level.setBlockAndUpdate(pos,(pos.equals(controller)?StorageRegistry.CONTROLLER.get():StorageRegistry.VAULT.get()).defaultBlockState());
        VaultConnections.merge((StorageBlockEntity)level.getBlockEntity(controller));
    }
    private static void next() { phase++; ticks=0; }
    private static void require(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
    private static void capture(String name) {
        try { var dir=Path.of(System.getProperty(OUTPUT)); Files.createDirectories(dir);
            try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(dir.resolve(name+".png")); }
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
