package com.viscript.npc.npc.ai.mind.intention;

import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import com.viscript.npc.npc.ai.mind.api.BaseIntention;
import com.viscript.npc.npc.ai.mind.api.IntentionFunctionRegistries;
import com.viscript.npc.npc.ai.mind.api.IntentionType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CustomIntention extends SimpleIntention {
    @Getter
    protected final Map<String, Object> intentionMemory;
    protected final ResourceLocation executeFucRes;
    protected final ResourceLocation holdFuncRes;

    public CustomIntention(Entity origin, IntentionType type, ResourceLocation executeFucRes, ResourceLocation holdFuncRes) {
        super(origin, type);
        this.executeFucRes = executeFucRes;
        this.holdFuncRes = holdFuncRes;
        this.intentionMemory = new HashMap<>();
    }

    @Override
    public boolean execute() {
        Optional<Predicate<BaseIntention>> executeFunction = IntentionFunctionRegistries.getExecuteFunction(executeFucRes);
        return executeFunction.map(baseIntentionPredicate -> baseIntentionPredicate.test(this)).orElse(true);
    }

    @Override
    public void hold() {
        Optional<Consumer<BaseIntention>> holdFunction = IntentionFunctionRegistries.getHoldFunction(holdFuncRes);
        holdFunction.ifPresent(baseIntentionConsumer -> baseIntentionConsumer.accept(this));
    }

    @Override
    public CompoundTag serialize() {
        return super.serialize();
    }

    @Override
    public void deserialize(CompoundTag tag) {
        super.deserialize(tag);
    }
}
