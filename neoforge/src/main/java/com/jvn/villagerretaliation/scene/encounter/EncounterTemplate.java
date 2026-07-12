package com.jvn.villagerretaliation.scene.encounter;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public record EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
        int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
        RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition,
        SpawnMode spawnMode,int waveCount,int waveIntervalTicks,WaveTrigger waveTrigger,boolean bossBar,String locationMessage,Area area) {
    public EncounterTemplate {
        if(id==null||controller==null||members==null||members.isEmpty())throw new IllegalArgumentException("encounter needs id, controller, and members");
        members=List.copyOf(members);version=Math.max(1,version);extraPerAdditionalPlayer=Math.max(0,extraPerAdditionalPlayer);
        maxPartySize=Math.max(1,maxPartySize);placementAttempts=Math.max(1,Math.min(64,placementAttempts));
        spawnRadius=Math.max(1,Math.min(32,spawnRadius));respawnPolicy=respawnPolicy==null?RespawnPolicy.NEVER:respawnPolicy;
        cleanupPolicy=cleanupPolicy==null?CleanupPolicy.REMOVE_SURVIVORS:cleanupPolicy;
        completionCondition=completionCondition==null?CompletionCondition.ALL_DEFEATED:completionCondition;
        spawnMode=spawnMode==null?SpawnMode.GROUP:spawnMode;waveCount=spawnMode==SpawnMode.RAID_WAVES?Math.max(1,Math.min(32,waveCount)):1;
        waveIntervalTicks=Math.max(0,waveIntervalTicks);waveTrigger=waveTrigger==null?WaveTrigger.ALL_DEFEATED:waveTrigger;
        locationMessage=locationMessage==null?"":locationMessage;
    }
    /** Source-compatible constructor for extension code targeting encounter/v1 before spawn modes were added. */
    public EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
            int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
            RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition) {
        this(id,version,controller,members,extraPerAdditionalPlayer,maxPartySize,placementAttempts,spawnRadius,
                respawnPolicy,cleanupPolicy,completionCondition,SpawnMode.GROUP,1,0,WaveTrigger.ALL_DEFEATED,true,"",null);
    }
    /** Source-compatible constructor for encounter/v1 spawn-mode extension code written before encounter areas. */
    public EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
            int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
            RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition,
            SpawnMode spawnMode,int waveCount,int waveIntervalTicks,WaveTrigger waveTrigger,boolean bossBar,String locationMessage) {
        this(id,version,controller,members,extraPerAdditionalPlayer,maxPartySize,placementAttempts,spawnRadius,
                respawnPolicy,cleanupPolicy,completionCondition,spawnMode,waveCount,waveIntervalTicks,waveTrigger,bossBar,locationMessage,null);
    }
    public int scaledCount(int partySize){int base=members.stream().mapToInt(Member::count).sum();return base+Math.max(0,Math.min(maxPartySize,partySize)-1)*extraPerAdditionalPlayer;}
    public int totalCount(int partySize){return scaledCount(partySize)*waveCount;}
    public record Member(ResourceLocation entityType,int count,Map<EquipmentSlot,Gear> equipment){
        public Member{if(entityType==null)throw new IllegalArgumentException("encounter member entity type is required");count=Math.max(1,Math.min(64,count));equipment=equipment==null?Map.of():Map.copyOf(equipment);}
        public Member(ResourceLocation entityType,int count){this(entityType,count,Map.of());}
    }
    public record Gear(ResourceLocation item,int count,Map<ResourceLocation,Integer> enchantments,float dropChance){
        public Gear{if(item==null)throw new IllegalArgumentException("equipment item is required");count=Math.max(1,Math.min(99,count));enchantments=enchantments==null?Map.of():Map.copyOf(enchantments);dropChance=Math.max(0.0F,Math.min(1.0F,dropChance));}
    }
    public record Area(int radius,int verticalRadius,LeaveBehavior leaveBehavior,int leaveTimeoutTicks,
            MobBehavior mobBehavior,int mobTimeoutTicks){
        public Area{
            if(radius<1||radius>256)throw new IllegalArgumentException("area.radius must be between 1 and 256");
            if(verticalRadius<1||verticalRadius>128)throw new IllegalArgumentException("area.vertical_radius must be between 1 and 128");
            if(leaveTimeoutTicks<1||leaveTimeoutTicks>12000)throw new IllegalArgumentException("area.leave_timeout_ticks must be between 1 and 12000");
            if(mobTimeoutTicks<1||mobTimeoutTicks>12000)throw new IllegalArgumentException("area.mob_timeout_ticks must be between 1 and 12000");
            leaveBehavior=leaveBehavior==null?LeaveBehavior.IGNORE:leaveBehavior;
            mobBehavior=mobBehavior==null?MobBehavior.IGNORE:mobBehavior;
        }
    }
    public enum LeaveBehavior{IGNORE,WARN,PAUSE,FAIL}
    public enum MobBehavior{IGNORE,RETURN,TELEPORT}
    public enum SpawnMode{GROUP,NEAR_PLAYER,FIXED,RAID_WAVES}
    public enum WaveTrigger{ALL_DEFEATED,TIMER}
    public enum RespawnPolicy{NEVER,MISSING_IF_LOADED,UNTIL_FIRST_DEFEAT}
    public enum CleanupPolicy{REMOVE_SURVIVORS,PRESERVE_IN_WORLD}
    public enum CompletionCondition{ALL_DEFEATED,ALL_GONE}
}
