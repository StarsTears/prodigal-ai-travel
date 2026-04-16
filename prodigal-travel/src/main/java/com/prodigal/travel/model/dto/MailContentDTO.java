package com.prodigal.travel.model.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 邮件内容
 * @since 2026/4/15
 */
@Data
@Builder
public class MailContentDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String subject;

    private String content;

    private String to;

    private boolean isHtml = false;

}
