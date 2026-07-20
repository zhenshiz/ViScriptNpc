package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.view.NpcAttributesComparisonView;
import com.viscript.npc.gui.edit.view.NpcAttributesPageState;
import com.viscript.npc.gui.edit.view.NpcAttributesReferenceView;

@LDLRegisterClient(name = "attributes", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 700)
public class AttributesNpcEditorPage extends SimpleNpcEditorPage {
    public AttributesNpcEditorPage() {
        super(Icons.EDIT_ON);
    }

    @Override
    public View createLeftView(NpcEditor editor, NPCProject project) {
        return new NpcAttributesReferenceView(editor, pageState(editor));
    }

    @Override
    public View createCenterView(NpcEditor editor, NPCProject project) {
        return new NpcAttributesComparisonView(editor, pageState(editor));
    }

    @Override
    public void onInspectorConfiguratorChanged(NpcEditor editor, NPCProject project, Configurator configurator) {
        pageState(editor).notifyChanged();
    }

    private NpcAttributesPageState pageState(NpcEditor editor) {
        return editor.getPageState(this, NpcAttributesPageState.class, NpcAttributesPageState::new);
    }
}
