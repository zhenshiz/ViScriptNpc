package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.view.NpcBehaviorDebugView;
import com.viscript.npc.gui.edit.view.NpcBehaviorGraphView;
import com.viscript.npc.gui.edit.view.NpcBehaviorInspectorView;

@LDLRegisterClient(name = "ai", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 800)
public class AiNpcEditorPage extends SimpleNpcEditorPage {
    public AiNpcEditorPage() {
        super(Icons.NODE);
    }

    @Override
    public View createCenterView(NpcEditor editor, NPCProject project) {
        return editor.getPageState(this, NpcBehaviorGraphView.class, NpcBehaviorGraphView::new);
    }

    @Override
    public View createRightView(NpcEditor editor, NPCProject project) {
        return editor.getPageState(this, NpcBehaviorInspectorView.class, () -> new NpcBehaviorInspectorView(editor,
                () -> editor.getPageState(this, NpcBehaviorGraphView.class, NpcBehaviorGraphView::new)));
    }

    @Override
    public View createBottomView(NpcEditor editor, NPCProject project) {
        return editor.getPageState(this, NpcBehaviorDebugView.class, () -> new NpcBehaviorDebugView(editor,
                () -> editor.getPageState(this, NpcBehaviorGraphView.class, NpcBehaviorGraphView::new)));
    }
}
