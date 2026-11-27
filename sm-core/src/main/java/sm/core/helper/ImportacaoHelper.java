package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import sm.core.data.JogadorData;
import sm.core.data.PedidoImportacaoData;
import sm.core.data.ResultadoImportacaoData;
import sm.core.data.StaffData;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;

/**
 * Importacao de jogadores e staff a partir de um CSV lido no browser (desenho importar-csv §5 B5). Valida e
 * identifica existentes (validarImportacao, sem escrita) e grava linha a linha numa transacao por linha
 * (gravarImportacao), repetindo validacao e identificacao (R2, R42). Tenant e utilizador vem so da Identidade.
 * Logs e mensagens nunca levam valores das celulas (R4).
 */
@Component
public class ImportacaoHelper {

	private static final Logger log = LoggerFactory.getLogger(ImportacaoHelper.class);

	static final int MAX_LINHAS = 1000;

	public static final String CRIAR = "CRIAR";
	public static final String ATUALIZAR = "ATUALIZAR";
	public static final String SEM_ALTERACOES = "SEM_ALTERACOES";
	public static final String POSSIVEL_DUPLICADO = "POSSIVEL_DUPLICADO";
	public static final String ERRO = "ERRO";
	public static final String CRIADO = "CRIADO";
	public static final String ATUALIZADO = "ATUALIZADO";
	public static final String MUDOU = "MUDOU";

	private final DBUtils dbUtils;
	private final JogadorHelper jogadorHelper;
	private final StaffHelper staffHelper;

	public ImportacaoHelper(DBUtils dbUtils, JogadorHelper jogadorHelper, StaffHelper staffHelper) {
		this.dbUtils = dbUtils;
		this.jogadorHelper = jogadorHelper;
		this.staffHelper = staffHelper;
	}

	/** Registo existente do tenant, com os valores ja normalizados ("" = sem valor). */
	private static final class Pessoa {
		int id;
		final Map<CampoImportacao, String> valores = new EnumMap<>(CampoImportacao.class);
		final Set<String> nomes = new HashSet<>();
		boolean inativo;
		boolean ligadoJogador;

		String valor(CampoImportacao parmCampo) {
			return valores.getOrDefault(parmCampo, "");
		}

		void calculaNomes() {
			nomes.clear();
			for (CampoImportacao campo : List.of(CampoImportacao.NOME, CampoImportacao.NOME_COMPLETO)) {
				String nome = CampoImportacao.normalizaNome(valor(campo));
				if (!nome.isEmpty()) {
					nomes.add(nome);
				}
			}
		}
	}

	private record Identificacao(String estado, List<Pessoa> candidatos, String mensagem) {
	}

	/** Estado de um pedido (validacao ou gravacao). */
	private static final class Contexto {
		String tipo;
		List<CampoImportacao> campos = new ArrayList<>();
		String funcaoFixa = "";
		int idEquipa;
		int tenant;
		int utilizador;
		List<Pessoa> pessoas = new ArrayList<>();
		Set<Integer> membros = new HashSet<>();
		/** R31: licenca/NIF/nome+data -> primeira linha. */
		Map<String, Integer> chaves = new HashMap<>();
		/** V10: id existente -> primeira linha. */
		Map<Integer, Integer> idsUsados = new HashMap<>();

		boolean isJogador() {
			return CampoImportacao.JOGADOR.equals(tipo);
		}
	}

	// ==================== validarImportacao ====================

	public ResultadoImportacaoData valida(PedidoImportacaoData parmPedido, Identidade parmIdentidade) {
		Contexto ctx = preparaPedido(parmPedido, parmIdentidade, false);
		try (Connection conn = dbUtils.getConnection()) {
			carregaContexto(conn, ctx);
		} catch (SQLException e) {
			throw erroBD("valida", e);
		}

		ResultadoImportacaoData resultado = new ResultadoImportacaoData();
		for (PedidoImportacaoData.Linha linha : parmPedido.getLinhas()) {
			resultado.getLinhas().add(validaLinha(ctx, linha));
		}
		return resultado;
	}

