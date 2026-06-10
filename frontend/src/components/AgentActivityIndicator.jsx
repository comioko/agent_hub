import { useState, useEffect } from 'react'
import { AgentAvatar, getInitials, stringToColor } from './AgentStatusSidebar'

export default function AgentActivityIndicator({ agents, activeAgentId, activeAgentContent, completedAgents = [] }) {
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    if (activeAgentId && activeAgentContent) {
      const timer = setTimeout(() => {
        setProgress(prev => {
          const newProgress = prev + Math.random() * 15
          return newProgress > 90 ? 90 : newProgress
        })
      }, 500)
      return () => clearTimeout(timer)
    } else {
      setProgress(0)
    }
  }, [activeAgentContent, activeAgentId])

  if (!agents || agents.length === 0) return null

  const activeAgent = agents.find(a => a.id === activeAgentId)
  const idleAgents = agents.filter(a =>
    a.id !== activeAgentId && !completedAgents.includes(a.id)
  )

  return (
    <div className="mb-2 px-4 py-2 bg-gray-800/50 border-b border-gray-700">
      {/* Compact Header */}
      <div className="flex items-center gap-2 mb-1.5">
        <span className="text-xs">🎭</span>
        <span className="text-xs font-medium text-gray-400">Agent 状态</span>
        {activeAgentId && (
          <span className="px-1.5 py-0.5 bg-blue-500/20 text-blue-300 rounded text-xs animate-pulse">
            进行中
          </span>
        )}
      </div>

      {/* Active agent - compact */}
      {activeAgent && (
        <div className="flex items-center gap-2">
          <AgentAvatar agent={activeAgent} size="sm" showStatus={true} isActive={true} />
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1">
              <span className="text-xs font-medium text-white truncate">{activeAgent.name}</span>
              <span className="text-xs">{activeAgentContent ? '✍️' : '🤔'}</span>
            </div>
            <div className="h-0.5 bg-gray-700 rounded-full mt-0.5 overflow-hidden">
              <div
                className="h-full bg-blue-500 rounded-full transition-all duration-300"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
        </div>
      )}

      {/* Idle agents - compact horizontal */}
      {!activeAgent && idleAgents.length > 0 && (
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-gray-500">等待:</span>
          {idleAgents.slice(0, 4).map(agent => (
            <div
              key={agent.id}
              className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-gray-700/50 text-xs"
            >
              <AgentAvatar agent={agent} size="sm" showStatus={false} />
              <span className="text-gray-400">{agent.name}</span>
            </div>
          ))}
        </div>
      )}

      {/* Completed */}
      {completedAgents.length > 0 && (
        <div className="flex items-center gap-1 mt-1">
          <span className="text-xs text-green-400">✅</span>
          {completedAgents.map(id => {
            const agent = agents.find(a => a.id === id)
            return agent ? (
              <span key={id} className="text-xs text-green-400">{agent.name}</span>
            ) : null
          })}
        </div>
      )}
    </div>
  )
}
