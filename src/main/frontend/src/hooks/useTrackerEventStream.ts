import { useEffect, useRef } from 'react'
import { useDispatch } from 'react-redux'
import { openTrackerEventSource } from '@/services/api'
import { processEvent, setSseStatus } from '@/store/slices/uploadSlice'
import type { ProcessTracker } from '@/store/slices/uploadSlice'

const MAX_RETRY_ATTEMPTS = 10
const MAX_RETRY_DELAY_MS = 15000

/**
 * Subscribes the app to the live processing-status SSE stream.
 *
 * Mounted once at the layout level so status updates keep flowing into Redux no
 * matter which page the user is on. On (re)connect the backend replays a
 * snapshot of every active tracker, which also restores the current status
 * after a full page reload.
 */
export const useTrackerEventStream = () => {
  const dispatch = useDispatch()
  const dispatchRef = useRef(dispatch)
  dispatchRef.current = dispatch

  useEffect(() => {
    let eventSource: EventSource | null = null
    let retryAttempts = 0
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined
    let disposed = false

    const connect = async () => {
      if (disposed) return

      let source: EventSource
      try {
        source = await openTrackerEventSource()
      } catch {
        dispatchRef.current(setSseStatus('error'))
        // The token request failed (e.g. session expired); retry on the same
        // backoff schedule so a refreshed session reconnects on its own.
        if (disposed || retryAttempts >= MAX_RETRY_ATTEMPTS) {
          return
        }
        const delay = Math.min(MAX_RETRY_DELAY_MS, 1000 * 2 ** retryAttempts)
        retryAttempts += 1
        reconnectTimer = setTimeout(connect, delay)
        return
      }
      eventSource = source
      dispatchRef.current(setSseStatus('connecting'))

      source.onopen = () => {
        retryAttempts = 0
        dispatchRef.current(setSseStatus('open'))
      }

      source.onmessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data) as ProcessTracker & { type?: string }
          dispatchRef.current(processEvent(data))
        } catch {
          // Ignore malformed / heartbeat-only events
        }
      }

      source.onerror = () => {
        source.close()
        eventSource = null
        dispatchRef.current(setSseStatus('error'))

        if (disposed || retryAttempts >= MAX_RETRY_ATTEMPTS) {
          return
        }
        // Controlled reconnect with exponential backoff. A fresh SSE token is
        // requested on every attempt via the axios refresh flow.
        const delay = Math.min(MAX_RETRY_DELAY_MS, 1000 * 2 ** retryAttempts)
        retryAttempts += 1
        reconnectTimer = setTimeout(connect, delay)
      }
    }

    connect()

    return () => {
      disposed = true
      if (reconnectTimer) clearTimeout(reconnectTimer)
      if (eventSource) eventSource.close()
    }
  }, [])
}
