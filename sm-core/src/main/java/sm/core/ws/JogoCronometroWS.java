package sm.core.ws;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.data.AtualizarTempoJogoRequest;
import sm.core.data.JogadorJogo;
import sm.core.data.JogoConfigData;
import sm.core.data.JogoEventoData;
import sm.core.helper.JogoCronometroHelper;

/**
 * Web Services de suporte ao registo de atividade no jogo:
 * modo normal / cronómetro, timeline de eventos, substituições,
 * tempo de jogo dos jogadores e exclusão por cartão azul.
 */
@RestController
@RequestMapping("/sm")
public class JogoCronometroWS {

    private static final Logger log = LoggerFactory.getLogger(JogoCronometroWS.class);

    @Autowired
    private JogoCronometroHelper jogoCronometroHelper;

    private String toJson(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Configuração (modo normal vs cronómetro, partes, 5 inicial)
    // ------------------------------------------------------------------

    @CrossOrigin
    @PutMapping("/getConfigJogo/{idJogo}")
    @ResponseBody
    public String getConfigJogo(@PathVariable String idJogo) {
        log.info("getConfigJogo | Start | idJogo: " + idJogo);
        JogoConfigData config = jogoCronometroHelper.getConfigJogo(Integer.valueOf(idJogo));
        log.info("getConfigJogo | End");
        return toJson(config);
    }

    @CrossOrigin
    @PutMapping("/guardarConfigJogo")
    @ResponseBody
    public String guardarConfigJogo(@RequestBody JogoConfigData config) {
        log.info("guardarConfigJogo | Start | idJogo: " + config.getId_jogo());
        boolean saved = jogoCronometroHelper.guardarConfigJogo(config);
        log.info("guardarConfigJogo | End");
        return toJson(saved);
    }

    // ------------------------------------------------------------------
    // Timeline de eventos
    // ------------------------------------------------------------------

    @CrossOrigin
    @PutMapping("/registarEvento")
    @ResponseBody
    public String registarEvento(@RequestBody JogoEventoData evento) {
        log.info("registarEvento | Start | evento: " + evento);
        JogoEventoData saved = jogoCronometroHelper.registarEvento(evento);
        log.info("registarEvento | End");
        return toJson(saved);
    }

    @CrossOrigin
    @PutMapping("/marcarSubstituicao")
    @ResponseBody
    public String marcarSubstituicao(@RequestBody JogoEventoData evento) {
        log.info("marcarSubstituicao | Start | idJogo: " + evento.getId_jogo());
        JogoEventoData saved = jogoCronometroHelper.marcarSubstituicao(evento);
        log.info("marcarSubstituicao | End");
        return toJson(saved);
    }

    @CrossOrigin
    @PutMapping("/getTimeline/{idJogo}")
    @ResponseBody
    public String getTimeline(@PathVariable String idJogo) {
        log.info("getTimeline | Start | idJogo: " + idJogo);
        ArrayList<JogoEventoData> timeline = jogoCronometroHelper.getTimeline(Integer.valueOf(idJogo));
        log.info("getTimeline | End");
        return toJson(timeline);
    }

    @CrossOrigin
    @PutMapping("/getEventosControle/{idJogo}")
    @ResponseBody
    public String getEventosControle(@PathVariable String idJogo) {
        log.info("getEventosControle | Start | idJogo: " + idJogo);
        ArrayList<JogoEventoData> eventos = jogoCronometroHelper.getEventosControleJogo(Integer.valueOf(idJogo));
        log.info("getEventosControle | End");
        return toJson(eventos);
    }

    @CrossOrigin
    @PutMapping("/editarEvento")
    @ResponseBody
    public String editarEvento(@RequestBody JogoEventoData evento) {
        log.info("editarEvento | Start | id: " + evento.getId());
        boolean saved = jogoCronometroHelper.editarEvento(evento);
        log.info("editarEvento | End");
        return toJson(saved);
    }

    @CrossOrigin
    @PutMapping("/eliminarEvento/{id}")
    @ResponseBody
    public String eliminarEvento(@PathVariable String id) {
        log.info("eliminarEvento | Start | id: " + id);
        boolean deleted = jogoCronometroHelper.eliminarEvento(Integer.valueOf(id));
        log.info("eliminarEvento | End");
        return toJson(deleted);
    }

    // ------------------------------------------------------------------
    // Tempo de jogo dos jogadores
    // ------------------------------------------------------------------

    @CrossOrigin
    @PutMapping("/getTemposJogo/{idJogo}")
    @ResponseBody
    public String getTemposJogo(@PathVariable String idJogo) {
        log.info("getTemposJogo | Start | idJogo: " + idJogo);
        ArrayList<JogadorJogo> tempos = jogoCronometroHelper.getTemposJogo(Integer.valueOf(idJogo));
        log.info("getTemposJogo | End");
        return toJson(tempos);
    }

    @CrossOrigin
    @PutMapping("/atualizarTempoAtual")
    @ResponseBody
    public String atualizarTempoAtual(@RequestBody AtualizarTempoJogoRequest request) {
        log.info("atualizarTempoAtual | Start | idJogo: " + request.getId_jogo());
        boolean saved = jogoCronometroHelper.atualizarTempoAtual(request.getId_jogo(), request.getTempo_atual_segundos());
        log.info("atualizarTempoAtual | End");
        return toJson(saved);
    }
}