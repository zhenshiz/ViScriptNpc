package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.gui.edit.npc.NPC;
import com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript_lib.gui.editor.EditorFileFormat;
import com.viscript_lib.gui.editor.IRuntimeFileProject;
import com.viscript_lib.gui.editor.ProjectFileProjectType;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class NPCProject implements IRuntimeFileProject {
    public static int VERSION = 1;
    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC;
    public static final ProjectFileProjectType PROVIDER = new ProjectFileProjectType(IGuiTexture.EMPTY, Component.translatable("editor.project.npc.add").getString(), FORMAT, NPCProject::new);

    @Getter
    private final Resources resources;
    public NPC npc = new NPC();
    @Nullable
    private Supplier<CompoundTag> behaviorGraphSnapshotSupplier;

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
        refreshEditorSnapshots();
        var data = new CompoundTag();
        data.put("npc", npc.serializeNBT(provider));
        return data;
    }

    @Override
    public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
        refreshEditorSnapshots();
        return IRuntimeFileProject.super.serializeNBT(provider);
    }

    @Override
    public CompoundTag serializeRuntimeFile(HolderLookup.Provider provider) {
        refreshEditorSnapshots();
        CompoundTag data = npc.serializeNBT(provider);
        NpcAI ai = npc.npcConfig.getNpcData(NpcAI.class);
        if (ai != null) {
            CompoundTag aiTag = data.getCompound(ai.getConfigurableName());
            aiTag.put("behaviorProgram", ai.getCompiledBehaviorProgram());
            data.put(ai.getConfigurableName(), aiTag);
        }
        return data;
    }

    public CompoundTag serializeNpcConfig(HolderLookup.Provider provider) {
        refreshEditorSnapshots();
        return npc.npcConfig.serializeNBT(provider);
    }

    @Override
    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        npc.deserializeNBT(provider, nbt.getCompound("npc"));
    }

    @Override
    public void onClosed(Editor editor) {
        behaviorGraphSnapshotSupplier = null;
    }

    @Override
    public CompoundTag getMetadata() {
        var meta = IRuntimeFileProject.super.getMetadata();
        meta.putInt("version_num", VERSION);
        return meta;
    }

    public void setBehaviorGraphSnapshotSupplier(@Nullable Supplier<CompoundTag> behaviorGraphSnapshotSupplier) {
        this.behaviorGraphSnapshotSupplier = behaviorGraphSnapshotSupplier;
    }

    private void refreshEditorSnapshots() {
        if (behaviorGraphSnapshotSupplier == null) {
            return;
        }
        NpcAI ai = npc.npcConfig.getNpcData(NpcAI.class);
        if (ai != null) {
            CompoundTag graphTag = behaviorGraphSnapshotSupplier.get();
            if (graphTag != null && !graphTag.isEmpty()) {
                ai.setBehaviorGraph(graphTag);
                ai.setBehaviorProgram(ai.getCompiledBehaviorProgram());
            }
        }
    }

}
