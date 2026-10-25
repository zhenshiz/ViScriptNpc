package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.ai.data.AiProgram;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 加载独立行为的结构定义；完整依赖由发布和执行入口校验，发布后刷新缓存。 */
public final class NpcActionFiles {
    private static final Map<Path, AiProgram> CACHE = new HashMap<>();
    public static List<String> list() {
        var ids = new java.util.TreeSet<>(NpcEditorFiles.listRuntimeFiles(NpcEditorFormats.NPC_ACTION, true));
        com.viscript.npc.npc.ai.runtime.AiExamples.NAMES.forEach(name -> ids.add("examples/" + name));
        return List.copyOf(ids);
    }
    /** 判断逻辑引用是否指向模组提供的学习示例。 */
    public static boolean isBuiltin(String id) {
        return id.startsWith("examples/") && com.viscript.npc.npc.ai.runtime.AiExamples.NAMES.contains(id.substring(9));
    }
    /** 列出可用于组合调用的玩家行为，示例由独立入口展示。 */
    public static List<String> listCustomLocal() {
        return listLocal().stream().filter(id -> !isBuiltin(id)).toList();
    }
    public static synchronized void invalidate() { CACHE.clear(); }
    public static List<String> listLocal() {
        var result = new java.util.TreeSet<>(list());
        result.addAll(NpcEditorFiles.listProjectFiles(NpcEditorFormats.NPC_ACTION, true));
        return List.copyOf(result);
    }
    public static AiProgram loadLocal(String id) {
        try {
            Path path = NpcEditorFiles.resolveProjectFile(NpcEditorFormats.NPC_ACTION, id, true);
            if (!Files.isRegularFile(path)) return load(id);
            var tag = NbtIo.read(path);
            if (tag == null) return null;
            AiProgram program = new AiProgram();
            program.deserializeNBT(Platform.getFrozenRegistry(), tag.getCompound("data").getCompound("program"));
            return program.kind.equals("action") && AiValidation.validateDefinition(program).isEmpty() ? program : null;
        } catch (Exception exception) { return null; }
    }
    public static synchronized AiProgram load(String fileId) {
        try {
            Path path = NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, fileId, true);
            AiProgram cached = CACHE.get(path);
            if (cached != null) return cached;
            if (!Files.isRegularFile(path)) {
                var example = fileId.startsWith("examples/") ? com.viscript.npc.npc.ai.runtime.AiExamples.action(fileId.substring(9)) : null;
                if (example != null) CACHE.put(path, example);
                return example;
            }
            if (Files.size(path) > 4 * 1024 * 1024) return null;
            AiProgram program = new AiProgram();
            try (var input = Files.newInputStream(path)) {
                program.deserializeNBT(Platform.getFrozenRegistry(), NbtIo.readCompressed(input, NbtAccounter.create(16 * 1024 * 1024)));
            }
            if (!program.kind.equals("action") || !AiValidation.validateDefinition(program).isEmpty()) return null;
            CACHE.put(path, program);
            return program;
        } catch (Exception exception) {
            ViScriptNpc.LOGGER.warn("无法加载 NPC 行为 {}: {}", fileId, exception.getMessage());
            return null;
        }
    }
}
