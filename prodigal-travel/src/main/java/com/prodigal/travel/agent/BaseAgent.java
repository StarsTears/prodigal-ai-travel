package com.prodigal.travel.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description: 抽象基础代理，管理代理状态、执行流程
 * @since 2026/4/6
 */
@Slf4j
@Data
public abstract class BaseAgent {
    private String name;

    // 提示
    private String systemPrompt;
    private String nextStepPrompt;

    //状态
    private AgentState state = AgentState.IDLE;

    //执行控制
    private int maxStep = 10;
    private int currentStep = 0;

    //LLM
    private ChatClient chatClient;

    //Memory 自主维护会话上下文信息
    private List<Message> messageList = new ArrayList<>();

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return
     */
    public String run(String userPrompt) {
        //校验
        if (state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state:" + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt!");
        }
        //执行，更改状态
        this.state = AgentState.RUNNING;
        //记录上下文
        messageList.add(new UserMessage(userPrompt));
        //结果列表
        List<String> resultList = new ArrayList<>();
        try {
            //执行循环（agent loop）
            while (this.currentStep < this.maxStep && this.state != AgentState.FINISHED) {
                this.currentStep += 1;
                log.info("Running step: {}/{} for agent {}", this.currentStep, this.maxStep, name);
                String result = this.step();
                log.info("Step {} result: {}", this.currentStep, result);
                resultList.add(result);
            }
            if (this.currentStep >= this.maxStep) {
                this.state = AgentState.FINISHED;
                resultList.add(String.format("Terminate: Max steps (%d)  reached.", this.maxStep));
            }
            return String.join("\n", resultList);
        } catch (Exception e) {
            this.state = AgentState.ERROR;
            log.error("Error running agent: {}", e.getMessage());
            return "执行错误！" + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    /**
     * 运行代理 (流式输出)
     *
     * @param userPrompt 用户提示词
     * @return
     */
    public SseEmitter runStream(String userPrompt) {

        SseEmitter sseEmitter = new SseEmitter(300000L);
        //使用异步处理
        CompletableFuture.runAsync(() -> {
            //校验
            try {
                if (state != AgentState.IDLE) {

                    sseEmitter.send("错误:无法从状态运行代理 ！");

                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误:不能使用空提示词运行代理！");
                    sseEmitter.complete();
                    return;
                }
            } catch (IOException e) {
                sseEmitter.completeWithError(e);
            }
            //执行，更改状态
            this.state = AgentState.RUNNING;
            //记录上下文
            messageList.add(new UserMessage(userPrompt));
            //结果列表
            List<String> resultList = new ArrayList<>();
            try {
                //执行循环（agent loop）
                while (this.currentStep < this.maxStep && this.state != AgentState.FINISHED) {
                    this.currentStep += 1;
                    log.info("Running step: {}/{} for agent {}", this.currentStep, this.maxStep, name);
                    String result = this.step();
                    log.info("Step {} result: {}", this.currentStep, result);
//                    resultList.add(result);
                    //发送每一步的结果
                    sseEmitter.send(result);
                }
                if (this.currentStep >= this.maxStep) {
                    this.state = AgentState.FINISHED;
                    sseEmitter.send(String.format("执行结束:达到最大步骤 (%d).", this.maxStep));
                }
                sseEmitter.complete();
            } catch (Exception e) {
                this.state = AgentState.ERROR;
                log.error("Error running agent: {}", e.getMessage());
                try {
                    sseEmitter.send("执行错误！" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
            }
        });

        //设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("Timeout error running agent.");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("Completed running agent.");
        });
        return sseEmitter;
    }

    /**
     * 运行单个步骤
     *
     * @return 步骤结果
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {

    }
}
