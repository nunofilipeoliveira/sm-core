# Jogo — Modo Cronómetro, Timeline, Substituições e Tempo de Jogo

Documento técnico da funcionalidade implementada em **sm-core** (backend Spring Boot)
e da respetiva proposta de base de dados e de interface (app Angular `sm`).

---

## 1. Base de dados

Ficheiro: `sm-core/src/main/resources/db/V1__jogo_cronometro_timeline.sql`

### 1.1 Colunas novas em `jogo_jogador`

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `titular` | TINYINT(1) | Jogador pertence ao 5 inicial (marcado de forma especial) |
| `em_campo` | TINYINT(1) | Está em campo neste momento (gerido pelos eventos) |
| `excluido_ate_segundos` | INT NULL | Tempo absoluto de jogo até ao qual está excluído (cartão azul = 2 min) |
| `tempo_jogo_segundos` | INT | Tempo de jogo corrigido manualmente (segundos) |
| `tempo_manual` | TINYINT(1) | 1 quando `tempo_jogo_segundos` foi definido à mão |

### 1.2 Tabela `jogo_config` (escolha do modo de registo)

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id_jogo` | INT (UNIQUE, FK jogo) | Jogo |
| `modo_registo` | VARCHAR(20) | `NORMAL` \| `CRONOMETRO` |
| `duracao_parte_minutos` | INT | Minutos de cada parte (ex.: 25) |
| `numero_partes` | INT | Número de partes (ex.: 2) |
| `num_jogadores_iniciais` | INT | Jogadores que começam em campo (ex.: 5) |
| `duracao_exclusao_azul_segundos` | INT | Exclusão após azul (120 s) |
| `tempo_atual_segundos` | INT | Posição do cronómetro (para retomar após recarregar) |

### 1.3 Tabela `jogo_evento` (timeline de eventos)

Cada evento regista a parte, o tempo exibido (`MM:SS`) e o tempo absoluto em
segundos. Eventos previstos:

- `GOLO` / `GOLO_SOFRIDO` → `detalhe`: `normal|p|ld|pp|up`
- `AMARELO` / `AZUL` / `VERMELHO`
- `FALTA` / `ASSISTENCIA` / `RECUPERACAO_BOLA` / `PERDA_BOLA` / `REMATE`
- `PENALTY_FALHADO` / `PENALTY_DEFESA` / `LD_FALHADO` / `LD_DEFESA`
- `SUBSTITUICAO` → `id_jogador` (entra) / `id_jogador_secundario` (sai)
- `INICIO_JOGO` / `INICIO_PARTE` / `FIM_PARTE` / `FIM_JOGO`
- `CORRECAO_TEMPO` → `detalhe` guarda o novo tempo (`MM:SS`)

---

## 2. Endpoints REST (sm-core → `/sm`)

| Método | Endpoint | Corpo | Devolve | Descrição |
| --- | --- | --- | --- | --- |
| PUT | `/sm/getConfigJogo/{idJogo}` | — | `JogoConfigData` | Config + jogadores (titular/em_campo/excluído/tempo) |
| PUT | `/sm/guardarConfigJogo` | `JogoConfigData` | `boolean` | Guarda modo, partes, minutos e 5 inicial |
| PUT | `/sm/registarEvento` | `JogoEventoData` | `JogoEventoData` | Regista evento na timeline + efeitos |
| PUT | `/sm/marcarSubstituicao` | `JogoEventoData` | `JogoEventoData` | Atalho para SUBSTITUICAO |
| PUT | `/sm/getTimeline/{idJogo}` | — | `JogoEventoData[]` | Timeline completa ordenada por tempo |
| PUT | `/sm/getEventosControle/{idJogo}` | — | `JogoEventoData[]` | Apenas eventos de controle (INICIO/FIM) para modo visualização |
| PUT | `/sm/editarEvento` | `JogoEventoData` | `boolean` | Corrige tempo e/ou evento |
| PUT | `/sm/eliminarEvento/{id}` | — | `boolean` | Elimina evento + reverte efeitos |
| PUT | `/sm/getTemposJogo/{idJogo}` | — | `JogadorJogo[]` | Tempo de jogo de todos os jogadores |
| PUT | `/sm/atualizarTempoJogo` | `AtualizarTempoJogoRequest` | `boolean` | Corrige tempo de jogo de um jogador |
| PUT | `/sm/atualizarTempoAtual` | `AtualizarTempoJogoRequest` | `boolean` | Persiste posição do cronómetro |

`AtualizarTempoJogoRequest`: `{ id_jogo, id_jogador, tempo_correcao_segundos, tempo_atual_segundos, tempo_atual_display }`

O contrato `PUT` + `@ResponseBody` + JSON segue exatamente o padrão já usado nos
restantes WS (`JogoWS`, `PresencaWS`).
---

## 3. Proposta de UI (expert gráfico)

A funcionalidade vive na página `jogo` (componente `JogoComponent` da app `sm`).
Sugestão de organização em 3 zonas na mesma página:

### 3.1 Escolha do modo (topo da página, antes de iniciar o registo)
- **Toggle / cards lado a lado**: `Modo Normal` vs `Modo Cronómetro`.
- No **Modo Cronómetro** abre um painel de setup:
  - *Duração da parte* (nº de minutos, stepper `− / +` e input);
  - *Nº de partes*;
  - *5 inicial* — grelha de jogadores com toque para marcar; os escolhidos
    ficam com **contorno/halo verde** e o número do jogador sobre fundo escuro;
    botão "Guardar configuração" dispara `guardarConfigJogo`.

### 3.2 Painel do cronómetro (Modo Cronómetro)
- **Display grande tipo relógio de recinto**: `MM:SS` a **descer**, com estado
  cromaticamente claro (verde = a correr, vermelho/âmbar = parado).
- Botão único **Iniciar / Parar** (ícone play/pause, grande, acessível a toque).
- Ao **Iniciar a 1.ª parte** dispara `INICIO_JOGO` (5 inicial entra em campo);
  ao **Iniciar outras partes** dispara `INICIO_PARTE`.
- Os jogadores **excluídos** (azul) mostram um badge/cronómetro próprio
  (`2:00` → `0:00`) calculado com `excluido_ate_segundos - tempo_atual_segundos`.

### 3.3 Registo de eventos com tempo automático
- Ao registar qualquer evento (golo, cartão, assistência, falta, …), o backend
  recebe `tempo_evento`/`tempo_segundos` do cronómetro atual e cria a entrada na
  timeline. No modo **Normal** a hora é preenchida manualmente (ou fica `00:00`).

### 3.4 Timeline (painel lateral ou expansível)
- Lista cronológica vertical com mini-ícones por tipo (⚽, 🟨, 🟦, 🟥, 🔄, ⏱️, 🏁).
- Cada linha mostra `Parte • MM:SS`, nome do jogador e detalhe.
- Ações por linha: **editar** (corrigir tempo `MM:SS` e tipo/jogador) e **apagar**
  (com confirmação). Botões discretos (ícones) para não poluir a leitura.

### 3.5 Substituições
- Botão **Substituir** no seletor de jogadores: abre modal com duas listas,
  "Entra" e "Sai"; ao confirmar regista `SUBSTITUICAO` na timeline.

### 3.6 Tempo de jogo dos jogadores
- Secção/abas **"Tempos de Jogo"**: tabela com Nome / Nº / Tempo (MM:SS) /
  Em campo (●) / Excluído (badge).
- Botão de **corrigir** (lápis) por linha → modal com input `MM:SS` →
  `atualizarTempoJogo`; o valor corrigido fica assinalado (ícone de mão) e é
  registado `CORRECAO_TEMPO` na timeline.

---

## 4. Integração no frontend Angular (`sm`)

### 4.1 Extensões à interface `JogadorJogo` (jogoData.ts)

```ts
titular?: boolean;            // 5 inicial
emCampo?: boolean;            // está em campo
excluidoAteSegundos?: number; // null quando não está excluído
tempoJogoSegundos?: number;   // tempo de jogo (corrigido se tempoManual)
tempoManual?: boolean;
isTitular?: boolean;          // conveniência de UI
```

### 4.2 Serviço (jogo.service.ts)

```ts
getConfigJogo(idJogo: number): Observable<JogoConfigData>
guardarConfigJogo(config: JogoConfigData): Observable<boolean>
registarEvento(evento: JogoEventoData): Observable<JogoEventoData>
marcarSubstituicao(evento: JogoEventoData): Observable<JogoEventoData>
getTimeline(idJogo: number): Observable<JogoEventoData[]>
getEventosControle(idJogo: number): Observable<JogoEventoData[]>  // Apenas INICIO/FIM
editarEvento(evento: JogoEventoData): Observable<boolean>
eliminarEvento(id: number): Observable<boolean>
getTemposJogo(idJogo: number): Observable<JogadorJogo[]>
atualizarTempoJogo(req: AtualizarTempoJogoRequest): Observable<boolean>
atualizarTempoAtual(req: AtualizarTempoJogoRequest): Observable<boolean>
```

Todos os pedidos usam `PUT` + `Content-Type: application/json`, como os atuais.

---

## 5. Notas / decisões

- Os **contadores** de `jogo_jogador` (golos, cartões, faltas…) continuam a ser a
  fonte para estatísticas; os eventos da timeline dão a cronologia e os tempos.
- O **azul** marca o jogador como fora de campo durante 2 minutos
  (`excluido_ate_segundos = tempo_segundos + 120`), configurável.
- **Editar/eliminar** eventos reverte e reaplica contadores; é 100% seguro para
  os eventos mais recentes (reversão em ordem cronológica).
- No **atualizarJogo** (rewrite da convocatória) os novos campos são preservados
  pelo payload (o frontend não deve chamá-lo a meio do jogo em modo cronómetro;
  cada evento usa `registarEvento`).