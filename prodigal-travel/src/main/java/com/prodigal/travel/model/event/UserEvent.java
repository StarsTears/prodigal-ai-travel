package com.prodigal.travel.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 用户消息
 * @since 2026/4/15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String type;
}
