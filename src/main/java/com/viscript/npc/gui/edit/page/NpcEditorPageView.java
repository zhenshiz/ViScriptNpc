package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.viscript.npc.gui.edit.NpcEditor;

public class NpcEditorPageView extends UIElement {
    protected final NpcEditor editor;
    protected final INpcEditorPage page;

    public NpcEditorPageView(NpcEditor editor, INpcEditorPage page) {
        this.editor = editor;
        this.page = page;
        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
    }
}
