package com.jvn.villagerretaliation.scene.encounter;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record EncounterTemplate(ResourceLocation id,int version,ResourceLocation controller,List<Member> members,
        int extraPerAdditionalPlayer,int maxPartySize,int placementAttempts,int spawnRadius,
        RespawnPolicy respawnPolicy,CleanupPolicy cleanupPolicy,CompletionCondition completionCondition) {
    public EncounterTemplate { if(id==null||controller==null||members==null||members.isEmpty())throw new IllegalArgumentException("encounter needs id, controller, and members");members=List.copyOf(members);version=Math.max(1,version);extraPerAdditionalPlayer=Math.max(0,extraPerAdditionalPlayer);maxPartySize=Math.max(1,maxPartySize);placementAttempts=Math.max(1,Math.min(64,placementAttempts));spawnRadius=Math.max(1,Math.min(32,spawnRadius));respawnPolicy=respawnPolicy==null?RespawnPolicy.NEVER:respawnPolicy;cleanupPolicy=cleanupPolicy==null?CleanupPolicy.REMOVE_SURVIVORS:cleanupPolicy;completionCondition=completionCondition==null?CompletionCondition.ALL_DEFEATED:completionCondition; }
    public int scaledCount(int partySize){int base=members.stream().mapToInt(Member::count).sum();return base+Math.max(0,Math.min(maxPartySize,partySize)-1)*extraPerAdditionalPlayer;}
    public record Member(ResourceLocation entityType,int count){public Member{if(entityType==null)throw new IllegalArgumentException("encounter member entity type is required");count=Math.max(1,Math.min(64,count));}}
    public enum RespawnPolicy{NEVER,MISSING_IF_LOADED,UNTIL_FIRST_DEFEAT}
    public enum CleanupPolicy{REMOVE_SURVIVORS,PRESERVE_IN_WORLD}
    public enum CompletionCondition{ALL_DEFEATED,ALL_GONE}
}
