package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NpcEditor;

public class NpcListView extends View {
    public final NpcEditor editor;
    public final ScrollerView scrollerView = new ScrollerView();

    public NpcListView(NpcEditor editor) {
        super(ViScriptNpc.formattedMod("%s.editor.view.npcList"));
        this.editor = editor;
        this.scrollerView.layout((layout) -> {
            layout.setWidthPercent(100.0F);
            layout.setFlex(1.0F);
        });
        this.addChild(this.scrollerView);
    }
}
