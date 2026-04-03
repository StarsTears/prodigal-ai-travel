import { Alert, Button, Flex } from 'antd';
import React, { Component, type ErrorInfo, type ReactNode } from 'react';

type Props = { children: ReactNode };

type State = { hasError: boolean; message: string };

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, message: '' };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error.message || '发生未知错误' };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return (
        <Flex vertical align="center" justify="center" gap="middle">
          <Alert
            type="error"
            showIcon
            message="页面出错"
            description={this.state.message}
          />
          <Button
            type="primary"
            onClick={() => this.setState({ hasError: false, message: '' })}
          >
            重试
          </Button>
        </Flex>
      );
    }
    return this.props.children;
  }
}
