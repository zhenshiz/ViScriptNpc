package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.NpcEditorFiles;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;

/** 验证保存场景文件后，即使原场地被清空，方块、缺口和箱子内容仍可恢复。 */
@LDLRegisterClient(name = "npc_test_scene_persistence", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcTestScenePersistenceScenario implements UIScenario {
    static Path fixtureFile() {
        return NpcEditorFiles.resolveRuntimeFile(NpcTestSceneProject.FORMAT,
                "__ldlib2_tests__/scene_persistence", true);
    }

    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).defaultTimeoutMs(15000)
                .tags("ui", "real_level", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        s.server("打开空测试场景", server -> {
                    resetFixture();
                    server.player().setGameMode(GameType.CREATIVE);
                    ViScriptNpcServerUtil.openNpcEditor(server.player(),
                            new NpcTestSceneProject().serializeNBT(Platform.getFrozenRegistry()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("进入测试维度", c -> clickTransition(c, "#npc_ai_test_enter"))
                .waitUntilServer("进入独立测试场地", server ->
                        NpcAiTestLevelService.getArenaCenter(server.player()) != null
                                && NpcAiTestEnvironment.isTestLevel(server.level()))
                .awaitElement("#npc_ai_test_scene")
                .server("搭建包含方块状态、地板缺口和箱子物品的场地", server -> {
                    BlockPos center = NpcAiTestLevelService.getArenaCenter(server.player());
                    server.put("saved_arena_center", center);
                    BlockPos origin = center.offset(-5, 0, -5);
                    server.level().setBlockAndUpdate(origin.offset(2, 1, 3), Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST));
                    server.level().setBlockAndUpdate(origin.offset(3, 2, 3), Blocks.GLASS.defaultBlockState());
                    server.level().setBlockAndUpdate(origin.offset(1, 0, 1), Blocks.AIR.defaultBlockState());
                    server.level().setBlockAndUpdate(origin.offset(9, 15, 9), Blocks.BLUE_WOOL.defaultBlockState());
                    server.level().setBlockAndUpdate(origin.offset(4, 1, 4), Blocks.CHEST.defaultBlockState());
                    ((ChestBlockEntity) server.level().getBlockEntity(origin.offset(4, 1, 4)))
                            .setItem(0, new ItemStack(Items.DIAMOND, 7));
                })
                .step("通过编辑器保存功能文件", c -> {
                    NpcEditor editor = editor(c);
                    c.setField(editor, "currentProjectFile", fixtureFile().toFile());
                    editor.saveProject(null, false);
                })
                .waitUntil("保存完成且实际文件含有方块快照", c -> {
                    if (!Files.isRegularFile(fixtureFile())) return false;
                    return readFixture()
                            .contains("scene_blocks", Tag.TAG_COMPOUND);
                })
                .step("检查文件保存相对坐标且不包含旧场地中心", c -> {
                    var saved = readFixture();
                    var blocks = saved.getCompound("scene_blocks").getList("blocks", Tag.TAG_COMPOUND);
                    boolean localPositions = !blocks.isEmpty();
                    for (int i = 0; i < blocks.size(); i++) {
                        var pos = blocks.getCompound(i).getList("pos", Tag.TAG_INT);
                        localPositions &= pos.size() == 3 && pos.getInt(0) >= 0 && pos.getInt(0) < 10
                                && pos.getInt(1) >= 0 && pos.getInt(1) < 16
                                && pos.getInt(2) >= 0 && pos.getInt(2) < 10;
                    }
                    c.check("方块全部使用场地内相对坐标", localPositions);
                    c.check("文件不依赖分配时的绝对坐标", !saved.contains(NpcAiTestEnvironment.ARENA_CENTER_TAG));
                    var reopened = (NpcTestSceneProject) loadFixture();
                    c.check("重新读取文件保留完整快照", reopened.getSceneBlocks().equals(saved.getCompound("scene_blocks")));
                    c.check("运行文件序列化也保留快照", reopened.serializeRuntimeFile(Platform.getFrozenRegistry())
                            .getCompound("scene_blocks").equals(saved.getCompound("scene_blocks")));
                })
                .server("清空旧场地并结束会话，排除依赖世界残留数据", server -> {
                    BlockPos center = server.get("saved_arena_center");
                    for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, 0, -5), center.offset(4, 15, 4))) {
                        server.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                    NpcAiTestLevelService.leave(server.player());
                }).closeScreen()
                .server("从磁盘文件重新创建编辑器工程", server -> {
                    var reopened = loadFixture();
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), reopened.serializeNBT(Platform.getFrozenRegistry()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("进入文件对应场地", c -> clickTransition(c, "#npc_ai_test_enter"))
                .waitUntilServer("文件场地重新恢复", server -> {
                    BlockPos center = NpcAiTestLevelService.getArenaCenter(server.player());
                    return center != null && NpcAiTestEnvironment.isTestLevel(server.level())
                            && server.level().getBlockState(center.offset(-3, 1, -2)).is(Blocks.OAK_STAIRS);
                }).awaitElement("#npc_ai_test_scene")
                .server("验证所有保存内容", NpcTestScenePersistenceScenario::checkRestored)
                .step("记录退出前的场景工程", c -> c.put("scene_before_exit", editor(c).getCurrentProject()))
                .screenshot("scene_restored_from_file")
                .step("退出并保存工程内的场地快照", c -> clickTransition(c, "#npc_ai_test_exit"))
                .waitUntilServer("退出前快照完成并回到原世界", server -> !NpcAiTestEnvironment.isTestLevel(server.level()))
                .check("退出后工程仍持有场地快照", c -> !c.<NpcTestSceneProject>get("scene_before_exit").getSceneBlocks().isEmpty())
                .closeScreen()
                .teardownServer("结束测试会话", server -> {
                    if (NpcAiTestEnvironment.isTestLevel(server.level())) NpcAiTestLevelService.leave(server.player());
                });
    }

    static void checkRestored(ServerContext server) {
        BlockPos origin = NpcAiTestLevelService.getArenaCenter(server.player()).offset(-5, 0, -5);
        var stair = server.level().getBlockState(origin.offset(2, 1, 3));
        server.check("台阶类型与朝向恢复", stair.is(Blocks.OAK_STAIRS) && stair.getValue(StairBlock.FACING) == Direction.EAST);
        server.check("高于地面的玻璃恢复", server.level().getBlockState(origin.offset(3, 2, 3)).is(Blocks.GLASS));
        server.check("被挖掉的地板保持空气", server.level().getBlockState(origin.offset(1, 0, 1)).isAir());
        server.check("区域最高最远角落方块恢复", server.level().getBlockState(origin.offset(9, 15, 9)).is(Blocks.BLUE_WOOL));
        var chest = server.level().getBlockEntity(origin.offset(4, 1, 4));
        server.check("箱子及服务端物品数据恢复", chest instanceof ChestBlockEntity container
                && container.getItem(0).is(Items.DIAMOND) && container.getItem(0).getCount() == 7);
        server.check("未修改的地板仍然存在", server.level().getBlockState(origin).is(Blocks.STONE));
    }

    static void clickTransition(TestContext c, String selector) {
        var bounds = c.el(selector).bounds();
        c.input().mouseDown(bounds.centerX(), bounds.centerY(), com.lowdragmc.lowdraglib2.uitest.input.Keys.MOUSE_LEFT);
        c.input().mouseUp(bounds.centerX(), bounds.centerY(), com.lowdragmc.lowdraglib2.uitest.input.Keys.MOUSE_LEFT);
    }

    @lombok.SneakyThrows
    private static void resetFixture() {
        Files.createDirectories(fixtureFile().getParent());
        Files.deleteIfExists(fixtureFile());
    }

    @lombok.SneakyThrows
    static NpcTestSceneProject loadFixture() {
        return (NpcTestSceneProject) NpcTestSceneProject.PROVIDER.loadProjectFromFile(fixtureFile().toFile());
    }

    @lombok.SneakyThrows
    private static net.minecraft.nbt.CompoundTag readFixture() {
        return NbtIo.readCompressed(fixtureFile(), NbtAccounter.unlimitedHeap());
    }

    private static NpcEditor editor(TestContext c) {
        return (NpcEditor) ((EditorWindow) ((ModularUIContainerScreen) c.mc().screen)
                .getMenu().getModularUI().ui.rootElement).getCurrentEditor();
    }
}
