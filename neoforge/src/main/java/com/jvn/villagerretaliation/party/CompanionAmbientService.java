package com.jvn.villagerretaliation.party;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;

/** One shared scheduler for perception and meaningful home encounters. No menus, cameras or rewards. */
public final class CompanionAmbientService {
    private static MinecraftServer server;
    private static final Map<UUID, Candidate> pending = new HashMap<>();
    private static final Set<UUID> observedThisRun = new HashSet<>();
    private CompanionAmbientService() { }
    private record Candidate(UUID speaker, CompanionStructurePerception.Evidence evidence, long since, long expires) { }

    public static void tick(MinecraftServer value) {
        if (server != value) { server=value; pending.clear(); observedThisRun.clear(); }
        var ledger=CompanionAmbientLedger.get(value); if (!ledger.available()) return;
        ledger.tick(); if (ledger.clock()%20!=0) return;
        var parties=new ArrayList<PartyRecord>(); PartySavedData.get(value.overworld()).partyRecords().forEach(parties::add);
        parties.sort(Comparator.comparing(p -> p.id().toString()));
        if (!parties.isEmpty()) Collections.rotate(parties, -(int)((ledger.clock()/20)%parties.size()));
        var villagers=new LinkedHashMap<UUID,Villager>();
        for (var party:parties) for (var record:party.villagers()) {
            var v=VillagerEntityResolver.loaded(value,record.villagerId()); if (v!=null) villagers.put(v.getUUID(),v);
        }
        // Retained personal companions allow a real, nearby reunion after dismissal.
        for (var player:value.getPlayerList().getPlayers()) for (var state:CompanionSavedData.get(value.overworld()).forPlayer(player.getUUID())) {
            var v=VillagerEntityResolver.loaded(value,state.getUUID("Villager")); if (v!=null) villagers.put(v.getUUID(),v);
        }
        // Observe busy intervals even when there is no pending callout.
        value.getPlayerList().getPlayers().forEach(p -> quiet(p,ledger));
        for (var v:villagers.values()) { eligible(v,ledger); home(v,ledger); }
        int budget=4;
        for (var party:parties) {
            if (budget--<=0) break;
            if(ledger.partyCooling(party.id())) {pending.remove(party.id());continue;}
            Candidate candidate=pending.get(party.id());
            if (candidate!=null) {
                var v=villagers.get(candidate.speaker());
                if (v==null || party.villager(v.getUUID())==null || !eligible(v,ledger) || ledger.clock()>candidate.expires()
                        || !fresh(v,candidate.evidence())) { pending.remove(party.id()); continue; }
                var recipients=recipients(party,v,ledger,candidate.evidence());
                if (recipients.isEmpty()) { pending.remove(party.id()); continue; }
                if (ledger.clock()-candidate.since()>=CompanionAmbientResources.get().dwell()) {
                    speak(v,recipients,candidate.evidence().instance(),candidate.evidence().pool(),candidate.evidence().anchor(),ledger);
                    pending.remove(party.id());
                }
                continue;
            }
            var speakers=party.villagers().stream().map(r -> villagers.get(r.villagerId())).filter(Objects::nonNull)
                    .filter(v -> eligible(v,ledger)).sorted(Comparator.comparing(v -> v.getUUID().toString())).toList();
            var choices=new ArrayList<Candidate>();
            for (var v:speakers) {
                var level=(ServerLevel)v.level();
                var starts=CompanionStructurePerception.starts(level,v.blockPosition());
                if (starts.isEmpty()) continue;
                var entry=starts.get((int)((ledger.clock()/20)%starts.size()));
                var evidence=CompanionStructurePerception.inspect(level,v,entry.getKey(),entry.getValue(),ledger.clock()/20);
                if (evidence.isEmpty()) continue;
                var e=evidence.get();
                ledger.section("Observed").putBoolean(e.instance()+"/"+v.getUUID(),true);
                if (ledger.spoken(e.instance(),v.getUUID()) || recipients(party,v,ledger,e).isEmpty()) continue;
                choices.add(new Candidate(v.getUUID(),e,ledger.clock(),ledger.clock()+60));
            }
            choices.stream().min(Comparator.comparingDouble((Candidate c) -> villagers.get(c.speaker()).position().distanceToSqr(Vec3.atCenterOf(c.evidence().anchor())))
                    .thenComparing(c -> c.speaker().toString())).ifPresent(c -> pending.put(party.id(),c));
        }
        if (ledger.clock()%200==0) ledger.commit();
    }

