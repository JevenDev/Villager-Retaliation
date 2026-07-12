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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class EncounterService {
    private static final String OWNER="VillagerRetaliationEncounter";private static final String SCENE="VillagerRetaliationScene";
    private static final String BOSS="VillagerRetaliationEncounterBoss";private static final String BOSS_COLOR="VillagerRetaliationBossColor";private static final String BOSS_OVERLAY="VillagerRetaliationBossOverlay";
    private static final Map<UUID,ServerBossEvent> BOSS_BARS=new HashMap<>();
    private static final Map<UUID,ServerBossEvent> MOB_BOSS_BARS=new HashMap<>();
    private EncounterService(){}

    public static Result reconcileSpawn(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template){
        ServerLevel level=level(server,encounter);if(level==null)return Result.waiting("spawn anchor dimension is unavailable");
        Result area=updateArea(server,data,encounter,template,level.getGameTime());if(area.status()!=Status.ACTIVE)return area;
        if(!level.hasChunkAt(encounter.anchor()))return Result.waiting("spawn anchor chunk is unloaded");
        int recoveryRadius=template.spawnMode()==EncounterTemplate.SpawnMode.NEAR_PLAYER?4:template.spawnRadius()+2;
        // Recover entities spawned before their UUID list was saved, but only inside the bounded anchor area and by exact durable owner tag.
        for(Entity entity:level.getEntities((Entity)null,new AABB(encounter.anchor()).inflate(recoveryRadius),value->ownedBy(value,encounter.id())))if(!encounter.spawned().contains(entity.getUUID()))encounter.addSpawn(entity.getUUID());
        notifyLocation(server,encounter,template,data);

        int spawned=encounter.spawned().size();ensureWaveIdentity(encounter,template,spawned);
        if(encounter.currentWaveIndex()<0||encounter.currentWaveIndex()>=template.waveCount()||!template.wave(encounter.currentWaveIndex()).id().equals(encounter.currentWaveId())){encounter.fail("persisted wave identity no longer matches encounter template");data.changed();return Result.failed(encounter.diagnostic());}
        updateBossBar(server,encounter,template);
        updateMobBossBars(server,encounter);
        if(spawned>=encounter.expectedCount()){data.changed();return Result.active();}
        int waveIndex=encounter.currentWaveIndex();EncounterTemplate.Wave wave=template.wave(waveIndex);int start=template.waveStart(waveIndex,encounter.partySize());int waveSize=template.scaledCount(wave,encounter.partySize());int target=start+waveSize;
        if(spawned>=target&&waveIndex+1<template.waveCount()){
            EncounterTemplate.Wave next=template.wave(waveIndex+1);if(next.trigger()==EncounterTemplate.WaveTrigger.ALL_DEFEATED&&encounter.defeated().size()<target)return Result.waiting("waiting for wave "+wave.id()+" to be defeated");
            encounter.advanceWave(waveIndex+1,next.id());encounter.nextGeneration();data.changed();waveIndex++;wave=next;start=target;waveSize=template.scaledCount(wave,encounter.partySize());target=start+waveSize;
        }
        if(!encounter.startedWaves().contains(wave.id())){if(wave.delayTicks()>0&&encounter.nextWaveAt()==0L){encounter.scheduleNextWave(level.getGameTime()+wave.delayTicks());data.changed();}if(level.getGameTime()<encounter.nextWaveAt())return Result.waiting("waiting to start wave "+wave.id());encounter.markWaveStarted(wave.id());encounter.scheduleNextWave(0L);fireWaveHooks(server,data,encounter,wave);data.changed();}
        List<EncounterTemplate.Member> desired=desiredMembers(wave,template,encounter.partySize());
        for(int index=spawned;index<target;index++){
            EncounterTemplate.Member member=desired.get(index-start);SpawnResult spawnedEntity=spawn(level,member,encounter,template,index);Entity entity=spawnedEntity.entity();
            if(entity==null){encounter.fail(spawnedEntity.diagnostic().isBlank()?"safe placement exhausted after "+template.placementAttempts()+" attempts for member "+index:spawnedEntity.diagnostic());hideBossBars(encounter);data.changed();return Result.failed(encounter.diagnostic());}
            encounter.addSpawn(entity.getUUID());data.changed();
        }
        updateBossBar(server,encounter,template);
        updateMobBossBars(server,encounter);
        return Result.active();
    }

    private static void ensureWaveIdentity(EncounterInstance encounter,EncounterTemplate template,int spawned){if(!encounter.currentWaveId().isBlank())return;int index=0;while(index+1<template.waveCount()){int boundary=template.waveStart(index+1,encounter.partySize());if(spawned<boundary)break;if(spawned>boundary){index++;continue;}EncounterTemplate.Wave next=template.wave(index+1);if(encounter.nextWaveAt()>0L||next.trigger()==EncounterTemplate.WaveTrigger.TIMER||encounter.defeated().size()>=boundary)index++;break;}encounter.initializeWave(index,template.wave(index).id());}
    private static void fireWaveHooks(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate.Wave wave){for(EncounterTemplate.WaveHook hook:wave.hooks()){String receipt=wave.id()+"/"+hook.id();if(!encounter.markWaveHookFired(receipt))continue;data.changed();Component message=Component.literal(hook.text());for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)player.sendSystemMessage(message);}}}

    public static Result updateArea(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template,long gameTime){
        EncounterTemplate.Area area=template.area();if(area==null)return Result.active();boolean pause=false;
        for(UUID participant:encounter.participants()){
            var player=server.getPlayerList().getPlayer(participant);if(player==null)continue;
            if(inside(player.level().dimension().location(),player.blockPosition(),encounter,area)){encounter.clearLeave(participant);continue;}
            if(area.leaveBehavior()!=EncounterTemplate.LeaveBehavior.IGNORE&&!encounter.leaveWarned().contains(participant)){
                String message=switch(area.leaveBehavior()){
                    case WARN->"You have left the quest encounter area.";
                    case PAUSE->"The quest encounter is paused while you are outside its area.";
                    case FAIL->"Return to the quest encounter area within "+area.leaveTimeoutTicks()+" ticks or the encounter will fail.";
                    case IGNORE->"";
                };
                if(!message.isBlank())player.sendSystemMessage(Component.literal(message));encounter.markLeaveWarned(participant);
            }
            if(area.leaveBehavior()==EncounterTemplate.LeaveBehavior.PAUSE)pause=true;
            if(area.leaveBehavior()==EncounterTemplate.LeaveBehavior.FAIL){
                long deadline=encounter.leaveDeadlines().getOrDefault(participant,gameTime+area.leaveTimeoutTicks());
                if(!encounter.leaveDeadlines().containsKey(participant))encounter.setLeaveDeadline(participant,deadline);
                if(gameTime>=deadline){encounter.fail("participant remained outside the encounter area past the leave timeout");hideBossBars(encounter);data.changed();return Result.failed(encounter.diagnostic());}
            }
        }
        if(encounter.areaPaused()!=pause)encounter.setAreaPaused(pause);
        enforceMobArea(server,encounter,area,gameTime);data.changed();
        return pause?Result.waiting("encounter paused while a participant is outside its area"):Result.active();
    }

    private static void enforceMobArea(MinecraftServer server,EncounterInstance encounter,EncounterTemplate.Area area,long gameTime){
        if(area.mobBehavior()==EncounterTemplate.MobBehavior.IGNORE)return;
        ServerLevel anchorLevel=level(server,encounter);
        for(UUID id:encounter.spawned()){
            if(encounter.defeated().contains(id)){encounter.clearMobDeadline(id);continue;}
            Entity entity=find(server,id);if(entity==null)continue;
            if(inside(entity.level().dimension().location(),entity.blockPosition(),encounter,area)){encounter.clearMobDeadline(id);continue;}
            if(area.mobBehavior()==EncounterTemplate.MobBehavior.RETURN){
                if(entity.level()==anchorLevel&&entity instanceof Mob mob)mob.getNavigation().moveTo(encounter.anchor().getX()+.5D,encounter.anchor().getY(),encounter.anchor().getZ()+.5D,1.1D);
                continue;
            }
            long deadline=encounter.mobDeadlines().getOrDefault(id,gameTime+area.mobTimeoutTicks());
            if(!encounter.mobDeadlines().containsKey(id))encounter.setMobDeadline(id,deadline);
            if(gameTime>=deadline&&entity.level()==anchorLevel&&anchorLevel!=null&&anchorLevel.hasChunkAt(encounter.anchor())){
                entity.teleportTo(encounter.anchor().getX()+.5D,encounter.anchor().getY(),encounter.anchor().getZ()+.5D);encounter.clearMobDeadline(id);
            }
        }
    }

    private static boolean inside(net.minecraft.resources.ResourceLocation dimension,BlockPos position,EncounterInstance encounter,EncounterTemplate.Area area){
        if(!encounter.anchorDimension().equals(dimension))return false;long dx=position.getX()-encounter.anchor().getX();long dz=position.getZ()-encounter.anchor().getZ();
        return Math.abs(position.getY()-encounter.anchor().getY())<=area.verticalRadius()&&dx*dx+dz*dz<=(long)area.radius()*area.radius();
    }

    public static Result refresh(MinecraftServer server,SceneSavedData data,EncounterInstance encounter){
        EncounterTemplate template=EncounterResources.template(server,encounter.templateId()).orElse(null);
        if(template==null){encounter.fail("encounter template is unavailable");hideBossBars(encounter);data.changed();return Result.failed(encounter.diagnostic());}
        if(encounter.state()==EncounterInstance.EncounterState.PREPARED||encounter.state()==EncounterInstance.EncounterState.SPAWNING||encounter.state()==EncounterInstance.EncounterState.ACTIVE){
            Result spawning=reconcileSpawn(server,data,encounter,template);if(spawning.status()==Status.FAILED||spawning.status()==Status.WAITING)return spawning;
        }
        encounter.checkComplete();if(encounter.state()==EncounterInstance.EncounterState.COMPLETED||encounter.state()==EncounterInstance.EncounterState.FAILED)hideBossBars(encounter);else{updateBossBar(server,encounter,template);updateMobBossBars(server,encounter);}data.changed();return encounter.state()==EncounterInstance.EncounterState.COMPLETED?Result.completed():encounter.state()==EncounterInstance.EncounterState.FAILED?Result.failed(encounter.diagnostic()):Result.active();
    }

    public static Result cleanup(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,boolean forceRemove){hideBossBars(encounter);encounter.cleaning();boolean waiting=false;for(UUID id:encounter.spawned()){if(encounter.defeated().contains(id))continue;Entity entity=find(server,id);if(entity==null){waiting=true;continue;}if(forceRemove||encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS)entity.discard();else release(entity);}if(waiting&&(forceRemove||encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS)){data.changed();return Result.waiting("owned encounter entities are unloaded; cleanup will resume when they return");}if(encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.PRESERVE_IN_WORLD&&!forceRemove)encounter.released();else encounter.cleaned();data.changed();return Result.completed();}
    public static void onDeath(LivingEntity entity){hideMobBossBar(entity.getUUID());CompoundTag persistent=entity.getPersistentData();if(!persistent.hasUUID(OWNER)||!(entity.level() instanceof ServerLevel level))return;SceneSavedData data=SceneSavedData.get(level);data.encounter(persistent.getUUID(OWNER)).ifPresent(encounter->{encounter.defeated(entity.getUUID());if(encounter.state()==EncounterInstance.EncounterState.COMPLETED)hideBossBars(encounter);else EncounterResources.template(level.getServer(),encounter.templateId()).ifPresent(template->{updateBossBar(level.getServer(),encounter,template);updateMobBossBars(level.getServer(),encounter);});data.changed();});}
    public static void onEntityJoin(Entity entity){CompoundTag persistent=entity.getPersistentData();if(!persistent.hasUUID(OWNER)||!(entity.level() instanceof ServerLevel level))return;SceneSavedData data=SceneSavedData.get(level);EncounterInstance encounter=data.encounter(persistent.getUUID(OWNER)).orElse(null);if(encounter==null)return;if(encounter.state()==EncounterInstance.EncounterState.RELEASED)release(entity);else if(encounter.state()==EncounterInstance.EncounterState.CLEANED)entity.discard();else if(encounter.state()==EncounterInstance.EncounterState.PREPARED||encounter.state()==EncounterInstance.EncounterState.SPAWNING||encounter.state()==EncounterInstance.EncounterState.ACTIVE)updateMobBossBars(level.getServer(),encounter);else hideMobBossBar(entity.getUUID());}

    private static SpawnResult spawn(ServerLevel level,EncounterTemplate.Member member,EncounterInstance encounter,EncounterTemplate template,int index){
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(member.entityType());
        for(int attempt=0;attempt<template.placementAttempts();attempt++){
            BlockPos horizontal=horizontalPosition(encounter,template,index,attempt);if(!level.hasChunkAt(horizontal))continue;
            int y=template.spawnMode()==EncounterTemplate.SpawnMode.FIXED?encounter.anchor().getY():level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,horizontal.getX(),horizontal.getZ());BlockPos pos=new BlockPos(horizontal.getX(),y,horizontal.getZ());
            Entity entity=type.create(level);if(entity==null)continue;entity.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
            if(!level.noCollision(entity)){entity.discard();continue;}
            // Direct EntityType#create skips vanilla mob initialization (including a pillager's crossbow).
            if(entity instanceof Mob mob)mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);
            String optionError=applyMobOptions(entity,member);if(!optionError.isBlank()){entity.discard();return new SpawnResult(null,optionError);}
            applyEquipment(level,entity,member);entity.getPersistentData().putUUID(OWNER,encounter.id());entity.getPersistentData().putUUID(SCENE,encounter.sceneId());entity.getPersistentData().putInt("VillagerRetaliationSpawnGeneration",encounter.spawnGeneration());
            if(level.addFreshEntity(entity))return new SpawnResult(entity,"");
        }
        return new SpawnResult(null,"");
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

    private static String applyMobOptions(Entity entity,EncounterTemplate.Member member){
        EncounterTemplate.MobOptions options=member.options();if(!options.customName().isBlank())entity.setCustomName(Component.literal(options.customName()));entity.setCustomNameVisible(options.nameVisible());entity.setGlowingTag(options.glowing());
        if(options.persistent()){if(entity instanceof Mob mob)mob.setPersistenceRequired();else return "persistent encounter presentation requires a mob entity for "+member.entityType();}
        if((!options.attributes().isEmpty()||options.boss())&&!(entity instanceof LivingEntity))return "elite attributes and boss designation require a living entity for "+member.entityType();
        if(entity instanceof LivingEntity living){boolean healthChanged=false;for(var entry:options.attributes().entrySet()){AttributeInstance attribute=attribute(living,entry.getKey());if(attribute==null)return "entity "+member.entityType()+" does not support encounter attribute "+entry.getKey();attribute.setBaseValue(entry.getValue());if(entry.getKey().getPath().equals("max_health"))healthChanged=true;}if(healthChanged)living.setHealth(living.getMaxHealth());}
        if(options.boss()){CompoundTag tag=entity.getPersistentData();tag.putBoolean(BOSS,true);tag.putString(BOSS_COLOR,options.bossBarColor().name());tag.putString(BOSS_OVERLAY,options.bossBarOverlay().name());}
        return "";
    }

    private static AttributeInstance attribute(LivingEntity living,net.minecraft.resources.ResourceLocation id){return switch(id.toString()){case "minecraft:max_health"->living.getAttribute(Attributes.MAX_HEALTH);case "minecraft:movement_speed"->living.getAttribute(Attributes.MOVEMENT_SPEED);case "minecraft:attack_damage"->living.getAttribute(Attributes.ATTACK_DAMAGE);case "minecraft:armor"->living.getAttribute(Attributes.ARMOR);case "minecraft:knockback_resistance"->living.getAttribute(Attributes.KNOCKBACK_RESISTANCE);default->null;};}

    private static void updateMobBossBars(MinecraftServer server,EncounterInstance encounter){
        for(UUID id:encounter.spawned()){Entity entity=find(server,id);if(!(entity instanceof LivingEntity living)||!entity.getPersistentData().getBoolean(BOSS)||!living.isAlive()){hideMobBossBar(id);continue;}CompoundTag tag=entity.getPersistentData();BossEvent.BossBarColor color=bossColor(tag.getString(BOSS_COLOR));BossEvent.BossBarOverlay overlay=bossOverlay(tag.getString(BOSS_OVERLAY));ServerBossEvent bar=MOB_BOSS_BARS.get(id);if(bar==null||bar.getColor()!=color||bar.getOverlay()!=overlay){hideMobBossBar(id);bar=new ServerBossEvent(entity.getDisplayName(),color,overlay);MOB_BOSS_BARS.put(id,bar);}bar.setName(entity.getDisplayName());bar.setProgress(Math.max(0.0F,Math.min(1.0F,living.getHealth()/Math.max(1.0F,living.getMaxHealth()))));for(var player:new ArrayList<>(bar.getPlayers()))if(!encounter.participants().contains(player.getUUID()))bar.removePlayer(player);for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)bar.addPlayer(player);}bar.setVisible(true);}
    }
    private static BossEvent.BossBarColor bossColor(String value){try{return BossEvent.BossBarColor.valueOf(value);}catch(IllegalArgumentException e){return BossEvent.BossBarColor.RED;}}
    private static BossEvent.BossBarOverlay bossOverlay(String value){try{return BossEvent.BossBarOverlay.valueOf(value);}catch(IllegalArgumentException e){return BossEvent.BossBarOverlay.PROGRESS;}}
    private static void hideMobBossBar(UUID entityId){ServerBossEvent bar=MOB_BOSS_BARS.remove(entityId);if(bar!=null)bar.removeAllPlayers();}
    public static void hideBossBars(EncounterInstance encounter){hideBossBar(encounter.id());for(UUID id:encounter.spawned())hideMobBossBar(id);}
    public static boolean hasMobBossBar(UUID entityId){return MOB_BOSS_BARS.containsKey(entityId);}

    private static void notifyLocation(MinecraftServer server,EncounterInstance encounter,EncounterTemplate template,SceneSavedData data){
        if(encounter.locationNotified()||template.spawnMode()!=EncounterTemplate.SpawnMode.FIXED)return;
        String message=template.locationMessage().isBlank()?"Go to the encounter at {x}, {y}, {z}.":template.locationMessage();
        message=message.replace("{x}",Integer.toString(encounter.anchor().getX())).replace("{y}",Integer.toString(encounter.anchor().getY())).replace("{z}",Integer.toString(encounter.anchor().getZ())).replace("{dimension}",encounter.anchorDimension().toString());
        Component component=Component.literal(message);for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)player.sendSystemMessage(component);}
        encounter.markLocationNotified();data.changed();
    }

    private static void updateBossBar(MinecraftServer server,EncounterInstance encounter,EncounterTemplate template){
        if(template.spawnMode()!=EncounterTemplate.SpawnMode.RAID_WAVES||!template.bossBar()){hideBossBar(encounter.id());return;}
        int wave=Math.max(0,Math.min(template.waveCount()-1,encounter.currentWaveIndex()));EncounterTemplate.Wave definition=template.wave(wave);
        ServerBossEvent bar=BOSS_BARS.computeIfAbsent(encounter.id(),ignored->new ServerBossEvent(Component.literal("Raid"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.NOTCHED_10));
        String title=definition.bossBarTitle().isBlank()?"Raid - Wave "+(wave+1)+"/"+template.waveCount():definition.bossBarTitle();bar.setName(Component.literal(title));
        bar.setProgress(Math.max(0.0F,Math.min(1.0F,(encounter.expectedCount()-encounter.defeated().size())/(float)Math.max(1,encounter.expectedCount()))));
        for(var player:new ArrayList<>(bar.getPlayers()))if(!encounter.participants().contains(player.getUUID()))bar.removePlayer(player);
        for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)bar.addPlayer(player);}
        bar.setVisible(true);
    }

    public static void hideBossBar(UUID encounterId){ServerBossEvent bar=BOSS_BARS.remove(encounterId);if(bar!=null)bar.removeAllPlayers();}

    private static List<EncounterTemplate.Member> desiredMembers(EncounterTemplate.Wave wave,EncounterTemplate template,int partySize){List<EncounterTemplate.Member> values=new ArrayList<>();for(var member:wave.members())for(int i=0;i<member.count();i++)values.add(member);int extra=template.scaledCount(wave,partySize)-values.size();for(int i=0;i<extra;i++)values.add(wave.members().getFirst());return values;}
    private static ServerLevel level(MinecraftServer server,EncounterInstance e){return server.getLevel(ResourceKey.create(Registries.DIMENSION,e.anchorDimension()));}private static Entity find(MinecraftServer server,UUID id){for(ServerLevel level:server.getAllLevels()){Entity e=level.getEntity(id);if(e!=null)return e;}return null;}private static boolean ownedBy(Entity entity,UUID id){return entity.getPersistentData().hasUUID(OWNER)&&id.equals(entity.getPersistentData().getUUID(OWNER));}private static void release(Entity entity){hideMobBossBar(entity.getUUID());entity.getPersistentData().remove(OWNER);entity.getPersistentData().remove(SCENE);entity.getPersistentData().remove("VillagerRetaliationSpawnGeneration");entity.getPersistentData().remove(BOSS);entity.getPersistentData().remove(BOSS_COLOR);entity.getPersistentData().remove(BOSS_OVERLAY);}
    private record SpawnResult(Entity entity,String diagnostic){}
    public record Result(Status status,String diagnostic){public static Result active(){return new Result(Status.ACTIVE,"");}public static Result waiting(String m){return new Result(Status.WAITING,m);}public static Result completed(){return new Result(Status.COMPLETED,"");}public static Result failed(String m){return new Result(Status.FAILED,m);}}public enum Status{ACTIVE,WAITING,COMPLETED,FAILED}
}
