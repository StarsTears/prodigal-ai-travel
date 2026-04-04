import { SendOutlined } from '@ant-design/icons';
import { Button, Input, theme } from 'antd';
import React, { useCallback, useState } from 'react';

export interface MessageInputProps {
  disabled?: boolean;
  onSend: (text: string) => void | Promise<void>;
  placeholder?: string;
}

const { TextArea } = Input;

/** 为右下角发送按钮预留空间，避免文字与按钮重叠 */
const TEXTAREA_PAD_RIGHT = 92;
const TEXTAREA_PAD_BOTTOM = 40;

export const MessageInput: React.FC<MessageInputProps> = ({
  disabled,
  onSend,
  placeholder = '输入问题，Enter 发送；Shift + Enter 换行',
}) => {
  const { token } = theme.useToken();
  const [value, setValue] = useState('');

  const composerStyle: React.CSSProperties = {
    position: 'relative',
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
            padding: `6px ${TEXTAREA_PAD_RIGHT}px ${TEXTAREA_PAD_BOTTOM}px 4px`,
            fontSize: token.fontSize,
            lineHeight: 1.5,
          },
        }}
      />
      <div
        style={{
          position: 'absolute',
          right: 8,
          bottom: 8,
          zIndex: 1,
        }}
      >
        <Button
          type="primary"
          size="middle"
          icon={<SendOutlined />}
          loading={disabled}
          onClick={() => void submit()}
          disabled={disabled || !value.trim()}
        >
          发送
        </Button>
      </div>
    </div>
  );
};
