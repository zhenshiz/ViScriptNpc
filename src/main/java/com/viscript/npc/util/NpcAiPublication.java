package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.*;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript_lib.gui.editor.*;
import com.viscript_lib.network.s2c.EditorUploadS2CPackets;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** 校验完整依赖后发布；文件写入失败会恢复之前的有效文件。 */
public final class NpcAiPublication {
    public static final String RPC = ViScriptNpc.MOD_ID + ":publish_ai";
    public static final String DEPENDENCIES = "npc_action_dependencies";

    public static CompoundTag bundle(AiProgram tree, Function<String, AiProgram> resolver) {
        AiValidation.requireValid(tree, resolver);
        CompoundTag dependencies = new CompoundTag();
        collect(tree, resolver, dependencies);
        return dependencies;
    }

    private static void collect(AiProgram program, Function<String, AiProgram> resolver, CompoundTag dependencies) {
        for (AiNodeData node : program.nodes) if (node.type.equals("call")) {
            String id = node.literal("file", AiValue.text("")).text;
            if (dependencies.contains(id)) continue;
            AiProgram action = Objects.requireNonNull(resolver.apply(id));
            dependencies.put(id, action.serializeNBT(Platform.getFrozenRegistry()));
            collect(action, resolver, dependencies);
        }
    }

