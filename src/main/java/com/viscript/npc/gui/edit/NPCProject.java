package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.menu.FileMenu;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.npc.NPC;
import com.viscript.npc.util.npc.NpcHelper;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;

public class NPCProject implements IProject {
    public static int VERSION = 1;
    public static final FileMenu.ProjectProvider PROVIDER = FileMenu.ProjectProvider.of(IGuiTexture.EMPTY, Component.translatable("editor.project.npc.add").getString(), ".npcproj", NPCProject::new);

    @Getter
    private final Resources resources;
    public NPC npc = new NPC();

    // runtime
    //导出npc数据文本按钮
    @Nullable
    private ISubscription exportMenuSubscription;

    public NPCProject() {
        this.resources = Resources.of(
                ColorsResource.INSTANCE,
                TexturesResource.INSTANCE,
                IRendererResource.INSTANCE
        );
    }

    @Override
    public String getVersion() {
        return "%d.0".formatted(VERSION) ;
    }

    @Override
    public String getSuffix() {
        return PROVIDER.suffix;
    }

    @Override
    public String getName() {
        return PROVIDER.name;
    }

    @Override
    public CompoundTag serializeProject(@NotNull HolderLookup.Provider provider) {
        var data = new CompoundTag();
        data.put("npc", npc.serializeNBT(provider));
        return data;
    }

    @Override
    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        npc.deserializeNBT(provider, nbt.getCompound("npc"));
    }

    @Override
    public CompoundTag getMetadata() {
        var meta = IProject.super.getMetadata();
        meta.putInt("version_num", VERSION);
        return meta;
    }

    @Override
    public void onLoad(Editor editor) {
        IProject.super.onLoad(editor);
        if (exportMenuSubscription != null) {
            exportMenuSubscription.unsubscribe();
        }
        exportMenuSubscription = editor.fileMenu.registerMenuCreator((tab, menu) ->
                menu.branch("editor.project.menu.export", m ->
                        m.leaf("editor.project.export_npc", () -> {
                            Dialog.showFileDialog("editor.project.tips.save_as", new File(LDLib2.getAssetsDir(), "%s/npc/".formatted(ViScriptNpc.MOD_ID)), false,
                                    Dialog.suffixFilter(NPC.SUFFIX), file -> {
                                        if (file != null && !file.isDirectory()) {
                                            if (!file.getName().endsWith(NPC.SUFFIX)) {
                                                file = new File(file.getParentFile(), file.getName() + NPC.SUFFIX);
                                            }
                                            try {
                                                var fileData = npc.serializeNBT(Platform.getFrozenRegistry());
                                                NbtIo.writeCompressed(fileData, file.toPath());
                                                NpcHelper.clearCache();
                                            } catch (Exception ignored) {}
                                        }
                                    }).show(editor);
                        })
                ));
    }

    @Override
    public void onClosed(Editor editor) {
        IProject.super.onClosed(editor);
        if (exportMenuSubscription != null) {
            exportMenuSubscription.unsubscribe();
            exportMenuSubscription = null;
        }
    }
}
