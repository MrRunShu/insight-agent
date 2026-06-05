package com.insightagent.tools;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * Tool: read and write text files under the project's {@code /tmp/insight/} directory.
 *
 * <p>The save directory is isolated so the AI cannot touch arbitrary paths.
 * Useful for persisting analysis reports, exporting summaries, etc.
 */
@Slf4j
public class FileOperationTool {

    /** All files are confined to this directory. */
    private static final String SAVE_DIR =
            System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "insight";

    @Tool(description = "Save text content to a named file. "
            + "Use this to persist an analysis report or summary for the user. "
            + "Returns the absolute path of the saved file.")
    public String writeFile(
            @ToolParam(description = "File name (e.g. 'analysis.md'). No path separators.")
            String fileName,
            @ToolParam(description = "Text content to write to the file.")
            String content) {

        // Reject names with path separators to prevent path traversal
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return "Error: invalid file name";
        }
        String filePath = SAVE_DIR + File.separator + fileName;
        try {
            FileUtil.writeUtf8String(content, filePath);
            log.info("[FileOperationTool] wrote {} bytes to {}", content.length(), filePath);
            return "Saved to: " + filePath;
        } catch (Exception e) {
            log.warn("[FileOperationTool] write failed: {}", e.getMessage());
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool(description = "Read the text content of a previously saved file. "
            + "Returns the file content as a string.")
    public String readFile(
            @ToolParam(description = "File name to read (e.g. 'analysis.md').")
            String fileName) {

        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return "Error: invalid file name";
        }
        String filePath = SAVE_DIR + File.separator + fileName;
        try {
            String content = FileUtil.readUtf8String(filePath);
            log.info("[FileOperationTool] read {} bytes from {}", content.length(), filePath);
            return content;
        } catch (Exception e) {
            log.warn("[FileOperationTool] read failed: {}", e.getMessage());
            return "Error reading file: " + e.getMessage();
        }
    }
}
