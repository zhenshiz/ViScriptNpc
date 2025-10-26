package com.viscript.npc.npc.ai.mind.api;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class IntentionFunctionRegistries {
    private static final HashMap<ResourceLocation, Consumer<BaseIntention>> holdFunctions = new HashMap<>();
    private static final HashMap<ResourceLocation, Predicate<BaseIntention>> executeFunctions = new HashMap<>();

    public static Optional<Consumer<BaseIntention>> getHoldFunction(ResourceLocation id) {
        return Optional.ofNullable(holdFunctions.get(id));
    }

    public static Optional<Predicate<BaseIntention>> getExecuteFunction(ResourceLocation id) {
        return Optional.ofNullable(executeFunctions.get(id));
    }

    public static void registerHoldFunction(ResourceLocation id, Consumer<BaseIntention> function) {
        holdFunctions.put(id, function);
    }

    public static void registerExecuteFunction(ResourceLocation id, Predicate<BaseIntention> function) {
        executeFunctions.put(id, function);
    }
}
