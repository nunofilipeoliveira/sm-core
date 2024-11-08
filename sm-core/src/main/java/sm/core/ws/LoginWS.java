package sm.core.ws;

import java.sql.Timestamp;
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

import sm.core.data.HistoricoLoginData;
import sm.core.data.LoginData;
import sm.core.data.UtilizadorParaAtivarData;
import sm.core.helper.LoginHelper;
import sm.core.ws.requesdata.LoginRequest;

@RestController
@RequestMapping("/sm")
public class LoginWS {

	@CrossOrigin
	@PutMapping("/login")
	@ResponseBody
	public String login(@RequestBody LoginRequest loginRequest) {
		// Faz o login e obtem informação do utilizador

		System.out.println("login | Start");
		System.out.println("login | User:" + loginRequest.getUser());

		LoginHelper loginHelper = new LoginHelper();
		LoginData loginData = loginHelper.Dologin(loginRequest.getUser(), loginRequest.getPwd());

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("login | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(loginData);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("login | Error End");
		}

		System.out.println("login | Error End");
		return "";
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

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("activateuser | Error End");
		}

		System.out.println("login | Error End");
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

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("getCode | Error End");
		}

		System.out.println("getCode | Error End");
		return "";
	}

	@CrossOrigin
	@PutMapping("/createuser")
	@ResponseBody
	public String createUser(@RequestBody LoginData parmLoginData) {

		boolean resultado = false;
		System.out.println("LoginWS | createUser | Start");
		System.out.println("LoginWS | createUser | user:" + parmLoginData.getUser());

		LoginHelper loginHelper = new LoginHelper();
		resultado = loginHelper.createUtilizador(parmLoginData);

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("LoginWS | createUser | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("LoginWS | createUser | Error End");
		}

		return "";

	}

	@CrossOrigin
	@PutMapping("/helloworld")
	public String login() {
		// Faz o login e obtem informação do utilizador

		Timestamp timestamp; // 2021-03-24 16:34:26.666
		timestamp = new Timestamp(System.currentTimeMillis());
		System.out.println("HelloWorld:" + timestamp);

		return "Hello World - SM";
	}

	@CrossOrigin
	@PutMapping("/gethistoricoLogins")
	public String getHistoricoLogins() {
		// Faz o login e obtem informação do utilizador

		System.out.println("getHistoricoLogins | Start");
		ArrayList<HistoricoLoginData> listaHistorico;

		LoginHelper loginHelper = new LoginHelper();
		listaHistorico = loginHelper.getHistoricoLogins();

		ObjectMapper mapper = new ObjectMapper();

		try {

			System.out.println("getHistoricoLogins | End");
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(listaHistorico);

			// return mapper.writeValueAsString(loginData);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("getHistoricoLogins | Error End");
		}

		System.out.println("getHistoricoLogins | Error End");
		return "";

	}

}
