package com.viscript.npc.command;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.util.ViScriptNpcServerUtil;
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
import java.util.ArrayList;
import java.util.List;

@LDLRegister(name = "npc", registry = "viscript_npc:command")
public class NpcCommand implements ICommand {
    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        dispatcher.register(Commands.literal(ViScriptNpc.MOD_ID).requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("file", StringArgumentType.string())
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
                        .then(Commands.argument("file", StringArgumentType.string())
                                .suggests(((context, builder) -> {
                                    getServerNpcFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })).executes(this::openEditor)
                        )
                )
        );
    }

    static List<String> getServerNpcFiles() {
        List<String> npcFiles = new ArrayList<>();
        var assets = new File(LDLib2.getAssetsDir(), "viscript_npc/npc");
        if (assets.exists() && assets.isDirectory()) {
            try (var stream = Files.walk(assets.toPath())) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    String string = file.toString();
                    if (string.endsWith(".npc")) {
                        npcFiles.add("\"" + string.replace(assets.getPath() + "\\", "").replace("\\", "/").replace(".npc", "") + "\"");
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return npcFiles;
    }

    static File getNpcFile(String fileName) {
        if (fileName.startsWith("\"")) fileName = fileName.substring(1);
        if (fileName.endsWith("\""))   fileName = fileName.substring(0, fileName.length() - 1);
        return new File(LDLib2.getAssetsDir(), "viscript_npc/npc/" + fileName + ".npc");
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
