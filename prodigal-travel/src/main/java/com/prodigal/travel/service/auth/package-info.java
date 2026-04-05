/**
 * 认证编排层：对外暴露注册/登录/退出/注销等业务入口，内部委托 {@link com.prodigal.travel.service.UserService}
 * 做用户数据与签发 JWT；会话白名单由 {@link com.prodigal.travel.security.LoginTokenCache} 维护。
 *
 * @see com.prodigal.travel.controller.AuthController HTTP 入口
 * @see com.prodigal.travel.security.package-info 登录与鉴权流程说明
 */
package com.prodigal.travel.service.auth;
