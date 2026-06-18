package sm.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import sm.core.data.Torneio_jogo;
import java.util.ArrayList;

/**
 * Serviço responsável por enviar notificações WebSocket para o frontend.
 * Publica mensagens nos tópicos STOMP para que os clientes recebam
 * atualizações em tempo real.
 */
@Service
public class TournamentWebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Notifica todos os clientes que houve uma atualização geral nos jogos.
     * Os clientes devem recarregar a lista completa de jogos.
     */
    public void notifyGamesUpdate() {
        System.out.println("📡 WebSocket: Notificando atualização geral de jogos...");
        messagingTemplate.convertAndSend("/topic/games", "UPDATE");
    }

    /**
     * Notifica todos os clientes sobre a atualização de um jogo específico.
     * Envia o objeto do jogo atualizado para que o frontend possa atualizar
     * apenas esse jogo na lista.
     */
    public void notifyGameUpdate(Torneio_jogo match) {
        System.out.println("📡 WebSocket: Notificando atualização do jogo #" + match.getId());
        messagingTemplate.convertAndSend("/topic/game/" + match.getId(), match);
    }

    /**
     * Notifica todos os clientes sobre a atualização de classificação de um round.
     * Envia a classificação completa do round para atualizar o modal.
     */
    public void notifyClassificacaoUpdate(String round, ArrayList<sm.core.data.Torneio_classificacao> classificacao) {
        System.out.println("📡 WebSocket: Notificando classificação do round " + round);
        messagingTemplate.convertAndSend("/topic/classificacao/" + round, classificacao);
    }

    /**
     * Notifica que um jogo foi resetado.
     */
    public void notifyGameReset(int matchId) {
        System.out.println("� WebSocket: Notificando reset do jogo #" + matchId);
        messagingTemplate.convertAndSend("/topic/game/" + matchId, "RESET");
        messagingTemplate.convertAndSend("/topic/games", "RESET");
    }
}