    private static boolean fresh(Villager v, CompanionStructurePerception.Evidence e) {
        return CompanionStructurePerception.fresh((ServerLevel)v.level(),v,e);
    }

    private static List<ServerPlayer> recipients(PartyRecord party,Villager v,CompanionAmbientLedger ledger,CompanionStructurePerception.Evidence evidence) {
        return party.playerIds().stream().map(id -> v.getServer().getPlayerList().getPlayer(id)).filter(Objects::nonNull)
                .filter(p -> p.serverLevel()==v.level() && p.distanceToSqr(v)<=24*24 && quiet(p,ledger)
                        && !ledger.heard(evidence.instance(),p.getUUID()) && !ledger.cooling(p.getUUID())
                        && CompanionStructurePerception.visible(p.serverLevel(),p,evidence.anchor(),Math.min(CompanionAmbientResources.get().range(),Math.max(0,p.requestedViewDistance()*16-16)))).toList();
    }

    private static boolean eligible(Villager v,CompanionAmbientLedger ledger) {
        var level=(ServerLevel)v.level(); var state=CompanionSavedData.get(level).find(v.getUUID());
        boolean dialogue=VillagerConversationService.isConversing(v)
                || com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(v);
        boolean busy=!v.isAlive() || !CompanionLifecycleService.canPart(v) || VillagerConversationService.isConversing(v)
                || com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(v)
                || state==null || state.getBoolean("Returning") || state.getString("Status").equals("At home")
                || CompanionRelationshipService.trustSuspended(level,v.getUUID());
        boolean hurt=v.hurtTime>0 || v.getTarget()!=null || v.getLastHurtByMob()!=null && v.tickCount-v.getLastHurtByMobTimestamp()<CompanionAmbientResources.get().combatQuiet();
        busy |= hurt;
        if (dialogue || hurt) ledger.section("Quiet").putLong(v.getUUID().toString(),ledger.clock()+(hurt?CompanionAmbientResources.get().combatQuiet():CompanionAmbientResources.get().dialogueQuiet()));
        return !busy && !ledger.cooling(v.getUUID()) && ledger.section("Quiet").getLong(v.getUUID().toString())<=ledger.clock();
    }
    private static boolean quiet(ServerPlayer p,CompanionAmbientLedger ledger) {
        boolean hurt=p.hurtTime>0 || p.getLastHurtByMob()!=null && p.tickCount-p.getLastHurtByMobTimestamp()<CompanionAmbientResources.get().combatQuiet();
        boolean busy=!p.isAlive() || p.isSpectator() || p.getChatVisibility()!=net.minecraft.world.entity.player.ChatVisiblity.FULL
                || VillagerConversationService.isConversing(p) || p.containerMenu!=p.inventoryMenu;
        if (hurt||busy) ledger.section("Quiet").putLong(p.getUUID().toString(),ledger.clock()+(hurt?CompanionAmbientResources.get().combatQuiet():CompanionAmbientResources.get().dialogueQuiet()));
        return !hurt && !busy && ledger.section("Quiet").getLong(p.getUUID().toString())<=ledger.clock();
    }

