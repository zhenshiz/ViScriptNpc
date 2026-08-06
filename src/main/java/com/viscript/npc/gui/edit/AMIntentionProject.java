package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.viscript.npc.npc.ai.editor.AMIntentionGraphCompiler;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript_lib.gui.editor.EditorFileFormat;
import com.viscript_lib.gui.editor.IRuntimeFileProject;
import com.viscript_lib.gui.editor.ProjectFileProjectType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.Set;
import java.util.UUID;

public class AMIntentionProject implements IRuntimeFileProject {
    public static final int VERSION = 1;
    public static final EditorFileFormat FORMAT = NpcEditorFormats.AM_INTENTION;
    public static final ProjectFileProjectType PROVIDER = new ProjectFileProjectType(IGuiTexture.EMPTY,
            Component.translatable("editor.project.am_intention.add").getString(), FORMAT, AMIntentionProject::new);

    private final Resources resources;
    private CompoundTag graph = new CompoundTag();
    @Nullable
    private Supplier<Snapshot> snapshotSupplier;
    @Nullable
    private Supplier<Set<UUID>> selectedNodesSupplier;

    public AMIntentionProject() {
        resources = Platform.isClient()
                ? Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE, IRendererResource.INSTANCE)
                : Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE);
    }

    public CompoundTag getGraph() { return graph.copy(); }
    public void setSnapshotSupplier(@Nullable Supplier<Snapshot> supplier) { snapshotSupplier = supplier; }
    public void setSelectedNodesSupplier(@Nullable Supplier<Set<UUID>> supplier) { selectedNodesSupplier = supplier; }
    public Set<UUID> getSelectedNodes() {
        return selectedNodesSupplier == null ? Set.of() : Set.copyOf(selectedNodesSupplier.get());
    }

    public AMIntentionGraphCompiler.Compilation compile() {
        Snapshot snapshot = snapshotSupplier == null ? null : snapshotSupplier.get();
        return snapshot == null ? AMIntentionGraphCompiler.Compilation.failure("AM graph editor is not loaded")
                : snapshot.compilation();
    }

    private void refresh() {
        if (snapshotSupplier == null) return;
        Snapshot snapshot = snapshotSupplier.get();
        if (snapshot != null) graph = snapshot.graph().copy();
    }

    public String getVersion() { return VERSION + ".0"; }
    public ProjectFileProjectType getProjectType() { return PROVIDER; }

    public CompoundTag serializeProject(@NotNull HolderLookup.Provider provider) {
        refresh();
        CompoundTag tag = new CompoundTag();
        tag.put("graph", graph.copy());
        return tag;
    }

    public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
        refresh();
        return IRuntimeFileProject.super.serializeNBT(provider);
    }

    public CompoundTag serializeRuntimeFile(HolderLookup.Provider provider) {
        refresh();
        AMIntentionGraphCompiler.Compilation compilation = compile();
        CompoundTag tag = new CompoundTag();
        if (compilation.assetId() != null) tag.putString("asset_id", compilation.assetId().toString());
        tag.putString("json", compilation.json());
        var errors = new net.minecraft.nbt.ListTag();
        compilation.errors().forEach(error -> errors.add(net.minecraft.nbt.StringTag.valueOf(error)));
        tag.put("errors", errors);
        return tag;
    }

    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        snapshotSupplier = null;
        selectedNodesSupplier = null;
        graph = nbt.getCompound("graph").copy();
    }

    public void onClosed(Editor editor) {
        snapshotSupplier = null;
        selectedNodesSupplier = null;
    }
    public Resources getResources() { return resources; }

    public CompoundTag getMetadata() {
        CompoundTag tag = IRuntimeFileProject.super.getMetadata();
        tag.putInt("version_num", VERSION);
        return tag;
    }

    public record Snapshot(CompoundTag graph, AMIntentionGraphCompiler.Compilation compilation) {
    }
}
