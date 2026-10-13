package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.storage.*;
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
    private static CompletableFuture<?> pending;
    private static final BlockPos CENTER=new BlockPos(1024,-60,1024);
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
                        var pos=CENTER.offset(x,y,z); var block=x+y+z==0?StorageRegistry.CONTROLLER.get():StorageRegistry.VAULT.get();
                        level.setBlockAndUpdate(pos,block.defaultBlockState());
                        VaultConnections.merge((StorageBlockEntity)level.getBlockEntity(pos));
                    }
                    var bank=(StorageBlockEntity)level.getBlockEntity(CENTER); bank.issue(p);
                    int index=0;
                    for(var member:bank.group()) for(int i=0;i<27;i++,index++) member.inventory().setItem(i,new ItemStack(index%2==0?Items.COPPER_INGOT:Items.IRON_INGOT,index%64+1));
                    var last=bank.group().getLast(); last.inventory().setItem(26,new ItemStack(Items.DIAMOND,31));
                    level.setBlockAndUpdate(CENTER.east(5),StorageRegistry.SAFE.get().defaultBlockState().setValue(StorageBlock.CHARGE,true));
                    level.setBlockAndUpdate(CENTER,bank.getBlockState().setValue(StorageBlock.CHARGE,true));
                    bank.open(p); return null;
                }); next();
            }
            case 1 -> {
                if(!pending.isDone() || !(mc.screen instanceof StorageScreen screen)) return; pending.join();
                require(screen.getMenu().capacity==729,"Native GUI exposes all 729 fixed server slots"); capture("vault-first-page");
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
                try { Files.writeString(Path.of(System.getProperty(OUTPUT),"result.txt"),"Bank storage native smoke passed\n729 slots; wheel outside slot region; last-page synchronization; native shift-click; scrollbar click/drag; server closes after key removal; mounted charge world capture.\n"); }
                catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                phase=99; mc.stop();
            }
        }
    }
    private static void next() { phase++; ticks=0; }
    private static void require(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
    private static void capture(String name) {
        try { var dir=Path.of(System.getProperty(OUTPUT)); Files.createDirectories(dir);
            try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(dir.resolve(name+".png")); }
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
