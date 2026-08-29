package sm.core.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.data.SondaResponse;
import sm.core.helper.SondaHelper;

@RestController
@RequestMapping("/sm")
public class SondaWS {

    private static final Logger log = LoggerFactory.getLogger(SondaWS.class);

    @Autowired
    private SondaHelper sondaHelper;

    /**
     * Endpoint de health check para a sonda
     * Verifica se todo o sistema está correto e funcional
     * 
     * Uso: PUT http://<host>:<porta>/sm/sonda
     */
    @CrossOrigin
    @PutMapping("/sonda")
    @ResponseBody
    public String verificarSistema() {
        log.info("SondaWS | verificarSistema | Start");

        SondaResponse response = sondaHelper.verificarSistema();

        ObjectMapper mapper = new ObjectMapper();

        try {
            log.info("SondaWS | verificarSistema | End - Status: " + response.getStatus());
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("SondaWS | verificarSistema | Error End", e);
        }

        log.error("SondaWS | verificarSistema | Error End");
        return "";
    }
}