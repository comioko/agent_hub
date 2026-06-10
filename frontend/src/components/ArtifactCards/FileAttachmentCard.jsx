import { useState } from 'react'

export default function FileAttachmentCard({ block }) {
  const [isDownloading, setIsDownloading] = useState(false)
  const [error, setError] = useState(null)

  const metadata = parseMetadata(block.metadata)
  const fileName = metadata.fileName || 'attachment'
  const fileSize = metadata.size || metadata.fileSize
  const mimeType = metadata.mimeType || metadata.contentType || 'application/octet-stream'

  const formatFileSize = (bytes) => {
    if (!bytes) return ''
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  const getFileIcon = () => {
    if (mimeType.includes('pdf')) return '📄'
    if (mimeType.includes('zip') || mimeType.includes('rar') || mimeType.includes('tar')) return '📦'
    if (mimeType.includes('image')) return '🖼️'
    if (mimeType.includes('video')) return '🎬'
    if (mimeType.includes('audio')) return '🎵'
    if (mimeType.includes('text')) return '📝'
    return '📎'
  }

  const getFileColor = () => {
    if (mimeType.includes('pdf')) return 'text-red-400'
    if (mimeType.includes('image')) return 'text-purple-400'
    if (mimeType.includes('video')) return 'text-blue-400'
    if (mimeType.includes('audio')) return 'text-green-400'
    return 'text-gray-400'
  }

  const handleDownload = async () => {
    if (!block.content) {
      setError('No content to download')
      return
    }

    setIsDownloading(true)
    setError(null)

    try {
      // If content is a URL, download from URL
      if (block.content.startsWith('http')) {
        const link = document.createElement('a')
        link.href = block.content
        link.download = fileName
        link.click()
      } else {
        // If content is base64, decode and download
        const byteCharacters = atob(block.content)
        const byteNumbers = new Array(byteCharacters.length)
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i)
        }
        const byteArray = new Uint8Array(byteNumbers)
        const blob = new Blob([byteArray], { type: mimeType })
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = fileName
        link.click()
        URL.revokeObjectURL(url)
      }
    } catch (err) {
      setError('Download failed: ' + err.message)
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <div className="bg-gray-800 rounded-lg p-4 my-2 border border-gray-700">
      <div className="flex items-center gap-3">
        <div className={`text-3xl ${getFileColor()}`}>
          {getFileIcon()}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-white font-medium truncate">{fileName}</p>
          <p className="text-gray-400 text-sm">
            {formatFileSize(fileSize)} • {mimeType.split('/')[1] || 'file'}
          </p>
        </div>
        <button
          onClick={handleDownload}
          disabled={isDownloading}
          className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-sm rounded transition disabled:opacity-50"
        >
          {isDownloading ? '下载中...' : '下载'}
        </button>
      </div>
      {error && (
        <p className="mt-2 text-red-400 text-sm">{error}</p>
      )}
    </div>
  )
}

function parseMetadata(jsonStr) {
  try {
    return JSON.parse(jsonStr)
  } catch {
    return {}
  }
}
