package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.ViScriptNpcServerUtil;

import java.util.UUID;

/** 验证原生节点外观检查器的选色、宽度编辑、撤销和工程往返。 */
@LDLRegisterClient(name = "npc_ai_appearance", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiAppearanceScenario implements UIScenario {
    private static final String INSPECTOR = "#npc_ai_appearance_inspector *";
    private static final int COLOR = 0xFF4488CC;

    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        var fixture = NpcAiFixtures.action("wait");
        var wait = fixture.nodes.stream().filter(n -> n.type.equals("wait")).findFirst().orElseThrow();
        UUID id = UUID.fromString(wait.id);
        s.server("打开编辑器", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("载入待机行为", c -> {
                    var project = new NpcActionProject(); project.program = fixture;
                    var graph = new AiGraph("action"); graph.model().importProgram(fixture);
                    project.graph = graph.model().serializeNBT(Platform.getFrozenRegistry());
                    window(c).createNewEditor(NpcEditor::new).loadProject(project, null);
                }).awaitElement("#npc_ai_graph_action")
                .waitUntil("首次行为库同步完成", c -> !view(c).isReadOnly())
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .click("#npc_ai_node_" + id + " .__node-title-element__").ticks(2)
                .step("展开原生外观检查器", c -> c.el("#npc_ai_appearance").as(GraphPanel.class).setCollapsed(false)).ticks(2)
                .check("使用 LDLib2 原生颜色和数值配置器", c ->
                        c.query(INSPECTOR).type(ColorConfigurator.class).count() == 1
                                && c.query(INSPECTOR).type(NumberConfigurator.class).count() == 1)
                .check("行为参数独立显示在右侧 View", c -> c.count("#npc_ai_parameters_view #npc_ai_field_seconds") == 1
                        && c.count("#npc_ai_appearance_inspector #npc_ai_field_seconds") == 0)
                .step("定位原生色块", c -> {
                    var color = c.query(INSPECTOR).type(ColorConfigurator.class).one().as(ColorConfigurator.class);
                    c.put("native_color_configurator", color);
                    color.colorPreview.getChildren().getFirst().setId("npc_ai_test_color_swatch");
                    color.colorSelector.hexConfigurator.textField.setId("npc_ai_test_color_hex");
                }).step("点击色块打开原生选色器", c -> {
                    var bounds = c.el("#npc_ai_test_color_swatch").bounds();
                    c.input().moveTo(bounds.centerX(), bounds.centerY());
                    c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                }).ticks(2)
                .checkVisible("#npc_ai_test_color_hex")
                .screenshot("native_color_picker")
                .focus("#npc_ai_test_color_hex").key(Keys.HOME).key(Keys.END, Keys.MOD_SHIFT)
                .type("#ff4488cc").key(Keys.ENTER).ticks(2)
                .check("原生选色器写入节点颜色", c -> node(c, id).hasUserColor() && node(c, id).getElementColor() == COLOR)
                .step("收起选色器", c -> c.<ColorConfigurator>get("native_color_configurator").hide())
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("原生改色命令可以撤销", c -> !node(c, id).hasUserColor())
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .check("原生改色命令可以重做", c -> node(c, id).getElementColor() == COLOR)
                .click("#npc_ai_node_" + id + " .__node-title-element__").ticks(2)
                .step("定位原生宽度输入", c -> c.query(INSPECTOR).type(NumberConfigurator.class).one()
                        .as(NumberConfigurator.class).textField.setId("npc_ai_test_min_width"))
                .focus("#npc_ai_test_min_width").key(Keys.HOME).key(Keys.END, Keys.MOD_SHIFT)
                .type("280").key(Keys.ENTER).ticks(3)
                .check("原生最小宽度更新节点模型和界面", c -> node(c, id).getMinWidth() == 280
                        && view(c).getModelElement(node(c, id)).getSizeWidth() >= 280)
                .screenshot("native_appearance_fields")
                .step("保存并重开工程快照", c -> {
                    var editor = window(c).getCurrentEditor();
                    var reopened = new NpcActionProject();
                    reopened.deserializeNBT(Platform.getFrozenRegistry(), editor.getCurrentProject().serializeNBT(Platform.getFrozenRegistry()));
                    editor.loadProject(reopened, null);
                }).awaitElement("#npc_ai_graph_action")
                .waitUntil("重开后的行为定义已就绪", c -> !view(c).isReadOnly())
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .check("工程保留原生颜色和最小宽度", c -> node(c, id).hasUserColor()
                        && node(c, id).getElementColor() == COLOR && node(c, id).getMinWidth() == 280)
                .click("#npc_ai_node_" + id + " .__node-title-element__").ticks(2)
                .check("重开后仍使用原生外观配置器及独立行为面板", c ->
                        c.query(INSPECTOR).type(ColorConfigurator.class).count() == 1
                                && c.query(INSPECTOR).type(NumberConfigurator.class).count() == 1
                                && c.count("#npc_ai_parameters_view #npc_ai_field_seconds") == 1)
                .closeScreen();
    }

    private static AiGraphView view(TestContext c) { return c.el("#npc_ai_graph_action").as(AiGraphView.class); }
    private static AiNodeModel node(TestContext c, UUID id) { return (AiNodeModel) view(c).model().getModel(id); }
    private static EditorWindow window(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
}
