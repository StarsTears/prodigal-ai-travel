package com.prodigal.travel.tools;

import cn.hutool.core.io.FileUtil;
import com.prodigal.travel.constants.TravelConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;


/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 文件操作工具
 * @since 2026/4/2
 */
public class FileOperationTool {
    public static final String FILE_DIR = TravelConstant.FILE_SAVE_PATH +"/"+TravelConstant.FILE_DIR;
    @Tool(description = "write content to fileName")
    public String writeFile(@ToolParam(description = "file name") String fileName,
                            @ToolParam(description = "content write to a file") String content){
        String filePath = FILE_DIR +"/"+fileName;
        try {
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "file write successfully to: " + filePath;
        }catch (Exception e){
            return "file write failed: " + e.getMessage();
        }
    }

    @Tool(description = "read content from fileName")
    public String readFile(@ToolParam(description = "file name") String fileName){
        String filePath = FILE_DIR +"/"+fileName;
        try {
            String content = FileUtil.readUtf8String(filePath);
            return "file read successfully from: " + filePath + "\n" + content;
        }catch (Exception e){
            return "file read failed: " + e.getMessage();
        }
    }
}
