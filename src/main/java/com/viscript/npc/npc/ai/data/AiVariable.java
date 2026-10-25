package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

/** 通过稳定 ID 引用的变量声明；显示名称可独立修改。 */
public class AiVariable implements IPersistedSerializable {
    @Persisted public String id = java.util.UUID.randomUUID().toString();
    @Persisted public String name = "";
    /** 可选的显示名称翻译键；未填写或未提供翻译时使用内部名称。 */
    @Persisted public String displayNameKey = "";
    /** 可选的用途说明翻译键，供参数表单和蓝图端口显示悬浮提示。 */
    @Persisted public String tipsKey = "";
    @Persisted public AiValue initial = new AiValue();
}
