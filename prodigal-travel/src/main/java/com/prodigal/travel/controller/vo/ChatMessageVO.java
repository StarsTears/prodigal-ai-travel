package com.prodigal.travel.controller.vo;

import com.prodigal.travel.model.entity.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 会话VO
 * @since 2026/4/4
 */
@Data
@Builder
public class ChatMessageVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String conversationId;

    private String title;

    private List<ChatMessage> messages;

    /**
     * 会话最近活动时间（用于前端历史分组、排序），一般对应库表 update_time。
     */
    private Date updateTime;

}
