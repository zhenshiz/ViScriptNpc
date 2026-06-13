package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.editor.ui.view.InspectorView;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.page.INpcEditorPage;

public class NpcInspectorView extends InspectorView implements INpcEditorSlotView {
    public NpcInspectorView(NpcEditor editor) {
        super(editor);
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, INpcEditorPage page) {
        page.inspect(editor, project, this);
    }
}
