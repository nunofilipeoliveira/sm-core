package sm.core.ws;

import java.util.ArrayList;

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

		System.out.println("loadEquipabyID | Start");
		System.out.println("loadEquipabyID | ID:" + id);

		
		EquipaData equipaData = equipaHelper.getEquipaID(Integer.valueOf(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("loadEquipabyID | End" + id);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(equipaData);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("loadEquipabyID | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEquipaLight/{id}")
	@ResponseBody
	public String loadEquipabyIDLight(@PathVariable String id) {

		// Carregar equipa

		System.out.println("loadEquipabyIDLight | Start");
		System.out.println("loadEquipabyIDLight | ID:" + id);

		
		EquipaData equipaData = equipaHelper.getEquipaIDLight(Integer.valueOf(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("loadEquipabyIDLight | End" + id);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(equipaData);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("loadEquipabyIDLight | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getJogadoreDisponiveis/{id}/{all}/{tenant_id}")
	@ResponseBody
	public String getJogadoresDisponiveis(@PathVariable String id, @PathVariable String all, @PathVariable String tenant_id) {

		// Carregar equipa

		System.out.println("getJogadoresDisponiveis | Start");
		System.out.println("getJogadoresDisponiveis | ID:" + id);
		System.out.println("getJogadoresDisponiveis | ID:" + tenant_id);

		ArrayList<ElementoSeleccao> jogadores = new ArrayList<ElementoSeleccao>();
		
		boolean allJogadores = false;
		if(all.equals("1")) {
			allJogadores=true;
		}

		
		jogadores = jogadorHelper.getJogadorDisponiveis(Integer.valueOf(id), allJogadores, Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getJogadoresDisponiveis | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogadores);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getJogadoresDisponiveis | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllJogadores/{tenant_id}")
	@ResponseBody
	public String getAllJogadores(@PathVariable String tenant_id) {

		// Carregar equipa

		System.out.println("getAllJogadores | Start");
		System.out.println("getAllJogadores | tenant_ID:" + tenant_id);

		ArrayList<ElementoSeleccao> jogadores = new ArrayList<ElementoSeleccao>();

		
		jogadores = jogadorHelper.getAllJogadorAtivo(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getAllJogadores | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogadores);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getAllJogadores | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllStaff/{tenant_id}")
	@ResponseBody
	public String getAllStaff(@PathVariable String tenant_id) {

		// Carregar equipa

		System.out.println("getAllStaff | Start");
		System.out.println("getAllStaff | tenant_ID:" + tenant_id);

		ArrayList<ElementoSeleccao> staffs = new ArrayList<ElementoSeleccao>();

		
		staffs = staffHelper.getAllStaffAtivo(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getAllStaff | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(staffs);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getAllStaff | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllEquipasEpocaAtual/{tenant_id}")
	@ResponseBody
	public String getAllEquipasEpocaAtual(@PathVariable String tenant_id) {

		// Carregar equipa

		System.out.println("getAllEquipasEpocaAtual | Start");
		System.out.println("getAllEquipasEpocaAtual | tenant_ID:" + tenant_id);

		ArrayList<EquipaData> equipas = new ArrayList<EquipaData>();

		
		equipas = equipaHelper.getAllEquipaLightEpocaAtual(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getAllEquipasEpocaAtual | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(equipas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getAllEquipasEpocaAtual | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEpocaAtual/{tenant_id}")
	@ResponseBody
	public String getEpocaAtual(@PathVariable String tenant_id) {

		// Carregar equipa

		System.out.println("getEpocaAtual | Start");
		System.out.println("getEpocaAtual | tenant_ID:" + tenant_id);

		EpocaData epocaAtual = null;

		
		epocaAtual = equipaHelper.getEpocaAtiva(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getEpocaAtual | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(epocaAtual);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getAllStaff | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/setEpocaAtual/{idepoca}/{tenant_id}")
	@ResponseBody
	public String setEpocaAtual(@PathVariable String idepoca, @PathVariable String tenant_id) {

		// Carregar equipa

		System.out.println("setEpocaAtual | Start");
		System.out.println("setEpocaAtual | idepoca:" + idepoca);
		System.out.println("setEpocaAtual | tenant_ID:" + tenant_id);

		Boolean sucesso = null;

		
		sucesso = equipaHelper.setEpocaAtual(Integer.valueOf(idepoca), Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("setEpocaAtual | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sucesso);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("setEpocaAtual | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/createEscalaoEpoca/{tenant_id}")
	@ResponseBody
	public String createEscalaoEpoca(@PathVariable String tenant_id,
			@RequestBody EscalaoEpocaData parmEscalaoEpocaData) {

		// Carregar equipa

		System.out.println("createEscalaoEpoca | Start");

		System.out.println("createEscalaoEpoca | tenant_ID:" + tenant_id);
		System.out.println("createEscalaoEpoca | parmEscalaoEpocaData:" + parmEscalaoEpocaData);

		Boolean sucesso = null;

		
		sucesso = equipaHelper.createEscalaoEpoca(parmEscalaoEpocaData, Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("createEscalaoEpoca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sucesso);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("createEscalaoEpoca | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/deleteEscalaoEpoca/{tenant_id}")
	@ResponseBody
	public String deleteEscalaoEpoca(@PathVariable String tenant_id,
			@RequestBody EscalaoEpocaData parmEscalaoEpocaData) {

		// Carregar equipa

		System.out.println("deleteEscalaoEpoca | Start");

		System.out.println("deleteEscalaoEpoca | tenant_ID:" + tenant_id);
		System.out.println("deleteEscalaoEpoca | parmEscalaoEpocaData:" + parmEscalaoEpocaData);

		Boolean sucesso = null;

		
		sucesso = equipaHelper.deleteEscalaoEpoca(parmEscalaoEpocaData, Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("deleteEscalaoEpoca | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sucesso);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("deleteEscalaoEpoca | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllEpocas/{tenant_id}")
	@ResponseBody
	public String getAllEpocas(@PathVariable String tenant_id) {

		// Carregar equipa

		System.out.println("getAllEpocas | Start");
		System.out.println("getAllEpocas | tenant_ID:" + tenant_id);

		ArrayList<EpocaData> epocas = null;

		
		epocas = equipaHelper.getAllEpocas(Integer.valueOf(tenant_id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getAllEpocas | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(epocas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getAllStaff | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEscaloes")
	@ResponseBody
	public String getEscaloes() {

		// Carregar equipa

		System.out.println("getEscaloes | Start");

		ArrayList<EscalaoData> escaloes = null;

		
		escaloes = equipaHelper.getAllEscaloes();

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getEscaloes | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(escaloes);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getEscaloes | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllStaffDisponivel/{tenant_id}/{idequipa}")
	@ResponseBody
	public String getAllStaff(@PathVariable String tenant_id, @PathVariable String idequipa) {

		// Carregar equipa

		System.out.println("getAllStaffDisponivel | Start");
		System.out.println("getAllStaffDisponivel | tenant_ID:" + tenant_id);

		ArrayList<ElementoSeleccao> staffs = new ArrayList<ElementoSeleccao>();

		
		staffs = staffHelper.getAllStaffAtivoDisponivel(Integer.valueOf(tenant_id), Integer.valueOf(idequipa));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getAllStaffDisponivel | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(staffs);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getAllStaffDisponivel | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/updateJogador/{idUtilizador}")
	@ResponseBody
	public String updateJogador(@PathVariable String idUtilizador, @RequestBody JogadorData jogadorData) {
		boolean resultado = false;
		System.out.println("PresencaWS | updateJogador | Start");
		System.out.println("PresencaWS | updateJogador | ID:" + idUtilizador);

		
		resultado = jogadorHelper.updateJogador(jogadorData, Integer.parseInt(idUtilizador));

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("PresencaWS | updateJogador | End");
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
		System.out.println("EquipaWS | equipaAddJogador | Start");
		System.out.println("EquipaWS | equipaAddJogador | Equipa:" + idEquipa);

		
		resultado = equipaHelper.addJogadorEquipa(Integer.parseInt(idEquipa), jogadorData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("EquipaWS | equipaAddJogador | End");
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
		System.out.println("EquipaWS | addStaffEquipa | Start");
		System.out.println("EquipaWS | addStaffEquipa | Equipa:" + idEquipa);
		System.out.println("EquipaWS | addStaffEquipa | Staff:" + staffData.getId());
		System.out.println("EquipaWS | addStaffEquipa | Tipo:" + staffData.getTipo());

		
		resultado = equipaHelper.addStaffEquipa(Integer.parseInt(idEquipa), staffData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("EquipaWS | addStaffEquipa | End");
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
		System.out.println("EquipaWS | addStaff | Start");

		
		resultado = staffHelper.addStaff(staffData, Integer.valueOf(tenant_id), Integer.valueOf(utilizador_id));

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("EquipaWS | addStaff | End");
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
		boolean resultado = false;
		System.out.println("EquipaWS | addJogador | Start");

		
		resultado = jogadorHelper.addJogador(jogadorData, Integer.valueOf(tenant_id), Integer.valueOf(utilizador_id));

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("EquipaWS | addJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@CrossOrigin
	@PutMapping("/equipa/removeJogadorEquipa/{idEquipa}")
	@ResponseBody
	public String removeJogadorEquipa(@PathVariable String idEquipa, @RequestBody JogadorData jogadorData) {
		boolean resultado = false;
		System.out.println("EquipaWS | removeJogadorEquipa | Start");
		System.out.println("EquipaWS | removeJogadorEquipa | Equipa:" + idEquipa);

		
		resultado = equipaHelper.removeJogadorEquipa(Integer.parseInt(idEquipa), jogadorData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("EquipaWS | removeJogadorEquipa | End");
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
		System.out.println("EquipaWS | removeStaffEquipa | Start");
		System.out.println("EquipaWS | removeStaffEquipa | Equipa:" + idEquipa);
		System.out.println("EquipaWS | removeStaffEquipa | Staff?id:" + staffData.getId());

		
		resultado = equipaHelper.removeStaffEquipa(Integer.parseInt(idEquipa), staffData);

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("EquipaWS | removeStaffEquipa | End");
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
		System.out.println("PresencaWS | updateStaff | Start");
		System.out.println("PresencaWS | updateStaff | ID:" + idUtilizador);

		
		resultado = staffHelper.updateStaff(staffData, Integer.parseInt(idUtilizador));

		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println("PresencaWS | updateStaff | End");
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

		System.out.println("loadJogadorbyID | Start");
		System.out.println("loadJogadorbyID | ID:" + id);

		
		JogadorData jogador = jogadorHelper.getJogadorbyID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("loadJogadorbyID | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jogador);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("loadJogadorbyID | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getStaff/{id}")
	@ResponseBody
	public String getStaffbyID(@PathVariable String id) {

		// Carregar equipa

		System.out.println("getStaffbyID | Start");
		System.out.println("getStaffbyID | ID:" + id);

		
		StaffData staff = staffHelper.getStaffByID(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getStaffbyID | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(staff);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getStaffbyID | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getFaltas/{id}")
	@ResponseBody
	public String getFaltasByJogador(@PathVariable String id) {

		// Carregar equipa

		System.out.println("getFaltasByJogador | Start");
		System.out.println("getFaltasByJogador | ID:" + id);

		
		ArrayList<FichaJogadorPresencasData> faltas = jogadorHelper.getFaltasByJogador(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getFaltasByJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(faltas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("loadJogadorbyID | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEscalaoByEquipa/{parmEquipaId}")
	@ResponseBody
	public String getEscalaoByEquipa(@PathVariable String parmEquipaId) {

		// Carregar equipa

		System.out.println("getEscalaoByEquipa | Start");
		System.out.println("getEscalaoByEquipa | parmEquipaId:" + parmEquipaId);

		
		String escalao = equipaHelper.getEscalaoByEquipa(Integer.parseInt(parmEquipaId));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getEscalaoByEquipa | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(escalao);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getEscalaoByEquipa | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getCountPresencas/{id}")
	@ResponseBody
	public String getPresencasByJogador(@PathVariable String id) {

		// Carregar equipa

		System.out.println("getPresencasByJogador | Start");
		System.out.println("getPresencasByJogador | ID:" + id);

		
		ArrayList<ContadorPresencaData> presencas = jogadorHelper.getPresencasByJogador(Integer.parseInt(id));

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getPresencasByJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(presencas);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("getPresencasByJogador | Error End");
		return "";
	}

}
