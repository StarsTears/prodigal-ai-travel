package com.prodigal.travel.client;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class TravelAiClientTest {
    @Resource
    private TravelAiClient travelAiClient;

    @Test
    void doChat() {
        String chatId = UUID.fastUUID().toString();
        //第一轮
        String content = travelAiClient.doChat("你好！我是 Itlang", chatId);
        log.info(content);
        assertNotNull(content);
        //第二轮
        content = travelAiClient.doChat("我想去贵州旅游，有推荐的景点吗？", chatId);
        log.info(content);
        assertNotNull(content);
        //第三轮
        content = travelAiClient.doChat("那些景点好玩吗？", chatId);
        log.info(content);
        assertNotNull(content);
    }


    @Test
    void doChatWithRag() {
        String chatId = UUID.fastUUID().toString();
        String content = travelAiClient.doChatWithRag("我想去伦敦旅游，有推荐的景点吗？并将内容发送给 邮箱", chatId);
        assertNotNull(content);
    }

    @Test
    void doChatWithTool() {
        //第一轮
        String chatId = UUID.fastUUID().toString();
        String content = travelAiClient.doChatWithTool("当前时间是多少？厦门近几日的天气如何?", chatId);
        assertNotNull(content);
        //第二轮 ,2812632023@qq.com
        content = travelAiClient.doChatWithTool("将内容发送给 198116203@qq.com", chatId);
        assertNotNull(content);
    }

//    @Test
//    void doChatWithMCP() {
//        String chatId = UUID.fastUUID().toString();
//        String message = "我想去贵州旅游，有推荐的景点吗？若要区梵净山游玩，请给出行程规划";
//        String content = travelAiClient.doChatWithMCP(message, chatId);
//        assertNotNull(content);
//
//        //第二轮 ,2812632023@qq.com
//        content = travelAiClient.doChatWithTool("将上述内容发送给 198116203@qq.com", chatId);
//        assertNotNull(content);
//    }
}