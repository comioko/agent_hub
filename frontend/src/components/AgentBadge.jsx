export default function AgentBadge({ isSystem, ownerUsername }) {
  if (isSystem) {
    return (
      <span className="px-2 py-0.5 rounded text-xs bg-blue-500/20 text-blue-400">
        System
      </span>
    )
  }
  return (
    <span className="px-2 py-0.5 rounded text-xs bg-purple-500/20 text-purple-400" title={`Created by ${ownerUsername}`}>
      My Agent
    </span>
  )
}
