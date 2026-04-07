package com.prodigal.travel.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

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
    private String  systemPrompt;
    private String  nextStepPrompt;

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
     * @param userPrompt 用户提示词
     * @return
     */
    public String run(String userPrompt){
        //校验
        if (state != AgentState.IDLE){
            throw new RuntimeException("Cannot run agent from state:"+ this.state);
        }
        if (StrUtil.isBlank(userPrompt)){
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
            while (this.currentStep < this.maxStep && this.state != AgentState.FINISHED){
                this.currentStep+=1;
                log.info("Running step: {}/{} for agent {}", this.currentStep,this.maxStep,name);
                String result = this.step();
                log.info("Step {} result: {}",this.currentStep, result);
                resultList.add(result);
            }
            if (this.currentStep>=this.maxStep){
                this.state = AgentState.FINISHED;
                resultList.add(String.format("Terminate: Max steps (%d)  reached.",this.maxStep));
            }
            return String.join("\n",resultList);
        } catch (Exception e) {
            this.state = AgentState.ERROR;
            log.error("Error running agent: {}",e.getMessage());
            return "执行错误！"+ e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    /**
     * 运行单个步骤
     * @return 步骤结果
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup(){

    }
}
