package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InteractionMenu(SessionRef session, UUID id, long revision, String title, String text,
        List<DialogueSpeech> exchanges, List<Choice> choices) {
    public InteractionMenu {
        Objects.requireNonNull(session);
        Objects.requireNonNull(id);
        if (revision < 1) throw new IllegalArgumentException("Menu revision must be positive");
        title = Objects.requireNonNullElse(title, "");
        text = Objects.requireNonNullElse(text, "");
        exchanges = List.copyOf(exchanges);
        choices = List.copyOf(choices);
    }

    public record SessionRef(UUID id, UUID actorId, int entityId) {
        public SessionRef {
            Objects.requireNonNull(id);
            Objects.requireNonNull(actorId);
        }

        public static SessionRef of(InteractionSession session) {
            return new SessionRef(session.id(), session.actorId(), session.entityId());
        }
    }

    public record Choice(String id, String label, boolean available, String unavailableReason, UUID token) {
        public Choice(String id, String label, boolean available, String unavailableReason) {
            this(id, label, available, unavailableReason, null);
        }

        public Choice {
            Objects.requireNonNull(id);
            label = Objects.requireNonNullElse(label, "");
            unavailableReason = Objects.requireNonNullElse(unavailableReason, "");
        }

        public Choice withToken(UUID token) { return new Choice(this.id, this.label, this.available, this.unavailableReason, token); }
    }

    public record Content(String title, String text, List<DialogueSpeech> exchanges, List<Choice> choices) {
        public Content(String title, List<Choice> choices) { this(title, "", List.of(), choices); }

        public Content {
            title = Objects.requireNonNullElse(title, "");
            text = Objects.requireNonNullElse(text, "");
            exchanges = List.copyOf(exchanges);
            choices = List.copyOf(choices);
            var ids = new HashSet<String>();
            for (var choice : choices) if (choice.id().isBlank() || !ids.add(choice.id()))
                throw new IllegalArgumentException("Interaction choice ids must be nonempty and unique");
        }
    }

    public record Selection(SessionRef session, UUID menuId, long revision, String choiceId, UUID token) {
        public Selection {
            Objects.requireNonNull(session);
            Objects.requireNonNull(menuId);
            Objects.requireNonNull(choiceId);
            Objects.requireNonNull(token);
        }
    }
}
