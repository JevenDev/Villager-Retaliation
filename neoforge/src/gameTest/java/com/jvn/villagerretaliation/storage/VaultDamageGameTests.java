package com.jvn.villagerretaliation.storage;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bank_storage")
@PrefixGameTestTemplate(false)
public final class VaultDamageGameTests {
    @GameTest(template="empty") public static void chargesHurtNearbyEntitiesWithoutBreakingStorage(GameTestHelper h) {
        var bank=BankStorageGameTests.vault(h,new BlockPos(2,3,5),Direction.NORTH);
        bank.inventory().setItem(0,new ItemStack(Items.DIAMOND,7));
        var near=target(h,bank.getBlockPos().getCenter().add(0,0,-1.5));
        var far=target(h,bank.getBlockPos().getCenter().add(0,0,-6));
        var wall=bank.getBlockPos().offset(2,0,-1);
        h.getLevel().setBlockAndUpdate(wall,Blocks.GLASS.defaultBlockState());
        detonate(bank);
        h.assertTrue(near.getHealth()<near.getMaxHealth(),"first charge damages a nearby entity");
        h.assertValueEqual(far.getHealth(),far.getMaxHealth(),"charge has less reach than normal TNT");
        h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.GLASS),"charge leaves nearby blocks intact");
        h.assertValueEqual(bank.inventory().getItem(0).getCount(),7,"charge preserves stored items");
        h.assertValueEqual(bank.damage,1,"first charge damages the lock once");
        h.assertFalse(bank.breached(),"first charge does not breach the vault");
        float health=near.getHealth();
        for(int i=0;i<12;i++) tick(bank);
        h.assertValueEqual(near.getHealth(),health,"non-final charge has no door impact");
        near.discard(); far.discard(); h.succeed();
    }

    @GameTest(template="empty") public static void finalBreachAddsOneDoorHitDespiteBlastCooldown(GameTestHelper h) {
        var bank=BankStorageGameTests.vault(h,new BlockPos(2,3,5),Direction.NORTH);
        bank.hit(); bank.hit();
        var victim=target(h,bank.getBlockPos().getCenter().add(0,0,-2));
        detonate(bank);
        for(int i=0;i<10;i++) tick(bank);
        var source=victim.getLastDamageSource();
        h.assertTrue(source!=null && source.typeHolder().getRegisteredName().equals("villagerretaliation:vault_door"),"swing deals its own damage after the blast");
        h.assertTrue(victim.getHealth()<=victim.getMaxHealth()-12,"door damage is applied in addition to blast damage");
        float health=victim.getHealth();
        victim.invulnerableTime=0;
        for(int i=0;i<20;i++) tick(bank);
        h.assertValueEqual(victim.getHealth(),health,"hanging door cannot repeatedly damage the victim");
        var saved=bank.saveWithoutMetadata(h.getLevel().registryAccess());
        bank.loadWithComponents(saved,h.getLevel().registryAccess());
        tick(bank);
        h.assertValueEqual(victim.getHealth(),health,"reloading a breached vault cannot replay impact");
        victim.setHealth(1F);
        var lethalImpact=new VaultDoorImpact();
        for(int i=0;i<10;i++) lethalImpact.tick(bank);
        h.assertTrue(victim.isDeadOrDying(),"door impact can kill a mob");
        h.assertValueEqual(victim.getLastDamageSource().getLocalizedDeathMessage(victim).getString(),"Iron Golem got squashed by the vault door","mob death names the vault door");
        victim.discard(); h.succeed();
    }

    @GameTest(template="empty") public static void swingTracksEveryFacingAndExcludesEmptyCorners(GameTestHelper h) {
        for(var facing:Direction.Plane.HORIZONTAL) {
            var bank=BankStorageGameTests.vault(h,new BlockPos(3,3,3),facing);
            var front=bank.getBlockPos().getCenter().relative(facing,2);
            var behind=bank.getBlockPos().getCenter().relative(facing,-2);
            boolean struck=false;
            for(int i=0;i<=100;i++) {
                struck|=VaultDoorImpact.intersects(bank,AABB.ofSize(front,0.5,1,0.5),0,i/100F);
                h.assertFalse(VaultDoorImpact.intersects(bank,AABB.ofSize(behind,0.5,1,0.5),0,i/100F),"door does not hit behind the vault");
                h.assertFalse(VaultDoorImpact.intersects(bank,AABB.ofSize(front.add(0,4,0),0.5,1,0.5),0,i/100F),"door does not hit above its sweep");
            }
            h.assertTrue(struck,"door reaches the front for "+facing);
            h.assertFalse(VaultDoorImpact.intersects(bank,AABB.ofSize(front,0.5,1,0.5),0,1),"open door leaves the middle of its swept bounds empty");
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void doorHitAwardsAdvancementButInvulnerablePlayersDoNotQualify(GameTestHelper h) {
        var bank=BankStorageGameTests.vault(h,new BlockPos(2,3,5),Direction.NORTH);
        var advancement=h.getLevel().getServer().getAdvancements().get(VillagerRetaliation.id("reputation/knock_knock"));
        h.assertTrue(advancement!=null,"door advancement loads");
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"DoorTest"),false);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.getAbilities().invulnerable=false;
        player.setPos(bank.getBlockPos().getCenter().add(0,0,-2));
        for(int i=0;i<60;i++) player.tick();
        try {
            var impact=new VaultDoorImpact();
            player.invulnerableTime=20;
            for(int i=0;i<10;i++) impact.tick(bank);
            h.assertValueEqual(player.getHealth(),8F,"door delivers exactly one six-heart hit");
            h.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),"struck player earns Knock Knock");
            player.getAdvancements().revoke(advancement,"unlocked");
            player.getAbilities().invulnerable=true;
            player.setHealth(20F);
            impact=new VaultDoorImpact();
            for(int i=0;i<10;i++) impact.tick(bank);
            h.assertValueEqual(player.getHealth(),20F,"invulnerable player takes no damage");
            h.assertFalse(player.getAdvancements().getOrStartProgress(advancement).isDone(),"rejected hit awards no advancement");
            player.getAbilities().invulnerable=false;
            player.setHealth(1F);
            impact=new VaultDoorImpact();
            for(int i=0;i<10;i++) impact.tick(bank);
            h.assertTrue(player.isDeadOrDying(),"door impact can kill a player");
            h.assertValueEqual(player.getLastDamageSource().getLocalizedDeathMessage(player).getString(),"DoorTest got squashed by the vault door","player death names the vault door");
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    private static IronGolem target(GameTestHelper h,Vec3 position) {
        var entity=EntityType.IRON_GOLEM.create(h.getLevel());
        entity.setNoAi(true); entity.setNoGravity(true); entity.setPos(position);
        h.getLevel().addFreshEntity(entity); return entity;
    }
    private static void tick(StorageBlockEntity bank) {
        StorageBlockEntity.tick(bank.getLevel(),bank.getBlockPos(),bank.getBlockState(),bank);
    }
    private static void detonate(StorageBlockEntity bank) {
        bank.getLevel().setBlockAndUpdate(bank.getBlockPos(),bank.getBlockState().setValue(StorageBlock.CHARGE,true));
        bank.fuse=1; tick(bank);
    }
}
