import {
  BookOutlined,
  EnvironmentOutlined,
  GlobalOutlined,
  PauseOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { Button, Input } from 'antd';
import React, { useCallback, useState } from 'react';
import type { TravelSearchMode } from '@/types';
import { GEO_STATUS_TEXT, type GeoStatus } from '@/utils/preferences';

const { TextArea } = Input;

export interface MessageInputProps {
  /** 正在生成中：保留草稿可编辑，但禁止再次发送，改为「停止生成」 */
  disabled?: boolean;
  onSend: (text: string) => void | Promise<void>;
  onStop?: () => void;
  placeholder?: string;
  /** 仅旅游助手显示「联网工具 / 知识库 RAG」切换 */
  showSearchModeSwitch?: boolean;
  searchMode?: TravelSearchMode;
  onSearchModeChange?: (mode: TravelSearchMode) => void;
  /** 未登录时在输入区直接给出登录入口，而不是点击发送后才提示 */
  loginRequired?: boolean;
  onOpenLogin?: () => void;
  /** 当前定位状态，仅旅游助手展示 */
  geoStatus?: GeoStatus;
}

const GEO_SHORT_LABEL: Partial<Record<GeoStatus, string>> = {
  granted: '已定位',
  pending: '定位中…',
  denied: 'IP 定位',
  unsupported: 'IP 定位',
  timeout: 'IP 定位',
  error: 'IP 定位',
  off: '定位已关',
};

const MODE_OPTIONS: {
  value: TravelSearchMode;
  label: string;
  icon: React.ReactNode;
  hint: string;
}[] = [
  {
    value: 'web',
    label: '联网工具',
    icon: <GlobalOutlined />,
    hint: '可查天气 · 实时搜索',
  },
  {
    value: 'rag',
    label: '知识库 RAG',
    icon: <BookOutlined />,
    hint: '仅依据入库文档 · 不联网',
  },
];

export const MessageInput: React.FC<MessageInputProps> = ({
  disabled = false,
  onSend,
  onStop,
  placeholder = '描述你的旅行问题，或点一个示例问题开始…',
  showSearchModeSwitch = false,
  searchMode = 'web',
  onSearchModeChange,
  loginRequired = false,
  onOpenLogin,
  geoStatus,
}) => {
  const [value, setValue] = useState('');
  const hasValue = value.trim().length > 0;
  const activeMode = MODE_OPTIONS.find((o) => o.value === searchMode) ?? MODE_OPTIONS[0];
  const sourceHint = showSearchModeSwitch
    ? activeMode.hint
    : '思考过程可展开，最终结论单独呈现';
  const geoLabel = geoStatus ? GEO_SHORT_LABEL[geoStatus] : undefined;

  const submit = useCallback(async () => {
    const t = value.trim();
    if (!t || disabled) return;
    setValue('');
    await onSend(t);
  }, [disabled, onSend, value]);

  return (
    <div className="composer">
      <div className="composer__top">
        {showSearchModeSwitch ? (
          <div className="source-switch" role="group" aria-label="回答模式">
            {MODE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                aria-pressed={searchMode === opt.value}
                className={`source-switch__item ${
                  searchMode === opt.value ? 'source-switch__item--active' : ''
                }`}
                onClick={() => onSearchModeChange?.(opt.value)}
              >
                {opt.icon}
                {opt.label}
              </button>
            ))}
          </div>
        ) : (
          <span className="ui-chip ui-chip--violet">
            <span className="ui-chip__dot" />
            多步推理 · 工具编排
          </span>
        )}
        <div className="composer__meta">
          <span className="composer__source">{sourceHint}</span>
          {geoLabel && geoStatus ? (
            <span
              className={`composer__geo ${
                geoStatus === 'granted' ? 'composer__geo--ok' : ''
              }`}
              title={GEO_STATUS_TEXT[geoStatus]}
            >
              <EnvironmentOutlined />
              {geoLabel}
            </span>
          ) : null}
        </div>
      </div>

      <TextArea
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault();
            void submit();
          }
        }}
        placeholder={placeholder}
        autoSize={{ minRows: 2, maxRows: 8 }}
        variant="borderless"
        styles={{ textarea: { padding: '6px 4px', background: 'transparent' } }}
      />

      <div className="composer__foot">
        <span className="composer__hint">
          {disabled ? (
            '正在生成，可点击「停止生成」中断'
          ) : (
            <>
              Enter 发送
              <span className="composer__hint-extra"> · Shift + Enter 换行</span>
            </>
          )}
          {hasValue ? <span className="composer__count">　·　{value.length} 字</span> : null}
          {loginRequired ? (
            <>
              　·　
              <button
                type="button"
                className="chat-action"
                style={{ opacity: 1, color: '#93c5fd' }}
                onClick={() => onOpenLogin?.()}
              >
                登录后发送
              </button>
            </>
          ) : null}
        </span>

        {disabled ? (
          <Button
            danger
            icon={<PauseOutlined />}
            onClick={() => onStop?.()}
            disabled={!onStop}
          >
            停止生成
          </Button>
        ) : (
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={() => void submit()}
            disabled={!hasValue}
          >
            发送
          </Button>
        )}
      </div>
    </div>
  );
};
