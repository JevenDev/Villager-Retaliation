package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import static com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.RelationshipType.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class AutonomousVillageSuccession {
    private AutonomousVillageSuccession() {}
    static void update(ServerLevel level,SettlementRecord town) {
        var state=town.autonomous(); var graph=VillagerSocialGraphSavedData.get(level);
        var roster=VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id()).orElseThrow().residents();
        if(state.ruler!=null && !graph.confirmedDead(state.ruler) && roster.containsKey(state.ruler)) return;
        if(state.ruler!=null) { state.predecessor=state.ruler; state.ruler=null; }
        var candidates=new HashMap<UUID,Boolean>();
        for(var resident:roster.values()) {
            if(graph.confirmedDead(resident.id())) continue;
            var entity=level.getEntity(resident.id());
            if(entity instanceof Villager villager) {
                if(!villager.isAlive()) continue;
                graph.ensureProfile(level,villager);
            }
            if(graph.confirmedAlive(resident.id()) && !town.departures().containsKey(resident.id()))
                candidates.put(resident.id(),!graph.knownBaby(resident.id()).orElse(!resident.adult()));
        }
        state.ruler=choose(graph,state.predecessor,candidates,level.random);
        town.changed();
    }
    static UUID choose(VillagerSocialGraphSavedData graph,UUID previous,Map<UUID,Boolean> candidates,net.minecraft.util.RandomSource random) {
        if(previous!=null) {
            var partner=graph.successionPartners(previous,candidates.keySet()).stream().filter(candidates::containsKey).sorted().findFirst();
            if(partner.isPresent()) return partner.get();
            var descendants=walk(graph,previous,true);
            // If a pathological graph exceeds the traversal budget, wait rather than pick a lower-priority stranger.
            if(descendants==null) return null;
            var descendant=descendants.stream().filter(candidates::containsKey)
                    .sorted(Comparator.comparing((UUID id)->!candidates.get(id)).thenComparing(id->id)).findFirst();
            if(descendant.isPresent()) return descendant.get();
            var relatives=walk(graph,previous,false);
            if(relatives==null) return null;
            var relative=relatives.stream().filter(id->candidates.getOrDefault(id,false)).sorted().findFirst();
            if(relative.isPresent()) return relative.get();
        }
        var adults=candidates.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).sorted().toList();
        return adults.isEmpty()?null:adults.get(random.nextInt(adults.size()));
    }
    private static Set<UUID> walk(VillagerSocialGraphSavedData graph,UUID root,boolean descendants) {
        var visited=new HashSet<UUID>(); var queue=new ArrayDeque<UUID>(); queue.add(root); visited.add(root);
        while(!queue.isEmpty()) {
            var next=queue.removeFirst();
            var adjacent=new HashSet<>(graph.relationships(next,CHILD));
            if(!descendants) {adjacent.addAll(graph.relationships(next,PARENT));adjacent.addAll(graph.relationships(next,SIBLING));}
            for(var id:adjacent) if(visited.add(id)) { if(visited.size()>4096) return null; queue.addLast(id); }
        }
        visited.remove(root);return visited;
    }
}
