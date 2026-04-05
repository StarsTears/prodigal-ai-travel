// @ts-ignore
/* eslint-disable */
import { request } from "@/request";

/** 注销账号 逻辑删除当前用户，并吊销其在服务端登记的全部 JWT；需携带有效 Bearer token POST /auth/deregister */
export async function deregister(options?: { [key: string]: any }) {
  return request<API.BaseResultVoid>(`/api/auth/deregister`, {
    method: "POST",
    ...(options || {}),
  });
}

/** 登录（账号/邮箱 + 密码） POST /auth/login */
export async function login(
  body: API.LoginRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultLoginResponse>(`/api/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 登录（邮箱 + 验证码） 邮箱必须先注册；验证码通过「发送登录验证码」获取 POST /auth/login/by-code */
export async function loginByCode(
  body: API.LoginByCodeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultLoginResponse>(`/api/auth/login/by-code`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 退出登录 从服务端移除当前 JWT 白名单记录；请同时清除前端本地 token POST /auth/logout */
export async function logout(options?: { [key: string]: any }) {
  return request<API.BaseResultVoid>(`/api/auth/logout`, {
    method: "POST",
    ...(options || {}),
  });
}

/** 注册 校验邮箱验证码后创建账号 POST /auth/register */
export async function register(
  body: API.RegisterRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultString>(`/api/auth/register`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 发送验证码 向邮箱发送 6 位数字验证码，同一邮箱有发送间隔限制 POST /auth/send-code */
export async function sendEmailCode(
  body: API.SendRegisterCodeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultString>(`/api/auth/send-code`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}
