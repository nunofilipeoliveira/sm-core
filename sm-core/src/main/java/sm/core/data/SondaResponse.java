package sm.core.data;

public class SondaResponse {
    private String status;
    private String timestamp;
    private String database;
    private String application;
    private String message;

    public SondaResponse() {
    }

    public SondaResponse(String status, String timestamp, String database, String application, String message) {
        this.status = status;
        this.timestamp = timestamp;
        this.database = database;
        this.application = application;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}