package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

/** 验证实体变量、作用对象选择、命中连接和工程保存。 */
@LDLRegisterClient(name = "npc_ai_entity", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiEntityScenario implements UIScenario {
    @Override public void configure(ScenarioOptions o) { o.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression"); }
    @Override public void define(ScenarioBuilder s) {
        var fixture = new AiProgram(); fixture.kind = "action";
        var root = new AiNodeData(); root.type = "root"; fixture.root = root.id;
        var ranged = new AiNodeData(); ranged.type = "ranged"; root.children.add(ranged.id);
        var effect = new AiNodeData(); effect.type = "effect"; ranged.onHit = effect.id;
        fixture.nodes.add(root); fixture.nodes.add(ranged); fixture.nodes.add(effect);
        var entity = new AiVariable(); entity.name = "victim"; entity.initial = AiValue.entity(new AiEntityRef()); fixture.variables.add(entity);
        s.server("打开实体节点测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(), new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("载入带命中分支的行为", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = new NpcActionProject(); project.program = fixture;
                    window.createNewEditor(NpcEditor::new).loadProject(project, null);
                }).click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .check("命中分支独立于顺序子节点", c -> {
                    var model = view(c).model(); var n = (AiNodeModel) model.getModel(java.util.UUID.fromString(ranged.id));
                    return model.children(n).isEmpty() && model.descendants(n).size() == 1
                            && model.compile().node(ranged.id).onHit.equals(effect.id)
                            && AiValidation.validate(model.compile(), ignored -> null).isEmpty();
                })
                .click("#npc_ai_node_" + effect.id).ticks(2)
                .check("作用对象选择器提供实体变量和命中实体", c -> {
                    var source = c.el("#npc_ai_source_recipient");
                    return source != null && view(c).model().declarations().variables.getFirst().initial.type.equals("entity");
                }).click("#npc_ai_source_recipient").ticks(1)
                .step("选择实际命中实体", c -> {
                    var bounds = c.query().type(Label.class).withText(Component.translatable("viscript_npc.ai.entity.hit").getString())
                            .visible().where(e -> e.getChildren().stream().anyMatch(child -> child.getId().equals("selector#overlayButton"))).one().bounds();
                    c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                }).ticks(2)
                .check("表单将命中实体写为独立上下文绑定", c -> {
                    var b = view(c).model().compile().node(effect.id).inputs.get("recipient");
                    return b.scope.equals("context") && b.variableId.equals("hit") && b.value.type.equals("entity");
                }).screenshot("entity_hit_recipient")
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("作用对象修改可以撤销", c -> !view(c).model().compile().node(effect.id).inputs.containsKey("recipient"))
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .check("保存重开保留实体类型、命中端口和上下文", c -> {
                    var reopened = new AiGraph("action");
                    reopened.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    var p = reopened.model().compile();
                    return p.node(ranged.id).onHit.equals(effect.id) && p.node(ranged.id).children.isEmpty()
                            && p.node(effect.id).inputs.get("recipient").variableId.equals("hit")
                            && p.variables.getFirst().initial.type.equals("entity") && AiValidation.validate(p, ignored -> null).isEmpty();
                })
                .step("断开命中连接", c -> {
                    var model = view(c).model(); var n = (AiNodeModel) model.getModel(java.util.UUID.fromString(ranged.id));
                    view(c).edit("disconnect", () -> model.deleteWires(java.util.List.copyOf(n.getOutputsById().get("hit").getConnectedWires())));
                }).ticks(2)
                .check("断开后不会残留已编译命中分支", c -> view(c).model().compile().node(ranged.id).onHit.isEmpty()
                        && view(c).model().compile().nodes.size() == 2)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销断线恢复命中分支", c -> view(c).model().compile().node(ranged.id).onHit.equals(effect.id))
                .closeScreen();
    }
    private static AiGraphView view(TestContext c) { return c.el("#npc_ai_graph_action").as(AiGraphView.class); }
}
