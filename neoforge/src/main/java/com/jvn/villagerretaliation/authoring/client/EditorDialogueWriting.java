package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;

final class EditorDialogueWriting {
    static boolean ownsWording(EditorGraph.Card card, AuthoringDocument document, String path) {
        boolean text = path.matches(".*/(?:text|line|lines|variants|label)(?:/[0-9]+)?");
        return card.blocks().stream().anyMatch(block -> text && block.pointer().equals(path)
                && block.fields().stream().anyMatch(EditorGraphInline.Field::multiline))
                || card.blocks().stream().flatMap(block -> block.fields().stream())
                .anyMatch(field -> field.multiline() && field.document() == document
                        && (field.pointer().equals(path) || text && field.pointer().startsWith(path + "/")));
    }
}
