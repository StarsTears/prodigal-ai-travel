package com.prodigal.travel.advisor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

/**
 * {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor} 会把历史
 * user/assistant 放在前、本轮指令（含 system）放在后，得到 [user, assistant, system, user]。
 * MiniMax 等厂商要求 system 在首位，否则会报 invalid message role: system。
 */
public class SystemMessageFirstAdvisor implements BaseAdvisor {

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 1;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        List<Message> messages = chatClientRequest.prompt().getInstructions();
        List<Message> system = new ArrayList<>();
        List<Message> rest = new ArrayList<>();
        for (Message m : messages) {
            if (m.getMessageType() == MessageType.SYSTEM) {
                system.add(m);
            }
            else {
                rest.add(m);
            }
        }
        List<Message> ordered = new ArrayList<>(system.size() + rest.size());
        ordered.addAll(system);
        ordered.addAll(rest);
        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().mutate().messages(ordered).build())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }
}
