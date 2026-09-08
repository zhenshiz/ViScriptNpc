package com.viscript.npc.command;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.test.NpcTestSceneProject;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript.npc.util.NpcRuntimeFiles;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript_lib.gui.editor.EditorAssetFiles;
import com.viscript_lib.gui.editor.EditorFileFormat;
import com.viscript_lib.register.ICommand;
import lombok.SneakyThrows;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@LDLRegister(name = "npc", registry = ICommand.COMMAND_ID)
public class NpcCommand implements ICommand {
    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        dispatcher.register(Commands.literal(ViScriptNpc.MOD_ID).requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .suggests(((context, builder) -> {
                                    getServerNpcFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })).executes(context -> this.summon(context, null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(context -> this.summon(context, Vec3Argument.getVec3(context, "pos")))
                                )
                        )
                )
                .then(Commands.literal("editor").executes(this::openEditor)
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .suggests(((context, builder) -> {
                                    getServerEditorFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })).executes(this::openEditor)
                        )
                )
                .then(Commands.literal("test")
                        .executes(this::openTestScene)
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .suggests(((context, builder) -> {
                                    getServerTestSceneFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })).executes(this::openTestScene)
                        )
                )
        );
    }

    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC;

    static List<String> getServerNpcFiles() {
        return NpcRuntimeFiles.listFileIds();
    }

    static List<String> getServerEditorFiles() {
        return EditorAssetFiles.listProjectFiles(FORMAT, true);
    }

    static List<String> getServerTestSceneFiles() {
        return EditorAssetFiles.listProjectFiles(NpcTestSceneProject.FORMAT, true);
    }

    static File getNpcProjectFile(String fileName) {
        return EditorAssetFiles.resolveProjectFile(FORMAT, normalizeFileArgument(fileName), true).toFile();
    }

    static File getTestSceneProjectFile(String fileName) {
        return EditorAssetFiles.resolveProjectFile(NpcTestSceneProject.FORMAT,
                normalizeFileArgument(fileName), true).toFile();
    }

    private static String normalizeFileArgument(String fileName) {
        if (fileName.startsWith("\"")) fileName = fileName.substring(1);
        if (fileName.endsWith("\"")) fileName = fileName.substring(0, fileName.length() - 1);
        return fileName;
    }

    static CompoundTag readProjectFile(File file) {
        if (!file.exists()) return new CompoundTag();
        try {
            CompoundTag tag = NbtIo.read(file.toPath());
            return tag == null ? new CompoundTag() : tag;
        } catch (IOException e) {
            return new CompoundTag();
        }
    }

    @SneakyThrows
    private int summon(CommandContext<CommandSourceStack> context, Vec3 pos) {
        String fileName = StringArgumentType.getString(context, "file");
        CommandSourceStack source = context.getSource();
        if (pos == null) {
            Entity entity = source.getEntity();
            if (entity != null) {
                pos = entity.position();
            } else {
                throw entityOnlyException();
            }
        }
        Entity npc = ViScriptNpcServerUtil.summonNpc(
                NpcRuntimeFiles.load(normalizeFileArgument(fileName)), pos);
        if (npc != null) {
            source.sendSuccess(() -> Component.translatable("commands.summon.success", npc.getDisplayName()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("commands.summon.failed"));
        return 0;
    }

    @SneakyThrows
    private int openEditor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            String fileName = "";
            try {
                fileName = StringArgumentType.getString(context, "file");
            } catch (Exception ignored) {
            }
            if (!fileName.isEmpty()) {
                var projectFile = getNpcProjectFile(fileName);
                var projectTag = readProjectFile(projectFile);
                if (!projectTag.isEmpty()) {
                    ViScriptNpcServerUtil.openNpcEditor(player, projectTag);
                    return 1;
                }
                source.sendFailure(Component.translatable("command.viscript_npc.editor.project_file_required", fileName));
                return 0;
            }
            ViScriptNpcServerUtil.openNpcEditor(player, new CompoundTag());
            return 1;
        } else {
            throw playerOnlyException();
        }
    }

    @SneakyThrows
    private int openTestScene(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw playerOnlyException();
        }
        String fileName = "";
        try {
            fileName = StringArgumentType.getString(context, "file");
        } catch (Exception ignored) {
        }
        CompoundTag projectTag;
        if (fileName.isEmpty()) {
            NpcTestSceneProject project = new NpcTestSceneProject();
            project.initNewProject();
            projectTag = project.serializeNBT(Platform.getFrozenRegistry());
        } else {
            projectTag = readProjectFile(getTestSceneProjectFile(fileName));
        }
        if (!fileName.isEmpty() && projectTag.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "command.viscript_npc.test_scene.project_file_required", fileName));
            return 0;
        }
        ViScriptNpcServerUtil.openNpcEditor(player, projectTag);
        return 1;
    }

}
