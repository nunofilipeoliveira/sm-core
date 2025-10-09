package sm.core.data;

public class Torneio_jogo {
    private int id;
    private int court;
    private String time; // formato HH:mm
    private int homeTeamId;
    private int awayTeamId;
    private String homeTeam;
    private String awayTeam;
    private int goalsHomeTeam;
    private int goalsAwayTeam;
    private String status;
    private String result;
    private String round;



    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public int getCourt() {
        return court;
    }
    public void setCourt(int court) {
        this.court = court;
    }
    public String getTime() {
        return time;
    }
    public void setTime(String time) {
        this.time = time;
    }
    public int getHomeTeamId() {
        return homeTeamId;
    }
    public void setHomeTeamId(int homeTeamId) {
        this.homeTeamId = homeTeamId;
    }
    public int getAwayTeamId() {
        return awayTeamId;
    }
    public void setAwayTeamId(int awayTeamId) {
        this.awayTeamId = awayTeamId;
    }
    public String getHomeTeam() {
        return homeTeam;
    }
    public void setHomeTeam(String homeTeam) {
        this.homeTeam = homeTeam;
    }
    public String getAwayTeam() {
        return awayTeam;
    }
    public void setAwayTeam(String awayTeam) {
        this.awayTeam = awayTeam;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }
    public String getRound() {
        return round;
    }
    public void setRound(String round) {
        this.round = round;
    }
   
    public int getGoalsHomeTeam() {
        return goalsHomeTeam;
    }
    public void setGoalsHomeTeam(int goalsHomeTeam) {
        this.goalsHomeTeam = goalsHomeTeam;
    }
    public int getGoalsAwayTeam() {
        return goalsAwayTeam;
    }
    public void setGoalsAwayTeam(int goalsAwayTeam) {
        this.goalsAwayTeam = goalsAwayTeam;
    }

    //construtor 
public Torneio_jogo() {
    }

    public Torneio_jogo(int id, int court, String time, int homeTeamId, int awayTeamId, String homeTeam, String awayTeam, int goalsHomeTeam, int goalsAwayTeam,
            String status, String result, String round) {
        this.id = id;
        this.court = court;
        this.time = time;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.status = status;
        this.result = result;
        this.round = round;
        this.goalsHomeTeam = goalsHomeTeam;
        this.goalsAwayTeam = goalsAwayTeam;
    } 
    
}
