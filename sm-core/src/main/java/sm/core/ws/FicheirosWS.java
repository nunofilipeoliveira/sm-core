package sm.core.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/sm")
public class FicheirosWS {

	@CrossOrigin
	@PostMapping(value = "/uploadfoto/{nomeFoto}", consumes = "multipart/form-data")
	@ResponseBody
	public String uploadFoto(@PathVariable String nomeFoto, @RequestPart MultipartFile foto) {

		boolean resultado = false;
		System.out.println("FicheirosWS | uploadfoto | Start");
		System.out.println("FicheirosWS | uploadfoto | nomeFoto:" + nomeFoto);

		String server = "94.46.180.24";
		int port = 21;
		String user = "smcompt";
		String pass = "xo5igqMs.X";

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
//			File firstLocalFile = new File("C:/Users/nuno8/Downloads/Jacob.jpg");

			File firstLocalFile = convFile;

			String firstRemoteFile = "/httpdocs/hcmaia/assets/img/jogadores/" + nomeFoto + ".jpg";
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

		return "";

	}

}
