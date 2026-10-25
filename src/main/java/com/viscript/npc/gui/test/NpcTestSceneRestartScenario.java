package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** 在第二次客户端启动中读取前一轮保存的场地文件，验证跨进程恢复。 */
@LDLRegisterClient(name = "npc_test_scene_restart", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcTestSceneRestartScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).defaultTimeoutMs(15000).tags("restart_fixture");
    }

    @Override public void define(ScenarioBuilder s) {
        s.server("读取上一轮客户端保存的测试场景文件", server -> {
                    server.player().setGameMode(GameType.CREATIVE);
                    var project = NpcTestScenePersistenceScenario.loadFixture();
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), project.serializeNBT(Platform.getFrozenRegistry()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("进入测试维度恢复场地", c ->
                        NpcTestScenePersistenceScenario.clickTransition(c, "#npc_ai_test_enter"))
                .waitUntilServer("重启后场地恢复到新会话", server -> {
                    var center = NpcAiTestLevelService.getArenaCenter(server.player());
                    return center != null && NpcAiTestEnvironment.isTestLevel(server.level())
                            && server.level().getBlockState(center.offset(-3, 1, -2)).is(Blocks.OAK_STAIRS);
                }).awaitElement("#npc_ai_test_scene")
                .server("重启后检查方块、相对坐标和箱子数据", NpcTestScenePersistenceScenario::checkRestored)
                .screenshot("scene_restored_after_client_restart")
                .teardownServer("结束重启测试会话", server -> NpcAiTestLevelService.leave(server.player()));
    }
}
