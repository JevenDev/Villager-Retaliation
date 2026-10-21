package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.interaction.InteractionMenu;
import java.util.Optional;

public final class InteractionClientSession {
    private InteractionMenu menu;
    private boolean submitted;

    public boolean accept(InteractionMenu menu) {
        if (this.menu != null && this.menu.session().equals(menu.session()) && this.menu.revision() >= menu.revision()) return false;
        this.menu = menu;
        this.submitted = false;
        return true;
    }

    public Optional<InteractionMenu> menu() { return Optional.ofNullable(this.menu); }

    public Optional<InteractionMenu.Selection> select(String choiceId) {
        if (this.menu == null || this.submitted) return Optional.empty();
        var choice = this.menu.choices().stream().filter(value -> value.available() && value.token() != null
                && value.id().equals(choiceId)).findFirst().orElse(null);
        if (choice == null) return Optional.empty();
        this.submitted = true;
        return Optional.of(new InteractionMenu.Selection(this.menu.session(), this.menu.id(), this.menu.revision(), choice.id(), choice.token()));
    }

    public boolean close(InteractionMenu.SessionRef session) {
        if (this.menu == null || !this.menu.session().equals(session)) return false;
        this.menu = null;
        this.submitted = false;
        return true;
    }

    public void reset() { this.menu = null; this.submitted = false; }
}
