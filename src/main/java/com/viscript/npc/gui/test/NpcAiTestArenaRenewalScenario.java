package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.scene.RealLevelSceneViewport;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 验证玩家位于测试维度时创建新场景工程会获得新的独立场地。
 */
@LDLRegisterClient(
        name = "npc_ai_test_new_project_arena",
        group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY
)
public final class NpcAiTestArenaRenewalScenario implements UIScenario {
    private static final String SCENE = "#npc_ai_test_scene";
    private static final int COMPLETE_ARENA_BLOCK_COUNT =
            NpcAiTestEnvironment.DEFAULT_ARENA_WIDTH * NpcAiTestEnvironment.DEFAULT_ARENA_LENGTH;

    @Override
    public void configure(ScenarioOptions options) {
        options.requiresWorld(true)
                .guiScale(2)
                .tags("ui", "editor", "real_level", "regression");
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.server("打开第一个测试场景工程", server -> {
                    server.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    NpcTestSceneProject project = new NpcTestSceneProject();
                    project.initNewProject();
                    CompoundTag projectTag = project.serializeNBT(Platform.getFrozenRegistry());
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), projectTag);
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .awaitElement("#npc_ai_test_enter")
                .click("#npc_ai_test_enter")
                .frames(60)
                .waitUntilServer("第一个场地完成分配", server -> {
                    BlockPos center = NpcAiTestLevelService.getArenaCenter(server.player());
                    if (center == null || !NpcAiTestEnvironment.isTestLevel(server.player().serverLevel())) {
                        return false;
                    }
                    server.put("first_arena_center", center);
                    return true;
                })
                .awaitElement(SCENE)
                .waitUntil("第一个场地完成真实方块渲染", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class).getVisibleBlockCount()
                                >= COMPLETE_ARENA_BLOCK_COUNT)
                .step("将鼠标移动到第一个场景中心", context -> {
                    BlockPos firstCenter = context.get("first_arena_center");
                    var bounds = context.query(SCENE)
                            .where(element -> element instanceof RealLevelSceneViewport viewport
                                    && viewport.getSceneCenter().equals(firstCenter))
                            .one().bounds();
                    context.input().moveTo(bounds.centerX(), bounds.centerY());
                })
                .frames(2)
                .step("校验第一个场景中心的鼠标射线", context -> {
                    BlockPos firstCenter = context.get("first_arena_center");
                    var blockHit = context.query(SCENE)
                            .where(element -> element instanceof RealLevelSceneViewport viewport
                                    && viewport.getSceneCenter().equals(firstCenter))
                            .one().as(RealLevelSceneViewport.class).getCurrentScenePick().blockHit();
                    BlockPos actual = blockHit == null ? null : blockHit.getBlockPos();
                    BlockPos offset = actual == null ? null : actual.subtract(firstCenter);
                    context.put("first_center_pick_offset", offset);
                    boolean nearCenter = offset != null && offset.getY() == 0
                            && Math.abs(offset.getX()) <= 1 && Math.abs(offset.getZ()) <= 1;
                    context.check("第一个场景中心的鼠标射线命中中心附近方块",
                            nearCenter, "x/z 偏移不超过 1，y 偏移为 0", offset);
                })
                .step("在测试维度创建新的测试场景工程", context -> {
                    if (!(context.mc().screen instanceof ModularUIContainerScreen screen)
                            || !(screen.getMenu().getModularUI().ui.rootElement
                            instanceof EditorWindow editorWindow)
                            || !(editorWindow.getCurrentEditor() instanceof NpcEditor previousEditor)) {
                        throw new IllegalStateException("当前界面不是 NPC 编辑器");
                    }
                    NpcEditor newEditor = (NpcEditor) editorWindow.createNewEditor(NpcEditor::new);
                    editorWindow.removeEditor(previousEditor);
                    editorWindow.editorContainer.removeChild(previousEditor);
                    NpcTestSceneProject project = new NpcTestSceneProject();
                    project.initNewProject();
                    newEditor.loadProject(project, null);
                })
                .waitUntilServer("新工程获得不同的场地", server -> {
                    BlockPos firstCenter = server.get("first_arena_center");
                    BlockPos newCenter = NpcAiTestLevelService.getArenaCenter(server.player());
                    if (newCenter == null || newCenter.equals(firstCenter)) {
                        return false;
                    }
                    server.put("new_arena_center", newCenter);
                    return true;
                })
                .waitUntil("新工程收到服务端场地坐标", context -> {
                    if (!(context.mc().screen instanceof ModularUIContainerScreen screen)
                            || !(screen.getMenu().getModularUI().ui.rootElement
                            instanceof EditorWindow editorWindow)
                            || !(editorWindow.getCurrentEditor() instanceof NpcEditor editor)
                            || !(editor.getCurrentProject() instanceof NpcTestSceneProject project)) {
                        return false;
                    }
                    BlockPos newCenter = context.get("new_arena_center");
                    return project.hasRuntimeArenaCenter()
                            && project.getRuntimeArenaCenter().equals(newCenter);
                })
                .awaitElement(SCENE)
                .waitUntil("新场地完成真实方块渲染", context -> {
                    BlockPos newCenter = context.get("new_arena_center");
                    var scene = context.query(SCENE)
                            .where(element -> element instanceof RealLevelSceneViewport viewport
                                    && viewport.getSceneCenter().equals(newCenter))
                            .optional();
                    return scene.isPresent()
                            && scene.get().as(RealLevelSceneViewport.class).getVisibleBlockCount()
                            >= COMPLETE_ARENA_BLOCK_COUNT;
                })
                .check("视口使用新分配的场地中心", context -> {
                    BlockPos newCenter = context.get("new_arena_center");
                    return context.query(SCENE)
                            .where(element -> element instanceof RealLevelSceneViewport viewport
                                    && viewport.getSceneCenter().equals(newCenter))
                            .count() == 1;
                })
                .step("将鼠标移动到新场景中心", context -> {
                    BlockPos newCenter = context.get("new_arena_center");
                    var bounds = context.query(SCENE)
                            .where(element -> element instanceof RealLevelSceneViewport viewport
                                    && viewport.getSceneCenter().equals(newCenter))
                            .one().bounds();
                    context.input().moveTo(bounds.centerX(), bounds.centerY());
                })
                .frames(2)
                .step("校验新建 UI 的鼠标中心", context -> {
                    BlockPos newCenter = context.get("new_arena_center");
                    var blockHit = context.query(SCENE)
                            .where(element -> element instanceof RealLevelSceneViewport viewport
                                    && viewport.getSceneCenter().equals(newCenter))
                            .one().as(RealLevelSceneViewport.class).getCurrentScenePick().blockHit();
                    BlockPos actual = blockHit == null ? null : blockHit.getBlockPos();
                    BlockPos offset = actual == null ? null : actual.subtract(newCenter);
                    BlockPos firstOffset = context.get("first_center_pick_offset");
                    context.check("新建 UI 沿用相同的局部鼠标拾取坐标",
                            firstOffset != null && firstOffset.equals(offset), firstOffset, offset);
                })
                .screenshot("new_project_uses_new_arena")
                .server("离开 NPC AI 测试维度", server ->
                        NpcAiTestLevelService.leave(server.player()))
                .closeScreen()
                .teardownServer("离开测试维度", server -> {
                    if (NpcAiTestEnvironment.isTestLevel(server.player().level())) {
                        NpcAiTestLevelService.leave(server.player());
                    }
                })
                .teardown("关闭测试界面", context -> context.mc().setScreen(null));
    }
}
