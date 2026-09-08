import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from '../../context/AuthContext'

function TestConsumer() {
  const { user, token, isAuthenticated, login, logout } = useAuth()
  return (
    <div>
      <span data-testid="auth-status">{isAuthenticated ? 'authenticated' : 'unauthenticated'}</span>
      <span data-testid="token">{token ?? 'none'}</span>
      <span data-testid="nickname">{user?.name ?? 'none'}</span>
      <button onClick={() => login('test-token', { id: 1, email: 'admin@example.com', name: '관리자', role: 'SUPER_ADMIN', isTwoFactorEnabled: false })}>
        Login
      </button>
      <button onClick={logout}>Logout</button>
    </div>
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('starts unauthenticated with no stored data', () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated')
    expect(screen.getByTestId('token')).toHaveTextContent('none')
    expect(screen.getByTestId('nickname')).toHaveTextContent('none')
  })

  it('restores session from localStorage', () => {
    localStorage.setItem('admin_token', 'stored-token')
    localStorage.setItem('admin_user', JSON.stringify({ id: 1, email: 'admin@example.com', name: '관리자', role: 'SUPER_ADMIN', isTwoFactorEnabled: false }))

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated')
    expect(screen.getByTestId('token')).toHaveTextContent('stored-token')
    expect(screen.getByTestId('nickname')).toHaveTextContent('관리자')
  })

  it('login sets authenticated state and persists to localStorage', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await user.click(screen.getByText('Login'))

    expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated')
    expect(screen.getByTestId('token')).toHaveTextContent('test-token')
    expect(screen.getByTestId('nickname')).toHaveTextContent('관리자')
    expect(localStorage.getItem('admin_token')).toBe('test-token')
  })

  it('logout clears state and removes from localStorage', async () => {
    const user = userEvent.setup()
    localStorage.setItem('admin_token', 'stored-token')
    localStorage.setItem('admin_user', JSON.stringify({ id: 1, email: 'admin@example.com', name: '관리자', role: 'SUPER_ADMIN', isTwoFactorEnabled: false }))

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await user.click(screen.getByText('Logout'))

    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated')
    expect(screen.getByTestId('token')).toHaveTextContent('none')
    expect(localStorage.getItem('admin_token')).toBeNull()
    expect(localStorage.getItem('admin_user')).toBeNull()
  })

  it('useAuth throws when used outside AuthProvider', () => {
    const originalError = console.error
    console.error = () => {}

    expect(() => render(<TestConsumer />)).toThrow('useAuth must be used within an AuthProvider')

    console.error = originalError
  })
})
