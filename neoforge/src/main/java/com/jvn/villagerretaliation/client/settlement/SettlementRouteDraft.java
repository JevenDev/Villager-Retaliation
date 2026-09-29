package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

public final class SettlementRouteDraft {
    private final HiredVillagerRole role;
    private final List<BlockPos> nodes = new ArrayList<>();
    private final List<HiredRoute.Branch> branches = new ArrayList<>();
    private SettlementGuardOrder.Mode mode;
    private boolean loop;
    private int selected = -1;
    private List<BlockPos> dragNodes;
    private List<HiredRoute.Branch> dragBranches;
    private boolean insert;
    private boolean branch;

    public SettlementRouteDraft(HiredVillagerRole role, SettlementGuardOrder route) {
        this.role = role;
        this.mode = route == null ? SettlementGuardOrder.Mode.PATROL : route.mode();
        if (route != null) {
            this.nodes.addAll(route.points());
            this.branches.addAll(route.branches());
            this.loop = route.loop();
        }
    }

    public List<BlockPos> nodes() { return List.copyOf(this.nodes); }
    public List<HiredRoute.Branch> branches() { return List.copyOf(this.branches); }
    public int selected() { return this.selected; }
    public boolean loop() { return this.loop; }
    public boolean dragging() { return this.dragNodes != null; }
    public boolean inserting() { return this.insert; }
    public boolean branching() { return this.branch; }

    public List<BlockPos> handles() {
        var handles = new ArrayList<>(this.nodes);
        for (var branch : this.branches) { handles.add(branch.anchor()); handles.add(branch.end()); }
        return handles;
    }

    public SettlementGuardOrder route() {
        return new SettlementGuardOrder(this.mode, this.nodes, this.loop, this.branches);
    }

    public String validation() {
        if (this.mode != SettlementGuardOrder.Mode.PATROL && !this.branches.isEmpty()) return "Add a second route node before branching";
        if (this.nodes.size() == 1 && this.mode == SettlementGuardOrder.Mode.PATROL) return "Place at least two route nodes";
        if (new java.util.HashSet<>(this.nodes).size() != this.nodes.size()) return "Route nodes must have different positions";
        if (this.loop && (this.nodes.size() < 2 || !HiredRoute.canConnect(this.nodes.getLast(), this.nodes.getFirst())))
            return "The closing loop must be within 16 blocks";
        var shared = new HiredRoute(this.nodes, this.loop, this.branches);
        if (!shared.branches().equals(this.branches)) return "Branches must attach to the route and extend at most 16 blocks";
        if (this.role == HiredVillagerRole.COURIER && !shared.equals(shared.validatedChain())) return "Courier nodes must connect within 16 blocks";
        return "";
    }

    public void select(int index) {
        cancelDrag();
        if (index >= 0 && index < handles().size()) this.selected = index;
    }

    public void beginDrag(int index) {
        select(index);
        if (index >= 0 && index < handles().size()) {
            this.dragNodes = List.copyOf(this.nodes);
            this.dragBranches = List.copyOf(this.branches);
        }
    }

    public void dragTo(BlockPos position) {
        if (dragging() && position != null) {
            restoreDrag();
            replace(this.selected, position.immutable());
        }
    }

    public boolean finishDrag(Predicate<BlockPos> permitted) {
        if (!dragging()) return true;
        boolean valid = validation().isEmpty() && handles().stream().allMatch(permitted);
        if (!valid) restoreDrag();
        this.dragNodes = null;
        this.dragBranches = null;
        return valid;
    }

    public void cancelDrag() {
        if (dragging()) restoreDrag();
        this.dragNodes = null;
        this.dragBranches = null;
    }

    private void restoreDrag() {
        this.nodes.clear();
        this.nodes.addAll(this.dragNodes);
        this.branches.clear();
        this.branches.addAll(this.dragBranches);
    }

    private void replace(int index, BlockPos position) {
        if (index < this.nodes.size()) {
            var previous = this.nodes.set(index, position);
            this.branches.replaceAll(branch -> branch.anchor().equals(previous) ? new HiredRoute.Branch(position, branch.end()) : branch);
        }
        else {
            int branchIndex = (index - this.nodes.size()) / 2;
            var old = this.branches.get(branchIndex);
            this.branches.set(branchIndex, (index - this.nodes.size()) % 2 == 0
                    ? new HiredRoute.Branch(position, old.end()) : new HiredRoute.Branch(old.anchor(), position));
        }
    }

    public void insertAfterSelection() { this.insert = !this.insert; this.branch = false; }
    public void branchFromSelection() { this.branch = !this.branch; this.insert = false; }

    public String place(BlockPos position) {
        if (this.branch) {
            if (this.selected < 0 || this.selected >= this.nodes.size()) return "Select a route node as the branch anchor";
            if (this.branches.size() >= HiredRoute.MAX_BRANCHES) return "Branch limit reached";
            var candidate = new HiredRoute.Branch(this.nodes.get(this.selected), position);
            if (candidate.anchor().equals(position) || !HiredRoute.canConnect(candidate.anchor(), position)) return "Place the branch within 16 blocks of its anchor";
            if (this.branches.contains(candidate)) return "This branch already exists";
            this.branches.add(candidate);
            this.branch = false;
            return "";
        }
        if (this.nodes.size() >= HiredRoute.MAX_NODES) return "Route node limit reached";
        if (this.nodes.contains(position)) return "Select or drag the existing node";
        int index = this.insert && this.selected >= 0 && this.selected < this.nodes.size() ? this.selected + 1 : this.nodes.size();
        this.nodes.add(index, position.immutable());
        this.selected = index;
        this.insert = false;
        if (this.nodes.size() > 1) this.mode = SettlementGuardOrder.Mode.PATROL;
        return validation();
    }

    public void remove() {
        cancelDrag();
        if (this.selected < 0) return;
        if (this.selected < this.nodes.size()) this.nodes.remove(this.selected);
        else this.branches.remove((this.selected - this.nodes.size()) / 2);
        this.selected = Math.min(this.selected, handles().size() - 1);
        if (this.nodes.isEmpty()) clear();
    }

    public void reorder(int delta) {
        cancelDrag();
        int destination = this.selected + delta;
        if (this.selected < 0 || this.selected >= this.nodes.size() || destination < 0 || destination >= this.nodes.size()) return;
        java.util.Collections.swap(this.nodes, this.selected, destination);
        this.selected = destination;
    }

    public void toggleLoop() { this.loop = !this.loop; }

    public void clear() {
        this.nodes.clear();
        this.branches.clear();
        this.selected = -1;
        this.dragNodes = null;
        this.dragBranches = null;
        this.mode = SettlementGuardOrder.Mode.PATROL;
        this.loop = false;
        this.insert = false;
        this.branch = false;
    }
}
