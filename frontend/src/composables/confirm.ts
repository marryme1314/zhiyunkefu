import { reactive } from 'vue'

export type ConfirmOptions = {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

type ConfirmState = {
  open: boolean
  title: string
  message: string
  confirmText: string
  cancelText: string
  danger: boolean
  resolve: ((ok: boolean) => void) | null
}

const state = reactive<ConfirmState>({
  open: false,
  title: '请确认',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: true,
  resolve: null
})

export function useConfirmState() {
  return state
}

export function askConfirm(options: ConfirmOptions | string): Promise<boolean> {
  const opts = typeof options === 'string' ? { message: options } : options
  if (state.open && state.resolve) {
    state.resolve(false)
  }
  state.title = opts.title || '请确认'
  state.message = opts.message
  state.confirmText = opts.confirmText || '确定'
  state.cancelText = opts.cancelText || '取消'
  state.danger = opts.danger !== false
  state.open = true
  return new Promise((resolve) => {
    state.resolve = resolve
  })
}

export function finishConfirm(ok: boolean) {
  const resolve = state.resolve
  state.open = false
  state.resolve = null
  resolve?.(ok)
}
