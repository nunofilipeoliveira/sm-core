# Correções - Componente Jogo e Timeline

## Resumo das Alterações

### 1. Eventos INICIO/FIM na timeline (modo visualização)
- Criado método `getEventosControleJogo()` para retornar apenas eventos de controle
- Adicionado endpoint `/sm/getEventosControle/{idJogo}`

### 2. Modo detalhe - mostrar 5 inicial
- O campo `titular` já está disponível em `getConfigJogo()` e `getJogadoresResumo()`
- Frontend deve usar `jogador.titular === true` para destacar titulares

### 3. Duplicação do início da primeira parte
- Adicionado método `existeEventoInicio()` para verificar duplicação
- `registarEvento()` agora impede criação de múltiplos INICIO_JOGO/INICIO_PARTE
- Retorna null com log de aviso se já existir

### 4. Status do jogo atualizado (concluído)
- Adicionado método `atualizarStatusJogo()` para atualizar `torneio_jogo.status`
- FIM_JOGO → atualiza status para 'completed'
- INICIO_JOGO → atualiza status para 'in-progress'
- Quando status = 'completed', modo live desaparece automaticamente

### 5. Tempo em real-time (modo decrescente)
- Backend já fornece `tempo_atual_segundos` em `jogo_config`
- Frontend deve implementar cronómetro decrescente baseado em `duracao_parte_minutos`
- Parar quando status = 'completed' ou 'scheduled'

### 6. Múltiplas substituições no mesmo momento
- Backend já suporta múltiplas substituições no mesmo `tempo_segundos`
- Frontend deve enviar cada substituição como POST separado

## Ficheiros Modificados

1. `JogoCronometroHelper.java`
   - + método `existeEventoInicio()` 
   - + método `atualizarStatusJogo()`
   - + método `getEventosControleJogo()`
   - Modificado `registarEvento()` com validações

2. `JogoCronometroWS.java`
   - + endpoint `/getEventosControle/{idJogo}`

3. `docs/jogo-cronometro-timeline.md`
   - Atualizado documentação com novo endpoint

## Próximos Passos (Frontend)

1. Modo visualização: usar `/getEventosControle` para exibir apenas INICIO/FIM
2. Modo detalhe: usar `/getTimeline` e destacar `titular = true`
3. Implementar cronómetro decrescente baseado em `tempo_atual_segundos`
4. Parar cronómetro quando status = 'completed'
5. Permitir múltiplas substituições no mesmo momento

## Validações Backend
- Impede duplicação de INICIO_JOGO/INICIO_PARTE
- Atualiza status automaticamente: INICIO → 'in-progress', FIM → 'completed'
- Logs de debug para acompanhamento