package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting;
import com.viscript_lib.gui.editor.EditorAssetFiles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 提供服务端 NPC 运行时文件的枚举与读取功能。
 *
 * <p>所有文件路径都通过编辑器资源目录解析，调用方只能访问 NPC 运行时文件目录内的文件。
 */
public final class NpcRuntimeFiles {
    private NpcRuntimeFiles() {
    }

    /**
     * 列出服务端 NPC 运行时目录中的逻辑文件标识。
     *
     * @return 不含运行时文件后缀的逻辑文件标识列表
     */
    public static List<String> listFileIds() {
        return EditorAssetFiles.listRuntimeFiles(NpcEditorFormats.NPC, true);
    }

    /**
     * 列出服务端 NPC 运行时目录中可以加载的 NPC。
     *
     * <p>损坏的文件和没有设置 <code>npcId</code> 的文件不会出现在结果中。
     *
     * @return 按 <code>npcId</code> 和逻辑文件标识排序的运行时 NPC 列表
     */
    public static List<Entry> listEntries() {
        return listFileIds().stream()
                .map(fileId -> new Entry(fileId, readNpcId(fileId)))
                .filter(entry -> !entry.npcId().isBlank())
                .sorted(Comparator.comparing(Entry::npcId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Entry::fileId))
                .toList();
    }

    /**
     * 从服务端 NPC 运行时目录读取一个 NPC 配置。
     *
     * @param fileId 不含运行时文件后缀的逻辑文件标识
     * @return 读取成功时返回 NPC 配置；文件不存在或无法读取时返回空标签
     */
    public static CompoundTag load(String fileId) {
        try {
            Path file = EditorAssetFiles.resolveRuntimeFile(NpcEditorFormats.NPC, fileId, true);
            if (!Files.isRegularFile(file)) {
                return new CompoundTag();
            }
            try (var input = Files.newInputStream(file)) {
                return NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            }
        } catch (IOException | RuntimeException exception) {
            ViScriptNpc.LOGGER.warn("无法读取 NPC 运行时文件 {}：{}", fileId, exception.getMessage());
            return new CompoundTag();
        }
    }

    private static String readNpcId(String fileId) {
        CompoundTag npcData = load(fileId);
        if (npcData.isEmpty()) {
            return "";
        }
        try {
            NpcBasicsSetting basics = new NpcBasicsSetting();
            basics.deserializeNBT(Platform.getFrozenRegistry(), npcData);
            return basics.getNpcId().trim();
        } catch (RuntimeException exception) {
            ViScriptNpc.LOGGER.warn("无法解析 NPC 运行时文件 {} 的 npcId：{}", fileId, exception.getMessage());
            return "";
        }
    }

    /**
     * 描述一个服务端 NPC 运行时文件及其界面显示标识。
     *
     * @param fileId 服务端运行时目录中的逻辑文件标识
     * @param npcId NPC 配置中保存的显示标识
     */
    public record Entry(String fileId, String npcId) {
    }
}
