package sm.core.ws;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import sm.core.data.ContadorPresencaData;
import sm.core.data.ElementoSeleccao;
import sm.core.data.EpocaData;
import sm.core.data.EquipaData;
import sm.core.data.EscalaoData;
import sm.core.data.EscalaoEpocaData;
import sm.core.data.FichaJogadorPresencasData;
import sm.core.data.HistoricoJogadorData;
import sm.core.data.JogadorData;
import sm.core.data.StaffData;
import jakarta.servlet.http.HttpServletRequest;
import sm.core.data.EpocaHistoricoData;
import sm.core.data.JogoData;
import sm.core.data.PresencaData;
import sm.core.data.PresencaJogadorData;
import sm.core.data.PresencaStaffData;
import sm.core.helper.AutorizacaoHelper;
import sm.core.helper.EquipaHelper;
import sm.core.helper.JogadorHelper;
import sm.core.helper.StaffHelper;
import sm.core.security.AcessoNegadoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;
import sm.core.security.TipoRecurso;

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

	@Autowired
	private AutorizacaoHelper autorizacaoHelper;

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

	@PutMapping("/deleteEscalaoEpoca/{tenant_id}")
	@ResponseBody
	public String deleteEscalaoEpoca(@PathVariable String tenant_id,
			@RequestBody EscalaoEpocaData parmEscalaoEpocaData, HttpServletRequest request) {

		autorizacaoHelper.verificaCorpo(request, TipoRecurso.EQUIPA, parmEscalaoEpocaData.getId_escalao_epoca());

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

	@PutMapping("/updateJogador/{idUtilizador}")
	@ResponseBody
	public String updateJogador(@PathVariable String idUtilizador, @RequestBody JogadorData jogadorData,
			HttpServletRequest request) {
		boolean resultado = false;
		log.info("updateJogador | Start");
		log.info("updateJogador | ID:" + idUtilizador);

		Identidade identidade = Identidade.de(request);
		if (identidade != null && identidade.isAtleta()) {
			// ATLETA: so a propria ficha e so os campos da lista branca (Q4)
			if (identidade.getIdJogador() == null || jogadorData.getId() != identidade.getIdJogador()) {
				throw new AcessoNegadoException();
			}
			JogadorData gravado = jogadorHelper.getJogadorbyID(identidade.getIdJogador());
			if (gravado == null) {
				throw new AcessoNegadoException();
			}
			copiaListaBrancaAtleta(jogadorData, gravado);
			resultado = jogadorHelper.updateJogador(gravado, identidade.getIdUtilizador());
		} else {
			autorizacaoHelper.verificaCorpo(request, TipoRecurso.JOGADOR, jogadorData.getId());
			resultado = jogadorHelper.updateJogador(jogadorData, Integer.parseInt(idUtilizador));
		}

		ObjectMapper mapper = new ObjectMapper();
		try {
			log.info("updateJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
		}
		return null;
	}

	@PutMapping("/equipa/addJogadorEquipa/{idEquipa}")
	@ResponseBody
	public String addJogadorEquipa(@PathVariable String idEquipa, @RequestBody JogadorData jogadorData,
			HttpServletRequest request) {
		autorizacaoHelper.verificaCorpo(request, TipoRecurso.JOGADOR, jogadorData.getId());
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

	@PutMapping("/equipa/addStaffEquipa/{idEquipa}")
	@ResponseBody
	public String addStaffEquipa(@PathVariable String idEquipa, @RequestBody StaffData staffData,
			HttpServletRequest request) {
		autorizacaoHelper.verificaCorpo(request, TipoRecurso.STAFF, staffData.getId());
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

	@PutMapping("/addStaff/{tenant_id}/{utilizador_id}")
	@ResponseBody
	public String addStaff(@PathVariable String tenant_id, @PathVariable String utilizador_id,
			@RequestBody StaffData staffData) {
		boolean resultado = false;
		log.info("addStaff | Start");

		// staff.licença e decimal(10,0): texto livre dava erro de SQL
		String licenca = staffData.getLicenca();
		if (licenca != null && !licenca.isEmpty() && !licenca.matches("\\d{1,10}")) {
			throw new PedidoInvalidoException("A licença tem de ser numérica (até 10 dígitos).");
		}

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

	@PutMapping("/addJogador/{tenant_id}/{utilizador_id}")
	@ResponseBody
	public String addJogador(@PathVariable String tenant_id, @PathVariable String utilizador_id,
			@RequestBody JogadorData jogadorData) {
		int jogadorId = 0;
		log.info("addJogador | Start");

		// jogador.nome e varchar(30). NIF e licenca sao int: o Jackson ja recusa nao numericos (ver handleCorpoInvalido)
		String nome = jogadorData.getNome();
		if (nome == null || nome.isBlank()) {
			throw new PedidoInvalidoException("O nome é obrigatório.");
		}
		if (nome.codePointCount(0, nome.length()) > 30) {
			throw new PedidoInvalidoException("O nome não pode ter mais de 30 caracteres.");
		}

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

	/**
	 * Corpo JSON que o Jackson nao consegue ler (ex.: NIF ou licenca do jogador com letras ou espacos).
	 * Devolve 400 no mesmo formato do GlobalExceptionHandler, em vez da pagina de erro do Spring.
	 * So se aplica aos endpoints deste controller.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> handleCorpoInvalido(HttpMessageNotReadableException e) {
		String mensagem = "O corpo do pedido é inválido.";
		if (e.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
			String campo = mie.getPath().get(mie.getPath().size() - 1).getFieldName();
			Class<?> tipo = mie.getTargetType();
			boolean numerico = tipo != null && (tipo.isPrimitive() || Number.class.isAssignableFrom(tipo));
			if (campo != null) {
				mensagem = numerico ? "O campo " + campo + " tem de ser numérico."
						: "O campo " + campo + " tem um valor inválido.";
			}
		}
		// sem o valor recebido no log (pode ser um dado pessoal)
		log.info("handleCorpoInvalido | pedido invalido: {}", mensagem);
		Map<String, String> corpo = new LinkedHashMap<>();
		corpo.put("error", "pedido_invalido");
		corpo.put("message", mensagem);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(corpo);
	}

	@PutMapping("/equipa/removeJogadorEquipa/{idEquipa}")
	@ResponseBody
	public String removeJogadorEquipa(@PathVariable String idEquipa, @RequestBody JogadorData jogadorData,
			HttpServletRequest request) {
		autorizacaoHelper.verificaCorpo(request, TipoRecurso.JOGADOR, jogadorData.getId());
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

	@PutMapping("/equipa/removeStaffEquipa/{idEquipa}")
	@ResponseBody
	public String removeStaffEquipa(@PathVariable String idEquipa, @RequestBody StaffData staffData,
			HttpServletRequest request) {
		autorizacaoHelper.verificaCorpo(request, TipoRecurso.STAFF, staffData.getId());
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

	@PutMapping("/updateStaff/{idUtilizador}")
	@ResponseBody
	public String updateStaff(@PathVariable String idUtilizador, @RequestBody StaffData staffData,
			HttpServletRequest request) {
		autorizacaoHelper.verificaCorpo(request, TipoRecurso.STAFF, staffData.getId());
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

	@PutMapping("/getJogador/{id}")
	@ResponseBody
	public String loadJogadorbyID(@PathVariable String id, HttpServletRequest request) {

		// Carregar equipa

		log.info("loadJogadorbyID | Start");
		log.info("loadJogadorbyID | ID:" + id);

		
		JogadorData jogador = jogadorHelper.getJogadorbyID(Integer.parseInt(id));
		if (jogador != null && isAtleta(request)) {
			jogador.setObservacoes(null);
		}

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

	@PutMapping("/getFaltas/{id}")
	@ResponseBody
	public String getFaltasByJogador(@PathVariable String id, HttpServletRequest request) {

		// Carregar equipa

		log.info("getFaltasByJogador | Start");
		log.info("getFaltasByJogador | ID:" + id);

		
		ArrayList<FichaJogadorPresencasData> faltas = jogadorHelper.getFaltasByJogador(Integer.parseInt(id));
		if (faltas != null && isAtleta(request)) {
			for (FichaJogadorPresencasData falta : faltas) {
				falta.setMotivo(null);
			}
		}

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

	@PutMapping("/getHistoricobyJogador/{idJogador}/{tenant_id}")
	@ResponseBody
	public String getHistoricobyJogador(@PathVariable String idJogador, @PathVariable String tenant_id,
			HttpServletRequest request) {

		// Carregar histórico do jogador

		log.info("getHistoricobyJogador | Start");
		log.info("getHistoricobyJogador | ID Jogador:" + idJogador);
		log.info("getHistoricobyJogador | tenant_ID:" + tenant_id);

		
		HistoricoJogadorData historico = equipaHelper.getHistoricobyJogador(Integer.parseInt(idJogador), Integer.parseInt(tenant_id));
		if (historico != null && isAtleta(request)) {
			limpaHistoricoAtleta(historico);
		}

		ObjectMapper mapper = new ObjectMapper();

		try {

			log.info("getHistoricobyJogador | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(historico);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.error("getHistoricobyJogador | Error End");
		return "";
	}

	private static boolean isAtleta(HttpServletRequest request) {
		Identidade identidade = Identidade.de(request);
		return identidade != null && identidade.isAtleta();
	}

	/** Q5: o atleta nao ve o motivo das faltas nos treinos nem o obs dos jogadores nos jogos. */
	private static void limpaHistoricoAtleta(HistoricoJogadorData historico) {
		if (historico.getEpocas() == null) {
			return;
		}
		for (EpocaHistoricoData epoca : historico.getEpocas()) {
			if (epoca.getTreinos() != null) {
				for (PresencaData treino : epoca.getTreinos()) {
					if (treino.getJogadoresPresenca() != null) {
						for (PresencaJogadorData jogador : treino.getJogadoresPresenca()) {
							jogador.setMotivo(null);
						}
					}
					if (treino.getStaffPresenca() != null) {
						for (PresencaStaffData staff : treino.getStaffPresenca()) {
							staff.setMotivo(null);
						}
					}
				}
			}
			if (epoca.getJogos() != null) {
				for (JogoData jogo : epoca.getJogos()) {
					AutorizacaoHelper.limpaObsJogadores(jogo);
				}
			}
		}
	}

	/** Q4: campos da ficha que o atleta pode alterar. Os restantes ficam como estao gravados. */
	private static void copiaListaBrancaAtleta(JogadorData pedido, JogadorData gravado) {
		if (pedido.getEmail() != null) {
			gravado.setEmail(pedido.getEmail());
		}
		if (pedido.getTelemovel() != null) {
			gravado.setTelemovel(pedido.getTelemovel());
		}
		if (pedido.getPai_nome() != null) {
			gravado.setPai_nome(pedido.getPai_nome());
		}
		if (pedido.getPai_email() != null) {
			gravado.setPai_email(pedido.getPai_email());
		}
		if (pedido.getPai_telemovel() != null) {
			gravado.setPai_telemovel(pedido.getPai_telemovel());
		}
		if (pedido.getMae_nome() != null) {
			gravado.setMae_nome(pedido.getMae_nome());
		}
		if (pedido.getMae_email() != null) {
			gravado.setMae_email(pedido.getMae_email());
		}
		if (pedido.getMae_telemovel() != null) {
			gravado.setMae_telemovel(pedido.getMae_telemovel());
		}
		if (pedido.getMorada() != null) {
			gravado.setMorada(pedido.getMorada());
		}
		if (pedido.getCidade() != null) {
			gravado.setCidade(pedido.getCidade());
		}
		if (pedido.getCodigo_postal() != null) {
			gravado.setCodigo_postal(pedido.getCodigo_postal());
		}
	}

}
