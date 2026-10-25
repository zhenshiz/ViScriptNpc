package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.util.NpcEditorFiles;
import com.viscript_lib.gui.editor.EditorFileFormat;
import com.viscript_lib.gui.editor.IRuntimeFileProject;
import com.viscript_lib.gui.editor.ProjectFileProjectType;

import java.io.File;
import java.util.function.Supplier;

/** 沿用 VSL 工程格式，将 NPC 与行为的工程保存在各自分类目录中。 */
public final class NpcProjectFileType extends ProjectFileProjectType {
    public NpcProjectFileType(IGuiTexture icon, String name, EditorFileFormat format,
                              Supplier<? extends IRuntimeFileProject> creator) {
        super(icon, name, format, creator);
    }

    @Override
    public File getRootSavePath(IProject project, File projectRoot) {
        return NpcEditorFiles.projectDirectory(getFormat());
    }
}
