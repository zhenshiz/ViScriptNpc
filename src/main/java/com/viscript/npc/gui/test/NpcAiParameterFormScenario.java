package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.ui.*;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;

/** 通过真实输入验证紧凑参数表单、原生类型控件和双精度坐标的保存与撤销。 */
@LDLRegisterClient(name = "npc_ai_parameter_form", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiParameterFormScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        var fixture = NpcAiFixtures.action("find_target");
        var target = fixture.nodes.stream().filter(n -> n.type.equals("find_target")).findFirst().orElseThrow();
        var stage = new AiNodeData(); stage.type = "stage";
        var navigate = new AiNodeData(); navigate.type = "navigate";
        stage.children.add(target.id); stage.children.add(navigate.id);
        fixture.node(fixture.root).children.set(0, stage.id);
        fixture.nodes.add(stage); fixture.nodes.add(navigate);
        s.server("打开参数表单测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .click("#npc_editor_page_tab_attributes").ticks(2)
                .check("属性页仍可编辑生命值且不再提供移动速度", c ->
                        c.query("*").withText(Component.translatable("npcConfig.npcAttributes.maxHealth").getString()).visible().count() > 0
                        && c.query("*").withText("移动速度").visible().count() == 0
                        && c.query("*").withText("Movement speed").visible().count() == 0)
                .screenshot("attributes_without_movement_speed")
                .step("载入含不同参数类型的行为", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = new NpcActionProject(); project.program = fixture;
                    window.createNewEditor(NpcEditor::new).loadProject(project, null);
                    view(c).parametersView.setId("npc_ai_form_view");
                }).click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .click("#npc_ai_node_" + target.id).ticks(2)
                .check("参数直接显示为表单行且没有重复说明", c -> {
                    var rows = c.query("#npc_ai_form_view .npc_ai_parameter_row").visible().list();
                    return rows.size() == 7 && rows.stream().noneMatch(r -> r.element() instanceof ConfiguratorGroup)
                            && c.count("#npc_ai_form_view #npc_ai_node_info_find_target") == 0
                            && c.query("#npc_ai_field_range *").type(NumberConfigurator.class).count() == 1
                            && c.query("#npc_ai_field_filter *").type(SelectorConfigurator.class).count() == 1
                            && !c.el("#npc_ai_field_entity_type").element().isDisplayed();
                })
                .check("标签与控件同行且表单高度紧凑", NpcAiParameterFormScenario::compact)
                .screenshot("find_target_parameter_form")
                .step("清空原距离", c -> c.el("#npc_ai_value_range").as(
                        com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class).setText("", false))
                .typeInto("#npc_ai_value_range", "31.25").key(Keys.ENTER).ticks(2)
                .check("原生数字表单保留小数", c -> view(c).model().compile().node(target.id).inputs.get("range").value.number == 31.25)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("表单编辑支持撤销", c -> view(c).model().compile().node(target.id).literal("range", AiValue.number(24)).number != 31.25)
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .check("表单编辑支持重做", c -> view(c).model().compile().node(target.id).inputs.get("range").value.number == 31.25)
                .step("标记目标筛选下拉框", c -> c.query("#npc_ai_field_filter *").type(SelectorConfigurator.class)
                        .one().as(SelectorConfigurator.class).selector.setId("npc_ai_form_filter"))
                .click("#npc_ai_form_filter").ticks(1)
                .step("选择可攻击生物", c -> chooseFilter(c, "living")).ticks(2)
                .check("原生枚举选择写回节点", c -> view(c).model().compile().node(target.id).inputs.get("filter").value.text.equals("living"))
                .check("可攻击生物模式显示实体类型", c -> c.el("#npc_ai_field_entity_type").element().isDisplayed()
                        && c.query("#npc_ai_form_view .npc_ai_parameter_row").visible().count() == 8)
                .step("设置类型后重新选择玩家", c -> {
                    var node = (AiNodeModel) view(c).model().getModel(java.util.UUID.fromString(target.id));
                    view(c).edit("parameter", () -> node.data.input("entity_type", AiValue.mobType("minecraft:zombie")));
                    view(c).inspectNode(node);
                    markFilter(c);
                }).click("#npc_ai_form_filter").ticks(1)
                .step("切换玩家模式", c -> chooseFilter(c, "player")).ticks(2)
                .check("玩家模式隐藏实体类型且保留已填值", c -> !c.el("#npc_ai_field_entity_type").element().isDisplayed()
                        && view(c).model().compile().node(target.id).literal("entity_type", AiValue.text("")).text.equals("minecraft:zombie"))
                .check("隐藏后表单收起空白", NpcAiParameterFormScenario::compact)
                .screenshot("player_filter_hides_entity_type")
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销恢复生物模式和类型编辑", c -> c.el("#npc_ai_field_entity_type").element().isDisplayed()
                        && view(c).model().compile().node(target.id).literal("filter", AiValue.text("")).text.equals("living"))
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .check("重做再次隐藏类型编辑", c -> !c.el("#npc_ai_field_entity_type").element().isDisplayed())
                .step("标记阵营筛选下拉框", NpcAiParameterFormScenario::markFilter)
                .click("#npc_ai_form_filter").ticks(1)
                .step("切换阵营敌对模式", c -> chooseFilter(c, "hostile")).ticks(2)
                .check("阵营敌对模式不显示实体类型", c -> !c.el("#npc_ai_field_entity_type").element().isDisplayed()
                        && view(c).model().compile().node(target.id).literal("filter", AiValue.text("")).text.equals("hostile"))
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .click("#npc_ai_node_" + stage.id).ticks(2)
                .step("标记原生文本和布尔控件", c -> {
                    c.query("#npc_ai_field_name *").type(StringConfigurator.class).one().as(StringConfigurator.class)
                            .textField.setId("npc_ai_form_name");
                    c.query("#npc_ai_field_interruptible *").type(BooleanConfigurator.class).one().as(BooleanConfigurator.class)
                            .toggle.toggleButton.setId("npc_ai_form_interruptible");
                }).typeInto("#npc_ai_form_name", "prepare").key(Keys.ENTER).ticks(2)
                .click("#npc_ai_form_interruptible").ticks(2)
                .check("原生文本和开关写回节点", c -> {
                    var result = view(c).model().compile().node(stage.id);
                    return result.inputs.get("name").value.text.equals("prepare") && !result.inputs.get("interruptible").value.flag;
                }).click("#npc_ai_node_" + navigate.id).ticks(2)
                .check("移动速度默认 0.5 且标签不再表示倍率", c ->
                        c.el("#npc_ai_value_speed").as(com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class).getText().equals("0.5")
                        && c.query("#npc_ai_field_speed *").withText(Component.translatable("viscript_npc.ai.field.speed").getString()).count() > 0)
                .step("清空原移动速度", c -> c.el("#npc_ai_value_speed").as(
                        com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class).setText("", false))
                .focus("#npc_ai_value_speed").type("0.35").key(Keys.ENTER).ticks(2)
                .check("移动速度直接保存填写值", c -> view(c).model().compile().node(navigate.id).inputs.get("speed").value.number == .35)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("逐字撤销恢复上一次有效移动速度", c ->
                        view(c).model().compile().node(navigate.id).inputs.get("speed").value.number == .3)
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .check("重做恢复填写的移动速度", c -> view(c).model().compile().node(navigate.id).inputs.get("speed").value.number == .35)
                .step("标记坐标 X 输入框", c -> c.query("#npc_ai_field_position *").type(NumberConfigurator.class).list().getFirst()
                        .as(NumberConfigurator.class).textField.setId("npc_ai_form_position_x"))
                .typeInto("#npc_ai_form_position_x", "7.1234567890123").key(Keys.ENTER).ticks(2)
                .check("坐标表单保持双精度且没有轴分组", c -> Math.abs(view(c).model().compile().node(navigate.id)
                        .inputs.get("position").value.position.x - 7.1234567890123) < 1e-13
                        && c.query("#npc_ai_field_position *").type(ConfiguratorGroup.class).count() == 0)
                .screenshot("position_parameter_form")
                .step("保存重开表单数据", c -> {
                    var reopened = new AiGraph("action");
                    reopened.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    var program = reopened.model().compile();
                    c.check("表单各类型数据保存后不变", program.node(target.id).inputs.get("range").value.number == 31.25
                            && program.node(target.id).inputs.get("filter").value.text.equals("living")
                            && program.node(stage.id).inputs.get("name").value.text.equals("prepare")
                            && !program.node(stage.id).inputs.get("interruptible").value.flag
                            && program.node(navigate.id).inputs.get("speed").value.number == .35
                            && Math.abs(program.node(navigate.id).inputs.get("position").value.position.x - 7.1234567890123) < 1e-13);
                }).click("#npc_ai_node_" + target.id)
                .step("检查 GUI Scale 3", c -> { c.mc().options.guiScale().set(3); c.mc().resizeDisplay(); }).ticks(3)
                .check("高 GUI 缩放仍保持可编辑的同行布局", NpcAiParameterFormScenario::compact)
                .screenshot("find_target_parameter_form_scale3").closeScreen();
    }

    private static boolean compact(TestContext c) {
        var row = c.el("#npc_ai_field_range").as(Configurator.class);
        var label = ElementBounds.of(row.label);
        var input = c.el("#npc_ai_value_range").bounds();
        return input.width() >= 40 && label.right() <= input.x() && Math.abs(label.y() - input.y()) <= 2
                && (c.el("#npc_ai_field_entity_type").element().isDisplayed() ? c.el("#npc_ai_field_entity_type")
                    : c.el("#npc_ai_field_filter")).bounds().bottom() - c.el("#npc_ai_field_range").bounds().y()
                    < (c.el("#npc_ai_field_entity_type").element().isDisplayed() ? 110 : 88);
    }

    private static void markFilter(TestContext c) {
        c.query("#npc_ai_field_filter *").type(SelectorConfigurator.class).one().as(SelectorConfigurator.class)
                .selector.setId("npc_ai_form_filter");
    }

    private static void chooseFilter(TestContext c, String mode) {
        var bounds = c.query().type(Label.class).withText(Component.translatable("viscript_npc.ai.choice." + mode).getString())
                .visible().where(e -> e.getChildren().stream().anyMatch(child -> child.getId().equals("selector#overlayButton")))
                .one().bounds();
        c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
        c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
    }

    private static AiGraphView view(TestContext context) {
        return context.el("#npc_ai_graph_action").as(AiGraphView.class);
    }
}
