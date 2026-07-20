package com.viscript.npc.command;

import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@LDLRegister(name = "npc", registry = ICommand.COMMAND_ID)
public class NpcCommand implements ICommand {
    private static final double DEFAULT_PATH_TEST_SPEED = 1.0D;
    private static final double NEAREST_PATH_TEST_RANGE = 128.0D;

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
                .then(Commands.literal("path")
                        .then(Commands.literal("test")
                                .then(Commands.literal("nearest")
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(context -> this.pathTestNearest(context, DEFAULT_PATH_TEST_SPEED))
                                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.01D, 16.0D))
                                                        .executes(context -> this.pathTestNearest(context, DoubleArgumentType.getDouble(context, "speed")))
                                                )
                                        )
                                )
                        )
                )
        );
    }

    public static final EditorFileFormat FORMAT = NpcEditorFormats.NPC;

    static List<String> getServerNpcFiles() {
        return EditorAssetFiles.listRuntimeFiles(FORMAT, true);
    }

    static List<String> getServerEditorFiles() {
        return EditorAssetFiles.listProjectFiles(FORMAT, true);
    }

    static File getNpcProjectFile(String fileName) {
        return EditorAssetFiles.resolveProjectFile(FORMAT, normalizeFileArgument(fileName), true).toFile();
    }

    static File getNpcFile(String fileName) {
        return EditorAssetFiles.resolveRuntimeFile(FORMAT, normalizeFileArgument(fileName), true).toFile();
    }

    private static String normalizeFileArgument(String fileName) {
        if (fileName.startsWith("\"")) fileName = fileName.substring(1);
        if (fileName.endsWith("\"")) fileName = fileName.substring(0, fileName.length() - 1);
        return fileName;
    }

    static CompoundTag readNpcFile(File file) {
        if (!file.exists()) return new CompoundTag();
        try (var inputStream = Files.newInputStream(file.toPath())) {
            return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            return new CompoundTag();
        }
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

    private int pathTestNearest(CommandContext<CommandSourceStack> context, double speed) {
        CommandSourceStack source = context.getSource();
        CustomNpc npc = findNearestPathTestNpc(source);
        if (npc == null) {
            source.sendFailure(Component.translatable("command.viscript_npc.path.no_nearest_npc", NEAREST_PATH_TEST_RANGE));
            return 0;
        }
        return pathTest(source, npc, Vec3Argument.getVec3(context, "pos"), speed);
    }

    private int pathTest(CommandSourceStack source, CustomNpc npc, Vec3 target, double speed) {
        Path path = npc.getNavigation().createPath(target.x, target.y, target.z, 1);
        if (path == null) {
            source.sendFailure(Component.translatable("command.viscript_npc.path.failed",
                    npc.getDisplayName(), formatVec3(target)));
            return 0;
        }
        if (!path.canReach()) {
            source.sendFailure(Component.translatable("command.viscript_npc.path.unreachable",
                    npc.getDisplayName(), formatVec3(target), path.getNodeCount(), formatBlockPos(path.getTarget())));
            return 0;
        }
        boolean moving = npc.startCommandPathTest(path, target, speed);
        if (!moving) {
            source.sendFailure(Component.translatable("command.viscript_npc.path.move_failed",
                    npc.getDisplayName(), formatVec3(target), path.getNodeCount(), path.canReach()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.viscript_npc.path.started",
                npc.getDisplayName(), formatVec3(target), speed, path.getNodeCount(), path.canReach(), formatBlockPos(path.getTarget())), true);
        return 1;
    }

    private CustomNpc findNearestPathTestNpc(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        AABB bounds = AABB.ofSize(pos, NEAREST_PATH_TEST_RANGE * 2.0D, NEAREST_PATH_TEST_RANGE * 2.0D, NEAREST_PATH_TEST_RANGE * 2.0D);
        return level.getEntitiesOfClass(CustomNpc.class, bounds, CustomNpc::isAlive).stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(pos), b.distanceToSqr(pos)))
                .orElse(null);
    }

    private static String formatVec3(Vec3 pos) {
        return "%.2f %.2f %.2f".formatted(pos.x, pos.y, pos.z);
    }

    private static String formatBlockPos(net.minecraft.core.BlockPos pos) {
        return "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
    }
}
