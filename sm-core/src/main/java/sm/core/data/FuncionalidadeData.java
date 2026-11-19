package sm.core.data;

import java.sql.Timestamp;

/**
 * Funcionalidade do catalogo e o seu estado no tenant (FuncionalidadeWS).
 * Em getFuncionalidades so vem codigo e ativo; em setFuncionalidade o corpo e {codigo, ativo}.
 * impactoDesligar so e preenchido com a funcionalidade ligada.
 */
public class FuncionalidadeData {

	private String codigo;
	private String nome;
	private String descricao;
	private boolean ativo;
	private String alteradoPor;
	private Timestamp dataAlteracao;
	private String impactoDesligar;

	public FuncionalidadeData() {
	}

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getDescricao() {
		return descricao;
	}

	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void setAtivo(boolean ativo) {
		this.ativo = ativo;
	}

	public String getAlteradoPor() {
		return alteradoPor;
	}

	public void setAlteradoPor(String alteradoPor) {
		this.alteradoPor = alteradoPor;
	}

	public Timestamp getDataAlteracao() {
		return dataAlteracao;
	}

	public void setDataAlteracao(Timestamp dataAlteracao) {
		this.dataAlteracao = dataAlteracao;
	}

	public String getImpactoDesligar() {
		return impactoDesligar;
	}

	public void setImpactoDesligar(String impactoDesligar) {
		this.impactoDesligar = impactoDesligar;
	}
}
