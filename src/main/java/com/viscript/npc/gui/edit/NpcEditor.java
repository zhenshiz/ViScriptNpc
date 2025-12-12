package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.view.NPCPreviewView;
import com.viscript.npc.gui.edit.view.NpcListView;
import com.viscript.npc.util.npc.NpcHelper;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NpcEditor extends Editor {
    public final static SpriteTexture ICON = SpriteTexture.of(ViScriptNpc.formattedMod("textures/icon.png"));

    public final NpcListView npcListView = new NpcListView(this);
    public final NPCPreviewView npcPreviewView = new NPCPreviewView(this);

    public NpcEditor() {
        super();
        fileMenu.addProjectProvider(NPCProject.PROVIDER);
        this.icon.style(style -> style.backgroundTexture(ICON));
    }

    @Override
    protected void loadNewProject(IProject project, @Nullable File projectFile) {
        System.out.println(project.getResources().resources);
        if (project instanceof NPCProject npcProject) {
            super.loadNewProject(project, projectFile);
            NpcHelper.cacheNpcProject = npcProject;
            this.leftWindow.getLeftTop().addView(npcListView);
            this.centerWindow.getLeftTop().addView(npcPreviewView);
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
