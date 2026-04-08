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
  const [focused, setFocused] = useState(false);
  const hasValue = value.trim().length > 0;
  const isHighlight = focused || hasValue;

  const composerStyle: React.CSSProperties = {
    position: 'relative',
    borderRadius: 12,
    border: isHighlight
      ? '1px solid rgba(56, 189, 248, 0.62)'
      : '1px solid rgba(125, 211, 252, 0.42)',
    boxShadow: isHighlight
      ? '0 0 0 1px rgba(56, 189, 248, 0.2), 0 14px 28px rgba(2, 6, 23, 0.44)'
      : '0 10px 22px rgba(2, 6, 23, 0.38)',
    padding: '4px 4px 4px 8px',
    background: isHighlight
      ? 'linear-gradient(180deg, rgba(15, 23, 42, 0.86), rgba(2, 6, 23, 0.74))'
      : 'linear-gradient(180deg, rgba(15, 23, 42, 0.78), rgba(2, 6, 23, 0.62))',
    backdropFilter: 'blur(10px)',
    transition: 'all 180ms ease',
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
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
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
            background: 'transparent',
            color: 'rgba(241, 245, 249, 0.95)',
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
          type={hasValue ? 'primary' : 'default'}
          size="middle"
          icon={<SendOutlined />}
          loading={disabled}
          onClick={() => void submit()}
          disabled={disabled}
          style={{
            color: hasValue ? undefined : 'rgba(148, 163, 184, 0.96)',
            borderColor: hasValue ? undefined : 'rgba(125, 211, 252, 0.46)',
            background: hasValue ? undefined : 'rgba(15, 23, 42, 0.9)',
            boxShadow: hasValue
              ? '0 6px 18px rgba(14, 165, 233, 0.35)'
              : '0 2px 10px rgba(2, 6, 23, 0.3)',
            opacity: disabled ? 0.55 : 1,
          }}
        >
          发送
        </Button>
      </div>
    </div>
  );
};
