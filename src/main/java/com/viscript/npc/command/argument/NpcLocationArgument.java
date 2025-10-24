package com.viscript.npc.command.argument;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public class NpcLocationArgument extends ResourceLocationArgument {
    public static NpcLocationArgument npc() {
        return new NpcLocationArgument();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (LDLib2.isClient()) {
            return SharedSuggestionProvider.suggestResource(
                    Minecraft.getInstance().getResourceManager().listResources("npc", arg -> arg.getPath().endsWith(".npc")).keySet()
                            .stream().map(rl -> ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), rl.getPath().substring(4, rl.getPath().length() - 4))),
                    builder);
        }
        return super.listSuggestions(context, builder);
    }
}
