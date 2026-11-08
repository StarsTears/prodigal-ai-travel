import {
  BookOutlined,
  CloudOutlined,
  CompassOutlined,
  ExperimentOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import React from 'react';
import type { StreamChatKind } from '@/pages/chat/hooks/useStreaming';
import type { TravelSearchMode } from '@/types';

export interface QuickPrompt {
  label: string;
  prompt: string;
  icon: React.ReactNode;
}

const TRAVEL_QUICK_PROMPTS: QuickPrompt[] = [
  {
    label: '行程规划',
    prompt: '帮我规划贵州五天行程，想去黄果树和西江苗寨',
    icon: <CompassOutlined />,
  },
  {
    label: '天气与穿搭',
    prompt: '查一下杭州今天天气怎么样，适合穿什么、需要带伞吗？',
    icon: <CloudOutlined />,
  },
  {
    label: '知识库问答',
    prompt: '三山五岳分别指哪些山？各自有什么特色？',
    icon: <BookOutlined />,
  },
  {
    label: '景点速查',
    prompt: '推荐几个适合看云海的景点，并说明理由',
    icon: <ExperimentOutlined />,
  },
];

const MANUS_QUICK_PROMPTS: QuickPrompt[] = [
  {
    label: '复杂行程',
    prompt: '帮我规划一次 3 天的成都周边自驾，包含天气、路线、住宿预算和备选方案',
    icon: <CompassOutlined />,
  },
  {
    label: '信息对比',
    prompt: '对比杭州和苏州本周末的天气与热门景点，给出取舍建议',
    icon: <GlobalOutlined />,
  },
  {
    label: '多步任务',
    prompt: '先确认我当前所在城市，再给出今天适合户外活动的周边推荐与理由',
    icon: <RocketOutlined />,
  },
];

export function quickPromptsFor(mode: StreamChatKind): QuickPrompt[] {
  return mode === 'manus' ? MANUS_QUICK_PROMPTS : TRAVEL_QUICK_PROMPTS;
}

export interface ToolPanelProps {
  onQuickPrompt: (text: string) => void;
  mode?: StreamChatKind;
  searchMode?: TravelSearchMode;
  /** 空态时中央已有示例问题，右侧改为展示操作提示，避免重复 */
  showQuickPrompts?: boolean;
}

/** 右侧面板：当前模式的数据来源说明 + 示例问题 / 操作提示。 */
export const ToolPanel: React.FC<ToolPanelProps> = ({
  onQuickPrompt,
  mode = 'travel',
  searchMode = 'web',
  showQuickPrompts = true,
}) => {
  const sourceLabel =
    mode === 'manus'
      ? '超级智能体 · 自主调用工具'
      : searchMode === 'rag'
        ? '知识库 RAG · 严格离线'
        : '联网工具 · 实时信息';

  const sourceText =
    mode === 'manus'
      ? '按 ReAct 循环自主推理并调用工具，思考与执行过程可逐条展开，最终结论单独呈现。'
      : searchMode === 'rag'
        ? '只读取已入库文档，不联网、不调用外部工具；资料不足时会直接说明，不会用通用知识补写。'
        : '可调用天气、搜索等工具获取实时信息，适合查天气、找近期资讯。';

  return (
    <div className="tool-panel">
      <div>
        <h3 className="tool-panel__title">
          {showQuickPrompts ? '快速开始' : '使用说明'}
        </h3>
        <p className="tool-panel__desc">
          {showQuickPrompts
            ? '点选一个问题即可发起对话，也可以直接在下方输入框描述你的需求。'
            : '这里说明当前模式的数据来源与常用操作；中央的示例问题可直接点击发起对话。'}
        </p>
      </div>

      <div className="tool-panel__note">
        <div className="tool-panel__note-title">
          <InfoCircleOutlined />
          {sourceLabel}
        </div>
        <p className="tool-panel__note-text">{sourceText}</p>
      </div>

      {showQuickPrompts ? (
        <>
          <div className="tool-panel__section-label">示例问题</div>
          <div className="quick-prompt-grid" style={{ gridTemplateColumns: '1fr' }}>
            {quickPromptsFor(mode).map((item) => (
              <button
                key={item.prompt}
                type="button"
                className="quick-prompt"
                onClick={() => onQuickPrompt(item.prompt)}
              >
                <span className="quick-prompt__label">
                  {item.icon}　{item.label}
                </span>
                <span className="quick-prompt__text">{item.prompt}</span>
              </button>
            ))}
          </div>
        </>
      ) : (
        <>
          <div className="tool-panel__section-label">常用操作</div>
          <ul className="tool-panel__tips">
            <li>点击中央示例问题，或直接输入需求发送</li>
            <li>Enter 发送，Shift + Enter 换行</li>
            <li>生成中可点「停止生成」中断，回看时可点「回到最新」</li>
            <li>切到「知识库 RAG」后只依据入库文档，资料不足会直接说明</li>
            <li>左侧可新建、搜索、删除历史对话</li>
          </ul>
        </>
      )}
    </div>
  );
};
