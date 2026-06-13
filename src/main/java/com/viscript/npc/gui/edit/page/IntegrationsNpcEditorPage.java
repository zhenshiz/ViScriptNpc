package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;

@LDLRegisterClient(name = "integrations", group = INpcEditorPage.TRANSLATION_GROUP, registry = INpcEditorPage.ID, priority = 500)
public class IntegrationsNpcEditorPage extends SimpleNpcEditorPage {
    public IntegrationsNpcEditorPage() {
        super(Icons.LINK);
    }
}
