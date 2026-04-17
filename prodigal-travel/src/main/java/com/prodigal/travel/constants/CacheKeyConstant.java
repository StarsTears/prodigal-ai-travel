package com.prodigal.travel.constants;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 缓存key
 * @since 2026/4/5
 */
public interface CacheKeyConstant {
    //用户token
    String TOKEN_KEY_PREFIX = "prodigal:auth:token:";
    String USER_TOKENS_KEY_PREFIX = "prodigal:auth:user-tokens:";

    //邮件验证码
    String EMAIL_CODE_KEY_PREFIX = "prodigal:auth:email-code:";
    String EMAIL_THROTTLE_KEY_PREFIX = "prodigal:auth:email-throttle:";

    //会话缓存
    String CHAT_KEY_PREFIX = "prodigal:chat:memory:recent:";

}
