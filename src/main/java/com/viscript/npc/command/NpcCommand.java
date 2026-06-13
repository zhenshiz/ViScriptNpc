package com.viscript.npc.command;

import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.util.NpcEditorFormats;
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
import net.minecraft.nbt.NbtAccounter;
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
                                    getServerNpcFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })).executes(this::openEditor)
                        )
                )
        );
    }

    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC;

    static List<String> getServerNpcFiles() {
        return EditorAssetFiles.listRuntimeFiles(FORMAT, true);
    }

    static File getNpcFile(String fileName) {
        if (fileName.startsWith("\"")) fileName = fileName.substring(1);
        if (fileName.endsWith("\"")) fileName = fileName.substring(0, fileName.length() - 1);
        return EditorAssetFiles.resolveRuntimeFile(FORMAT, fileName, true).toFile();
    }

    static CompoundTag readNpcFile(File file) {
        if (!file.exists()) return new CompoundTag();
        try (var inputStream = Files.newInputStream(file.toPath())) {
            return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
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
        Entity npc = ViScriptNpcServerUtil.summonNpc(readNpcFile(getNpcFile(fileName)), pos);
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
                var tag = readNpcFile(getNpcFile(fileName));
                if (!tag.isEmpty()) {
                    ViScriptNpcServerUtil.openNpcEditor(player, tag);
                    return 1;
                }
            }
            ViScriptNpcServerUtil.openNpcEditor(player, new CompoundTag());
            return 1;
        } else {
            throw playerOnlyException();
        }
    }
}
