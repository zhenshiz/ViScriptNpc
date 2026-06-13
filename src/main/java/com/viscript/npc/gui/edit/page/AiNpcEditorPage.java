package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;

@LDLRegisterClient(name = "ai", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 800)
public class AiNpcEditorPage extends SimpleNpcEditorPage {
    public AiNpcEditorPage() {
        super(Icons.NODE);
    }
}
