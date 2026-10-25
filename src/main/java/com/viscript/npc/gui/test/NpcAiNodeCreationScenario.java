package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.PortLibraryItem;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.PortElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.AiGraphView;
import com.viscript.npc.gui.ai.AiNodeModel;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 验证双击节点文字创建、拖线自动连接，以及执行端口子项的隐藏。 */
@LDLRegisterClient(name = "npc_ai_node_creation", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiNodeCreationScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        s.server("打开空白 NPC 工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .click("#npc_editor_page_tab_ai").awaitElement("#npc_ai_graph_tree");
        openLibrary(s);
        searchTimeout(s).click("#npc_ai_creation_label").ticks(1)
                .checkExists("#npc_ai_node_library #npc_ai_node_info_timeout")
                .check("单击文字只查看说明", c -> view(c).model().getNodeModels().size() == 1)
                .doubleClick("#npc_ai_creation_label").ticks(2)
                .check("普通列表双击文字创建一次", c -> view(c).model().getNodeModels().size() == 2
                        && view(c).model().getWireModels().isEmpty()
                        && view(c).model().getNodeModels().stream().anyMatch(n ->
                        n instanceof AiNodeModel ai && ai.operation().equals("timeout")))
                .checkNotExists("#npc_ai_node_library")
                .click("#npc_ai_graph_tree #npc_ai_undo").ticks(2)
                .check("普通添加可撤销", c -> view(c).model().getNodeModels().size() == 1)
                .click("#npc_ai_graph_tree #npc_ai_fit").ticks(2)
                .step("定位根节点执行端口和空白画布", c -> {
                    var v = view(c);
                    var root = (AiNodeModel) v.model().getNodeModels().getFirst();
                    var port = (PortElement) v.getModelElement(root.getOutputsById().get("children"));
                    port.getConnector().getWireDragParts().findFirst().orElseThrow().setId("npc_ai_creation_port");
                    var bounds = ElementBounds.of(v.graphView);
                    c.put("creation_drop", new float[]{bounds.x() + bounds.width() * .8f,
                            bounds.y() + bounds.height() * .7f});
                })
                .step("按下执行端口", c -> {
                    var b = c.el("#npc_ai_creation_port").bounds();
                    c.input().moveTo(b.centerX(), b.centerY());
                    c.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                })
                .step("将连线拖到空白画布", c -> {
                    float[] drop = c.get("creation_drop");
                    c.input().dragTo(drop[0], drop[1], Keys.MOUSE_LEFT);
                })
                .step("释放连线打开节点目录", c -> {
                    float[] drop = c.get("creation_drop");
                    c.input().mouseUp(drop[0], drop[1], Keys.MOUSE_LEFT);
                }).awaitElement("#npc_ai_node_library").ticks(2)
                .step("展开分类检查直接创建的节点行", c -> {
                    var library = view(c).itemLibrary;
                    library.nodeTree.expandAllNodesIf(library.nodeTree.getRoot(), node -> true);
                    library.recommendationTree.expandAllNodesIf(library.recommendationTree.getRoot(), node -> true);
                }).ticks(2)
                .check("执行流列表没有空圆圈子项", NpcAiNodeCreationScenario::hasNoFlowSubitems)
                .screenshot("wire_node_names");
        searchTimeout(s).click("#npc_ai_creation_label").ticks(1)
                .checkExists("#npc_ai_node_library #npc_ai_node_info_timeout")
                .check("搜索结果同样没有执行端口子项", NpcAiNodeCreationScenario::hasNoFlowSubitems)
                .check("拖线单击仍不创建节点", c -> view(c).model().getNodeModels().size() == 1)
                .screenshot("wire_node_description")
                .doubleClick("#npc_ai_creation_label").ticks(2)
                .check("拖线双击文字创建并自动连接", c -> view(c).model().getNodeModels().size() == 2
                        && view(c).model().getWireModels().size() == 1
                        && ((AiNodeModel) view(c).model().getWireModels().getFirst().getToPort().getNodeModel())
                        .operation().equals("timeout"))
                .checkNotExists("#npc_ai_node_library")
                .click("#npc_ai_graph_tree #npc_ai_undo").ticks(2)
                .check("节点和自动连线一起撤销", c -> view(c).model().getNodeModels().size() == 1
                        && view(c).model().getWireModels().isEmpty())
                .click("#npc_ai_graph_tree #npc_ai_redo").ticks(2)
                .check("节点和自动连线一起重做", c -> view(c).model().getNodeModels().size() == 2
                        && view(c).model().getWireModels().size() == 1)
                .click("#npc_ai_graph_tree #npc_ai_fit").ticks(2)
                .screenshot("double_click_connected_node");
        openLibrary(s);
        s.step("展开装饰器分类并定位节点文字", c -> {
                    var tree = view(c).itemLibrary.nodeTree;
                    tree.expandAllNodesIf(tree.getRoot(), node -> node == tree.getRoot()
                            || node.getKey().getPath().equals("decorator"));
                    c.query("#npc_ai_node_library .__item-library_node-tree__ *").type(TextElement.class)
                            .withText(Component.translatable("viscript_npc.ai.node.timeout").getString())
                            .one().element().setId("npc_ai_creation_category_label");
                }).ticks(2).doubleClick("#npc_ai_creation_category_label").ticks(2)
                .check("分类列表可直接双击添加且不沿用上次的连线", c -> view(c).model().getNodeModels().size() == 3
                        && view(c).model().getWireModels().size() == 1)
                .closeScreen();
    }

    private static ScenarioBuilder openLibrary(ScenarioBuilder s) {
        return s.step("右键打开画布菜单", c -> {
                    var b = ElementBounds.of(view(c).graphView);
                    float x = b.x() + 30, y = b.y() + 35;
                    c.input().moveTo(x, y);
                    c.input().mouseDown(x, y, Keys.MOUSE_RIGHT);
                    c.input().mouseUp(x, y, Keys.MOUSE_RIGHT);
                }).ticks(2)
                .step("选择添加节点", c -> {
                    var b = c.query(".__node-graph-view_context-menu__ .__menu_leaf-node__").nth(0).one().bounds();
                    c.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                }).awaitElement("#npc_ai_node_library");
    }

    private static ScenarioBuilder searchTimeout(ScenarioBuilder s) {
        return s.step("清空上次搜索", c -> view(c).itemLibrary.searchField.setText("", true))
                .typeInto("#npc_ai_node_search", "timeout").ticks(1)
                .step("定位节点名称文字", c -> c.query("#npc_ai_node_library .__item-library_search-tree__ *").type(TextElement.class)
                        .withText(Component.translatable("viscript_npc.ai.node.timeout").getString())
                        .one().element().setId("npc_ai_creation_label"));
    }

    private static boolean hasNoFlowSubitems(TestContext c) {
        var library = view(c).itemLibrary;
        return List.of(library.nodeTree, library.recommendationTree, library.searchTree).stream()
                .filter(tree -> tree.getRoot() != null).flatMap(tree -> tree.getRoot().flatten().stream())
                .noneMatch(node -> node.getKey() instanceof PortLibraryItem port
                        && port.getPort().getDataTypeHandle().equals(TypeHandles.EXECUTION_FLOW));
    }

    private static AiGraphView view(TestContext c) {
        return c.el("#npc_ai_graph_tree").as(AiGraphView.class);
    }
}
