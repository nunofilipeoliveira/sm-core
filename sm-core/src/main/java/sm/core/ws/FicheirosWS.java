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
import org.springframework.beans.factory.annotation.Value;
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

/**
 * REST controller para upload de ficheiros (fotos de jogadores e logos de clubes).
 *
 * Modos de funcionamento, configuráveis via application.properties:
 *
 *   sm.core.amb=DEV                          → grava em disco local Windows (desenvolvimento)
 *   sm.core.amb=PROD + sm.core.storage=FTP   → envia via FTP para servidor remoto
 *   sm.core.amb=PROD + sm.core.storage=LOCAL → grava diretamente no volume montado no container
 *
 * Exemplo de application.properties completo:
 *
 *   sm.core.amb=PROD
 *
 *   # Modo de storage: FTP ou LOCAL
 *   sm.core.storage=FTP
 *
 *   # Configuração FTP
 *   sm.core.ftp.server=94.46.180.24
 *   sm.core.ftp.port=21
 *   sm.core.ftp.user=smcompt
 *   sm.core.ftp.pass=xo5igqMs.X
 *   sm.core.ftp.basePath=/httpdocs
 *
 *   # Configuração LOCAL (volume montado em container)
 *   sm.core.storage.basePath=/SM/www
 *
 *   # Configuração DEV (caminho local Windows)
 *   sm.core.dev.basePath=D:\\SM\\SM-Front\\sm\\src\\assets\\img
 */
@Component
@RestController
@RequestMapping("/sm")
public class FicheirosWS {

    // -------------------------------------------------------------------------
    // Injeção de propriedades — application.properties
    // -------------------------------------------------------------------------

    // Ambiente e modo de storage
    @Value("${sm.core.amb:PROD}")
    private String amb;

    @Value("${sm.core.storage:FTP}")
    private String storage;

    // FTP
    @Value("${sm.core.ftp.server:94.46.180.24}")
    private String ftpServer;

    @Value("${sm.core.ftp.port:21}")
    private int ftpPort;

    @Value("${sm.core.ftp.user:smcompt}")
    private String ftpUser;

    @Value("${sm.core.ftp.pass:}")
    private String ftpPass;

    @Value("${sm.core.ftp.basePath:/httpdocs}")
    private String ftpBasePath;

    // LOCAL (volume montado)
    @Value("${sm.core.storage.basePath:/SM/www}")
    private String localBasePath;

    // DEV (disco local Windows)
    @Value("${sm.core.dev.basePath:D:\\\\SM\\\\SM-Front\\\\sm\\\\src\\\\assets\\\\img}")
    private String devBasePath;

    // -------------------------------------------------------------------------

    // Tenants para o uploadLogo (logo é partilhada por todos os tenants)
    private static final String[] ALL_TENANTS = { "hcmaia", "advalongo", "cis" };

    @Autowired
    private TenantProperties tenantProperties;

    // =========================================================================
    //  POST /sm/uploadfoto/{nomeFoto}/{tenantId}
    // =========================================================================
    @CrossOrigin
    @PostMapping(value = "/uploadfoto/{nomeFoto}/{tenantId}")
    @ResponseBody
    public String uploadFoto(@PathVariable String nomeFoto,
                             @PathVariable String tenantId,
                             @RequestPart MultipartFile foto) {

        log("uploadfoto", "Start | nomeFoto=" + nomeFoto + " | tenantId=" + tenantId);

        boolean resultado = false;
        ObjectMapper mapper = new ObjectMapper();
        String tmpTenantID = resolveTenantName(tenantId);

        try {
            if (isDev()) {
                // ----- DEV: grava localmente em Windows -----
                String destDir = devBasePath + "\\jogadores";
                gravarFicheiroLocal(foto, destDir, nomeFoto + ".jpg");

            } else if (isLocal()) {
                // ----- PROD LOCAL: escreve diretamente no volume montado -----
                String destDir = localBasePath + "/" + tmpTenantID + "/assets/img/jogadores";
                gravarFicheiroLocal(foto, destDir, nomeFoto + ".jpg");

            } else {
                // ----- PROD FTP -----
                String remotePath = ftpBasePath + "/" + tmpTenantID + "/assets/img/jogadores/" + nomeFoto + ".jpg";
                uploadViaFtp(foto, remotePath);
            }

            resultado = true;

        } catch (Exception e) {
            log("uploadfoto", "Erro: " + e.getMessage());
            e.printStackTrace();
        }

        log("uploadfoto", "End | resultado=" + resultado);
        return toJson(mapper, resultado);
    }

