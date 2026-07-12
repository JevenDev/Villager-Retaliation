package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class EncounterService {
    private static final String OWNER="VillagerRetaliationEncounter";private static final String SCENE="VillagerRetaliationScene";
    private static final Map<UUID,ServerBossEvent> BOSS_BARS=new HashMap<>();
    private EncounterService(){}

    public static Result reconcileSpawn(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template){
        ServerLevel level=level(server,encounter);if(level==null||!level.hasChunkAt(encounter.anchor()))return Result.waiting("spawn anchor chunk is unloaded");
        int recoveryRadius=template.spawnMode()==EncounterTemplate.SpawnMode.NEAR_PLAYER?4:template.spawnRadius()+2;
        // Recover entities spawned before their UUID list was saved, but only inside the bounded anchor area and by exact durable owner tag.
        for(Entity entity:level.getEntities((Entity)null,new AABB(encounter.anchor()).inflate(recoveryRadius),value->ownedBy(value,encounter.id())))if(!encounter.spawned().contains(entity.getUUID()))encounter.addSpawn(entity.getUUID());
        notifyLocation(server,encounter,template,data);
        updateBossBar(server,encounter,template);

        List<EncounterTemplate.Member> desired=desiredMembers(template,encounter.partySize());
        int waveSize=template.scaledCount(encounter.partySize());int spawned=encounter.spawned().size();
        if(spawned>=encounter.expectedCount()){data.changed();return Result.active();}
        int target=encounter.expectedCount();
        if(template.spawnMode()==EncounterTemplate.SpawnMode.RAID_WAVES){
            int completedWaves=spawned/waveSize;target=Math.min(encounter.expectedCount(),(completedWaves+1)*waveSize);
            if(spawned>0&&spawned%waveSize==0){
                boolean cleared=encounter.defeated().size()>=spawned;
                if(template.waveTrigger()==EncounterTemplate.WaveTrigger.ALL_DEFEATED&&!cleared)return Result.waiting("waiting for wave "+completedWaves+" to be defeated");
                if(encounter.nextWaveAt()==0L){encounter.scheduleNextWave(level.getGameTime()+template.waveIntervalTicks());data.changed();}
                if(level.getGameTime()<encounter.nextWaveAt())return Result.waiting("waiting for wave "+(completedWaves+1));
                encounter.nextGeneration();encounter.scheduleNextWave(0L);
            }
        }
        for(int index=spawned;index<target;index++){
            EncounterTemplate.Member member=desired.get(index%waveSize);Entity entity=spawn(level,member,encounter,template,index);
            if(entity==null){encounter.fail("safe placement exhausted after "+template.placementAttempts()+" attempts for member "+index);hideBossBar(encounter.id());data.changed();return Result.failed(encounter.diagnostic());}
            encounter.addSpawn(entity.getUUID());data.changed();
        }
        updateBossBar(server,encounter,template);
        return Result.active();
    }

    public static Result refresh(MinecraftServer server,SceneSavedData data,EncounterInstance encounter){
        EncounterTemplate template=EncounterResources.template(server,encounter.templateId()).orElse(null);
        if(template==null){encounter.fail("encounter template is unavailable");hideBossBar(encounter.id());data.changed();return Result.failed(encounter.diagnostic());}
        if(encounter.state()==EncounterInstance.EncounterState.PREPARED||encounter.state()==EncounterInstance.EncounterState.SPAWNING||encounter.state()==EncounterInstance.EncounterState.ACTIVE){
            Result spawning=reconcileSpawn(server,data,encounter,template);if(spawning.status()==Status.FAILED||spawning.status()==Status.WAITING)return spawning;
        }
        encounter.checkComplete();if(encounter.state()==EncounterInstance.EncounterState.COMPLETED||encounter.state()==EncounterInstance.EncounterState.FAILED)hideBossBar(encounter.id());else updateBossBar(server,encounter,template);data.changed();return encounter.state()==EncounterInstance.EncounterState.COMPLETED?Result.completed():encounter.state()==EncounterInstance.EncounterState.FAILED?Result.failed(encounter.diagnostic()):Result.active();
    }

    public static Result cleanup(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,boolean forceRemove){hideBossBar(encounter.id());encounter.cleaning();boolean waiting=false;for(UUID id:encounter.spawned()){if(encounter.defeated().contains(id))continue;Entity entity=find(server,id);if(entity==null){waiting=true;continue;}if(forceRemove||encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS)entity.discard();else release(entity);}if(waiting&&(forceRemove||encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS)){data.changed();return Result.waiting("owned encounter entities are unloaded; cleanup will resume when they return");}if(encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.PRESERVE_IN_WORLD&&!forceRemove)encounter.released();else encounter.cleaned();data.changed();return Result.completed();}
    public static void onDeath(LivingEntity entity){CompoundTag persistent=entity.getPersistentData();if(!persistent.hasUUID(OWNER)||!(entity.level() instanceof ServerLevel level))return;SceneSavedData data=SceneSavedData.get(level);data.encounter(persistent.getUUID(OWNER)).ifPresent(encounter->{encounter.defeated(entity.getUUID());if(encounter.state()==EncounterInstance.EncounterState.COMPLETED)hideBossBar(encounter.id());else EncounterResources.template(level.getServer(),encounter.templateId()).ifPresent(template->updateBossBar(level.getServer(),encounter,template));data.changed();});}
    public static void onEntityJoin(Entity entity){CompoundTag persistent=entity.getPersistentData();if(!persistent.hasUUID(OWNER)||!(entity.level() instanceof ServerLevel level))return;SceneSavedData data=SceneSavedData.get(level);EncounterInstance encounter=data.encounter(persistent.getUUID(OWNER)).orElse(null);if(encounter==null)return;if(encounter.state()==EncounterInstance.EncounterState.RELEASED)release(entity);else if(encounter.state()==EncounterInstance.EncounterState.CLEANED)entity.discard();}

    private static Entity spawn(ServerLevel level,EncounterTemplate.Member member,EncounterInstance encounter,EncounterTemplate template,int index){
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(member.entityType());
        for(int attempt=0;attempt<template.placementAttempts();attempt++){
            BlockPos horizontal=horizontalPosition(encounter,template,index,attempt);if(!level.hasChunkAt(horizontal))continue;
            int y=template.spawnMode()==EncounterTemplate.SpawnMode.FIXED?encounter.anchor().getY():level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,horizontal.getX(),horizontal.getZ());BlockPos pos=new BlockPos(horizontal.getX(),y,horizontal.getZ());
            Entity entity=type.create(level);if(entity==null)continue;entity.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
            if(!level.noCollision(entity)){entity.discard();continue;}
            // Direct EntityType#create skips vanilla mob initialization (including a pillager's crossbow).
            if(entity instanceof Mob mob)mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);
            applyEquipment(level,entity,member);entity.getPersistentData().putUUID(OWNER,encounter.id());entity.getPersistentData().putUUID(SCENE,encounter.sceneId());entity.getPersistentData().putInt("VillagerRetaliationSpawnGeneration",encounter.spawnGeneration());
            if(level.addFreshEntity(entity))return entity;
        }
        return null;
    }

    private static BlockPos horizontalPosition(EncounterInstance encounter,EncounterTemplate template,int index,int attempt){
        int radius=switch(template.spawnMode()){case NEAR_PLAYER->Math.min(3,template.spawnRadius());case FIXED->Math.min(2,template.spawnRadius());case GROUP,RAID_WAVES->template.spawnRadius();};
        int width=radius*2+1;int seed=Math.floorMod(encounter.id().hashCode()*31+index*17+attempt*13,width*width);int dx=seed%width-radius;int dz=(seed/width)%width-radius;
        if(template.spawnMode()==EncounterTemplate.SpawnMode.RAID_WAVES&&radius>2){int inner=Math.max(2,radius/2);if(Math.abs(dx)<inner&&Math.abs(dz)<inner){dx=dx<0?-inner:inner;dz=dz<0?-inner:inner;}}
        return encounter.anchor().offset(dx,0,dz);
    }

    private static void applyEquipment(ServerLevel level,Entity entity,EncounterTemplate.Member member){
        if(!(entity instanceof LivingEntity living))return;
        for(var entry:member.equipment().entrySet()){
            EncounterTemplate.Gear gear=entry.getValue();ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(gear.item()),gear.count());
            var registry=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for(var enchantment:gear.enchantments().entrySet())registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT,enchantment.getKey())).ifPresent(holder->stack.enchant(holder,enchantment.getValue()));
            living.setItemSlot(entry.getKey(),stack);if(living instanceof Mob mob)mob.setDropChance(entry.getKey(),gear.dropChance());
        }
    }

    private static void notifyLocation(MinecraftServer server,EncounterInstance encounter,EncounterTemplate template,SceneSavedData data){
        if(encounter.locationNotified()||template.spawnMode()!=EncounterTemplate.SpawnMode.FIXED)return;
        String message=template.locationMessage().isBlank()?"Go to the encounter at {x}, {y}, {z}.":template.locationMessage();
        message=message.replace("{x}",Integer.toString(encounter.anchor().getX())).replace("{y}",Integer.toString(encounter.anchor().getY())).replace("{z}",Integer.toString(encounter.anchor().getZ())).replace("{dimension}",encounter.anchorDimension().toString());
        Component component=Component.literal(message);for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)player.sendSystemMessage(component);}
        encounter.markLocationNotified();data.changed();
    }

    private static void updateBossBar(MinecraftServer server,EncounterInstance encounter,EncounterTemplate template){
        if(template.spawnMode()!=EncounterTemplate.SpawnMode.RAID_WAVES||!template.bossBar()){hideBossBar(encounter.id());return;}
        int waveSize=template.scaledCount(encounter.partySize());int wave=Math.max(1,Math.min(template.waveCount(),(encounter.spawned().size()+waveSize-1)/waveSize));
        ServerBossEvent bar=BOSS_BARS.computeIfAbsent(encounter.id(),ignored->new ServerBossEvent(Component.literal("Raid"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.NOTCHED_10));
        bar.setName(Component.literal("Raid — Wave "+wave+"/"+template.waveCount()));
        bar.setProgress(Math.max(0.0F,Math.min(1.0F,(encounter.expectedCount()-encounter.defeated().size())/(float)Math.max(1,encounter.expectedCount()))));
        for(var player:new ArrayList<>(bar.getPlayers()))if(!encounter.participants().contains(player.getUUID()))bar.removePlayer(player);
        for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)bar.addPlayer(player);}
        bar.setVisible(true);
    }

    public static void hideBossBar(UUID encounterId){ServerBossEvent bar=BOSS_BARS.remove(encounterId);if(bar!=null)bar.removeAllPlayers();}

    private static List<EncounterTemplate.Member> desiredMembers(EncounterTemplate template,int partySize){List<EncounterTemplate.Member> values=new ArrayList<>();for(var member:template.members())for(int i=0;i<member.count();i++)values.add(member);int extra=template.scaledCount(partySize)-values.size();for(int i=0;i<extra;i++)values.add(template.members().getFirst());return values;}
    private static ServerLevel level(MinecraftServer server,EncounterInstance e){return server.getLevel(ResourceKey.create(Registries.DIMENSION,e.anchorDimension()));}private static Entity find(MinecraftServer server,UUID id){for(ServerLevel level:server.getAllLevels()){Entity e=level.getEntity(id);if(e!=null)return e;}return null;}private static boolean ownedBy(Entity entity,UUID id){return entity.getPersistentData().hasUUID(OWNER)&&id.equals(entity.getPersistentData().getUUID(OWNER));}private static void release(Entity entity){entity.getPersistentData().remove(OWNER);entity.getPersistentData().remove(SCENE);entity.getPersistentData().remove("VillagerRetaliationSpawnGeneration");}
    public record Result(Status status,String diagnostic){public static Result active(){return new Result(Status.ACTIVE,"");}public static Result waiting(String m){return new Result(Status.WAITING,m);}public static Result completed(){return new Result(Status.COMPLETED,"");}public static Result failed(String m){return new Result(Status.FAILED,m);}}public enum Status{ACTIVE,WAITING,COMPLETED,FAILED}
}
