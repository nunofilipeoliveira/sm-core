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

import sm.core.data.ContadorPresencaData;
import sm.core.data.ElementoSeleccao;
import sm.core.data.EpocaData;
import sm.core.data.EquipaData;
import sm.core.data.EscalaoData;
import sm.core.data.EscalaoEpocaData;
import sm.core.data.FichaJogadorPresencasData;
import sm.core.data.JogadorData;
import sm.core.data.StaffData;
import sm.core.helper.EquipaHelper;
import sm.core.helper.JogadorHelper;
import sm.core.helper.StaffHelper;

@RestController
@RequestMapping("/sm")
public class EquipaWS {

	private static final Logger log = LoggerFactory.getLogger(EquipaWS.class);

	@Autowired
	private EquipaHelper equipaHelper;

	@Autowired
	private JogadorHelper jogadorHelper;

	@Autowired
	private StaffHelper staffHelper;

	@CrossOrigin
	@PutMapping("/getEquipa/{id}")
	@ResponseBody
	public String loadEquipabyID(@PathVariable String id) {

		// Carregar equipa

		log.info("loadEquipabyID | Start");
		log.info("loadEquipabyID | ID:" + id);

		
		EquipaData equipaData = equipaHelper.getEquipaID(Integer.valueOf(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("loadEquipabyID | End" + id);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(equipaData);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("loadEquipabyID | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEquipaLight/{id}")
	@ResponseBody
	public String loadEquipabyIDLight(@PathVariable String id) {

		// Carregar equipa

		log.info("loadEquipabyIDLight | Start");
		log.info("loadEquipabyIDLight | ID:" + id);

		
		EquipaData equipaData = equipaHelper.getEquipaIDLight(Integer.valueOf(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("loadEquipabyIDLight | End" + id);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(equipaData);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("loadEquipabyIDLight | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getJogadoreDisponiveis/{id}/{all}/{tenant_id}")
	@ResponseBody
	public String getJogadoresDisponiveis(@PathVariable String id, @PathVariable String all, @PathVariable String tenant_id) {

		// Carregar equipa

		log.info("getJogadoresDisponiveis | Start");
		log.info("getJogadoresDisponiveis | ID:" + id);
		log.info("getJogadoresDisponiveis | ID:" + tenant_id);

		ArrayList<ElementoSeleccao> jogadores = new ArrayList<ElementoSeleccao>();
		
		boolean allJogadores = false;
		if(all.equals("1")) {
			allJogadores=true;
		}

		
		jogadores = jogadorHelper.getJogadorDisponiveis(Integer.valueOf(id), allJogadores, Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getJogadoresDisponiveis | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogadores);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getJogadoresDisponiveis | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllJogadores/{tenant_id}")
	@ResponseBody
	public String getAllJogadores(@PathVariable String tenant_id) {

		// Carregar equipa

		log.info("getAllJogadores | Start");
		log.info("getAllJogadores | tenant_ID:" + tenant_id);

		ArrayList<ElementoSeleccao> jogadores = new ArrayList<ElementoSeleccao>();

		
		jogadores = jogadorHelper.getAllJogadorAtivo(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getAllJogadores | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogadores);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getAllJogadores | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllStaff/{tenant_id}")
	@ResponseBody
	public String getAllStaff(@PathVariable String tenant_id) {

		// Carregar equipa

		log.info("getAllStaff | Start");
		log.info("getAllStaff | tenant_ID:" + tenant_id);

		ArrayList<ElementoSeleccao> staffs = new ArrayList<ElementoSeleccao>();

		
		staffs = staffHelper.getAllStaffAtivo(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getAllStaff | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(staffs);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getAllStaff | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllEquipasEpocaAtual/{tenant_id}")
	@ResponseBody
	public String getAllEquipasEpocaAtual(@PathVariable String tenant_id) {

		// Carregar equipa

		log.info("getAllEquipasEpocaAtual | Start");
		log.info("getAllEquipasEpocaAtual | tenant_ID:" + tenant_id);

		ArrayList<EquipaData> equipas = new ArrayList<EquipaData>();

		
		equipas = equipaHelper.getAllEquipaLightEpocaAtual(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getAllEquipasEpocaAtual | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(equipas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getAllEquipasEpocaAtual | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEpocaAtual/{tenant_id}")
	@ResponseBody
	public String getEpocaAtual(@PathVariable String tenant_id) {

		// Carregar equipa

		log.info("getEpocaAtual | Start");
		log.info("getEpocaAtual | tenant_ID:" + tenant_id);

		EpocaData epocaAtual = null;

		
		epocaAtual = equipaHelper.getEpocaAtiva(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getEpocaAtual | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(epocaAtual);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getEpocaAtual | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/setEpocaAtual/{idepoca}/{tenant_id}")
	@ResponseBody
	public String setEpocaAtual(@PathVariable String idepoca, @PathVariable String tenant_id) {

		// Carregar equipa

		log.info("setEpocaAtual | Start");
		log.info("setEpocaAtual | idepoca:" + idepoca);
		log.info("setEpocaAtual | tenant_ID:" + tenant_id);

		Boolean sucesso = null;

		
		sucesso = equipaHelper.setEpocaAtual(Integer.valueOf(idepoca), Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("setEpocaAtual | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sucesso);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("setEpocaAtual | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/createEscalaoEpoca/{tenant_id}")
	@ResponseBody
	public String createEscalaoEpoca(@PathVariable String tenant_id,
			@RequestBody EscalaoEpocaData parmEscalaoEpocaData) {

		// Carregar equipa

		log.info("createEscalaoEpoca | Start");

		log.info("createEscalaoEpoca | tenant_ID:" + tenant_id);
		log.info("createEscalaoEpoca | parmEscalaoEpocaData:" + parmEscalaoEpocaData);

		Boolean sucesso = null;

		
		sucesso = equipaHelper.createEscalaoEpoca(parmEscalaoEpocaData, Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("createEscalaoEpoca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sucesso);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("createEscalaoEpoca | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/deleteEscalaoEpoca/{tenant_id}")
	@ResponseBody
	public String deleteEscalaoEpoca(@PathVariable String tenant_id,
			@RequestBody EscalaoEpocaData parmEscalaoEpocaData) {

		// Carregar equipa

		log.info("deleteEscalaoEpoca | Start");

		log.info("deleteEscalaoEpoca | tenant_ID:" + tenant_id);
		log.info("deleteEscalaoEpoca | parmEscalaoEpocaData:" + parmEscalaoEpocaData);

		Boolean sucesso = null;

		
		sucesso = equipaHelper.deleteEscalaoEpoca(parmEscalaoEpocaData, Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("deleteEscalaoEpoca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sucesso);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("deleteEscalaoEpoca | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllEpocas/{tenant_id}")
	@ResponseBody
	public String getAllEpocas(@PathVariable String tenant_id) {

		// Carregar equipa

		log.info("getAllEpocas | Start");
		log.info("getAllEpocas | tenant_ID:" + tenant_id);

		ArrayList<EpocaData> epocas = null;

		
		epocas = equipaHelper.getAllEpocas(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getAllEpocas | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(epocas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getAllEpocas | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEscaloes")
	@ResponseBody
	public String getEscaloes() {

		// Carregar equipa

		log.info("getEscaloes | Start");

		ArrayList<EscalaoData> escaloes = null;

		
		escaloes = equipaHelper.getAllEscaloes();

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getEscaloes | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(escaloes);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getEscaloes | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllStaffDisponivel/{tenant_id}/{idequipa}")
	@ResponseBody
	public String getAllStaff(@PathVariable String tenant_id, @PathVariable String idequipa) {

		// Carregar equipa

		log.info("getAllStaffDisponivel | Start");
		log.info("getAllStaffDisponivel | tenant_ID:" + tenant_id);

		ArrayList<ElementoSeleccao> staffs = new ArrayList<ElementoSeleccao>();

		
		staffs = staffHelper.getAllStaffAtivoDisponivel(Integer.valueOf(tenant_id), Integer.valueOf(idequipa));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getAllStaffDisponivel | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(staffs);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getAllStaffDisponivel | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/updateJogador/{idUtilizador}")
	@ResponseBody
	public String updateJogador(@PathVariable String idUtilizador, @RequestBody JogadorData jogadorData) {
		boolean resultado = false;
		log.info("updateJogador | Start");
		log.info("updateJogador | ID:" + idUtilizador);

		
		resultado = jogadorHelper.updateJogador(jogadorData, Integer.parseInt(idUtilizador));

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("updateJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/equipa/addJogadorEquipa/{idEquipa}")
	@ResponseBody
	public String addJogadorEquipa(@PathVariable String idEquipa, @RequestBody JogadorData jogadorData) {
		boolean resultado = false;
		log.info("equipaAddJogador | Start");
		log.info("equipaAddJogador | Equipa:" + idEquipa);

		
		resultado = equipaHelper.addJogadorEquipa(Integer.parseInt(idEquipa), jogadorData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("equipaAddJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/equipa/addStaffEquipa/{idEquipa}")
	@ResponseBody
	public String addStaffEquipa(@PathVariable String idEquipa, @RequestBody StaffData staffData) {
		boolean resultado = false;
		log.info("addStaffEquipa | Start");
		log.info("addStaffEquipa | Equipa:" + idEquipa);
		log.info("addStaffEquipa | Staff:" + staffData.getId());
		log.info("addStaffEquipa | Tipo:" + staffData.getTipo());

		
		resultado = equipaHelper.addStaffEquipa(Integer.parseInt(idEquipa), staffData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("addStaffEquipa | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/addStaff/{tenant_id}/{utilizador_id}")
	@ResponseBody
	public String addStaff(@PathVariable String tenant_id, @PathVariable String utilizador_id,
			@RequestBody StaffData staffData) {
		boolean resultado = false;
		log.info("addStaff | Start");

		
		resultado = staffHelper.addStaff(staffData, Integer.valueOf(tenant_id), Integer.valueOf(utilizador_id));

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("addStaff | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/addJogador/{tenant_id}/{utilizador_id}")
	@ResponseBody
	public String addJogador(@PathVariable String tenant_id, @PathVariable String utilizador_id,
			@RequestBody JogadorData jogadorData) {
		int jogadorId = 0;
		log.info("addJogador | Start");


		jogadorId = jogadorHelper.addJogador(jogadorData, Integer.valueOf(tenant_id), Integer.valueOf(utilizador_id));

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("addJogador | End - Jogador criado com ID: " + jogadorId);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogadorId);
		} catch (JsonProcessingException e) {
			log.error("addJogador | Error serializing response");
			return "0";
		}
	}

	@CrossOrigin
	@PutMapping("/equipa/removeJogadorEquipa/{idEquipa}")
	@ResponseBody
	public String removeJogadorEquipa(@PathVariable String idEquipa, @RequestBody JogadorData jogadorData) {
		boolean resultado = false;
		log.info("removeJogadorEquipa | Start");
		log.info("removeJogadorEquipa | Equipa:" + idEquipa);

		
		resultado = equipaHelper.removeJogadorEquipa(Integer.parseInt(idEquipa), jogadorData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("removeJogadorEquipa | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/equipa/removeStaffEquipa/{idEquipa}")
	@ResponseBody
	public String removeStaffEquipa(@PathVariable String idEquipa, @RequestBody StaffData staffData) {
		boolean resultado = false;
		log.info("removeStaffEquipa | Start");
		log.info("removeStaffEquipa | Equipa:" + idEquipa);
		log.info("removeStaffEquipa | Staff?id:" + staffData.getId());

		
		resultado = equipaHelper.removeStaffEquipa(Integer.parseInt(idEquipa), staffData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("removeStaffEquipa | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/updateStaff/{idUtilizador}")
	@ResponseBody
	public String updateStaff(@PathVariable String idUtilizador, @RequestBody StaffData staffData) {
		boolean resultado = false;
		log.info("updateStaff | Start");
		log.info("updateStaff | ID:" + idUtilizador);

		
		resultado = staffHelper.updateStaff(staffData, Integer.parseInt(idUtilizador));

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("updateStaff | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/getJogador/{id}")
	@ResponseBody
	public String loadJogadorbyID(@PathVariable String id) {

		// Carregar equipa

		log.info("loadJogadorbyID | Start");
		log.info("loadJogadorbyID | ID:" + id);

		
		JogadorData jogador = jogadorHelper.getJogadorbyID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("loadJogadorbyID | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogador);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("loadJogadorbyID | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getStaff/{id}")
	@ResponseBody
	public String getStaffbyID(@PathVariable String id) {

		// Carregar equipa

		log.info("getStaffbyID | Start");
		log.info("getStaffbyID | ID:" + id);

		
		StaffData staff = staffHelper.getStaffByID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getStaffbyID | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(staff);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getStaffbyID | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getFaltas/{id}")
	@ResponseBody
	public String getFaltasByJogador(@PathVariable String id) {

		// Carregar equipa

		log.info("getFaltasByJogador | Start");
		log.info("getFaltasByJogador | ID:" + id);

		
		ArrayList<FichaJogadorPresencasData> faltas = jogadorHelper.getFaltasByJogador(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getFaltasByJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(faltas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getFaltasByJogador | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEscalaoByEquipa/{parmEquipaId}")
	@ResponseBody
	public String getEscalaoByEquipa(@PathVariable String parmEquipaId) {

		// Carregar equipa

		log.info("getEscalaoByEquipa | Start");
		log.info("getEscalaoByEquipa | parmEquipaId:" + parmEquipaId);

		
		String escalao = equipaHelper.getEscalaoByEquipa(Integer.parseInt(parmEquipaId));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getEscalaoByEquipa | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(escalao);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getEscalaoByEquipa | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getCountPresencas/{id}")
	@ResponseBody
	public String getPresencasByJogador(@PathVariable String id) {

		// Carregar equipa

		log.info("getPresencasByJogador | Start");
		log.info("getPresencasByJogador | ID:" + id);

		
		ArrayList<ContadorPresencaData> presencas = jogadorHelper.getPresencasByJogador(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getPresencasByJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presencas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getPresencasByJogador | Error End");
		return "";
	}

}
