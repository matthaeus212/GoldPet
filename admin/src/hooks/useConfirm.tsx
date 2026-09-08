import { useState, useCallback, useRef } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface ConfirmOptions {
  title?: string
  description?: string
  confirmText?: string
  cancelText?: string
  variant?: 'default' | 'destructive'
}

type Resolver = (value: boolean) => void

export function useConfirm() {
  const [open, setOpen] = useState(false)
  const [options, setOptions] = useState<ConfirmOptions>({})
  const resolverRef = useRef<Resolver | null>(null)

  const confirm = useCallback((opts: ConfirmOptions = {}): Promise<boolean> => {
    setOptions(opts)
    setOpen(true)
    return new Promise<boolean>((resolve) => {
      resolverRef.current = resolve
    })
  }, [])

  const handleResponse = useCallback((result: boolean) => {
    setOpen(false)
    resolverRef.current?.(result)
    resolverRef.current = null
  }, [])

  const ConfirmDialog = (
    <Dialog open={open} onOpenChange={(o) => { if (!o) handleResponse(false) }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{options.title ?? '확인'}</DialogTitle>
          {options.description && (
            <DialogDescription>{options.description}</DialogDescription>
          )}
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => handleResponse(false)}>
            {options.cancelText ?? '취소'}
          </Button>
          <Button
            variant={options.variant === 'destructive' ? 'destructive' : 'default'}
            onClick={() => handleResponse(true)}
          >
            {options.confirmText ?? '확인'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )

  return { confirm, ConfirmDialog }
}
