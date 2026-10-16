import { SendOutlined } from '@ant-design/icons';
import { Button, Input, theme } from 'antd';
import React, { useCallback, useState } from 'react';
import type { TravelSearchMode } from '@/types';

export interface MessageInputProps {
  disabled?: boolean;
  onSend: (text: string) => void | Promise<void>;
  placeholder?: string;
  /** 是否显示「联网搜索 / RAG 问答」切换（仅旅游助手显示） */
  showSearchModeSwitch?: boolean;
  searchMode?: TravelSearchMode;
  onSearchModeChange?: (mode: TravelSearchMode) => void;
}

const { TextArea } = Input;

const MODE_OPTIONS: { value: TravelSearchMode; label: string }[] = [
  { value: 'web', label: '联网搜索' },
  { value: 'rag', label: 'RAG 问答' },
];

export const MessageInput: React.FC<MessageInputProps> = ({
  disabled,
  onSend,
  placeholder = '输入问题，Enter 发送；Shift + Enter 换行',
  showSearchModeSwitch = false,
  searchMode = 'web',
  onSearchModeChange,
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
            padding: '6px 8px 4px',
            fontSize: token.fontSize,
            lineHeight: 1.5,
            background: 'transparent',
            color: 'rgba(241, 245, 249, 0.95)',
          },
        }}
      />
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: '0 6px 6px 4px',
        }}
      >
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {showSearchModeSwitch
            ? MODE_OPTIONS.map((opt) => {
                const active = searchMode === opt.value;
                return (
                  <Button
                    key={opt.value}
                    size="small"
                    type={active ? 'primary' : 'default'}
                    onClick={() => onSearchModeChange?.(opt.value)}
                    style={{
                      borderRadius: 999,
                      ...(active
                        ? {}
                        : {
                            color: 'rgba(203, 213, 225, 0.95)',
                            background: 'rgba(15, 23, 42, 0.9)',
                            borderColor: 'rgba(125, 211, 252, 0.46)',
                          }),
                    }}
                  >
                    {opt.label}
                  </Button>
                );
              })
            : null}
        </div>
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
