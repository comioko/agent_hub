import { useEffect, useRef, useCallback, useState } from 'react'

export function useSSE(onMessage) {
  const eventSourceRef = useRef(null)
  const reconnectTimeoutRef = useRef(null)
  const reconnectAttempts = useRef(0)
  const maxReconnectAttempts = 3
  const [isConnected, setIsConnected] = useState(false)
  const onMessageRef = useRef(onMessage)

  // Keep onMessage ref up to date
  useEffect(() => {
    onMessageRef.current = onMessage
  }, [onMessage])

  const connect = useCallback(() => {
    const token = localStorage.getItem('agenthub_token')
    if (!token) {
      console.warn('No token found for SSE connection')
      return Promise.resolve(false)
    }

    // If already connecting or connected, return existing connection status
    if (eventSourceRef.current?.readyState === 1) {
      return Promise.resolve(true)
    }

    // Close existing connection if any
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }

    return new Promise((resolve) => {
      const url = `/api/messages/subscribe?token=${encodeURIComponent(token)}`
      const eventSource = new EventSource(url)

      // Resolve promise once connected
      eventSource.onopen = () => {
        console.log('SSE connected')
        setIsConnected(true)
        reconnectAttempts.current = 0
        resolve(true)
      }

      eventSource.addEventListener('streaming', (event) => {
        try {
          const data = JSON.parse(event.data)
          if (data && onMessageRef.current) {
            onMessageRef.current({ type: 'streaming', data })
          }
        } catch (e) {
          console.error('Failed to parse streaming event:', e)
        }
      })

      eventSource.addEventListener('complete', (event) => {
        try {
          const data = JSON.parse(event.data)
          if (data && onMessageRef.current) {
            onMessageRef.current({ type: 'complete', data })
          }
        } catch (e) {
          console.error('Failed to parse complete event:', e)
        }
      })

      eventSource.addEventListener('tool_call', (event) => {
        try {
          const data = JSON.parse(event.data)
          if (data && onMessageRef.current) {
            onMessageRef.current({ type: 'tool_call', data })
          }
        } catch (e) {
          console.error('Failed to parse tool_call event:', e)
        }
      })

      eventSource.addEventListener('tool_result', (event) => {
        try {
          const data = JSON.parse(event.data)
          if (data && onMessageRef.current) {
            onMessageRef.current({ type: 'tool_result', data })
          }
        } catch (e) {
          console.error('Failed to parse tool_result event:', e)
        }
      })

      eventSource.onmessage = (event) => {
        try {
          if (event.data === 'Connected') {
            console.log('SSE connection confirmed')
            return
          }
          const data = JSON.parse(event.data)
          if (data && onMessageRef.current) {
            onMessageRef.current({ type: 'message', data })
          }
        } catch (e) {
          console.log('SSE message received:', event.data)
        }
      }

      eventSource.onerror = (error) => {
        console.error('SSE error:', error)
        eventSource.close()
        eventSourceRef.current = null
        setIsConnected(false)
        resolve(false)

        if (reconnectAttempts.current < maxReconnectAttempts) {
          const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 10000)
          console.log(`SSE reconnecting in ${delay}ms (attempt ${reconnectAttempts.current + 1})`)
          reconnectTimeoutRef.current = setTimeout(() => {
            reconnectAttempts.current++
            connect()
          }, delay)
        } else {
          console.error('SSE max reconnect attempts reached')
        }
      }

      eventSourceRef.current = eventSource
    })
  }, [])

  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
      reconnectTimeoutRef.current = null
    }
    reconnectAttempts.current = 0
    setIsConnected(false)
  }, [])

  useEffect(() => {
    return () => disconnect()
  }, [disconnect])

  return { connect, disconnect, isConnected }
}
