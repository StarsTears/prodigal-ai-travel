/**
 * 登录与会话鉴权。
 *
 * <h2>整体说明</h2>
 * <p>采用 JWT（Hutool 签发）+ Redis 服务端白名单：仅当 JWT 签名有效、未过期
 * <strong>且</strong> 仍存在于 {@link LoginTokenCache} 时，视为已登录。
 * 退出或注销会从 Redis 移除对应记录，即使 JWT 未过期也无法再通过受保护接口。
 *
 * <h2>登录（签发 token）</h2>
 * <ol>
 *   <li><b>账号/邮箱 + 密码</b>：{@code UserAuthService.login} → {@code UserService.login}
 *       → 校验用户与 BCrypt 密码 → {@code UserServiceImpl.buildLoginResponse}
 *       → {@code issueToken} 生成 JWT → {@code LoginTokenCache.remember} 写入 Redis。</li>
 *   <li><b>邮箱 + 邮件验证码</b>：先 {@code UserAuthService.sendEmailCode}（Redis 存验证码与发送频控），
 *       再 {@code UserAuthService.loginByEmailCode} → 校验验证码 → {@code UserService.loginWithoutPassword}
 *       → 同样走 {@code buildLoginResponse}。</li>
 * </ol>
 *
 * <h2>访问受保护接口</h2>
 * <p>{@link com.prodigal.travel.config.AuthWebFilterConfig} 将 {@link JwtAuthenticationFilter} 挂到
 * {@code /travel/*}。过滤器校验 {@code Authorization: Bearer &lt;jwt&gt;}，依次：格式 → Hutool 验签与 exp
 * → {@code LoginTokenCache.isActive} → 解析 {@code userId} 写入 request 属性
 * {@link com.prodigal.travel.constants.LoginUserConstant#REQUEST_ATTR_USER_ID}。
 *
 * <h2>退出与注销</h2>
 * <ul>
 *   <li><b>退出</b>：{@code UserAuthService.logout} → {@code LoginTokenCache.revoke(当前 token)}。</li>
 *   <li><b>注销</b>：删用户后 {@code LoginTokenCache.revokeAllForUser}，吊销该用户在本服务登记的全部 token。</li>
 * </ul>
 */
package com.prodigal.travel.security;
