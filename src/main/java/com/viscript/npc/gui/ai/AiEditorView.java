package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.AiProgram;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript.npc.util.NpcEditorFiles;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector2f;


/** 将 LDLib2 画布嵌入工程编辑器；工程保存和 AI 应用分别读取当前快照。 */
public final class AiEditorView extends GraphEditorView {
    public final AiGraph aiGraph;
    public final AiGraphView aiView;
    private final CompoundTag initialGraph;
    private final AiProgram initialProgram;
    private boolean loadingActions;

    public AiEditorView(NpcEditor editor, String kind, CompoundTag saved, AiProgram runtime) {
        super(AiGraphView::new);
        initialGraph = saved.copy();
        initialProgram = runtime.copy(Platform.getFrozenRegistry());
        loadingActions = !saved.isEmpty();
        setName("viscript_npc.ai.editor." + kind);
        aiGraph = new AiGraph(kind);
        aiGraph.actionResolver = id -> AiDrafts.resolve(editor, id);
        if (!saved.isEmpty()) aiGraph.model().deserializeNBT(Platform.getFrozenRegistry(), saved);
        else if (!runtime.nodes.isEmpty()) aiGraph.model().importProgram(runtime);
        else {
            aiGraph.model().importProgram(runtime);
            aiGraph.model().add("root", new Vector2f());
        }
        aiGraph.model().metadata.kind = kind;
        aiView = (AiGraphView) graphView;
        aiView.loadingActionDefinitions = true;
        aiView.setId("npc_ai_graph_" + kind);
        aiView.actionOpener = id -> openAction(editor, id);
        aiView.exampleOpener = id -> openExample(editor, id);
        aiView.treeExampleOpener = name -> {
            if (editor.getWindow() == null) return;
            var project = new com.viscript.npc.gui.edit.NPCProject();
            project.npc.getNpcData(com.viscript.npc.npc.data.ai.NpcAI.class)
                    .setProgram(com.viscript.npc.npc.ai.runtime.AiExamples.tree(name));
            project.npc.getNpcData(com.viscript.npc.npc.data.ai.NpcAI.class).setEnabled(true);
            var created = (NpcEditor) editor.getWindow().createNewEditor(NpcEditor::new);
            created.loadProject(project, null);
            created.selectNpcEditorPage(new com.viscript.npc.gui.edit.page.AiNpcEditorPage());
        };
        aiView.setReadOnly(loadingActions);
        loadGraph(aiGraph, tag -> {});
        saveButton.removeSelf();
        aiView.actionResolver = id -> AiDrafts.resolve(editor, id);
        aiView.configureForKind(kind);
        aiView.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.ADDED,
                event -> AiServerActions.request(() -> {
                    // 首次同步后重新恢复原图，保留加载时尚无服务端参数定义的连线。
                    if (loadingActions) {
                        if (!initialGraph.isEmpty()) aiGraph.model().deserializeNBT(Platform.getFrozenRegistry(), initialGraph);
                        loadingActions = false;
                        aiView.setReadOnly(false);
                    }
                    aiView.refreshActionDefinitions();
                    aiView.loadingActionDefinitions = false;
                }));
        aiView.debugToolbar.addChild(AiGraphView.icon("scene_window", () -> AiDrafts.openSceneWindow(editor)));
        if (editor.getCurrentProject() instanceof NpcActionProject action) {
            action.actionResolver = id -> AiDrafts.resolve(editor, id);
        }
        if (editor.getCurrentProject() instanceof com.viscript.npc.gui.edit.NPCProject npc) {
            npc.actionResolver = id -> AiDrafts.resolve(editor, id);
            aiView.addSaveButton(() -> {
                if (loadingActions) {
                    com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.showNotification(
                            "viscript_npc.ai.library.loading", 2).show(editor.getModularUI());
                } else AiDrafts.saveNpcAi(editor);
            });
        }
    }

    @Override public CompoundTag serializeGraph() {
        return loadingActions ? initialGraph.copy() : super.serializeGraph();
    }

    public AiProgram compileProgram() {
        return loadingActions ? initialProgram.copy(Platform.getFrozenRegistry()) : aiGraph.model().compile();
    }

    private static AiGraphView openAction(NpcEditor editor, String id) {
        try {
            var path = NpcEditorFiles.resolveProjectFile(NpcActionProject.FORMAT, id, true);
            if (editor.getWindow() != null) for (var tab : editor.getWindow().getEditors().keySet()) {
                if (tab.getCurrentProject() instanceof NpcActionProject action &&
                        (tab.getCurrentProjectFile() != null
                        ? tab.getCurrentProjectFile().toPath().toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize())
                        : action.sourceActionId.equals(id))) {
                    editor.getWindow().showEditor(tab); return actionView(tab);
                }
            }
            NpcActionProject project = new NpcActionProject();
            var definition = AiDrafts.resolve(editor, id);
            if (definition == null) return null;
            project.program = definition.copy(Platform.getFrozenRegistry());
            project.sourceActionId = id;
            if (editor.getWindow() != null) {
                var created = editor.getWindow().createNewEditor(NpcEditor::new);
                created.loadProject(project, null);
                return actionView(created);
            }
        } catch (Exception exception) {
            com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.showNotification("Dialog.error", exception.getMessage(), null).show(editor.getModularUI());
        }
        return null;
    }

    private static AiGraphView actionView(com.lowdragmc.lowdraglib2.editor.ui.Editor editor) {
        return editor.select("#npc_ai_graph_action").filter(AiGraphView.class::isInstance)
                .map(AiGraphView.class::cast).findFirst().orElse(null);
    }
    private static void openExample(NpcEditor editor, String id) {
        if (editor.getWindow() == null || !NpcActionFiles.isBuiltin(id)) return;
        var project = new NpcActionProject();
        project.program = com.viscript.npc.npc.ai.runtime.AiExamples.action(id.substring(9)).copy(Platform.getFrozenRegistry());
        editor.getWindow().createNewEditor(NpcEditor::new).loadProject(project, null);
    }

}
