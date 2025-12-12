package com.viscript.npc.plugin;

import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.INpcData;
import net.neoforged.neoforge.attachment.AttachmentType;

public class RegisterNpcEvent {

    /**
     * 注册npc的属性，并自动生成对应的可视化界面
     * @param clazz npc属性类
     * @param attachmentType npc属性类对应的数据附件
     * @param <T> npc通用属性类型
     */
    public <T extends INpcData> void registerNpcAttachment(Class<T> clazz, AttachmentType<T> attachmentType) {
        CustomNpc.putNpcAttachment(clazz, attachmentType);
    }
}
