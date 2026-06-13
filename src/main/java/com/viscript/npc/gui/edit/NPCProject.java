package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.gui.edit.npc.NPC;
import com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript_lib.gui.editor.EditorFileFormat;
import com.viscript_lib.gui.editor.IRuntimeFileProject;
import com.viscript_lib.gui.editor.ProjectFileProjectType;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class NPCProject implements IRuntimeFileProject {
    public static int VERSION = 1;
    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC;
    public static final ProjectFileProjectType PROVIDER = new ProjectFileProjectType(IGuiTexture.EMPTY, Component.translatable("editor.project.npc.add").getString(), FORMAT, NPCProject::new);

    @Getter
    private final Resources resources;
    public NPC npc = new NPC();
    public String getCurrentNpcType() {return npc.npcConfig.getNpcData(NpcBasicsSetting.class).getType();}

    public NPCProject() {
        this.resources = Resources.of(
                ColorsResource.INSTANCE,
                TexturesResource.INSTANCE,
                IRendererResource.INSTANCE
        );
    }

    @Override
    public String getVersion() {
        return "%d.0".formatted(VERSION);
    }

    @Override
    public ProjectFileProjectType getProjectType() {
        return PROVIDER;
    }

    @Override
    public CompoundTag serializeProject(@NotNull HolderLookup.Provider provider) {
        var data = new CompoundTag();
        data.put("npc", npc.serializeNBT(provider));
        return data;
    }

    @Override
    public CompoundTag serializeRuntimeFile(HolderLookup.Provider provider) {
        return npc.serializeNBT(provider);
    }

    @Override
    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        npc.deserializeNBT(provider, nbt.getCompound("npc"));
    }

    @Override
    public CompoundTag getMetadata() {
        var meta = IRuntimeFileProject.super.getMetadata();
        meta.putInt("version_num", VERSION);
        return meta;
    }
}