	private ResultadoImportacaoData.Linha validaLinha(Contexto ctx, PedidoImportacaoData.Linha parmLinha) {
		ResultadoImportacaoData.Linha r = new ResultadoImportacaoData.Linha();
		r.setLinha(parmLinha.getLinha());

		Map<CampoImportacao, String> valores = valoresLinha(ctx, parmLinha, r);
		if (!r.getErros().isEmpty()) {
			r.setEstado(ERRO);
			return r;
		}
		Integer repetida = repetidaNoFicheiro(ctx, valores, parmLinha.getLinha());
		if (repetida != null) {
			return erro(r, "Repetida da linha " + repetida);
		}

		Identificacao identificacao = identifica(ctx, valores);
		if (ERRO.equals(identificacao.estado())) {
			return erro(r, identificacao.mensagem());
		}
		if (CRIAR.equals(identificacao.estado())) {
			r.setEstado(CRIAR);
			return r;
		}

		Pessoa pessoa = identificacao.candidatos().get(0);
		Integer outra = ctx.idsUsados.putIfAbsent(pessoa.id, parmLinha.getLinha());
		if (outra != null) {
			return erro(r, "Repetida da linha " + outra);
		}
		r.setIdExistente(pessoa.id);
		r.getCandidatos().add(candidato(pessoa));
		r.setDiferencas(diferencas(pessoa, valores));
		r.setJaNaEquipa(ctx.membros.contains(pessoa.id));
		if (POSSIVEL_DUPLICADO.equals(identificacao.estado())) {
			r.setEstado(POSSIVEL_DUPLICADO);
		} else {
			r.setEstado(r.getDiferencas().isEmpty() ? SEM_ALTERACOES : ATUALIZAR);
		}
		return r;
	}

	// ==================== gravarImportacao ====================

