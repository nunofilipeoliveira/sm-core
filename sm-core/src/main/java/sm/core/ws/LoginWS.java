package sm.core.ws;

import java.sql.Timestamp;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.data.EscalaoData;
import sm.core.data.HistoricoLoginData;
import sm.core.data.LoginData;
import sm.core.data.TokenData;
import sm.core.data.UtilizadorData;
import sm.core.data.UtilizadorParaAtivarData;
import sm.core.helper.LoginHelper;
import sm.core.utils.TokenValidator; // Importando a classe TokenValidator
import sm.core.ws.requesdata.LoginRequest;

@RestController
@RequestMapping("/sm")
public class LoginWS {

	private static final Logger log = LoggerFactory.getLogger(LoginWS.class);
	
	@Autowired
	private LoginHelper loginHelper;

	@CrossOrigin
	@PutMapping("/login")
	@ResponseBody
	public String login(@RequestBody LoginRequest loginRequest) {
		// Faz o login e obtem informação do utilizador

		log.info("login | Start");
		log.info("login | User:" + loginRequest.getUser());

		
		LoginData loginData = loginHelper.Dologin(loginRequest.getUser(), loginRequest.getPwd(),
				loginRequest.getTenant_id());

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("login | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("login | Error End");
		}

		log.error("login | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/updateUserWithEscaloes/{parmUserId}")
	@ResponseBody
	public String updateUserWithEscaloes(@PathVariable String parmUserId, @RequestBody String escaloes) {
		// Faz o login e obtem informação do utilizador

		log.info("updateUserWithEscaloes | Start");
		log.info("updateUserWithEscaloes | parmUserId:" + parmUserId);
		log.info("updateUserWithEscaloes | body:" + escaloes);

		ObjectMapper mapper = new ObjectMapper();
		ArrayList<Integer> escalaoIds = new ArrayList<>();
		try {
			// Lê o JSON como JsonNode
			JsonNode jsonNode = mapper.readTree(escaloes);

			// Acessa o array escalaoIds
			JsonNode escalaoIdsNode = jsonNode.get("escalaoIds");

			// Itera sobre os elementos do array e adiciona ao ArrayList
			if (escalaoIdsNode.isArray()) {
				for (JsonNode idNode : escalaoIdsNode) {
					escalaoIds.add(idNode.asInt());
				}
			}

			
			Boolean retorno = loginHelper.updateUserWithEscaloes(escalaoIds, Integer.parseInt(parmUserId));

			log.info("updateUser WithEscaloes | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(retorno);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("updateUser WithEscaloes | Error End");
		}
		log.error("updateUser WithEscaloes | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/updateUser/{parmUserId}")
	@ResponseBody
	public String updateUser(@PathVariable String parmUserId, @RequestBody UtilizadorData parmUtilizadorData) {
		// Faz o login e obtem informação do utilizador

		log.info("updateUser | Start");
		log.info("updateUser | parmUserId:" + parmUserId);

		ObjectMapper mapper = new ObjectMapper();

		try {

			
			Boolean retorno = loginHelper.updateUser(Integer.parseInt(parmUserId), parmUtilizadorData);

			log.info("updateUser| End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(retorno);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("uupdateUser| Error End");
		}
		log.error("updateUser| Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/isAuthenticated")
	@ResponseBody
	public boolean isAuthenticated(@RequestBody TokenData token) {
		// Verifica se o token é válido

		log.info("isAuthenticated | Start");
		log.info("isAuthenticated | token:" + token.getToken());

		
		return loginHelper.isAuthenticated(token.getToken());
	}

	@CrossOrigin
	@PutMapping("/activateuser/{code}")
	@ResponseBody
	public String activateuser(@PathVariable String code) {
		log.info("activateuser | Start");
		log.info("activateuser | code:" + code);

		
		UtilizadorParaAtivarData utilizadorParaAtivarData = loginHelper.getUtilizadorParaAtivarByCode(code);

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("activateuser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizadorParaAtivarData);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("activateuser | Error End");
		}

		log.error("activateuser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getcode/{parmCode}")
	@ResponseBody
	public String getCode(@PathVariable String parmCode) {
		log.info("getCode | Start");
		log.info("getCode | code:" + parmCode);

		
		String code = loginHelper.getCodebyUser(parmCode);

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("getCode | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(code);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("getCode | Error End");
		}

		log.error("getCode | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllUser/{parmTenantId}")
	@ResponseBody
	public String getAllUser(@PathVariable String parmTenantId) {
		log.info("getAllUser | Start");
		log.info("getAllUser | parmTenantId:" + parmTenantId);

		
		ArrayList<UtilizadorData> utilizadores = loginHelper.getAllUser(Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("getAllUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizadores);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("getAllUser | Error End");
		}

		log.error("getAllUser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllUserWait/{parmTenantId}")
	@ResponseBody
	public String getAllUserWait(@PathVariable String parmTenantId) {
		log.info("getAllUserWait | Start");
		log.info("getAllUserWait | parmTenantId:" + parmTenantId);

		
		ArrayList<UtilizadorParaAtivarData> utilizadores = loginHelper.getAllUserWait(Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("getAllUserWait | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizadores);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("getAllUserWait | Error End");
		}

		log.error("getAllUserWait | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEscaloesByUser/{parmUserId}")
	@ResponseBody
	public String getEscaloesByUser(@PathVariable String parmUserId) {
		log.info("getEscaloesByUser | Start");
		log.info("getEscaloesByUser | parmTenantId:" + parmUserId);

		
		ArrayList<EscalaoData> escaloes = loginHelper.getEscaloesByUser(Integer.parseInt(parmUserId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("getEscaloesByUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(escaloes);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("getEscaloesByUser | Error End");
		}

		log.error("getEscaloesByUser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getUser/{parmUserId}/{parmTenantId}")
	@ResponseBody
	public String getUser(@PathVariable String parmUserId, @PathVariable String parmTenantId) {
		log.info("getUser | Start");
		log.info("getUser | parmUserId:" + parmUserId);
		log.info("getUser | parmTenantId:" + parmTenantId);

		
		UtilizadorData utilizador = loginHelper.getUser(Integer.parseInt(parmUserId), Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("getUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("getUser | Error End");
		}

		log.error("getUser | Error End");
		return "";
	}
	
	
	@CrossOrigin
	@PutMapping("/enableUser/{parmUserId}")
	@ResponseBody
	public String enableUser(@PathVariable String parmUserId) {
		log.info("enableUser | Start");
		log.info("enableUser | parmUserId:" + parmUserId);
	

		
		Boolean resultado= loginHelper.enableUser(Integer.parseInt(parmUserId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("enableUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("enableUser | Error End");
		}

		log.error("enableUser | Error End");
		return "";
	}
	
	
	@CrossOrigin
	@PutMapping("/resetPWD/{parmUserId}")
	@ResponseBody
	public String resetPWD(@PathVariable String parmUserId) {
		log.info("resetPWD | Start");
		log.info("resetPWD | parmUserId:" + parmUserId);
	

		
		Boolean resultado= loginHelper.resetPWD(Integer.parseInt(parmUserId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("resetPWD | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("resetPWD | Error End");
		}

		log.error("resetPWD | Error End");
		return "";
	}
	
	
	
	@CrossOrigin
	@PutMapping("/disableUser/{parmUserId}")
	@ResponseBody
	public String disableUser(@PathVariable String parmUserId) {
		log.info("disableUser | Start");
		log.info("disableUser | parmUserId:" + parmUserId);
	

		
		Boolean resultado= loginHelper.disableUser(Integer.parseInt(parmUserId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("disableUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("disableUser | Error End");
		}

		log.error("disableUser | Error End");
		return "";
	}
	
	
	
	@CrossOrigin
	@PutMapping("/getUserByUserName/{parmUserName}/{parmTenantId}")
	@ResponseBody
	public String getUserByUserName(@PathVariable String parmUserName, @PathVariable String parmTenantId) {
		log.info("getUser | Start");
		log.info("getUser | parmUserId:" + parmUserName);
		log.info("getUser | parmTenantId:" + parmTenantId);

		
		UtilizadorData utilizador = loginHelper.getUserByUserName(parmUserName, Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("getUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("getUser | Error End");
		}

		log.error("getUser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/createuser/{parmTenantId}")
	@ResponseBody
	public String createUser(@RequestBody LoginData parmLoginData, @PathVariable String parmTenantId) {
		boolean resultado = false;
		log.info("LoginWS | createUser  | Start");
		log.info("LoginWS | createUser  | user:" + parmLoginData.getUser());

		
		resultado = loginHelper.createUtilizador(parmLoginData, Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("LoginWS | createUser  | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("LoginWS | createUser  | Error End");
		}

		return "";
	}

	@CrossOrigin
	@PutMapping("/createUtilizador/{parmTenantId}")
	@ResponseBody
	public String createUtilizador(@PathVariable String parmTenantId,
			@RequestBody UtilizadorParaAtivarData parmUtilizadorData) {
		String codigoGeradoUtilizador = "";
		log.info("LoginWS | createUtilizador  | Start");
		log.info("LoginWS | createUtilizador  | user:" + parmUtilizadorData.getUser());

		
		codigoGeradoUtilizador = loginHelper.createUtilizadorParaAtivar(parmUtilizadorData,
				Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("LoginWS | createUtilizador  | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(codigoGeradoUtilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("LoginWS | createUtilizador  | Error End");
		}

		return "";
	}

	@CrossOrigin
	@PutMapping("/reenviarEmailAtivacao/{parmTenantId}")
	@ResponseBody
	public String reenviarEmailAtivacao(@PathVariable String parmTenantId,
			@RequestBody UtilizadorParaAtivarData parmUtilizadorData) {
		String codigoGeradoUtilizador = "";
		log.info("LoginWS | reenviarEmailAtivacao  | Start");
		log.info("LoginWS | reenviarEmailAtivacao  | user:" + parmUtilizadorData.getUser());

		
		codigoGeradoUtilizador = loginHelper.reenviarEmailAtivacao(parmUtilizadorData, Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("LoginWS | reenviarEmailAtivacao  | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(codigoGeradoUtilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("LoginWS | reenviarEmailAtivacao  | Error End");
		}

		return "";
	}

	@CrossOrigin
	@PutMapping("/helloworld")
	public String helloWorld() {
		Timestamp timestamp; // 2021-03-24 16:34:26.666
		timestamp = new Timestamp(System.currentTimeMillis());
		log.info("HelloWorld:" + timestamp);

		return "Hello World - SM";
	}

	@CrossOrigin
	@PutMapping("/gethistoricoLogins")
	public String getHistoricoLogins() {
		log.info("getHistoricoLogins | Start");
		ArrayList<HistoricoLoginData> listaHistorico;

		
		listaHistorico = loginHelper.getHistoricoLogins();

		ObjectMapper mapper = new ObjectMapper();

		try {
			log.info("getHistoricoLogins | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(listaHistorico);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("getHistoricoLogins | Error End");
		}

		log.error("getHistoricoLogins | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/extendSession")
	@ResponseBody
	public TokenData extendSession(@RequestBody TokenData tokenData) {
		log.info("extendSession | Start");
		log.info("extendSession | token:" + tokenData.getToken());

		String newToken = TokenValidator.handleExpiredToken(tokenData.getToken());
		TokenData newTokenData = new TokenData();

		if (!newToken.isEmpty()) {
			log.info("extendSession | New token generated");
			newTokenData.setToken(newToken);
			return newTokenData; // Retorna o novo token
		} else {
			log.info("extendSession | Token extended");
			//informacao do token
			log.info("extendSession | Decoding existing token to extend expiration time: " + tokenData.getToken());
			
			return newTokenData; // Retorna vazio se não for possível estender a sessão
		}
	}
}
