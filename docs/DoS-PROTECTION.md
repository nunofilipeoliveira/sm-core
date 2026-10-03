# Proteção contra abuso / ataques DoS — SM Core

Este documento descreve as proteções **já implementadas** no `sm-core`, como as
afinar, o que ainda deve ser feito na "borda" (nginx/docker) e o que fazer numa
situação de ataque.

Antes desta alteração a aplicação não tinha **nenhum** limite de tráfego: os ~105
endpoints `/sm/**` eram públicos, sem rate limiting, sem limite de tamanho de
corpo, sem limite de concorrência e o WebSocket aceitava qualquer origem/sessão.

---

## 1. O que ficou protegido

| Camada | Proteção implementada | Onde |
|---|---|---|
| HTTP | **Autenticação JWT obrigatória** (`401` sem token válido), com modo `REPORT`/`ENFORCE` e lista de caminhos públicos | `TokenAuthenticationFilter` |
| HTTP | CORS definido num único ponto configurável (anotações `@CrossOrigin` removidas) | `SecurityConfig` + `Cors` |
| HTTP | Rate limiting (token bucket) por IP e por tipo de endpoint → `429 Too Many Requests` + `Retry-After` | `RateLimitFilter` |
| HTTP | Limite de pedidos simultâneos por IP e global → `503` (evita esgotar threads/BD/heap) | `RateLimitFilter` |
| HTTP | Limite de tamanho do corpo (JSON) → `413`; multipart pelo Spring | `RateLimitFilter` + `GlobalExceptionHandler` |
| HTTP | Estado em memória limitado (`max-tracked-clients`) + limpeza periódica | `RateLimitFilter` |
| HTTP | Log com throttling (1 aviso/segundo) — em ataque o log não enche o disco | `RateLimitFilter` |
| HTTP | Validação de IP do cliente com suporte a `X-Forwarded-For` (desligado por omissão) | `ClientIpResolver` |
| HTTP | `/actuator/**` pode ser restringido por allowlist de IPs/CIDR | `RateLimitFilter` |
| WebSocket | Origens permitidas configuráveis (antes `*`) | `WebSocketConfig` |
| WebSocket | Máx. de sessões por IP e total; handshake recusado com `429` | `WebSocketSessionGuard` |
| WebSocket | Limite de tamanho de mensagem, de buffer de envio, de tempo de envio e de "1ª mensagem" | `WebSocketConfig` |
| WebSocket | Pools de entrada/saída com fila limitada (não acumula trabalho em memória) | `WebSocketConfig` |
| WebSocket | Heartbeat do broker (deteta e liberta ligações mortas) | `WebSocketConfig` |
| Servidor | Limites de threads, ligações, `accept-count`, timeouts, `max-swallow-size` | `application*.properties` |
| Servidor | Erros sem stacktrace/detalhes internos | `application*.properties` |
| Servidor | Graceful shutdown (as ligações em curso terminam) | `application*.properties` + Dockerfile |
| BD | `connectionTimeout` de 5s em PROD (falha rápido em vez de prender threads 30s) | `application-prod.properties` |
| Email | Timeouts SMTP em todos os ambientes (evita thread presa no envio) | `application*.properties` |
| Uploads | Ficheiros temporários no `java.io.tmpdir` e sempre apagados | `FicheirosWS` |
| Uploads | Rejeição de nomes com `..`/`/`/`\` (path traversal) | `FicheirosWS` |
| Container | Heap dimensionada pelo limite do container, Metaspace limitado, `ExitOnOutOfMemoryError` | `Dockerfile` |

Todos estes limites são configuráveis por propriedades (`sm.core.security.*`),
sem necessidade de alterar código.

---

## 2. Regras de rate limiting

Token bucket por IP: `capacity` = rajada permitida, `refill-per-second` = ritmo
sustentado. `max-concurrent-requests` = pedidos simultâneos por IP (0 = sem
limite).

| Regra | Endpoints | Rajada | Sustentado | Simul. | Porquê |
|---|---|---|---|---|---|
| `global` | tudo o resto | 300 | 80/s | 20 | proteção geral |
| `login` | `/sm/login`, `/sm/resetPWD`, `/sm/createUtilizador`, `/sm/updateUser`, `/sm/activateuser`, `/sm/getcode`, `/sm/createuser`, `/sm/updateUserWithEscaloes` | 15 | 30/min | 4 | BCrypt + BD são caros (força bruta + CPU) |
| `session` | `/sm/isAuthenticated`, `/sm/extendSession` | 150 | 900/min | 20 | pedidos frequentes do front-end |
| `email` | `/sm/reenviarEmailAtivacao` | 3 | 1/50s | 2 | protege a conta SMTP de spam/flood |
| `upload` | `/sm/uploadfoto/**`, `/sm/uploadLogo/**` | 30 | 30/min | 3 | disco + memória |
| `probe` | `/sm/sonda` | 30 | 60/min | 5 | endpoint público que consulta a BD |
| `actuator` | `/actuator/**` | 30 | 120/min | 5 | scraping de métricas |

Em PROD estes valores são apertados: `max-concurrent-requests` global passa a 50
(limite da aplicação) e 15 por IP.

### Como ajustar

```properties
sm.core.security.rate-limit.login.capacity=15
sm.core.security.rate-limit.login.refill-per-second=0.5
sm.core.security.rate-limit.upload.paths[0]=/sm/uploadfoto
sm.core.security.rate-limit.enabled=false   # desliga tudo (não recomendado)
```

> **Hotspots com NAT**: os limites são por IP. Se um clube/escola tem muitos
> utilizadores atrás do mesmo IP público, aumente `global.capacity` e
> `global.refill-per-second` antes de reduzir os das regras específicas.

---

## 3. IP real do cliente (`X-Forwarded-For`)

Por omissão `trust-client-ip-header=false`: o IP usado é o do socket. Isto é
intencional — se a porta da aplicação (8080) estiver acessível diretamente da
Internet, um atacante pode enviar `X-Forwarded-For` falsos e **contornar todos os
limites** (além de criar entradas ilimitadas em memória).

Para ativar (só depois de garantir que 8080 **não** está exposta):

```properties
sm.core.security.rate-limit.trust-client-ip-header=true
sm.core.security.rate-limit.client-ip-header=X-Forwarded-For
```

E no docker-compose, publicar apenas o nginx (**não** usar `ports: 8080:8080`):

```yaml
services:
  smcorews:
    expose:
      - "8080"       # visível apenas dentro da rede do docker
  nginx:
    ports:
      - "80:80"
      - "443:443"
```

---

## 4. Borda (nginx) — a primeira linha de defesa

O rate limiting na aplicação protege a JVM; o nginx deve travar volume antes de
chegar lá. Exemplo de configuração do `server` de `sm.com.pt`:

```nginx
# zona partilhada: 10MB de estado ~ 160k IPs
limit_req_zone $binary_remote_addr zone=sm_api:10m rate=30r/s;
limit_req_zone $binary_remote_addr zone=sm_login:10m rate=30r/m;
limit_conn_zone $binary_remote_addr zone=sm_conn:10m;
limit_req_status 429;
limit_conn_status 429;

server {
    client_max_body_size 12m;      # coerente com o limite de upload da app
    client_body_timeout 15s;
    client_header_timeout 10s;
    send_timeout 20s;
    keepalive_timeout 20s;
    keepalive_requests 200;

    location / {
        limit_req zone=sm_api burst=60 nodelay;
        limit_conn sm_conn 20;
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 30s;
    }

    # login mais apertado
    location /sm/login {
        limit_req zone=sm_login burst=10 nodelay;
        limit_conn sm_conn 5;
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # uploads pesados
    location ~ ^/sm/upload(foto|Logo)/ {
        limit_req zone=sm_login burst=10 nodelay;
        limit_conn sm_conn 3;
        proxy_pass http://127.0.0.1:8080;
    }

    # WebSocket (upgrade)
    location /ws-tournament {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
    }

    # métricas NUNCA expostas à Internet
    location /actuator/ {
        allow 127.0.0.1;
        allow 172.16.0.0/12;
        deny all;
        proxy_pass http://127.0.0.1:8080;
    }
}
```

`limit_req` protege contra rajadas por IP; para ataques distribuídos (botnet) o
que salva a aplicação é o limite de **concorrência** interno (50 pedidos) mais os
limites de Tomcat (60 threads, `accept-count` 40).

---

## 5. Docker — limites de recursos (obrigatório)

Sem limites de memória/CPU no container, um ataque que force alocação de heap pode
degradar o **host** inteiro. No `/SM/docker-compose.yml`:

```yaml
services:
  smcorews:
    image: nfoliveira/smcorews:latest
    mem_limit: 1g
    memswap_limit: 1g          # sem swap: melhor falhar depressa
    cpus: "1.5"
    pids_limit: 512            # anti fork-bomb / thread-bomb
    restart: unless-stopped
    stop_grace_period: 30s
    logging:
      driver: json-file
      options: { max-size: "20m", max-file: "5" }   # o log não enche o disco
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
```

O `Dockerfile` usa `-XX:MaxRAMPercentage=70`: com `mem_limit: 1g` a heap máxima
fica em ~700MB (Metaspace limitado a 192MB). Sem `mem_limit` a percentagem é
calculada sobre a RAM do **host** — não é o que se quer.

---

## 6. Monitorização e alertas

Endpoints: `/actuator/health` e (PROD) `/actuator/prometheus` — manter na rede
interna (secção 4). Existe também a opção de restringir na aplicação:

```properties
sm.core.security.rate-limit.actuator-allowed-clients[0]=127.0.0.1
sm.core.security.rate-limit.actuator-allowed-clients[1]=172.16.0.0/12
```

Métricas a vigiar (Prometheus):

| Métrica | Alerta sugerido |
|---|---|
| `http_server_requests_seconds_count{status="429"}` | > 50/min (limites a atuar) |
| `http_server_requests_seconds_count{status="503"}` | > 10/min (app saturada) |
| `hikaricp_connections_pending` | > 0 durante 1 min (threads à espera de BD) |
| `hikaricp_connections_active` | == `maximumPoolSize` durante 5 min |
| `jvm_memory_used_bytes{area="heap"}` vs `jvm_memory_max_bytes` | > 85% |
| `tomcat_threads_busy_threads` vs `tomcat_threads_config_max_threads` | > 80% |
| `process_cpu_usage` | > 0.9 durante 5 min |
| `system_load_average_1m` | > nº de núcleos |
| `http_server_requests_seconds_count{uri="/sm/login"}` | picos anormais (força bruta) |

Log a vigiar: `RateLimitFilter | N pedidos bloqueados ...` — uma linha por
segundo com o exemplo de IP/regra/rota bloqueada (não faz flood de log).

---

## 7. Runbook — estou a ser atacado, o que faço?

1. **Confirmar**: métricas de 429/503, threads Tomcat, CPU e ligações Hikari.
2. **Apertar limites sem parar o serviço** (editar e reiniciar apenas o `smcorews`):
   ```properties
   sm.core.security.rate-limit.max-concurrent-requests=20
   sm.core.security.rate-limit.global.capacity=60
   sm.core.security.rate-limit.global.refill-per-second=10
   ```
   e no nginx baixar `rate=30r/s` → `rate=5r/s` com `nginx -s reload`.
3. **Bloquear a origem** no nginx (`deny 1.2.3.4;`) para IPs identificados no log.
4. **Desligar o não essencial**:
   - emails: `sm.core.security.rate-limit.email.capacity=0`
   - uploads: `sm.core.security.rate-limit.upload.capacity=0`
   - novos WebSocket: `sm.core.security.websocket.max-total-sessions=0`
5. **OOM**: `-XX:+ExitOnOutOfMemoryError` + `restart: unless-stopped` reinicia o
   container; sem `mem_limit` definido o reinício pode competir com o host.
6. **Pós-incidente**: guardar logs (IPs/rotas/user-agents), ajustar limites e
   avaliar bloqueio na firewall.

### Testar as proteções

```bash
# deve começar a devolver 429 após a rajada da regra "probe" (30 tokens)
for i in $(seq 1 40); do curl -s -o /dev/null -w "%{http_code}\n" -X PUT https://sm.com.pt/sm/sonda; done

# 413 para corpo acima do limite (1MB)
curl -s -o /dev/null -w "%{http_code}\n" -X PUT https://sm.com.pt/sm/getPresenca \
  -H 'Content-Type: application/json' --data-binary @<(head -c 2000000 /dev/zero | tr '\0' 'a')

# 413 para upload acima de 10MB
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://sm.com.pt/sm/uploadfoto/1/1 -F foto=@ficheiro20mb.jpg
```

---

## 8. Autenticação de endpoints (JWT) — como ativar

**Implementado**: o `TokenAuthenticationFilter` exige um token JWT válido
(assinatura + expiração, gerado no login) para **todos** os endpoints, exceto:

```
/sm/login, /sm/isAuthenticated, /sm/extendSession, /sm/createUtilizador,
/sm/reenviarEmailAtivacao, /sm/activateuser, /sm/getcode, /sm/sonda,
/sm/helloworld, /actuator
```

O token é aceite em `Authorization: Bearer <token>`, `X-Auth-Token`, `Token`
(cabeçalhos configuráveis) ou `?token=<token>`. O utilizador autenticado fica
disponível no pedido em `sm.auth.user` (atributo) para uso futuro pelos
controladores.

### Porque está em modo REPORT (e como mudar)

O modo por omissão é `REPORT`: **registra em log mas não bloqueia**. É
deliberado — o front-end que está hoje em produção **não envia o token** em
nenhum cabeçalho, pelo que impor `ENFORCE` de imediato derrubaria a aplicação.

A ativação é feita em 3 passos, sem risco:

1. **Deploy do backend** (modo `REPORT`, por omissão) — nada muda para o
   utilizador, e passam a aparecer linhas como:
   ```
   TokenAuthenticationFilter | 12 pedidos sem autenticacao valida (sem token):
       exemplo path=/sm/getAllJogadores/1 ip=...
   ```
   Se estas linhas aparecerem em grande número com *token inválido*, o cliente
   está a enviar um token que já expirou — ver passo 2.

   **Nota — preflight CORS**: os avisos *não contam* pedidos `OPTIONS` de
   preflight. Esses pedidos são lançados pelo navegador sem qualquer cabeçalho
   (nunca levam `Authorization`), pelo que registá-los como "sem token" seria um
   falso positivo. Como o front-end corre noutra origem, quase todos os pedidos
   reais têm um preflight antes de si.
2. **Deploy do front-end** com o interceptor `AuthTokenInterceptor`
   (`sm/src/app/interceptors/auth-token.interceptor.ts`, já registado em
   `app.module.ts`), que passa a enviar `Authorization: Bearer <token>` em todos
   os pedidos para `environment.apiUrl`.
3. **Ativar o bloqueio** (uma propriedade, sem recompilar):
   ```properties
   sm.core.security.auth.mode=ENFORCE
   ```
   ou por variável de ambiente no container:
   ```
   SM_CORE_SECURITY_AUTH_MODE=ENFORCE
   ```

Se algo ficar bloqueado indevidamente: `sm.core.security.auth.enabled=false`
(ou `mode=REPORT`) repõe imediatamente o comportamento anterior, e
`sm.core.security.auth.public-paths[i]=/sm/...` isenta caminhos específicos.

### Cuidados

- `/sm/extendSession` e `/sm/isAuthenticated` ficam públicos de propósito: o
  guard do front-end chama-os quando a sessão expira e o token vai no **corpo**
  do pedido. Ambos validam o token internamente, por isso não perdem segurança.
- `/sm/createUtilizador` e `/sm/reenviarEmailAtivacao` ficam públicos (registo
  de novos utilizadores) mas estão limitados pela regra `login`/`email` do rate
  limiter (contra registo/spam massivo).
- WebSocket: se o cliente passar a enviar `?token=...` no handshake, ativar
  `sm.core.security.auth.websocket-enforce=true` (o front-end atual, desta
  versão, não usa WebSocket).

---

## 9. Limitações conhecidas / próximos passos

O que **ainda não** está resolvido (por ordem de impacto):

1. **Segredos em texto simples** em `application*.properties` (BD, SMTP, FTP,
   `SECRET_KEY` do JWT hardcoded em `TokenGenerator`/`TokenValidator`). Passar para
   variáveis de ambiente/`SPRING_APPLICATION_JSON` e rodar as credenciais —
   enquanto o segredo for conhecido, qualquer pessoa pode **forjar tokens válidos**
   e a autenticação da secção 8 perde valor.
2. **Sem autorização (perfis)**: qualquer utilizador autenticado pode chamar
   endpoints de administração (`/sm/createuser`, `/sm/disableUser`,
   `/sm/resetPWD`, `/sm/gethistoricoLogins`, `/sm/upload*`). O próximo passo é
   verificar o `perfil` do utilizador por endpoint.
3. **Uploads sem validação de conteúdo**: validar a assinatura (magic bytes) da
   imagem para impedir uso da aplicação como armazenamento de ficheiros.
4. **Corpo em `Transfer-Encoding: chunked`** não tem `Content-Length` e por isso
   não é travado pelo filtro; os limites do Tomcat (`max-http-form-post-size`,
   `max-swallow-size`) e o `client_max_body_size` do nginx cobrem a maioria dos
   casos — usar o nginx como única entrada resolve definitivamente.
5. **HTTPS**: não há `server.ssl` configurado — garantir que o TLS termina no
   nginx (HSTS) e que 8080 nunca é exposta.

### CORS (resolvido)

As 92 anotações `@CrossOrigin` sem atributos foram **removidas** dos
controladores: a política CORS passa a ser definida num único lugar
(`sm.core.security.cors.*`, aplicada em `SecurityConfig`). O valor por omissão
continua a ser `*`, ou seja, **nada muda** até configurares as origens:

```properties
sm.core.security.cors.allowed-origin-patterns[0]=https://sm.com.pt
sm.core.security.cors.allowed-origin-patterns[1]=https://*.sm.com.pt
sm.core.security.websocket.allowed-origin-patterns[0]=https://sm.com.pt
```

Confirmar primeiro a origem real de cada tenant (em `sm/src/environments/*.ts` o
`apiUrl` varia por tenant; o `root` define o subdomínio) e testar o preflight
(`OPTIONS`) antes de publicar.

---

## 10. Ficheiros alterados / criados

| Ficheiro | O que faz |
|---|---|
| `sm-core/src/main/java/sm/core/config/SecurityProperties.java` | propriedades `sm.core.security.*` |
| `sm-core/src/main/java/sm/core/config/RateLimit.java` | limites HTTP (regras por endpoint) |
| `sm-core/src/main/java/sm/core/config/Rule.java` | definição de uma regra (capacidade/ritmo/concorrência/paths) |
| `sm-core/src/main/java/sm/core/config/Cors.java` | CORS global |
| `sm-core/src/main/java/sm/core/config/WebSocketLimits.java` | limites WebSocket |
| `sm-core/src/main/java/sm/core/config/SecurityConfig.java` | registo do filtro + CORS |
| `sm-core/src/main/java/sm/core/config/GlobalExceptionHandler.java` | `413` para uploads grandes |
| `sm-core/src/main/java/sm/core/config/WebSocketConfig.java` | limites/origens/heartbeat do WebSocket |
| `sm-core/src/main/java/sm/core/config/Auth.java` | propriedades da autenticação por token |
| `sm-core/src/main/java/sm/core/security/TokenAuthenticationFilter.java` | exige token JWT válido nos endpoints (modos REPORT/ENFORCE) |
| `sm-core/src/main/java/sm/core/security/AuthTokens.java` | extracção/validação de tokens (headers e query string) |
| `sm-core/src/main/java/sm/core/utils/TokenValidator.java` | validação com logs a `debug` (evita log flood em ataque) |
| `sm-core/src/main/java/sm/core/security/RateLimitFilter.java` | filtro de rate limiting |
| `sm-core/src/main/java/sm/core/security/ClientIpResolver.java` | IP do cliente + allowlist CIDR |
| `sm-core/src/main/java/sm/core/security/WebSocketSessionGuard.java` | limite de sessões WebSocket |
| `sm-core/src/main/java/sm/core/ws/FicheirosWS.java` | path traversal + ficheiros temporários |
| `sm-core/src/main/resources/application*.properties` | Tomcat, actuator, erros, SMTP, rate limit |
| `sm-core/Dockerfile` | limites de JVM + graceful shutdown |
| `sm-core/src/test/java/sm/core/security/RateLimitFilterTest.java` | 11 testes do filtro de rate limiting |
| `sm-core/src/test/java/sm/core/security/TokenAuthenticationFilterTest.java` | 11 testes da autenticação por token |
| `sm-core/src/test/java/sm/core/security/SecurityHttpIntegrationTest.java` | 3 testes HTTP reais (CORS, preflight, modo REPORT) |
| `sm-core/src/test/java/sm/core/security/TokenAuthenticationIntegrationTest.java` | 5 testes HTTP reais em modo ENFORCE (401/200) |
| `sm-core/src/test/java/sm/core/config/SecurityPropertiesTest.java` | 3 testes de binding |
| `sm-core/src/test/java/sm/core/SmCoreContextTest.java` | arranque da app com as proteções |
| `sm/src/app/interceptors/auth-token.interceptor.ts` (front-end) | envia `Authorization: Bearer <token>` para a API |
| `sm/src/app/app.module.ts` (front-end) | regista o interceptor |

Testes: `cd sm-core && ./mvnw test` (51 testes, sem dependências novas).
Os testes `*IntegrationTest` arrancam o servidor HTTP real e usam
`java.net.http.HttpClient` — o `TestRestTemplate` não serve, porque o
`HttpURLConnection` do JDK descarta os cabeçalhos `Origin` e
`Access-Control-Request-*`, o que daria falsos negativos nos testes de CORS.


---

