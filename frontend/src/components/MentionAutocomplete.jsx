import { useState, useEffect, useRef } from 'react'

export default function MentionAutocomplete({ query, agents, onSelect, position }) {
  const [selectedIndex, setSelectedIndex] = useState(0)
  const listRef = useRef(null)

  const filteredAgents = agents.filter(agent =>
    agent.name.toLowerCase().includes(query.toLowerCase())
  )

  useEffect(() => {
    setSelectedIndex(0)
  }, [query])

  useEffect(() => {
    if (listRef.current) {
      const selectedEl = listRef.current.children[selectedIndex]
      if (selectedEl) {
        selectedEl.scrollIntoView({ block: 'nearest' })
      }
    }
  }, [selectedIndex])

  if (filteredAgents.length === 0) return null

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex(prev => (prev + 1) % filteredAgents.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex(prev => (prev - 1 + filteredAgents.length) % filteredAgents.length)
    } else if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault()
      onSelect(filteredAgents[selectedIndex])
    } else if (e.key === 'Escape') {
      e.preventDefault()
      onSelect(null)
    }
  }

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown, true)
    return () => document.removeEventListener('keydown', handleKeyDown, true)
  }, [filteredAgents, selectedIndex])

  return (
    <div
      ref={listRef}
      className="absolute z-50 bg-gray-800 border border-gray-600 rounded-lg shadow-xl overflow-hidden"
      style={{ bottom: position.bottom + 8, left: position.left }}
    >
      {filteredAgents.slice(0, 5).map((agent, idx) => (
        <div
          key={agent.id}
          onClick={() => onSelect(agent)}
          className={`px-4 py-2 cursor-pointer flex items-center gap-3 ${
            idx === selectedIndex ? 'bg-primary-600' : 'hover:bg-gray-700'
          }`}
        >
          <div className="w-8 h-8 rounded-full bg-primary-600 flex items-center justify-center text-white font-bold text-sm">
            {agent.name?.charAt(0).toUpperCase()}
          </div>
          <div>
            <div className="text-white text-sm font-medium">{agent.name}</div>
            <div className="text-gray-400 text-xs truncate max-w-[200px]">{agent.description}</div>
          </div>
        </div>
      ))}
    </div>
  )
}