    public static Map<String, AiProgram> readDependencies(CompoundTag tag) {
        if (tag.size() > 128) throw new IllegalArgumentException("Too many action dependencies");
        Map<String, AiProgram> result = new LinkedHashMap<>();
        for (String id : tag.getAllKeys()) {
            NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true);
            AiProgram program = new AiProgram();
            program.deserializeNBT(Platform.getFrozenRegistry(), tag.getCompound(id));
            if (!program.kind.equals("action")) throw new IllegalArgumentException("Expected action: " + id);
            result.put(id, program);
        }
        // 先读取整个包，再验证引用；顺序不影响多层依赖解析。
        result.values().forEach(program -> AiValidation.requireValid(program, result::get));
        return result;
    }

    public static void request(String kind, String name, @Nullable CompoundTag runtime, @Nullable CompoundTag project) {
        var tag = new CompoundTag();
        tag.putString("kind", kind); tag.putString("name", name);
        if (runtime != null) tag.put("runtime", runtime);
        if (project != null) tag.put("project", project);
        RPCPacketDistributor.rpcToServer(RPC, tag);
    }

    @RPCPacket(RPC)
    public static void receive(RPCSender sender, CompoundTag request) {
        if (sender.isServer()) return;
        var player = sender.asPlayer();
        if (player == null || !player.hasPermissions(net.minecraft.commands.Commands.LEVEL_OWNERS)) return;
        try {
            install(request);
            RPCPacketDistributor.rpcToPlayer(player, EditorUploadS2CPackets.EDITOR_UPLOAD_RESULT,
                    Component.translatable("viscript_lib.editor.server_upload_result.success.title"),
                    Component.translatable("viscript_lib.editor.server_upload_result.success.created", request.getString("name")));
        } catch (Exception exception) {
            RPCPacketDistributor.rpcToPlayer(player, EditorUploadS2CPackets.EDITOR_UPLOAD_RESULT,
                    Component.translatable("viscript_lib.editor.server_upload_result.error.title"),
                    Component.literal(Objects.toString(exception.getMessage(), "Publication failed")));
        }
    }

    public static void install(CompoundTag request) throws IOException {
        String kind = request.getString("kind"), name = request.getString("name");
        CompoundTag runtime = request.getCompound("runtime");
        Map<Path, FileData> files = new LinkedHashMap<>();
        EditorFileFormat format = switch (kind) {
            case "npc" -> NpcEditorFormats.NPC;
            case "action" -> NpcEditorFormats.NPC_ACTION;
            case "test_scene" -> NpcEditorFormats.NPC_TEST_SCENE;
            default -> throw new IllegalArgumentException("Unknown NPC file kind");
        };
        if (kind.equals("test_scene") && (!request.contains("runtime") || request.contains("project")))
            throw new IllegalArgumentException("Test scenes only support function files");
        if (request.contains("runtime") && kind.equals("npc")) {
            NpcAI ai = new NpcAI(); ai.deserializeNBT(Platform.getFrozenRegistry(), runtime);
            var dependencies = readDependencies(runtime.getCompound(DEPENDENCIES));
            AiValidation.requireValid(ai.getProgram(), id -> dependencies.containsKey(id) ? dependencies.get(id) : NpcActionFiles.load(id));
            dependencies.forEach((id, definition) -> files.put(NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true),
                    new FileData(definition.serializeNBT(Platform.getFrozenRegistry()), true)));
        } else if (request.contains("runtime") && kind.equals("action")) {
            AiProgram definition = new AiProgram(); definition.deserializeNBT(Platform.getFrozenRegistry(), runtime);
            if (!definition.kind.equals("action")) throw new IllegalArgumentException("Expected action");
            var dependencies = readDependencies(runtime.getCompound(DEPENDENCIES));
            Path destination = NpcEditorFiles.resolveRuntimeFile(format, name, true);
            Function<String, AiProgram> resolver = id -> NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true).equals(destination) ? definition
                    : dependencies.containsKey(id) ? dependencies.get(id) : NpcActionFiles.load(id);
            AiValidation.requireValid(definition, resolver);
            dependencies.forEach((id, dependency) -> files.put(NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true),
                    new FileData(dependency.serializeNBT(Platform.getFrozenRegistry()), true)));
        } else if (kind.equals("test_scene") && !runtime.getBoolean("test_scene")) {
            throw new IllegalArgumentException("Expected test scene");
        }
        if (request.contains("runtime")) files.put(NpcEditorFiles.resolveRuntimeFile(format, name, true), new FileData(runtime, true));
        if (request.contains("project")) files.put(NpcEditorFiles.resolveProjectFile(format,
                EditorFileNames.normalizeBaseName(name, format.projectSuffix(), format.runtimeSuffix()), true),
                new FileData(request.getCompound("project"), false));
        if (files.isEmpty()) throw new IllegalArgumentException("Missing NPC file data");
        writeTransaction(files);
        NpcActionFiles.invalidate();
    }

    private record FileData(CompoundTag tag, boolean compressed) {}

    public static void prepareConfig(CompoundTag config, boolean replaceDependencies) throws IOException {
        var dependencies = readDependencies(config.getCompound(DEPENDENCIES));
        NpcAI ai = new NpcAI(); ai.deserializeNBT(Platform.getFrozenRegistry(), config);
        Function<String, AiProgram> resolver = id -> {
            var installed = NpcActionFiles.load(id);
            return (replaceDependencies || installed == null) && dependencies.containsKey(id) ? dependencies.get(id) : installed;
        };
        AiValidation.requireValid(ai.getProgram(), resolver);
        Map<Path, FileData> files = new LinkedHashMap<>();
        dependencies.forEach((id, program) -> {
            Path path = NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true);
            if (replaceDependencies || !Files.exists(path)) files.put(path, new FileData(program.serializeNBT(Platform.getFrozenRegistry()), true));
        });
        if (!files.isEmpty()) { writeTransaction(files); NpcActionFiles.invalidate(); }
    }

    private static void writeTransaction(Map<Path, FileData> files) throws IOException {
        Map<Path, Path> staged = new LinkedHashMap<>();
        Map<Path, byte[]> previous = new LinkedHashMap<>();
        List<Path> replaced = new ArrayList<>();
        try {
            for (var entry : files.entrySet()) {
                Path path = entry.getKey(); Files.createDirectories(path.getParent());
                previous.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
                Path temporary = Files.createTempFile(path.getParent(), ".npc-ai-", ".tmp");
                staged.put(path, temporary);
                if (entry.getValue().compressed) NbtIo.writeCompressed(entry.getValue().tag, temporary);
                else NbtIo.write(entry.getValue().tag, temporary);
            }
            for (var entry : staged.entrySet()) {
                Files.move(entry.getValue(), entry.getKey(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                replaced.add(entry.getKey());
            }
        } catch (IOException exception) {
            for (Path path : replaced.reversed()) {
                try { if (previous.get(path) == null) Files.deleteIfExists(path); else Files.write(path, previous.get(path)); }
                catch (IOException restore) { exception.addSuppressed(restore); }
            }
            throw exception;
        } finally {
            for (Path temporary : staged.values()) Files.deleteIfExists(temporary);
        }
    }
}
