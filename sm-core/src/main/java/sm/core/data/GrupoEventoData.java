package sm.core.data;

/**
 * Grupo de eventos do clube (grupo_evento, V10): pedido e resposta de gravarGrupoEvento e
 * lista "grupos" do getMicroCiclo. idGrupo 0 cria; cor #RRGGBB ou null (automatica).
 */
public class GrupoEventoData {

	private int idGrupo;
	private String nome;
	private String cor;
	private boolean ativo = true;

	public GrupoEventoData() {
	}

	public int getIdGrupo() {
		return idGrupo;
	}

	public void setIdGrupo(int idGrupo) {
		this.idGrupo = idGrupo;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getCor() {
		return cor;
	}

	public void setCor(String cor) {
		this.cor = cor;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void setAtivo(boolean ativo) {
		this.ativo = ativo;
	}
}
