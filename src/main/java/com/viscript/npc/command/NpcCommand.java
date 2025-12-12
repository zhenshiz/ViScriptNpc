package com.viscript.npc.command;

import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript.npc.util.npc.NpcHelper;
import lombok.SneakyThrows;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@LDLRegisterClient(name = "npc", registry = "viscript_npc:command")
public class NpcCommand implements ICommand {
    public static final Set<ResourceLocation> npcFilesPath = new HashSet<>();

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        dispatcher.register(Commands.literal(ViScriptNpc.MOD_ID).requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("location", ResourceLocationArgument.id())
                                .suggests(this::npcFileSuggestions)
                                .executes(context -> this.summon(context, null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(context -> this.summon(context, Vec3Argument.getVec3(context, "pos")))
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .executes(this::reload)
                )
                .then(Commands.literal("editor")
                        .executes(this::openEditor)
                )
        );
    }

    //仅用于npc文件的补全重载
    private int reload(CommandContext<CommandSourceStack> context) {
        npcFilesPath.clear();
        for (String path : NpcHelper.scanNpcFiles()) {
            npcFilesPath.add(ViScriptNpc.id(path));
        }
        context.getSource().sendSuccess(() -> Component.translatable("command.viscript_npc.reload"), true);
        return 1;
    }

    @SneakyThrows
    private int summon(CommandContext<CommandSourceStack> context, Vec3 pos) {
        ResourceLocation location = ResourceLocationArgument.getId(context, "location");
        CommandSourceStack source = context.getSource();
        if (pos == null) {
            Entity entity = source.getEntity();
            if (entity != null) {
                pos = entity.position();
            } else {
                throw entityOnlyException();
            }
        }
        Entity npc = ViScriptNpcServerUtil.summonNpc(location, pos);
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
            ViScriptNpcServerUtil.openNpcEditor(player);
            return 1;
        } else {
            throw playerOnlyException();
        }
    }

    private CompletableFuture<Suggestions> npcFileSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        SharedSuggestionProvider.suggestResource(npcFilesPath, builder);
        return builder.buildFuture();
    }
}
