import React from 'react';
import { Navigate } from 'umi';

/** 旧地址 /chat 统一到首页选择入口 */
const ChatRedirect: React.FC = () => <Navigate to="/entry" replace />;

export default ChatRedirect;
