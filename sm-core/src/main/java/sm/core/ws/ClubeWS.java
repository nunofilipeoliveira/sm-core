package sm.core.ws;

import java.util.ArrayList;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.data.ClubeData;
import sm.core.helper.ClubeHelper;

@RestController
@RequestMapping("/sm")
public class ClubeWS {

    @CrossOrigin
    @PutMapping("/getClube/{id}")
    @ResponseBody
    public String loadClubeByID(@PathVariable String id) {

        // Carregar clube

        System.out.println("getClube | Start");
        System.out.println("getClube | ID:" + id);

        ClubeHelper clubeHelper = new ClubeHelper();
        ClubeData clubeData = clubeHelper.getClubebyId(Integer.valueOf(id));

        ObjectMapper mapper = new ObjectMapper();

        try {

            System.out.println("loadEquipabyID | End" + id);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(clubeData);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("getClube | Error End");
        return "";
    }



    @CrossOrigin
    @PutMapping("/getAllClubes")
    @ResponseBody
    public String loadAllClubes() {

        // Carregar todos os clubes

        System.out.println("getAllClubes | Start");

        ClubeHelper clubeHelper = new ClubeHelper();
        ArrayList<ClubeData> clubes = clubeHelper.getAllClubes();

        ObjectMapper mapper = new ObjectMapper();

        try {

            System.out.println("getAllClubes | End");
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(clubes);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("getAllClubes | Error End");
        return "";
    }


    @CrossOrigin
    @PutMapping("/updateClube")
    @ResponseBody
    public String updateClube(@RequestBody ClubeData clubeData) {

        // Atualizar clube

        System.out.println("updateClube | Start");
        System.out.println("updateClube | ID:" + clubeData.getId());

        ClubeHelper clubeHelper = new ClubeHelper();
        boolean success = clubeHelper.updateClube(clubeData);

        ObjectMapper mapper = new ObjectMapper();

        try {

            System.out.println("updateClube | End");
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(success);

            // return mapper.writeValueAsString(loginData);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println("updateClube | Error End");
        return "";
    }

       



}
