package sm.core.ws;

import java.sql.Timestamp;
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
	
	@Autowired
	private LoginHelper loginHelper2; 

	@CrossOrigin
	@PutMapping("/login")
	@ResponseBody
	public String login(@RequestBody LoginRequest loginRequest) {
		// Faz o login e obtem informação do utilizador

		System.out.println("login | Start");
		System.out.println("login | User:" + loginRequest.getUser());

		LoginHelper loginHelper = new LoginHelper();
		LoginData loginData = loginHelper.Dologin(loginRequest.getUser(), loginRequest.getPwd(),
				loginRequest.getTenant_id());

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("login | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("login | Error End");
		}

		System.out.println("login | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/updateUserWithEscaloes/{parmUserId}")
	@ResponseBody
	public String updateUserWithEscaloes(@PathVariable String parmUserId, @RequestBody String escaloes) {
		// Faz o login e obtem informação do utilizador

		System.out.println("updateUserWithEscaloes | Start");
		System.out.println("updateUserWithEscaloes | parmUserId:" + parmUserId);
		System.out.println("updateUserWithEscaloes | body:" + escaloes);

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

			LoginHelper loginHelper = new LoginHelper();
			Boolean retorno = loginHelper.updateUserWithEscaloes(escalaoIds, Integer.parseInt(parmUserId));

			System.out.println("updateUser WithEscaloes | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(retorno);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("updateUser WithEscaloes | Error End");
		}
		System.out.println("updateUser WithEscaloes | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/updateUser/{parmUserId}")
	@ResponseBody
	public String updateUser(@PathVariable String parmUserId, @RequestBody UtilizadorData parmUtilizadorData) {
		// Faz o login e obtem informação do utilizador

		System.out.println("updateUser | Start");
		System.out.println("updateUser | parmUserId:" + parmUserId);

		ObjectMapper mapper = new ObjectMapper();

		try {

			LoginHelper loginHelper = new LoginHelper();
			Boolean retorno = loginHelper.updateUser(Integer.parseInt(parmUserId), parmUtilizadorData);

			System.out.println("updateUser| End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(retorno);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("uupdateUser| Error End");
		}
		System.out.println("updateUser| Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/isAuthenticated")
	@ResponseBody
	public boolean isAuthenticated(@RequestBody TokenData token) {
		// Verifica se o token é válido

		System.out.println("isAuthenticated | Start");
		System.out.println("isAuthenticated | token:" + token.getToken());

		LoginHelper loginHelper = new LoginHelper();
		return loginHelper.isAuthenticated(token.getToken());
	}

	@CrossOrigin
	@PutMapping("/activateuser/{code}")
	@ResponseBody
	public String activateuser(@PathVariable String code) {
		System.out.println("activateuser | Start");
		System.out.println("activateuser | code:" + code);

		LoginHelper loginHelper = new LoginHelper();
		UtilizadorParaAtivarData utilizadorParaAtivarData = loginHelper.getUtilizadorParaAtivarByCode(code);

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("activateuser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizadorParaAtivarData);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("activateuser | Error End");
		}

		System.out.println("activateuser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getcode/{parmCode}")
	@ResponseBody
	public String getCode(@PathVariable String parmCode) {
		System.out.println("getCode | Start");
		System.out.println("getCode | code:" + parmCode);

		LoginHelper loginHelper = new LoginHelper();
		String code = loginHelper.getCodebyUser(parmCode);

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("getCode | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(code);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("getCode | Error End");
		}

		System.out.println("getCode | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllUser/{parmTenantId}")
	@ResponseBody
	public String getAllUser(@PathVariable String parmTenantId) {
		System.out.println("getAllUser | Start");
		System.out.println("getAllUser | parmTenantId:" + parmTenantId);

		LoginHelper loginHelper = new LoginHelper();
		ArrayList<UtilizadorData> utilizadores = loginHelper.getAllUser(Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("getAllUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizadores);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("getAllUser | Error End");
		}

		System.out.println("getAllUser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getAllUserWait/{parmTenantId}")
	@ResponseBody
	public String getAllUserWait(@PathVariable String parmTenantId) {
		System.out.println("getAllUserWait | Start");
		System.out.println("getAllUserWait | parmTenantId:" + parmTenantId);

		LoginHelper loginHelper = new LoginHelper();
		ArrayList<UtilizadorParaAtivarData> utilizadores = loginHelper.getAllUserWait(Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("getAllUserWait | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizadores);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("getAllUserWait | Error End");
		}

		System.out.println("getAllUserWait | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getEscaloesByUser/{parmUserId}")
	@ResponseBody
	public String getEscaloesByUser(@PathVariable String parmUserId) {
		System.out.println("getEscaloesByUser | Start");
		System.out.println("getEscaloesByUser | parmTenantId:" + parmUserId);

		LoginHelper loginHelper = new LoginHelper();
		ArrayList<EscalaoData> escaloes = loginHelper.getEscaloesByUser(Integer.parseInt(parmUserId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("getEscaloesByUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(escaloes);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("getEscaloesByUser | Error End");
		}

		System.out.println("getEscaloesByUser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/getUser/{parmUserId}/{parmTenantId}")
	@ResponseBody
	public String getUser(@PathVariable String parmUserId, @PathVariable String parmTenantId) {
		System.out.println("getUser | Start");
		System.out.println("getUser | parmUserId:" + parmUserId);
		System.out.println("getUser | parmTenantId:" + parmTenantId);

		LoginHelper loginHelper = new LoginHelper();
		UtilizadorData utilizador = loginHelper.getUser(Integer.parseInt(parmUserId), Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("getUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("getUser | Error End");
		}

		System.out.println("getUser | Error End");
		return "";
	}
	
	
	@CrossOrigin
	@PutMapping("/getUserByUserName/{parmUserName}/{parmTenantId}")
	@ResponseBody
	public String getUserByUserName(@PathVariable String parmUserName, @PathVariable String parmTenantId) {
		System.out.println("getUser | Start");
		System.out.println("getUser | parmUserId:" + parmUserName);
		System.out.println("getUser | parmTenantId:" + parmTenantId);

		LoginHelper loginHelper = new LoginHelper();
		UtilizadorData utilizador = loginHelper.getUserByUserName(parmUserName, Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("getUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(utilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("getUser | Error End");
		}

		System.out.println("getUser | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/createuser")
	@ResponseBody
	public String createUser(@RequestBody LoginData parmLoginData) {
		boolean resultado = false;
		System.out.println("LoginWS | createUser  | Start");
		System.out.println("LoginWS | createUser  | user:" + parmLoginData.getUser());

		LoginHelper loginHelper = new LoginHelper();
		resultado = loginHelper.createUtilizador(parmLoginData);

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("LoginWS | createUser  | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("LoginWS | createUser  | Error End");
		}

		return "";
	}

	@CrossOrigin
	@PutMapping("/createUtilizador/{parmTenantId}")
	@ResponseBody
	public String createUtilizador(@PathVariable String parmTenantId,
			@RequestBody UtilizadorParaAtivarData parmUtilizadorData) {
		String codigoGeradoUtilizador = "";
		System.out.println("LoginWS | createUtilizador  | Start");
		System.out.println("LoginWS | createUtilizador  | user:" + parmUtilizadorData.getUser());

		LoginHelper loginHelper = new LoginHelper();
		codigoGeradoUtilizador = loginHelper.createUtilizadorParaAtivar(parmUtilizadorData,
				Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("LoginWS | createUtilizador  | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(codigoGeradoUtilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("LoginWS | createUtilizador  | Error End");
		}

		return "";
	}

	@CrossOrigin
	@PutMapping("/reenviarEmailAtivacao/{parmTenantId}")
	@ResponseBody
	public String reenviarEmailAtivacao(@PathVariable String parmTenantId,
			@RequestBody UtilizadorParaAtivarData parmUtilizadorData) {
		String codigoGeradoUtilizador = "";
		System.out.println("LoginWS | reenviarEmailAtivacao  | Start");
		System.out.println("LoginWS | reenviarEmailAtivacao  | user:" + parmUtilizadorData.getUser());

		
		codigoGeradoUtilizador = loginHelper2.reenviarEmailAtivacao(parmUtilizadorData, Integer.parseInt(parmTenantId));

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("LoginWS | reenviarEmailAtivacao  | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(codigoGeradoUtilizador);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("LoginWS | reenviarEmailAtivacao  | Error End");
		}

		return "";
	}

	@CrossOrigin
	@PutMapping("/helloworld")
	public String helloWorld() {
		Timestamp timestamp; // 2021-03-24 16:34:26.666
		timestamp = new Timestamp(System.currentTimeMillis());
		System.out.println("HelloWorld:" + timestamp);

		return "Hello World - SM";
	}

	@CrossOrigin
	@PutMapping("/gethistoricoLogins")
	public String getHistoricoLogins() {
		System.out.println("getHistoricoLogins | Start");
		ArrayList<HistoricoLoginData> listaHistorico;

		LoginHelper loginHelper = new LoginHelper();
		listaHistorico = loginHelper.getHistoricoLogins();

		ObjectMapper mapper = new ObjectMapper();

		try {
			System.out.println("getHistoricoLogins | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(listaHistorico);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			System.out.println("getHistoricoLogins | Error End");
		}

		System.out.println("getHistoricoLogins | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/extendSession")
	@ResponseBody
	public TokenData extendSession(@RequestBody TokenData tokenData) {
		System.out.println("extendSession | Start");
		System.out.println("extendSession | token:" + tokenData.getToken());

		String newToken = TokenValidator.handleExpiredToken(tokenData.getToken());
		TokenData newTokenData = new TokenData();

		if (!newToken.isEmpty()) {
			System.out.println("extendSession | New token generated");
			newTokenData.setToken(newToken);
			return newTokenData; // Retorna o novo token
		} else {
			System.out.println("extendSession | Unable to extend session");
			return newTokenData; // Retorna vazio se não for possível estender a sessão
		}
	}
}
