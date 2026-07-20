package com.viscript.npc.npc.data.ai.runtime;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.ai.NpcAI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.thexeler.MindMachine;
import org.thexeler.api.IntentionTypeRegistry;
import org.thexeler.intention.BaseIntention;

public class NpcBehaviorTreeIntention extends BaseIntention {
    public static final ResourceLocation TYPE = ViScriptNpc.id("behavior_tree");

    static {
        IntentionTypeRegistry.register(TYPE, (machine, tag) -> {
            NpcBehaviorTreeIntention intention = new NpcBehaviorTreeIntention(machine);
            intention.deserializeAdditional(tag);
            return intention;
        });
    }

    private NpcBehaviorRuntime runtime;
    private CompoundTag aiTag = new CompoundTag();

    public NpcBehaviorTreeIntention(MindMachine machine) {
        super(machine, machine.getOrigin());
    }

    public NpcBehaviorTreeIntention(MindMachine machine, NpcAI ai) {
        this(machine);
        configure(ai);
    }

    public void configure(NpcAI ai) {
        aiTag = new CompoundTag();
        aiTag.putBoolean("enabled", ai != null && ai.isEnabled());
        aiTag.putInt("tickRate", ai == null ? 8 : ai.getTickRate());
        aiTag.put("behaviorGraph", ai == null ? new CompoundTag() : ai.getBehaviorGraph().copy());
        aiTag.put("behaviorProgram", ai == null ? new CompoundTag() : ai.getCompiledBehaviorProgram());
        runtime = null;
    }

    @Override
    public boolean execute() {
        return false;
    }

    @Override
    public void tick() {
        NpcBehaviorRuntime runtime = getRuntime();
        if (runtime != null) {
            runtime.tick();
        }
    }

    @Override
    public void hold() {
        if (origin instanceof org.thexeler.api.world.MindEntityActor actor && actor.entity() instanceof CustomNpc npc) {
            npc.getNavigation().stop();
        }
    }

    @Override
    public ResourceLocation getType() {
        return TYPE;
    }

    @Override
    protected void serializeAdditional(CompoundTag tag) {
        tag.put("ai", aiTag.copy());
    }

    @Override
    protected void deserializeAdditional(CompoundTag tag) {
        aiTag = tag.getCompound("ai").copy();
        runtime = null;
    }

    private NpcBehaviorRuntime getRuntime() {
        if (runtime != null) {
            return runtime;
        }
        if (!(origin instanceof org.thexeler.api.world.MindEntityActor actor) || !(actor.entity() instanceof CustomNpc npc)) {
            return null;
        }
        NpcAI ai = new NpcAI();
        ai.setEnabled(aiTag.getBoolean("enabled"));
        ai.setTickRate(aiTag.contains("tickRate") ? aiTag.getInt("tickRate") : 8);
        ai.setBehaviorGraph(aiTag.getCompound("behaviorGraph").copy());
        ai.setBehaviorProgram(aiTag.getCompound("behaviorProgram").copy());
        runtime = new NpcBehaviorRuntime(npc);
        runtime.configure(ai);
        return runtime;
    }

    @Nullable
    public NpcBehaviorRuntime runtime() {
        return getRuntime();
    }
}
