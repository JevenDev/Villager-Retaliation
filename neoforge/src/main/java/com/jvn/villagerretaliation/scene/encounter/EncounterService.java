package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class EncounterService {
    private static final String OWNER="VillagerRetaliationEncounter";private static final String SCENE="VillagerRetaliationScene";
    private static final String ALLY_OWNER="VillagerRetaliationEncounterAlly";private static final String ALLY_KEY="VillagerRetaliationAllyKey";
    private static final String SPAWN_INDEX="VillagerRetaliationSpawnIndex";private static final String SPAWN_POINT="VillagerRetaliationSpawnPoint";private static final String SPAWN_SEQUENCE="VillagerRetaliationSpawnSequence";
    private static final String MEMBER_ID="VillagerRetaliationMemberId";
    private static final String BOSS="VillagerRetaliationEncounterBoss";private static final String BOSS_COLOR="VillagerRetaliationBossColor";private static final String BOSS_OVERLAY="VillagerRetaliationBossOverlay";
    private static final Map<UUID,ServerBossEvent> BOSS_BARS=new HashMap<>();
    private static final Map<UUID,ServerBossEvent> MOB_BOSS_BARS=new HashMap<>();
    private EncounterService(){}

    public static Result reconcileSpawn(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template){
        ServerLevel level=level(server,encounter);if(level==null)return Result.waiting("spawn anchor dimension is unavailable");
        Result retry=processFailureRetry(server,data,encounter,template,level.getGameTime());if(retry.status()!=Status.ACTIVE)return retry;
        Result environment=applyEnvironment(server,data,encounter,template);if(environment.status()!=Status.ACTIVE){if(environment.status()==Status.FAILED){encounter.fail(environment.diagnostic());restoreEnvironment(server,data,encounter);data.changed();}return environment;}
        encounter.ensureStartedAt(level.getGameTime());data.changed();
        Result area=updateArea(server,data,encounter,template,level.getGameTime());if(area.status()!=Status.ACTIVE)return area;
        Result allies=reconcileAllies(server,data,encounter,template,level.getGameTime());if(allies.status()!=Status.ACTIVE)return allies;
        if(template.spawnPoints().isEmpty()&&!level.hasChunkAt(encounter.anchor()))return Result.waiting("spawn anchor chunk is unloaded");
        int recoveryRadius=template.spawnMode()==EncounterTemplate.SpawnMode.NEAR_PLAYER?4:template.spawnRadius()+2;
        // Recover entities spawned before their UUID list was saved, but only inside the bounded anchor area and by exact durable owner tag.
        if(template.spawnPoints().isEmpty()&&level.hasChunkAt(encounter.anchor()))for(Entity entity:level.getEntities((Entity)null,new AABB(encounter.anchor()).inflate(recoveryRadius),value->ownedBy(value,encounter.id())))recoverSpawn(encounter,entity);
        for(EncounterInstance.ResolvedSpawnPoint point:encounter.resolvedSpawnPoints().values())if(level.hasChunkAt(point.position()))for(Entity entity:level.getEntities((Entity)null,new AABB(point.position()).inflate(4),value->ownedBy(value,encounter.id())))recoverSpawn(encounter,entity);
        notifyLocation(server,encounter,template,data);
        updateGuidance(server,data,encounter,template,level.getGameTime());

        int spawned=encounter.spawned().size();ensureWaveIdentity(encounter,template,spawned);
        if(encounter.currentWaveIndex()<0||encounter.currentWaveIndex()>=template.waveCount()||!template.wave(encounter.currentWaveIndex()).id().equals(encounter.currentWaveId())){encounter.fail("persisted wave identity no longer matches encounter template");data.changed();return Result.failed(encounter.diagnostic());}
        if(!template.spawnPoints().isEmpty()){if(encounter.resolvedSpawnPoints().size()!=template.spawnPoints().size()||template.spawnPoints().stream().anyMatch(point->!encounter.resolvedSpawnPoints().containsKey(point.id()))){encounter.fail("authored spawn points were not durably resolved when the encounter was created");data.changed();return Result.failed(encounter.diagnostic());}for(var selection:encounter.selectedSpawnPoints().entrySet())if(selection.getKey()<0||selection.getKey()>=encounter.expectedCount()||!encounter.resolvedSpawnPoints().containsKey(selection.getValue())){encounter.fail("persisted encounter contains an invalid authored spawn-point selection");data.changed();return Result.failed(encounter.diagnostic());}}
        Result phases=evaluatePhases(server,data,encounter,template,level.getGameTime());if(phases.status()==Status.FAILED)return phases;
        Result objectives=evaluateObjectives(server,data,encounter,template,level.getGameTime());String rewardError=grantEligibleRewards(server,data,encounter,template);if(!rewardError.isBlank()){encounter.fail(rewardError);data.changed();return Result.failed(rewardError);}if(objectives.status()!=Status.ACTIVE)return objectives;
        updateBossBar(server,encounter,template);
        updateMobBossBars(server,encounter);
        if(spawned>=encounter.expectedCount()){updateAllyTargeting(server,encounter);data.changed();return Result.active();}
        int waveIndex=encounter.currentWaveIndex();EncounterTemplate.Wave wave=template.wave(waveIndex);int start=template.waveStart(waveIndex,encounter.partySize());int waveSize=template.scaledCount(wave,encounter.partySize());int target=start+waveSize;
        if(spawned>=target&&waveIndex+1<template.waveCount()){
            EncounterTemplate.Wave next=template.wave(waveIndex+1);if(next.trigger()==EncounterTemplate.WaveTrigger.ALL_DEFEATED&&encounter.defeated().size()<target)return Result.waiting("waiting for wave "+wave.id()+" to be defeated");
            encounter.advanceWave(waveIndex+1,next.id());encounter.nextGeneration();data.changed();waveIndex++;wave=next;start=target;waveSize=template.scaledCount(wave,encounter.partySize());target=start+waveSize;
        }
        if(!encounter.startedWaves().contains(wave.id())){if(wave.delayTicks()>0&&encounter.nextWaveAt()==0L){encounter.scheduleNextWave(level.getGameTime()+wave.delayTicks());data.changed();}if(level.getGameTime()<encounter.nextWaveAt())return Result.waiting("waiting to start wave "+wave.id());encounter.markWaveStarted(wave.id());encounter.scheduleNextWave(0L);fireWaveHooks(server,data,encounter,wave);data.changed();phases=evaluatePhases(server,data,encounter,template,level.getGameTime());if(phases.status()==Status.FAILED)return phases;}
        List<SpawnMember> desired=desiredMembers(wave,template,encounter.partySize());
        for(int index=spawned;index<target;index++){
            SpawnMember desiredMember=desired.get(index-start);PointSelection selection=selectSpawnPoint(server,encounter,template,index,desiredMember.groupIndex());if(selection.waiting())return Result.waiting(selection.diagnostic());if(!selection.diagnostic().isBlank()){encounter.fail(selection.diagnostic());data.changed();return Result.failed(encounter.diagnostic());}data.changed();EncounterTemplate.Member member=desiredMember.member();SpawnResult spawnedEntity=spawn(level,member,encounter,template,index,selection.point());Entity entity=spawnedEntity.entity();
            if(spawnedEntity.waiting())return Result.waiting(spawnedEntity.diagnostic());
            if(entity==null){encounter.fail(spawnedEntity.diagnostic().isBlank()?"safe placement exhausted after "+template.placementAttempts()+" attempts for member "+index:spawnedEntity.diagnostic());hideBossBars(encounter);data.changed();return Result.failed(encounter.diagnostic());}
            encounter.addSpawn(entity.getUUID());data.changed();
        }
        updateBossBar(server,encounter,template);
        updateMobBossBars(server,encounter);
        updateAllyTargeting(server,encounter);
        return evaluateObjectives(server,data,encounter,template,level.getGameTime());
    }

    private static void ensureWaveIdentity(EncounterInstance encounter,EncounterTemplate template,int spawned){if(!encounter.currentWaveId().isBlank())return;int index=0;while(index+1<template.waveCount()){int boundary=template.waveStart(index+1,encounter.partySize());if(spawned<boundary)break;if(spawned>boundary){index++;continue;}EncounterTemplate.Wave next=template.wave(index+1);if(encounter.nextWaveAt()>0L||next.trigger()==EncounterTemplate.WaveTrigger.TIMER||encounter.defeated().size()>=boundary)index++;break;}encounter.initializeWave(index,template.wave(index).id());}
    private static Result reconcileAllies(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template,long gameTime){
        if(template.allies().isEmpty()){encounter.setAllyCompletionBlocked(false);return Result.active();}SceneInstance scene=data.get(encounter.sceneId()).orElse(null);if(scene==null){encounter.fail("encounter allies cannot find their owning scene");data.changed();return Result.failed(encounter.diagnostic());}
        for(EncounterTemplate.Ally definition:template.allies())for(int index=0;index<definition.count();index++){String key=definition.count()==1?definition.id():definition.id()+"."+(index+1);EncounterInstance.AllyIdentity identity=encounter.allies().get(key);if(identity==null){Entity entity;if(!definition.actorAlias().isBlank()){var binding=scene.actorBindings().get(definition.actorAlias());if(binding==null||binding.entityId()==null||binding.lastDimension()==null||binding.lastPosition()==null){encounter.fail("ally "+definition.id()+" references unresolved scene actor "+definition.actorAlias());data.changed();return Result.failed(encounter.diagnostic());}entity=find(server,binding.entityId());if(entity==null){ServerLevel actorLevel=server.getLevel(ResourceKey.create(Registries.DIMENSION,binding.lastDimension()));if(actorLevel==null||!actorLevel.hasChunkAt(binding.lastPosition()))return Result.waiting("waiting for bound ally "+definition.id()+" to load");encounter.fail("bound ally "+definition.id()+" is missing from its loaded snapshot");data.changed();return Result.failed(encounter.diagnostic());}}else{SpawnResult result=spawnAlly(server,encounter,definition,key,index);if(result.waiting())return Result.waiting(result.diagnostic());entity=result.entity();if(entity==null){encounter.fail(result.diagnostic());data.changed();return Result.failed(encounter.diagnostic());}}ResourceLocation type=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());boolean original=entity.isInvulnerable();if(definition.invulnerable())entity.setInvulnerable(true);entity.getPersistentData().putUUID(ALLY_OWNER,encounter.id());entity.getPersistentData().putString(ALLY_KEY,key);encounter.putAlly(new EncounterInstance.AllyIdentity(key,definition.id(),index,entity.getUUID(),type,entity.level().dimension().location(),entity.blockPosition(),1,EncounterInstance.AllyState.ACTIVE,0L,!definition.actorAlias().isBlank(),definition.cleanupPolicy(),original,definition.invulnerable()));data.changed();continue;}
            Entity entity=find(server,identity.entityId());if(identity.state()==EncounterInstance.AllyState.DEAD){if(definition.requiredSurvival()){encounter.fail("required ally "+definition.id()+" was defeated");data.changed();return Result.failed(encounter.diagnostic());}if(definition.revivable()&&gameTime>=identity.recoverAt()){SpawnResult result=spawnAlly(server,encounter,definition,key,index);if(result.waiting())return Result.waiting(result.diagnostic());if(result.entity()==null){encounter.fail(result.diagnostic());data.changed();return Result.failed(encounter.diagnostic());}entity=result.entity();boolean original=entity.isInvulnerable();if(definition.invulnerable())entity.setInvulnerable(true);encounter.replaceAlly(identity.replaced(entity.getUUID(),entity.level().dimension().location(),entity.blockPosition(),original,definition.invulnerable()));data.changed();}continue;}if(entity==null){ServerLevel lastLevel=server.getLevel(ResourceKey.create(Registries.DIMENSION,identity.dimension()));if(lastLevel==null||!lastLevel.hasChunkAt(identity.position()))continue;if(definition.replacementPolicy()==EncounterTemplate.AllyReplacementPolicy.MISSING_IF_LOADED){SpawnResult result=spawnAlly(server,encounter,definition,key,index);if(result.waiting())return Result.waiting(result.diagnostic());if(result.entity()==null){encounter.fail(result.diagnostic());data.changed();return Result.failed(encounter.diagnostic());}entity=result.entity();boolean original=entity.isInvulnerable();if(definition.invulnerable())entity.setInvulnerable(true);encounter.replaceAlly(identity.replaced(entity.getUUID(),entity.level().dimension().location(),entity.blockPosition(),original,definition.invulnerable()));data.changed();}else{encounter.replaceAlly(identity.missing(0L));if(definition.requiredSurvival()){encounter.fail("required ally "+definition.id()+" is missing");data.changed();return Result.failed(encounter.diagnostic());}}continue;}if(!(entity instanceof LivingEntity)){encounter.fail("ally "+definition.id()+" resolved to a non-living entity");data.changed();return Result.failed(encounter.diagnostic());}if(definition.invulnerable())entity.setInvulnerable(true);entity.getPersistentData().putUUID(ALLY_OWNER,encounter.id());entity.getPersistentData().putString(ALLY_KEY,key);encounter.replaceAlly(identity.observed(entity.level().dimension().location(),entity.blockPosition(),identity.originalInvulnerable(),identity.invulnerabilityApplied()||definition.invulnerable()));}
        boolean blocked=false;for(EncounterTemplate.Ally definition:template.allies())if(definition.affectsCompletion())for(EncounterInstance.AllyIdentity identity:encounter.allies().values())if(identity.definitionId().equals(definition.id())&&identity.state()!=EncounterInstance.AllyState.ACTIVE){boolean recoverable=identity.state()==EncounterInstance.AllyState.DEAD&&definition.revivable()||identity.state()==EncounterInstance.AllyState.MISSING&&definition.replacementPolicy()==EncounterTemplate.AllyReplacementPolicy.MISSING_IF_LOADED;if(!recoverable){encounter.fail("completion-affecting ally "+definition.id()+" cannot recover");data.changed();return Result.failed(encounter.diagnostic());}blocked=true;break;}encounter.setAllyCompletionBlocked(blocked);data.changed();return Result.active();
    }
    private static SpawnResult spawnAlly(MinecraftServer server,EncounterInstance encounter,EncounterTemplate.Ally ally,String key,int index){ServerLevel level=level(server,encounter);if(level==null||!level.hasChunkAt(encounter.anchor()))return new SpawnResult(null,"ally spawn anchor chunk is unloaded",true);ResourceLocation entityType=ally.entityType();if(entityType==null){EncounterInstance.AllyIdentity prior=encounter.allies().get(key);entityType=prior==null?null:prior.entityType();}if(entityType==null)return new SpawnResult(null,"bound ally "+ally.id()+" has no captured entity type for recovery",false);EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(entityType);for(int attempt=0;attempt<32;attempt++){int radius=3,width=radius*2+1,seed=Math.floorMod(encounter.id().hashCode()*31+index*17+attempt*13,width*width);BlockPos pos=encounter.anchor().offset(seed%width-radius,0,(seed/width)%width-radius);if(!level.hasChunkAt(pos))continue;Entity entity=type.create(level);if(!(entity instanceof LivingEntity)){if(entity!=null)entity.discard();return new SpawnResult(null,"ally "+ally.id()+" entity must be living",false);}entity.moveTo(pos.getX()+.5D,pos.getY(),pos.getZ()+.5D,0,0);if(!level.noCollision(entity)){entity.discard();continue;}if(entity instanceof Mob mob)mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);String error=applyMobOptions(entity,new EncounterTemplate.Member(entityType,1,ally.equipment(),ally.options(),""));if(!error.isBlank()){entity.discard();return new SpawnResult(null,error,false);}applyEquipment(level,entity,new EncounterTemplate.Member(entityType,1,ally.equipment(),ally.options(),""));entity.getPersistentData().putUUID(ALLY_OWNER,encounter.id());entity.getPersistentData().putString(ALLY_KEY,key);if(level.addFreshEntity(entity))return new SpawnResult(entity,"",false);}return new SpawnResult(null,"safe placement exhausted for ally "+ally.id(),false);}
    private static void updateAllyTargeting(MinecraftServer server,EncounterInstance encounter){List<LivingEntity> allies=new ArrayList<>();for(EncounterInstance.AllyIdentity identity:encounter.allies().values()){Entity entity=find(server,identity.entityId());if(entity instanceof LivingEntity living&&living.isAlive())allies.add(living);}List<LivingEntity> enemies=new ArrayList<>();for(UUID id:encounter.spawned()){if(encounter.defeated().contains(id))continue;Entity entity=find(server,id);if(entity instanceof LivingEntity living&&living.isAlive())enemies.add(living);}for(LivingEntity ally:allies)if(ally instanceof Mob mob){if(mob.getTarget()!=null&&allies.stream().anyMatch(value->value.getUUID().equals(mob.getTarget().getUUID())))mob.setTarget(null);if(mob.getTarget()==null&&!enemies.isEmpty())mob.setTarget(nearest(ally,enemies));}for(LivingEntity enemy:enemies)if(enemy instanceof Mob mob){if(mob.getTarget()!=null&&enemies.stream().anyMatch(value->value.getUUID().equals(mob.getTarget().getUUID())))mob.setTarget(null);if(mob.getTarget()==null&&!allies.isEmpty())mob.setTarget(nearest(enemy,allies));}}
    private static LivingEntity nearest(LivingEntity source,List<LivingEntity> values){LivingEntity result=null;double best=Double.POSITIVE_INFINITY;for(LivingEntity value:values){double distance=source.distanceToSqr(value);if(distance<best){best=distance;result=value;}}return result;}
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

    private static Result applyEnvironment(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template){EncounterTemplate.Environment environment=template.environment();if(environment==null)return Result.active();ServerLevel level=level(server,encounter);if(level==null)return Result.waiting("environment dimension is unavailable");for(EncounterTemplate.TemporaryBlock definition:environment.temporaryBlocks()){BlockPos position=encounter.anchor().offset(definition.offset());EncounterInstance.EnvironmentBlock owned=encounter.environmentBlocks().get(definition.id());var placed=BuiltInRegistries.BLOCK.get(definition.block()).defaultBlockState();if(owned==null){if(!level.hasChunkAt(position))return Result.waiting("environment block "+definition.id()+" chunk is unloaded");var original=level.getBlockState(position);if(!original.canBeReplaced())return Result.failed("environment block "+definition.id()+" cannot replace occupied position "+position.toShortString());owned=new EncounterInstance.EnvironmentBlock(definition.id(),encounter.anchorDimension(),position,original,placed,EncounterInstance.EnvironmentBlockState.PREPARED);encounter.putEnvironmentBlock(owned);data.changed();}if(owned.state()==EncounterInstance.EnvironmentBlockState.PREPARED){if(!level.hasChunkAt(position))return Result.waiting("environment block "+definition.id()+" chunk is unloaded");var current=level.getBlockState(position);if(current.equals(owned.placed()))encounter.replaceEnvironmentBlock(owned.applied());else if(current.equals(owned.original())){level.setBlock(position,owned.placed(),Block.UPDATE_CLIENTS);encounter.replaceEnvironmentBlock(owned.applied());}else encounter.replaceEnvironmentBlock(owned.preserved());data.changed();}}for(EncounterTemplate.EnvironmentCue cue:environment.cues())if(!encounter.firedEnvironmentCues().contains(cue.id())){encounter.fireEnvironmentCue(cue.id());data.changed();BlockPos position=encounter.anchor().offset(cue.offset());switch(cue.type()){case SOUND,MUSIC->{var sound=BuiltInRegistries.SOUND_EVENT.get(cue.resource());SoundSource source=cue.type()==EncounterTemplate.EnvironmentCueType.MUSIC?SoundSource.MUSIC:SoundSource.AMBIENT;for(UUID id:encounter.participants()){var player=server.getPlayerList().getPlayer(id);if(player!=null&&player.serverLevel()==level)player.playNotifySound(sound,source,cue.volume(),cue.pitch());}}case PARTICLES,GLOWING_COLUMN->{var particle=BuiltInRegistries.PARTICLE_TYPE.get(cue.resource());if(particle instanceof SimpleParticleType simple){int columns=cue.type()==EncounterTemplate.EnvironmentCueType.GLOWING_COLUMN?cue.height():1;for(int y=0;y<columns;y++)level.sendParticles(simple,position.getX()+.5,position.getY()+.5+y,position.getZ()+.5,Math.max(1,cue.count()/columns),.2,.2,.2,.01);}}}}return Result.active();}
    private static boolean restoreEnvironment(MinecraftServer server,SceneSavedData data,EncounterInstance encounter){boolean complete=true;for(EncounterInstance.EnvironmentBlock owned:encounter.environmentBlocks().values()){if(owned.state()==EncounterInstance.EnvironmentBlockState.RESTORED||owned.state()==EncounterInstance.EnvironmentBlockState.PRESERVED)continue;ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,owned.dimension()));if(level==null||!level.hasChunkAt(owned.position())){complete=false;continue;}var current=level.getBlockState(owned.position());if(current.equals(owned.placed())){level.setBlock(owned.position(),owned.original(),Block.UPDATE_CLIENTS);encounter.replaceEnvironmentBlock(owned.restored());}else encounter.replaceEnvironmentBlock(owned.preserved());data.changed();}return complete;}
    public static void maintainCleanup(MinecraftServer server,SceneSavedData data){for(EncounterInstance encounter:data.encounters()){if(encounter.completionRewardEligible())EncounterResources.template(server,encounter.templateId()).ifPresent(template->grantEligibleRewards(server,data,encounter,template));if(encounter.state()==EncounterInstance.EncounterState.CLEANING_UP)cleanup(server,data,encounter,false);else if(encounter.state()==EncounterInstance.EncounterState.COMPLETED||encounter.state()==EncounterInstance.EncounterState.FAILED||encounter.state()==EncounterInstance.EncounterState.CANCELLED){restoreEnvironment(server,data,encounter);removeGuidance(server,data,encounter);}}}

    private static String grantEligibleRewards(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template){String profileError=grantVillagerCompletionGuts(server,data,encounter,template);if(!profileError.isBlank())return profileError;EncounterTemplate.RewardPolicy policy=template.rewards();if(policy==null||policy.waves().isEmpty()&&policy.phases().isEmpty()&&policy.completion().isEmpty())return "";if(encounter.state()!=EncounterInstance.EncounterState.ACTIVE&&!encounter.completionRewardEligible())return "";SceneInstance scene=data.get(encounter.sceneId()).orElse(null);if(scene==null)return "encounter rewards require their owning scene";List<RewardGrant> grants=new ArrayList<>();int gone=encounter.defeated().size()+(encounter.completionCondition()==EncounterTemplate.CompletionCondition.ALL_GONE?encounter.missing().size():0);for(EncounterTemplate.Reward reward:policy.waves()){int index=-1;for(int i=0;i<template.waveCount();i++)if(template.wave(i).id().equals(reward.target())){index=i;break;}if(index>=0&&gone>=template.waveStart(index,encounter.partySize())+template.scaledCount(template.wave(index),encounter.partySize()))grants.add(new RewardGrant(reward,"wave/"+reward.target()));}for(EncounterTemplate.Reward reward:policy.phases()){int fires=encounter.phaseFireCounts().getOrDefault(reward.target(),0);for(int ordinal=1;ordinal<=fires;ordinal++)grants.add(new RewardGrant(reward,"phase/"+reward.target()+"/"+ordinal));}if(encounter.completionRewardEligible())for(EncounterTemplate.Reward reward:policy.completion())grants.add(new RewardGrant(reward,"completion"));for(RewardGrant grant:grants){if(grant.reward().lootTable()!=null&&server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,grant.reward().lootTable()))==LootTable.EMPTY)return "encounter reward "+grant.reward().id()+" references unavailable loot table "+grant.reward().lootTable();for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player==null)continue;String operation=scene.id()+"/encounter/"+encounter.id()+"/reward/"+grant.trigger()+"/"+grant.reward().id()+"/player/"+participant;SceneOperationReceipt existing=scene.receipts().get(operation);if(existing!=null){if(existing.state()!=SceneOperationReceipt.ReceiptState.COMPLETED)existing.completed(server.overworld().getGameTime(),"reserved before reward delivery; replay suppressed");data.changed();continue;}SceneOperationReceipt receipt=scene.prepareReceipt(operation,grant.reward().lootTable()==null?SceneOperationReceipt.Kind.ITEM_GRANT:SceneOperationReceipt.Kind.LOOT_GRANT,server.overworld().getGameTime());receipt.applied(server.overworld().getGameTime(),"recipient="+participant);receipt.completed(server.overworld().getGameTime(),"reserved before reward delivery");data.changed();deliverReward(player,encounter,grant.reward(),grant.trigger());}}return "";}
    public static int completionGutsReward(EncounterTemplate template) {
        if (template == null) return 0;
        boolean elite = false;
        for (int waveIndex = 0; waveIndex < template.waveCount(); waveIndex++) {
            for (EncounterTemplate.Member member : template.wave(waveIndex).members()) {
                if (member.options().boss()) return 5;
                elite |= !member.options().attributes().isEmpty();
            }
        }
        return elite ? 3 : 0;
    }

    private static String grantVillagerCompletionGuts(
            MinecraftServer server,
            SceneSavedData data,
            EncounterInstance encounter,
            EncounterTemplate template) {
        int amount = completionGutsReward(template);
        if (amount == 0 || !encounter.completionRewardEligible()) return "";
        SceneInstance scene = data.get(encounter.sceneId()).orElse(null);
        if (scene == null) return "encounter profile rewards require their owning scene";
        long gameTime = server.overworld().getGameTime();
        ResourceLocation villagerType = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VILLAGER);
        for (EncounterInstance.AllyIdentity ally : encounter.allies().values()) {
            if (ally.state() == EncounterInstance.AllyState.DEAD
                    || ally.state() == EncounterInstance.AllyState.REMOVED) continue;
            Entity entity = find(server, ally.entityId());
            Villager villager = entity instanceof Villager value ? value : null;
            if (entity != null && villager == null) continue;
            ServerLevel profileLevel = villager != null
                    ? (ServerLevel) villager.level()
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, ally.dimension()));
            if (profileLevel == null || villager == null
                    && (!ally.entityType().equals(villagerType)
                    || VillagerProfileManager.getProfile(profileLevel, ally.entityId()).isEmpty())) continue;
            String operation = scene.id() + "/encounter/" + encounter.id()
                    + "/profile/guts/" + ally.key();
            SceneOperationReceipt existing = scene.receipts().get(operation);
            if (existing != null) {
                if (existing.state() != SceneOperationReceipt.ReceiptState.COMPLETED) {
                    existing.completed(gameTime, "reserved before profile reward delivery; replay suppressed");
                    data.changed();
                }
                continue;
            }
            if (villager != null) {
                VillagerProfileManager.adjustAttribute(
                        profileLevel, villager, VillagerSocialAttribute.GUTS, amount);
            } else {
                VillagerProfileManager.adjustAttribute(
                        profileLevel, ally.entityId(), VillagerSocialAttribute.GUTS, amount);
            }
            SceneOperationReceipt receipt = scene.prepareReceipt(
                    operation, SceneOperationReceipt.Kind.PROFILE_ATTRIBUTE_CHANGE, gameTime);
            String evidence = "villager=" + ally.entityId() + " guts=" + amount;
            receipt.applied(gameTime, evidence);
            receipt.completed(gameTime, evidence);
            data.changed();
        }
        return "";
    }
    private static void deliverReward(net.minecraft.server.level.ServerPlayer player,EncounterInstance encounter,EncounterTemplate.Reward reward,String trigger){if(reward.item()!=null){ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(reward.item()),reward.count());if(!reward.trophyName().isBlank())stack.set(DataComponents.CUSTOM_NAME,Component.literal(reward.trophyName()));give(player,stack);return;}LootTable table=player.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,reward.lootTable()));LootParams params=new LootParams.Builder(player.serverLevel()).withLuck(player.getLuck()).create(LootContextParamSets.EMPTY);long seed=encounter.variantSeed()^encounter.id().getMostSignificantBits()^player.getUUID().getLeastSignificantBits()^reward.id().hashCode()^trigger.hashCode();for(ItemStack stack:table.getRandomItems(params,RandomSource.create(seed)))if(!stack.isEmpty())give(player,stack.copy());}
    private static void give(net.minecraft.server.level.ServerPlayer player,ItemStack stack){if(!player.addItem(stack)&&!stack.isEmpty())player.drop(stack,false);}
    private record RewardGrant(EncounterTemplate.Reward reward,String trigger){}

    public static void onDrops(LivingDropsEvent event){if(event.getEntity().level() instanceof ServerLevel level)onDrops(event,SceneSavedData.get(level));}
    public static void onDrops(LivingDropsEvent event,SceneSavedData data){LivingEntity entity=event.getEntity();if(!(entity.level() instanceof ServerLevel level))return;CompoundTag tag=entity.getPersistentData();if(!tag.hasUUID(OWNER))return;EncounterInstance encounter=data.encounter(tag.getUUID(OWNER)).orElse(null);EncounterTemplate template=encounter==null?null:EncounterResources.template(level.getServer(),encounter.templateId()).orElse(null);EncounterTemplate.RewardPolicy policy=template==null?null:template.rewards();if(policy==null||policy.dropPolicy()==EncounterTemplate.DropPolicy.NORMAL)return;event.getDrops().clear();if(encounter.state()!=EncounterInstance.EncounterState.ACTIVE&&!encounter.completionRewardEligible())return;if(policy.dropPolicy()==EncounterTemplate.DropPolicy.SUPPRESS)return;int spawnIndex=tag.getInt(SPAWN_INDEX);EncounterTemplate.Member member=memberForSpawn(template,encounter,spawnIndex);if(member==null)return;if(policy.dropPolicy()==EncounterTemplate.DropPolicy.AUTHORED_ONLY){for(var entry:member.equipment().entrySet().stream().sorted(java.util.Comparator.comparingInt(value->value.getKey().ordinal())).toList()){EncounterTemplate.Gear gear=entry.getValue();long seed=encounter.variantSeed()^spawnIndex*0x9E3779B97F4A7C15L^entry.getKey().ordinal();if(gear.dropChance()>0&&RandomSource.create(seed).nextFloat()<gear.dropChance())event.getDrops().add(drop(entity,equipmentStack(level,gear)));}return;}List<EncounterTemplate.Trophy> trophies=policy.trophies().stream().filter(trophy->trophy.memberId().equals(member.id())).toList();if(trophies.isEmpty()||!encounter.claimTrophySpawn(spawnIndex))return;data.changed();for(EncounterTemplate.Trophy trophy:trophies){ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(trophy.item()),trophy.count());if(!trophy.name().isBlank())stack.set(DataComponents.CUSTOM_NAME,Component.literal(trophy.name()));event.getDrops().add(drop(entity,stack));}}
    private static ItemEntity drop(LivingEntity entity,ItemStack stack){return new ItemEntity(entity.level(),entity.getX(),entity.getY(),entity.getZ(),stack);}
    private static EncounterTemplate.Member memberForSpawn(EncounterTemplate template,EncounterInstance encounter,int spawnIndex){for(int waveIndex=0;waveIndex<template.waveCount();waveIndex++){int start=template.waveStart(waveIndex,encounter.partySize()),count=template.scaledCount(template.wave(waveIndex),encounter.partySize());if(spawnIndex>=start&&spawnIndex<start+count){List<SpawnMember> desired=desiredMembers(template.wave(waveIndex),template,encounter.partySize());int relative=spawnIndex-start;return relative<desired.size()?desired.get(relative).member():null;}}return null;}
    private static ItemStack equipmentStack(ServerLevel level,EncounterTemplate.Gear gear){ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(gear.item()),gear.count());var registry=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);for(var enchantment:gear.enchantments().entrySet())registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT,enchantment.getKey())).ifPresent(holder->stack.enchant(holder,enchantment.getValue()));return stack;}

    private static Result processFailureRetry(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template,long gameTime){if(encounter.state()!=EncounterInstance.EncounterState.PAUSED&&encounter.state()!=EncounterInstance.EncounterState.RETRY_WAIT)return Result.active();EncounterTemplate.FailureAction action=encounter.pendingFailureAction();if(action==null){encounter.fail("persisted retry is missing its failure action");data.changed();return Result.failed(encounter.diagnostic());}if(gameTime<encounter.retryAt())return Result.waiting((encounter.state()==EncounterInstance.EncounterState.PAUSED?"encounter paused":"encounter retry")+" until "+encounter.retryAt());if(action==EncounterTemplate.FailureAction.PAUSE){encounter.resumeFailurePause();data.changed();return Result.active();}int waveIndex=encounter.currentWaveIndex(),start=action==EncounterTemplate.FailureAction.RESET_WAVE?template.waveStart(waveIndex,encounter.partySize()):0;java.util.stream.Stream<EncounterTemplate.Wave> resetWaves=action==EncounterTemplate.FailureAction.RESET_WAVE?java.util.stream.Stream.of(template.wave(waveIndex)):java.util.stream.IntStream.range(0,template.waveCount()).mapToObj(template::wave);Set<String> resetMemberIds=resetWaves.flatMap(wave->wave.members().stream()).map(EncounterTemplate.Member::id).filter(id->!id.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());Set<UUID> removed=encounter.resetForRetry(start,template.failure()!=null&&template.failure().retainDefeated(),resetMemberIds);for(UUID id:removed){Entity entity=find(server,id);if(entity!=null)entity.discard();}if(action==EncounterTemplate.FailureAction.RESTART_ENCOUNTER)encounter.advanceWave(0,template.wave(0).id());else encounter.advanceWave(waveIndex,template.wave(waveIndex).id());data.changed();return Result.active();}
    private static void applyFailure(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template,EncounterTemplate.FailureAction action,String cause,String actorAlias,long gameTime){if(encounter.state()!=EncounterInstance.EncounterState.ACTIVE)return;EncounterTemplate.FailurePolicy failure=template.failure();if(failure==null||action==EncounterTemplate.FailureAction.FAIL){encounter.fail(cause);hideBossBars(encounter);data.changed();return;}if(action==EncounterTemplate.FailureAction.BRANCH_SCENE){SceneInstance scene=data.get(encounter.sceneId()).orElse(null);var definition=scene==null?null:SceneResources.scene(server,scene.sceneId()).orElse(null);var record=scene==null?null:scene.stepRecords().get(scene.currentStep());if(definition==null||record==null||!definition.steps().containsKey(failure.branchStep())){encounter.fail("failure branch step "+failure.branchStep()+" is unavailable");hideBossBars(encounter);data.changed();return;}String operation=scene.id()+"/encounter/"+encounter.id()+"/failure/"+encounter.attemptCount();SceneOperationReceipt receipt=scene.prepareReceipt(operation,SceneOperationReceipt.Kind.SCENE_TRANSITION,gameTime);if(receipt.state()!=SceneOperationReceipt.ReceiptState.COMPLETED){record.chooseTransition(failure.branchStep());receipt.applied(gameTime,"target="+failure.branchStep());receipt.completed(gameTime,"target="+failure.branchStep());}encounter.fail(cause);hideBossBars(encounter);data.changed();return;}if(encounter.attemptCount()>=failure.maxAttempts()){encounter.fail(cause+"; retry attempts exhausted");hideBossBars(encounter);data.changed();return;}encounter.scheduleFailure(action,gameTime+failure.retryDelayTicks(),cause,actorAlias);hideBossBars(encounter);data.changed();}
    private static boolean protectedActor(EncounterTemplate template,String alias){if(template.completionObjectives()!=null&&template.completionObjectives().objectives().stream().anyMatch(objective->objective.type()==EncounterTemplate.ObjectiveType.PROTECT_ACTOR&&objective.actorAlias().equals(alias)))return true;return template.allies().stream().anyMatch(ally->ally.requiredSurvival()&&ally.actorAlias().equals(alias));}

    public static Result refresh(MinecraftServer server,SceneSavedData data,EncounterInstance encounter){
        EncounterTemplate template=EncounterResources.template(server,encounter.templateId()).orElse(null);
        if(template==null){encounter.fail("encounter template is unavailable");hideBossBars(encounter);data.changed();return Result.failed(encounter.diagnostic());}
        if(encounter.state()==EncounterInstance.EncounterState.PREPARED||encounter.state()==EncounterInstance.EncounterState.SPAWNING||encounter.state()==EncounterInstance.EncounterState.ACTIVE||encounter.state()==EncounterInstance.EncounterState.PAUSED||encounter.state()==EncounterInstance.EncounterState.RETRY_WAIT){
            Result spawning=reconcileSpawn(server,data,encounter,template);if(spawning.status()==Status.FAILED||spawning.status()==Status.WAITING)return spawning;
        }
        encounter.checkComplete();if(encounter.customCompletion()&&encounter.state()==EncounterInstance.EncounterState.ACTIVE)evaluateObjectives(server,data,encounter,template,server.overworld().getGameTime());String rewardError=grantEligibleRewards(server,data,encounter,template);if(!rewardError.isBlank())encounter.fail(rewardError);if(encounter.state()==EncounterInstance.EncounterState.COMPLETED||encounter.state()==EncounterInstance.EncounterState.FAILED)hideBossBars(encounter);else{updateBossBar(server,encounter,template);updateMobBossBars(server,encounter);}data.changed();return encounter.state()==EncounterInstance.EncounterState.COMPLETED?Result.completed():encounter.state()==EncounterInstance.EncounterState.FAILED?Result.failed(encounter.diagnostic()):Result.active();
    }

    public static Result cleanup(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,boolean forceRemove){hideBossBars(encounter);removeGuidance(server,data,encounter);encounter.cleaning();boolean waiting=!restoreEnvironment(server,data,encounter);for(UUID id:encounter.spawned()){if(encounter.defeated().contains(id))continue;Entity entity=find(server,id);if(entity==null){waiting=true;continue;}if(forceRemove||encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS)entity.discard();else release(entity);}for(EncounterInstance.AllyIdentity ally:encounter.allies().values()){if(ally.state()==EncounterInstance.AllyState.DEAD||ally.state()==EncounterInstance.AllyState.REMOVED)continue;Entity entity=find(server,ally.entityId());boolean remove=forceRemove||ally.cleanupPolicy()==EncounterTemplate.AllyCleanupPolicy.REMOVE;if(entity==null){if(remove)waiting=true;else encounter.setAllyState(ally.key(),EncounterInstance.AllyState.PRESERVED);continue;}if(remove){entity.discard();encounter.setAllyState(ally.key(),EncounterInstance.AllyState.REMOVED);}else{releaseAlly(entity,ally);encounter.setAllyState(ally.key(),EncounterInstance.AllyState.PRESERVED);}}if(waiting){data.changed();return Result.waiting("owned encounter entities, allies, or environment blocks are unloaded; cleanup will resume when they return");}if(encounter.cleanupPolicy()==EncounterTemplate.CleanupPolicy.PRESERVE_IN_WORLD&&!forceRemove)encounter.released();else encounter.cleaned();data.changed();return Result.completed();}
    public static void onDeath(LivingEntity entity){if(entity.level() instanceof ServerLevel level)onDeath(entity,SceneSavedData.get(level));else hideMobBossBar(entity.getUUID());}
    public static void onDeath(LivingEntity entity,SceneSavedData data){
        hideMobBossBar(entity.getUUID());if(!(entity.level() instanceof ServerLevel level))return;Set<UUID> affected=new java.util.LinkedHashSet<>();
        for(EncounterInstance candidate:data.encounters()){if(candidate.state()!=EncounterInstance.EncounterState.ACTIVE)continue;EncounterTemplate template=EncounterResources.template(level.getServer(),candidate.templateId()).orElse(null);if(template==null)continue;if(candidate.participants().contains(entity.getUUID())&&template.failure()!=null)applyFailure(level.getServer(),data,candidate,template,template.failure().onPlayerDeath(),"participant died","",level.getGameTime());SceneInstance scene=data.get(candidate.sceneId()).orElse(null);if(scene==null)continue;for(var binding:scene.actorBindings().entrySet())if(entity.getUUID().equals(binding.getValue().entityId())){candidate.destroyActorAlias(binding.getKey());if(template.failure()!=null&&protectedActor(template,binding.getKey()))applyFailure(level.getServer(),data,candidate,template,template.failure().onProtectedActorDeath(),"protected actor "+binding.getKey()+" died",binding.getKey(),level.getGameTime());affected.add(candidate.id());}}
        CompoundTag persistent=entity.getPersistentData();if(persistent.hasUUID(OWNER))data.encounter(persistent.getUUID(OWNER)).ifPresent(encounter->{encounter.defeated(entity.getUUID());encounter.defeatedMember(persistent.getString(MEMBER_ID));affected.add(encounter.id());});
        if(persistent.hasUUID(ALLY_OWNER))data.encounter(persistent.getUUID(ALLY_OWNER)).ifPresent(encounter->{EncounterInstance.AllyIdentity identity=encounter.ally(entity.getUUID());if(identity!=null)EncounterResources.template(level.getServer(),encounter.templateId()).ifPresent(template->{EncounterTemplate.Ally definition=template.allies().stream().filter(value->value.id().equals(identity.definitionId())).findFirst().orElse(null);long deadline=definition!=null&&definition.revivable()?level.getGameTime()+definition.reviveDelayTicks():0L;encounter.replaceAlly(identity.dead(deadline));if(definition!=null&&definition.affectsCompletion())encounter.setAllyCompletionBlocked(true);if(definition!=null&&definition.requiredSurvival()&&encounter.state()==EncounterInstance.EncounterState.ACTIVE){EncounterTemplate.FailurePolicy failure=template.failure();applyFailure(level.getServer(),data,encounter,template,failure==null?EncounterTemplate.FailureAction.FAIL:failure.onProtectedActorDeath(),"required ally "+definition.id()+" was defeated",definition.actorAlias(),level.getGameTime());}});affected.add(encounter.id());});
        for(UUID id:affected)data.encounter(id).ifPresent(encounter->{if(encounter.state()==EncounterInstance.EncounterState.ACTIVE)EncounterResources.template(level.getServer(),encounter.templateId()).ifPresent(template->{evaluatePhases(level.getServer(),data,encounter,template,level.getGameTime());evaluateObjectives(level.getServer(),data,encounter,template,level.getGameTime());if(encounter.state()!=EncounterInstance.EncounterState.COMPLETED&&encounter.state()!=EncounterInstance.EncounterState.FAILED){updateBossBar(level.getServer(),encounter,template);updateMobBossBars(level.getServer(),encounter);}});if(encounter.state()==EncounterInstance.EncounterState.ACTIVE||encounter.state()==EncounterInstance.EncounterState.COMPLETED)EncounterResources.template(level.getServer(),encounter.templateId()).ifPresent(template->grantEligibleRewards(level.getServer(),data,encounter,template));if(encounter.state()==EncounterInstance.EncounterState.COMPLETED||encounter.state()==EncounterInstance.EncounterState.FAILED)hideBossBars(encounter);});data.changed();
    }
    public static void onEntityJoin(Entity entity){CompoundTag persistent=entity.getPersistentData();if(!(entity.level() instanceof ServerLevel level))return;SceneSavedData data=SceneSavedData.get(level);if(persistent.hasUUID(ALLY_OWNER)){EncounterInstance encounter=data.encounter(persistent.getUUID(ALLY_OWNER)).orElse(null);if(encounter==null)return;EncounterInstance.AllyIdentity ally=encounter.ally(entity.getUUID());if(encounter.state()==EncounterInstance.EncounterState.RELEASED||encounter.state()==EncounterInstance.EncounterState.CLEANED&&ally!=null&&ally.cleanupPolicy()==EncounterTemplate.AllyCleanupPolicy.PRESERVE)releaseAlly(entity,ally);else if(encounter.state()==EncounterInstance.EncounterState.CLEANED)entity.discard();return;}if(!persistent.hasUUID(OWNER))return;EncounterInstance encounter=data.encounter(persistent.getUUID(OWNER)).orElse(null);if(encounter==null)return;if(!encounter.spawned().contains(entity.getUUID())&&persistent.getInt("VillagerRetaliationSpawnGeneration")<encounter.spawnGeneration()){entity.discard();return;}if(encounter.state()==EncounterInstance.EncounterState.RELEASED)release(entity);else if(encounter.state()==EncounterInstance.EncounterState.CLEANED)entity.discard();else if(encounter.state()==EncounterInstance.EncounterState.PREPARED||encounter.state()==EncounterInstance.EncounterState.SPAWNING||encounter.state()==EncounterInstance.EncounterState.ACTIVE)updateMobBossBars(level.getServer(),encounter);else hideMobBossBar(entity.getUUID());}
    public static boolean shouldCancelFriendlyDamage(LivingEntity target,Entity attacker,Entity direct){if(!(target.level() instanceof ServerLevel level))return false;Entity source=attacker!=null?attacker:direct;CompoundTag targetData=target.getPersistentData();CompoundTag sourceData=source==null?null:source.getPersistentData();if(targetData.hasUUID(ALLY_OWNER)){UUID encounterId=targetData.getUUID(ALLY_OWNER);if(sourceData!=null&&sourceData.hasUUID(ALLY_OWNER)&&encounterId.equals(sourceData.getUUID(ALLY_OWNER)))return true;if(source instanceof net.minecraft.world.entity.player.Player player){EncounterInstance encounter=SceneSavedData.get(level).encounter(encounterId).orElse(null);return encounter!=null&&encounter.participants().contains(player.getUUID());}}if(sourceData!=null&&sourceData.hasUUID(ALLY_OWNER)&&target instanceof net.minecraft.world.entity.player.Player player){EncounterInstance encounter=SceneSavedData.get(level).encounter(sourceData.getUUID(ALLY_OWNER)).orElse(null);return encounter!=null&&encounter.participants().contains(player.getUUID());}return false;}

    private static SpawnResult spawn(ServerLevel level,EncounterTemplate.Member member,EncounterInstance encounter,EncounterTemplate template,int index,EncounterInstance.ResolvedSpawnPoint spawnPoint){
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(member.entityType());
        if(spawnPoint!=null&&!level.hasChunkAt(spawnPoint.position()))return new SpawnResult(null,"selected spawn point chunk is unloaded",true);
        boolean loadedCandidate=false;
        for(int attempt=0;attempt<template.placementAttempts();attempt++){
            BlockPos horizontal=horizontalPosition(encounter,template,index,attempt,spawnPoint);if(!level.hasChunkAt(horizontal))continue;loadedCandidate=true;
            int y=spawnPoint!=null?spawnPoint.position().getY():template.spawnMode()==EncounterTemplate.SpawnMode.FIXED?encounter.anchor().getY():level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,horizontal.getX(),horizontal.getZ());BlockPos pos=new BlockPos(horizontal.getX(),y,horizontal.getZ());
            Entity entity=type.create(level);if(entity==null)continue;entity.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
            if(!level.noCollision(entity)){entity.discard();continue;}
            // Direct EntityType#create skips vanilla mob initialization (including a pillager's crossbow).
            if(entity instanceof Mob mob)mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);
            String optionError=applyMobOptions(entity,member);if(!optionError.isBlank()){entity.discard();return new SpawnResult(null,optionError,false);}
            applyEquipment(level,entity,member);entity.getPersistentData().putUUID(OWNER,encounter.id());entity.getPersistentData().putUUID(SCENE,encounter.sceneId());entity.getPersistentData().putInt("VillagerRetaliationSpawnGeneration",encounter.spawnGeneration());entity.getPersistentData().putInt(SPAWN_INDEX,index);entity.getPersistentData().putInt(SPAWN_SEQUENCE,encounter.spawnPointSequence());if(spawnPoint!=null)entity.getPersistentData().putString(SPAWN_POINT,spawnPoint.id());if(!member.id().isBlank())entity.getPersistentData().putString(MEMBER_ID,member.id());
            if(level.addFreshEntity(entity))return new SpawnResult(entity,"",false);
        }
        return loadedCandidate?new SpawnResult(null,"",false):new SpawnResult(null,"selected spawn point chunk is unloaded",true);
    }

    private static BlockPos horizontalPosition(EncounterInstance encounter,EncounterTemplate template,int index,int attempt,EncounterInstance.ResolvedSpawnPoint spawnPoint){
        if(spawnPoint!=null){if(attempt==0)return spawnPoint.position();int radius=Math.min(2,template.spawnRadius());int width=radius*2+1;int seed=Math.floorMod(encounter.id().hashCode()*31+index*17+attempt*13,width*width);return spawnPoint.position().offset(seed%width-radius,0,(seed/width)%width-radius);}
        int radius=switch(template.spawnMode()){case NEAR_PLAYER->Math.min(3,template.spawnRadius());case FIXED->Math.min(2,template.spawnRadius());case GROUP,RAID_WAVES->template.spawnRadius();};
        int width=radius*2+1;int seed=Math.floorMod(encounter.id().hashCode()*31+index*17+attempt*13,width*width);int dx=seed%width-radius;int dz=(seed/width)%width-radius;
        if(template.spawnMode()==EncounterTemplate.SpawnMode.RAID_WAVES&&radius>2){int inner=Math.max(2,radius/2);if(Math.abs(dx)<inner&&Math.abs(dz)<inner){dx=dx<0?-inner:inner;dz=dz<0?-inner:inner;}}
        return encounter.anchor().offset(dx,0,dz);
    }

    private static PointSelection selectSpawnPoint(MinecraftServer server,EncounterInstance encounter,EncounterTemplate template,int spawnIndex,int groupIndex){
        if(template.spawnPoints().isEmpty())return new PointSelection(null,"",false);
        List<EncounterInstance.ResolvedSpawnPoint> points=new ArrayList<>();
        for(EncounterTemplate.SpawnPoint definition:template.spawnPoints()){
            EncounterInstance.ResolvedSpawnPoint point=encounter.resolvedSpawnPoints().get(definition.id());
            if(point==null)return new PointSelection(null,"persisted encounter is missing authored spawn point "+definition.id(),false);
            points.add(point);
        }
        String saved=encounter.selectedSpawnPoints().get(spawnIndex);
        if(saved!=null){for(EncounterInstance.ResolvedSpawnPoint point:points)if(point.id().equals(saved))return new PointSelection(point,"",false);return new PointSelection(null,"persisted spawn "+spawnIndex+" references unknown point "+saved,false);}
        int selected;
        boolean advance=false;
        switch(template.spawnSelection()){
            case RANDOM->selected=Math.floorMod(encounter.id().hashCode()*31+spawnIndex*17,points.size());
            case SEQUENTIAL->{selected=Math.floorMod(encounter.spawnPointSequence(),points.size());advance=true;}
            case WEIGHTED->{long total=0;for(EncounterInstance.ResolvedSpawnPoint point:points)total+=point.weight();long roll=Math.floorMod(((long)encounter.id().hashCode()<<32)^spawnIndex*0x9E3779B97F4A7C15L,total);selected=0;for(int i=0;i<points.size();i++){roll-=points.get(i).weight();if(roll<0){selected=i;break;}}}
            case ONE_GROUP_PER_POINT->selected=Math.floorMod(groupIndex,points.size());
            case NEAREST_PLAYER,FARTHEST_PLAYER->{
                List<net.minecraft.server.level.ServerPlayer> players=new ArrayList<>();for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)players.add(player);}if(players.isEmpty())return new PointSelection(null,"waiting for an online participant to select an authored spawn point",true);
                selected=-1;double best=template.spawnSelection()==EncounterTemplate.SpawnSelectionMode.NEAREST_PLAYER?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY;
                for(int i=0;i<points.size();i++){EncounterInstance.ResolvedSpawnPoint point=points.get(i);double score=Double.POSITIVE_INFINITY;for(var player:players)if(player.level().dimension().location().equals(point.dimension()))score=Math.min(score,player.distanceToSqr(point.position().getX()+.5D,point.position().getY(),point.position().getZ()+.5D));if(!Double.isFinite(score))continue;boolean better=template.spawnSelection()==EncounterTemplate.SpawnSelectionMode.NEAREST_PLAYER?score<best:score>best;if(better){best=score;selected=i;}}
                if(selected<0)return new PointSelection(null,"waiting for an online participant in the authored spawn-point dimension",true);
            }
            default->throw new IllegalStateException("Unhandled spawn selection "+template.spawnSelection());
        }
        EncounterInstance.ResolvedSpawnPoint point=points.get(selected);encounter.selectSpawnPoint(spawnIndex,point.id(),advance);return new PointSelection(point,"",false);
    }

    private static Result evaluatePhases(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template,long gameTime){
        if(template.phases().isEmpty())return Result.active();SceneInstance scene=data.get(encounter.sceneId()).orElse(null);if(scene==null){encounter.fail("encounter phases cannot find their owning scene");data.changed();return Result.failed(encounter.diagnostic());}
        for(EncounterTemplate.Phase phase:template.phases()){
            int count=encounter.phaseFireCounts().getOrDefault(phase.id(),0);if(count>0){String pending=executePhaseRun(server,data,encounter,scene,phase,count,gameTime);if(!pending.isBlank()){encounter.fail(pending);data.changed();return Result.failed(pending);}}
            if(count>0&&!phase.repeatable()||count>=phase.maxFires()||gameTime<encounter.phaseNextAt().getOrDefault(phase.id(),0L)||!phaseMatches(encounter,template,phase,gameTime))continue;
            int ordinal=encounter.startPhaseFire(phase.id(),phase.repeatable()?gameTime+phase.repeatIntervalTicks():0L);data.changed();String error=executePhaseRun(server,data,encounter,scene,phase,ordinal,gameTime);if(!error.isBlank()){encounter.fail(error);data.changed();return Result.failed(error);}
        }
        return Result.active();
    }
    private static Result evaluateObjectives(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template,long gameTime){
        EncounterTemplate.ObjectiveComposition composition=template.completionObjectives();if(composition==null)return Result.active();encounter.enableCustomCompletion();SceneInstance scene=data.get(encounter.sceneId()).orElse(null);if(scene==null){encounter.fail("encounter objectives cannot find their owning scene");data.changed();return Result.failed(encounter.diagnostic());}
        for(EncounterTemplate.Objective objective:composition.objectives()){
            if(encounter.completedObjectives().contains(objective.id())||encounter.failedObjectives().contains(objective.id()))continue;
            boolean complete=false,failed=false;
            switch(objective.type()){
                case ALL_DEFEATED->complete=encounter.defeated().size()>=encounter.expectedCount();
                case ALL_GONE->complete=encounter.defeated().size()+encounter.missing().size()>=encounter.expectedCount();
                case SURVIVE_DURATION->complete=elapsed(encounter.startedAt(),gameTime,objective.durationTicks());
                case PROTECT_ACTOR->{failed=destroyed(encounter,scene,objective.actorAlias());complete=!failed&&elapsed(encounter.startedAt(),gameTime,objective.durationTicks());}
                case PREVENT_ENTRY->{EncounterInstance.ResolvedSpawnPoint point=encounter.resolvedSpawnPoints().get(objective.pointId());failed=point!=null&&ownedEnemyInside(server,encounter,point,objective.radius(),objective.verticalRadius());complete=!failed&&elapsed(encounter.startedAt(),gameTime,objective.durationTicks());}
                case ESCORT_ACTOR->{failed=destroyed(encounter,scene,objective.actorAlias());Entity actor=actor(server,scene,objective.actorAlias());EncounterInstance.ResolvedSpawnPoint point=encounter.resolvedSpawnPoints().get(objective.pointId());complete=!failed&&actor!=null&&point!=null&&inside(actor,point,objective.radius(),objective.verticalRadius());}
                case DESTROY_TARGETS->{complete=true;for(String alias:objective.actorAliases())if(!destroyed(encounter,scene,alias)){complete=false;break;}}
                case DEFEAT_LEADER->complete=encounter.defeatedMemberIds().contains(objective.memberId());
                case RETRIEVE_ITEM->{int count=0;var item=BuiltInRegistries.ITEM.get(objective.item());for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)count+=player.getInventory().countItem(item);if(count>=objective.count())break;}complete=count>=objective.count();}
                case HOLD_AREAS->{boolean held=true;for(String pointId:objective.pointIds()){EncounterInstance.ResolvedSpawnPoint point=encounter.resolvedSpawnPoints().get(pointId);boolean occupied=false;if(point!=null)for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null&&inside(player,point,objective.radius(),objective.verticalRadius())){occupied=true;break;}}if(!occupied){held=false;break;}}if(held){encounter.setObjectiveSince(objective.id(),gameTime);complete=elapsed(encounter.objectiveSince().getOrDefault(objective.id(),gameTime),gameTime,objective.durationTicks());}else encounter.clearObjectiveSince(objective.id());}
            }
            if(failed)encounter.failObjective(objective.id());else if(complete)encounter.completeObjective(objective.id());
        }
        Set<String> currentIds=composition.objectives().stream().map(EncounterTemplate.Objective::id).collect(java.util.stream.Collectors.toSet());int total=currentIds.size(),complete=(int)encounter.completedObjectives().stream().filter(currentIds::contains).count(),failed=(int)encounter.failedObjectives().stream().filter(currentIds::contains).count();
        if(composition.mode()==EncounterTemplate.ObjectiveMode.ALL&&failed>0||composition.mode()==EncounterTemplate.ObjectiveMode.ANY&&failed>=total){encounter.fail("completion objectives failed: "+encounter.failedObjectives());hideBossBars(encounter);data.changed();return Result.failed(encounter.diagnostic());}
        if(!encounter.allyCompletionBlocked()&&(composition.mode()==EncounterTemplate.ObjectiveMode.ALL&&complete>=total||composition.mode()==EncounterTemplate.ObjectiveMode.ANY&&complete>0)){encounter.complete();hideBossBars(encounter);data.changed();return Result.completed();}
        data.changed();return Result.active();
    }
    private static boolean elapsed(long since,long now,long duration){return since>=0&&now-since>=duration;}
    private static boolean destroyed(EncounterInstance encounter,SceneInstance scene,String alias){if(encounter.destroyedActorAliases().contains(alias))return true;var binding=scene.actorBindings().get(alias);if(binding!=null&&binding.state()==com.jvn.villagerretaliation.scene.actor.SceneActorBinding.BindingState.DEAD){encounter.destroyActorAlias(alias);return true;}return false;}
    private static Entity actor(MinecraftServer server,SceneInstance scene,String alias){var binding=scene.actorBindings().get(alias);return binding==null||binding.entityId()==null||binding.state()==com.jvn.villagerretaliation.scene.actor.SceneActorBinding.BindingState.DOWNED?null:find(server,binding.entityId());}
    private static boolean ownedEnemyInside(MinecraftServer server,EncounterInstance encounter,EncounterInstance.ResolvedSpawnPoint point,int radius,int verticalRadius){for(UUID id:encounter.spawned()){if(encounter.defeated().contains(id)||encounter.missing().contains(id))continue;Entity entity=find(server,id);if(entity!=null&&inside(entity,point,radius,verticalRadius))return true;}return false;}
    private static boolean inside(Entity entity,EncounterInstance.ResolvedSpawnPoint point,int radius,int verticalRadius){if(!entity.level().dimension().location().equals(point.dimension()))return false;double dx=entity.getX()-(point.position().getX()+.5D),dz=entity.getZ()-(point.position().getZ()+.5D);return Math.abs(entity.getY()-point.position().getY())<=verticalRadius&&dx*dx+dz*dz<=(double)radius*radius;}
    private static boolean phaseMatches(EncounterInstance encounter,EncounterTemplate template,EncounterTemplate.Phase phase,long gameTime){EncounterTemplate.PhaseTrigger trigger=phase.trigger();return switch(trigger.type()){case WAVE_STARTED->encounter.startedWaves().contains(trigger.waveId());case WAVE_COMPLETED->{int index=-1;for(int i=0;i<template.waveCount();i++)if(template.wave(i).id().equals(trigger.waveId())){index=i;break;}yield index>=0&&encounter.defeated().size()>=template.waveStart(index,encounter.partySize())+template.scaledCount(template.wave(index),encounter.partySize());}case REMAINING_PERCENTAGE->(long)Math.max(0,encounter.expectedCount()-encounter.defeated().size())*100L<=(long)encounter.expectedCount()*trigger.percentage();case ELAPSED_TIME->encounter.startedAt()>=0&&gameTime-encounter.startedAt()>=trigger.ticks();case ELITE_DEFEATED->encounter.defeatedMemberIds().contains(trigger.memberId());};}
    private static String executePhaseRun(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,SceneInstance scene,EncounterTemplate.Phase phase,int ordinal,long gameTime){String operation=scene.id()+"/encounter/"+encounter.id()+"/phase/"+phase.id()+"/"+ordinal;SceneOperationReceipt phaseReceipt=scene.prepareReceipt(operation,SceneOperationReceipt.Kind.ENCOUNTER_PHASE,gameTime);if(phaseReceipt.state()==SceneOperationReceipt.ReceiptState.COMPLETED)return "";for(EncounterTemplate.PhaseAction action:phase.actions()){String actionOperation=operation+"/action/"+action.id();SceneOperationReceipt receipt=scene.prepareReceipt(actionOperation,phaseActionKind(action),gameTime);if(receipt.state()==SceneOperationReceipt.ReceiptState.COMPLETED)continue;String error=executePhaseAction(server,data,encounter,scene,action,receipt,gameTime);if(!error.isBlank())return "phase "+phase.id()+" action "+action.id()+" failed: "+error;}phaseReceipt.applied(gameTime,"phase="+phase.id()+" ordinal="+ordinal);phaseReceipt.completed(gameTime,"phase="+phase.id()+" ordinal="+ordinal);data.changed();return "";}
    private static SceneOperationReceipt.Kind phaseActionKind(EncounterTemplate.PhaseAction action){return switch(action.type()){case NOTIFICATION,DIALOGUE->SceneOperationReceipt.Kind.DIALOGUE_DELIVERY;case FACT->SceneOperationReceipt.Kind.FACT_CHANGE;case TRANSITION->SceneOperationReceipt.Kind.SCENE_TRANSITION;};}
    private static String executePhaseAction(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,SceneInstance scene,EncounterTemplate.PhaseAction action,SceneOperationReceipt receipt,long gameTime){
        switch(action.type()){
            case NOTIFICATION,DIALOGUE->{receipt.applied(gameTime,"participants="+encounter.participants().size());receipt.completed(gameTime,"reserved before participant delivery");data.changed();Component message=Component.literal(action.text());for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)player.sendSystemMessage(message);}}
            case FACT->{ServerLevel level=level(server,encounter);if(level==null)return "encounter dimension is unavailable";VillagerQuestFacts facts=VillagerQuestFacts.get(level);List<QuestScopeKey> scopes=new ArrayList<>();switch(action.scope()){case WORLD->scopes.add(QuestScopeKey.WORLD);case PLAYER->{for(UUID participant:encounter.participants())scopes.add(QuestScopeKey.player(participant));}case QUEST->{if(scene.owningQuestId()==null)return "quest fact scope has no linked quest";for(UUID participant:encounter.participants())scopes.add(QuestScopeKey.quest(participant,scene.owningQuestId()));}}if(scopes.isEmpty())return "fact action has no participant scope";for(QuestScopeKey scope:scopes)if(action.tag()!=null)facts.setTag(scope,action.tag());else facts.setVariable(scope,action.key(),action.value());receipt.applied(gameTime,"scopes="+scopes.size());receipt.completed(gameTime,"idempotent fact set");data.changed();}
            case TRANSITION->{var definition=SceneResources.scene(server,scene.sceneId()).orElse(null);if(definition==null||!definition.steps().containsKey(action.target()))return "target scene step "+action.target()+" is unavailable";var record=scene.stepRecords().get(scene.currentStep());if(record==null)return "current scene step has no durable record";if(!record.chosenTransition().isBlank()&&!record.chosenTransition().equals(action.target()))return "current scene step already chose transition "+record.chosenTransition();record.chooseTransition(action.target());receipt.applied(gameTime,"target="+action.target());receipt.completed(gameTime,"target="+action.target());data.changed();}
        }return "";
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
        if(template.guidance()!=null||encounter.locationNotified()||template.spawnMode()!=EncounterTemplate.SpawnMode.FIXED)return;
        String message=template.locationMessage().isBlank()?"Go to the encounter at {x}, {y}, {z}.":template.locationMessage();
        message=message.replace("{x}",Integer.toString(encounter.anchor().getX())).replace("{y}",Integer.toString(encounter.anchor().getY())).replace("{z}",Integer.toString(encounter.anchor().getZ())).replace("{dimension}",encounter.anchorDimension().toString());
        Component component=Component.literal(message);for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)player.sendSystemMessage(component);}
        encounter.markLocationNotified();data.changed();
    }

    private static void updateGuidance(MinecraftServer server,SceneSavedData data,EncounterInstance encounter,EncounterTemplate template,long gameTime){
        EncounterTemplate.Guidance guidance=template.guidance();if(guidance==null)return;
        for(UUID id:encounter.participants()){
            var player=server.getPlayerList().getPlayer(id);if(player==null)continue;boolean sameDimension=player.serverLevel().dimension().location().equals(encounter.anchorDimension());double distance=sameDimension?Math.sqrt(player.blockPosition().distSqr(encounter.anchor())):Double.POSITIVE_INFINITY;
            boolean wasNotified=encounter.guidanceNotified().contains(id),newlyDiscovered=sameDimension&&distance<=guidance.discoveryRadius()&&!encounter.guidanceDiscovered().contains(id);if(newlyDiscovered){encounter.discoverGuidance(id);data.changed();}
            if(!wasNotified||newlyDiscovered&&guidance.exactCoordinates()==EncounterTemplate.ExactCoordinates.AFTER_DISCOVERY){if(!wasNotified)encounter.notifyGuidance(id);data.changed();String message=guidance.coordinateMessage().isBlank()?"Encounter destination: {location}.":guidance.coordinateMessage();player.sendSystemMessage(Component.literal(guidanceText(message,player,encounter,guidance,distance)));}
            if(sameDimension&&distance<=guidance.arrivalRadius()&&!encounter.guidanceArrived().contains(id)){encounter.arriveGuidance(id);data.changed();if(!guidance.arrivalMessage().isBlank())player.sendSystemMessage(Component.literal(guidanceText(guidance.arrivalMessage(),player,encounter,guidance,distance)));}
            if(gameTime<encounter.guidanceNextAt().getOrDefault(id,0L))continue;encounter.scheduleGuidance(id,gameTime+guidance.updateIntervalTicks());data.changed();
            String direction=sameDimension?direction(player.getX(),player.getZ(),encounter.anchor()):encounter.anchorDimension().toString();
            if(guidance.hudMarker()){StringBuilder marker=new StringBuilder("Encounter");if(guidance.compassTarget())marker.append(" ").append(direction);if(guidance.distanceTracker()&&sameDimension)marker.append(" · ").append(Math.round(distance)).append("m");if(!sameDimension)marker.append(" · ").append(encounter.anchorDimension());player.displayClientMessage(Component.literal(marker.toString()),true);}
            if(guidance.directionalParticles()&&sameDimension&&!encounter.guidanceArrived().contains(id)){double dx=encounter.anchor().getX()+.5-player.getX(),dz=encounter.anchor().getZ()+.5-player.getZ(),length=Math.max(1D,Math.sqrt(dx*dx+dz*dz));for(int step=1;step<=3;step++){double scale=step*1.25D/length;player.serverLevel().sendParticles(player,ParticleTypes.END_ROD,true,player.getX()+dx*scale,player.getY()+.25,player.getZ()+dz*scale,1,0,0,0,0);}}
        }
    }
    private static String guidanceText(String template,net.minecraft.server.level.ServerPlayer player,EncounterInstance encounter,EncounterTemplate.Guidance guidance,double distance){boolean exact=guidance.exactCoordinates()==EncounterTemplate.ExactCoordinates.ALWAYS||guidance.exactCoordinates()==EncounterTemplate.ExactCoordinates.AFTER_DISCOVERY&&encounter.guidanceDiscovered().contains(player.getUUID());String coordinates=exact?encounter.anchor().getX()+", "+encounter.anchor().getY()+", "+encounter.anchor().getZ():"undiscovered";String direction=Double.isFinite(distance)?direction(player.getX(),player.getZ(),encounter.anchor()):encounter.anchorDimension().toString();return template.replace("{location}",exact?coordinates+" in "+encounter.anchorDimension():encounter.anchorDimension().toString()).replace("{coordinates}",coordinates).replace("{x}",exact?Integer.toString(encounter.anchor().getX()):"?").replace("{y}",exact?Integer.toString(encounter.anchor().getY()):"?").replace("{z}",exact?Integer.toString(encounter.anchor().getZ()):"?").replace("{dimension}",encounter.anchorDimension().toString()).replace("{distance}",Double.isFinite(distance)?Long.toString(Math.round(distance)):"?").replace("{direction}",direction);}
    private static String direction(double x,double z,BlockPos target){double angle=Math.atan2(target.getX()+.5-x,-(target.getZ()+.5-z)),slice=Math.PI/4D;String[] names={"N","NE","E","SE","S","SW","W","NW"};return names[Math.floorMod((int)Math.round(angle/slice),8)];}
    public static Map<String,String> guidanceReplacements(net.minecraft.server.level.ServerPlayer player,EncounterInstance encounter,EncounterTemplate template){Map<String,String> values=new java.util.LinkedHashMap<>();boolean participant=player!=null&&encounter.participants().contains(player.getUUID());EncounterTemplate.Guidance guidance=template==null||!participant?null:template.guidance();boolean same=guidance!=null&&player.serverLevel().dimension().location().equals(encounter.anchorDimension()),discovered=guidance!=null&&encounter.guidanceDiscovered().contains(player.getUUID()),arrived=guidance!=null&&encounter.guidanceArrived().contains(player.getUUID()),exact=guidance!=null&&(guidance.exactCoordinates()==EncounterTemplate.ExactCoordinates.ALWAYS||guidance.exactCoordinates()==EncounterTemplate.ExactCoordinates.AFTER_DISCOVERY&&discovered);double distance=same?Math.sqrt(player.blockPosition().distSqr(encounter.anchor())):Double.POSITIVE_INFINITY;values.put("encounter_distance",guidance!=null&&guidance.distanceTracker()&&Double.isFinite(distance)?Long.toString(Math.round(distance)):"");values.put("encounter_direction",guidance!=null&&guidance.compassTarget()&&same?direction(player.getX(),player.getZ(),encounter.anchor()):"");values.put("encounter_coordinates",exact?encounter.anchor().getX()+", "+encounter.anchor().getY()+", "+encounter.anchor().getZ():"");values.put("encounter_dimension",guidance==null?"":encounter.anchorDimension().toString());values.put("encounter_discovered",Boolean.toString(discovered));values.put("encounter_arrived",Boolean.toString(arrived));return Map.copyOf(values);}
    private static void removeGuidance(MinecraftServer server,SceneSavedData data,EncounterInstance encounter){for(UUID id:encounter.participants())if(!encounter.guidanceCleared().contains(id)){var player=server.getPlayerList().getPlayer(id);if(player!=null)player.displayClientMessage(Component.empty(),true);encounter.clearGuidance(id);data.changed();}}

    private static void updateBossBar(MinecraftServer server,EncounterInstance encounter,EncounterTemplate template){
        if(template.spawnMode()!=EncounterTemplate.SpawnMode.RAID_WAVES||!template.bossBar()){hideBossBar(encounter.id());return;}
        int wave=Math.max(0,Math.min(template.waveCount()-1,encounter.currentWaveIndex()));EncounterTemplate.Wave definition=template.wave(wave);
        ServerBossEvent bar=BOSS_BARS.computeIfAbsent(encounter.id(),ignored->new ServerBossEvent(Component.translatable("villagerretaliation.encounter.raid"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.NOTCHED_10));
        String title=definition.bossBarTitle().isBlank()?"Raid - Wave "+(wave+1)+"/"+template.waveCount():definition.bossBarTitle();bar.setName(Component.literal(title));
        bar.setProgress(Math.max(0.0F,Math.min(1.0F,(encounter.expectedCount()-encounter.defeated().size())/(float)Math.max(1,encounter.expectedCount()))));
        for(var player:new ArrayList<>(bar.getPlayers()))if(!encounter.participants().contains(player.getUUID()))bar.removePlayer(player);
        for(UUID participant:encounter.participants()){var player=server.getPlayerList().getPlayer(participant);if(player!=null)bar.addPlayer(player);}
        bar.setVisible(true);
    }

    public static void hideBossBar(UUID encounterId){ServerBossEvent bar=BOSS_BARS.remove(encounterId);if(bar!=null)bar.removeAllPlayers();}
    public static void clearRuntimeState() {
        BOSS_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        MOB_BOSS_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BOSS_BARS.clear();
        MOB_BOSS_BARS.clear();
    }

    private static List<SpawnMember> desiredMembers(EncounterTemplate.Wave wave,EncounterTemplate template,int partySize){List<SpawnMember> values=new ArrayList<>();int group=0;for(var member:wave.members()){for(int i=0;i<member.count();i++)values.add(new SpawnMember(member,group));group++;}int extra=template.scaledCount(wave,partySize)-values.size();for(int i=0;i<extra;i++)values.add(new SpawnMember(wave.members().getFirst(),0));return values;}
    private static void recoverSpawn(EncounterInstance encounter,Entity entity){CompoundTag tag=entity.getPersistentData();if(!encounter.spawned().contains(entity.getUUID()))encounter.addSpawn(entity.getUUID());if(tag.contains(SPAWN_POINT,Tag.TAG_STRING))encounter.restoreSpawnPoint(tag.getInt(SPAWN_INDEX),tag.getString(SPAWN_POINT),tag.getInt(SPAWN_SEQUENCE));}
    private static ServerLevel level(MinecraftServer server,EncounterInstance e){return server.getLevel(ResourceKey.create(Registries.DIMENSION,e.anchorDimension()));}private static Entity find(MinecraftServer server,UUID id){for(ServerLevel level:server.getAllLevels()){Entity e=level.getEntity(id);if(e!=null)return e;}return null;}private static boolean ownedBy(Entity entity,UUID id){return entity.getPersistentData().hasUUID(OWNER)&&id.equals(entity.getPersistentData().getUUID(OWNER));}private static void release(Entity entity){hideMobBossBar(entity.getUUID());entity.getPersistentData().remove(OWNER);entity.getPersistentData().remove(SCENE);entity.getPersistentData().remove("VillagerRetaliationSpawnGeneration");entity.getPersistentData().remove(SPAWN_INDEX);entity.getPersistentData().remove(SPAWN_POINT);entity.getPersistentData().remove(SPAWN_SEQUENCE);entity.getPersistentData().remove(MEMBER_ID);entity.getPersistentData().remove(BOSS);entity.getPersistentData().remove(BOSS_COLOR);entity.getPersistentData().remove(BOSS_OVERLAY);}private static void releaseAlly(Entity entity,EncounterInstance.AllyIdentity identity){if(identity.invulnerabilityApplied())entity.setInvulnerable(identity.originalInvulnerable());entity.getPersistentData().remove(ALLY_OWNER);entity.getPersistentData().remove(ALLY_KEY);if(entity instanceof Mob mob)mob.setTarget(null);}
    private record SpawnMember(EncounterTemplate.Member member,int groupIndex){}
    private record SpawnResult(Entity entity,String diagnostic,boolean waiting){}
    private record PointSelection(EncounterInstance.ResolvedSpawnPoint point,String diagnostic,boolean waiting){}
    public record Result(Status status,String diagnostic){public static Result active(){return new Result(Status.ACTIVE,"");}public static Result waiting(String m){return new Result(Status.WAITING,m);}public static Result completed(){return new Result(Status.COMPLETED,"");}public static Result failed(String m){return new Result(Status.FAILED,m);}}public enum Status{ACTIVE,WAITING,COMPLETED,FAILED}
}
