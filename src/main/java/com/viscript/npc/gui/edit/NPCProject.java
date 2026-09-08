package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.gui.edit.data.NpcConfig;
import com.viscript.npc.npc.data.ai.NpcAI;
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
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.Set;
import java.util.UUID;

public class NPCProject implements IRuntimeFileProject {
    public static int VERSION = 1;
    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC;
    public static final ProjectFileProjectType PROVIDER = new ProjectFileProjectType(IGuiTexture.EMPTY, Component.translatable("editor.project.npc.add").getString(), FORMAT, NPCProject::new);

    @Getter
    private final Resources resources;
    public NpcConfig npc = new NpcConfig();
    @Nullable
    private Supplier<FlowEditorSnapshot> flowEditorSnapshotSupplier;
    @Nullable
    private Supplier<Set<UUID>> selectedFlowNodesSupplier;

    public record FlowEditorSnapshot(CompoundTag graph, CompoundTag compiledFlow) {
    }

    public void setFlowEditorSnapshotSupplier(@Nullable Supplier<FlowEditorSnapshot> supplier) {
        this.flowEditorSnapshotSupplier = supplier;
    }

    public void setSelectedFlowNodesSupplier(@Nullable Supplier<Set<UUID>> supplier) {
        selectedFlowNodesSupplier = supplier;
    }

    public Set<UUID> getSelectedFlowNodes() {
        return selectedFlowNodesSupplier == null ? Set.of() : Set.copyOf(selectedFlowNodesSupplier.get());
    }

    public FlowEditorSnapshot getFlowEditorSnapshot() {
        return flowEditorSnapshotSupplier == null ? null : flowEditorSnapshotSupplier.get();
    }

    private void refreshFlowEditorSnapshot() {
        if (flowEditorSnapshotSupplier == null) return;
        NpcAI ai = npc.getNpcData(NpcAI.class);
        if (ai.usesTemplate()) return;
        FlowEditorSnapshot snapshot = flowEditorSnapshotSupplier.get();
        if (snapshot != null) {
            ai.useEmbeddedGraph(snapshot.graph(), snapshot.compiledFlow());
        }
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
        refreshFlowEditorSnapshot();
        var data = new CompoundTag();
        data.put("npc", npc.serializeNBT(provider));
        return data;
    }

    @Override
    public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
        refreshFlowEditorSnapshot();
        return IRuntimeFileProject.super.serializeNBT(provider);
    }

    @Override
    public CompoundTag serializeRuntimeFile(HolderLookup.Provider provider) {
        refreshFlowEditorSnapshot();
        CompoundTag data = npc.serializeNBT(provider);
        return data;
    }

    public CompoundTag serializeNpcConfig(HolderLookup.Provider provider) {
        refreshFlowEditorSnapshot();
        return npc.serializeNBT(provider);
    }

    @Override
    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        flowEditorSnapshotSupplier = null;
        selectedFlowNodesSupplier = null;
        npc.deserializeNBT(provider, nbt.getCompound("npc"));
    }

    @Override
    public void onClosed(Editor editor) {
        flowEditorSnapshotSupplier = null;
        selectedFlowNodesSupplier = null;
    }

    @Override
    public CompoundTag getMetadata() {
        var meta = IRuntimeFileProject.super.getMetadata();
        meta.putInt("version_num", VERSION);
        return meta;
    }

}
