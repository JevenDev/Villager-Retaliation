package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.*;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

/** Natural villages use the existing settlement workers, stock, claim index and escrow. */
public final class AutonomousVillageService {
    private static final Map<MinecraftServer,Schedule> SCHEDULES=new WeakHashMap<>();
    private static final int MAX_RESIDENTS=128;
    private AutonomousVillageService() {}
    private static final class Schedule { List<VillageAllegianceId> villages=List.of(); long refresh; int cursor; }

    public static void tick(MinecraftServer server) {
        long now=server.overworld().getGameTime();
        if(now%20!=0) return;
        var rules=AutonomousVillageRules.get(server); if(!rules.enabled()) return;
        var data=SettlementSavedData.get(server.overworld()); if(data.readOnly()) return;
        var registry=VillageAllegianceRegistrySavedData.get(server.overworld());
        var schedule=SCHEDULES.computeIfAbsent(server,ignored->new Schedule());
        if(now>=schedule.refresh) {
            schedule.villages=registry.activeRecords(null).stream().filter(village-> !registry.isManaged(village.id())
                    || data.get(village.id()).map(town->town.autonomous()!=null).orElse(false)).map(VillageAllegianceRegistrySavedData.AllegianceRecord::id).toList();
            schedule.refresh=now+200;
        }
        if(schedule.villages.isEmpty()) return;
        // One village per server turn, regardless of village count. No catch-up loop.
        var id=schedule.villages.get(Math.floorMod(schedule.cursor++,schedule.villages.size()));
        var village=registry.canonicalRecord(id).orElse(null);
        if(village==null || village.archived() || village.originDimension()==null || village.residents().size()>MAX_RESIDENTS) return;
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,village.originDimension())); if(level==null) return;
        var town=data.get(id).orElse(null);
        if(town==null) {
            if(AutonomousVillageAdmission.inspect(level,id)!=AutonomousVillageAdmission.Result.ELIGIBLE) return;
            town=data.establishAutonomous(level,village).orElse(null);
        }
        if(town==null || town.autonomous()==null) return;
        var state=town.autonomous();
        if(!loaded(level,town)) { state.status="Paused: village center or residents unloaded"; return; }
        survey(level,town);
        if(!beginPlan(town,rules,now)) return;
        plan(level,town,rules,now);
    }

    static boolean beginPlan(SettlementRecord town,AutonomousVillageRules.Rules rules,long now) {
        if(now<town.autonomous().nextPlan) return false;
        town.autonomous().nextPlan=now+rules.cadence();town.changed();return true;
    }
    static boolean loaded(ServerLevel level,SettlementRecord town) {
        if(!level.hasChunkAt(town.center()) || !level.isPositionEntityTicking(town.center())) return false;
        return VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id()).map(village -> village.residents().values().stream()
                .limit(MAX_RESIDENTS).anyMatch(member -> level.getEntity(member.id()) instanceof Villager v && v.isAlive()
                        && level.isPositionEntityTicking(v.blockPosition()))).orElse(false);
    }

    static void plan(ServerLevel level,SettlementRecord town,AutonomousVillageRules.Rules rules,long now) {
        var state=town.autonomous();
        AutonomousVillageSuccession.update(level,town);
        town.territory().autonomousCapacity(Math.max(0,rules.expansions()-state.expansions));
        town.economy().configureBudget(CurrencyAmount.of(rules.reserve(),1),CurrencyAmount.of(rules.dailyBudget(),1));
        survey(level,town);
        complete(level,town);
        var jobs=new ArrayList<Runnable>();
        SettlementCareService.schedule(level,town,now,jobs);
        jobs.add(()->SettlementConstructionSiteService.update(level,town));
        jobs.add(town::maintainProjects);
        for(var depot:town.depots().values()) jobs.add(()->SettlementDepotService.observeRules(level,depot));
        SettlementTradeService.schedule(level,town,now,jobs);
        SettlementSavedData.get(level).simulation.enqueue("autonomous-care:"+town.id(),SettlementWork.guard(new SettlementWork(jobs),()->town.autonomous()!=null && loaded(level,town)));
        repairTreasury(level,town);
        SettlementAutonomyService.fill(level,town,null);
        configureTrade(town,rules);
        income(level,town,rules,now);
        town.economy().depositOverflow();
        if(state.ruler==null) {state.status="Ruler vacant: no verified eligible successor";state.nextAction="Await an eligible resident";return;}
        var active=town.projects().values().stream().filter(SettlementProject::unfinished).findFirst().orElse(null);
        if(active!=null) {
            if(active.pending()) {assignBuilder(level,town,active.workplace());SettlementConstructionService.tickQueue(level,town); }
            state.status="Construction: "+active.status()+" "+active.blocker()+" "+SettlementConstructionMaterials.blocker(active);
            state.nextAction="Finish "+active.label()+"; supply missing materials/tools or clear the site";
            return;
        }
        if(AutonomousVillageMaintenance.plan(level,town,rules,now)) return;
        var roster=VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(),now);
        int population=roster.size();
        var need=need(town,population,rules.population());
        if((need==null || need==SettlementAreaType.WORKSHOP) && SettlementFoodService.nutrition(town)<SettlementFoodService.reserveNutrition(level,town)) {
            state.need="food_reserve";state.observations=0;state.status="Food reserve low: prioritize staffed food production and permitted supplies";
            state.nextAction="Harvest/store food; funded staffed trade may buy bread; defer discretionary construction";return;
        }
        if(need==null) {state.need="";state.observations=0;state.status="Stable: essential facilities available";state.nextAction="Maintain food, jobs and treasury";return;}
        if(!state.need.equals(need.name())) {state.need=need.name();state.observations=0;state.siteCursor=0;}
        state.observations=Math.min(rules.sustained(),state.observations+1);
        state.nextAction="Provide "+need.name().toLowerCase(Locale.ROOT);
        if(state.observations<rules.sustained()) {state.status="Observing sustained need: "+state.observations+"/"+rules.sustained();return;}
        if(state.surveyCursor<town.territory().plots().size()) {state.status="Surveying existing facilities before development";return;}
        if(state.completed>=rules.projects()) {state.status="Development cap reached";return;}
        var structure=rules.catalog().get(need);
        if(structure==null) {state.status="No authored template configured for "+need;return;}
        var plan=SettlementConstructionService.scan(level,structure,Rotation.NONE);
        if(plan==null || plan.blocks().size()>4096 || (long)(plan.localMax().getX()-plan.localMin().getX()+1)*(plan.localMax().getY()-plan.localMin().getY()+1)*(plan.localMax().getZ()-plan.localMin().getZ()+1)>8192) {state.status="Template unavailable or exceeds 4096-block planning limit";return;}
        if(town.economy().constructionFunds().compareTo(CurrencyAmount.of(plan.price(),1))<0) {state.status="Insufficient spendable funds (pending deposits are not funds)";return;}
        var plots=town.autonomous().plots(town);
        // One candidate per observation, remembered across reload. Existing placement checks are authoritative.
        int index=Math.floorMod(state.siteCursor++,plots.size()); var chunk=new ChunkPos(plots.get(index));
        if(!level.hasChunk(chunk.x,chunk.z)) {state.status="Candidate site unloaded";return;}
        var anchor=new BlockPos(chunk.getMinBlockX()+8,0,chunk.getMinBlockZ()+8);
        anchor=anchor.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,anchor.getX(),anchor.getZ()));
        var origin=SettlementConstructionService.origin(plan,anchor);
        String failure=SettlementConstructionService.placementFailure(level,SettlementStorageService.actor(level,town),town,plan,origin);
        if(failure.isEmpty()) {
            var request=UUID.randomUUID(); var area=SettlementConstructionService.workplace(level,town,plan,origin,request);
            var project=SettlementProject.planned(town,area,SettlementProject.AUTOMATIC_BUILDER,plan,origin,request,now);
            project.queue(town.leader());
            if(town.queueProject(town.leader(),project)) {
                state.projects.put(request,need);state.status="Queued "+project.label();state.observations=0;
                assignBuilder(level,town,area); SettlementConstructionService.tickQueue(level,town);town.changed();return;
            }
            failure="queue capacity";
        }
        state.status="Site blocked: "+failure;
        if(state.siteCursor>=plots.size() && (failure.equals("bounds") || failure.equals("overlap") || failure.equals("blocked_existing")))
            expand(level,town,rules,now);
    }

    static SettlementAreaType need(SettlementRecord town,int population,int target) {
        if(population==0) return null;
        if(town.areas().values().stream().noneMatch(area->area.enabled() && area.type().foodProduction())) return SettlementAreaType.FARM;
        if(town.housingCapacity()<population) return SettlementAreaType.HOME;
        if(town.areas().values().stream().noneMatch(area->area.enabled() && area.type()==SettlementAreaType.KITCHEN)) return SettlementAreaType.KITCHEN;
        if(town.areas().values().stream().noneMatch(area->area.enabled() && area.type()==SettlementAreaType.WORKSHOP)) return SettlementAreaType.WORKSHOP;
        return null;
    }

    static void income(ServerLevel level,SettlementRecord town,AutonomousVillageRules.Rules rules,long now) {
        var state=town.autonomous();
        if(state.lastIncome<0) {state.lastIncome=now;town.changed();return;}
        if(now-state.lastIncome<rules.incomeInterval()) return;
        if(now-state.lastIncome>rules.incomeInterval()+rules.cadence()) {state.lastIncome=now;town.changed();return;}
        state.lastIncome=now; // No elapsed-time multiplier and no offline/backlog catch-up.
        long day=Math.floorDiv(now,24000);
        if(day>state.incomeDay) {state.incomeDay=day;state.dailyIncome=0;}
        int workers=(int)town.duties().keySet().stream().filter(id -> level.getEntity(id) instanceof Villager v && v.isAlive() && !v.isBaby()
                && level.isPositionEntityTicking(v.blockPosition()) && SettlementWorkforceService.onDuty(level,v)
                && SettlementWorkforceService.role(v)!=HiredVillagerRole.NITWIT && SettlementWorkforceService.role(v)!=HiredVillagerRole.BUILDER).count();
        int count=Math.min(workers,Math.max(0,rules.incomeCap()-state.dailyIncome));
        var remaining=CurrencyAmount.of(rules.fundsCap(),1).subtractClamped(town.economy().physicalBalance().add(town.economy().virtualBalance()).add(town.economy().pendingDeposit()));
        count=Math.min(count,remaining.wholeUnits().min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).intValue());
        if(count>0) {town.economy().credit(CurrencyAmount.of(count,1));state.dailyIncome+=count;}
        town.changed();
    }

    static boolean expand(ServerLevel level,SettlementRecord town,AutonomousVillageRules.Rules rules,long now) {
        var state=town.autonomous();
        if(state.expansions>=rules.expansions() || state.observations<rules.sustained()) {state.status="Expansion cap or sustained-need check";return false;}
        var data=SettlementSavedData.get(level);var registry=VillageAllegianceRegistrySavedData.get(level);
        var cost=CurrencyAmount.of(rules.expansionPrice(),1);
        if(town.economy().constructionFunds().compareTo(cost)<0) {state.status="Expansion needs "+rules.expansionPrice()+" spendable currency plus reserve";return false;}
        var plots=town.autonomous().plots(town);
        var edge=new ChunkPos(plots.get(Math.floorMod(state.siteCursor,plots.size())));
        int direction=Math.floorMod(state.siteCursor/plots.size(),4);
        int x=edge.x+(direction==0?1:direction==1?-1:0),z=edge.z+(direction==2?1:direction==3?-1:0);
        if(!SettlementTerritory.validChunk(x,z) || !level.hasChunk(x,z) || !level.getWorldBorder().isWithinBounds(new ChunkPos(x,z))
                || town.territory().contains(x,z) || !town.territory().adjacent(x,z) || data.reserved(registry,town,x,z) || town.territory().plots().size()>=town.territory().capacity()) {state.status="Expansion candidate protected, occupied, unloaded or at capacity";return false;}
        // Claim and debit serialize together; registry footprint is reconstructed from this record on startup.
        if(!town.economy().withdraw(cost)) return false;
        town.territory().claim(x,z,false);
        int y=SectionPos.blockToSectionCoord(town.center().getY());
        for(int dy=-1;dy<=1;dy++) state.sections.add(SectionPos.asLong(x,y+dy,z));
        state.expansions++;state.siteCursor=0;state.observations=0;state.status="Paid boundary expansion: "+x+", "+z;
        data.claimed(registry,town);return true;
    }

    static void assignBuilder(ServerLevel level,SettlementRecord town,SettlementArea site) {
        if(site==null || town.duties().keySet().stream().anyMatch(id -> level.getEntity(id) instanceof Villager v
                && SettlementConstructionService.eligible(level,town,v))) return;
        var id=UUID.nameUUIDFromBytes((site.id()+":builders").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var area=new SettlementArea(id,SettlementAreaType.CONSTRUCTION,"Village builders",site.min(),site.min(),5,true);
        if(!town.putArea(town.leader(),area)) return;
        var residents=VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(),level.getGameTime());
        for(var member:residents) {
            if(level.getEntity(member.id()) instanceof Villager v && !town.duties().containsKey(v.getUUID())
                    && SettlementWorkforceService.fillAssignment(level,town,v,area.id())) return;
        }
        // Keep the last food worker. Other idle civic facilities can lend an adult to the queued project.
        for(var member:residents) {
            if(!(level.getEntity(member.id()) instanceof Villager v) || v.isBaby() || !v.isAlive() || SettlementWorkforceService.hasPersonalCommitment(v)) continue;
            var current=town.workplace(town.duties().get(v.getUUID()));
            if(current==null || current.type()==SettlementAreaType.CONSTRUCTION || town.activeProject(v.getUUID())!=null) continue;
            if(current.type().foodProduction() && town.duties().values().stream().map(town::workplace).filter(Objects::nonNull).filter(a->a.type().foodProduction()).count()<=1) continue;
            town.removeDuty(v.getUUID());town.returnToAutomaticStaffing(town.leader(),v.getUUID());
            if(SettlementWorkforceService.fillAssignment(level,town,v,area.id())) return;
        }
    }

    static void complete(ServerLevel level,SettlementRecord town) {
        var state=town.autonomous();
        for(var entry:List.copyOf(state.projects.entrySet())) {
            var project=town.project(entry.getKey());
            if(project==null || project.status()!=SettlementProject.Status.COMPLETED) continue;
            var staging=UUID.nameUUIDFromBytes((project.area()+":builders").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var workers=town.duties().entrySet().stream().filter(duty->duty.getValue().equals(staging)).map(Map.Entry::getKey).toList();
            if(town.removeArea(town.leader(),staging)) workers.forEach(worker->town.returnToAutomaticStaffing(town.leader(),worker));
            if(project.repairOf()==null) {
            var area=new SettlementArea(UUID.nameUUIDFromBytes((project.id()+":facility").getBytes(java.nio.charset.StandardCharsets.UTF_8)),entry.getValue(),project.label().substring(0,Math.min(48,project.label().length())),project.min(),project.max(),5,true);
            if(!town.putArea(town.leader(),area)) {state.status="Completed structure awaiting facility registration";continue;}
            SettlementBuildingService.enqueue(level,town,area);
            }
            if(project.repairOf()==null) state.completed++;
            else state.structures.remove(project.repairOf());
            state.structures.put(project.id(),entry.getValue());state.projects.remove(entry.getKey());town.changed();
        }
    }

    static void configureTrade(SettlementRecord town,AutonomousVillageRules.Rules rules) {
        if(!town.economy().orders().isEmpty()) return;
        var bread=net.minecraft.resources.ResourceLocation.parse("minecraft:bread");
        town.economy().putOrder(new SettlementTradeOrder(UUID.nameUUIDFromBytes((town.id()+":bread").getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                SettlementTradeOrder.Direction.BUY,bread,rules.population()*3,CurrencyAmount.of(rules.foodPrice(),1),CurrencyAmount.of(rules.dailyBudget(),1),true));
        for(String item:List.of("minecraft:wheat","minecraft:carrot","minecraft:potato")) {
            var id=net.minecraft.resources.ResourceLocation.parse(item);
            town.economy().putOrder(new SettlementTradeOrder(UUID.nameUUIDFromBytes((town.id()+":"+item).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    SettlementTradeOrder.Direction.SELL,id,rules.population()*16,CurrencyAmount.ZERO,CurrencyAmount.ZERO,true));
        }
    }
    private static void repairTreasury(ServerLevel level,SettlementRecord town) {
        for(var entry:town.economy().bank.stocks.entrySet()) {
            var stock=entry.getValue();
            if(!stock.registered || !level.hasChunkAt(stock.position) || !(level.getBlockEntity(stock.position) instanceof com.jvn.villagerretaliation.storage.StorageBlockEntity bank)
                    || !bank.identity.equals(entry.getKey()) || !bank.controller() || bank.damage==0 || bank.cooling() || bank.fuse>=0) continue;
            BankTreasury.repair(level,town,bank);return;
        }
    }
    static void survey(ServerLevel level,SettlementRecord town) {
        var plots=town.autonomous().plots(town); if(plots.isEmpty()) return;
        var chunk=new ChunkPos(plots.get(Math.floorMod(town.autonomous().surveyCursor++,plots.size())));
        if(!level.hasChunk(chunk.x,chunk.z)) return;
        var pois=level.getPoiManager().getInChunk(type->true,chunk,PoiManager.Occupancy.ANY).limit(16).toList();
        for(var poi:pois) {
            var pos=poi.getPos();if(!town.contains(pos)) continue;
            var type=poi.getPoiType();
            SettlementAreaType kind=type.is(PoiTypes.HOME)?SettlementAreaType.HOME:type.is(PoiTypes.FARMER)?SettlementAreaType.FARM
                    :type.is(PoiTypes.LIBRARIAN)?SettlementAreaType.TRADING_POST:type.is(PoiTypes.BUTCHER)?SettlementAreaType.KITCHEN:type.is(PoiTypes.TOOLSMITH)?SettlementAreaType.WORKSHOP:null;
            if(kind==null || town.areas().values().stream().anyMatch(area->area.contains(pos))
                    || town.projects().values().stream().anyMatch(project->project.workplace()!=null && project.workplace().contains(pos))) continue;
            int radius=kind==SettlementAreaType.FARM || kind==SettlementAreaType.HOME?4:2;
            var area=new SettlementArea(UUID.nameUUIDFromBytes((town.id()+":"+pos.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    kind,"Village "+kind.name().toLowerCase(Locale.ROOT),pos.offset(-radius,-1,-radius),pos.offset(radius,3,radius),5,true);
            if(town.putArea(town.leader(),area)) SettlementBuildingService.enqueue(level,town,area);
        }
    }

    public static String donateStorage(ServerPlayer player,SettlementRecord town,BlockPos pos) {
        var level=player.serverLevel();
        if(town==null || town.autonomous()==null || !town.dimension().equals(level.dimension().location()) || !town.contains(pos)
                || !player.isAlive() || player.isSpectator() || player.distanceToSqr(pos.getCenter())>64 || !level.hasChunkAt(pos)) return "Storage unavailable";
        if(level.getBlockEntity(pos) instanceof com.jvn.villagerretaliation.storage.StorageBlockEntity bank) {
            if(!bank.controller() || bank.treasury!=null || !bank.allowed(player) || !com.jvn.villagerretaliation.storage.StorageKeys.carries(player,bank.identity)) return "Carry the matching key to donate an unregistered safe or controller";
            if (!BankTreasury.register(level,town,bank)) return "Treasury registration blocked";
            com.jvn.villagerretaliation.progress.ProgressHistoryService.contribution(level,player.getUUID(),town.id(),"storage","",1);
            return "Treasury donated; normal withdrawals now require village leadership";
        }
        if(level.getBlockEntity(pos) instanceof com.jvn.villagerretaliation.block.SettlementDepotBlockEntity block && block.inventory()==null) {
            var area=new SettlementArea(UUID.randomUUID(),SettlementAreaType.DEPOT,"Village supplies",pos,pos,5,true);
            if(!town.putArea(town.leader(),area)) return "Depot site blocked";
            var depot=town.registerDepot(town.leader(),area.id(),pos,SettlementDepotPurpose.GENERAL);
            if(depot==null) return "Depot capacity reached";
            block.bind(town.id(),depot.id());SettlementDepotService.observeRules(level,depot);
            com.jvn.villagerretaliation.progress.ProgressHistoryService.contribution(level,player.getUUID(),town.id(),"storage","",1);
            return "Depot donated; residents can use permitted supplies and outputs";
        }
        return "Donate an unbound settlement depot, safe or vault controller";
    }
    public static void rulerDied(ServerLevel level,UUID id) {
        var town=SettlementSavedData.get(level).settlements().stream().filter(value->value.autonomous()!=null && id.equals(value.autonomous().ruler)).findFirst().orElse(null);
        if(town!=null) {town.autonomous().nextPlan=level.getGameTime();AutonomousVillageSuccession.update(level,town);}
    }
    public static String describe(ServerLevel level,SettlementRecord town) {
        var state=town.autonomous();if(state==null) return "Player-led settlement";
        String ruler=state.ruler==null?"vacant":com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get(level).knownName(state.ruler).orElse(state.ruler.toString());
        return "Village: "+town.id()+" | Ruler: "+ruler+" | "+state.status+" | Next: "+state.nextAction+" | Physical: "+town.economy().physicalBalance().mixedFraction()
                +"; spendable: "+town.economy().balance().mixedFraction()+"; virtual change: "+town.economy().virtualBalance().mixedFraction()+"; pending deposit (not spendable): "+town.economy().pendingDeposit().mixedFraction()
                +"; reserve: "+town.economy().reserve().mixedFraction()+" | Depots: "+town.depots().size()+"; food days: "+SettlementFoodService.reserveDays(town,town.residents().size())
                +" | Plots: "+town.territory().plots().size()+"; paid expansions: "+state.expansions+"; completed improvements: "+state.completed;
    }
}
