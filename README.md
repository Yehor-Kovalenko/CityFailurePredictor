## Prerequisites
- Java 25
- Docker and Docker compose installed

## General configuration steps
1. Run eureka server (service-registry) (See dashboard at `http://localhost:8761/`)
2. Run config server
3. Run api gateway
4. Run other services

---

### How to prepare ConfigServer for the first time
In order to access the ConfigServer config registry, ONLY ONCE you need to do this:
1. `ssh-keygen -t ed25519 -C "email"`, where `email` is your github email
2. Visit https://github.com/settings/keys
3. Create new authentication key
4. Copy key from location where you saved generated public key (default `~/.ssh/*.pub`)
5. ssh -T git@github.com
