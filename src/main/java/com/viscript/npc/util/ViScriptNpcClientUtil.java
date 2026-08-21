package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.test.NpcTestSceneProject;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.editor.NpcAiDescriptorCache;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ViScriptNpcClientUtil {
    private static final double NPC_AI_DEBUG_TARGET_RADIUS = 64.0D;
    private static final Map<Integer, CompoundTag> NPC_AI_DEBUG_SNAPSHOTS = new HashMap<>();
    private static List<NpcRuntimeEntry> npcAiTestRuntimeNpcs = List.of();
    private static int npcAiTestRuntimeNpcRevision;

    public static NPCProject cacheNpcProject;

    @Info("客户端打开 NPC 编辑器")
    public static void openNpcEditor(@Nullable CompoundTag tag) {
        EditorWindow editorWindow = getCurrentEditorWindow();
        if (editorWindow == null) return;
        Editor editor = editorWindow.getCurrentEditor();
        if (editor == null) return;
        if (tag != null && !tag.isEmpty()) {
            boolean testSceneProject = isTestSceneProjectFileTag(tag);
            if (!testSceneProject && !isProjectFileTag(tag)) {
                showEditorOpenFailure(Component.translatable("viscript_npc.editor.open_project.invalid_file"));
                return;
            }
            var project = testSceneProject
                    ? NpcTestSceneProject.PROVIDER.projectCreator.get()
                    : NPCProject.PROVIDER.projectCreator.get();
            project.initNewProject();
            try {
                project.deserializeNBT(Platform.getFrozenRegistry(), tag);
                if (project instanceof NpcTestSceneProject testProject
                        && tag.contains(NpcAiTestEnvironment.ARENA_CENTER_TAG, Tag.TAG_LONG)) {
                    BlockPos arenaCenter = BlockPos.of(tag.getLong(NpcAiTestEnvironment.ARENA_CENTER_TAG));
                    testProject.setRuntimeArenaCenter(arenaCenter);
                }
                editor.loadProject(project, null);
                return;
            } catch (Exception exception) {
                showEditorOpenFailure(Component.translatable("viscript_npc.editor.open_project.failed",
                        exception.getClass().getSimpleName()));
                return;
            }
        }
        if (cacheNpcProject != null) {
            editor.loadProject(cacheNpcProject, null);
        }
    }

    public static int findNearestNpcAiDebugTarget(@Nullable String npcType) {
        CustomNpc npc = findNearestNpcAiDebugTargetEntity(npcType);
        return npc == null ? -1 : npc.getId();
    }

    public static String getClientNpcDebugName(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(entityId) instanceof CustomNpc npc)) {
            return "";
        }
        return npc.getNpcType() + " #" + entityId;
    }

    public static void setNpcAiDebugSnapshot(CompoundTag payload) {
        if (payload == null || !payload.contains("entityId")) {
            return;
        }
        NPC_AI_DEBUG_SNAPSHOTS.put(payload.getInt("entityId"), payload.copy());
    }

    public static CompoundTag getNpcAiDebugSnapshot(int entityId) {
        CompoundTag payload = NPC_AI_DEBUG_SNAPSHOTS.get(entityId);
        return payload == null ? new CompoundTag() : payload.copy();
    }

    public static void setNpcAiDescriptors(CompoundTag payload) {
        NpcAiDescriptorCache.set(payload);
    }

    public static CompoundTag getNpcAiDescriptors() {
        return NpcAiDescriptorCache.get();
    }

    /**
     * 清空客户端保存的测试场景 NPC 运行时文件列表。
     *
     * <p>请求服务端刷新列表前调用此方法，可以让界面显示加载状态而不是旧数据。
     */
    public static void clearNpcAiTestRuntimeNpcs() {
        npcAiTestRuntimeNpcs = List.of();
    }

    /**
     * 接收服务端返回的测试场景 NPC 运行时文件列表。
     *
     * @param payload 包含逻辑文件标识和 <code>npcId</code> 的列表数据
     */
    public static void setNpcAiTestRuntimeNpcs(CompoundTag payload) {
        List<NpcRuntimeEntry> entries = new ArrayList<>();
        if (payload != null) {
            var values = payload.getList("values", Tag.TAG_COMPOUND);
            for (int index = 0; index < values.size(); index++) {
                CompoundTag value = values.getCompound(index);
                String fileId = value.getString("fileId");
                String npcId = value.getString("npcId");
                if (!fileId.isBlank() && !npcId.isBlank()) {
                    entries.add(new NpcRuntimeEntry(fileId, npcId));
                }
            }
        }
        npcAiTestRuntimeNpcs = List.copyOf(entries);
        npcAiTestRuntimeNpcRevision++;
    }

    /**
     * 获取服务端最近一次返回的测试场景 NPC 列表。
     *
     * @return 不可变的运行时 NPC 条目列表
     */
    public static List<NpcRuntimeEntry> getNpcAiTestRuntimeNpcs() {
        return npcAiTestRuntimeNpcs;
    }

    /**
     * 获取测试场景 NPC 列表的客户端修订号。
     *
     * @return 每次接收服务端列表后递增的修订号
     */
    public static int getNpcAiTestRuntimeNpcRevision() {
        return npcAiTestRuntimeNpcRevision;
    }

    private static boolean isProjectFileTag(CompoundTag tag) {
        return tag.contains("data", Tag.TAG_COMPOUND)
                && tag.getCompound("data").contains("npc", Tag.TAG_COMPOUND);
    }

    private static boolean isTestSceneProjectFileTag(CompoundTag tag) {
        if (!tag.contains("data", Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag data = tag.getCompound("data");
        return data.getBoolean("test_scene") || data.contains("presets", Tag.TAG_LIST);
    }

    private static void showEditorOpenFailure(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }

    @Nullable
    private static EditorWindow getCurrentEditorWindow() {
        return getEditorWindow(Minecraft.getInstance().screen);
    }

    @Nullable
    private static EditorWindow getEditorWindow(@Nullable Screen currentScreen) {
        if (currentScreen instanceof ModularUIContainerScreen screen
                && screen.getMenu().getModularUI().ui.rootElement instanceof EditorWindow editorWindow) {
            return editorWindow;
        }
        if (currentScreen instanceof ModularUIScreen screen
                && screen.modularUI.ui.rootElement instanceof EditorWindow editorWindow) {
            return editorWindow;
        }
        return null;
    }

    @Nullable
    private static CustomNpc findNearestNpcAiDebugTargetEntity(@Nullable String npcType) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        String targetType = npcType == null ? "" : npcType.trim();
        return minecraft.level.getEntitiesOfClass(CustomNpc.class,
                        player.getBoundingBox().inflate(NPC_AI_DEBUG_TARGET_RADIUS))
                .stream()
                .filter(npc -> targetType.isEmpty() || targetType.equals(npc.getNpcType()))
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    /**
     * 描述服务端返回给测试场景界面的一个 NPC 运行时文件。
     *
     * @param fileId 服务端运行时目录中的逻辑文件标识
     * @param npcId NPC 配置中用于界面显示的标识
     */
    public record NpcRuntimeEntry(String fileId, String npcId) {
    }
}