	public ResultadoImportacaoData grava(PedidoImportacaoData parmPedido, Identidade parmIdentidade) {
		Contexto ctx = preparaPedido(parmPedido, parmIdentidade, true);
		ResultadoImportacaoData resultado = new ResultadoImportacaoData();
		try (Connection conn = dbUtils.getConnection()) {
			carregaContexto(conn, ctx);
			// R41: uma transacao por linha
			conn.setAutoCommit(false);
			try {
				for (PedidoImportacaoData.Linha linha : parmPedido.getLinhas()) {
					resultado.getLinhas().add(gravaLinha(conn, ctx, linha));
				}
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw erroBD("grava", e);
		}
		return resultado;
	}

	private ResultadoImportacaoData.Linha gravaLinha(Connection conn, Contexto ctx, PedidoImportacaoData.Linha parmLinha)
			throws SQLException {
		ResultadoImportacaoData.Linha r = new ResultadoImportacaoData.Linha();
		r.setLinha(parmLinha.getLinha());

		Map<CampoImportacao, String> valores = valoresLinha(ctx, parmLinha, r);
		if (!r.getErros().isEmpty()) {
			r.setEstado(ERRO);
			return r;
		}
		Integer repetida = repetidaNoFicheiro(ctx, valores, parmLinha.getLinha());
		if (repetida != null) {
			return erro(r, "Repetida da linha " + repetida);
		}

		// R42: reidentifica e so aceita o que o admin viu na pre-visualizacao
		Identificacao identificacao = identifica(ctx, valores);
		boolean criar = CRIAR.equals(parmLinha.getAcao());
		Pessoa alvo = null;
		if (criar) {
			boolean aceite = CRIAR.equals(identificacao.estado())
					|| (POSSIVEL_DUPLICADO.equals(identificacao.estado()) && lista(parmLinha.getIdsVistos())
							.containsAll(identificacao.candidatos().stream().map(p -> p.id).collect(Collectors.toList())));
			if (!aceite) {
				return mudou(r);
			}
		} else {
			boolean aceite = (ATUALIZAR.equals(identificacao.estado())
					|| POSSIVEL_DUPLICADO.equals(identificacao.estado()))
					&& identificacao.candidatos().get(0).id == parmLinha.getIdExistente();
			if (!aceite) {
				return mudou(r);
			}
			alvo = identificacao.candidatos().get(0);
			Integer outra = ctx.idsUsados.putIfAbsent(alvo.id, parmLinha.getLinha());
			if (outra != null) {
				return erro(r, "Repetida da linha " + outra);
			}
			r.setIdExistente(alvo.id);
		}

		try {
			int idPessoa;
			String estado;
			if (criar) {
				idPessoa = cria(conn, ctx, valores);
				if (idPessoa <= 0) {
					throw new SQLException("Sem id gerado.");
				}
				estado = CRIADO;
			} else {
				int alteracoes = atualiza(conn, ctx, alvo, valores, lista(parmLinha.getAtualizar()));
				if (alteracoes < 0) {
					conn.rollback();
					return mudou(r);
				}
				idPessoa = alvo.id;
				estado = alteracoes > 0 ? ATUALIZADO : SEM_ALTERACOES;
			}

			boolean associado = false;
			boolean jaNaEquipa = false;
			if (ctx.idEquipa > 0) {
				if (ctx.membros.contains(idPessoa)) {
					jaNaEquipa = true;
				} else {
					associado = associa(conn, ctx, idPessoa, funcaoDaLinha(ctx, valores));
					jaNaEquipa = !associado;
				}
			}

			conn.commit();

			r.setEstado(estado);
			r.setAssociadoEquipa(associado);
			r.setJaNaEquipa(jaNaEquipa);
			if (criar) {
				r.setIdCriado(idPessoa);
				ctx.pessoas.add(novaPessoa(idPessoa, valores));
			}
			if (ctx.idEquipa > 0) {
				ctx.membros.add(idPessoa);
			}
			return r;
		} catch (SQLException | RuntimeException e) {
			conn.rollback();
			// sem e.getMessage(): pode citar valores (R4)
			SQLException sql = e instanceof SQLException ? (SQLException) e : null;
			log.error("ImportacaoHelper | grava | linha:{} erro ao gravar (rollback) sqlState:{} errorCode:{} tipo:{}",
					parmLinha.getLinha(), sql == null ? null : sql.getSQLState(),
					sql == null ? null : sql.getErrorCode(), e.getClass().getSimpleName());
			return erro(r, "Não foi possível gravar a linha.");
		}
	}

	private int cria(Connection conn, Contexto ctx, Map<CampoImportacao, String> parmValores) throws SQLException {
		if (ctx.isJogador()) {
			JogadorData jogador = new JogadorData();
			jogador.setNome(texto(parmValores, CampoImportacao.NOME));
			jogador.setNome_completo(texto(parmValores, CampoImportacao.NOME_COMPLETO));
			jogador.setData_nascimento(numero(parmValores, CampoImportacao.DATA_NASCIMENTO));
			jogador.setEmail(texto(parmValores, CampoImportacao.EMAIL));
			jogador.setTelemovel(texto(parmValores, CampoImportacao.TELEMOVEL));
			jogador.setPai_nome(texto(parmValores, CampoImportacao.PAI_NOME));
			jogador.setPai_email(texto(parmValores, CampoImportacao.PAI_EMAIL));
			jogador.setPai_telemovel(texto(parmValores, CampoImportacao.PAI_TELEMOVEL));
			jogador.setMae_nome(texto(parmValores, CampoImportacao.MAE_NOME));
			jogador.setMae_email(texto(parmValores, CampoImportacao.MAE_EMAIL));
			jogador.setMae_telemovel(texto(parmValores, CampoImportacao.MAE_TELEMOVEL));
			jogador.setMorada(texto(parmValores, CampoImportacao.MORADA));
			jogador.setCidade(texto(parmValores, CampoImportacao.CIDADE));
			jogador.setCodigo_postal(texto(parmValores, CampoImportacao.CODIGO_POSTAL));
			jogador.setObservacoes(texto(parmValores, CampoImportacao.OBSERVACOES));
			jogador.setNumero(texto(parmValores, CampoImportacao.NUMERO));
			jogador.setCC(texto(parmValores, CampoImportacao.CC));
			jogador.setNIF(numero(parmValores, CampoImportacao.NIF));
			jogador.setLicenca(numero(parmValores, CampoImportacao.LICENCA));
			return jogadorHelper.insereJogador(conn, jogador, ctx.tenant, ctx.utilizador);
		}
		StaffData staff = new StaffData();
		staff.setNome(texto(parmValores, CampoImportacao.NOME));
		staff.setNome_completo(texto(parmValores, CampoImportacao.NOME_COMPLETO));
		staff.setTelemovel(texto(parmValores, CampoImportacao.TELEMOVEL));
		staff.setEmail(texto(parmValores, CampoImportacao.EMAIL));
		staff.setMorada(texto(parmValores, CampoImportacao.MORADA));
		staff.setCodigo_postal(texto(parmValores, CampoImportacao.CODIGO_POSTAL));
		staff.setData_nascimento(numero(parmValores, CampoImportacao.DATA_NASCIMENTO));
		staff.setLicenca(texto(parmValores, CampoImportacao.LICENCA));
		return staffHelper.insereStaff(conn, staff, ctx.tenant, ctx.utilizador);
	}

	/**
	 * D7: so os campos marcados, comparados com o valor lido agora (R37), sem tocar em estado (R39).
	 *
	 * @return numero de campos alterados, ou -1 se o registo ja nao existe no tenant (MUDOU)
	 */
	private int atualiza(Connection conn, Contexto ctx, Pessoa parmAlvo, Map<CampoImportacao, String> parmValores,
			List<String> parmAtualizar) throws SQLException {
		List<CampoImportacao> campos = new ArrayList<>();
		for (String codigo : parmAtualizar) {
			CampoImportacao campo = CampoImportacao.porCodigo(codigo, ctx.tipo);
			if (campo != null && campo.getColuna() != null && parmValores.containsKey(campo)
					&& editavel(parmAlvo, campo) && !campos.contains(campo)) {
				campos.add(campo);
			}
		}
		String tabela = ctx.isJogador() ? "jogador" : "staff";

		// colunas so do enum (nunca do pedido)
		StringBuilder select = new StringBuilder("select id");
		for (CampoImportacao campo : campos) {
			select.append(", `").append(campo.getColuna()).append('`');
		}
		select.append(" from ").append(tabela).append(" where id=? and tenant_id=? for update");
		PreparedStatement preparedStatement = conn.prepareStatement(select.toString());
		preparedStatement.setInt(1, parmAlvo.id);
		preparedStatement.setInt(2, ctx.tenant);
		ResultSet rs = preparedStatement.executeQuery();
		if (!rs.next()) {
			return -1;
		}

		List<CampoImportacao> mudar = new ArrayList<>();
		List<String> antigos = new ArrayList<>();
		for (CampoImportacao campo : campos) {
			if (!parmValores.get(campo).equals(lerValor(rs, campo))) {
				mudar.add(campo);
				String antigo = rs.getString(campo.getColuna());
				antigos.add(antigo == null ? "" : antigo);
			}
		}
		if (mudar.isEmpty()) {
			return 0;
		}

		StringBuilder update = new StringBuilder("update ").append(tabela).append(" set ");
		for (int i = 0; i < mudar.size(); i++) {
			update.append(i == 0 ? "`" : ", `").append(mudar.get(i).getColuna()).append("`=?");
		}
		update.append(" where id=? and tenant_id=?");
		preparedStatement = conn.prepareStatement(update.toString());
		int indice = 1;
		for (CampoImportacao campo : mudar) {
			if (campo.isNumerico()) {
				preparedStatement.setLong(indice++, Long.parseLong(parmValores.get(campo)));
			} else {
				preparedStatement.setString(indice++, parmValores.get(campo));
			}
		}
		preparedStatement.setInt(indice++, parmAlvo.id);
		preparedStatement.setInt(indice, ctx.tenant);
		preparedStatement.executeUpdate();

		for (int i = 0; i < mudar.size(); i++) {
			CampoImportacao campo = mudar.get(i);
			if (ctx.isJogador()) {
				jogadorHelper.registaHistorico(ctx.utilizador, parmAlvo.id, conn, campo.getRotulo(), antigos.get(i),
						parmValores.get(campo));
			} else {
				staffHelper.registaHistoricoStaff(ctx.utilizador, parmAlvo.id, conn, campo.getRotulo(), antigos.get(i),
						parmValores.get(campo));
			}
		}
		return mudar.size();
	}

	/** D8: associacao idempotente a equipa, na transacao da linha. @return true se inseriu */
	private boolean associa(Connection conn, Contexto ctx, int parmIdPessoa, String parmFuncao) throws SQLException {
		PreparedStatement preparedStatement;
		if (ctx.isJogador()) {
			preparedStatement = conn.prepareStatement(
					"INSERT INTO escalao_epoca_jogador (id_escalao_epoca, id_jogador) SELECT ?, ? FROM DUAL "
							+ "WHERE NOT EXISTS (SELECT 1 FROM escalao_epoca_jogador WHERE id_escalao_epoca=? AND id_jogador=?)");
			preparedStatement.setInt(1, ctx.idEquipa);
			preparedStatement.setInt(2, parmIdPessoa);
			preparedStatement.setInt(3, ctx.idEquipa);
			preparedStatement.setInt(4, parmIdPessoa);
		} else {
			preparedStatement = conn.prepareStatement(
					"INSERT INTO escalao_epoca_staff (id_escalao_epoca, id_staff, tipo) SELECT ?, ?, ? FROM DUAL "
							+ "WHERE NOT EXISTS (SELECT 1 FROM escalao_epoca_staff WHERE id_escalao_epoca=? AND id_staff=?)");
			preparedStatement.setInt(1, ctx.idEquipa);
			preparedStatement.setInt(2, parmIdPessoa);
			preparedStatement.setString(3, parmFuncao);
			preparedStatement.setInt(4, ctx.idEquipa);
			preparedStatement.setInt(5, parmIdPessoa);
		}
		return preparedStatement.executeUpdate() > 0;
	}

	// ==================== pedido e linhas ====================

	/** Validacao do pedido (§4.1): 400 com mensagens sem valores. */
	private static Contexto preparaPedido(PedidoImportacaoData parmPedido, Identidade parmIdentidade,
			boolean parmGravacao) {
		if (parmPedido == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		Contexto ctx = new Contexto();
		ctx.tenant = parmIdentidade.getTenantId();
		ctx.utilizador = parmIdentidade.getIdUtilizador();
		ctx.tipo = parmPedido.getTipo();
		if (!CampoImportacao.JOGADOR.equals(ctx.tipo) && !CampoImportacao.STAFF.equals(ctx.tipo)) {
			throw new PedidoInvalidoException("Tipo de importação inválido.");
		}
		if (parmPedido.getIdEquipa() < 0) {
			throw new PedidoInvalidoException("Equipa inválida.");
		}
		ctx.idEquipa = parmPedido.getIdEquipa();

		List<String> codigos = lista(parmPedido.getCampos());
		if (codigos.isEmpty()) {
			throw new PedidoInvalidoException("Não há colunas mapeadas.");
		}
		for (String codigo : codigos) {
			CampoImportacao campo = CampoImportacao.porCodigo(codigo, ctx.tipo);
			if (campo == null) {
				throw new PedidoInvalidoException("Campo desconhecido para o tipo de importação.");
			}
			if (ctx.campos.contains(campo)) {
				throw new PedidoInvalidoException("Campo mapeado mais de uma vez.");
			}
			ctx.campos.add(campo);
		}
		if (!ctx.campos.contains(CampoImportacao.NOME)) {
			throw new PedidoInvalidoException("O campo Nome tem de estar mapeado.");
		}

		if (!ctx.isJogador()) {
			String funcao = CampoImportacao.limpa(parmPedido.getFuncao());
			if (!funcao.isEmpty()) {
				ctx.funcaoFixa = CampoImportacao.funcao(funcao);
				if (ctx.funcaoFixa == null) {
					throw new PedidoInvalidoException("Função inválida (Treinador, T-adjunto ou Seccionista).");
				}
			}
			if (ctx.idEquipa > 0 && ctx.funcaoFixa.isEmpty() && !ctx.campos.contains(CampoImportacao.FUNCAO)) {
				throw new PedidoInvalidoException("Falta a função do staff para associar à equipa.");
			}
		}

		List<PedidoImportacaoData.Linha> linhas = lista(parmPedido.getLinhas());
		if (linhas.isEmpty() || linhas.size() > MAX_LINHAS) {
			throw new PedidoInvalidoException("O pedido tem de ter entre 1 e " + MAX_LINHAS + " linhas.");
		}
		Set<Integer> numeros = new HashSet<>();
		for (PedidoImportacaoData.Linha linha : linhas) {
			if (linha == null || lista(linha.getValores()).size() != ctx.campos.size()) {
				throw new PedidoInvalidoException("Linha com um número de valores diferente do número de campos.");
			}
			if (!numeros.add(linha.getLinha())) {
				throw new PedidoInvalidoException("Número de linha repetido no pedido.");
			}
			if (parmGravacao) {
				boolean criar = CRIAR.equals(linha.getAcao());
				boolean atualizar = ATUALIZAR.equals(linha.getAcao()) && linha.getIdExistente() > 0;
				if (!criar && !atualizar) {
					throw new PedidoInvalidoException("Ação de gravação inválida.");
				}
				for (String codigo : lista(linha.getAtualizar())) {
					CampoImportacao campo = CampoImportacao.porCodigo(codigo, ctx.tipo);
					if (campo == null || campo == CampoImportacao.FUNCAO || !ctx.campos.contains(campo)) {
						throw new PedidoInvalidoException("Campo a atualizar inválido.");
					}
				}
			}
		}
		return ctx;
	}

	/** Valida cada celula (B4); erros e avisos vao para a linha. @return valores validos e nao vazios */
	private static Map<CampoImportacao, String> valoresLinha(Contexto ctx, PedidoImportacaoData.Linha parmLinha,
			ResultadoImportacaoData.Linha r) {
		Map<CampoImportacao, String> valores = new EnumMap<>(CampoImportacao.class);
		for (int i = 0; i < ctx.campos.size(); i++) {
			CampoImportacao campo = ctx.campos.get(i);
			CampoImportacao.Valor valor = campo.valida(ctx.tipo, parmLinha.getValores().get(i));
			if (valor.erro() != null) {
				r.getErros().add(new ResultadoImportacaoData.Mensagem(campo.getCodigo(), valor.erro()));
				continue;
			}
			if (!valor.valor().isEmpty()) {
				valores.put(campo, valor.valor());
			}
			if (valor.aviso() != null) {
				r.getAvisos().add(new ResultadoImportacaoData.Mensagem(campo.getCodigo(), valor.aviso()));
			}
		}
		// R14: staff com equipa e funcao vinda da coluna
		if (!ctx.isJogador() && ctx.idEquipa > 0 && ctx.funcaoFixa.isEmpty()
				&& !valores.containsKey(CampoImportacao.FUNCAO) && r.getErros().stream()
						.noneMatch(m -> CampoImportacao.FUNCAO.getCodigo().equals(m.getCampo()))) {
			r.getErros().add(new ResultadoImportacaoData.Mensagem(CampoImportacao.FUNCAO.getCodigo(),
					"A função é obrigatória para associar à equipa."));
		}
		return valores;
	}

	private static String funcaoDaLinha(Contexto ctx, Map<CampoImportacao, String> parmValores) {
		return ctx.funcaoFixa.isEmpty() ? parmValores.get(CampoImportacao.FUNCAO) : ctx.funcaoFixa;
	}

	/** R31: mesma licenca, NIF ou nome+data noutra linha anterior. @return a linha anterior, ou {@code null} */
	private static Integer repetidaNoFicheiro(Contexto ctx, Map<CampoImportacao, String> parmValores, int parmLinha) {
		List<String> chaves = new ArrayList<>();
		if (parmValores.containsKey(CampoImportacao.LICENCA)) {
			chaves.add("L" + parmValores.get(CampoImportacao.LICENCA));
		}
		if (parmValores.containsKey(CampoImportacao.NIF)) {
			chaves.add("N" + parmValores.get(CampoImportacao.NIF));
		}
		if (parmValores.containsKey(CampoImportacao.DATA_NASCIMENTO)) {
			chaves.add("D" + parmValores.get(CampoImportacao.DATA_NASCIMENTO) + "|"
					+ CampoImportacao.normalizaNome(parmValores.get(CampoImportacao.NOME)));
		}
		for (String chave : chaves) {
			Integer anterior = ctx.chaves.get(chave);
			if (anterior != null) {
				return anterior;
			}
		}
		for (String chave : chaves) {
			ctx.chaves.put(chave, parmLinha);
		}
		return null;
	}

	// ==================== identificacao (R25-R30, V7, V8) ====================

	private static Identificacao identifica(Contexto ctx, Map<CampoImportacao, String> parmValores) {
		String licenca = parmValores.get(CampoImportacao.LICENCA);
		String data = parmValores.get(CampoImportacao.DATA_NASCIMENTO);
		Set<String> nomes = new HashSet<>();
		for (CampoImportacao campo : List.of(CampoImportacao.NOME, CampoImportacao.NOME_COMPLETO)) {
			String nome = CampoImportacao.normalizaNome(parmValores.get(campo));
			if (!nome.isEmpty()) {
				nomes.add(nome);
			}
		}

		List<Pessoa> porLicenca = licenca == null ? List.of()
				: filtra(ctx, p -> licenca.equals(p.valor(CampoImportacao.LICENCA)));
		if (porLicenca.size() > 1) {
			return varios(porLicenca);
		}

		boolean semIdentificacao;
		if (ctx.isJogador()) {
			String nif = parmValores.get(CampoImportacao.NIF);
			List<Pessoa> porNif = nif == null ? List.of() : filtra(ctx, p -> nif.equals(p.valor(CampoImportacao.NIF)));
			if (porNif.size() > 1) {
				return varios(porNif);
			}
			if (porLicenca.size() == 1 && porNif.size() == 1 && porLicenca.get(0) != porNif.get(0)) {
				List<Pessoa> ambos = new ArrayList<>(porLicenca);
				ambos.addAll(porNif);
				return varios(ambos);
			}
			if (porLicenca.size() == 1) {
				return new Identificacao(ATUALIZAR, porLicenca, "");
			}
			if (porNif.size() == 1) {
				return new Identificacao(ATUALIZAR, porNif, "");
			}
			semIdentificacao = licenca == null && nif == null && data == null;
		} else {
			if (porLicenca.size() == 1) {
				return new Identificacao(ATUALIZAR, porLicenca, "");
			}
			String email = parmValores.get(CampoImportacao.EMAIL);
			if (email != null) {
				List<Pessoa> porEmail = filtra(ctx, p -> email.equalsIgnoreCase(p.valor(CampoImportacao.EMAIL)));
				if (porEmail.size() > 1) {
					return varios(porEmail);
				}
				if (porEmail.size() == 1) {
					return new Identificacao(POSSIVEL_DUPLICADO, porEmail, "");
				}
			}
			semIdentificacao = licenca == null && email == null && data == null;
		}

		List<Pessoa> porNome;
		if (data != null) {
			porNome = filtra(ctx, p -> data.equals(p.valor(CampoImportacao.DATA_NASCIMENTO))
					&& p.nomes.stream().anyMatch(nomes::contains));
		} else if (semIdentificacao) {
			// R30/V7: so pelo nome
			porNome = filtra(ctx, p -> p.nomes.stream().anyMatch(nomes::contains));
		} else {
			porNome = List.of();
		}
		if (porNome.size() > 1) {
			return varios(porNome);
		}
		if (porNome.size() == 1) {
			return new Identificacao(POSSIVEL_DUPLICADO, porNome, "");
		}
		return new Identificacao(CRIAR, List.of(), "");
	}

	private static List<Pessoa> filtra(Contexto ctx, Predicate<Pessoa> parmFiltro) {
		return ctx.pessoas.stream().filter(parmFiltro).collect(Collectors.toList());
	}

	private static Identificacao varios(List<Pessoa> parmPessoas) {
		Set<Integer> ids = new LinkedHashSet<>();
		parmPessoas.forEach(p -> ids.add(p.id));
		String lista = ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
		return new Identificacao(ERRO, List.of(), "Corresponde a vários registos (ids " + lista + ")");
	}

	/** R32: staff ligado a jogador so atualiza a licenca de staff. */
	private static boolean editavel(Pessoa parmPessoa, CampoImportacao parmCampo) {
		return !parmPessoa.ligadoJogador || parmCampo == CampoImportacao.LICENCA;
	}

	/** R33: campos mapeados com valor valido diferente do atual. */
	private static List<ResultadoImportacaoData.Diferenca> diferencas(Pessoa parmPessoa,
			Map<CampoImportacao, String> parmValores) {
		List<ResultadoImportacaoData.Diferenca> diferencas = new ArrayList<>();
		for (Map.Entry<CampoImportacao, String> entrada : parmValores.entrySet()) {
			CampoImportacao campo = entrada.getKey();
			if (campo.getColuna() == null) {
				continue;
			}
			String atual = parmPessoa.valor(campo);
			if (!entrada.getValue().equals(atual)) {
				diferencas.add(new ResultadoImportacaoData.Diferenca(campo.getCodigo(), campo.getRotulo(), atual,
						entrada.getValue(), editavel(parmPessoa, campo)));
			}
		}
		return diferencas;
	}

	private static ResultadoImportacaoData.Pessoa candidato(Pessoa parmPessoa) {
		ResultadoImportacaoData.Pessoa candidato = new ResultadoImportacaoData.Pessoa();
		candidato.setId(parmPessoa.id);
		candidato.setNome(parmPessoa.valor(CampoImportacao.NOME));
		candidato.setNomeCompleto(parmPessoa.valor(CampoImportacao.NOME_COMPLETO));
		String data = parmPessoa.valor(CampoImportacao.DATA_NASCIMENTO);
		candidato.setDataNascimento(data.isEmpty() ? 0 : Integer.parseInt(data));
		candidato.setInativo(parmPessoa.inativo);
		candidato.setLigadoJogador(parmPessoa.ligadoJogador);
		return candidato;
	}

	private static Pessoa novaPessoa(int parmId, Map<CampoImportacao, String> parmValores) {
		Pessoa pessoa = new Pessoa();
		pessoa.id = parmId;
		parmValores.forEach((campo, valor) -> {
			if (campo.getColuna() != null) {
				pessoa.valores.put(campo, valor);
			}
		});
		pessoa.calculaNomes();
		return pessoa;
	}

	// ==================== leituras (D5) ====================

	private void carregaContexto(Connection conn, Contexto ctx) throws SQLException {
		if (ctx.idEquipa > 0 && !equipaDaEpocaAtual(conn, ctx.idEquipa, ctx.tenant)) {
			throw new PedidoInvalidoException("A equipa não é da época atual do clube.");
		}
		ctx.pessoas = ctx.isJogador() ? carregaJogadores(conn, ctx.tenant) : carregaStaff(conn, ctx.tenant);
		if (ctx.idEquipa > 0) {
			ctx.membros = membrosEquipa(conn, ctx);
		}
	}

	private boolean equipaDaEpocaAtual(Connection conn, int parmIdEquipa, int parmTenant) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select 1 from escalao_epoca ee "
				+ "inner join epoca e on e.id=ee.id_epoca where ee.id=? and e.tenant_id=? and e.estado='1' LIMIT 1");
		preparedStatement.setInt(1, parmIdEquipa);
		preparedStatement.setInt(2, parmTenant);
		return preparedStatement.executeQuery().next();
	}

