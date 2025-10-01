package sm.core.ws;

import java.util.ArrayList;

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

    @CrossOrigin
    @PutMapping("/getAllJogosByEquipa/{id}")
    @ResponseBody
    public String loadAllJogosByEquipa(@PathVariable String id) {

        // Carregar jogos por equipa

        System.out.println("getAllJogosByEquipa | Start");
        System.out.println("getAllJogosByEquipa | ID:" + id);

        JogoHelper jogoHelper = new JogoHelper();
        ArrayList<JogoData> jogos = jogoHelper.getAllJogosByEquipa(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            System.out.println("getAllJogosByEquipa | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogos);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("getAllJogosByEquipa | Error End");
        return "";
    }

    @CrossOrigin
    @PutMapping("/getAllCompeticoes")
    @ResponseBody
    public String getAllCompeticoes() {

        // Carregar todas as competições

        System.out.println("getAllCompeticoes | Start");

        JogoHelper jogoHelper = new JogoHelper();
        ArrayList<CompeticaoData> competicoes = jogoHelper.getAllCompeticoes();

        ObjectMapper mapper = new ObjectMapper();

        try {

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(competicoes);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("getAllCompeticoes | Error End");
        return "";
    }

    @CrossOrigin
    @PutMapping("/createJogo")
    @ResponseBody
    public String createJogo(@RequestBody JogoData entity) {
        System.out.println("createJogo | Start");
        // TODO: process PUT request
        System.out.println("createJogo | Jogo:" + entity.toString());

        JogoHelper jogoHelper = new JogoHelper();
        jogoHelper.createJogo(entity);

        System.out.println("createJogo | End");

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
        System.out.println("updateJogo | Start");
        JogoHelper jogoHelper = new JogoHelper();
        jogoHelper.updateJogo(entity);
        System.out.println("updateJogo | End");
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
        System.out.println("deleteJogo | Start");
        System.out.println("deleteJogo | ID:" + id);

        JogoHelper jogoHelper = new JogoHelper();
        boolean deleted = jogoHelper.deleteJogo(Integer.valueOf(id));

        System.out.println("deleteJogo | End");
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
        System.out.println("getJogoById | Start");
        System.out.println("getJogoById | ID:" + id);

        JogoHelper jogoHelper = new JogoHelper();
        JogoData jogo = jogoHelper.getJogoById(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            System.out.println("getJogoById | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogo);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("getJogoById | Error End");
        return "";
    }    

    @CrossOrigin
    @PutMapping("/salvarConvocatoria")
    @ResponseBody
    public String salvarConvocatoria(@RequestBody ConvocatoriaData convocatoriaData) {
        System.out.println("salvarConvocatoria | Start");
        System.out.println("salvarConvocatoria | IDs Atletas:" + convocatoriaData.getJogadoresConvocados().toString());

        JogoHelper jogoHelper = new JogoHelper();
        boolean saved = jogoHelper.salvarConvocatoria(convocatoriaData);

        System.out.println("salvarConvocatoria | End");
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
        System.out.println("getConvocatoriaByJogoId | Start");
        System.out.println("getConvocatoriaByJogoId | ID Jogo:" + id);

        JogoHelper jogoHelper = new JogoHelper();
        ConvocatoriaData convocatoria = jogoHelper.getConvocatoriaByJogoId(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            System.out.println("getConvocatoriaByJogoId | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(convocatoria);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("getConvocatoriaByJogoId | Error End");
        return "";
    }

    @CrossOrigin
    @PutMapping("/atualizarJogo")
    @ResponseBody
    public String atualizarJogo(@RequestBody JogoData entity) {
        System.out.println("atualizarJogo | Start");
        JogoHelper jogoHelper = new JogoHelper();
        jogoHelper.updateJogo(entity);
        System.out.println("atualizarJogo | End");
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }
}
