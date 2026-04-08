package com.prodigal.travel.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 推理顾问
 * @since 2026/4/1
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(this.before(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return streamAdvisorChain.nextStream(this.before(chatClientRequest));
    }

    /**
     * 执行请求前，改变 Prompt
     * @param request
     * @return
     */
    private ChatClientRequest before(ChatClientRequest request) {
        String text = request.prompt().getUserMessage().getText();
        //添加上下文参数
        request.context().put("re2_input_query",text);
        //修改用户提示词
        String newText = """
                %s
                Read the question again: %s
                """.formatted(text,text);

        Prompt prompt = request.prompt().augmentUserMessage(newText);
        return new ChatClientRequest(prompt,request.context());
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }



}
