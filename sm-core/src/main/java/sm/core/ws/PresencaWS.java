package sm.core.ws;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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

	private static final Logger log = LoggerFactory.getLogger(PresencaWS.class);

	
	@Autowired
	private PresencaHelper presencaHelper;

	@CrossOrigin
	@PutMapping("/presenca/{parmTenantId}")
	public String createPresenca(@RequestBody PresencaData presencaData, @PathVariable String parmTenantId) {
		// Faz o login e obtem informação do utilizador

		boolean resultado = false;
		log.info("PresencaWS | createPresenca | Start");
		log.info("PresencaWS | createPresenca | Escalao:" + presencaData.getEscalao_descricao());
		log.info("PresencaWS | createPresenca | Dia:" + presencaData.getData());

	
		resultado = presencaHelper.createPresenca(presencaData, parmTenantId);

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("PresencaWS | createPresenca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error("PresencaWS | createPresenca | Error End");
		}

		return "";

	}

	@CrossOrigin
	@PutMapping("/updatepresenca/{idUtilizador}")
	@ResponseBody
	public String updatePresenca(@PathVariable String idUtilizador, @RequestBody PresencaData presencaData) {
		// Faz o login e obtem informação do utilizador

		boolean resultado = false;
		log.info("PresencaWS | updatePresenca | Start");
		log.info("PresencaWS | updatePresenca | IdUtilizador:" + idUtilizador);
		log.info("PresencaWS | updatePresenca | IdPresenca:" + presencaData.getId());

		
		resultado = presencaHelper.updatePresenca(presencaData, Integer.parseInt(idUtilizador));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("PresencaWS | updatePresenca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error("PresencaWS | updatePresenca | Error End");
		}

		return "";

	}

	@CrossOrigin
	@PutMapping("/getPresenca")
	@ResponseBody
	public String getPresenca() {
		// Faz o login e obtem informação do utilizador

		log.info("PresencaWS | getPresenca default() -> 1 | Start");

		ObjectMapper mapper = new ObjectMapper();
		PresencaData presenca = new PresencaData(1, 1, "15:00", 1, "Sub-7", "20240815", 1, "Nuno");

		presenca.addJogador(1, "Antonio", "", "");

		try {
			log.info("PresencaWS | getPresenca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presenca);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";

	}

	@CrossOrigin
	@GetMapping("/dashboard/getTotalTrainings/{parmEquipaID}")
	@ResponseBody
	public String getTotalTrainings(@PathVariable String parmEquipaID) {
		// Faz o login e obtem informação do utilizador

		log.info("PresencaWS | dashboard/totalTrainings | Start");
		log.info("PresencaWS | dashboard/totalTrainings | parmEquipaID:" + parmEquipaID);

		ObjectMapper mapper = new ObjectMapper();
		int numTreinos = presencaHelper.totalTrainings(Integer.parseInt(parmEquipaID));

		try {
			log.info("PresencaWS | dashboard/totalTrainings | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(numTreinos);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";

	}

	@CrossOrigin
	@GetMapping("/dashboard/getAverageAthletes/{parmEquipaID}")
	@ResponseBody
	public String getAverageAthletes(@PathVariable String parmEquipaID) {
		// Faz o login e obtem informação do utilizador

		log.info("PresencaWS | dashboard/getAverageAthletes | Start");
		log.info("PresencaWS | dashboard/getAverageAthletes | parmEquipaID:" + parmEquipaID);

		ObjectMapper mapper = new ObjectMapper();
		int numTreinos = presencaHelper.totalTrainings(Integer.parseInt(parmEquipaID));
		int numTotalAthletesPresent = presencaHelper.totalAthletesByEstado(Integer.parseInt(parmEquipaID), "Presente");
		int numMedia = 0;
		if (numTotalAthletesPresent > 0) {
			numMedia = numTotalAthletesPresent / numTreinos;
		}

		try {
			log.info("PresencaWS | dashboard/getAverageAthletes | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(numMedia);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";

	}

	@CrossOrigin
	@GetMapping("/dashboard/getAbsencePercentage/{parmEquipaID}")
	@ResponseBody
	public String getAbsencePercentage(@PathVariable String parmEquipaID) {
		// Faz o login e obtem informação do utilizador

		log.info("PresencaWS | dashboard/getAbsencePercentage | Start");
		log.info("PresencaWS | dashboard/getAbsencePercentage | parmEquipaID:" + parmEquipaID);

		ObjectMapper mapper = new ObjectMapper();
		

		int numTotalAthletesPresent = presencaHelper.totalAthletesByEstado(Integer.parseInt(parmEquipaID), "Presente");
		int numTotalAthletes_FJ = presencaHelper.totalAthletesByEstado(Integer.parseInt(parmEquipaID),
				"Ausente (Avisou)");
		int numTotalAthletes_FI = presencaHelper.totalAthletesByEstado(Integer.parseInt(parmEquipaID),
				"Ausente (Não Avisou)");
		int numTotalAthletes_FL = presencaHelper.totalAthletesByEstado(Integer.parseInt(parmEquipaID),
				"Lesão");
		double numMedia = 0;
		log.info(
				"PresencaWS | dashboard/getAbsencePercentage | numTotalAthletesPresent :" + numTotalAthletesPresent);
		log.info("PresencaWS | dashboard/getAbsencePercentage | numTotalAthletes_FJ :" + numTotalAthletes_FJ);
		log.info("PresencaWS | dashboard/getAbsencePercentage | numTotalAthletes_FI :" + numTotalAthletes_FI);
		log.info("PresencaWS | dashboard/getAbsencePercentage | numTotalAthletes_FL :" + numTotalAthletes_FL);

		int total = numTotalAthletesPresent + numTotalAthletes_FJ + numTotalAthletes_FI + numTotalAthletes_FL;

		if (numTotalAthletesPresent > 0) {
			numMedia = ((double) (numTotalAthletes_FJ + numTotalAthletes_FI + numTotalAthletes_FL) / total) * 100.0;
			numMedia = Math.round(numMedia * 100.0) / 100.0;

			log.info("PresencaWS | dashboard/getAbsencePercentage | numMedia :" + numMedia);
		}

		try {
			log.info("PresencaWS | dashboard/getAbsencePercentage | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(numMedia);

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

		log.info("getPresencaByDatas | Start");
		log.info("getPresencaByDatas | datas e id :" + datas);

		String[] tmpDatas = datas.split("_");

		ArrayList<PresencaData> presencas = presencaHelper.loadPresencasbyData(Integer.parseInt(tmpDatas[0]),
				Integer.parseInt(tmpDatas[1]), Integer.parseInt(tmpDatas[2]));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getPresencaByDatas | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presencas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error("getPresencaByDatas | Error End");
		}

		log.error("getPresencaByDatas | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/isPresencabyEquipaDataHora/{datas}")
	@ResponseBody
	public String isPresencabyEquipaDataHora(@PathVariable String datas) {

		// Carregar equipa

		log.info("isPresencabyEquipaDataHora | Start");
		log.info("isPresencabyEquipaDataHora | data e hora:" + datas);

		String[] tmpDatas = datas.split("_");

		boolean presencas = presencaHelper.isPresencasbyEquipaDataHora(Integer.parseInt(tmpDatas[0]),
				Integer.parseInt(tmpDatas[1]), tmpDatas[2]);

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("isPresencabyEquipaDataHora | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presencas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error("isPresencabyEquipaDataHora | Error End");
		}

		log.error("isPresencabyEquipaDataHora | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getPresencaById/{id}")
	@ResponseBody
	public String getPresencasbyId(@PathVariable String id) {

		// Carregar equipa

		log.info("getPresencasbyId | Start");
		log.info("getPresencasbyId | id:" + id);

		PresencaData presenca = presencaHelper.loadPresencasbyID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getPresencasbyId | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presenca);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error("getPresencasbyId | Error End");
		}

		log.error("getPresencasbyId | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getHistoricoById/{id}")
	@ResponseBody
	public String getHistoricobyId(@PathVariable String id) {

		// Carregar equipa

		log.info("getHistoricobyId | Start");
		log.info("getHistoricobyId | id:" + id);

		ArrayList<String> presenca = presencaHelper.loadHistoricoByID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getHistoricobyId | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presenca);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error("getHistoricobyId | Error End");
		}

		log.error("getHistoricobyId | Error End");
		return "";
	}

}
