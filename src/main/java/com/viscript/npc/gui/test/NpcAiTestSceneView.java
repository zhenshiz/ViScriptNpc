package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.scene.RealLevelSceneViewport;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import com.viscript_lib.gui.components.search.BlockSearchBox;
import com.viscript_lib.gui.components.search.EntityTypeSearchBox;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在独立场景工程中提供真实维度渲染、场景编辑和 NPC 寻路测试。
 *
 * <p>客户端仅负责工具状态和拾取。所有方块、实体、玩家与 NPC 操作都通过 LDLib2 RPC 交给服务端校验并执行。
 */
public final class NpcAiTestSceneView extends View {
    private static final long NPC_DRAG_INTERVAL_MILLIS = 75L;

    private final NpcTestSceneProject project;
    private final NpcAiTestToolboxView toolboxView;
    private final NpcAiTestInspectorView inspectorView;
    private Tool activeTool = Tool.SELECT;
    private String blockId = "minecraft:stone";
    private String entityId = "minecraft:pig";
    private String selectedRuntimeNpcFileId = "";
    private int selectedEntityId = -1;
    private int runtimeNpcListRevision;
    private boolean runtimeNpcListLoading;
    private boolean draggingEntity;
    private long lastNpcDragTime;
    private boolean displayedTestLevel;
    @Nullable
    private RealLevelSceneViewport viewport;

    /**
     * 创建绑定到独立场景工程的真实维度测试视图。
     *
     * @param project 保存 NPC 配置快照的场景工程
     */
    public NpcAiTestSceneView(NpcTestSceneProject project) {
        super(ViScriptNpc.MOD_ID + ".editor.view.ai_test");
        this.project = project;
        runtimeNpcListRevision = ViScriptNpcClientUtil.getNpcAiTestRuntimeNpcRevision();
        toolboxView = new NpcAiTestToolboxView(this);
        inspectorView = new NpcAiTestInspectorView(this);
        setFloatable(false);
        setFocusable(true);
        setId("npc_ai_test_view");
        layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(4).gapAll(3));
        addEventListener(UIEvents.KEY_DOWN, event -> {
            if (!(event.target instanceof TextField)
                    && (event.keyCode == GLFW.GLFW_KEY_DELETE
                    || event.keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
                deleteSelectedEntity();
                event.stopPropagation();
            }
        });
        displayedTestLevel = isTestLevelActive();
        rebuild();
    }

    @Override
    public void screenTick() {
        super.screenTick();
        boolean testLevelActive = isTestLevelActive();
        if (testLevelActive != displayedTestLevel) {
            displayedTestLevel = testLevelActive;
            rebuild();
            return;
        }
        if (selectedEntityId >= 0) {
            Entity entity = Minecraft.getInstance().level == null
                    ? null : Minecraft.getInstance().level.getEntity(selectedEntityId);
            if (entity == null || entity.isRemoved()) {
                selectedEntityId = -1;
                if (viewport != null) {
                    viewport.setHighlightedEntityId(-1);
                }
                updateStatus();
            }
        }
        int currentRuntimeNpcListRevision = ViScriptNpcClientUtil.getNpcAiTestRuntimeNpcRevision();
        if (currentRuntimeNpcListRevision != runtimeNpcListRevision) {
            runtimeNpcListRevision = currentRuntimeNpcListRevision;
            runtimeNpcListLoading = false;
            boolean selectionExists = ViScriptNpcClientUtil.getNpcAiTestRuntimeNpcs().stream()
                    .anyMatch(entry -> entry.fileId().equals(selectedRuntimeNpcFileId));
            if (!selectionExists) {
                selectedRuntimeNpcFileId = "";
            }
            if (activeTool == Tool.PLACE_NPC) {
                inspectorView.rebuild();
            }
        }
    }

    /**
     * 获取当前场景工具，供真实客户端界面测试断言使用。
     *
     * @return 当前工具的稳定名称
     */
    public String getActiveToolName() {
        return activeTool.name();
    }

