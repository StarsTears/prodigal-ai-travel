package com.prodigal.travel.tools;

import com.prodigal.travel.tools.FileOperationTool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class FileOperationToolTest {

    @Test
    void writeFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "道德经_1.txt";
        String content = "道可道，非常道；名可名，非常名！";
        String result = fileOperationTool.writeFile(fileName, content);
        Assertions.assertNotNull( result);

    }

    @Test
    void readFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "道德经_1.txt";
        String result = fileOperationTool.readFile(fileName);
        log.info(result);
        Assertions.assertNotNull( result);
    }
}