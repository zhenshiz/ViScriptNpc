package com.viscript.npc.gui.edit.view;

import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.page.INpcEditorPage;

public interface INpcEditorSlotView {
    default void onViewSelected(NpcEditor editor, NPCProject project, INpcEditorPage page) {
    }
}
