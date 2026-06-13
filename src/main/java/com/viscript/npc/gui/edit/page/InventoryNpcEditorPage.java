package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;

@LDLRegisterClient(name = "inventory", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 600)
public class InventoryNpcEditorPage extends SimpleNpcEditorPage {
    public InventoryNpcEditorPage() {
        super(Icons.RESOURCE);
    }

    @Override
    public View createCenterView(NpcEditor editor, NPCProject project) {
        return editor.getPreviewView();
    }
}
