import { useState, useEffect } from 'react'

// Generate consistent color from string
function stringToColor(str) {
  if (!str) return '#6366f1'
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  const colors = [
    '#6366f1', // indigo
    '#8b5cf6', // violet
    '#ec4899', // pink
    '#f43f5e', // rose
    '#f97316', // orange
    '#eab308', // yellow
    '#22c55e', // green
    '#14b8a6', // teal
    '#06b6d4', // cyan
    '#3b82f6', // blue
  ]
  return colors[Math.abs(hash) % colors.length]
}

// Get initials from name
function getInitials(name) {
  if (!name) return '?'
  const parts = name.split(' ').filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
}

// Agent avatar component
function AgentAvatar({ agent, size = 'md', showStatus = true, isActive = false }) {
  const sizeClasses = {
    sm: 'w-6 h-6 text-xs',
    md: 'w-8 h-8 text-sm',
    lg: 'w-10 h-10 text-base',
    xl: 'w-12 h-12 text-lg'
  }

  const bgColor = stringToColor(agent?.name)

  return (
    <div className="relative">
      <div
        className={`${sizeClasses[size]} rounded-full flex items-center justify-center font-bold text-white transition-all`}
        style={{ backgroundColor: bgColor }}
      >
        {getInitials(agent?.name)}
      </div>
      {showStatus && (
        <div className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-gray-900 ${
          isActive ? 'bg-blue-500 animate-pulse' : 'bg-gray-500'
        }`} />
      )}
    </div>
  )
}

// Agent status card
function AgentStatusCard({ agent, isActive, isCompleted, taskContent }) {
  const status = isActive ? 'active' : isCompleted ? 'completed' : 'idle'

  const statusConfig = {
    active: {
      bg: 'bg-blue-500/20 border-blue-500/50',
      text: 'text-blue-400',
      label: '执行中',
      icon: (
        <svg className="w-4 h-4 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
      )
    },
    completed: {
      bg: 'bg-green-500/20 border-green-500/50',
      text: 'text-green-400',
      label: '已完成',
      icon: (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
        </svg>
      )
    },
    idle: {
      bg: 'bg-gray-700/50 border-gray-600/50',
      text: 'text-gray-400',
      label: '等待中',
      icon: (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      )
    }
  }

  const config = statusConfig[status]

  return (
    <div className={`p-3 rounded-lg border ${config.bg} transition-all`}>
      <div className="flex items-center gap-3">
        <AgentAvatar agent={agent} size="md" showStatus={true} isActive={isActive} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-white text-sm truncate">{agent?.name}</span>
            <span className={`flex items-center gap-1 text-xs ${config.text}`}>
              {config.icon}
              {config.label}
            </span>
          </div>
          {agent?.description && (
            <p className="text-xs text-gray-500 truncate mt-0.5">{agent.description}</p>
          )}
          {isActive && taskContent && (
            <p className="text-xs text-gray-400 mt-1 truncate">
              任务: {taskContent}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

export default function AgentStatusSidebar({ agents, activeAgents, completedAgentIds }) {
  const [expandedId, setExpandedId] = useState(null)

  if (!agents || agents.length === 0) {
    return (
      <div className="w-72 bg-gray-800/50 border-l border-gray-700 p-4">
        <h3 className="text-sm font-medium text-gray-400 mb-3">Agent 状态</h3>
        <p className="text-xs text-gray-500">无可用 Agent</p>
      </div>
    )
  }

  // activeAgents is a Map: agentId -> content
  const activeAgentIds = activeAgents ? Array.from(activeAgents.keys()) : []
  const completedAgents = agents.filter(a => completedAgentIds?.includes(a.id))
  const idleAgents = agents.filter(a =>
    !activeAgentIds.includes(a.id) && !completedAgentIds?.includes(a.id)
  )

  return (
    <div className="w-72 bg-gray-800/50 border-l border-gray-700 flex flex-col h-full">
      {/* Header */}
      <div className="p-4 border-b border-gray-700">
        <h3 className="text-sm font-medium text-gray-400 flex items-center gap-2">
          <span>🎭</span>
          <span>Agent 状态</span>
          <span className="ml-auto text-xs bg-gray-700 px-2 py-0.5 rounded">
            {agents.length}
          </span>
        </h3>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {/* Active agents */}
        {activeAgentIds.length > 0 && (
          <div>
            <p className="text-xs text-gray-500 uppercase tracking-wide mb-2">执行中 ({activeAgentIds.length})</p>
            <div className="space-y-2">
              {activeAgentIds.map(agentId => {
                const agent = agents.find(a => a.id === agentId)
                const content = activeAgents.get(agentId)
                return (
                  <AgentStatusCard
                    key={agentId}
                    agent={agent}
                    isActive={true}
                    isCompleted={false}
                    taskContent={content}
                  />
                )
              })}
            </div>
          </div>
        )}

        {/* Idle agents */}
        {idleAgents.length > 0 && (
          <div>
            <p className="text-xs text-gray-500 uppercase tracking-wide mb-2">等待中</p>
            <div className="space-y-2">
              {idleAgents.map(agent => (
                <AgentStatusCard
                  key={agent.id}
                  agent={agent}
                  isActive={false}
                  isCompleted={false}
                />
              ))}
            </div>
          </div>
        )}

        {/* Completed agents */}
        {completedAgents.length > 0 && (
          <div>
            <p className="text-xs text-gray-500 uppercase tracking-wide mb-2">已完成</p>
            <div className="space-y-2">
              {completedAgents.map(agent => (
                <AgentStatusCard
                  key={agent.id}
                  agent={agent}
                  isActive={false}
                  isCompleted={true}
                />
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Footer with quick stats */}
      <div className="p-3 border-t border-gray-700">
        <div className="flex justify-between text-xs text-gray-500">
          <span>活跃: {activeAgentIds.length}</span>
          <span>完成: {completedAgents.length}</span>
          <span>等待: {idleAgents.length}</span>
        </div>
      </div>
    </div>
  )
}

export { AgentAvatar, getInitials, stringToColor }