    /**
     * 获取当前选中的 NPC 实体编号。
     *
     * @return 客户端实体编号；没有选中 NPC 时返回 {@code -1}
     */
    public int getSelectedNpcId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getEntity(selectedEntityId) instanceof CustomNpc
                ? selectedEntityId : -1;
    }

    /**
     * 获取当前选中的实体编号，供真实客户端界面测试断言使用。
     *
     * @return 客户端实体编号；没有选中实体时返回 {@code -1}
     */
    public int getSelectedEntityId() {
        return selectedEntityId;
    }

    /**
     * 获取当前选择的服务端运行时 NPC 标识。
     *
     * @return 已选文件中的 <code>npcId</code>；尚未选择时返回空字符串
     */
    public String getSelectedRuntimeNpcId() {
        return ViScriptNpcClientUtil.getNpcAiTestRuntimeNpcs().stream()
                .filter(entry -> entry.fileId().equals(selectedRuntimeNpcFileId))
                .map(ViScriptNpcClientUtil.NpcRuntimeEntry::npcId)
                .findFirst()
                .orElse("");
    }

    /**
     * 获取测试场景底部工具视图。
     *
     * @return 负责显示工具按钮和场景状态的视图
     */
    public View getToolboxView() {
        return toolboxView;
    }

    /**
     * 获取测试场景右侧参数视图。
     *
     * @return 负责显示当前工具参数的视图
     */
    public View getInspectorView() {
        return inspectorView;
    }

    private void rebuild() {
        clearAllChildren();
        viewport = null;
        if (!displayedTestLevel) {
            buildEnterPage();
        } else {
            buildTestPage();
        }
        toolboxView.rebuild();
        inspectorView.rebuild();
    }

    private void buildEnterPage() {
        UIElement panel = new UIElement();
        panel.setId("npc_ai_test_enter_panel");
        panel.addClass("panel_bg");
        panel.layout(layout -> layout.widthPercent(100).heightPercent(100)
                .alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER).gapAll(6));

        Label explanation = new Label();
        explanation.setText(Component.translatable("viscript_npc.editor.ai_test.enter_description"));
        explanation.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        explanation.layout(layout -> layout.widthPercent(100).height(18));

        Button enter = new Button();
        enter.setId("npc_ai_test_enter");
        enter.setText(Component.translatable("viscript_npc.editor.ai_test.enter"));
        enter.addPreIcon(Icons.PLAY);
        enter.setOnClick(event -> {
            if (event.button == 0) {
                RPCPacketDistributor.rpcToServer(
                        C2SPayload.ENTER_NPC_AI_TEST,
                        project.serializeNBT(Platform.getFrozenRegistry()));
            }
        });
        enter.layout(layout -> layout.width(140).height(20));
        panel.addChildren(explanation, enter);
        addChild(panel);
    }

    private void buildTestPage() {
        viewport = new RealLevelSceneViewport(
                project.getRuntimeArenaCenter(), project.getArenaWidth(), project.getArenaLength());
        viewport.setId("npc_ai_test_scene");
        viewport.setRenderLocalPlayer(true);
        viewport.setHighlightedEntityId(selectedEntityId);
        viewport.setInteractionHandler(new TestInteractionHandler());
        viewport.setSceneChangedListener(ignored -> updateStatus());
        addChild(viewport);
    }

    private void selectTool(Tool tool) {
        activeTool = tool;
        toolboxView.selectTool(tool);
        if (tool == Tool.PLACE_NPC) {
            requestRuntimeNpcList();
        } else {
            inspectorView.rebuild();
        }
        updateStatus();
    }

    private void requestRuntimeNpcList() {
        runtimeNpcListLoading = true;
        ViScriptNpcClientUtil.clearNpcAiTestRuntimeNpcs();
        inspectorView.rebuild();
        RPCPacketDistributor.rpcToServer(C2SPayload.REQUEST_NPC_AI_TEST_RUNTIME_NPCS);
    }

    private void updateStatus() {
        if (toolboxView == null) {
            return;
        }
        toolboxView.updateStatus();
    }

    private void resizeArena(int width, int length) {
        if (!project.setArenaSize(width, length)) {
            return;
        }
        if (viewport != null) {
            viewport.setArenaSize(project.getArenaWidth(), project.getArenaLength());
        }
        CompoundTag payload = new CompoundTag();
        payload.putInt("width", project.getArenaWidth());
        payload.putInt("length", project.getArenaLength());
        sendAction(NpcAiTestLevelService.ACTION_RESIZE_ARENA, payload);
    }

    private void applyTool(RealLevelSceneViewport.ScenePick pick) {
        switch (activeTool) {
            case PLACE_BLOCK -> sendBlockAction(NpcAiTestLevelService.ACTION_PLACE_BLOCK, pick.blockHit());
            case BREAK_BLOCK -> sendBlockAction(NpcAiTestLevelService.ACTION_BREAK_BLOCK, pick.blockHit());
            case PLACE_NPC -> sendPositionAction(NpcAiTestLevelService.ACTION_PLACE_NPC, pick, true);
            case PLACE_ENTITY -> sendPositionAction(NpcAiTestLevelService.ACTION_PLACE_ENTITY, pick, false);
            case PLACE_PLAYER -> sendPositionAction(NpcAiTestLevelService.ACTION_PLACE_PLAYER, pick, false);
            case SELECT -> {
            }
        }
    }

    private void sendBlockAction(String action, @Nullable BlockHitResult hit) {
        if (hit == null) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putLong("blockPos", hit.getBlockPos().asLong());
        payload.putString("direction", hit.getDirection().name());
        payload.putString("block", blockId);
        sendAction(action, payload);
    }

    private void sendPositionAction(String action, RealLevelSceneViewport.ScenePick pick, boolean includeNpc) {
        Vec3 position = surfacePosition(pick);
        if (position == null) {
            return;
        }
        CompoundTag payload = positionPayload(position);
        if (includeNpc) {
            if (selectedRuntimeNpcFileId.isBlank()) {
                return;
            }
            payload.putString("npcFile", selectedRuntimeNpcFileId);
        }
        payload.putString("entity", entityId);
        sendAction(action, payload);
    }

    private void moveSelectedNpc(RealLevelSceneViewport.ScenePick pick, boolean force) {
        if (!draggingEntity || selectedEntityId < 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastNpcDragTime < NPC_DRAG_INTERVAL_MILLIS) {
            return;
        }
        Vec3 position = surfacePosition(pick);
        if (position == null) {
            return;
        }
        lastNpcDragTime = now;
        CompoundTag payload = positionPayload(position);
        payload.putInt("entityId", selectedEntityId);
        sendAction(NpcAiTestLevelService.ACTION_MOVE_ENTITY, payload);
    }

    private void pathfindSelectedNpc(RealLevelSceneViewport.ScenePick pick) {
        if (selectedEntityId < 0 || Minecraft.getInstance().level == null
                || !(Minecraft.getInstance().level.getEntity(selectedEntityId) instanceof CustomNpc npc)
                || !npc.getNpcAI().isEnabled()) {
            return;
        }
        Vec3 position = surfacePosition(pick);
        if (position == null) {
            return;
        }
        CompoundTag payload = positionPayload(position);
        payload.putInt("entityId", selectedEntityId);
        sendAction(NpcAiTestLevelService.ACTION_PATHFIND_NPC, payload);
    }

    private void deleteSelectedEntity() {
        if (selectedEntityId < 0 || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(selectedEntityId);
        if (entity == null || entity.isRemoved() || entity instanceof Player) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putInt("entityId", selectedEntityId);
        sendAction(NpcAiTestLevelService.ACTION_DELETE_ENTITY, payload);
        selectedEntityId = -1;
        draggingEntity = false;
        if (viewport != null) {
            viewport.setHighlightedEntityId(-1);
        }
        updateStatus();
    }

    private static void sendAction(String action, CompoundTag payload) {
        RPCPacketDistributor.rpcToServer(C2SPayload.APPLY_NPC_AI_TEST_ACTION, action, payload);
    }

    @Nullable
    private static Vec3 surfacePosition(RealLevelSceneViewport.ScenePick pick) {
        if (pick.blockHit() != null) {
            Vec3 location = pick.blockHit().getLocation();
            return location.add(Vec3.atLowerCornerOf(pick.blockHit().getDirection().getNormal()).scale(0.02D));
        }
        return pick.worldPosition();
    }

    private static CompoundTag positionPayload(Vec3 position) {
        CompoundTag payload = new CompoundTag();
        payload.putDouble("x", position.x());
        payload.putDouble("y", position.y());
        payload.putDouble("z", position.z());
        return payload;
    }

    private static boolean isTestLevelActive() {
        return NpcAiTestEnvironment.isTestLevel(Minecraft.getInstance().level);
    }

    static String runtimeNpcElementId(String fileId) {
        return "npc_ai_test_runtime_npc_" + Integer.toUnsignedString(fileId.hashCode(), 36);
    }

    private final class TestInteractionHandler implements RealLevelSceneViewport.SceneInteractionHandler {
        @Override
        public boolean beginLeft(RealLevelSceneViewport.ScenePick pick) {
            if (activeTool != Tool.SELECT) {
                applyTool(pick);
                return true;
            }
            if (pick.entity() != null) {
                selectedEntityId = pick.entity().getId();
                if (viewport != null) {
                    viewport.setHighlightedEntityId(selectedEntityId);
                }
                draggingEntity = true;
                lastNpcDragTime = 0L;
                updateStatus();
                return true;
            }
            selectedEntityId = -1;
            if (viewport != null) {
                viewport.setHighlightedEntityId(-1);
            }
            draggingEntity = false;
            updateStatus();
            return false;
        }

        @Override
        public void dragLeft(RealLevelSceneViewport.ScenePick pick) {
            moveSelectedNpc(pick, false);
        }

        @Override
        public void endLeft(RealLevelSceneViewport.ScenePick pick) {
            moveSelectedNpc(pick, true);
            draggingEntity = false;
        }

        @Override
        public void rightClick(RealLevelSceneViewport.ScenePick pick) {
            pathfindSelectedNpc(pick);
        }
    }

    private enum Tool {
        SELECT("select", "viscript_npc.editor.ai_test.tool.select", 62.0F),
        PLACE_NPC("place_npc", "viscript_npc.editor.ai_test.tool.place_npc", 70.0F),
        PLACE_ENTITY("place_entity", "viscript_npc.editor.ai_test.tool.place_entity", 70.0F),
        PLACE_PLAYER("place_player", "viscript_npc.editor.ai_test.tool.place_player", 70.0F),
        PLACE_BLOCK("place_block", "viscript_npc.editor.ai_test.tool.place_block", 70.0F),
        BREAK_BLOCK("break_block", "viscript_npc.editor.ai_test.tool.break_block", 70.0F);

        private final String id;
        private final String translationKey;
        private final float width;

        Tool(String id, String translationKey, float width) {
            this.id = id;
            this.translationKey = translationKey;
            this.width = width;
        }
    }

    private static final class NpcAiTestToolboxView extends View {
        private final NpcAiTestSceneView sceneView;
        private final Map<Tool, Tab> toolTabs = new EnumMap<>(Tool.class);
        @Nullable
        private Label statusLabel;

        private NpcAiTestToolboxView(NpcAiTestSceneView sceneView) {
            super(ViScriptNpc.MOD_ID + ".editor.view.ai_test_tools");
            this.sceneView = sceneView;
            setId("npc_ai_test_tools_view");
            setFloatable(false);
            layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(3).gapAll(2));
        }

        private void rebuild() {
            clearAllChildren();
            toolTabs.clear();
            statusLabel = null;
            if (!sceneView.displayedTestLevel) {
                return;
            }

            UIElement toolbar = new UIElement();
            toolbar.setId("npc_ai_test_toolbar");
            toolbar.addClass("panel_bg");
            toolbar.layout(layout -> layout.widthPercent(100).height(22).paddingAll(2)
                    .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2));
            for (Tool tool : Tool.values()) {
                Tab tab = new Tab();
                tab.setId("npc_ai_test_tool_" + tool.id);
                tab.setText(Component.translatable(tool.translationKey));
                tab.setSelected(tool == sceneView.activeTool);
                tab.layout(layout -> {
                    layout.width(tool.width).height(18);
                    layout.minWidth(tool == Tool.SELECT ? 50.0F : 58.0F);
                    layout.flexGrow(0.0F).flexShrink(1.0F).flexBasis(tool.width);
                });
                tab.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                    if (event.button == 0) {
                        sceneView.selectTool(tool);
                        event.stopPropagation();
                    }
                });
                toolTabs.put(tool, tab);
                toolbar.addChild(tab);
            }

            Button exit = new Button();
            exit.setId("npc_ai_test_exit");
            exit.setText(Component.translatable("viscript_npc.editor.ai_test.exit"));
            exit.addPreIcon(Icons.CLOSE);
            exit.layout(layout -> {
                layout.width(76).minWidth(76).maxWidth(76).height(18).marginLeftAuto();
                layout.flexGrow(0.0F).flexShrink(0.0F).flexBasis(76.0F);
            });
            exit.setOnClick(event -> {
                if (event.button == 0) {
                    RPCPacketDistributor.rpcToServer(C2SPayload.LEAVE_NPC_AI_TEST);
                }
            });
            toolbar.addChild(exit);

            statusLabel = new Label();
            statusLabel.setId("npc_ai_test_status");
            statusLabel.layout(layout -> layout.widthPercent(100).height(18));
            addChildren(toolbar, statusLabel);
            updateStatus();
        }

        private void selectTool(Tool tool) {
            toolTabs.forEach((candidate, tab) -> tab.setSelected(candidate == tool));
        }

        private void updateStatus() {
            if (statusLabel == null) {
                return;
            }
            String selected = Component.translatable("viscript_npc.editor.ai_test.none").getString();
            if (sceneView.selectedEntityId >= 0 && Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(sceneView.selectedEntityId);
                if (entity != null) {
                    selected = entity instanceof CustomNpc npc
                            ? npc.getDisplayName().getString()
                            : entity.getType().getDescription().getString();
                    selected += " #" + sceneView.selectedEntityId;
                }
            }
            RealLevelSceneViewport viewport = sceneView.viewport;
            int blocks = viewport == null ? 0 : viewport.getVisibleBlockCount();
            int fluids = viewport == null ? 0 : viewport.getVisibleFluidCount();
            int entities = viewport == null ? 0 : viewport.getVisibleEntityCount();
            statusLabel.setText(Component.translatable("viscript_npc.editor.ai_test.status",
                    Component.translatable(sceneView.activeTool.translationKey), selected,
                    blocks, fluids, entities));
        }
    }

    private static final class NpcAiTestInspectorView extends View {
        private final NpcAiTestSceneView sceneView;

        private NpcAiTestInspectorView(NpcAiTestSceneView sceneView) {
            super(ViScriptNpc.MOD_ID + ".editor.view.ai_test_inspector");
            this.sceneView = sceneView;
            setId("npc_ai_test_inspector_view");
            setFloatable(false);
            layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(3).gapAll(3));
        }

        private void rebuild() {
            clearAllChildren();
            if (!sceneView.displayedTestLevel) {
                return;
            }
            addHelp(switch (sceneView.activeTool) {
                case SELECT -> "viscript_npc.editor.ai_test.scene_settings_help";
                case PLACE_NPC -> "viscript_npc.editor.ai_test.place_npc_help";
                case PLACE_ENTITY -> "viscript_npc.editor.ai_test.place_entity_help";
                case PLACE_PLAYER -> "viscript_npc.editor.ai_test.place_player_help";
                case PLACE_BLOCK -> "viscript_npc.editor.ai_test.place_block_help";
                case BREAK_BLOCK -> "viscript_npc.editor.ai_test.break_block_help";
            });

            switch (sceneView.activeTool) {
                case PLACE_BLOCK -> buildBlockParameters();
                case PLACE_ENTITY -> buildEntityParameters();
                case PLACE_NPC -> buildNpcParameters();
                case SELECT -> buildSceneParameters();
                case PLACE_PLAYER, BREAK_BLOCK -> {
                }
            }
        }

        private void buildSceneParameters() {
            addLabel("viscript_npc.editor.ai_test.arena_width");
            TextField widthField = new TextField();
            widthField.setId("npc_ai_test_arena_width");
            widthField.setNumbersOnlyInt(
                    NpcAiTestEnvironment.MIN_ARENA_SIZE, NpcAiTestEnvironment.MAX_ARENA_SIZE);
            widthField.setText(Integer.toString(sceneView.project.getArenaWidth()), false);
            widthField.setTextResponder(value -> sceneView.resizeArena(
                    Integer.parseInt(value), sceneView.project.getArenaLength()));
            widthField.layout(layout -> layout.widthPercent(100).height(18));
            addChild(widthField);

            addLabel("viscript_npc.editor.ai_test.arena_length");
            TextField lengthField = new TextField();
            lengthField.setId("npc_ai_test_arena_length");
            lengthField.setNumbersOnlyInt(
                    NpcAiTestEnvironment.MIN_ARENA_SIZE, NpcAiTestEnvironment.MAX_ARENA_SIZE);
            lengthField.setText(Integer.toString(sceneView.project.getArenaLength()), false);
            lengthField.setTextResponder(value -> sceneView.resizeArena(
                    sceneView.project.getArenaWidth(), Integer.parseInt(value)));
            lengthField.layout(layout -> layout.widthPercent(100).height(18));
            addChild(lengthField);
        }

        private void buildBlockParameters() {
            addLabel("viscript_npc.editor.ai_test.block_id");
            BlockSearchBox field = new BlockSearchBox(resolveBlock(sceneView.blockId));
            field.setId("npc_ai_test_block_id");
            field.setOnValueChanged(value -> {
                String blockId = BlockSearchBox.getBlockIdString(value);
                if (!blockId.isBlank()) {
                    sceneView.blockId = blockId;
                }
            });
            field.layout(layout -> layout.widthPercent(100).height(18));
            addChild(field);
        }

        private void buildEntityParameters() {
            addLabel("viscript_npc.editor.ai_test.entity_id");
            EntityTypeSearchBox field = new EntityTypeSearchBox(resolveEntityType(sceneView.entityId));
            field.setId("npc_ai_test_entity_id");
            field.setOnValueChanged(value -> {
                String entityId = EntityTypeSearchBox.getEntityTypeIdString(value);
                if (!entityId.isBlank()) {
                    sceneView.entityId = entityId;
                }
            });
            field.layout(layout -> layout.widthPercent(100).height(18));
            addChild(field);
        }

        private void buildNpcParameters() {
            UIElement runtimeNpcs = new UIElement();
            runtimeNpcs.setId("npc_ai_test_runtime_npcs");
            runtimeNpcs.addClass("panel_bg");
            runtimeNpcs.layout(layout -> layout.widthPercent(100).flex(1).paddingAll(2).gapAll(2));

            Label listLabel = new Label();
            listLabel.setText(Component.translatable("viscript_npc.editor.ai_test.runtime_npcs"));
            listLabel.layout(layout -> layout.widthPercent(100).height(18));
            runtimeNpcs.addChild(listLabel);

            ScrollerView scroller = new ScrollerView();
            scroller.setId("npc_ai_test_runtime_npc_scroller");
            scroller.getScrollerViewStyle().mode(ScrollerMode.VERTICAL);
            scroller.layout(layout -> layout.widthPercent(100).flex(1));
            scroller.viewContainer.layout(layout -> layout.widthPercent(100).gapAll(2));
            runtimeNpcs.addChild(scroller);

            if (sceneView.runtimeNpcListLoading) {
                addRuntimeNpcInfo(scroller, "viscript_npc.editor.ai_test.runtime_npcs_loading");
                addChild(runtimeNpcs);
                return;
            }

            var entries = ViScriptNpcClientUtil.getNpcAiTestRuntimeNpcs();
            if (entries.isEmpty()) {
                addRuntimeNpcInfo(scroller, "viscript_npc.editor.ai_test.runtime_npcs_empty");
                addChild(runtimeNpcs);
                return;
            }

            Map<String, Tab> runtimeNpcTabs = new LinkedHashMap<>();
            for (ViScriptNpcClientUtil.NpcRuntimeEntry entry : entries) {
                Tab tab = new Tab();
                tab.setId(runtimeNpcElementId(entry.fileId()));
                tab.addClass("npc_ai_test_runtime_npc");
                tab.setText(Component.literal(entry.npcId()));
                tab.setSelected(entry.fileId().equals(sceneView.selectedRuntimeNpcFileId));
                tab.layout(layout -> layout.widthPercent(100).height(18));
                tab.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                    if (event.button == 0) {
                        sceneView.selectedRuntimeNpcFileId = entry.fileId();
                        runtimeNpcTabs.forEach((fileId, candidate) ->
                                candidate.setSelected(fileId.equals(sceneView.selectedRuntimeNpcFileId)));
                        sceneView.updateStatus();
                        event.stopPropagation();
                    }
                });
                runtimeNpcTabs.put(entry.fileId(), tab);
                scroller.viewContainer.addChild(tab);
            }
            addChild(runtimeNpcs);
        }

        private static void addRuntimeNpcInfo(ScrollerView scroller, String translationKey) {
            Label info = new Label();
            info.setText(Component.translatable(translationKey));
            info.layout(layout -> layout.widthPercent(100).height(18));
            scroller.viewContainer.addChild(info);
        }

        private static Block resolveBlock(String blockId) {
            ResourceLocation id = ResourceLocation.tryParse(blockId);
            if (id == null) {
                return Blocks.STONE;
            }
            return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.STONE);
        }

        private static EntityType<?> resolveEntityType(String entityId) {
            ResourceLocation id = ResourceLocation.tryParse(entityId);
            if (id == null) {
                return EntityType.PIG;
            }
            return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(EntityType.PIG);
        }

        private void addLabel(String translationKey) {
            Label label = new Label();
            label.setText(Component.translatable(translationKey));
            label.layout(layout -> layout.widthPercent(100).height(18));
            addChild(label);
        }

        private void addHelp(String translationKey) {
            Label label = new Label();
            label.setId("npc_ai_test_tool_help");
            label.setText(Component.translatable(translationKey));
            label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
            label.setOverflowVisible(false);
            label.layout(layout -> layout.widthPercent(100).height(18));
            addChild(label);
        }
    }
}
