package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.*;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.viscript.npc.npc.ai.data.AiProgram;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript.npc.gui.edit.NpcProjectFileType;
import com.viscript_lib.gui.editor.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.function.Supplier;
import java.util.function.Function;

/** 独立可视化行为工程；草稿图和经过校验的运行定义分别保存。 */
public final class NpcActionProject implements IRuntimeFileProject {
    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC_ACTION;
    public static final ProjectFileProjectType PROVIDER = new NpcProjectFileType(Icons.NODE,
            "editor.project.npc_action.add", FORMAT, NpcActionProject::new);
    private final Resources resources = Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE);
    public CompoundTag graph = new CompoundTag();
    public AiProgram program = new AiProgram();
    /** 从运行文件打开且尚未另存的行为所对应的逻辑引用。 */
    public String sourceActionId = "";
    private boolean snapshotting;
    public Function<String, AiProgram> actionResolver = com.viscript.npc.util.NpcActionFiles::loadLocal;
    public Supplier<CompoundTag> graphSnapshot;
    public Supplier<AiProgram> compiler;

    public NpcActionProject() { program.kind = "action"; }
    @Override public Resources getResources() { return resources; }
    @Override public ProjectFileProjectType getProjectType() { return PROVIDER; }
    public void snapshot() {
        if (snapshotting) return;
        snapshotting = true;
        try {
            if (graphSnapshot != null) graph = graphSnapshot.get();
            if (compiler != null) program = compiler.get();
        } finally { snapshotting = false; }
    }
    @Override public CompoundTag serializeProject(HolderLookup.Provider provider) {
        snapshot();
        CompoundTag data = new CompoundTag();
        data.put("graph", graph.copy());
        data.put("program", program.serializeNBT(provider));
        data.putString("sourceActionId", sourceActionId);
        return data;
    }
    @Override public void deserializeProject(HolderLookup.Provider provider, CompoundTag data) {
        graph = data.getCompound("graph").copy();
        program = new AiProgram();
        program.deserializeNBT(provider, data.getCompound("program"));
        program.kind = "action";
        sourceActionId = data.getString("sourceActionId");
    }
    @Override public CompoundTag serializeRuntimeFile(HolderLookup.Provider provider) {
        snapshot();
        AiValidation.requireValid(program, actionResolver);
        var runtime = program.serializeNBT(provider);
        runtime.put(com.viscript.npc.util.NpcAiPublication.DEPENDENCIES,
                com.viscript.npc.util.NpcAiPublication.bundle(program, actionResolver));
        return runtime;
    }
    @Override public void onClosed(Editor editor) { snapshot(); graphSnapshot = null; compiler = null; }
}
