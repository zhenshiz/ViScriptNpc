package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.gui.edit.*;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.test.NpcAiTestControl;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.*;
import com.viscript.npc.util.NpcEditorFiles;
import net.minecraft.nbt.CompoundTag;

/** 测试与导出读取打开的行为工程快照；保留源标签和画布位置。 */
public final class AiDrafts {
    public static AiProgram resolve(NpcEditor editor, String id) {
        // 调用节点创建时尚未选择行为文件，保留为未解析引用供画布校验。
        if (id == null || id.isBlank()) return null;
        if (editor.getWindow() != null) {
            final java.nio.file.Path path;
            try { path = NpcEditorFiles.resolveProjectFile(NpcActionProject.FORMAT, id, true); }
            catch (IllegalArgumentException exception) { return null; }
            for (var tab : editor.getWindow().getEditors().keySet()) {
                if (tab.getCurrentProject() instanceof NpcActionProject project &&
                        (tab.getCurrentProjectFile() != null
                        ? tab.getCurrentProjectFile().toPath().toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize())
                        : project.sourceActionId.equals(id))) {
                    project.snapshot(); return project.program;
                }
            }
        }
        if (editor.getCurrentProject() instanceof NPCProject project) {
            var saved = project.npc.getNpcData(NpcAI.class).getActionDefinitions().get(id);
            if (saved != null) return saved;
        }
        return AiServerActions.load(id);
    }

    /**
     * 读取完整画布与已打开的行为草稿；只在明确点击保存时提交服务器。
     *
     * @param editor 拥有 NPC 工程的编辑器
     */
    public static void saveNpcAi(NpcEditor editor) {
        if (!(editor.getCurrentProject() instanceof NPCProject project)) return;
        try {
            String marker = project.getCurrentNpcType();
            if (marker.isBlank()) throw new IllegalArgumentException("viscript_npc.ai.save.empty_marker");
            project.snapshotAi();
            var ai = project.npc.getNpcData(NpcAI.class);
            var dependencies = NpcAiPublication.bundle(ai.getProgram(), id -> resolve(editor, id));
            ai.setActionDefinitions(NpcAiPublication.readDependencies(dependencies));
            // 只发送 AI 配置，不把外观、属性等草稿覆盖到世界实体。
            var snapshot = new NpcAI();
            snapshot.setProgram(ai.getProgram()); snapshot.setEnabled(ai.isEnabled());
            var tag = snapshot.serializeNBT(Platform.getFrozenRegistry());
            tag.put(NpcAiPublication.DEPENDENCIES, dependencies);
            RPCPacketDistributor.rpcToServer(com.viscript.npc.network.c2s.C2SPayload.SAVE_NPC_AI, marker, tag);
        } catch (RuntimeException exception) {
            com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.showNotification("editor.error",
                    exception.getMessage(), null).show(editor.getModularUI());
        }
    }

    /**
     * 将保存结果显示在当前编辑器中；关闭界面后则显示在聊天栏。
     *
     * @param success 是否成功应用
     * @param message 服务器返回的可翻译结果
     */
    public static void showSaveResult(boolean success, net.minecraft.network.chat.Component message) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen screen) {
            var dialog = success ? com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.showNotification(message.getString(), 3)
                    : com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.showNotification("editor.error", message.getString(), null);
            dialog.setId("npc_ai_save_result");
            dialog.show(screen.getMenu().getModularUI());
        } else if (mc.player != null) mc.player.displayClientMessage(message, false);
    }

    /**
     * 显示已打开的测试场景；尚无场景时创建当前行为的测试会话。
     *
     * @param editor 提供当前 AI 草稿和主窗口的编辑器
     */
    public static void openSceneWindow(NpcEditor editor) {
        var window = editor.getWindow();
        if (window != null) for (var candidate : window.getEditors().keySet()) {
            if (candidate instanceof NpcEditor npc && npc.getCurrentProject() instanceof com.viscript.npc.gui.test.NpcTestSceneProject) {
                if (!npc.openTestSceneWindow()) window.showEditor(npc);
                return;
            }
        }
        createSceneWindow(editor);
    }

    private static void createSceneWindow(NpcEditor editor) {
        try {
            CompoundTag config;
            if (editor.getCurrentProject() instanceof NPCProject project) config = project.serializeRuntimeFile(Platform.getFrozenRegistry());
            else if (editor.getCurrentProject() instanceof NpcActionProject action) {
                action.serializeRuntimeFile(Platform.getFrozenRegistry());
                NPCProject npc = new NPCProject();
                AiProgram tree = new AiProgram();
                tree.variables.addAll(action.program.npcVariables);
                AiNodeData call = new AiNodeData(); call.type = "call"; call.input("file", AiValue.text("session/action"));
                AiNodeData root = new AiNodeData(); root.type = "root"; root.children.add(call.id);
                tree.nodes.add(root); tree.nodes.add(call); tree.root = root.id;
                npc.npc.getNpcData(NpcAI.class).setProgram(tree);
                config = npc.npc.serializeNBT(Platform.getFrozenRegistry());
                config.put(NpcAiPublication.DEPENDENCIES, NpcAiPublication.bundle(tree, id -> id.equals("session/action") ? action.program : resolve(editor, id)));
            } else return;
            var points = new net.minecraft.nbt.ListTag();
            if (editor.getWindow() != null) for (var tab : editor.getWindow().getEditors().keySet())
                for (var element : tab.select("*").toList()) if (element instanceof AiGraphView view && view.getGraph() != null)
                    view.model().breakpoints.forEach(id -> points.add(net.minecraft.nbt.StringTag.valueOf(id)));
            config.put("ai_breakpoints", points);
            config.putBoolean("ai_detach_scene", true);
            if (editor.getWindow() != null) editor.getWindow().minimizeWindow();
            RPCPacketDistributor.rpcToServer(NpcAiTestControl.OPEN, config);
        } catch (RuntimeException exception) {
            com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.showNotification("Dialog.error", exception.getMessage(), null).show(editor.getModularUI());
        }
    }
}
