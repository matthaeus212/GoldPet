import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from '../../context/AuthContext'
import RequireAuth from '../auth/RequireAuth'

function renderWithRouter(initialEntry: string, authenticated: boolean) {
  if (authenticated) {
    localStorage.setItem('admin_token', 'test-token')
    localStorage.setItem('admin_user', JSON.stringify({ id: 1, email: 'admin@example.com', name: '관리자', role: 'SUPER_ADMIN', isTwoFactorEnabled: false }))
  }

  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route
            path="/dashboard"
            element={
              <RequireAuth>
                <div>Dashboard</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  )
}

describe('RequireAuth', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('renders children when authenticated', () => {
    renderWithRouter('/dashboard', true)

    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument()
  })

  it('redirects to /login when not authenticated', () => {
    renderWithRouter('/dashboard', false)

    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument()
  })
})
