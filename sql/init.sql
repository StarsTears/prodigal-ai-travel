-- pg vector 向量数据库，暂时不启用
SELECT * FROM pg_extension WHERE extname = 'vector';
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS travel_vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1536)
    );

-- ========== MySQL：用户与持久化会话（与 prodigal-travel 中 X-User-Id、chatId 对齐）==========
-- 建库
CREATE DATABASE IF NOT EXISTS prodigal_ai_travel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE prodigal_ai_travel;

-- 用户表：注册 / 登录（密码仅存哈希，如 BCrypt）
CREATE TABLE IF NOT EXISTS user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash   VARCHAR(255) NOT NULL COMMENT '密码哈希',
    email           VARCHAR(128) NOT NULL COMMENT '邮箱',
    nickname        VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    avatar_url      VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0 禁用 1 正常',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0 否 1 是',
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_email (email)
) COMMENT '用户表';

-- 会话元数据：便于按用户列出历史会话、标题等（与 TravelAssistantController 的 chatId 一致）
CREATE TABLE IF NOT EXISTS chat_conversation (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id          VARCHAR(64)  NOT NULL COMMENT '用户标识，与请求头 X-User-Id 一致',
    conversation_id  VARCHAR(64)  NOT NULL COMMENT '会话 ID（UUID）',
    title            VARCHAR(255) DEFAULT NULL COMMENT '会话标题，可由首条消息摘要生成',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_chat_conversation_id (conversation_id),
    KEY idx_chat_conversation_user (user_id, update_time)
) COMMENT '会话表';

-- 单条消息持久化（Spring AI ChatMemory / 前端消息列表）
CREATE TABLE IF NOT EXISTS chat_message (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    conversation_id  VARCHAR(64) NOT NULL COMMENT '会话 ID，对应 chat_conversation.conversation_id',
    user_id          VARCHAR(64) NOT NULL COMMENT '用户标识，与 X-User-Id 一致',
    role             VARCHAR(32) NOT NULL COMMENT '角色：user、assistant、system、tool 等',
    content          LONGTEXT    NOT NULL COMMENT '消息内容',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_chat_message_user_conv (user_id, conversation_id),
    KEY idx_chat_message_conv_time (conversation_id, create_time)
) COMMENT '会话消息表';
