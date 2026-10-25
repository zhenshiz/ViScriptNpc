package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.page.AiNpcEditorPage;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.test.NpcAiFixtures;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.UUID;

/** 通过真实数字输入和 RPC 验证草稿隔离、按标记保存及引用行为草稿的提交。 */
@LDLRegisterClient(name = "npc_ai_save", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiSaveScenario implements UIScenario {
    @Override public void configure(ScenarioOptions o) {
        o.requiresWorld(true).guiScale(2).defaultTimeoutMs(15000).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        String marker = "__ai_save_" + UUID.randomUUID();
        String actionId = "examples/patrol";
        var tree = new AiProgram();
        var call = new AiNodeData(); call.type = "call"; call.input("file", AiValue.text(actionId));
        var root = new AiNodeData(); root.type = "root"; root.children.add(call.id);
        tree.root = root.id; tree.nodes.add(root); tree.nodes.add(call);
        var action = NpcAiFixtures.action("navigate");
        var navigate = action.nodes.getLast();
        navigate.input("speed", AiValue.number(.5));
        s.server("创建两只同标记 NPC 和一只不同标记 NPC", server -> {
                    navigate.input("position", AiValue.position(server.player().position().add(10, 0, 3)));
                    for (int i = 0; i < 3; i++) {
                        var npc = NpcRegister.CUSTOM_NPC.get().create(server.level());
                        if (npc == null) throw new IllegalStateException("NPC creation failed");
                        npc.moveTo(server.player().position().add(3 + i, 0, 3));
                        npc.getNpcBasicsSetting().setNpcId(i == 2 ? marker + "_other" : marker);
                        npc.getAiController().preview(tree, Map.of(actionId, action));
                        npc.getAiController().pause();
                        npc.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 255, false, false));
                        server.level().addFreshEntity(npc);
                        server.put("save_npc_" + i, npc.getId());
                        server.put("save_generation_" + i, npc.getAiController().generation());
                    }
                    var project = new NPCProject();
                    server.check("NPC 工程默认标记为 test", project.getCurrentNpcType().equals("test"));
                    project.npc.getNpcData(com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting.class).setNpcId(marker);
                    project.npc.getNpcData(NpcAI.class).setProgram(tree);
                    project.npc.getNpcData(NpcAI.class).setActionDefinitions(new java.util.LinkedHashMap<>(Map.of(actionId, action)));
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), project.serializeNBT(Platform.getFrozenRegistry()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .click("#npc_editor_page_tab_ai").awaitElement("#npc_ai_graph_tree #npc_ai_save")
                .step("保存树编辑器引用并打开被调用的行为草稿", c -> {
                    c.put("save_tree_editor", window(c).getCurrentEditor());
                    c.el("#npc_ai_graph_tree").as(AiGraphView.class).openAction(actionId);
                    var editor = (NpcEditor) window(c).getCurrentEditor();
                    c.check("打开内置调用时保留引用标识和 NPC 的行为快照",
                            editor.getCurrentProject() instanceof NpcActionProject project
                                    && project.sourceActionId.equals(actionId) && project.program.node(navigate.id) != null);
                    c.put("save_action_editor", editor);
                }).awaitElement("#npc_ai_graph_action")
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .click("#npc_ai_node_" + navigate.id).ticks(2)
                .step("清空速度输入框", c -> c.el("#npc_ai_value_speed").as(TextField.class).setText("", false))
                .focus("#npc_ai_value_speed").type("0.").ticks(3)
                .check("0. 中间输入不会关闭界面或崩溃", c -> c.mc().screen instanceof ModularUIContainerScreen
                        && c.el("#npc_ai_value_speed").as(TextField.class).getText().equals("0."))
                .checkServer("未保存的中间输入不影响世界 NPC", server -> unchanged(server, 0) && unchanged(server, 1) && unchanged(server, 2))
                .type("3").key(Keys.ENTER).ticks(2)
                .check("行为草稿完成输入 0.3", c -> c.el("#npc_ai_graph_action").as(AiGraphView.class).model()
                        .compile().node(navigate.id).literal("speed", AiValue.number(0)).number == .3)
                .checkServer("完整参数在保存前仍不自动更新", server -> unchanged(server, 0) && unchanged(server, 1))
                .step("切回行为树", c -> window(c).showEditor(c.get("save_tree_editor")))
                .awaitElement("#npc_ai_graph_tree #npc_ai_save")
                .screenshot("ai_save_button_and_draft_tree")
                .click("#npc_ai_graph_tree #npc_ai_save")
                .waitUntilServer("保存后两只同标记 NPC 都开始使用 0.3", server ->
                        npc(server, 0).getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == .3
                                && npc(server, 1).getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == .3)
                .checkServer("不同标记 NPC 保持原 AI 与暂停状态", server -> unchanged(server, 2)
                        && npc(server, 2).getAiController().isDebugPaused())
                .checkServer("提交的是打开标签的行为草稿", server -> npc(server, 0).getNpcAI().resolveAction(actionId)
                        .node(navigate.id).literal("speed", AiValue.number(0)).number == .3)
                .check("NPC 工程保存最新行为快照，关闭行为标签后仍可恢复", c -> {
                    var project = (NPCProject) window(c).getCurrentEditor().getCurrentProject();
                    var restored = new NPCProject();
                    restored.deserializeNBT(Platform.getFrozenRegistry(), project.serializeNBT(Platform.getFrozenRegistry()));
                    var definition = restored.npc.getNpcData(NpcAI.class).getActionDefinitions().get(actionId);
                    return definition != null && definition.node(navigate.id).literal("speed", AiValue.number(0)).number == .3;
                })
                .waitUntil("保存反馈包含匹配标记及数量", c -> c.query("#npc_ai_save_result *")
                        .withText(Component.translatable("viscript_npc.ai.save.applied", marker, 2).getString()).visible().count() > 0)
                .screenshot("ai_save_applied_two_npcs")
                .step("将草稿改成无效负速度", c -> {
                    window(c).showEditor(c.get("save_action_editor"));
                    var view = c.el("#npc_ai_graph_action").as(AiGraphView.class);
                    var node = (AiNodeModel) view.model().getModel(UUID.fromString(navigate.id));
                    view.edit("parameter", () -> node.data.input("speed", AiValue.number(-1)));
                    window(c).showEditor(c.get("save_tree_editor"));
                }).step("点击保存并接收同步校验弹窗", c -> {
                    var bounds = c.el("#npc_ai_graph_tree #npc_ai_save").bounds();
                    c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                }).ticks(2)
                .check("无效参数显示已翻译的校验错误弹窗", c -> c.query(".__confirm-button__").visible().count() > 0
                        && c.query("*").withText(Component.translatable("editor.error").getString()).visible().count() > 0)
                .checkServer("无效保存保留上一份有效的 0.3 AI", server -> npc(server, 0).getNpcAI().resolveAction(actionId)
                        .node(navigate.id).literal("speed", AiValue.number(0)).number == .3)
                .screenshot("ai_invalid_save_keeps_previous_ai")
                .closeScreen()
                .teardownServer("清理本次测试生成的 NPC", server -> {
                    for (int i = 0; i < 3; i++) {
                        Integer id = server.get("save_npc_" + i);
                        if (id != null && server.level().getEntity(id) instanceof CustomNpc npc) npc.discard();
                    }
                });
    }

    private static CustomNpc npc(ServerContext server, int index) {
        return (CustomNpc) server.level().getEntity((Integer) server.get("save_npc_" + index));
    }
    private static boolean unchanged(ServerContext server, int index) {
        return npc(server, index).getAiController().generation() == (Long) server.get("save_generation_" + index)
                && npc(server, index).getNpcAI().resolveAction("examples/patrol").nodes.getLast()
                .literal("speed", AiValue.number(0)).number == .5;
    }
    private static EditorWindow window(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen)c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
}
