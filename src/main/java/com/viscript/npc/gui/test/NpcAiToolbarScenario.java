package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** 验证测试工具栏的数字顺序、快捷键、输入避让和服务端运行控制。 */
@LDLRegisterClient(name = "npc_ai_test_toolbar", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiToolbarScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai", "real_level", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        s.server("打开测试场景", server -> {
                    server.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    ViScriptNpcServerUtil.openNpcEditor(server.player(),
                            new NpcTestSceneProject().serializeNBT(Platform.getFrozenRegistry()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("进入测试维度", c -> NpcTestScenePersistenceScenario.clickTransition(c, "#npc_ai_test_enter"))
                .waitUntilServer("进入测试维度", server -> NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .awaitElement("#npc_ai_test_scene")
                .check("十个按钮按 1 到 0 排列，方块工具位于第二、三位", c -> {
                    var buttons = c.el("#npc_ai_test_toolbar").element().getChildren();
                    var expected = List.of("npc_ai_test_tool_select", "npc_ai_test_tool_place_block", "npc_ai_test_tool_break_block",
                            "npc_ai_test_tool_target", "npc_ai_test_tool_place_npc", "npc_ai_test_tool_place_entity",
                            "npc_ai_test_tool_place_player", "npc_ai_test_exit", "npc_ai_run", "npc_ai_pause");
                    return buttons.stream().limit(10).map(button -> button.getId()).toList().equals(expected)
                            && buttons.getLast().getId().equals("npc_ai_test_window")
                            && c.query("#npc_ai_test_toolbar .npc_ai_test_shortcut").type(Label.class).list().stream()
                            .map(ref -> ref.as(Label.class).getText().getString()).toList()
                            .equals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"));
                }).screenshot("toolbar_number_order");

        var tools = List.of("SELECT", "PLACE_BLOCK", "BREAK_BLOCK", "TARGET", "PLACE_NPC", "PLACE_ENTITY", "PLACE_PLAYER");
        for (int slot = 0; slot < tools.size(); slot++) {
            String tool = tools.get(slot);
            s.focus("#npc_ai_test_view").key(GLFW.GLFW_KEY_1 + slot)
                    .check("数字键切换工具并同步按钮选中态 " + (slot + 1), c ->
                            c.el("#npc_ai_test_view").as(NpcAiTestSceneView.class).getActiveToolName().equals(tool)
                                    && c.el("#npc_ai_test_tool_" + tool.toLowerCase(java.util.Locale.ROOT)).element().hasClass("__selected__"));
        }

        s.focus("#npc_ai_test_view").key(GLFW.GLFW_KEY_1)
                .focus("#npc_ai_test_arena_width").key(GLFW.GLFW_KEY_2).key(GLFW.GLFW_KEY_8).key(GLFW.GLFW_KEY_0)
                .check("输入框内数字不会切换工具或退出", c ->
                        c.el("#npc_ai_test_view").as(NpcAiTestSceneView.class).getActiveToolName().equals("SELECT"))
                .checkServer("输入数字后仍在测试场景", server -> NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .focus("#npc_ai_test_view").key(GLFW.GLFW_KEY_2)
                .checkExists("#npc_ai_test_block_id")
                .step("聚焦 VSL 方块补全输入框", c ->
                        c.query("#npc_ai_test_block_id *").type(com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class)
                                .list().getFirst().element().focus())
                .key(GLFW.GLFW_KEY_3)
                .check("VSL 补全输入框内不会误触破坏工具", c ->
                        c.el("#npc_ai_test_view").as(NpcAiTestSceneView.class).getActiveToolName().equals("PLACE_BLOCK"))
                .focus("#npc_ai_test_view").key(GLFW.GLFW_KEY_1)
                .server("放入可运行的测试 NPC", server -> {
                    var center = NpcAiTestLevelService.getArenaCenter(server.player());
                    var npc = NpcRegister.CUSTOM_NPC.get().create(server.level());
                    npc.moveTo(Vec3.atBottomCenterOf(center.above()).add(2, 0, 0));
                    npc.getAiController().preview(AiExamples.tree("melee"), java.util.Map.of());
                    server.level().addFreshEntity(npc);
                    server.put("toolbar_npc_id", npc.getId());
                }).waitUntil("测试 NPC 同步到客户端", c -> c.level().getEntity(c.<Integer>get("toolbar_npc_id")) instanceof CustomNpc)
                .step("悬停测试 NPC", c -> {
                    var scene = c.el("#npc_ai_test_scene").as(com.viscript.npc.gui.scene.RealLevelSceneViewport.class);
                    var point = scene.getEntityScreenPosition(c.get("toolbar_npc_id"));
                    c.put("toolbar_npc_screen", point);
                    c.input().moveTo(point.x, point.y);
                }).frames(2)
                .step("选择被测 NPC", c -> {
                    org.joml.Vector2f point = c.get("toolbar_npc_screen");
                    c.input().mouseDown(point.x, point.y, Keys.MOUSE_LEFT);
                    c.input().mouseUp(point.x, point.y, Keys.MOUSE_LEFT);
                }).waitUntil("选中被测 NPC", c ->
                        c.el("#npc_ai_test_view").as(NpcAiTestSceneView.class).getSelectedNpcId() == c.<Integer>get("toolbar_npc_id"))
                .focus("#npc_ai_test_view").key(GLFW.GLFW_KEY_0)
                .waitUntilServer("0 暂停当前 NPC", server ->
                        ((CustomNpc) server.level().getEntity(server.<Integer>get("toolbar_npc_id")))
                                .getAiController().snapshot().status.equals("paused"))
                .key(GLFW.GLFW_KEY_9)
                .waitUntilServer("9 运行当前 NPC", server ->
                        !((CustomNpc) server.level().getEntity(server.<Integer>get("toolbar_npc_id")))
                                .getAiController().snapshot().status.equals("paused"))
                .key(GLFW.GLFW_KEY_8)
                .waitUntilServer("8 退出测试并返回原世界", server -> !NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .closeScreen()
                .teardownServer("结束测试会话", server -> NpcAiTestLevelService.leave(server.player()));
    }
}
