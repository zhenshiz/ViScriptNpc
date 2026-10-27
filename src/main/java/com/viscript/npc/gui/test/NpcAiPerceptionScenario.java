package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript.npc.util.NpcAiPublication;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** 验证整棵树模板、感知自动表单、撤销保存及真实测试场景中的感知线框。 */
@LDLRegisterClient(name = "npc_ai_perception", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiPerceptionScenario implements UIScenario {
    @Override public void configure(ScenarioOptions o) {
        o.requiresWorld(true).guiScale(2).defaultTimeoutMs(15000).scenarioTimeoutMs(120_000).tags("ui", "ai", "real_level", "regression");
    }
    @Override public void define(ScenarioBuilder s) {
        s.server("打开行为树工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .click("#npc_editor_page_tab_ai").click("#npc_ai_examples")
                .checkExists("#npc_ai_tree_example_guard")
                .checkNotExists("#npc_ai_example_melee")
                .step("保存原工程并打开守卫树副本", c -> {
                    c.put("perception_original_editor", window(c).getCurrentEditor());
                    c.put("perception_original_graph", view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    click(c, "#npc_ai_tree_example_guard");
                }).awaitElement("#npc_ai_graph_tree")
                .step("标记当前守卫画布", c -> view(c).setId("npc_ai_perception_graph"))
                .click("#npc_ai_perception_graph #npc_ai_fit").ticks(2)
                .check("守卫树独立调用巡逻与追击攻击行为", c -> {
                    var p = view(c).model().compile();
                    var sense = p.node(p.node(p.root).children.getFirst());
                    var selector = p.node(sense.children.getFirst());
                    return window(c).getCurrentEditor() != c.get("perception_original_editor")
                            && sense.type.equals("guard_area") && selector.type.equals("reactive_selector") && selector.children.size() == 3
                            && p.node(selector.children.getFirst()).type.equals("reactive_sequence")
                            && p.node(p.node(selector.children.getFirst()).children.getFirst()).type.equals("returning_home")
                            && p.node(selector.children.get(2)).literal("file", AiValue.text("")).text.equals("examples/patrol")
                            && p.node(p.node(selector.children.get(1)).children.get(1)).literal("file", AiValue.text("")).text.equals("examples/pursue_melee")
                            && p.nodes.stream().filter(n -> n.type.equals("call")).count() == 3
                            && AiValidation.validate(p, NpcActionFiles::loadLocal).isEmpty();
                }).step("选择检测目标条件节点", c -> {
                    var sense = view(c).model().compile().nodes.stream().filter(n -> n.type.equals("guard_area")).findFirst().orElseThrow();
                    c.put("perception_sense_id", sense.id);
                    var b = c.el("#npc_ai_node_" + sense.id).bounds();
                    c.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                }).ticks(2)
                .check("组合感知显示圆圈与扇形尺寸表单", c -> c.el("#npc_ai_field_range").element().isDisplayed()
                        && c.el("#npc_ai_field_view_radius").element().isDisplayed()
                        && c.el("#npc_ai_field_view_angle").element().isDisplayed()
                        && c.el("#npc_ai_field_leash_range").element().isDisplayed()
                        && c.el("#npc_ai_field_memory_seconds").element().isDisplayed())
                .step("编辑视野半径", c -> c.el("#npc_ai_value_view_radius").as(TextField.class).setText("", false))
                .typeInto("#npc_ai_value_view_radius", "8.5").key(Keys.ENTER).ticks(2)
                .check("自动数字表单写回尺寸", c -> view(c).model().compile().node(c.get("perception_sense_id"))
                        .literal("view_radius", AiValue.number(0)).number == 8.5)
                .step("编辑扇形夹角", c -> c.el("#npc_ai_value_view_angle").as(TextField.class).setText("", false))
                .typeInto("#npc_ai_value_view_angle", "120").key(Keys.ENTER).ticks(2)
                .check("完整夹角写回节点", c -> view(c).model().compile().node(c.get("perception_sense_id"))
                        .literal("view_angle", AiValue.number(0)).number == 120)
                .step("标记感知模式选择框", c -> c.query("#npc_ai_field_perception *").type(SelectorConfigurator.class)
                        .one().as(SelectorConfigurator.class).selector.setId("npc_ai_perception_mode"))
                .click("#npc_ai_perception_mode").ticks(1)
                .step("选择仅前方视野", c -> choose(c, "view")).ticks(2)
                .check("仅视野模式隐藏圆圈半径并保留扇形编辑", c -> !c.el("#npc_ai_field_range").element().isDisplayed()
                        && c.el("#npc_ai_field_view_radius").element().isDisplayed())
                .click("#npc_ai_perception_graph #npc_ai_undo").ticks(2)
                .check("撤销恢复组合感知", c -> c.el("#npc_ai_field_range").element().isDisplayed())
                .check("保存重开保留感知参数和树结构", c -> {
                    var graph = new AiGraph("tree"); graph.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    var p = graph.model().compile();
                    return p.node(c.get("perception_sense_id")).literal("view_radius", AiValue.number(0)).number == 8.5
                            && p.node(c.get("perception_sense_id")).literal("view_angle", AiValue.number(0)).number == 120
                            && AiValidation.validate(p, NpcActionFiles::loadLocal).isEmpty();
                }).screenshot("guard_tree_perception_form")
                .step("检查原树未被模板替换", c -> {
                    window(c).showEditor(c.get("perception_original_editor"));
                    c.check("原工程保持原样", c.<net.minecraft.nbt.CompoundTag>get("perception_original_graph")
                            .equals(view(c).model().serializeNBT(Platform.getFrozenRegistry())));
                    window(c).createNewEditor(NpcEditor::new).loadProject(new NpcTestSceneProject(), null);
                }).awaitElement("#npc_ai_test_enter")
                .step("进入真实测试场景", c -> NpcTestScenePersistenceScenario.clickTransition(c, "#npc_ai_test_enter"))
                .waitUntilServer("测试维度就绪", server -> NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .awaitElement("#npc_ai_test_scene")
                .server("放入组合感知 NPC", server -> {
                    var center = NpcAiTestLevelService.getArenaCenter(server.player());
                    var npc = NpcRegister.CUSTOM_NPC.get().create(server.level());
                    npc.moveTo(Vec3.atBottomCenterOf(center.above())); npc.setYHeadRot(180);
                    var p = AiExamples.tree("guard");
                    var sense = p.nodes.stream().filter(n -> n.type.equals("guard_area")).findFirst().orElseThrow();
                    sense.input("range", AiValue.number(2)).input("view_radius", AiValue.number(4)).input("view_angle", AiValue.number(90));
                    var dependencies = NpcAiPublication.readDependencies(NpcAiPublication.bundle(p, NpcActionFiles::load));
                    npc.getAiController().preview(p, dependencies); server.level().addFreshEntity(npc);
                    // 保持真实感知和寻路运行，用缓慢效果防止实体走离截图点击位置。
                    npc.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 200, 255, false, false));
                    npc.getAiController().tick();
                    server.put("perception_npc_id", npc.getId());
                }).waitUntil("NPC 同步", c -> c.level().getEntity(c.<Integer>get("perception_npc_id")) instanceof CustomNpc)
                .waitUntil("场景投影就绪", c -> c.el("#npc_ai_test_scene").as(com.viscript.npc.gui.scene.RealLevelSceneViewport.class)
                        .getEntityScreenPosition(c.get("perception_npc_id")) != null)
                .step("悬停被测 NPC", c -> {
                    var point = c.el("#npc_ai_test_scene").as(com.viscript.npc.gui.scene.RealLevelSceneViewport.class)
                            .getEntityScreenPosition(c.get("perception_npc_id"));
                    c.put("perception_point", point); c.input().moveTo(point.x, point.y);
                }).frames(2)
                .step("选择被测 NPC", c -> {
                    org.joml.Vector2f point = c.get("perception_point");
                    c.input().mouseDown(point.x, point.y, Keys.MOUSE_LEFT); c.input().mouseUp(point.x, point.y, Keys.MOUSE_LEFT);
                }).waitUntil("测试场景选中 NPC", c -> c.el("#npc_ai_test_view").as(NpcAiTestSceneView.class).getSelectedNpcId() == c.<Integer>get("perception_npc_id"))
                .click("#npc_ai_run")
                .waitUntilServer("被测 NPC 恢复运行", server -> !((CustomNpc) server.level().getEntity(server.<Integer>get("perception_npc_id")))
                        .getAiController().snapshot().status.equals("paused"))
                .waitUntil("收到服务器感知快照", c -> AiClientDebug.latest.entityId == c.<Integer>get("perception_npc_id")
                        && AiClientDebug.latest.perceptions.size() == 1)
                .check("调试数据携带实际圆圈、扇形与高度", c -> {
                    var v = AiClientDebug.latest.perceptions.getFirst();
                    return v.hasRange() && v.hasView() && v.range == 2 && v.viewRadius == 4 && v.viewAngle == 90 && v.height == 6 && v.requireSight;
                }).ticks(3).screenshot("combined_perception_in_test_scene")
                .step("退出测试", c -> NpcTestScenePersistenceScenario.clickTransition(c, "#npc_ai_test_exit"))
                .waitUntilServer("返回原世界", server -> !NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .closeScreen().teardownServer("清理场地", server -> NpcAiTestLevelService.leave(server.player()));
    }
    private static EditorWindow window(TestContext c) { return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement; }
    private static AiGraphView view(TestContext c) { return (AiGraphView) window(c).getCurrentEditor().select("#npc_ai_graph_tree").findFirst()
            .or(() -> window(c).getCurrentEditor().select("#npc_ai_perception_graph").findFirst()).orElseThrow(); }
    private static void click(TestContext c, String selector) {
        var b = c.el(selector).bounds(); c.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_LEFT); c.input().mouseUp(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
    }
    private static void choose(TestContext c, String mode) {
        var b = c.query().type(Label.class).withText(Component.translatable("viscript_npc.ai.choice." + mode).getString()).visible()
                .where(e -> e.getChildren().stream().anyMatch(child -> child.getId().equals("selector#overlayButton"))).one().bounds();
        c.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_LEFT); c.input().mouseUp(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
    }
}
