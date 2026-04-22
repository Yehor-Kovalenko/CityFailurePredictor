## Prerequisites
- Java 21
- Docker and Docker compose installed
- Access to private repository: CityFailurePredictor-ConfigServer

## General configuration steps
```bash
docker compose down
docker compose up -d --build
```

---

## 🔐 First-time Config Server setup (REQUIRED)

Config Server uses a private GitHub repository, so each developer must configure SSH access.

### 1. Generate dedicated SSH key (project-specific)

```bash
ssh-keygen -t ed25519 -f config-server-key -C "config-server" -N ""
```

This will create:
- `config-server-key` (private key)
- `config-server-key.pub` (public key)

---

## 2. Add key to GitHub (IMPORTANT)

Go to:
https://github.com/Senegalion/CityFailurePredictor-ConfigServer/settings/keys

- Click **"Add deploy key"**
- Paste contents of:

```bash
cat config-server-key.pub
```

- Name it (e.g. `config-server-local`)
- **DO NOT enable write access**
- Save

---

## 3. Add GitHub to known hosts

```bash
ssh-keyscan github.com > known_hosts
```
## 4. Verify files exist

You should have in project root:
- `config-server-key`
- `config-server-key.pub`
- `known_hosts`

---

## 5. Start containers

```bash
docker compose down
docker compose up -d --build
```
