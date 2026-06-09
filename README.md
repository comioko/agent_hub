# AgentHub

Multi-Agent Collaboration Platform - A chat-based interface for collaborating with AI agents.

## Features

- **Single Chat**: 1:1 conversation with AI agents
- **Multi-Agent Collaboration**: Group chat with multiple agents working together
- **Rich Messages**: Code blocks with syntax highlighting, markdown support
- **Real-time Updates**: SSE-based real-time message delivery
- **Conversation History**: Persistent chat history across sessions

## Tech Stack

- **Backend**: Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL
- **Frontend**: React 18 + Vite + Tailwind CSS + Zustand
- **Real-time**: Server-Sent Events (SSE)
- **Auth**: JWT

## Quick Start

### Prerequisites

- JDK 17+
- Node.js 18+
- MySQL 8.0

### Backend Setup

```bash
cd backend

# Create database
mysql -u root -p < src/main/resources/db/schema.sql

# Configure environment variables (optional, defaults work for local dev)
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=agenthub
export DB_USER=root
export DB_PASSWORD=your_password
export JWT_SECRET=your-secret-key

# Run
./mvnw spring-boot:run
# Or: mvn spring-boot:run
```

Backend runs at http://localhost:8080

### Frontend Setup

```bash
cd frontend

npm install
npm run dev
```

Frontend runs at http://localhost:5173

## API Endpoints

### Auth
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login
- `GET /api/auth/me` - Get current user

### Sessions
- `GET /api/sessions` - List user conversations
- `POST /api/sessions` - Create new conversation
- `GET /api/sessions/:id` - Get conversation details
- `DELETE /api/sessions/:id` - Delete conversation

### Messages
- `GET /api/messages/conversation/:id` - Get conversation messages
- `POST /api/messages` - Send message
- `GET /api/messages/subscribe` - SSE subscription

### Agents
- `GET /api/agents` - List available agents
- `GET /api/agents/:id` - Get agent details

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| DB_HOST | MySQL host | localhost |
| DB_PORT | MySQL port | 3306 |
| DB_NAME | Database name | agenthub |
| DB_USER | Database user | root |
| DB_PASSWORD | Database password | - |
| JWT_SECRET | JWT signing secret | - |
| OPENAI_API_KEY | OpenAI API key | - |
| ANTHROPIC_API_KEY | Anthropic API key | - |

## Project Structure

```
agenthub/
├── backend/           # Java Spring Boot backend
│   ├── src/main/java/com/agenthub/
│   │   ├── config/     # Configuration classes
│   │   ├── controller/ # REST controllers
│   │   ├── service/    # Business logic
│   │   ├── agent/      # Agent core logic
│   │   ├── adapter/    # Agent provider adapters
│   │   ├── repository/ # Data access
│   │   └── model/      # Entities and DTOs
│   └── src/main/resources/
│       ├── application.yml
│       └── db/schema.sql
└── frontend/           # React frontend
    ├── src/
    │   ├── api/        # API calls
    │   ├── components/ # UI components
    │   ├── pages/      # Page components
    │   ├── stores/     # Zustand stores
    │   └── hooks/      # Custom hooks
    └── package.json
```

## License

MIT
