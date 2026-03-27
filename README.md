# FPS Intranet — PicPay Team Building

FPS multiplayer estilo CS para rede local (LAN/intranet). Java 21 + Vulkan (LWJGL 3) + Netty.

## Setup (todo mundo precisa fazer)

### 1. Clonar o projeto

```bash
git clone git@github.com:pedroharibeiropicpay/fps-intranet.git
cd fps-intranet
```

### 2. Instalar dependências

- **Java 21+** (JDK) — `java -version` pra conferir
- **macOS** — instalar Vulkan:
  ```bash
  brew install vulkan-loader molten-vk
  ```

### 3. Build

```bash
./gradlew build
```

## Como jogar em rede (multiplayer)

```
    Rede local (ex: 10.200.1.x)
    ┌──────────────────────────────────────────┐
    │                                          │
    │  [Server]  10.200.1.50:27015 (UDP)       │
    │     ▲  ▲  ▲                              │
    │     │  │  │                               │
    │     │  │  └── Client 3: "Ana"            │
    │     │  └───── Client 2: "João"           │
    │     └──────── Client 1: "Pedro"          │
    │                                          │
    └──────────────────────────────────────────┘
```

Todos precisam estar na **mesma rede** (mesma VLAN/subnet). Na VPN do PicPay funciona se estiverem na mesma rede corporativa. O protocolo é **UDP na porta 27015**.

### 1. Alguém vira o Server

Uma pessoa inicia o server (não precisa de GPU, pode ser qualquer máquina):

```bash
./gradlew :server:run
```

Essa pessoa descobre o IP local:

```bash
# macOS
ipconfig getifaddr en0

# Linux
hostname -I | awk '{print $1}'

# Windows
ipconfig | findstr IPv4
```

Compartilha o IP com o grupo (ex: `10.200.1.50`).

### 2. Todo mundo conecta como Client

Cada jogador roda no seu computador:

```bash
# macOS — exportar antes de rodar
export DYLD_LIBRARY_PATH=/opt/homebrew/lib

# Conectar (troque o IP e o nome)
./gradlew :client:run -Dserver=10.200.1.50 -Dname="SeuNome"
```

O host (quem roda o server) também pode jogar — basta abrir outro terminal e conectar com `-Dserver=127.0.0.1`.

O server aceita até **20 jogadores** e distribui automaticamente nos times **Red** e **Blue**.

### Controles

| Tecla | Ação |
|-------|------|
| W/A/S/D | Mover |
| Mouse | Olhar |
| Clique esquerdo | Atirar |
| Shift | Correr |
| ESC | Liberar/capturar mouse |

## Gameplay

- **Team Deathmatch** — Red vs Blue (10v10)
- Mapa arena com paredes de cobertura
- Arma hitscan (raio instantâneo, estilo CS)
- Auto-respawn em 3 segundos
- Visual low-poly (sem texturas, cores por vértice)

## Arquitetura

```
fps-intranet/
├── shared/     → Protocolo de rede, constantes, math (AABB)
├── server/     → Game server autoritativo (64 tick/s, Netty UDP)
└── client/     → Vulkan renderer, GLFW input, networking
```

- **Server autoritativo**: processa toda a lógica de jogo
- **Client prediction**: client move localmente para responsividade
- **UDP via Netty**: baixa latência, ideal para LAN
- **Snapshots**: server broadcast do estado do mundo a cada tick

## Configuração

Edite `shared/src/main/java/com/picpay/fps/shared/constants/GameConfig.java` para ajustar:
- `SERVER_PORT` — porta UDP (padrão: 27015)
- `MAX_PLAYERS` — máximo de jogadores (padrão: 20)
- `TICK_RATE` — ticks por segundo do server (padrão: 64)
- `PLAYER_SPEED` / `PLAYER_SPRINT_SPEED` — velocidade de movimento
- `PLAYER_MAX_HP` — vida máxima
- `PISTOL_DAMAGE` / `RIFLE_DAMAGE` — dano das armas
- `MOUSE_SENSITIVITY` / `FOV` — controles de câmera

Depois de alterar, rebuilde: `./gradlew build`

## Troubleshooting

### macOS: processo morre silenciosamente (SIGKILL)
O Vulkan loader não está instalado ou não está no path:
```bash
brew install vulkan-loader molten-vk
export DYLD_LIBRARY_PATH=/opt/homebrew/lib
```

### Client não conecta no server
1. Confira se estão na **mesma rede**
2. Confira o **IP** (não use `localhost` — use o IP real)
3. Confira o **firewall** da máquina do server:
   - macOS: System Settings → Network → Firewall → liberar porta 27015 UDP
   - Ou desative temporariamente pra testar
4. Teste a porta: `nc -u -z <IP_DO_SERVER> 27015`

### "Address already in use" no server
O server anterior ainda está rodando. Mate ele:
```bash
lsof -ti:27015 | xargs kill
```

### Windows
O build Gradle detecta o OS automaticamente e baixa os natives corretos. Não precisa de MoltenVK — Vulkan roda nativamente. Só garanta que tem drivers de GPU atualizados.

### Linux
Instale o Vulkan SDK do seu distro:
```bash
# Ubuntu/Debian
sudo apt install vulkan-tools libvulkan-dev

# Fedora
sudo dnf install vulkan-tools vulkan-loader-devel
```
