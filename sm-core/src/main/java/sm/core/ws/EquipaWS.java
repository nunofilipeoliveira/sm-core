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

import sm.core.data.ContadorPresencaData;
import sm.core.data.EquipaData;
import sm.core.data.FichaJogadorPresencasData;
import sm.core.data.JogadorData;
import sm.core.data.JogadorSeleccao;
import sm.core.data.StaffData;
import sm.core.helper.EquipaHelper;
import sm.core.helper.JogadorHelper;
import sm.core.helper.StaffHelper;

@RestController
@RequestMapping("/sm")
public class EquipaWS {

	@CrossOrigin
	@PutMapping("/getEquipa/{id}")
	@ResponseBody
	public String loadEquipabyID(@PathVariable String id) {

		// Carregar equipa

		System.out.println("loadEquipabyID | Start");
		System.out.println("loadEquipabyID | ID:" + id);

		EquipaHelper equipaHelper = new EquipaHelper();
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

		EquipaHelper equipaHelper = new EquipaHelper();
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
	@PutMapping("/getJogadoreDisponiveis/{id}")
	@ResponseBody
	public String getJogadoresDisponiveis(@PathVariable String id) {

		// Carregar equipa

		System.out.println("getJogadoresDisponiveis | Start");
		System.out.println("getJogadoresDisponiveis | ID:" + id);

		ArrayList<JogadorSeleccao> jogadores = new ArrayList<JogadorSeleccao>();

		JogadorHelper jogadorHelper = new JogadorHelper();
		jogadores = jogadorHelper.getJogadorDisponiveis(Integer.valueOf(id));

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
	@PutMapping("/updateJogador/{idUtilizador}")
	@ResponseBody
	public String updateJogador(@PathVariable String idUtilizador, @RequestBody JogadorData jogadorData) {
		boolean resultado = false;
		System.out.println("PresencaWS | updateJogador | Start");
		System.out.println("PresencaWS | updateJogador | ID:" + idUtilizador);

		JogadorHelper jogadorHelper = new JogadorHelper();
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
	@PutMapping("/updateStaff/{idUtilizador}")
	@ResponseBody
	public String updateStaff(@PathVariable String idUtilizador, @RequestBody StaffData staffData) {
		boolean resultado = false;
		System.out.println("PresencaWS | updateStaff | Start");
		System.out.println("PresencaWS | updateStaff | ID:" + idUtilizador);

		StaffHelper staffHelper = new StaffHelper();
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

		JogadorHelper jogadorHelper = new JogadorHelper();
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

		StaffHelper staffHelper = new StaffHelper();
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

		JogadorHelper jogadorHelper = new JogadorHelper();
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
	@PutMapping("/getCountPresencas/{id}")
	@ResponseBody
	public String getPresencasByJogador(@PathVariable String id) {

		// Carregar equipa

		System.out.println("getPresencasByJogador | Start");
		System.out.println("getPresencasByJogador | ID:" + id);

		JogadorHelper jogadorHelper = new JogadorHelper();
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
