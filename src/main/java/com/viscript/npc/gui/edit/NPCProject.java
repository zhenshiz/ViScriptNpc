package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.gui.edit.data.NpcConfig;
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
    public static int VERSION = 2;
    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC;
    public static final ProjectFileProjectType PROVIDER = new NpcProjectFileType(IGuiTexture.EMPTY, Component.translatable("editor.project.npc.add").getString(), FORMAT, NPCProject::new);

    @Getter
    private final Resources resources;
    public NpcConfig npc = new NpcConfig();
    public CompoundTag aiGraph = new CompoundTag();
    public java.util.function.Supplier<CompoundTag> aiGraphSnapshot;
    public java.util.function.Supplier<com.viscript.npc.npc.ai.data.AiProgram> aiCompiler;
    public java.util.function.Function<String, com.viscript.npc.npc.ai.data.AiProgram> actionResolver = com.viscript.npc.util.NpcActionFiles::loadLocal;

    public void snapshotAi() {
        if (aiGraphSnapshot != null) aiGraph = aiGraphSnapshot.get();
        if (aiCompiler != null) npc.getNpcData(com.viscript.npc.npc.data.ai.NpcAI.class).setProgram(aiCompiler.get());
    }

    public String getCurrentNpcType() {return npc.getNpcData(NpcBasicsSetting.class).getNpcId();}

    public NPCProject() {
        this.resources = Platform.isClient()
                ? Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE, IRendererResource.INSTANCE)
                : Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE);
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
        snapshotAi();
        var data = new CompoundTag();
        data.put("npc", npc.serializeNBT(provider));
        data.put("aiGraph", aiGraph.copy());
        return data;
    }

    @Override
    public CompoundTag serializeRuntimeFile(HolderLookup.Provider provider) {
        snapshotAi();
        var ai = npc.getNpcData(com.viscript.npc.npc.data.ai.NpcAI.class);
        var dependencies = com.viscript.npc.util.NpcAiPublication.bundle(ai.getProgram(), actionResolver);
        ai.setActionDefinitions(com.viscript.npc.util.NpcAiPublication.readDependencies(dependencies));
        CompoundTag data = npc.serializeNBT(provider);
        data.put(com.viscript.npc.util.NpcAiPublication.DEPENDENCIES, dependencies);
        return data;
    }

    public CompoundTag serializeNpcConfig(HolderLookup.Provider provider) {
        snapshotAi();
        return npc.serializeNBT(provider);
    }

    @Override
    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        npc.deserializeNBT(provider, nbt.getCompound("npc"));
        aiGraph = nbt.getCompound("aiGraph").copy();
    }

    @Override public void onClosed(com.lowdragmc.lowdraglib2.editor.ui.Editor editor) {
        snapshotAi(); aiGraphSnapshot = null; aiCompiler = null; actionResolver = com.viscript.npc.util.NpcActionFiles::loadLocal;
    }

    @Override
    public CompoundTag getMetadata() {
        var meta = IRuntimeFileProject.super.getMetadata();
        meta.putInt("version_num", VERSION);
        return meta;
    }

}
