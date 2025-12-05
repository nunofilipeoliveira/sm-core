package sm.core.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.Start_SMCore;
import sm.core.utils.TenantProperties;

@Component
@RestController
@RequestMapping("/sm")
public class FicheirosWS {

	@Autowired
	private TenantProperties tenantProperties;

	@CrossOrigin
	@PostMapping(value = "/uploadfoto/{nomeFoto}/{tenantId}")
	@ResponseBody
	public String uploadFoto(@PathVariable String nomeFoto, @PathVariable String tenantId,
			@RequestPart MultipartFile foto) {

		System.out.println("FicheirosWS | uploadfoto | Start");
		System.out.println("FicheirosWS | uploadfoto | nomeFoto:" + nomeFoto);
		System.out.println("FicheirosWS | uploadfoto | tenantId:" + tenantId);

		String server = "94.46.180.24";
		int port = 21;
		String user = "smcompt";
		String pass = "xo5igqMs.X";

		boolean resultado = false;
		ObjectMapper mapper = new ObjectMapper();

		Object tenantObj = tenantProperties.getTenant_id().get(String.valueOf(tenantId));
		String tmpTenantID = null;
		if (tenantObj instanceof Map) {
			Map<?, ?> tenantMap = (Map<?, ?>) tenantObj;
			tmpTenantID = (String) tenantMap.get("name");
			// agora tmpTenantID == "hcmaia"
		}

		String amb = Start_SMCore.configProp.getProperty("sm.core.amb");
		if (amb != null && amb.equals("DEV")) {
			System.out.println("FicheirosWS | uploadfoto | Ambiente:" + amb);
			try {
				// Define o caminho completo do ficheiro local
				String localDir = "D:\\SM\\SM-Front\\sm\\src\\assets\\img\\jogadores";
				File dir = new File(localDir);
				if (!dir.exists()) {
					dir.mkdirs(); // cria a pasta se não existir
				}
				// Nome do ficheiro com extensão .jpg
				File localFile = new File(dir, nomeFoto + ".jpg");
				// Grava os bytes do MultipartFile no ficheiro local
				foto.transferTo(localFile);
				System.out.println("Ficheiro gravado localmente em: " + localFile.getAbsolutePath());

			} catch (IOException e) {
				System.out.println("Erro ao gravar ficheiro localmente: " + e.getMessage());
				e.printStackTrace();
				return "Erro ao gravar ficheiro localmente";
			}
			resultado = true;

		} else {
			System.out.println("FicheirosWS | uploadfoto | Ambiente:" + amb);

			FTPClient ftpClient = new FTPClient();
			try {

				File convFile = new File(foto.getOriginalFilename());
				FileOutputStream fos = new FileOutputStream(convFile);
				fos.write(foto.getBytes());
				fos.close();

				ftpClient.connect(server, port);
				ftpClient.login(user, pass);
				ftpClient.enterLocalPassiveMode();

				ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

				// APPROACH #1: uploads first file using an InputStream
				// File firstLocalFile = new File("C:/Users/nuno8/Downloads/Jacob.jpg");

				File firstLocalFile = convFile;

				String firstRemoteFile = "/httpdocs/" + tmpTenantID + "/assets/img/jogadores/" + nomeFoto + ".jpg";
				System.out.println("FicheirosWS | uploadfoto | firstRemoteFile:" + firstRemoteFile);
				InputStream inputStream = new FileInputStream(firstLocalFile);

				System.out.println("Start uploading first file");
				boolean done = ftpClient.storeFile(firstRemoteFile, inputStream);
				inputStream.close();
				if (done) {
					System.out.println("The first file is uploaded successfully.");
				}

			} catch (IOException ex) {
				System.out.println("Error: " + ex.getMessage());
				ex.printStackTrace();
			} finally {
				try {
					if (ftpClient.isConnected()) {
						ftpClient.logout();
						ftpClient.disconnect();
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

			resultado = true;
		}

		try {
			System.out.println("FicheirosWS | uploadfoto | End: Resultado" + resultado);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
			return "";
		}

	}


	@CrossOrigin
	@PostMapping(value = "/uploadLogo/{nomeFoto}/{tenantId}")
	@ResponseBody
	public String uploadLogo(@PathVariable String nomeFoto, @PathVariable String tenantId,
			@RequestPart MultipartFile foto) {

		System.out.println("FicheirosWS | uploadLogo | Start");
		System.out.println("FicheirosWS | uploadLogo | nomeFoto:" + nomeFoto);
		System.out.println("FicheirosWS | uploadLogo | tenantId:" + tenantId);

		String server = "94.46.180.24";
		int port = 21;
		String user = "smcompt";
		String pass = "xo5igqMs.X";

		boolean resultado = false;
		ObjectMapper mapper = new ObjectMapper();

		Object tenantObj = tenantProperties.getTenant_id().get(String.valueOf(tenantId));
		String tmpTenantID = null;
		if (tenantObj instanceof Map) {
			Map<?, ?> tenantMap = (Map<?, ?>) tenantObj;
			tmpTenantID = (String) tenantMap.get("name");
			// agora tmpTenantID == "hcmaia"
		}

		String amb = Start_SMCore.configProp.getProperty("sm.core.amb");
		if (amb != null && amb.equals("DEV")) {
			System.out.println("FicheirosWS | uploadfoto | Ambiente:" + amb);
			try {
				// Define o caminho completo do ficheiro local
				String localDir = "D:\\SM\\SM-Front\\sm\\src\\assets\\img\\clubes";
				File dir = new File(localDir);
				if (!dir.exists()) {
					dir.mkdirs(); // cria a pasta se não existir
				}
				// Nome do ficheiro com extensão .png
				File localFile = new File(dir, nomeFoto + ".png");
				// Grava os bytes do MultipartFile no ficheiro local
				foto.transferTo(localFile);
				System.out.println("Ficheiro gravado localmente em: " + localFile.getAbsolutePath());

			} catch (IOException e) {
				System.out.println("Erro ao gravar ficheiro localmente: " + e.getMessage());
				e.printStackTrace();
				return "Erro ao gravar ficheiro localmente";
			}
			resultado = true;

		} else {
			System.out.println("FicheirosWS | uploadfoto | Ambiente:" + amb);

			FTPClient ftpClient = new FTPClient();
			try {

				File convFile = new File(foto.getOriginalFilename());
				FileOutputStream fos = new FileOutputStream(convFile);
				fos.write(foto.getBytes());
				fos.close();

				ftpClient.connect(server, port);
				ftpClient.login(user, pass);
				ftpClient.enterLocalPassiveMode();

				ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

				// APPROACH #1: uploads first file using an InputStream
				// File firstLocalFile = new File("C:/Users/nuno8/Downloads/Jacob.jpg");

				File firstLocalFile = convFile;

				//colocar em todas tenants
				//hcmaia é o tenantId = 1
				String firstRemoteFile = "/httpdocs/hcmaia/assets/img/clubes/" + nomeFoto + ".png";
				System.out.println("FicheirosWS | uploadfoto | firstRemoteFile:" + firstRemoteFile);
				InputStream inputStream = new FileInputStream(firstLocalFile);

				System.out.println("Start uploading first file");
				boolean done = ftpClient.storeFile(firstRemoteFile, inputStream);

				//advalongo é o tenantId = 2
				firstRemoteFile = "/httpdocs/advalongo/assets/img/clubes/" + nomeFoto + ".png";
				System.out.println("FicheirosWS | uploadfoto | firstRemoteFile:" + firstRemoteFile);
				inputStream = new FileInputStream(firstLocalFile);	
				done = ftpClient.storeFile(firstRemoteFile, inputStream);	

				//cis é o tenantId = 3
				firstRemoteFile = "/httpdocs/cis/assets/img/clubes/" + nomeFoto + ".png";
				System.out.println("FicheirosWS | uploadfoto | firstRemoteFile:" + firstRemoteFile);
				inputStream = new FileInputStream(firstLocalFile);	
				done = ftpClient.storeFile(firstRemoteFile, inputStream);	


				inputStream.close();
				if (done) {
					System.out.println("The first file is uploaded successfully.");
				}

			} catch (IOException ex) {
				System.out.println("Error: " + ex.getMessage());
				ex.printStackTrace();
			} finally {
				try {
					if (ftpClient.isConnected()) {
						ftpClient.logout();
						ftpClient.disconnect();
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

			resultado = true;
		}

		try {
			System.out.println("FicheirosWS | uploadLogo | End: Resultado" + resultado);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			resultado = false;
			return "";
		}

	}


}
