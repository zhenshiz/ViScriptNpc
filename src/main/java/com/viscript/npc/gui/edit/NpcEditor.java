package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.view.NPCPreviewView;
import com.viscript.npc.gui.edit.view.NpcListView;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NpcEditor extends Editor {
    public final static SpriteTexture ICON = SpriteTexture.of(ViScriptNpc.formattedMod("%s:textures/icon.png"));

    public final NpcListView npcListView = new NpcListView(this);
    public final NPCPreviewView npcPreviewView = new NPCPreviewView(this);

    public NpcEditor() {
        fileMenu.addProjectProvider(NPCProject.PROVIDER);
        this.icon.style(style -> style.backgroundTexture(ICON));
        this.leftWindow.getLeftTop().addView(npcListView);
        this.centerWindow.getLeftTop().addView(npcPreviewView);
    }

    @Override
    protected void loadNewProject(IProject project, @Nullable File projectFile) {
        if (project instanceof NPCProject npcProject) {
            super.loadNewProject(project, projectFile);
            inspectorView.inspect(npcProject.npc.npcConfig);
            npcPreviewView.loadScene();
        }
    }

    @Override
    protected void closeCurrentProject() {
        super.closeCurrentProject();
        inspectorView.clear();
        npcPreviewView.clearScene();
    }
}
