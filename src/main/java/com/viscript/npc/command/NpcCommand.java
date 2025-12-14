package com.viscript.npc.command;

import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.command.argument.NpcLocationArgument;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import lombok.SneakyThrows;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

@LDLRegister(name = "npc", registry = "viscript_npc:command")
public class NpcCommand implements ICommand {
    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        dispatcher.register(Commands.literal(ViScriptNpc.MOD_ID).requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("location", NpcLocationArgument.npc())
                                .executes(context -> this.summon(context, null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(context -> this.summon(context, Vec3Argument.getVec3(context, "pos")))
                                )
                        )
                )
                .then(Commands.literal("editor")
                        .executes(this::openEditor)
                )
        );
    }

    @SneakyThrows
    private int summon(CommandContext<CommandSourceStack> context, Vec3 pos) {
        ResourceLocation location = NpcLocationArgument.getId(context, "location");
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
}
