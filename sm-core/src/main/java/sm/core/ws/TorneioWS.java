package sm.core.ws;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.helper.Torneio_jogoHelper;

@RestController
@RequestMapping("/sm")
public class TorneioWS {

    @Autowired
    private Torneio_jogoHelper torneio_jogoHelper;

    @CrossOrigin
    @PutMapping("/loadAllGames")
    @ResponseBody
    public String loadAllGames() {

        // Carregar todos os jogos

        System.out.println("loadAllGames | Start");
        
        
        ArrayList<sm.core.data.Torneio_jogo> games = torneio_jogoHelper.loadAllGames();

        ObjectMapper mapper = new ObjectMapper();

        try {

            System.out.println("loadAllGames | End");
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(games);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("loadAllGames | Error End");
        return "";
    }

    @CrossOrigin
    @PutMapping("/saveMatch")
    @ResponseBody
    public String saveMatch(@RequestBody sm.core.data.Torneio_jogo match) {
        // Salvar o jogo
        System.out.println("saveMatch | Start");
        
        boolean result = torneio_jogoHelper.saveMatch(match);

        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println("saveMatch | End");
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
       System.out.println("saveMatch | Error End");

        return "false";
    }

    @CrossOrigin
    @PutMapping("/resetMatch")
    @ResponseBody
    public String resetMatch(@RequestBody sm.core.data.Torneio_jogo match) {
        // Reset o jogo
        System.out.println("resetMatch | Start");
        
        boolean result = torneio_jogoHelper.resetMatch(match.getId());

        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println("resetMatch | End");
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
       System.out.println("resetMatch | Error End");

        return "false";
    }
}
