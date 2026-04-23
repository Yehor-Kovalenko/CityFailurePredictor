## Prerequisites
- Java 21
- Docker and Docker compose installed

## General configuration steps
```bash
docker compose down
docker compose up -d --build
```

---

### How to prepare ConfigServer for the first time
In order to access the ConfigServer config registry, ONLY ONCE you need to do this:
1. `ssh-keygen -t ed25519 -C "email"`, where `email` is your github email
2. Visit https://github.com/settings/keys
3. Create new authentication key
4. Copy key from location where you saved generated public key (default `~/.ssh/*.pub`)
5. ssh -T git@github.com

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

---

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
