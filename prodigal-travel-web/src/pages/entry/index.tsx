import {
  ArrowRightOutlined,
  BookOutlined,
  CheckOutlined,
  CompassOutlined,
  GlobalOutlined,
  RocketOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Modal } from 'antd';
import React, { useState } from 'react';
import { history } from 'umi';
import { AuthCard } from '@/components/auth/AuthCard';
import { UserSessionBlock } from '@/components/chat/UserSessionBlock';
import { type ChatMode, persistChatMode } from '@/utils/chatMode';

const TRAVEL_FEATURES = [
  '「联网工具」查天气、景点与实时信息',
  '「知识库 RAG」只依据已入库文档，资料不足会说明',
  '会话保存在服务端，可随时回到历史对话',
];

const MANUS_FEATURES = [
  '自主拆解任务，多步推理并调用工具',
  '思考与执行过程可展开，结论单独呈现',
  '会话仅保存在本机，适合一次性复杂任务',
];

const EntryPage: React.FC = () => {
  const [authOpen, setAuthOpen] = useState(false);

  const go = (mode: ChatMode) => {
    persistChatMode(mode);
    // 不再附带 fresh=1：进入智能体时保留本机历史，需要新对话用顶栏按钮
    history.push(mode === 'manus' ? '/chat/manus' : '/chat/travel');
  };

  return (
    <div className="app-shell entry-page">
      <nav className="entry-nav">
        <div className="chat-topbar__group">
          <span className="brand-mark">
            <img src="/logo.svg" alt="" width={22} height={22} />
          </span>
          <span className="entry-nav__title">AI 旅行工作台</span>
        </div>
        <UserSessionBlock variant="toolbar" onOpenLogin={() => setAuthOpen(true)} />
      </nav>

      <main className="entry-main">
        <section className="entry-hero">
          <div className="entry-hero__eyebrow">
            <span className="ui-chip ui-chip--brand">
              <ThunderboltOutlined /> 实时流式对话
            </span>
            <span className="ui-chip ui-chip--violet">工具编排</span>
          </div>
          <h1 className="entry-hero__title">
            把旅行问题，交给<em>会查证的 AI</em>
          </h1>
          <p className="entry-hero__desc">
            两种工作方式：旅游助手负责查资料、看天气、做行程；超级智能体负责把复杂目标拆成步骤并执行。
            选一个入口开始，进入后随时可以在顶栏切换。
          </p>
          <span className="entry-hero__en">AI Travel Workspace</span>
        </section>

        <section className="entry-cards">
          <article className="entry-card entry-card--blue">
            <div className="entry-card__icon entry-card__icon--blue">
              <CompassOutlined />
            </div>
            <h2 className="entry-card__title">AI 旅游助手</h2>
            <p className="entry-card__desc">
              问路线、查天气、找景点，一键得到可执行的出行建议；回答依据可以在联网工具与知识库之间切换。
            </p>
            <ul className="entry-card__features">
              {TRAVEL_FEATURES.map((f) => (
                <li key={f}>
                  <CheckOutlined />
                  <span>{f}</span>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className="entry-card__cta entry-card__cta--blue"
              onClick={() => go('travel')}
            >
              进入旅游助手 <ArrowRightOutlined />
            </button>
            <div className="entry-card__foot">适合：查资料 · 做行程 · 问天气</div>
          </article>

          <article className="entry-card entry-card--violet">
            <div className="entry-card__icon entry-card__icon--violet">
              <RocketOutlined />
            </div>
            <h2 className="entry-card__title">超级智能体</h2>
            <p className="entry-card__desc">
              面对多城市对比、带预算约束的规划等复杂任务，让智能体自己决定步骤、调用工具并汇总结论。
            </p>
            <ul className="entry-card__features">
              {MANUS_FEATURES.map((f) => (
                <li key={f}>
                  <CheckOutlined />
                  <span>{f}</span>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className="entry-card__cta entry-card__cta--violet"
              onClick={() => go('manus')}
            >
              启动超级智能体 <ArrowRightOutlined />
            </button>
            <div className="entry-card__foot">适合：多步任务 · 信息对比 · 自动执行</div>
          </article>
        </section>

        <section className="entry-guide">
          <span className="entry-guide__item">
            <GlobalOutlined /> 实时信息 <strong>联网工具</strong>
          </span>
          <span className="entry-guide__item">
            <BookOutlined /> 严格依据文档 <strong>知识库 RAG</strong>
          </span>
          <span className="entry-guide__item">
            <RocketOutlined /> 复杂任务 <strong>超级智能体</strong>
          </span>
        </section>

        <p className="entry-footer">
          未登录可浏览界面，发送消息前需登录授权　·　AI 生成内容仅供参考，请自行核实关键信息
        </p>
      </main>

      <Modal
        title={null}
        open={authOpen}
        onCancel={() => setAuthOpen(false)}
        footer={null}
        width={440}
        destroyOnHidden
        centered
        styles={{ content: { paddingTop: 20 } }}
      >
        <AuthCard
          embedded
          onLoginSuccess={() => setAuthOpen(false)}
          subtitle="登录后即可发送消息并与 AI 对话"
        />
      </Modal>
    </div>
  );
};

export default EntryPage;
