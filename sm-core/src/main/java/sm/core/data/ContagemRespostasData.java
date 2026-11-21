package sm.core.data;

/**
 * v8 (Y9): respostas da sessao (staff). semResposta inclui as caducadas e os sem conta (R235).
 */
public class ContagemRespostasData {

	private int vao;
	private int naoVao;
	private int semResposta;
	private int semConta;

	public ContagemRespostasData() {
	}

	public int getVao() {
		return vao;
	}

	public void setVao(int vao) {
		this.vao = vao;
	}

	public int getNaoVao() {
		return naoVao;
	}

	public void setNaoVao(int naoVao) {
		this.naoVao = naoVao;
	}

	public int getSemResposta() {
		return semResposta;
	}

	public void setSemResposta(int semResposta) {
		this.semResposta = semResposta;
	}

	public int getSemConta() {
		return semConta;
	}

	public void setSemConta(int semConta) {
		this.semConta = semConta;
	}
}
