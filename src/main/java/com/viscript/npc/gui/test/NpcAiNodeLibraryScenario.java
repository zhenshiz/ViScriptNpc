package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

/** 验证原生节点选择列表的本地化、全部节点说明、搜索和创建流程。 */
@LDLRegisterClient(name = "npc_ai_node_library", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiNodeLibraryScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).scenarioTimeoutMs(240_000).tags("ui", "ai");
    }

    @Override public void define(ScenarioBuilder s) {
        s.server("打开空白 NPC 工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .click("#npc_editor_page_tab_ai").awaitElement("#npc_ai_graph_tree");

        for (String kind : List.of("tree", "action")) {
            if (kind.equals("action")) {
                s.step("打开独立行为工程", c -> {
                    view(c).itemLibrary.hide();
                    window(c).createNewEditor(NpcEditor::new).loadProject(new NpcActionProject(), null);
                }).awaitElement("#npc_ai_graph_action");
            }
            s.step("切换节点目录 " + kind, c -> c.put("library_kind", kind))
                    .check("列表名称和分类均使用翻译 " + kind, c -> {
                        var library = view(c).itemLibrary;
                        return library.getAllItems().allMatch(item -> !item.getDisplayName().getString().startsWith("viscript_npc:"))
                                && library.nodeTree.getRoot().getChildren().stream().allMatch(node ->
                                node.getKey().getDisplayName().getString().equals(Component.translatable(
                                        "viscript_npc.ai.category." + node.getKey().getPath()).getString()));
                    })
                    .check("目录排除未注册操作并完整保留当前节点 " + kind, c -> {
                        var graph = (AiGraph) view(c).getGraph();
                        var actual = graph.getLibrarySupportNodes().stream()
                                .map(type -> type.getAnnotation(NodeAttribute.class).name())
                                .collect(Collectors.toSet());
                        var expected = AiOperations.all().stream()
                                .filter(operation -> AiOperations.supports(kind, operation.id()))
                                .map(operation -> "viscript_npc:" + operation.id()).collect(Collectors.toSet());
                        return AiGraph.REGISTRY.get("viscript_npc:test_unregistered_operation") != null
                                && AiOperations.get("test_unregistered_operation") == null && actual.equals(expected);
                    })
                    .step("右键打开画布菜单 " + kind, c -> {
                        var bounds = ElementBounds.of(view(c).graphView);
                        float x = bounds.x() + 30, y = bounds.y() + 35;
                        c.input().moveTo(x, y);
                        c.input().mouseDown(x, y, Keys.MOUSE_RIGHT);
                        c.input().mouseUp(x, y, Keys.MOUSE_RIGHT);
                    }).ticks(2)
                    .step("点击原生添加节点菜单项", c -> {
                        var bounds = c.query(".__node-graph-view_context-menu__ .__menu_leaf-node__").nth(0).one().bounds();
                        c.input().moveTo(bounds.centerX(), bounds.centerY());
                        c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                        c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    }).awaitElement("#npc_ai_node_library");

            for (var operation : AiOperations.all()) {
                if (!AiOperations.supports(kind, operation.id())) continue;
                String id = operation.id();
                search(s, id)
                        .step("定位搜索结果 " + id, c -> {
                            var library = view(c).itemLibrary;
                            var node = library.searchTree.getRoot().getChildren().stream().filter(n ->
                                    n.getKey().getSearchableName().startsWith("viscript_npc:" + id + " "))
                                    .findFirst().orElseThrow();
                            library.searchTree.getNodeUIs().get(node).setId("npc_ai_library_result");
                        })
                        .click("#npc_ai_library_result").ticks(1)
                        .check("原生侧栏显示用途和执行规则 " + id, c -> {
                            String info = "#npc_ai_node_library #npc_ai_node_info_" + id;
                            return c.count(info) == 1
                                    && c.query(info + " *").type(Label.class).withText(Component.translatable(
                                            "viscript_npc.ai.description." + id).getString()).count() == 1
                                    && c.query(info + " *").type(Label.class).withText(Component.translatable(
                                            "viscript_npc.ai.behavior." + id).getString()).count() == 1
                                    && view(c).model().getNodeModels().size() == 1;
                        });
            }
            if (kind.equals("tree")) {
                search(s, Component.translatable("viscript_npc.ai.node.detect_target").getString())
                        .key(Keys.DOWN).ticks(2)
                        .checkExists("#npc_ai_node_library #npc_ai_node_info_detect_target")
                        .screenshot("detect_target_description")
                        .scroll(".__item-library_description-view__", -4).ticks(2)
                        .check("长说明可滚动查看", c -> view(c).itemLibrary.descriptionView.verticalScroller.getValue() > 0);
            }
        }

        search(s, "timeout").key(Keys.DOWN).ticks(2)
                .checkExists("#npc_ai_node_library #npc_ai_node_info_timeout")
                .step("清空英文搜索", c -> view(c).itemLibrary.searchField.setText("", true))
                .typeInto("#npc_ai_node_search", Component.translatable("viscript_npc.ai.node.timeout").getString())
                .key(Keys.DOWN).ticks(2)
                .checkExists("#npc_ai_node_library #npc_ai_node_info_timeout")
                .screenshot("translated_node_library")
                .key(Keys.ENTER).ticks(2)
                .check("原生创建命令保留稳定节点标识", c -> view(c).model().getNodeModels().stream()
                        .anyMatch(n -> n instanceof AiNodeModel ai && ai.operation().equals("timeout")))
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .step("定位新建节点", c -> {
                    var node = view(c).model().getNodeModels().stream()
                            .filter(n -> n instanceof AiNodeModel ai && ai.operation().equals("timeout")).findFirst().orElseThrow();
                    c.el("#npc_ai_node_" + node.getUid()).element().setId("npc_ai_created_timeout");
                }).click("#npc_ai_created_timeout").ticks(2)
                .checkNotExists("#npc_ai_parameters_view #npc_ai_node_info_timeout")
                .checkExists("#npc_ai_parameters_view #npc_ai_field_seconds")
                .step("原有工程格式可保存重开", c -> {
                    var reopened = new AiGraph("action");
                    reopened.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    c.check("节点类型保存重开不变", reopened.model().getNodeModels().stream()
                            .anyMatch(n -> n instanceof AiNodeModel ai && ai.operation().equals("timeout")));
                }).screenshot("selected_node_parameters").closeScreen();
    }

    private static AiGraphView view(TestContext context) {
        String kind = context.get("library_kind");
        return context.el("#npc_ai_graph_" + (kind == null ? "tree" : kind)).as(AiGraphView.class);
    }

    private static ScenarioBuilder search(ScenarioBuilder scenario, String word) {
        return scenario.step("清空上次搜索", c -> view(c).itemLibrary.searchField.setText("", true))
                .typeInto("#npc_ai_node_search", word).ticks(1);
    }

    private static EditorWindow window(TestContext context) {
        return (EditorWindow) ((ModularUIContainerScreen) context.mc().screen).getMenu().getModularUI().ui.rootElement;
    }

    /** 模拟被节点注册表发现、但没有当前运行时定义的节点。 */
    @NodeAttribute(name = "viscript_npc:test_unregistered_operation", group = "flow", graphTypes = AiGraph.class,
            environment = RegistrationEnvironment.DEV_ONLY)
    public static final class UnregisteredOperationNode extends AiGraphNode {
        public UnregisteredOperationNode() { super("test_unregistered_operation"); }
    }
}
