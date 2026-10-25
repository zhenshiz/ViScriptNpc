package com.viscript.npc.util;

import com.viscript_lib.gui.editor.EditorAssetFiles;
import com.viscript_lib.gui.editor.EditorFileFormat;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** 按文件类型分组的 VSN 资产目录；文件名与相对路径校验沿用 VSL。 */
public final class NpcEditorFiles {
    private NpcEditorFiles() {}

    /**
     * 返回指定类型的功能文件目录。
     *
     * @param format 文件格式定义
     * @return <code>viscript_npc/&lt;domain&gt;/function</code> 目录
     */
    public static File functionDirectory(EditorFileFormat format) {
        return new File(format.functionDirectory(), "function");
    }

    /**
     * 返回指定类型的工程文件目录；本方法不创建目录。
     *
     * @param format 文件格式定义
     * @return <code>viscript_npc/&lt;domain&gt;/project</code> 目录
     */
    public static File projectDirectory(EditorFileFormat format) {
        return new File(format.functionDirectory(), "project");
    }

    public static Path resolveRuntimeFile(EditorFileFormat format, String name, boolean appendSuffix) {
        return EditorAssetFiles.resolveFile(functionDirectory(format), name, format.runtimeSuffix(), appendSuffix);
    }

    public static Path resolveProjectFile(EditorFileFormat format, String name, boolean appendSuffix) {
        return EditorAssetFiles.resolveFile(projectDirectory(format), name, format.projectSuffix(), appendSuffix);
    }

    public static List<String> listRuntimeFiles(EditorFileFormat format, boolean stripSuffix) {
        return EditorAssetFiles.listFiles(functionDirectory(format), format.runtimeSuffix(), stripSuffix);
    }

    public static List<String> listProjectFiles(EditorFileFormat format, boolean stripSuffix) {
        return EditorAssetFiles.listFiles(projectDirectory(format), format.projectSuffix(), stripSuffix);
    }
}
