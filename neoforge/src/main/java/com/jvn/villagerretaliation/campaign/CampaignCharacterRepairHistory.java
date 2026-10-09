package com.jvn.villagerretaliation.campaign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class CampaignCharacterRepairHistory {
    private CampaignCharacterRepairHistory() { }

    static void validate(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings, Map<UUID, CampaignCharacterRepair> repairs) {
        var latest = new LinkedHashMap<CampaignCharacterBinding.Key, CampaignCharacterRepair>();
        var claims = new LinkedHashMap<UUID, CampaignCharacterBinding.Key>();
        bindings.values().forEach(binding -> claim(claims, binding));
        for (var repair : repairs.values()) {
            var key = repair.after().key();
            var current = bindings.get(key);
            CampaignStateCodec.require(current != null && current.sameOwner(repair.after()), "character repair has no matching role owner");
            var prior = latest.get(key);
            CampaignStateCodec.require(prior == null || CampaignCharacterRepair.sameIdentity(prior.after(), repair.before())
                    && repair.time() >= prior.time(), "character repair history is discontinuous");
            CampaignStateCodec.require(CampaignCharacterRepair.token(repair.before(), prior == null ? null : prior.operation()).equals(repair.expected()),
                    "character repair token does not match its history");
            if (repair.before() != null) claim(claims, repair.before());
            claim(claims, repair.after());
            latest.put(key, repair);
        }
        latest.forEach((key, repair) -> CampaignStateCodec.require(CampaignCharacterRepair.sameIdentity(repair.after(), bindings.get(key)),
                "character repair does not match its current binding"));
    }

    private static void claim(Map<UUID, CampaignCharacterBinding.Key> claims, CampaignCharacterBinding binding) {
        var prior = claims.putIfAbsent(binding.entityId(), binding.key());
        CampaignStateCodec.require(prior == null || prior.equals(binding.key()), "character identity belongs to multiple current or historical roles");
    }

    static CampaignCharacterBinding known(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings,
            Map<UUID, CampaignCharacterRepair> repairs, CampaignCharacterBinding.Key key, UUID entity) {
        var current = bindings.get(key);
        if (current != null && current.entityId().equals(entity)) return current;
        for (var repair : repairs.values()) {
            if (!repair.after().key().equals(key)) continue;
            if (repair.before() != null && repair.before().entityId().equals(entity)) return repair.before();
            if (repair.after().entityId().equals(entity)) return repair.after();
        }
        return null;
    }
}
