package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class SettlementNegotiatedTrade {
    public enum State { OFFERED, ACCEPTED, CANCELLING, DECLINED, CANCELLED, COMPLETED, PARTIAL, FAILED }
    public enum Custody { RESERVED, ONBOARD, CLOSED }
    SettlementExchangeTerms terms;
    VillageAllegianceId proposer;
    State state = State.OFFERED;
    int counters;
    long routeRevision;
    UUID firstAuthority, secondAuthority, courier;
    Cargo first, second;
    String explanation = "";
    CurrencyAmount transport = CurrencyAmount.ZERO;

    SettlementNegotiatedTrade(SettlementExchangeTerms terms, VillageAllegianceId proposer) {
        this.terms = terms; this.proposer = proposer;
    }
    public SettlementExchangeTerms terms() { return terms; }
    public State state() { return state; }
    public int countersRemaining() { return 3 - counters; }
    boolean counter(SettlementExchangeTerms next, VillageAllegianceId sender) {
        if (state != State.OFFERED || counters >= 3 || proposer.equals(sender)) return false;
        terms = next; proposer = sender; counters++; firstAuthority = null; secondAuthority = null; return true;
    }
    boolean unresolved() { return first != null && (!first.empty() || !second.empty()); }
    boolean loss() { return first != null && (first.loss() || second.loss()); }
    SettlementFoodOffer.Arrangement arrangement() {
        return terms.firstSends() ? SettlementFoodOffer.Arrangement.DONOR_DELIVERY : SettlementFoodOffer.Arrangement.REQUESTER_COLLECTION;
    }
    Cargo onboard() {
        if (first != null && first.custody == Custody.ONBOARD) return first;
        return second != null && second.custody == Custody.ONBOARD ? second : null;
    }

    static final class Cargo {
        final SettlementExchangeTerms.Bundle original;
        final List<ItemStack> remaining, delivered = new ArrayList<>(), returned = new ArrayList<>(), lost = new ArrayList<>();
        CurrencyAmount funds, deliveredFunds = CurrencyAmount.ZERO, returnedFunds = CurrencyAmount.ZERO, lostFunds = CurrencyAmount.ZERO;
        Custody custody = Custody.RESERVED;
        Cargo(SettlementExchangeTerms.Bundle original) {
            this.original = original; this.remaining = new ArrayList<>(original.items()); funds = original.funds();
        }
        boolean empty() { return remaining.isEmpty() && funds.isZero(); }
        boolean loss() { return !lost.isEmpty() || !lostFunds.isZero(); }
        CompoundTag save(HolderLookup.Provider provider) {
            var tag = new CompoundTag(); tag.put("Original", original.save(provider)); tag.putString("Custody", custody.name());
            tag.put("Remaining", new SettlementExchangeTerms.Bundle(remaining, funds).save(provider));
            tag.put("Delivered", bundle(delivered, deliveredFunds, provider));
            tag.put("Returned", bundle(returned, returnedFunds, provider));
            tag.put("Lost", bundle(lost, lostFunds, provider)); return tag;
        }
        private static CompoundTag bundle(List<ItemStack> items, CurrencyAmount funds, HolderLookup.Provider provider) {
            var tag = new CompoundTag(); var list = new ListTag();
            items.forEach(item -> list.add(item.save(provider))); tag.put("Items", list); tag.put("Funds", funds.save()); return tag;
        }
        static Cargo load(CompoundTag tag, HolderLookup.Provider provider) {
            var result = new Cargo(SettlementExchangeTerms.Bundle.load(tag.getCompound("Original"), provider));
            result.custody = Custody.valueOf(tag.getString("Custody"));
            var remaining = SettlementExchangeTerms.Bundle.load(tag.getCompound("Remaining"), provider);
            result.remaining.clear(); result.remaining.addAll(remaining.items()); result.funds = remaining.funds();
            result.deliveredFunds = readAudit(tag.getCompound("Delivered"), result.delivered, provider);
            result.returnedFunds = readAudit(tag.getCompound("Returned"), result.returned, provider);
            result.lostFunds = readAudit(tag.getCompound("Lost"), result.lost, provider);
            var available = new ArrayList<>(result.original.items());
            var accounted = new ArrayList<>(result.remaining); accounted.addAll(result.delivered); accounted.addAll(result.returned); accounted.addAll(result.lost);
            for (var item : accounted) {
                int count = item.getCount();
                for (var source : available) if (ItemStack.isSameItemSameComponents(item, source)) {
                    int taken = Math.min(count, source.getCount()); source.shrink(taken); count -= taken;
                }
                if (count != 0) throw new IllegalArgumentException("Exchange cargo exceeds its reservation");
            }
            if (available.stream().anyMatch(item -> !item.isEmpty())
                    || !result.funds.add(result.deliveredFunds).add(result.returnedFunds).add(result.lostFunds).equals(result.original.funds())
                    || result.custody == Custody.CLOSED && !result.empty())
                throw new IllegalArgumentException("Unaccounted exchange cargo");
            return result;
        }
        private static CurrencyAmount readAudit(CompoundTag tag, List<ItemStack> target, HolderLookup.Provider provider) {
            var list = tag.getList("Items", Tag.TAG_COMPOUND);
            if (!list.equals(tag.get("Items")) || list.size() > 1728) throw new IllegalArgumentException("Invalid exchange audit");
            for (var raw : list) {
                var item = ItemStack.parse(provider, (CompoundTag) raw).orElseThrow(() -> new IllegalArgumentException("Unreadable exchange audit"));
                if (item.isEmpty() || item.getCount() > item.getMaxStackSize()) throw new IllegalArgumentException("Invalid audit stack");
                target.add(item);
            }
            var amount = CurrencyAmount.load(tag.getCompound("Funds"));
            if (!amount.save().equals(tag.get("Funds"))) throw new IllegalArgumentException("Invalid audit funds");
            return amount;
        }
    }
    CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag(); tag.putInt("Version", 1); tag.put("Terms", terms.save(provider));
        tag.putUUID("Proposer", proposer.value()); tag.putString("State", state.name()); tag.putInt("Counters", counters);
        tag.putLong("RouteRevision", routeRevision);
        tag.putString("Explanation", explanation); tag.put("Transport", transport.save());
        if (firstAuthority != null) tag.putUUID("FirstAuthority", firstAuthority);
        if (secondAuthority != null) tag.putUUID("SecondAuthority", secondAuthority);
        if (courier != null) tag.putUUID("Courier", courier);
        if (first != null) { tag.put("FirstCargo", first.save(provider)); tag.put("SecondCargo", second.save(provider)); }
        return tag;
    }
    static SettlementNegotiatedTrade load(CompoundTag tag, HolderLookup.Provider provider, SettlementFoodRequest request) {
        if (tag.getInt("Version") != 1 || !tag.hasUUID("Proposer") || !tag.contains("Counters", Tag.TAG_INT))
            throw new IllegalArgumentException("Unsupported exchange");
        var result = new SettlementNegotiatedTrade(SettlementExchangeTerms.load(tag.getCompound("Terms"), provider),
                new VillageAllegianceId(tag.getUUID("Proposer")));
        result.state = State.valueOf(tag.getString("State")); result.counters = tag.getInt("Counters");
        result.routeRevision = tag.getLong("RouteRevision");
        result.explanation = tag.getString("Explanation"); result.transport = CurrencyAmount.load(tag.getCompound("Transport"));
        if (tag.hasUUID("FirstAuthority")) result.firstAuthority = tag.getUUID("FirstAuthority");
        if (tag.hasUUID("SecondAuthority")) result.secondAuthority = tag.getUUID("SecondAuthority");
        if (tag.hasUUID("Courier")) result.courier = tag.getUUID("Courier");
        if (tag.contains("FirstCargo")) {
            result.first = Cargo.load(tag.getCompound("FirstCargo"), provider); result.second = Cargo.load(tag.getCompound("SecondCargo"), provider);
            if (!result.first.original.save(provider).equals(result.terms.first().save(provider))
                    || !result.second.original.save(provider).equals(result.terms.second().save(provider)))
                throw new IllegalArgumentException("Exchange reservation differs from agreed terms");
        }
        if (!result.transport.save().equals(tag.get("Transport"))) throw new IllegalArgumentException("Invalid transport value");
        if (result.routeRevision < 0 || result.counters < 0 || result.counters > 3 || result.explanation.length() > 512
                || !result.proposer.equals(request.donor()) && !result.proposer.equals(request.requester())
                || result.state == State.OFFERED && (result.first != null || request.journey != null)
                || (result.state == State.ACCEPTED || result.state == State.CANCELLING || result.state == State.COMPLETED || result.state == State.PARTIAL || result.state == State.FAILED)
                && (result.first == null || result.firstAuthority == null || result.secondAuthority == null)
                || request.terminal() && result.unresolved())
            throw new IllegalArgumentException("Invalid exchange lifecycle");
        if (request.journey != null && (request.preparation == null || result.first == null || result.courier == null || !result.courier.equals(request.journey.resident)
                || !request.id().equals(request.journey.id) || request.journey.revision > request.revision
                || !request.journey.home.equals(result.terms.firstSends() ? request.donor() : request.requester())))
            throw new IllegalArgumentException("Invalid exchange journey");
        return result;
    }
}
