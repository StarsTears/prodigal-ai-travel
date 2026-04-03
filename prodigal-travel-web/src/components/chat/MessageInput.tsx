import { SendOutlined } from '@ant-design/icons';
import { Button, Col, Flex, Input, Row, theme } from 'antd';
import React, { useCallback, useState } from 'react';

export interface MessageInputProps {
  disabled?: boolean;
  onSend: (text: string) => void | Promise<void>;
  placeholder?: string;
}

const { TextArea } = Input;

export const MessageInput: React.FC<MessageInputProps> = ({
  disabled,
  onSend,
  placeholder = '输入问题，Enter 发送；Shift + Enter 换行',
}) => {
  const { token } = theme.useToken();
  const [value, setValue] = useState('');

  const composerStyle: React.CSSProperties = {
    borderRadius: 12,
    border: `1px solid ${token.colorBorderSecondary}`,
    boxShadow: '0 1px 2px rgba(0, 0, 0, 0.04)',
    padding: '4px 4px 4px 8px',
    background: token.colorBgContainer,
  };

  const submit = useCallback(async () => {
    const t = value.trim();
    if (!t || disabled) return;
    setValue('');
    await onSend(t);
  }, [disabled, onSend, value]);

  return (
    <Row gutter={[0, 12]}>
      <Col span={24}>
        <div style={composerStyle}>
          <TextArea
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                void submit();
              }
            }}
            placeholder={placeholder}
            disabled={disabled}
            autoSize={{ minRows: 2, maxRows: 8 }}
            variant="borderless"
            styles={{
              textarea: {
                padding: '6px 4px',
                fontSize: token.fontSize,
              },
            }}
          />
        </div>
      </Col>
      <Col span={24}>
        <Flex justify="end">
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={disabled}
            onClick={() => void submit()}
            disabled={disabled || !value.trim()}
          >
            发送
          </Button>
        </Flex>
      </Col>
    </Row>
  );
};