    static boolean speak(Villager v,List<ServerPlayer> recipients,String key,String pool,BlockPos anchor,CompanionAmbientLedger ledger) {
        var lines=CompanionAmbientResources.get().pools().get(pool); if (lines==null||recipients.isEmpty()) return false;
        String previous=ledger.section("LastLine").getString(v.getUUID().toString());
        var fresh=lines.stream().filter(l -> lines.size()==1 || !l.id().equals(previous)).toList();
        int roll=v.getRandom().nextInt(fresh.stream().mapToInt(CompanionAmbientResources.Line::weight).sum());
        var chosen=fresh.getLast(); for(var line:fresh) { roll-=line.weight(); if(roll<0) { chosen=line; break; } }
        var party=PartyService.getPartyForVillager((ServerLevel)v.level(),v.getUUID()).map(PartyRecord::id).orElse(null);
        if (!ledger.reserve(key,v.getUUID(),recipients.stream().map(ServerPlayer::getUUID).toList(),chosen.id(),anchor.toShortString(),CompanionAmbientResources.get().cooldown(),party)) return false;
        for(var p:recipients) {
            String text=chosen.text().replace("{direction}",direction(p.getYRot(),p.getXRot(),p.position(),Vec3.atCenterOf(anchor)));
            // Literal private chat. Never broadcast one listener's direction or parse dynamic markup.
            p.sendSystemMessage(Component.literal("<Companion> "+text));
        }
        return true;
    }

