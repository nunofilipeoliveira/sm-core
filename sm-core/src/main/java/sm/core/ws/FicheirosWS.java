package sm.core.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Autowired
    private TenantProperties tenantProperties;

    // =========================================================================
    //  POST /sm/uploadfoto/{nomeFoto}/{tenantId}
    // =========================================================================
    @PostMapping(value = "/uploadfoto/{nomeFoto}/{tenantId}")
    @ResponseBody
    public String uploadFoto(@PathVariable String nomeFoto,
                             @PathVariable String tenantId,
                             @RequestPart MultipartFile foto) {

        log("uploadfoto", "Start | nomeFoto=" + nomeFoto + " | tenantId=" + tenantId);

        boolean resultado = false;
        ObjectMapper mapper = new ObjectMapper();

        // Nomes vindos do cliente sao validados: evita path traversal
        // (../) e escrita arbitraria em disco a partir deste endpoint.
        String nomeFicheiro = nomeSeguro(nomeFoto);
        String tenantSeguro = nomeSeguro(tenantId);
        if (nomeFicheiro == null || tenantSeguro == null) {
            log("uploadfoto", "Nomes invalidos: nomeFoto=" + nomeFoto + " | tenantId=" + tenantId);
            return toJson(mapper, false);
        }
        String tmpTenantID = resolveTenantName(tenantSeguro);

        try {
            if (isDev()) {
                // ----- DEV: grava localmente em Windows -----
                String destDir = devBasePath + "\\jogadores";
                gravarFicheiroLocal(foto, destDir, nomeFicheiro + ".jpg");

            } else if (isLocal()) {
                // ----- PROD LOCAL: escreve diretamente no volume montado -----
                String destDir = localBasePath + "/" + tmpTenantID + "/assets/img/jogadores";
                gravarFicheiroLocal(foto, destDir, nomeFicheiro + ".jpg");

            } else {
                // ----- PROD FTP -----
                String remotePath = ftpBasePath + "/" + tmpTenantID + "/assets/img/jogadores/" + nomeFicheiro
                        + ".jpg";
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
    @PostMapping(value = "/uploadLogo/{nomeFoto}/{tenantId}")
    @ResponseBody
    public String uploadLogo(@PathVariable String nomeFoto,
                             @PathVariable String tenantId,
                             @RequestPart MultipartFile foto) {

        log("uploadLogo", "Start | nomeFoto=" + nomeFoto + " | tenantId=" + tenantId);

        boolean resultado = false;
        ObjectMapper mapper = new ObjectMapper();

        // Nomes vindos do cliente sao validados: evita path traversal
        // (../) e escrita arbitraria em disco a partir deste endpoint.
        String nomeFicheiro = nomeSeguro(nomeFoto);
        String tenantSeguro = nomeSeguro(tenantId);
        if (nomeFicheiro == null || tenantSeguro == null) {
            log("uploadLogo", "Nomes invalidos: nomeFoto=" + nomeFoto + " | tenantId=" + tenantId);
            return toJson(mapper, false);
        }

        try {
            if (isDev()) {
                // ----- DEV: grava localmente em Windows -----
                String destDir = devBasePath + "\\clubes";
                gravarFicheiroLocal(foto, destDir, nomeFicheiro + ".png");

            } else if (isLocal()) {
                // ----- PROD LOCAL: escreve no volume montado para todos os tenants -----
                byte[] bytes = foto.getBytes(); // lê uma vez, reutiliza para todos os tenants
                for (String tenant : getAllTenantNames()) {
                    String destDir = localBasePath + "/" + tenant + "/assets/img/clubes";
                    gravarFicheiroBytes(bytes, destDir, nomeFicheiro + ".png");
                }

            } else {
                // ----- PROD FTP: envia para todos os tenants -----
                uploadLogoViaFtp(foto, nomeFicheiro);
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

    /**
     * Obtém os nomes de todos os tenants configurados em application.properties.
     * O logo é partilhada por todos os tenants, por isso se faz o upload para cada um.
     */
    private String[] getAllTenantNames() {
        Map<String, Map<String, String>> tenants = tenantProperties.getTenant_id();
        if (tenants == null || tenants.isEmpty()) {
            return new String[0];
        }
        return tenants.values().stream()
                .map(m -> m.get("name"))
                .toArray(String[]::new);
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
        File convFile = null;
        try {
            convFile = multipartToTempFile(foto);
            conectarFtp(ftpClient);
            try (InputStream is = new FileInputStream(convFile)) {
                boolean done = ftpClient.storeFile(remotePath, is);
                log("uploadViaFtp", "Upload " + (done ? "OK" : "FALHOU") + " → " + remotePath);
            }
        } finally {
            disconnectFtp(ftpClient);
            eliminarTempFile(convFile);
        }
    }

    /** Upload do logo via FTP para todos os tenants. */
    private void uploadLogoViaFtp(MultipartFile foto, String nomeFoto) throws IOException {
        FTPClient ftpClient = new FTPClient();
        File convFile = null;
        try {
            convFile = multipartToTempFile(foto);
            conectarFtp(ftpClient);

            for (String tenant : getAllTenantNames()) {
                String remotePath = ftpBasePath + "/" + tenant + "/assets/img/clubes/" + nomeFoto + ".png";
                log("uploadLogoViaFtp", "remotePath=" + remotePath);
                try (InputStream is = new FileInputStream(convFile)) {
                    boolean done = ftpClient.storeFile(remotePath, is);
                    log("uploadLogoViaFtp", "Upload " + (done ? "OK" : "FALHOU") + " → " + remotePath);
                }
            }
        } finally {
            disconnectFtp(ftpClient);
            eliminarTempFile(convFile);
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

    /**
     * Converte MultipartFile num ficheiro temporario do sistema (nunca no
     * diretorio de trabalho) — evita acumular ficheiros em disco, que era um
     * vetor simples de esgotar o espaco disponivel. O ficheiro devolvido tem de
     * ser eliminado pelo chamador ({@link #eliminarTempFile(File)}).
     */
    private File multipartToTempFile(MultipartFile foto) throws IOException {
        Path tempPath = Files.createTempFile("sm-upload-", ".tmp");
        foto.transferTo(tempPath);
        return tempPath.toFile();
    }

    /** Remove o ficheiro temporario, ignorando falhas. */
    private void eliminarTempFile(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            log("eliminarTempFile", "Nao foi possivel remover " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    /**
     * Valida um nome recebido do cliente (nome de ficheiro ou tenant).
     * Rejeita separadores de caminho, sequencias ".." e caracteres reservados,
     * impedindo escrita fora do diretorio previsto (path traversal).
     *
     * @return o nome limpo ou {@code null} quando invalido
     */
    private String nomeSeguro(String nome) {
        if (nome == null) {
            return null;
        }
        String limpo = nome.trim();
        if (limpo.isEmpty() || limpo.length() > 128) {
            return null;
        }
        if (limpo.contains("/") || limpo.contains("\\") || limpo.contains("..") || limpo.contains(":")) {
            return null;
        }
        for (char proibido : new char[] { '*', '?', '"', '<', '>', '|', '\0' }) {
            if (limpo.indexOf(proibido) >= 0) {
                return null;
            }
        }
        return limpo;
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