	private List<Pessoa> carregaJogadores(Connection conn, int parmTenant) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("SELECT id, nome, nome_completo, data_nascimento, "
				+ "email, telemovel, pai_nome, pai_email, pai_telemovel, mae_nome, mae_email, mae_telemovel, morada, "
				+ "cidade, codigo_postal, observacoes, numero, nif, cc, `licença`, estado, 0 as ligado "
				+ "FROM jogador WHERE tenant_id=?");
		preparedStatement.setInt(1, parmTenant);
		return lerPessoas(preparedStatement.executeQuery(), CampoImportacao.JOGADOR);
	}

	/** V11: id_jogador NULL conta como staff puro; staff ligado com os dados pessoais do jogador (R32). */
	private List<Pessoa> carregaStaff(Connection conn, int parmTenant) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select s.id as id, s.nome as nome, "
				+ "s.nome_completo as nome_completo, s.telemovel as telemovel, s.email as email, s.morada as morada, "
				+ "s.codigo_postal as codigo_postal, s.data_nascimento as data_nascimento, s.`licença` as `licença`, "
				+ "s.estado as estado, 0 as ligado from staff s "
				+ "where s.tenant_id=? and (s.id_jogador = 0 or s.id_jogador is null) "
				+ "union all "
				+ "select s.id, j.nome, j.nome_completo, j.telemovel, j.email, j.morada, j.codigo_postal, "
				+ "j.data_nascimento, s.`licença`, s.estado, 1 from staff s inner join jogador j on j.id=s.id_jogador "
				+ "where s.tenant_id=? and j.tenant_id=? and s.id_jogador <> 0");
		preparedStatement.setInt(1, parmTenant);
		preparedStatement.setInt(2, parmTenant);
		preparedStatement.setInt(3, parmTenant);
		return lerPessoas(preparedStatement.executeQuery(), CampoImportacao.STAFF);
	}

	private static List<Pessoa> lerPessoas(ResultSet rs, String parmTipo) throws SQLException {
		List<Pessoa> pessoas = new ArrayList<>();
		while (rs.next()) {
			Pessoa pessoa = new Pessoa();
			pessoa.id = rs.getInt("id");
			for (CampoImportacao campo : CampoImportacao.values()) {
				if (campo.getColuna() != null && campo.existeEm(parmTipo)) {
					pessoa.valores.put(campo, lerValor(rs, campo));
				}
			}
			pessoa.inativo = "0".equals(CampoImportacao.limpa(rs.getString("estado")));
			pessoa.ligadoJogador = rs.getInt("ligado") == 1;
			pessoa.calculaNomes();
			pessoas.add(pessoa);
		}
		return pessoas;
	}

	/** Valor atual normalizado como na validacao: texto com trim; numeros sem zeros, 0/NULL = "". */
	private static String lerValor(ResultSet rs, CampoImportacao parmCampo) throws SQLException {
		if (parmCampo.isNumerico()) {
			long numero = rs.getLong(parmCampo.getColuna());
			return numero == 0 ? "" : String.valueOf(numero);
		}
		return CampoImportacao.limpa(rs.getString(parmCampo.getColuna()));
	}

	private Set<Integer> membrosEquipa(Connection conn, Contexto ctx) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(ctx.isJogador()
				? "select id_jogador from escalao_epoca_jogador where id_escalao_epoca=?"
				: "select id_staff from escalao_epoca_staff where id_escalao_epoca=?");
		preparedStatement.setInt(1, ctx.idEquipa);
		ResultSet rs = preparedStatement.executeQuery();
		Set<Integer> membros = new HashSet<>();
		while (rs.next()) {
			membros.add(rs.getInt(1));
		}
		return membros;
	}

	// ==================== utilitarios ====================

	private static <T> List<T> lista(List<T> parmLista) {
		return parmLista == null ? List.of() : parmLista;
	}

	private static String texto(Map<CampoImportacao, String> parmValores, CampoImportacao parmCampo) {
		return parmValores.getOrDefault(parmCampo, "");
	}

	private static int numero(Map<CampoImportacao, String> parmValores, CampoImportacao parmCampo) {
		String valor = parmValores.get(parmCampo);
		return valor == null ? 0 : Integer.parseInt(valor);
	}

	private static ResultadoImportacaoData.Linha erro(ResultadoImportacaoData.Linha r, String parmMensagem) {
		r.setEstado(ERRO);
		r.setMensagem(parmMensagem);
		return r;
	}

	private static ResultadoImportacaoData.Linha mudou(ResultadoImportacaoData.Linha r) {
		r.setEstado(MUDOU);
		r.setMensagem("Mudou desde a pré-visualização");
		return r;
	}

	/** Sem a mensagem nem a causa do SQLException (podem citar valores, R4). */
	private static IllegalStateException erroBD(String parmMetodo, SQLException e) {
		log.error("ImportacaoHelper | {} | Erro de BD sqlState:{} errorCode:{}", parmMetodo, e.getSQLState(),
				e.getErrorCode());
		return new IllegalStateException("Erro de base de dados na importação.");
	}
}
