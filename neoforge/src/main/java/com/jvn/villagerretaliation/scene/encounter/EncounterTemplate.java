package com.jvn.villagerretaliation.scene.encounter;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public record EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
        int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
        RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition,
        SpawnMode spawnMode,int waveCount,int waveIntervalTicks,WaveTrigger waveTrigger,boolean bossBar,String locationMessage,Area area,List<Wave> waves) {
    public EncounterTemplate {
        members=members==null?List.of():List.copyOf(members);waves=waves==null?List.of():List.copyOf(waves);
        if(id==null||controller==null||(members.isEmpty()&&waves.isEmpty()))throw new IllegalArgumentException("encounter needs id, controller, and members or waves");
        if(!members.isEmpty()&&!waves.isEmpty())throw new IllegalArgumentException("encounter members and waves are mutually exclusive");
        if(members.size()>64||waves.size()>32)throw new IllegalArgumentException("encounter composition exceeds its bounded limits");
        version=Math.max(1,version);extraPerAdditionalPlayer=Math.max(0,Math.min(64,extraPerAdditionalPlayer));
        maxPartySize=Math.max(1,Math.min(16,maxPartySize));placementAttempts=Math.max(1,Math.min(64,placementAttempts));
        spawnRadius=Math.max(1,Math.min(32,spawnRadius));respawnPolicy=respawnPolicy==null?RespawnPolicy.NEVER:respawnPolicy;
        cleanupPolicy=cleanupPolicy==null?CleanupPolicy.REMOVE_SURVIVORS:cleanupPolicy;
        completionCondition=completionCondition==null?CompletionCondition.ALL_DEFEATED:completionCondition;
        spawnMode=spawnMode==null?SpawnMode.GROUP:spawnMode;if(!waves.isEmpty()&&spawnMode!=SpawnMode.RAID_WAVES)throw new IllegalArgumentException("explicit waves require spawn_mode raid_waves");
        waveCount=!waves.isEmpty()?waves.size():spawnMode==SpawnMode.RAID_WAVES?Math.max(1,Math.min(32,waveCount)):1;
        waveIntervalTicks=Math.max(0,waveIntervalTicks);waveTrigger=waveTrigger==null?WaveTrigger.ALL_DEFEATED:waveTrigger;
        locationMessage=locationMessage==null?"":locationMessage;
        long maximum=0;if(waves.isEmpty()){long perWave=members.stream().mapToInt(Member::count).sum()+(long)(maxPartySize-1)*extraPerAdditionalPlayer;maximum=perWave*waveCount;}else for(Wave wave:waves)maximum+=wave.members().stream().mapToInt(Member::count).sum()+(long)(maxPartySize-1)*extraPerAdditionalPlayer;if(maximum>4096)throw new IllegalArgumentException("scaled encounter composition exceeds 4096 owned mobs");
    }
    /** Source-compatible constructor for extension code targeting encounter/v1 before spawn modes were added. */
    public EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
            int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
            RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition) {
        this(id,version,controller,members,extraPerAdditionalPlayer,maxPartySize,placementAttempts,spawnRadius,
                respawnPolicy,cleanupPolicy,completionCondition,SpawnMode.GROUP,1,0,WaveTrigger.ALL_DEFEATED,true,"",null,List.of());
    }
    /** Source-compatible constructor for encounter/v1 spawn-mode extension code written before encounter areas. */
    public EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
            int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
            RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition,
            SpawnMode spawnMode,int waveCount,int waveIntervalTicks,WaveTrigger waveTrigger,boolean bossBar,String locationMessage) {
        this(id,version,controller,members,extraPerAdditionalPlayer,maxPartySize,placementAttempts,spawnRadius,
                respawnPolicy,cleanupPolicy,completionCondition,spawnMode,waveCount,waveIntervalTicks,waveTrigger,bossBar,locationMessage,null,List.of());
    }
    /** Source-compatible constructor for encounter/v1 area extension code written before explicit waves. */
    public EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
            int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
            RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition,
            SpawnMode spawnMode,int waveCount,int waveIntervalTicks,WaveTrigger waveTrigger,boolean bossBar,String locationMessage,Area area) {
        this(id,version,controller,members,extraPerAdditionalPlayer,maxPartySize,placementAttempts,spawnRadius,
                respawnPolicy,cleanupPolicy,completionCondition,spawnMode,waveCount,waveIntervalTicks,waveTrigger,bossBar,locationMessage,area,List.of());
    }
    public boolean explicitWaves(){return !waves.isEmpty();}
    public Wave wave(int index){if(explicitWaves())return waves.get(index);return new Wave("repeat_"+(index+1),members,index==0?0:waveIntervalTicks,waveTrigger,"",List.of());}
    public int scaledCount(int partySize){return scaledCount(wave(0),partySize);}
    public int scaledCount(Wave wave,int partySize){int base=wave.members().stream().mapToInt(Member::count).sum();return base+Math.max(0,Math.min(maxPartySize,partySize)-1)*extraPerAdditionalPlayer;}
    public int waveStart(int index,int partySize){int total=0;for(int i=0;i<index;i++)total+=scaledCount(wave(i),partySize);return total;}
    public int totalCount(int partySize){int total=0;for(int i=0;i<waveCount;i++)total+=scaledCount(wave(i),partySize);return total;}
    public record Member(ResourceLocation entityType,int count,Map<EquipmentSlot,Gear> equipment,MobOptions options){
        public Member{if(entityType==null)throw new IllegalArgumentException("encounter member entity type is required");count=Math.max(1,Math.min(64,count));equipment=equipment==null?Map.of():Map.copyOf(equipment);options=options==null?MobOptions.DEFAULT:options;}
        public Member(ResourceLocation entityType,int count){this(entityType,count,Map.of(),MobOptions.DEFAULT);}
        public Member(ResourceLocation entityType,int count,Map<EquipmentSlot,Gear> equipment){this(entityType,count,equipment,MobOptions.DEFAULT);}
    }
    public record Gear(ResourceLocation item,int count,Map<ResourceLocation,Integer> enchantments,float dropChance){
        public Gear{if(item==null)throw new IllegalArgumentException("equipment item is required");count=Math.max(1,Math.min(99,count));enchantments=enchantments==null?Map.of():Map.copyOf(enchantments);dropChance=Math.max(0.0F,Math.min(1.0F,dropChance));}
    }
    public record Wave(String id,List<Member> members,int delayTicks,WaveTrigger trigger,String bossBarTitle,List<WaveHook> hooks){
        public Wave{if(id==null||!id.matches("[a-z][a-z0-9_.-]{0,63}"))throw new IllegalArgumentException("wave id must be a stable lowercase identifier");if(members==null||members.isEmpty())throw new IllegalArgumentException("wave "+id+" needs members");if(members.size()>64)throw new IllegalArgumentException("wave "+id+" exceeds 64 member definitions");members=List.copyOf(members);if(delayTicks<0||delayTicks>12000)throw new IllegalArgumentException("wave "+id+" delay_ticks must be between 0 and 12000");trigger=trigger==null?WaveTrigger.ALL_DEFEATED:trigger;bossBarTitle=bossBarTitle==null?"":bossBarTitle;if(bossBarTitle.length()>128)throw new IllegalArgumentException("wave "+id+" boss_bar_title exceeds 128 characters");hooks=hooks==null?List.of():List.copyOf(hooks);if(hooks.size()>32)throw new IllegalArgumentException("wave "+id+" exceeds 32 hooks");}
    }
    public record WaveHook(String id,HookType type,String text){public WaveHook{if(id==null||!id.matches("[a-z][a-z0-9_.-]{0,63}"))throw new IllegalArgumentException("wave hook id must be a stable lowercase identifier");if(type==null)throw new IllegalArgumentException("wave hook type is required");if(text==null||text.isBlank()||text.length()>512)throw new IllegalArgumentException("wave hook text must contain 1 to 512 characters");}}
    public enum HookType{NOTIFICATION,DIALOGUE}
    public record MobOptions(String customName,boolean nameVisible,boolean glowing,boolean persistent,
            Map<ResourceLocation,Double> attributes,boolean boss,BossColor bossBarColor,BossOverlay bossBarOverlay){
        public static final MobOptions DEFAULT=new MobOptions("",false,false,false,Map.of(),false,BossColor.RED,BossOverlay.PROGRESS);
        public MobOptions{customName=customName==null?"":customName;if(customName.length()>128)throw new IllegalArgumentException("custom_name exceeds 128 characters");attributes=attributes==null?Map.of():Map.copyOf(attributes);bossBarColor=bossBarColor==null?BossColor.RED:bossBarColor;bossBarOverlay=bossBarOverlay==null?BossOverlay.PROGRESS:bossBarOverlay;}
    }
    public enum BossColor{PINK,BLUE,RED,GREEN,YELLOW,PURPLE,WHITE}
    public enum BossOverlay{PROGRESS,NOTCHED_6,NOTCHED_10,NOTCHED_12,NOTCHED_20}
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