    public static String direction(float yaw,float pitch,Vec3 listener,Vec3 anchor) {
        Vec3 delta=anchor.subtract(listener); double horizontal=delta.horizontalDistance();
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||horizontal<2 || Math.abs(pitch)>60 || Math.abs(delta.y)>horizontal) return "nearby";
        double radians=Math.toRadians(yaw); double forward=delta.x*-Math.sin(radians)+delta.z*Math.cos(radians);
        double right=delta.x*-Math.cos(radians)+delta.z*-Math.sin(radians);
        double angle=Math.toDegrees(Math.atan2(right,forward));
        if(angle>=-45 && angle<45) return "ahead of you";
        if(angle>=45 && angle<135) return "on your right";
        if(angle>=135 || angle<-135) return "behind you";
        return "on your left";
    }

    static void home(Villager v,CompanionAmbientLedger ledger) {
        var level=(ServerLevel)v.level(); var state=CompanionSavedData.get(level).find(v.getUUID());
        if(state==null || !state.contains("HomeDimension") || !state.hasUUID("Owner")) return;
        String key=v.getUUID().toString(); var journeys=ledger.section("Journeys");
        if(!journeys.contains(key)) journeys.put(key,new CompoundTag()); var journey=journeys.getCompound(key);
        String homeId=state.getString("HomeDimension")+"/"+(state.hasUUID("HomeVillage")?state.getUUID("HomeVillage"):v.getUUID())+"/"+state.getLong("HomePosition");
        if(!homeId.equals(journey.getString("Home"))) {
            archive(v,journey,ledger,"home_changed");journey=new CompoundTag(); journey.putString("Home",homeId); journeys.put(key,journey);
        }
        var anchor=BlockPos.of(state.getLong("HomePosition")); boolean homeDimension=state.getString("HomeDimension").equals(level.dimension().location().toString());
        boolean loaded=homeDimension && level.getChunkSource().getChunkNow(anchor.getX()>>4,anchor.getZ()>>4)!=null;
        if(loaded && !validHome(v,state,anchor)) { archive(v,journey,ledger,"home_invalid");journey.putString("State","CANCELLED"); journey.remove("Settled"); return; }
        var party=PartyService.getPartyForVillager(level,v.getUUID()).orElse(null);
        boolean active=party!=null && party.villager(v.getUUID())!=null;
        if(!active && journey.hasUUID("Journey") && journey.getCompound("Participants").getAllKeys().stream().noneMatch(id -> {
            var p=level.getServer().getPlayerList().getPlayer(UUID.fromString(id));
            return p!=null && p.serverLevel()==level && p.distanceToSqr(v)<=48*48;
        })) journey.putBoolean("Separated",true);
        double distance=homeDimension?v.position().distanceTo(Vec3.atCenterOf(anchor)):Double.MAX_VALUE;
        long now=ledger.clock(); boolean continuous=observedThisRun.add(v.getUUID()) ? false : now-journey.getLong("LastTick")==20;
        Vec3 previous=new Vec3(journey.getDouble("X"),journey.getDouble("Y"),journey.getDouble("Z"));
        double movement=continuous && journey.getString("Dimension").equals(level.dimension().location().toString())?v.position().distanceTo(previous):0;
        journey.putLong("LastTick",now); journey.putDouble("X",v.getX());journey.putDouble("Y",v.getY());journey.putDouble("Z",v.getZ());journey.putString("Dimension",level.dimension().location().toString());
        if(!v.isAlive()) { journey.putString("State","CANCELLED");journey.remove("Settled");return; }
        if(distance<=24 && loaded) {
            if(journey.getString("State").equals("AWAY_QUALIFIED") || journey.getString("State").equals("RETURN_PENDING")) {
                journey.putString("State","RETURN_PENDING");
                journey.putInt("ReturnDwell",continuous?journey.getInt("ReturnDwell")+20:0);
                if(journey.getInt("ReturnDwell")>=100) { joinCohort(v,journey,ledger);encounter(v,party,active,anchor,journey,ledger); }
                return;
            }
            if(journey.getString("State").equals("RETURNED")) { encounter(v,party,active,anchor,journey,ledger); return; }
            journey.putInt("Settling",continuous?journey.getInt("Settling")+20:0);
            if(journey.getInt("Settling")>=100) { journey.putBoolean("Settled",true); journey.putString("State","HOME"); }
            return;
        }
        journey.putInt("Settling",0);journey.putInt("ReturnDwell",0);
        if(distance<=64) { if(journey.getString("State").equals("DEPARTING")) { journey.putString("State","HOME"); journey.remove("Journey"); } return; }
        if(!journey.getBoolean("Settled") || !active || party.villager(v.getUUID()).commandMode()==PartyCommandMode.STAY) return;
        if(journey.getString("State").equals("HOME")) {
            journey.putString("State","DEPARTING");journey.putUUID("Journey",UUID.randomUUID());journey.putInt("Absence",0);journey.putInt("OutsideDwell",0);
            journey.remove("Episode");journey.remove("Separated");journey.putDouble("Travel",0);journey.put("Participants",new CompoundTag());
            var departing = journey.getCompound("Participants");
            for (UUID id : party.playerIds()) {
                var p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null && p.isAlive() && !p.isSpectator() && p.serverLevel() == level && p.distanceToSqr(v) <= 48*48) departing.put(id.toString(), new CompoundTag());
            }
            journey.put("Participants", departing);
        }
        if(!Set.of("DEPARTING","AWAY_QUALIFIED","RETURN_PENDING").contains(journey.getString("State"))) return;
        journey.putString("State",journey.getString("State").equals("RETURN_PENDING")?"AWAY_QUALIFIED":journey.getString("State"));
        var participants=journey.getCompound("Participants"); boolean online=false;
        for(UUID id:party.playerIds()) {
            if (!participants.contains(id.toString())) continue;
            var p=level.getServer().getPlayerList().getPlayer(id);
            String idKey=id.toString(); var trip=participants.getCompound(idKey);
            if(p!=null && !p.isAlive()) { trip.putBoolean("Invalid",true); continue; }
            if(trip.getBoolean("Invalid") || p==null || p.isSpectator() || p.serverLevel()!=level || p.distanceToSqr(v)>48*48) continue;
            online=true;
            accumulateParticipant(trip,p,now,continuous,movement);
            participants.put(idKey,trip);
        }
        journey.put("Participants",participants);
        journey.putInt("OutsideDwell",continuous && online?journey.getInt("OutsideDwell")+20:0);
        if(continuous && online) { journey.putInt("Absence",journey.getInt("Absence")+20); if(movement<=16)journey.putDouble("Travel",journey.getDouble("Travel")+movement); }
        if(journey.getInt("Absence")>=6000 && journey.getDouble("Travel")>=256 && journey.getInt("OutsideDwell")>=600) journey.putString("State","AWAY_QUALIFIED");
    }

    static void accumulateParticipant(CompoundTag trip,ServerPlayer player,long now,boolean continuous,double companionMovement) {
        Vec3 previous=new Vec3(trip.getDouble("X"),trip.getDouble("Y"),trip.getDouble("Z"));
        double movement=player.position().distanceTo(previous);
        if(continuous && trip.getLong("LastTick")==now-20 && trip.getString("Dimension").equals(player.serverLevel().dimension().location().toString())) {
            trip.putInt("Ticks",trip.getInt("Ticks")+20);
            trip.putInt("OutsideDwell",trip.getInt("OutsideDwell")+20);
            if(movement<=16 && companionMovement<=16) trip.putDouble("Travel",trip.getDouble("Travel")+Math.min(movement,companionMovement));
        } else trip.putInt("OutsideDwell",0);
        trip.putLong("LastTick",now);trip.putDouble("X",player.getX());trip.putDouble("Y",player.getY());trip.putDouble("Z",player.getZ());
        trip.putString("Dimension",player.serverLevel().dimension().location().toString());
    }

    private static void archive(Villager v,CompoundTag journey,CompanionAmbientLedger ledger,String reason) {
        if(!journey.hasUUID("Journey"))return;
        var record=journey.copy();record.putString("Reason",reason);
        ledger.section("JourneyHistory").put(v.getUUID()+"/"+journey.getUUID("Journey"),record);
    }

    static void claimHomecomingStory(Villager v) {
        var ledger=CompanionAmbientLedger.get(v.getServer());var journey=ledger.section("Journeys").getCompound(v.getUUID().toString());
        if(!journey.hasUUID("Journey") || Set.of("HOME","CONSUMED","CANCELLED").contains(journey.getString("State")))return;
        if(journey.hasUUID("Episode"))ledger.section("Cohorts").getCompound(journey.getString("Home")+"/"+journey.getUUID("Episode")).putBoolean("Sealed",true);
        journey.putString("State","CONSUMED");journey.remove("Settled");journey.putString("Reason","story_claimed");
        archive(v,journey,ledger,"story_claimed");ledger.commit();
    }

    public static void onDeath(net.minecraft.world.entity.LivingEntity entity) {
        if(!(entity instanceof Villager) && !(entity instanceof ServerPlayer))return;
        if(!(entity.level() instanceof ServerLevel level))return;
        var ledger=CompanionAmbientLedger.get(level.getServer());
        if(entity instanceof Villager v) {
            var journey=ledger.section("Journeys").getCompound(v.getUUID().toString());
            if(journey.hasUUID("Journey")) {journey.putString("State","CANCELLED");journey.remove("Settled");archive(v,journey,ledger,"death");}
        } else if(entity instanceof ServerPlayer p) {
            for(String key:ledger.section("Journeys").getAllKeys()) {
                var journey=ledger.section("Journeys").getCompound(key);var participants=journey.getCompound("Participants");
                if(!participants.contains(p.getUUID().toString()))continue;
                participants.getCompound(p.getUUID().toString()).putBoolean("Invalid",true);
                if(participants.getAllKeys().stream().allMatch(id -> participants.getCompound(id).getBoolean("Invalid"))) {
                    journey.putString("State","CANCELLED");journey.remove("Settled");journey.putString("Reason","participants_died");
                    ledger.section("JourneyHistory").put(key+"/"+journey.getUUID("Journey"),journey.copy());
                }
            }
        }
        ledger.commit();
    }

    private static void joinCohort(Villager v,CompoundTag journey,CompanionAmbientLedger ledger) {
        if(journey.hasUUID("Episode"))return;
        String home=journey.getString("Home");var windows=ledger.section("ArrivalWindows");var window=windows.getCompound(home);
        if(!window.hasUUID("Episode") || window.getLong("Until")<ledger.clock()) {
            window=new CompoundTag();window.putUUID("Episode",UUID.randomUUID());window.putLong("Until",ledger.clock()+100);windows.put(home,window);
        }
        UUID episode=window.getUUID("Episode");journey.putUUID("Episode",episode);
        String key=home+"/"+episode;var cohorts=ledger.section("Cohorts");
        if(!cohorts.contains(key)) {var cohort=new CompoundTag();cohort.putUUID("Episode",episode);cohorts.put(key,cohort);}
        cohorts.getCompound(key).putUUID(v.getUUID().toString(),journey.getUUID("Journey"));
    }

    private static boolean validHome(Villager v,CompoundTag state,BlockPos anchor) {
        var level=(ServerLevel)v.level();
        if(state.hasUUID("HomeVillage")) {
            var record=com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level).canonicalRecord(
                    new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(state.getUUID("HomeVillage"))).orElse(null);
            if(record==null || record.archived() || !record.center().equals(anchor)) return false;
        } else {
            var memory=v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).orElse(null);
            if(!state.getBoolean("HomeBed") || memory==null || !memory.pos().equals(anchor) || !memory.dimension().equals(level.dimension())
                    || !(level.getBlockState(anchor).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return false;
        }
        return CompanionLifecycleService.safeDestination(level,v,anchor)!=null;
    }

    private static void encounter(Villager v,PartyRecord party,boolean active,BlockPos anchor,CompoundTag journey,CompanionAmbientLedger ledger) {
        var level=(ServerLevel)v.level(); var participants=journey.getCompound("Participants");
        var listeners=new ArrayList<ServerPlayer>();
        for(String id:participants.getAllKeys()) {
            var trip=participants.getCompound(id); if(trip.getBoolean("Invalid") || trip.getInt("Ticks")<6000 || trip.getDouble("Travel")<256 || trip.getInt("OutsideDwell")<600) continue;
            var p=level.getServer().getPlayerList().getPlayer(UUID.fromString(id));
            if(p!=null && p.isAlive() && !p.isSpectator() && p.serverLevel()==level && p.distanceToSqr(v)<=24*24
                    && CompanionStructurePerception.visible(level,p,v.blockPosition().above(),24)) listeners.add(p);
        }
        if(listeners.isEmpty()) {journey.putString("State","RETURNED");return;}
        boolean reunion=journey.getString("State").equals("RETURNED");
        boolean supportedEncounter=active?!reunion:journey.getBoolean("Separated");
        String scenario=reunion?"reunited_at_home":active?"returning_together":"companion_returns_alone";
        joinCohort(v,journey,ledger);
        String home=journey.getString("Home");var cohort=ledger.section("Cohorts").getCompound(home+"/"+journey.getUUID("Episode"));
        boolean duplicate=cohort.getBoolean("Sealed");cohort.putBoolean("Sealed",true);
        journey.putString("State","CONSUMED"); journey.remove("Settled"); journey.putString("Reason",duplicate?"cohort_suppressed":"encounter_consumed");
        journey.putUUID("Episode",cohort.getUUID("Episode"));
        boolean story=listeners.stream().anyMatch(p -> CompanionGrowthService.ownsHomecoming(v,p));
        if(story)journey.putString("Reason","story_priority");
        if(!supportedEncounter)journey.putString("Reason","unverified_arrival_context");
        boolean canSpeak=supportedEncounter && !duplicate && !story && CompanionLifecycleService.canPart(v) && !VillagerConversationService.isConversing(v)
                && !com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(v)
                && !CompanionRelationshipService.trustSuspended(level,v.getUUID())
                && ledger.section("Quiet").getLong(v.getUUID().toString())<=ledger.clock()
                && listeners.stream().allMatch(p -> quiet(p,ledger) && !ledger.cooling(p.getUUID()));
        // Suppression is durable too. A later reunion cannot relabel this encounter.
        archive(v,journey,ledger,journey.getString("Reason"));
        if(canSpeak) { if (!speak(v,listeners,"home/"+v.getUUID()+"/"+journey.getUUID("Journey")+"/"+home,scenario,anchor,ledger)) ledger.commit(); }
        else ledger.commit();
    }
}
