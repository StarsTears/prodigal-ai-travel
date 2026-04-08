import {QuestionCircleOutlined} from '@ant-design/icons';
import {Alert, Button, Divider, Flex, Typography} from 'antd';
import React from 'react';

export interface ToolPanelProps {
    onQuickPrompt: (text: string) => void;
}

const {Title, Text} = Typography;

const SAMPLES: { label: string; prompt: string }[] = [
    {
        label: '给我几张云海风景照',
        prompt: '给我几张云海风景照',
    },
    {
        label: '查一下北京今天天气怎么样？',
        prompt: '查一下北京今天天气怎么样？',
    },
    {
        label: '帮我规划贵州五天行程，想去黄果树和西江苗寨',
        prompt: '帮我规划贵州五天行程，想去黄果树和西江苗寨',
    },
];

export const ToolPanel: React.FC<ToolPanelProps> = ({onQuickPrompt}) => {
    return (
        <Flex vertical gap="large" style={{ color: 'rgba(226, 232, 240, 0.92)' }}>
            <Alert
                type="info"
                showIcon
                icon={<QuestionCircleOutlined/>}
                message="对话即工具"
                style={{
                    background: 'rgba(2, 132, 199, 0.14)',
                    border: '1px solid rgba(125, 211, 252, 0.25)',
                }}
                description={
                    <Text style={{ color: 'rgba(203, 213, 225, 0.9)' }}>
                        天气、景点检索与行程规划由后端助手结合工具调用完成。请点击下方示例问题，将内容发送到对话区。
                    </Text>
                }
            />
            <Divider orientation="left" style={{ borderColor: 'rgba(148, 163, 184, 0.35)' }}>
                <span style={{ color: 'rgba(203, 213, 225, 0.95)' }}>示例问题</span>
            </Divider>
            <Title level={5} style={{ margin: 0, color: 'rgba(226, 232, 240, 0.96)' }}>一键发起</Title>
            <Flex vertical gap="middle">
                {SAMPLES.map((s) => (
                    <Button
                        key={s.prompt}
                        block
                        type="default"
                        style={{
                            color: 'rgba(226, 232, 240, 0.95)',
                            background: 'rgba(15, 23, 42, 0.55)',
                            borderColor: 'rgba(148, 163, 184, 0.35)',
                        }}
                        onClick={() => onQuickPrompt(s.prompt)}
                    >
                        {s.label}
                    </Button>
                ))}
            </Flex>
        </Flex>
    );
};
