package com.viscript.npc.gui.ai;

import com.viscript.npc.npc.ai.data.AiProgram;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 行为库和行为文件选择器共用的本地化名称。 */
public final class AiActionNames {
    private AiActionNames() {}

    /**
     * 解析行为名称，未提供或无法翻译名称时保留逻辑文件标识。
     *
     * @param id 字符串形式的逻辑文件标识
     * @param program 行为定义；无法解析的引用可为 {@code null}
     * @return 本地化显示名称或文件标识
     */
    public static Component displayName(String id, @Nullable AiProgram program) {
        return program == null || program.displayNameKey.isBlank() ? Component.literal(id)
                : Component.translatableWithFallback(program.displayNameKey, id);
    }
}
