import {
  CompassOutlined,
  FireOutlined,
  RobotOutlined,
  RocketOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Button, Card, Flex, Space, Tag, Typography } from 'antd';
import React from 'react';
import { history } from 'umi';
import {
  type ChatMode,
  persistChatMode,
} from '@/utils/chatMode';

const { Title, Paragraph, Text } = Typography;

const cardBodyStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  height: '100%',
  minHeight: 220,
};

const EntryPage: React.FC = () => {
  const go = (mode: ChatMode) => {
    persistChatMode(mode);
    history.push(
      mode === 'manus' ? '/chat/manus?fresh=1' : '/chat/travel'
    );
  };

  return (
    <Flex
      className="entry-geek-page"
      vertical
      align="center"
      justify="center"
      gap={32}
      style={{
        flex: 1,
        minHeight: 0,
        padding: '40px 20px 56px',
        overflow: 'auto',
        position: 'relative',
      }}
    >
      <div className="entry-geek-grid" />
      <div className="entry-geek-orb entry-geek-orb-left" />
      <div className="entry-geek-orb entry-geek-orb-right" />
      <Flex vertical align="center" gap={8} style={{ maxWidth: 520 }}>
        <Space size={8} wrap>
          <Tag color="processing" style={{ borderRadius: 999 }}>
            vNext AI Experience
          </Tag>
          <Tag color="purple" style={{ borderRadius: 999 }}>
            Live Streaming
          </Tag>
        </Space>
        <ThunderboltOutlined style={{ fontSize: 42, color: '#7dd3fc' }} />
        <Title
          level={2}
          style={{
            margin: 0,
            textAlign: 'center',
            color: '#fff',
            textShadow: '0 0 24px rgba(56, 189, 248, 0.45)',
          }}
        >
          Prodigal AI Command Deck
        </Title>
        <Text
          style={{
            textAlign: 'center',
            fontSize: 15,
            color: 'rgba(226, 232, 240, 0.9)',
          }}
        >
          进入你的智能中控台，解锁实时流式对话、工具编排与任务级自动化。
        </Text>
      </Flex>

      <Flex
        gap={24}
        wrap="wrap"
        justify="center"
        style={{ maxWidth: 940, width: '100%', zIndex: 1 }}
      >
        <Card
          className="entry-geek-card entry-geek-card-blue"
          hoverable
          style={{
            flex: '1 1 300px',
            maxWidth: 400,
            borderRadius: 16,
          }}
          styles={{ body: cardBodyStyle }}
        >
          <Flex align="center" gap={12} style={{ marginBottom: 12 }}>
            <CompassOutlined
              style={{ fontSize: 28, color: '#7dd3fc' }}
            />
            <Title level={4} style={{ margin: 0, color: '#f8fafc' }}>
              AI 旅游助手
            </Title>
          </Flex>
          <Paragraph style={{ flex: 1, color: 'rgba(226, 232, 240, 0.86)' }}>
            城市路线、天气洞察、景点速查一键联动，像科幻 HUD 一样快速给你可执行出行方案。
          </Paragraph>
          <Flex gap={8} style={{ marginBottom: 14 }} wrap>
            <Tag color="cyan">流式回答</Tag>
            <Tag color="blue">旅行决策</Tag>
            <Tag color="geekblue">即时建议</Tag>
          </Flex>
          <Button
            type="primary"
            size="large"
            block
            className="entry-geek-btn entry-geek-btn-blue"
            icon={<RobotOutlined />}
            onClick={() => go('travel')}
          >
            进入 AI 旅游助手
          </Button>
        </Card>

        <Card
          className="entry-geek-card entry-geek-card-purple"
          hoverable
          style={{
            flex: '1 1 300px',
            maxWidth: 400,
            borderRadius: 16,
          }}
          styles={{ body: cardBodyStyle }}
        >
          <Flex align="center" gap={12} style={{ marginBottom: 12 }}>
            <RocketOutlined style={{ fontSize: 28, color: '#c4b5fd' }} />
            <Title level={4} style={{ margin: 0, color: '#f8fafc' }}>
              超级智能体
            </Title>
          </Flex>
          <Paragraph style={{ flex: 1, color: 'rgba(226, 232, 240, 0.86)' }}>
            多步推理 + 工具调度 + 思考过程可视化，适合复杂任务的重火力自动执行模式。
          </Paragraph>
          <Flex gap={8} style={{ marginBottom: 14 }} wrap>
            <Tag color="purple">Agent Loop</Tag>
            <Tag color="magenta">多工具编排</Tag>
            <Tag color="volcano">高阶任务</Tag>
          </Flex>
          <Button
            size="large"
            block
            className="entry-geek-btn entry-geek-btn-purple"
            icon={<RocketOutlined />}
            onClick={() => go('manus')}
          >
            启动超级智能体
          </Button>
        </Card>
      </Flex>
      <Text style={{ color: 'rgba(148, 163, 184, 0.85)', fontSize: 12, zIndex: 1 }}>
        <FireOutlined /> 未登录也可预览模式；发送消息前需要登录授权。
      </Text>
    </Flex>
  );
};

export default EntryPage;
