import {
  CompassOutlined,
  RocketOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Button, Card, Flex, Typography } from 'antd';
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
      vertical
      align="center"
      justify="center"
      gap={32}
      style={{
        flex: 1,
        minHeight: 0,
        padding: '32px 20px 48px',
        background:
          'linear-gradient(165deg, #f0f5ff 0%, #f5f6f8 45%, #fafafa 100%)',
        overflow: 'auto',
      }}
    >
      <Flex vertical align="center" gap={8} style={{ maxWidth: 520 }}>
        <ThunderboltOutlined style={{ fontSize: 40, color: '#1677ff' }} />
        <Title level={2} style={{ margin: 0, textAlign: 'center' }}>
          Prodigal AI
        </Title>
        <Text type="secondary" style={{ textAlign: 'center', fontSize: 15 }}>
          未登录也可先选择体验方式；发送消息前需要登录。
        </Text>
      </Flex>

      <Flex
        gap={24}
        wrap="wrap"
        justify="center"
        style={{ maxWidth: 880, width: '100%' }}
      >
        <Card
          hoverable
          style={{
            flex: '1 1 300px',
            maxWidth: 400,
            borderRadius: 12,
            borderColor: 'rgba(22, 119, 255, 0.25)',
          }}
          styles={{ body: cardBodyStyle }}
        >
          <Flex align="center" gap={12} style={{ marginBottom: 12 }}>
            <CompassOutlined
              style={{ fontSize: 28, color: '#1677ff' }}
            />
            <Title level={4} style={{ margin: 0 }}>
              AI 旅游助手
            </Title>
          </Flex>
          <Paragraph type="secondary" style={{ flex: 1 }}>
            国内行程、景点、天气与 MCP 工具结合，支持会话历史与流式回复，适合规划与咨询出行。
          </Paragraph>
          <Button
            type="primary"
            size="large"
            block
            icon={<CompassOutlined />}
            onClick={() => go('travel')}
          >
            进入旅游助手
          </Button>
        </Card>

        <Card
          hoverable
          style={{
            flex: '1 1 300px',
            maxWidth: 400,
            borderRadius: 12,
            borderColor: 'rgba(114, 46, 209, 0.28)',
          }}
          styles={{ body: cardBodyStyle }}
        >
          <Flex align="center" gap={12} style={{ marginBottom: 12 }}>
            <RocketOutlined style={{ fontSize: 28, color: '#722ed1' }} />
            <Title level={4} style={{ margin: 0 }}>
              超级智能体
            </Title>
          </Flex>
          <Paragraph type="secondary" style={{ flex: 1 }}>
            多步推理与工具调用，流式推送每一步「思考与执行」过程，适合复杂任务与自动化编排。
          </Paragraph>
          <Button
            size="large"
            block
            style={{
              background: 'linear-gradient(90deg, #722ed1 0%, #9254de 100%)',
              color: '#fff',
              border: 'none',
            }}
            icon={<RocketOutlined />}
            onClick={() => go('manus')}
          >
            进入超级智能体
          </Button>
        </Card>
      </Flex>
    </Flex>
  );
};

export default EntryPage;