    // =========================================================================
    //  POST /sm/uploadLogo/{nomeFoto}/{tenantId}
    // =========================================================================
    @CrossOrigin
    @PostMapping(value = "/uploadLogo/{nomeFoto}/{tenantId}")
    @ResponseBody
    public String uploadLogo(@PathVariable String nomeFoto,
                             @PathVariable String tenantId,
                             @RequestPart MultipartFile foto) {

        log("uploadLogo", "Start | nomeFoto=" + nomeFoto + " | tenantId=" + tenantId);

        boolean resultado = false;
        ObjectMapper mapper = new ObjectMapper();

        try {
            if (isDev()) {
                // ----- DEV: grava localmente em Windows -----
                String destDir = devBasePath + "\\clubes";
                gravarFicheiroLocal(foto, destDir, nomeFoto + ".png");

            } else if (isLocal()) {
                // ----- PROD LOCAL: escreve no volume montado para todos os tenants -----
                byte[] bytes = foto.getBytes(); // lê uma vez, reutiliza para todos os tenants
                for (String tenant : ALL_TENANTS) {
                    String destDir = localBasePath + "/" + tenant + "/assets/img/clubes";
                    gravarFicheiroBytes(bytes, destDir, nomeFoto + ".png");
                }

            } else {
                // ----- PROD FTP: envia para todos os tenants -----
                uploadLogoViaFtp(foto, nomeFoto);
            }

            resultado = true;

        } catch (Exception e) {
            log("uploadLogo", "Erro: " + e.getMessage());
            e.printStackTrace();
        }

        log("uploadLogo", "End | resultado=" + resultado);
        return toJson(mapper, resultado);
    }

    // =========================================================================
    //  Métodos privados de suporte
    // =========================================================================

    private boolean isDev() {
        return "DEV".equalsIgnoreCase(amb);
    }

    private boolean isLocal() {
        return "LOCAL".equalsIgnoreCase(storage);
    }

    /** Resolve o nome do tenant a partir do tenantId numérico. */
    private String resolveTenantName(String tenantId) {
        Object tenantObj = tenantProperties.getTenant_id().get(String.valueOf(tenantId));
        if (tenantObj instanceof Map) {
            Map<?, ?> tenantMap = (Map<?, ?>) tenantObj;
            return (String) tenantMap.get("name");
        }
        return tenantId; // fallback: usa o id diretamente
    }

    // -------------------------------------------------------------------------
    //  Escrita local / volume montado
    // -------------------------------------------------------------------------

    /** Grava um MultipartFile no diretório destino, criando-o se não existir. */
    private void gravarFicheiroLocal(MultipartFile foto, String destDir, String nomeCompleto)
            throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File destFile = new File(dir, nomeCompleto);
        foto.transferTo(destFile);
        log("gravarFicheiroLocal", "Gravado em: " + destFile.getAbsolutePath());
    }

    /** Grava bytes em disco — usado para reutilizar o mesmo conteúdo em múltiplos tenants. */
    private void gravarFicheiroBytes(byte[] bytes, String destDir, String nomeCompleto)
            throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File destFile = new File(dir, nomeCompleto);
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            fos.write(bytes);
        }
        log("gravarFicheiroBytes", "Gravado em: " + destFile.getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    //  FTP
    // -------------------------------------------------------------------------

    /** Upload de um único ficheiro via FTP. */
    private void uploadViaFtp(MultipartFile foto, String remotePath) throws IOException {
        log("uploadViaFtp", "remotePath=" + remotePath);
        FTPClient ftpClient = new FTPClient();
        try {
            File convFile = multipartToTempFile(foto);
            conectarFtp(ftpClient);
            try (InputStream is = new FileInputStream(convFile)) {
                boolean done = ftpClient.storeFile(remotePath, is);
                log("uploadViaFtp", "Upload " + (done ? "OK" : "FALHOU") + " → " + remotePath);
            }
        } finally {
            disconnectFtp(ftpClient);
        }
    }

    /** Upload do logo via FTP para todos os tenants. */
    private void uploadLogoViaFtp(MultipartFile foto, String nomeFoto) throws IOException {
        FTPClient ftpClient = new FTPClient();
        try {
            File convFile = multipartToTempFile(foto);
            conectarFtp(ftpClient);

            // hcmaia → tenantId = 1
            // advalongo → tenantId = 2
            // cis → tenantId = 3
            for (String tenant : ALL_TENANTS) {
                String remotePath = ftpBasePath + "/" + tenant + "/assets/img/clubes/" + nomeFoto + ".png";
                log("uploadLogoViaFtp", "remotePath=" + remotePath);
                try (InputStream is = new FileInputStream(convFile)) {
                    boolean done = ftpClient.storeFile(remotePath, is);
                    log("uploadLogoViaFtp", "Upload " + (done ? "OK" : "FALHOU") + " → " + remotePath);
                }
            }
        } finally {
            disconnectFtp(ftpClient);
        }
    }

    /** Estabelece ligação FTP com as propriedades configuradas. */
    private void conectarFtp(FTPClient ftpClient) throws IOException {
        ftpClient.connect(ftpServer, ftpPort);
        ftpClient.login(ftpUser, ftpPass);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        log("conectarFtp", "Ligado a " + ftpServer + ":" + ftpPort);
    }

    private void disconnectFtp(FTPClient ftpClient) {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    //  Utilitários
    // -------------------------------------------------------------------------

    /** Converte MultipartFile num File temporário em disco. */
    private File multipartToTempFile(MultipartFile foto) throws IOException {
        String originalName = foto.getOriginalFilename();
        File convFile = new File(originalName != null ? originalName : "upload_tmp");
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(foto.getBytes());
        }
        return convFile;
    }

    /** Serializa um boolean para JSON. */
    private String toJson(ObjectMapper mapper, boolean value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    /** Log padronizado. */
    private void log(String metodo, String mensagem) {
        System.out.println("FicheirosWS | " + metodo + " | " + mensagem);
    }
}
