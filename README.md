# EmisferIA 3 - Interface Proativa Android

Interface minimalista de voz para Android que integra com o sistema EmisferIA 3.

**Sem botoes. Sem menus. Apenas voz.**

## Conceito

Uma tela limpa com visualizacao neural neon no centro. O app fica sempre ouvindo e responde por voz. Toda interacao acontece atraves de conversa natural em tempo real.

## Interface

```
┌─────────────────────────────┐
│                         [●] │  <- Indicador de conexao
│                             │
│    ┌─────────────────┐      │
│    │  Conversa       │      │
│    │  historico      │      │
│    │  de mensagens   │      │
│    └─────────────────┘      │
│                             │
│      ╭─────────────╮        │
│     ╱   ONDAS      ╲       │
│    │   NEURAIS     │       │  <- Visualizacao animada
│     ╲   NEON       ╱       │     que pulsa conforme
│      ╰─────────────╯        │     fala/escuta
│                             │
│      ═══════════════        │  <- Barra de status
│                             │
│      "transcricao..."       │  <- O que esta sendo dito
│                             │
└─────────────────────────────┘
```

## Funcionalidades

- **Visualizacao Neural Neon**: Ondas animadas que simbolizam EmisferIA
  - Pulsa em ciano quando ouvindo
  - Pulsa em roxo quando falando
  - Ondas reagem a amplitude da voz

- **Escuta Continua**: Sempre ouvindo, sem precisar pressionar nada
- **Historico de Conversa**: Mensagens aparecem como bolhas de chat
- **Sintese de Voz**: Respostas faladas em portugues brasileiro
- **Alertas Proativos**: Notifica automaticamente sobre urgencias

## Interacao por Voz

Basta falar naturalmente:

| Voce diz | EmisferIA responde |
|----------|-------------------|
| "Quais sao minhas tarefas?" | Lista tarefas pendentes |
| "Tenho compromissos hoje?" | Informa agenda do dia |
| "Como esta meu financeiro?" | Resumo de contas |
| "Me da um resumo" | Status geral do dia |
| "Oi" / "Bom dia" | Saudacao e oferece ajuda |
| "Criar tarefa comprar cafe" | Cria tarefa via API |
| "Enviar mensagem para Maria" | Inicia envio de mensagem |

## Estados Visuais

| Estado | Cor | Visual |
|--------|-----|--------|
| Ouvindo | Verde neon | Ondas suaves |
| Processando | Rosa neon | Ondas pulsando |
| Falando | Roxo neon | Ondas intensas |
| Erro conexao | Vermelho | Ponto vermelho |

## Arquitetura

```
app/src/main/java/com/emisferia/proactive/
├── MainActivity.kt              # Entry point
├── EmisferiaApp.kt             # Application class
├── ui/
│   ├── theme/
│   │   ├── Color.kt            # Cores neon
│   │   ├── Theme.kt            # Tema escuro
│   │   └── Type.kt             # Tipografia
│   ├── components/
│   │   ├── NeuralWaveView.kt   # Visualizacao central
│   │   └── InfoCard.kt         # Componentes auxiliares
│   └── screens/
│       └── MainScreen.kt       # Tela unica minimalista
├── service/
│   ├── VoiceRecognitionService.kt  # Speech-to-text
│   └── TextToSpeechService.kt      # Text-to-speech
├── viewmodel/
│   └── MainViewModel.kt        # Logica e estados
└── api/
    ├── EmisferiaApi.kt         # Cliente Retrofit
    └── ApiModels.kt            # Modelos de dados
```

## Configuracao

1. Clone o projeto
2. Crie `local.properties`:
   ```
   sdk.dir=/path/to/android/sdk
   EMISFERIA_API_URL=https://emisfera.valdigley.com/api
   ```

3. Build:
   ```bash
   ./gradlew assembleDebug
   ```

4. Instale:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Permissoes

- `RECORD_AUDIO` - Para reconhecimento de voz
- `INTERNET` - Para comunicacao com API

## Tecnologias

- Kotlin
- Jetpack Compose
- Material 3 (tema escuro customizado)
- Coroutines / Flow
- Retrofit + OkHttp
- Android Speech Recognition
- Android Text-to-Speech

## Tema Neon

```kotlin
NeonCyan    = #00FFFF  // Primario - ouvindo
NeonPurple  = #9D00FF  // Secundario - falando
NeonPink    = #FF00FF  // Destaque - processando
NeonGreen   = #00FF66  // Status - conectado
DarkBackground = #0A0A0F
```
