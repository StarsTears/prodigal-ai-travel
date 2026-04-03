import { QuestionCircleOutlined } from '@ant-design/icons';
import { Alert, Button, Divider, Flex, Typography } from 'antd';
import React from 'react';

export interface ToolPanelProps {
  onQuickPrompt: (text: string) => void;
}

const { Title, Text } = Typography;

const SAMPLES: { label: string; prompt: string }[] = [
  {
    label: '查一下北京今天天气怎么样？',
    prompt: '查一下北京今天天气怎么样？',
  },
  {
    label: '帮我规划贵州五天行程，想去黄果树和西江苗寨',
    prompt: '帮我规划贵州五天行程，想去黄果树和西江苗寨',
  },
];

export const ToolPanel: React.FC<ToolPanelProps> = ({ onQuickPrompt }) => {
  return (
    <Flex vertical gap="large">
      <Alert
        type="info"
        showIcon
        icon={<QuestionCircleOutlined />}
        message="对话即工具"
        description={
          <Text type="secondary">
            天气、景点检索与行程规划由后端助手结合工具调用完成。请点击下方示例问题，将内容发送到对话区。
          </Text>
        }
      />
      <Divider orientation="left">示例问题</Divider>
      <Title level={5}>一键发起</Title>
      <Flex vertical gap="middle">
        {SAMPLES.map((s) => (
          <Button
            key={s.prompt}
            block
            type="default"
            onClick={() => onQuickPrompt(s.prompt)}
          >
            {s.label}
          </Button>
        ))}
      </Flex>
    </Flex>
  );
};
