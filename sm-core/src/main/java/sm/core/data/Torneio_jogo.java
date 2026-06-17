package sm.core.data;

public class Torneio_jogo {
    private int id;
    private String date;
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
    private String round_number;
    private String round_action;
    private String tier; // exemplo: Sub-9, Sub-11, Sub-13, etc.
    private boolean hasTable; // Indica se o jogo possui tabela associada




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

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getRound_number() {
        return round_number;
    }

    public void setRound_number(String round_number) {
        this.round_number = round_number;
    }

    public String getRound_action() {
        return round_action;
    }

    public void setRound_action(String round_action) {
        this.round_action = round_action;
    }

    public boolean isHasTable() {
        return hasTable;
    }

    public void setHasTable(boolean hasTable) {
        this.hasTable = hasTable;
    }


    //construtor 
public Torneio_jogo() {
    }

    public Torneio_jogo(int id, int court, String time, int homeTeamId, int awayTeamId, String homeTeam, String awayTeam, int goalsHomeTeam, int goalsAwayTeam,
            String status, String result, String round, String tier, String date, String round_number, String round_action, boolean hasTable) {
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
        this.tier = tier;
        this.goalsHomeTeam = goalsHomeTeam;
        this.goalsAwayTeam = goalsAwayTeam;
        this.date = date;
        this.round_number = round_number;
        this.round_action = round_action;
        this.hasTable = hasTable; // Inicializa com o valor passado como parâmetro
    } 
    
}
