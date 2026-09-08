import React from 'react';
import { Navigate } from 'react-router-dom';
import { reportClientError } from '../../../lib/clientErrorReporter';

type Props = { children: React.ReactNode; fallbackHref?: string };
type State = { hasError: boolean };

export class WalkPhotoErrorBoundary extends React.Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    try {
      reportClientError(error, {
        componentStack: info.componentStack ?? null,
        extra: { context: 'WalkPhotoGallery' },
      });
    } catch {
      // reporter must never throw from boundary
    }
  }

  render() {
    if (this.state.hasError) {
      return <Navigate to={this.props.fallbackHref ?? '/walk-photos'} replace />;
    }
    return this.props.children;
  }
}

export default WalkPhotoErrorBoundary;
