package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;

@LDLRegisterClient(name = "basic", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 1000)
public class BasicNpcEditorPage extends SimpleNpcEditorPage {
    public BasicNpcEditorPage() {
        super(Icons.SETTINGS);
    }

    @Override
    public View createLeftView(NpcEditor editor, NPCProject project) {
        return editor.getNpcListView();
    }

    @Override
    public View createCenterView(NpcEditor editor, NPCProject project) {
        return editor.getPreviewView();
    }
}
