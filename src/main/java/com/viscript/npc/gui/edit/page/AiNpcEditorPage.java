package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.editor.NpcFlowGraphView;
import com.viscript.npc.gui.edit.view.NpcAiSourceView;
import com.viscript.npc.gui.edit.view.NpcAiDebugView;

@LDLRegisterClient(name = "ai", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 800)
public class AiNpcEditorPage extends SimpleNpcEditorPage {
    public AiNpcEditorPage() {
        super(Icons.NODE);
    }

    @Override
    public View createCenterView(NpcEditor editor, NPCProject project) {
        return new NpcFlowGraphView(project);
    }

    @Override
    public View createLeftView(NpcEditor editor, NPCProject project) {
        return new NpcAiSourceView(editor, project);
    }

    @Override
    public View createBottomView(NpcEditor editor, NPCProject project) {
        return new NpcAiDebugView(project);
    }
}
