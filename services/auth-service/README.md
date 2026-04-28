---

## 🔐 Auth Service (Google OAuth2 + JWT)

The system uses a dedicated **auth-service** responsible for authentication and token issuance.

### Authentication flow

1. User is redirected to Google OAuth2
2. After successful login:
    - user is created (if not exists)
    - JWT token is generated
3. Token must be used in all secured requests

## Available endpoints

### Login via Google

```http
GET /oauth2/authorization/google
```

**Example:**

```
http://localhost:9191/oauth2/authorization/google
```

**Response:**

```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "email": "...",
    "role": "ROLE_USER"
  }
}
```
