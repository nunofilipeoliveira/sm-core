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

import sm.core.data.PresencaData;
import sm.core.helper.PresencaHelper;

@RestController
@RequestMapping("/sm")
public class PresencaWS {

	@CrossOrigin
	@PutMapping("/presenca")
	@ResponseBody
	public String createPresenca(@RequestBody PresencaData presencaData) {
		// Faz o login e obtem informação do utilizador

		boolean resultado = false;
		System.out.println("PresencaWS | createPresenca | Start");
		System.out.println("PresencaWS | createPresenca | Escalao:" + presencaData.getEscalao_descricao());
		System.out.println("PresencaWS | createPresenca | Dia:" + presencaData.getData());

		PresencaHelper presencaHelper = new PresencaHelper();
		resultado = presencaHelper.createPresenca(presencaData);

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("PresencaWS | createPresenca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("PresencaWS | createPresenca | Error End");
		}

		return "";

	}

	@CrossOrigin
	@PutMapping("/updatepresenca/{idUtilizador}")
	@ResponseBody
	public String updatePresenca(@PathVariable String idUtilizador, @RequestBody PresencaData presencaData) {
		// Faz o login e obtem informação do utilizador

		boolean resultado = false;
		System.out.println("PresencaWS | updatePresenca | Start");
		System.out.println("PresencaWS | updatePresenca | IdUtilizador:" + idUtilizador);
		System.out.println("PresencaWS | updatePresenca | IdPresenca:" + presencaData.getId());

		PresencaHelper presencaHelper = new PresencaHelper();
		resultado = presencaHelper.updatePresenca(presencaData, Integer.parseInt(idUtilizador));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("PresencaWS | updatePresenca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("PresencaWS | updatePresenca | Error End");
		}

		return "";

	}

	@CrossOrigin
	@PutMapping("/getPresenca")
	@ResponseBody
	public String getPresenca() {
		// Faz o login e obtem informação do utilizador

		System.out.println("PresencaWS | getPresenca default() -> 1 | Start");

		ObjectMapper mapper = new ObjectMapper();
		PresencaData presenca = new PresencaData(1, 1, "15:00", 1, "Sub-7", "20240815", 1, "Nuno");

		presenca.addJogador(1, "Antonio", "", "");

		try {
			System.out.println("PresencaWS | getPresenca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presenca);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";

	}

	@CrossOrigin
	@PutMapping("/getPresencaByDatas/{datas}")
	@ResponseBody
	public String getPresencasbyDatasID(@PathVariable String datas) {

		// Carregar equipa

		System.out.println("getPresencaByDatas | Start");
		System.out.println("getPresencaByDatas | datas e id :" + datas);

		String[] tmpDatas = datas.split("_");

		PresencaHelper presencaHelper = new PresencaHelper();
		ArrayList<PresencaData> presencas = presencaHelper.loadPresencasbyData(Integer.parseInt(tmpDatas[0]),
				Integer.parseInt(tmpDatas[1]), Integer.parseInt(tmpDatas[2]));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getPresencaByDatas | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presencas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("getPresencaByDatas | Error End");
		}

		System.out.println("getPresencaByDatas | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/isPresencabyEquipaDataHora/{datas}")
	@ResponseBody
	public String isPresencabyEquipaDataHora(@PathVariable String datas) {

		// Carregar equipa

		System.out.println("isPresencabyEquipaDataHora | Start");
		System.out.println("isPresencabyEquipaDataHora | data e hora:" + datas);

		String[] tmpDatas = datas.split("_");

		PresencaHelper presencaHelper = new PresencaHelper();
		boolean presencas = presencaHelper.isPresencasbyEquipaDataHora(Integer.parseInt(tmpDatas[0]),
				Integer.parseInt(tmpDatas[1]), tmpDatas[2]);

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("isPresencabyEquipaDataHora | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presencas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("isPresencabyEquipaDataHora | Error End");
		}

		System.out.println("isPresencabyEquipaDataHora | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getPresencaById/{id}")
	@ResponseBody
	public String getPresencasbyId(@PathVariable String id) {

		// Carregar equipa

		System.out.println("getPresencasbyId | Start");
		System.out.println("getPresencasbyId | id:" + id);

		PresencaHelper presencaHelper = new PresencaHelper();
		PresencaData presenca = presencaHelper.loadPresencasbyID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getPresencasbyId | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presenca);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("getPresencasbyId | Error End");
		}

		System.out.println("getPresencasbyId | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getHistoricoById/{id}")
	@ResponseBody
	public String getHistoricobyId(@PathVariable String id) {

		// Carregar equipa

		System.out.println("getHistoricobyId | Start");
		System.out.println("getHistoricobyId | id:" + id);

		PresencaHelper presencaHelper = new PresencaHelper();
		ArrayList<String> presenca = presencaHelper.loadHistoricoByID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getHistoricobyId | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presenca);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("getHistoricobyId | Error End");
		}

		System.out.println("getHistoricobyId | Error End");
		return "";
	}

}
