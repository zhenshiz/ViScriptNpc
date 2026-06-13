package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.view.InspectorView;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;

public abstract class SimpleNpcEditorPage implements INpcEditorPage {
    private final IGuiTexture icon;
    private final boolean useDefaultLeftView;
    private final boolean useDefaultCenterPreviewView;
    private final boolean useDefaultRightInspectorView;

    protected SimpleNpcEditorPage(IGuiTexture icon) {
        this(icon, false, false, true);
    }

    protected SimpleNpcEditorPage(IGuiTexture icon, boolean useDefaultCenterPreviewView, boolean useDefaultLeftView) {
        this(icon, useDefaultCenterPreviewView, useDefaultLeftView, true);
    }

    protected SimpleNpcEditorPage(IGuiTexture icon, boolean useDefaultCenterPreviewView, boolean useDefaultLeftView, boolean useDefaultRightInspectorView) {
        this.icon = icon;
        this.useDefaultCenterPreviewView = useDefaultCenterPreviewView;
        this.useDefaultLeftView = useDefaultLeftView;
        this.useDefaultRightInspectorView = useDefaultRightInspectorView;
    }

    @Override
    public IGuiTexture icon() {
        return icon;
    }

    @Override
    public View createLeftView(NpcEditor editor, NPCProject project) {
        return useDefaultLeftView ? editor.getNpcListView() : null;
    }

    @Override
    public View createCenterView(NpcEditor editor, NPCProject project) {
        if (useDefaultCenterPreviewView) {
            return editor.getPreviewView();
        }
        return editor.createPageContentView(this, project);
    }

    @Override
    public InspectorView createInspectorView(NpcEditor editor, NPCProject project) {
        if (!useDefaultRightInspectorView) {
            return null;
        }
        return INpcEditorPage.super.createInspectorView(editor, project);
    }
}
