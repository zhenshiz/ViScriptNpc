package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.*;
import net.minecraft.nbt.NbtIo;
import org.joml.Vector2f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 验证首次从服务端取得行为定义后，工程中的行为参数连线仍能恢复。 */
@LDLRegisterClient(name = "npc_ai_server_action_restore", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiServerActionRestoreScenario implements UIScenario {
    private static final String ID = "__ldlib2_tests__/server_action_cold_open";

    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        var definition = NpcAiFixtures.movementAction();
        String parameter = definition.parameters.getFirst().id;
        s.server("准备尚未同步的服务端行为", server -> {
                    var path = NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, ID, true);
                    try {
                        originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
                        Files.createDirectories(path.getParent());
                        NbtIo.writeCompressed(definition.serializeNBT(Platform.getFrozenRegistry()), path);
                    } catch (Exception exception) { throw new IllegalStateException(exception); }
                    NpcActionFiles.invalidate();
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), new NPCProject().serializeNBT(Platform.getFrozenRegistry()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("打开含服务端参数连线的工程", c -> {
                    c.check("打开前客户端没有该行为定义", AiServerActions.load(ID) == null);
                    var graph = new AiGraph("action");
                    graph.actionResolver = id -> id.equals(ID) ? definition : null;
                    var model = graph.model();
                    var root = model.add("root", new Vector2f(0, -160));
                    var call = model.add("call", new Vector2f());
                    call.data.input("file", AiValue.text(ID)); call.defineNode();
                    model.createWire(call.getInputsById().get("parent"), root.getOutputsById().get("children"), null);
                    var variable = new AiVariable(); variable.name = "speed"; variable.initial = AiValue.number(1.75);
                    var declaration = model.addVariable(variable, "local");
                    var variableNode = model.createVariableNode(declaration, new Vector2f(-200, 0), null, null);
                    model.createWire(call.getInputsById().get("value_param:" + parameter), variableNode.getOutputPort(), null);
                    var project = new NpcActionProject();
                    project.graph = model.serializeNBT(Platform.getFrozenRegistry());
                    project.program = model.compile();
                    c.put("call_id", call.getUid());
                    c.put("variable_id", variable.id);
                    c.put("saved_graph", project.graph.copy());
                    var window = (EditorWindow) ((ModularUIContainerScreen)c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    window.createNewEditor(NpcEditor::new).loadProject(project, null);
                    c.check("同步期间保存仍保留原图", project.serializeProject(Platform.getFrozenRegistry()).getCompound("graph")
                            .equals(c.get("saved_graph")));
                }).awaitElement("#npc_ai_graph_action")
                .waitUntil("服务端定义完成同步且画布恢复编辑", c -> AiServerActions.load(ID) != null
                        && !c.el("#npc_ai_graph_action").as(AiGraphView.class).isReadOnly())
                .check("行为参数连线及变量绑定完整恢复", c -> {
                    var view = c.el("#npc_ai_graph_action").as(AiGraphView.class);
                    var node = (AiNodeModel)view.model().getModel(c.get("call_id"));
                    var binding = view.model().compile().node(node.getUid().toString()).inputs.get("param:" + parameter);
                    return node.getInputsById().get("value_param:" + parameter).getConnectedWires().size() == 1
                            && binding.scope.equals("local") && binding.variableId.equals(c.get("variable_id"))
                            && binding.value.number == 1.75;
                }).step("同步后导出包含服务端子行为", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen)c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = (NpcActionProject)window.getCurrentEditor().getCurrentProject();
                    var runtime = project.serializeRuntimeFile(Platform.getFrozenRegistry());
                    c.check("导出携带服务端行为依赖", runtime.getCompound(NpcAiPublication.DEPENDENCIES).contains(ID));
                    var syncedParameter = AiServerActions.load(ID).parameters.getFirst();
                    var expected = definition.parameters.getFirst();
                    var call = (AiNodeModel)c.el("#npc_ai_graph_action").as(AiGraphView.class).model().getModel(c.get("call_id"));
                    c.check("服务端参数翻译信息随行为同步并用于端口", syncedParameter.displayNameKey.equals(expected.displayNameKey)
                            && syncedParameter.tipsKey.equals(expected.tipsKey)
                            && call.getInputsById().get("value_param:" + parameter).getTitle().getString()
                            .equals(AiVariables.displayName(expected).getString())
                            && call.getInputsById().get("value_param:" + parameter).getTooltips().asList().stream()
                            .anyMatch(t -> t.getString().equals(AiVariables.tips(expected).asList().getFirst().getString())));
                    c.el("#npc_ai_graph_action").as(AiGraphView.class).fitGraphChildren();
                }).ticks(2).screenshot("server_action_parameter_wire_restored").closeScreen()
                .teardown("恢复服务端行为文件", c -> {
                    originals.forEach((path, bytes) -> {
                        try { if (bytes == null) Files.deleteIfExists(path); else Files.write(path, bytes); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                    });
                    NpcActionFiles.invalidate();
                });
    }
}
