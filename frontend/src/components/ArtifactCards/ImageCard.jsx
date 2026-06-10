import { useState } from 'react'

export default function ImageCard({ block }) {
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)

  const metadata = parseMetadata(block.metadata)
  const alt = metadata.alt || metadata.description || 'Image'

  const handleImageLoad = () => {
    setIsLoading(false)
  }

  const handleImageError = () => {
    setIsLoading(false)
    setError('Failed to load image')
  }

  const openFullscreen = () => {
    setIsFullscreen(true)
  }

  const closeFullscreen = () => {
    setIsFullscreen(false)
  }

  return (
    <>
      <div className="bg-gray-800 rounded-lg p-2 my-2 border border-gray-700">
        <div className="relative group">
          {isLoading && (
            <div className="absolute inset-0 flex items-center justify-center bg-gray-700 rounded">
              <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
          )}
          {error ? (
            <div className="flex items-center justify-center h-32 text-gray-400">
              <span>{error}</span>
            </div>
          ) : (
            <img
              src={block.content}
              alt={alt}
              className={`max-w-full max-h-96 rounded cursor-pointer transition-opacity ${isLoading ? 'opacity-0' : 'opacity-100'}`}
              onClick={openFullscreen}
              onLoad={handleImageLoad}
              onError={handleImageError}
            />
          )}
          {!isLoading && !error && (
            <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-all rounded flex items-center justify-center opacity-0 group-hover:opacity-100">
              <button
                onClick={openFullscreen}
                className="p-2 bg-white/20 hover:bg-white/30 rounded-full backdrop-blur transition"
              >
                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v6m3-3H7" />
                </svg>
              </button>
            </div>
          )}
        </div>
        {metadata.caption && (
          <p className="mt-2 text-gray-400 text-sm text-center">{metadata.caption}</p>
        )}
      </div>

      {/* Fullscreen Modal */}
      {isFullscreen && (
        <div
          className="fixed inset-0 bg-black/90 z-50 flex items-center justify-center p-4"
          onClick={closeFullscreen}
        >
          <button
            className="absolute top-4 right-4 p-2 text-white hover:text-gray-300 transition"
            onClick={closeFullscreen}
          >
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          <img
            src={block.content}
            alt={alt}
            className="max-w-full max-h-full object-contain rounded"
            onClick={(e) => e.stopPropagation()}
          />
          {metadata.caption && (
            <p className="absolute bottom-4 left-0 right-0 text-white text-sm text-center">
              {metadata.caption}
            </p>
          )}
        </div>
      )}
    </>
  )
}

function parseMetadata(jsonStr) {
  try {
    return JSON.parse(jsonStr)
  } catch {
    return {}
  }
}
