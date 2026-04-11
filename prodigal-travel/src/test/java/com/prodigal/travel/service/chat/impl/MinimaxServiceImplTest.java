package com.prodigal.travel.service.chat.impl;

import cn.hutool.core.lang.UUID;
import com.prodigal.travel.service.chat.AIService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
class MinimaxServiceImplTest {
    @Resource
    private AIService minimaxService;

    @Test
    void doChat() {
        String chatId = UUID.fastUUID().toString();
        String message = "你好！我是Prodigal！请问今天几号";
        String result = minimaxService.doChat(message, chatId);
        log.info(result);
        Assertions.assertNotNull( result);
        message = "获取当前位置。明天天气咋样？";
        result = minimaxService.doChatWithMCP(message, chatId,"222.76.147.108");
        log.info(result);
        Assertions.assertNotNull( result);
        message = "我是谁？";
        result = minimaxService.doChat(message, chatId);
        log.info(result);
        Assertions.assertNotNull( result);
    }
}