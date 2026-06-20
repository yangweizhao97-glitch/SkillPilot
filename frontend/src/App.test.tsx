import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import App from './App'

describe('authentication screen', () => {
  beforeEach(() => localStorage.clear())

  it('shows login first and switches to registration', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: '欢迎回来' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '还没有账号？立即注册' }))
    expect(screen.getByRole('heading', { name: '创建工作区' })).toBeInTheDocument()
    expect(screen.getByLabelText('昵称')).toBeInTheDocument()
  })
})

