package com.prodigal.travel.tools;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class BaiduTranslateToolTest {
    @Value("${prodigal.baidu.app-id:}")
    private String baiduAppId;
    @Value("${prodigal.baidu.api-key:}")
    private String baiduSecretKey;
    @Test
    void translateToEnglish() {
        BaiduTranslateTool baiduTranslateTool = new BaiduTranslateTool(baiduAppId, baiduSecretKey);
        String translate = baiduTranslateTool.translateToEnglish("你好！明月松间照、清泉石上流！");
        log.info(translate);
    }
}