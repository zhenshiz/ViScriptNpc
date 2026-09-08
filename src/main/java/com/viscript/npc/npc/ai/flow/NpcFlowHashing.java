package com.viscript.npc.npc.ai.flow;

import net.minecraft.nbt.*;
import org.thexeler.api.asset.AssetHashing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NpcFlowHashing {
    private NpcFlowHashing() {}

    public static String hash(Tag tag) {
        StringBuilder canonical = new StringBuilder();
        append(tag, canonical);
        return AssetHashing.sha256(canonical.toString());
    }

    private static void append(Tag tag, StringBuilder output) {
        if (tag instanceof CompoundTag compound) {
            output.append('{');
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            for (String key : keys) {
                output.append(key.length()).append(':').append(key).append('=');
                append(compound.get(key), output);
                output.append(';');
            }
            output.append('}');
        } else if (tag instanceof ListTag list) {
            output.append('[');
            for (Tag value : list) append(value, output);
            output.append(']');
        } else if (tag != null) {
            output.append(tag.getId()).append(':').append(tag);
        } else {
            output.append("null");
        }
    }
}
