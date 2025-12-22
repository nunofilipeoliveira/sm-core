package sm.core.ws;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.data.CompeticaoData;
import sm.core.data.ConvocatoriaData;
import sm.core.data.JogoData;
import sm.core.helper.JogoHelper;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/sm")
public class JogoWS {

    private static final Logger log = LoggerFactory.getLogger(JogoWS.class);

    @Autowired
    private JogoHelper jogoHelper;

    @CrossOrigin
    @PutMapping("/getAllJogosByEquipa/{id}")
    @ResponseBody
    public String loadAllJogosByEquipa(@PathVariable String id) {

        // Carregar jogos por equipa

        log.info("getAllJogosByEquipa | Start");
        log.info("getAllJogosByEquipa | ID:" + id);

        
        ArrayList<JogoData> jogos = jogoHelper.getAllJogosByEquipa(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            log.info("getAllJogosByEquipa | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogos);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.error("getAllJogosByEquipa | Error End");
        return "";
    }

    @CrossOrigin
    @PutMapping("/getAllCompeticoes")
    @ResponseBody
    public String getAllCompeticoes() {

        // Carregar todas as competições

        log.info("getAllCompeticoes | Start");

        
        ArrayList<CompeticaoData> competicoes = jogoHelper.getAllCompeticoes();

        ObjectMapper mapper = new ObjectMapper();

        try {

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(competicoes);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.error("getAllCompeticoes | Error End");
        return "";
    }

    @CrossOrigin
    @PutMapping("/createJogo")
    @ResponseBody
    public String createJogo(@RequestBody JogoData entity) {
        log.info("createJogo | Start");
        // TODO: process PUT request
        log.info("createJogo | Jogo:" + entity.toString());

        
        jogoHelper.createJogo(entity);

        log.info("createJogo | End");

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return "";
    }

    @CrossOrigin
    @PutMapping("/updateJogo")
    @ResponseBody
    public String updateJogo(@RequestBody JogoData entity) {
        log.info("updateJogo | Start");
        
        jogoHelper.updateJogo(entity);
        log.info("updateJogo | End");
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";

    }

    @CrossOrigin
    @PutMapping("/deleteJogo/{id}")
    @ResponseBody
    public String deleteJogo(@PathVariable String id) {
        log.info("deleteJogo | Start");
        log.info("deleteJogo | ID:" + id);

        
        boolean deleted = jogoHelper.deleteJogo(Integer.valueOf(id));

        log.info("deleteJogo | End");
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deleted);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";

    }

    @CrossOrigin
    @PutMapping("getJogoById/{id}")
    @ResponseBody
    public String getJogoById(@PathVariable String id) {    
        log.info("getJogoById | Start");
        log.info("getJogoById | ID:" + id);

        
        JogoData jogo = jogoHelper.getJogoById(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            log.info("getJogoById | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogo);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.error("getJogoById | Error End");
        return "";
    }    

    @CrossOrigin
    @PutMapping("/salvarConvocatoria")
    @ResponseBody
    public String salvarConvocatoria(@RequestBody ConvocatoriaData convocatoriaData) {
        log.info("salvarConvocatoria | Start");
     

        
        boolean saved = jogoHelper.salvarConvocatoria(convocatoriaData);

        log.info("salvarConvocatoria | End");
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(saved);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";

    }


    @CrossOrigin
    @PutMapping("/getConvocatoriaByJogoId/{id}")
    @ResponseBody
    public String getConvocatoriaByJogoId(@PathVariable String id) {
        log.info("getConvocatoriaByJogoId | Start");
        log.info("getConvocatoriaByJogoId | ID Jogo:" + id);

        
        ConvocatoriaData convocatoria = jogoHelper.getConvocatoriaByJogoId(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            log.info("getConvocatoriaByJogoId | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(convocatoria);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.error("getConvocatoriaByJogoId | Error End");
        return "";
    }

    @CrossOrigin
    @PutMapping("/atualizarJogo")
    @ResponseBody
    public String atualizarJogo(@RequestBody JogoData entity) {
        log.info("atualizarJogo | Start");
        
        jogoHelper.updateJogo(entity);
        log.info("atualizarJogo | End");
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }

    @CrossOrigin
    @PutMapping("/getJogosByJogadorId/{id}")
    @ResponseBody
    public String getJogosByJogadorId(@PathVariable String id) {
        log.info("getJogosByJogadorId | Start");
        log.info("getJogosByJogadorId | ID Jogador:" + id);

        
        ArrayList<JogoData> jogos = jogoHelper.getJogosByJogadorId(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            log.info("getJogosByJogadorId | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogos);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.error("getJogosByJogadorId | Error End");
        return "";
    }
    
}
