package com.jvn.villagerretaliation.scene.actor;

import com.jvn.villagerretaliation.scene.actor.SceneActorBinding.ReplacementHistoryEntry;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.ReplacementPolicy;
import java.util.ArrayList;
import java.util.List;

/** Pure replacement-policy boundary; it never searches the world for a substitute. */
public final class SceneActorBindingService {
    private SceneActorBindingService() {
    }

    public static RebindResult rebind(SceneActorDeclaration declaration, SceneActorBinding current,
            SceneActorBinding replacement, RebindKind kind, long gameTime, String reason, String operatorIdentity) {
        if (declaration == null || current == null || replacement == null) {
            return RebindResult.rejected(current, "actor declaration, current binding, and replacement are required");
        }
        if (!declaration.alias().equals(current.alias()) || !current.alias().equals(replacement.alias())) {
            return RebindResult.rejected(current, "replacement alias does not match declaration " + declaration.alias());
        }
        if (!declaration.actorType().equals(replacement.actorType())) {
            return RebindResult.rejected(current, "replacement actor type " + replacement.actorType()
                    + " is incompatible with " + declaration.actorType());
        }
        ReplacementPolicy policy = declaration.replacementPolicy();
        if (policy == ReplacementPolicy.FIXED) {
            return RebindResult.rejected(current, "fixed actor " + declaration.alias() + " cannot be replaced");
        }
        if (policy == ReplacementPolicy.OPERATOR_REBINDABLE && kind != RebindKind.OPERATOR) {
            return RebindResult.rejected(current, "actor " + declaration.alias() + " requires an operator rebind");
        }
        if (policy == ReplacementPolicy.RESPAWN_IF_OWNED
                && (kind != RebindKind.OWNED_RESPAWN || declaration.bindingSource() != SceneActorDeclaration.BindingSource.OWNED_SPAWN)) {
            return RebindResult.rejected(current, "actor " + declaration.alias() + " may only be replaced by its owned respawn");
        }
        if (kind == RebindKind.OPERATOR && (operatorIdentity == null || operatorIdentity.isBlank())) {
            return RebindResult.rejected(current, "operator identity is required for an operator rebind");
        }
        if (current.targetIdentity().equals(replacement.targetIdentity())) {
            return new RebindResult(true, false, current, "binding already targets " + current.targetIdentity());
        }

        long generation = current.generation() + 1L;
        List<ReplacementHistoryEntry> history = new ArrayList<>(current.replacementHistory());
        history.add(new ReplacementHistoryEntry(current.targetIdentity(), replacement.targetIdentity(),
                current.generation(), generation, reason, gameTime, operatorIdentity));
        SceneActorBinding updated = new SceneActorBinding(current.alias(), current.actorType(), replacement.targetIdentity(),
                replacement.entityId(), replacement.sourceType(), replacement.lastDimension(), replacement.lastPosition(),
                replacement.displaySnapshot(), generation, replacement.state(), history);
        return new RebindResult(true, true, updated, "actor binding replaced");
    }

    public enum RebindKind {
        COMPATIBLE,
        OPERATOR,
        OWNED_RESPAWN
    }

    public record RebindResult(boolean accepted, boolean changed, SceneActorBinding binding, String diagnostic) {
        static RebindResult rejected(SceneActorBinding binding, String diagnostic) {
            return new RebindResult(false, false, binding, diagnostic);
        }
    }
}
