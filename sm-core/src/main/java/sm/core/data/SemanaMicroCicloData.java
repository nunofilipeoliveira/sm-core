package sm.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Semana do Micro Ciclo (getMicroCiclo). microCiclo a null = sem plano. foraDoPlano: [{idPresenca, idEquipa, nomeEquipa, data, hora}].
 */
public class SemanaMicroCicloData {

	private int dataInicio;
	private MicroCicloData microCiclo;
	private List<TreinoData> treinos = new ArrayList<>();
	private List<Map<String, Object>> foraDoPlano = new ArrayList<>();
	private List<String> locais = new ArrayList<>();
	private boolean somenteLeitura;
	private List<Integer> equipasSemTreinador = new ArrayList<>();
	private List<String> avisos = new ArrayList<>();
	/** Todas as equipas da epoca: [{idEquipa, nome, cor}], por nome. */
	private List<Map<String, Object>> coresEquipas = new ArrayList<>();
	/** v4 (W2): jogos do tenant na semana, mesmo sem micro ciclo; nunca null. */
	private List<JogoSemanaData> jogos = new ArrayList<>();
	/** v4 (W6): {voltam, ficam}, so para o ADMIN num micro ciclo cancelado e reativavel; senao null. */
	private Map<String, Integer> reativacao;
	/** v5 (W2): todos os grupos de eventos do tenant. */
	private List<GrupoEventoData> grupos = new ArrayList<>();

	public SemanaMicroCicloData() {
	}

	public List<GrupoEventoData> getGrupos() {
		return grupos;
	}

	public void setGrupos(List<GrupoEventoData> grupos) {
		this.grupos = grupos;
	}

	public List<JogoSemanaData> getJogos() {
		return jogos;
	}

	public void setJogos(List<JogoSemanaData> jogos) {
		this.jogos = jogos;
	}

	public Map<String, Integer> getReativacao() {
		return reativacao;
	}

	public void setReativacao(Map<String, Integer> reativacao) {
		this.reativacao = reativacao;
	}

	public int getDataInicio() {
		return dataInicio;
	}

	public void setDataInicio(int dataInicio) {
		this.dataInicio = dataInicio;
	}

	public MicroCicloData getMicroCiclo() {
		return microCiclo;
	}

	public void setMicroCiclo(MicroCicloData microCiclo) {
		this.microCiclo = microCiclo;
	}

	public List<TreinoData> getTreinos() {
		return treinos;
	}

	public void setTreinos(List<TreinoData> treinos) {
		this.treinos = treinos;
	}

	public List<Map<String, Object>> getForaDoPlano() {
		return foraDoPlano;
	}

	public void setForaDoPlano(List<Map<String, Object>> foraDoPlano) {
		this.foraDoPlano = foraDoPlano;
	}

	public List<String> getLocais() {
		return locais;
	}

	public void setLocais(List<String> locais) {
		this.locais = locais;
	}

	public boolean isSomenteLeitura() {
		return somenteLeitura;
	}

	public void setSomenteLeitura(boolean somenteLeitura) {
		this.somenteLeitura = somenteLeitura;
	}

	public List<Integer> getEquipasSemTreinador() {
		return equipasSemTreinador;
	}

	public void setEquipasSemTreinador(List<Integer> equipasSemTreinador) {
		this.equipasSemTreinador = equipasSemTreinador;
	}

	public List<String> getAvisos() {
		return avisos;
	}

	public void setAvisos(List<String> avisos) {
		this.avisos = avisos;
	}

	public List<Map<String, Object>> getCoresEquipas() {
		return coresEquipas;
	}

	public void setCoresEquipas(List<Map<String, Object>> coresEquipas) {
		this.coresEquipas = coresEquipas;
	}
}
