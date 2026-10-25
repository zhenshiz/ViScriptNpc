package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;

@LDLRegisterClient(name = "ai", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 800)
public class AiNpcEditorPage extends SimpleNpcEditorPage {
    public AiNpcEditorPage() {
        super(Icons.NODE);
    }

    @Override public com.lowdragmc.lowdraglib2.editor.ui.View createCenterView(
            com.viscript.npc.gui.edit.NpcEditor editor, com.viscript.npc.gui.edit.NPCProject project) {
        project.snapshotAi();
        var ai = project.npc.getNpcData(com.viscript.npc.npc.data.ai.NpcAI.class);
        var view = new com.viscript.npc.gui.ai.AiEditorView(editor, "tree", project.aiGraph, ai.getProgram());
        editor.getPageState(this, com.viscript.npc.gui.ai.AiParametersView.class, () -> view.aiView.parametersView);
        project.aiGraphSnapshot = view::serializeGraph;
        project.aiCompiler = view::compileProgram;
        return view;
    }

    @Override public com.lowdragmc.lowdraglib2.editor.ui.View createRightView(
            com.viscript.npc.gui.edit.NpcEditor editor, com.viscript.npc.gui.edit.NPCProject project) {
        return editor.getPageState(this, com.viscript.npc.gui.ai.AiParametersView.class, com.viscript.npc.gui.ai.AiParametersView::new);
    }
    @Override public com.lowdragmc.lowdraglib2.editor.ui.View createBottomView(
            com.viscript.npc.gui.edit.NpcEditor editor, com.viscript.npc.gui.edit.NPCProject project) { return null; }
}
