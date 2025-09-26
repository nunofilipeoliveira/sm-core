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

import sm.core.data.JogoData;
import sm.core.helper.JogoHelper;

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
    
}
