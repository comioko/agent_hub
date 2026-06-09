import MessageItem from './MessageItem'

export default function MessageList({ messages, onPinMessage }) {
  if (messages.length === 0) {
    return (
      <div className="flex-1 overflow-y-auto p-4 text-gray-500 text-center">
        No messages yet. Start the conversation!
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-4">
      {messages.map((message) => (
        <div key={message.id} data-message-id={message.id}>
          <MessageItem message={message} onPinMessage={onPinMessage} />
        </div>
      ))}
    </div>
  )
}
