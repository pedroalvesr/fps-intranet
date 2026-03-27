# FPS Intranet — PicPay Team Building

FPS multiplayer estilo CS para rede local (LAN/intranet). Java 21 + Vulkan (LWJGL 3) + Netty.

## Requisitos

- **Java 21+** (JDK)
- **Vulkan-capable GPU**
- **macOS** (Apple Silicon ou Intel):
  ```bash
  brew install vulkan-loader molten-vk
  ```

## Build

```bash
./gradlew build
```

## Como jogar

### 1. Iniciar o Server

```bash
./gradlew :server:run
```

O server escuta na porta **27015** (UDP). Anote o IP da máquina (ex: `192.168.1.100`).

### 2. Conectar Clients

```bash
# macOS — precisa exportar antes de rodar
export DYLD_LIBRARY_PATH=/opt/homebrew/lib

# Conectar
./gradlew :client:run -Dserver=192.168.1.100 -Dname="SeuNome"

# Teste local
./gradlew :client:run -Dserver=127.0.0.1 -Dname="Pedro"
```

**Alternativa (java direto, mais confiável no macOS):**

```bash
# Build primeiro
./gradlew build

# Rodar server
java --enable-native-access=ALL-UNNAMED \
  -cp "server/build/libs/*:shared/build/libs/*" \
  com.picpay.fps.server.GameServer

# Rodar client (outro terminal)
export DYLD_LIBRARY_PATH=/opt/homebrew/lib
java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED \
  -cp "client/build/libs/*:shared/build/libs/*" \
  com.picpay.fps.client.game.GameClient 127.0.0.1 "SeuNome"
```

### Controles

| Tecla | Ação |
|-------|------|
| W/A/S/D | Mover |
| Mouse | Olhar |
| Clique esquerdo | Atirar |
| Shift | Correr |
| ESC | Liberar/capturar mouse |

## Arquitetura

```
fps-intranet/
├── shared/     → Protocolo de rede, constantes, math (AABB)
├── server/     → Game server autoritativo (64 tick/s, Netty UDP)
└── client/     → Vulkan renderer, GLFW input, networking
```

### Modelo de Rede
- **Server autoritativo**: server processa toda a lógica de jogo
- **Client prediction**: client move localmente para responsividade
- **UDP via Netty**: baixa latência, ideal para LAN
- **Snapshots**: server envia estado do mundo a cada tick

### Gameplay (MVP)
- Team Deathmatch (Red vs Blue)
- Até 20 jogadores (10v10)
- Mapa arena com cover walls
- Arma hitscan (raio instantâneo)
- Auto-respawn em 3 segundos
- Visual low-poly (cores por vértice, sem texturas)

## Configuração

Edite `shared/.../constants/GameConfig.java` para ajustar:
- Porta do server, tick rate, max jogadores
- Velocidade, HP, dano das armas
- Tamanho do mapa, sensibilidade do mouse, FOV

## Troubleshooting

### macOS: processo morre silenciosamente (SIGKILL)
Garanta que o Vulkan loader está instalado:
```bash
brew install vulkan-loader molten-vk
export DYLD_LIBRARY_PATH=/opt/homebrew/lib
```

### "Address already in use" no server
Mate processos anteriores: `lsof -ti:27015 | xargs kill`

### Firewall bloqueando conexão
Abra a porta UDP 27015. No macOS: System Settings → Network → Firewall.
