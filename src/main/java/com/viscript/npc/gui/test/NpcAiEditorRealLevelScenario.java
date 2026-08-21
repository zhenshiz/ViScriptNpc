package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.data.NpcConfig;
import com.viscript.npc.gui.scene.RealLevelSceneViewport;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript_lib.gui.components.search.BlockSearchBox;
import com.viscript_lib.gui.components.search.EntityTypeSearchBox;
import com.viscript_lib.gui.editor.EditorAssetFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.thexeler.api.IntentionPriority;
import org.thexeler.intention.base.MoveIntention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 验证 NPC 编辑器能够打开真实维度测试页，并通过界面完成世界编辑、实体删除、NPC 拖拽和寻路操作。
 */
@LDLRegisterClient(
        name = "npc_ai_editor_real_level",
        group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY
)
public final class NpcAiEditorRealLevelScenario implements UIScenario {
    private static final String TEST_VIEW = "#npc_ai_test_view";
    private static final String SCENE = "#npc_ai_test_scene";
    private static final String RUNTIME_NPC_FILE = "__ldlib2_tests__/npc_ai_test_runtime";
    private static final String RUNTIME_NPC_ID = "ldlib2_runtime_npc";

    @Override
    public void configure(ScenarioOptions options) {
        options.requiresWorld(true)
                .defaultTimeoutMs(15_000)
                .scenarioTimeoutMs(180_000)
                .guiScale(2)
                .tags("ui", "editor", "real_level");
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.server("打开 NPC 编辑器", server -> {
                    server.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    writeRuntimeNpcFixture(server);
                    NpcTestSceneProject project = new NpcTestSceneProject();
                    project.initNewProject();
                    CompoundTag projectTag = project.serializeNBT(Platform.getFrozenRegistry());
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), projectTag);
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .check("测试场景由 NPC 编辑器承载", context -> {
                    if (!(context.mc().screen instanceof ModularUIContainerScreen screen)) {
                        return false;
                    }
                    return screen.getMenu().getModularUI().ui.rootElement instanceof EditorWindow editorWindow
                            && editorWindow.getCurrentEditor() instanceof NpcEditor;
                })
                .awaitElement("#npc_ai_test_enter")
                .screenshot("enter_page_centered")
                .click("#npc_ai_test_enter")
                .frames(60)
                .waitUntilServer("玩家进入 NPC AI 测试维度", server ->
                        NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .awaitElement(TEST_VIEW)
                .awaitElement(SCENE)
                .checkExists("#npc_ai_test_tools_view")
                .checkExists("#npc_ai_test_inspector_view")
                .checkExists("#npc_ai_test_arena_width")
                .checkExists("#npc_ai_test_arena_length")
                .checkText("#npc_ai_test_arena_width", "10")
                .checkText("#npc_ai_test_arena_length", "10")
                .checkServer("新场景使用 10×10 场地且不再生成萤石", server -> {
                    AABB region = NpcAiTestLevelService.getArenaRegion(server.player());
                    BlockPos center = NpcAiTestLevelService.getArenaCenter(server.player());
                    ServerLevel level = server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY);
                    return region != null && center != null && level != null
                            && Math.round(region.getXsize()) == 10
                            && Math.round(region.getZsize()) == 10
                            && level.getBlockState(center.offset(0, 0, 4)).is(Blocks.STONE);
                })
                .step("将场地宽度改为 11", context ->
                        context.el("#npc_ai_test_arena_width").as(TextField.class).setText("11"))
                .waitUntilServer("场地宽度同步到服务端", server -> {
                    AABB region = NpcAiTestLevelService.getArenaRegion(server.player());
                    return region != null && Math.round(region.getXsize()) == 11;
                })
                .waitUntil("场地宽度同步到真实视口", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class).getArenaWidth() == 11)
                .step("将场地宽度恢复为 10", context ->
                        context.el("#npc_ai_test_arena_width").as(TextField.class).setText("10"))
                .waitUntilServer("场地宽度恢复为默认值", server -> {
                    AABB region = NpcAiTestLevelService.getArenaRegion(server.player());
                    return region != null && Math.round(region.getXsize()) == 10;
                })
                .waitUntil("真实视口恢复默认场地宽度", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class).getArenaWidth() == 10)
                .hover(SCENE)
                .frames(2)
                .check("鼠标悬停得到真实方块", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class).getHoveredBlockPos() != null)
                .screenshot("real_level_block_outline")
                .step("记录左键镜头拖拽前状态", context -> {
                    RealLevelSceneViewport viewport = context.el(SCENE).as(RealLevelSceneViewport.class);
                    var bounds = context.el(SCENE).bounds();
                    float[] start = {bounds.centerX(), bounds.centerY()};
                    context.put("camera_drag_start", start);
                    context.put("camera_eye_before_drag", viewport.getCameraController().getEyePosition());
                    context.input().moveTo(start[0], start[1]);
                })
                .frames(2)
                .step("按住场景开始旋转镜头", context -> {
                    float[] start = context.get("camera_drag_start");
                    context.input().mouseDown(start[0], start[1], Keys.MOUSE_LEFT);
                })
                .step("向右下拖动场景镜头", context -> {
                    float[] start = context.get("camera_drag_start");
                    context.input().dragTo(start[0] + 24.0F, start[1] + 16.0F, Keys.MOUSE_LEFT);
                })
                .step("释放场景镜头", context -> {
                    float[] start = context.get("camera_drag_start");
                    context.input().mouseUp(start[0] + 24.0F, start[1] + 16.0F, Keys.MOUSE_LEFT);
                })
                .check("左键镜头拖拽方向与 LDLib2 虚拟场景一致", context -> {
                    Vector3f before = context.get("camera_eye_before_drag");
                    Vector3f after = context.el(SCENE).as(RealLevelSceneViewport.class)
                            .getCameraController().getEyePosition();
                    return after.x > before.x && after.y > before.y;
                })
                .step("按住场景准备恢复镜头", context -> {
                    float[] start = context.get("camera_drag_start");
                    context.input().mouseDown(start[0] + 24.0F, start[1] + 16.0F, Keys.MOUSE_LEFT);
                })
                .step("恢复场景镜头角度", context -> {
                    float[] start = context.get("camera_drag_start");
                    context.input().dragTo(start[0], start[1], Keys.MOUSE_LEFT);
                })
                .step("释放已恢复的场景镜头", context -> {
                    float[] start = context.get("camera_drag_start");
                    context.input().mouseUp(start[0], start[1], Keys.MOUSE_LEFT);
                })
                .click("#npc_ai_test_tool_place_npc")
                .checkExists("#npc_ai_test_runtime_npcs")
                .awaitElement("#" + NpcAiTestSceneView.runtimeNpcElementId(RUNTIME_NPC_FILE))
                .checkText("#" + NpcAiTestSceneView.runtimeNpcElementId(RUNTIME_NPC_FILE), RUNTIME_NPC_ID)
                .check("操作说明位于服务端 NPC 列表上方", context ->
                        context.el("#npc_ai_test_tool_help").bounds().y()
                                < context.el("#npc_ai_test_runtime_npcs").bounds().y())
                .check("操作说明使用悬停滚动文本", context ->
                        context.el("#npc_ai_test_tool_help").as(Label.class)
                                .getTextStyle().textWrap() == TextWrap.HOVER_ROLL)
                .hover("#npc_ai_test_tool_help")
                .frames(2)
                .screenshot("server_runtime_npc_list")
                .click("#" + NpcAiTestSceneView.runtimeNpcElementId(RUNTIME_NPC_FILE))
                .check("运行时 NPC 已被选择", context ->
                        context.el(TEST_VIEW).as(NpcAiTestSceneView.class)
                                .getSelectedRuntimeNpcId().equals(RUNTIME_NPC_ID))
                .click("#npc_ai_test_tool_select")
                .checkExists("#npc_ai_test_tool_select")
                .checkExists("#npc_ai_test_tool_place_npc")
                .checkExists("#npc_ai_test_tool_place_entity")
                .checkExists("#npc_ai_test_tool_place_player")
                .checkExists("#npc_ai_test_tool_place_block")
                .checkExists("#npc_ai_test_tool_break_block")
                .server("记录测试区域初始状态", server -> {
                    ServerLevel level = testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY));
                    AABB region = testRegion(server);
                    server.put("test_region", region);
                    server.put("initial_pigs", entityIds(level, Pig.class, region));
                    server.put("initial_npcs", entityIds(level, CustomNpc.class, region));
                })
                .hover(SCENE)
                .frames(2)
                .step("记录真实方块表面", context -> {
                    RealLevelSceneViewport viewport = context.el(SCENE).as(RealLevelSceneViewport.class);
                    BlockHitResult blockHit = viewport.getCurrentScenePick().blockHit();
                    if (blockHit == null) {
                        throw new IllegalStateException("编辑器测试视口未拾取到真实方块");
                    }
                    context.put("place_block", blockHit.getBlockPos().relative(blockHit.getDirection()));
                })
                .server("重置待放置方块的位置", server -> {
                    ServerLevel level = testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY));
                    BlockPos pos = server.get("place_block");
                    server.put("original_block", level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                })
                .waitForSync("待放置位置同步为空气",
                        server -> testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY))
                                .getBlockState(server.get("place_block")).isAir(),
                        context -> context.level().getBlockState(context.get("place_block")).isAir())
                .click("#npc_ai_test_tool_place_block")
                .checkExists("#npc_ai_test_block_id")
                .check("方块输入使用 VSL 自动补全", context ->
                        context.el("#npc_ai_test_block_id").as(BlockSearchBox.class)
                                .getSelectedBlockIdString().equals("minecraft:stone"))
                .click(SCENE)
                .waitUntilServer("编辑器在真实维度放置方块", server -> {
                    BlockPos pos = server.get("place_block");
                    return testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY))
                            .getBlockState(pos).is(Blocks.STONE);
                })
                .waitForSync("已放置方块同步到编辑器场景",
                        server -> testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY))
                                .getBlockState(server.get("place_block")).is(Blocks.STONE),
                        context -> context.level().getBlockState(context.get("place_block")).is(Blocks.STONE))
                .hover(SCENE)
                .frames(2)
                .click("#npc_ai_test_tool_break_block")
                .click(SCENE)
                .waitUntilServer("编辑器在真实维度破坏方块", server -> {
                    BlockPos pos = server.get("place_block");
                    return testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY))
                            .getBlockState(pos).isAir();
                })
                .click("#npc_ai_test_tool_place_npc")
                .awaitElement("#" + NpcAiTestSceneView.runtimeNpcElementId(RUNTIME_NPC_FILE))
                .click(SCENE)
                .waitUntilServer("编辑器从服务端运行时文件放入 NPC", server -> {
                    ServerLevel level = testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY));
                    Set<UUID> initial = server.get("initial_npcs");
                    CustomNpc npc = level.getEntitiesOfClass(CustomNpc.class, server.get("test_region")).stream()
                            .filter(candidate -> !initial.contains(candidate.getUUID())
                                    && RUNTIME_NPC_ID.equals(candidate.getNpcType()))
                            .findFirst()
                            .orElse(null);
                    if (npc == null) {
                        return false;
                    }
                    server.put("test_npc_id", npc.getId());
                    return true;
                })
                .waitUntil("NPC 同步到编辑器场景", context ->
                        context.level().getEntity(context.<Integer>get("test_npc_id")) instanceof CustomNpc)
                .click("#npc_ai_test_tool_select")
                .click(SCENE)
                .waitUntil("左键选中 NPC", context ->
                        context.el(TEST_VIEW).as(NpcAiTestSceneView.class).getSelectedNpcId()
                                == context.<Integer>get("test_npc_id"))
                .serverGet("记录 NPC 拖拽前位置", "npc_position_before_drag", server -> {
                    CustomNpc npc = testNpc(server);
                    return npc == null ? Vec3.ZERO : npc.position();
                })
                .step("准备从 NPC 位置开始拖拽", context -> {
                    var bounds = context.el(SCENE).bounds();
                    float[] source = {bounds.centerX(), bounds.centerY()};
                    float[] target = {bounds.x() + bounds.width() * 0.44F,
                            bounds.y() + bounds.height() * 0.58F};
                    context.put("npc_drag_source", source);
                    context.put("npc_drag_target", target);
                    context.input().moveTo(source[0], source[1]);
                })
                .frames(2)
                .step("按住 NPC", context -> {
                    float[] source = context.get("npc_drag_source");
                    context.input().mouseDown(source[0], source[1], Keys.MOUSE_LEFT);
                })
                .step("启动 NPC 拖拽", context -> {
                    float[] source = context.get("npc_drag_source");
                    context.input().dragTo(source[0] + 1.0F, source[1] + 1.0F, Keys.MOUSE_LEFT);
                })
                .step("拖动 NPC", context -> {
                    float[] source = context.get("npc_drag_source");
                    float[] target = context.get("npc_drag_target");
                    context.input().dragTo((source[0] + target[0]) * 0.5F,
                            (source[1] + target[1]) * 0.5F, Keys.MOUSE_LEFT);
                })
                .step("将 NPC 拖到目标位置", context -> {
                    float[] target = context.get("npc_drag_target");
                    context.input().dragTo(target[0], target[1], Keys.MOUSE_LEFT);
                })
                .step("释放 NPC", context -> {
                    float[] target = context.get("npc_drag_target");
                    context.input().mouseUp(target[0], target[1], Keys.MOUSE_LEFT);
                })
                .waitUntilServer("NPC 在真实维度中被拖动", server -> {
                    CustomNpc npc = testNpc(server);
                    Vec3 start = server.get("npc_position_before_drag");
                    return npc != null && npc.position().distanceToSqr(start) > 0.25D;
                })
                .step("将鼠标移动到寻路目标", context -> {
                    var bounds = context.el(SCENE).bounds();
                    float[] target = {bounds.x() + bounds.width() * 0.56F,
                            bounds.y() + bounds.height() * 0.56F};
                    context.put("path_click", target);
                    context.input().moveTo(target[0], target[1]);
                })
                .frames(2)
                .step("确认寻路目标", context -> {
                    RealLevelSceneViewport.ScenePick pick = context.el(SCENE)
                            .as(RealLevelSceneViewport.class).getCurrentScenePick();
                    if (pick.blockHit() == null) {
                        throw new IllegalStateException("编辑器测试视口未拾取到寻路目标");
                    }
                })
                .server("记录 Attention Mind 寻路前位置", server -> {
                    CustomNpc npc = testNpc(server);
                    if (npc == null) {
                        throw new IllegalStateException("未找到准备测试寻路的 NPC");
                    }
                    server.put("npc_position_before_attention_move", npc.position());
                })
                .step("右键按下寻路目标", context -> {
                    float[] target = context.get("path_click");
                    context.input().mouseDown(target[0], target[1], Keys.MOUSE_RIGHT);
                })
                .step("右键释放寻路目标", context -> {
                    float[] target = context.get("path_click");
                    context.input().mouseUp(target[0], target[1], Keys.MOUSE_RIGHT);
                })
                .waitUntilServer("NPC 接收到 Attention Mind 寻路意图", server -> {
                    CustomNpc npc = testNpc(server);
                    if (npc == null || npc.getMind() == null) {
                        return false;
                    }
                    var snapshot = npc.getMind().debugSnapshot();
                    return snapshot.active().stream().anyMatch(entry ->
                            entry.type().equals(MoveIntention.TYPE)
                                    && entry.priority() == IntentionPriority.URGENT)
                            || snapshot.queued().stream().anyMatch(entry ->
                            entry.type().equals(MoveIntention.TYPE)
                                    && entry.priority() == IntentionPriority.URGENT);
                })
                .checkServer("Attention Mind 寻路没有占用原版导航路径", server -> {
                    CustomNpc npc = testNpc(server);
                    return npc != null && npc.getNavigation().getPath() == null;
                })
                .waitUntilServer("NPC 由 Attention Mind 移动控制器驱动", server -> {
                    CustomNpc npc = testNpc(server);
                    Vec3 start = server.get("npc_position_before_attention_move");
                    return npc != null && npc.position().distanceToSqr(start) > 0.01D;
                })
                .key(Keys.BACKSPACE)
                .waitUntilServer("退格键删除选中的 NPC", server ->
                        server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY)
                                .getEntity(server.<Integer>get("test_npc_id")) == null)
                .waitUntil("删除 NPC 后清空客户端选择", context ->
                        context.el(TEST_VIEW).as(NpcAiTestSceneView.class).getSelectedEntityId() < 0)
                .click("#npc_ai_test_tool_place_entity")
                .checkExists("#npc_ai_test_entity_id")
                .check("实体输入使用 VSL 自动补全", context ->
                        context.el("#npc_ai_test_entity_id").as(EntityTypeSearchBox.class)
                                .getSelectedEntityTypeIdString().equals("minecraft:pig"))
                .check("实体放置工具已激活", context ->
                        context.el(TEST_VIEW).as(NpcAiTestSceneView.class).getActiveToolName().equals("PLACE_ENTITY"))
                .step("移动到独立的普通实体放置位置", context -> {
                    var bounds = context.el(SCENE).bounds();
                    float[] target = {bounds.x() + bounds.width() * 0.52F,
                            bounds.y() + bounds.height() * 0.55F};
                    context.put("pig_place_position", target);
                    context.input().moveTo(target[0], target[1]);
                })
                .frames(2)
                .check("普通实体放置位置命中真实方块", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class)
                                .getCurrentScenePick().blockHit() != null)
                .step("在独立位置放置普通实体", context -> {
                    float[] target = context.get("pig_place_position");
                    context.input().mouseDown(target[0], target[1], Keys.MOUSE_LEFT);
                    context.input().mouseUp(target[0], target[1], Keys.MOUSE_LEFT);
                })
                .waitUntilServer("编辑器放入真实实体", server -> {
                    ServerLevel level = testLevel(server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY));
                    Set<UUID> initial = server.get("initial_pigs");
                    Pig pig = level.getEntitiesOfClass(Pig.class, server.get("test_region")).stream()
                            .filter(candidate -> !initial.contains(candidate.getUUID()))
                            .findFirst().orElse(null);
                    if (pig == null) {
                        return false;
                    }
                    pig.setNoAi(true);
                    server.put("test_pig_id", pig.getId());
                    return true;
                })
                .waitUntil("普通实体同步到编辑器场景", context ->
                        context.level().getEntity(context.<Integer>get("test_pig_id")) instanceof Pig)
                .waitUntil("真实场景开始渲染普通实体", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class).getVisibleEntityCount() > 0)
                .frames(3)
                .screenshot("real_level_entity_shadow")
                .step("移动到普通实体位置", context -> {
                    RealLevelSceneViewport viewport = context.el(SCENE).as(RealLevelSceneViewport.class);
                    Vector2f position = viewport.getEntityScreenPosition(context.get("test_pig_id"));
                    if (position == null) {
                        throw new IllegalStateException("普通实体没有可用的场景屏幕坐标");
                    }
                    float[] target = {position.x(), position.y()};
                    context.put("pig_screen_position", target);
                    context.input().moveTo(target[0], target[1]);
                })
                .frames(2)
                .check("鼠标悬停得到普通实体", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class).getHoveredEntityId()
                                == context.<Integer>get("test_pig_id"))
                .screenshot("real_level_entity_aabb")
                .click("#npc_ai_test_tool_select")
                .step("选择前重新移动到普通实体", context -> {
                    float[] target = context.get("pig_screen_position");
                    context.input().moveTo(target[0], target[1]);
                })
                .frames(2)
                .check("选择前普通实体拾取已更新", context ->
                        context.el(SCENE).as(RealLevelSceneViewport.class).getHoveredEntityId()
                                == context.<Integer>get("test_pig_id"))
                .step("左键选择普通实体", context -> {
                    float[] target = context.get("pig_screen_position");
                    context.input().mouseDown(target[0], target[1], Keys.MOUSE_LEFT);
                    context.input().mouseUp(target[0], target[1], Keys.MOUSE_LEFT);
                })
                .waitUntil("选择工具可以选中普通实体", context ->
                        context.el(TEST_VIEW).as(NpcAiTestSceneView.class).getSelectedEntityId()
                                == context.<Integer>get("test_pig_id"))
                .serverGet("记录普通实体拖拽前位置", "pig_position_before_drag", server ->
                        server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY)
                                .getEntity(server.<Integer>get("test_pig_id")).position())
                .step("准备普通实体拖拽目标", context -> {
                    var bounds = context.el(SCENE).bounds();
                    float[] source = context.get("pig_screen_position");
                    float[] target = {bounds.x() + bounds.width() * 0.56F,
                            bounds.y() + bounds.height() * 0.58F};
                    context.put("pig_drag_target", target);
                    context.input().mouseDown(source[0], source[1], Keys.MOUSE_LEFT);
                })
                .step("启动普通实体拖拽", context -> {
                    float[] source = context.get("pig_screen_position");
                    context.input().dragTo(source[0] + 1.0F, source[1] + 1.0F, Keys.MOUSE_LEFT);
                })
                .frames(2)
                .step("拖动普通实体经过中点", context -> {
                    float[] source = context.get("pig_screen_position");
                    float[] target = context.get("pig_drag_target");
                    context.input().dragTo((source[0] + target[0]) * 0.5F,
                            (source[1] + target[1]) * 0.5F, Keys.MOUSE_LEFT);
                })
                .frames(2)
                .step("将普通实体拖到目标位置", context -> {
                    float[] target = context.get("pig_drag_target");
                    context.input().dragTo(target[0], target[1], Keys.MOUSE_LEFT);
                })
                .frames(2)
                .step("释放普通实体", context -> {
                    float[] target = context.get("pig_drag_target");
                    context.input().mouseUp(target[0], target[1], Keys.MOUSE_LEFT);
                })
                .waitUntilServer("普通实体在真实维度中被拖动", server -> {
                    Entity entity = server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY)
                            .getEntity(server.<Integer>get("test_pig_id"));
                    Vec3 start = server.get("pig_position_before_drag");
                    return entity != null && entity.position().distanceToSqr(start) > 0.25D;
                })
                .focus("#npc_ai_test_arena_width")
                .key(Keys.DELETE)
                .checkServer("文本输入框获得焦点时不会删除选中实体", server ->
                        server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY)
                                .getEntity(server.<Integer>get("test_pig_id")) instanceof Pig)
                .focus(TEST_VIEW)
                .key(Keys.DELETE)
                .waitUntilServer("Delete 键删除选中的普通实体", server ->
                        server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY)
                                .getEntity(server.<Integer>get("test_pig_id")) == null)
                .waitUntil("删除普通实体后清空客户端选择", context ->
                        context.el(TEST_VIEW).as(NpcAiTestSceneView.class).getSelectedEntityId() < 0)
                .serverGet("记录测试玩家位置", "player_position_before_place", server ->
                        server.player().position())
                .click("#npc_ai_test_tool_place_player")
                .click(SCENE)
                .waitUntilServer("编辑器重新放置真实测试玩家", server -> {
                    Vec3 start = server.get("player_position_before_place");
                    return server.player().position().distanceToSqr(start) > 0.25D;
                })
                .server("尝试通过删除动作移除真实测试玩家", server -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putInt("entityId", server.player().getId());
                    NpcAiTestLevelService.applyAction(server.player(),
                            NpcAiTestLevelService.ACTION_DELETE_ENTITY, payload);
                })
                .checkServer("删除实体操作不会移除真实玩家", server ->
                        !server.player().isRemoved()
                                && NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .click("#npc_ai_test_tool_select")
                .check("选择工具已重新激活", context ->
                        context.el(TEST_VIEW).as(NpcAiTestSceneView.class).getActiveToolName().equals("SELECT"))
                .step("点击退出测试按钮", context -> {
                    var bounds = context.el("#npc_ai_test_exit").bounds();
                    float mouseX = bounds.centerX();
                    float mouseY = bounds.centerY();
                    context.input().mouseDown(mouseX, mouseY, Keys.MOUSE_LEFT);
                    context.input().mouseUp(mouseX, mouseY, Keys.MOUSE_LEFT);
                })
                .waitUntilServer("玩家离开 NPC AI 测试维度", server ->
                        !NpcAiTestEnvironment.isTestLevel(server.player().level()))
                .closeScreen()
                .teardownServer("恢复真实维度测试现场", server -> {
                    ServerLevel level = server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY);
                    if (level != null) {
                        BlockPos blockPos = server.get("place_block");
                        BlockState originalBlock = server.get("original_block");
                        if (blockPos != null && originalBlock != null) {
                            level.setBlockAndUpdate(blockPos, originalBlock);
                        }
                        AABB region = server.get("test_region");
                        if (region != null) {
                            removeAddedEntities(level, Pig.class, server.get("initial_pigs"), region);
                            removeAddedEntities(level, CustomNpc.class, server.get("initial_npcs"), region);
                        }
                    }
                    if (NpcAiTestEnvironment.isTestLevel(server.player().level())) {
                        NpcAiTestLevelService.leave(server.player());
                    }
                    restoreRuntimeNpcFixture(server);
                })
                .teardown("关闭测试界面", context -> context.mc().setScreen(null));
    }

    private static ServerLevel testLevel(ServerLevel level) {
        if (level == null) {
            throw new IllegalStateException("NPC AI 测试维度未注册");
        }
        return level;
    }

    private static CustomNpc testNpc(ServerContext server) {
        ServerLevel level = server.server().getLevel(NpcAiTestEnvironment.LEVEL_KEY);
        Integer entityId = server.get("test_npc_id");
        if (level == null || entityId == null) {
            return null;
        }
        return level.getEntity(entityId) instanceof CustomNpc npc ? npc : null;
    }

    private static AABB testRegion(ServerContext server) {
        AABB region = NpcAiTestLevelService.getArenaRegion(server.player());
        if (region == null) {
            throw new IllegalStateException("当前玩家没有 NPC AI 测试区域");
        }
        return region;
    }

    private static <T extends Entity> Set<UUID> entityIds(
            ServerLevel level, Class<T> entityClass, AABB region) {
        Set<UUID> ids = new HashSet<>();
        for (T entity : level.getEntitiesOfClass(entityClass, region)) {
            ids.add(entity.getUUID());
        }
        return ids;
    }

    private static <T extends Entity> void removeAddedEntities(
            ServerLevel level, Class<T> entityClass, Set<UUID> initialIds, AABB region) {
        if (initialIds == null) {
            return;
        }
        for (T entity : level.getEntitiesOfClass(entityClass, region)) {
            if (!initialIds.contains(entity.getUUID())) {
                entity.discard();
            }
        }
    }

    private static void writeRuntimeNpcFixture(ServerContext server) {
        Path file = EditorAssetFiles.resolveRuntimeFile(NpcEditorFormats.NPC, RUNTIME_NPC_FILE, true);
        try {
            server.put("runtime_npc_fixture_existed", Files.isRegularFile(file));
            if (Files.isRegularFile(file)) {
                server.put("runtime_npc_fixture_backup", Files.readAllBytes(file));
            }
            Files.createDirectories(file.getParent());
            NpcConfig config = new NpcConfig();
            config.getNpcData(NpcBasicsSetting.class).setNpcId(RUNTIME_NPC_ID);
            NbtIo.writeCompressed(config.serializeNBT(Platform.getFrozenRegistry()), file);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 NPC 运行时测试文件", exception);
        }
    }

    private static void restoreRuntimeNpcFixture(ServerContext server) {
        Path file = EditorAssetFiles.resolveRuntimeFile(NpcEditorFormats.NPC, RUNTIME_NPC_FILE, true);
        try {
            if (Boolean.TRUE.equals(server.get("runtime_npc_fixture_existed"))) {
                byte[] backup = server.get("runtime_npc_fixture_backup");
                if (backup != null) {
                    Files.write(file, backup);
                }
            } else {
                Files.deleteIfExists(file);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法恢复 NPC 运行时测试文件", exception);
        }
    }
}
