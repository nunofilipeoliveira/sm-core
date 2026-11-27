package sm.core.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Calculo automatico dos balnearios (desenho balnearios §6.1, B7). Puro: sem Spring, sem SQL, sem relogio. O mesmo
 * criterio de cruzamento serve o calculo e os avisos de conflito. Deterministico: tudo e ordenado e os mapas sao
 * TreeMap, por isso o toString() dos records serve de base a assinatura (B8).
 */
public final class CalculoBalnearios {

	static final String TREINO = "TREINO";
	static final String JOGO = "JOGO";
	static final String EQUIPA = "EQUIPA";
	static final String ADVERSARIO = "ADVERSARIO";
	static final String PREFERENCIA = "PREFERENCIA";
	static final String ORDEM_CLUBE = "ORDEM";
	/** Regra de uso de um balneario (V18, P21). */
	static final String TODOS = "TODOS";
	static final String DEDICADO = "DEDICADO";
	static final String EXCLUSAO = "EXCLUSAO";
	/** Motivo de um pedido sem proposta (P33). */
	static final String SEM_LIVRE = "SEM_LIVRE";
	static final String SEM_PERMITIDO = "SEM_PERMITIDO";

	private CalculoBalnearios() {
	}

	/** uso DEDICADO ou EXCLUSAO; escaloes e adversarios = a lista ("Adversários" a parte). */
	record Restricao(String uso, TreeSet<Integer> escaloes, boolean adversarios) {
	}

	/** So ativos; libertacao ja efetiva (propria ou a global). */
	record Balneario(int id, int ordem, int libertacao) {
	}

	/** Um evento (sessao de treino ou lado de jogo) a servir; inicio/fim = a janela, em minutos do dia. */
	record Pedido(String tipo, int id, String lado, int data, int inicio, int fim, int idEscalao) {
		String chave() {
			return tipo + ":" + id + (lado == null ? "" : ":" + lado);
		}
	}

	record Ocupacao(String chave, int idBalneario, int data, int inicio, int fim) {
	}

	record Regras(TreeMap<Integer, List<Integer>> preferenciasEscalao, List<Integer> preferenciasAdversario,
			List<Integer> prioridade, TreeMap<Integer, String> nomesEscalao, TreeMap<Integer, Restricao> restricoes) {
	}

	record Entrada(List<Pedido> pedidos, List<Ocupacao> ocupadas, List<Balneario> balnearios, Regras regras) {
	}

	/** idBalneario null = sem livre (origem null). */
	record Atribuicao(Pedido pedido, Integer idBalneario, String origem) {
	}

	/** O mesmo balneario so serve dois intervalos que nao se cruzam, com a libertacao entre eles. */
	static boolean cruzam(int aIni, int aFim, int bIni, int bFim, int libertacao) {
		return aIni < bFim + libertacao && bIni < aFim + libertacao;
	}

	/**
	 * P9/V4 (a unica ordem do calculo): dia, jogos antes dos treinos, posicao do escalao na prioridade (fora da lista:
	 * depois, por nome sem maiusculas e id), inicio da janela, id, lado (EQUIPA antes de ADVERSARIO).
	 */
	static Comparator<Pedido> ordem(Regras r) {
		List<Integer> prioridade = r.prioridade() == null ? List.of() : r.prioridade();
		Map<Integer, String> nomes = r.nomesEscalao() == null ? new TreeMap<>() : r.nomesEscalao();
		Comparator<Pedido> escalao = (a, b) -> {
			int pa = prioridade.indexOf(a.idEscalao());
			int pb = prioridade.indexOf(b.idEscalao());
			if (pa >= 0 || pb >= 0) {
				return pa < 0 ? 1 : pb < 0 ? -1 : Integer.compare(pa, pb);
			}
			int n = nomes.getOrDefault(a.idEscalao(), "").toLowerCase()
					.compareTo(nomes.getOrDefault(b.idEscalao(), "").toLowerCase());
			return n != 0 ? n : Integer.compare(a.idEscalao(), b.idEscalao());
		};
		return Comparator.comparingInt(Pedido::data).thenComparingInt(p -> JOGO.equals(p.tipo()) ? 0 : 1)
				.thenComparing(escalao).thenComparingInt(Pedido::inicio).thenComparingInt(Pedido::id)
				.thenComparingInt(p -> ADVERSARIO.equals(p.lado()) ? 1 : 0);
	}

	/** R51/R52: o lado ADVERSARIO e "Adversarios" (o idEscalao da casa so serve a prioridade); idEscalao null = sem equipa. */
	static boolean permite(Restricao r, String lado, Integer idEscalao) {
		if (r == null || TODOS.equals(r.uso())) {
			return true;
		}
		boolean naLista = ADVERSARIO.equals(lado) ? r.adversarios() : idEscalao != null && r.escaloes().contains(idEscalao);
		return DEDICADO.equals(r.uso()) ? naLista : !naLista;
	}

	/** Os balnearios que o pedido pode usar, pela ordem recebida (R55: o calculo nunca viola uma regra). */
	static List<Balneario> permitidos(List<Balneario> parmOrdenados, Regras r, Pedido p) {
		Map<Integer, Restricao> restricoes = r.restricoes() == null ? Map.of() : r.restricoes();
		List<Balneario> lista = new ArrayList<>();
		for (Balneario b : parmOrdenados) {
			if (permite(restricoes.get(b.id()), p.lado(), p.idEscalao())) {
				lista.add(b);
			}
		}
		return lista;
	}

	static List<Atribuicao> calcula(Entrada e) {
		List<Pedido> pedidos = new ArrayList<>(e.pedidos());
		pedidos.sort(ordem(e.regras()));
		List<Balneario> balnearios = new ArrayList<>(e.balnearios());
		balnearios.sort(Comparator.comparingInt(Balneario::ordem).thenComparingInt(Balneario::id));
		List<Ocupacao> ocupadas = new ArrayList<>(e.ocupadas());
		List<Atribuicao> resultado = new ArrayList<>();
		for (Pedido p : pedidos) {
			List<Balneario> podem = permitidos(balnearios, e.regras(), p);
			Map<Integer, Balneario> porId = new TreeMap<>(); // so os permitidos: uma preferencia incompativel e ignorada (R53)
			podem.forEach(b -> porId.put(b.id(), b));
			List<Integer> preferencias = ADVERSARIO.equals(p.lado()) ? e.regras().preferenciasAdversario()
					: e.regras().preferenciasEscalao() == null ? null : e.regras().preferenciasEscalao().get(p.idEscalao());
			Set<Integer> preferidos = new LinkedHashSet<>();
			if (preferencias != null) {
				for (Integer id : preferencias) {
					if (id != null && porId.containsKey(id)) {
						preferidos.add(id);
					}
				}
			}
			Atribuicao escolhida = null;
			for (Integer id : preferidos) {
				if (livre(porId.get(id), p, ocupadas)) {
					escolhida = new Atribuicao(p, id, PREFERENCIA);
					break;
				}
			}
			if (escolhida == null) {
				for (Balneario b : podem) {
					if (!preferidos.contains(b.id()) && livre(b, p, ocupadas)) {
						escolhida = new Atribuicao(p, b.id(), ORDEM_CLUBE);
						break;
					}
				}
			}
			if (escolhida == null) {
				resultado.add(new Atribuicao(p, null, null));
			} else {
				// o outro lado do mesmo jogo tem a mesma janela: nunca fica com o mesmo (CA15)
				ocupadas.add(new Ocupacao(p.chave(), escolhida.idBalneario(), p.data(), p.inicio(), p.fim()));
				resultado.add(escolhida);
			}
		}
		return resultado;
	}

	private static boolean livre(Balneario b, Pedido p, List<Ocupacao> parmOcupadas) {
		for (Ocupacao o : parmOcupadas) {
			if (o.idBalneario() == b.id() && o.data() == p.data()
					&& cruzam(p.inicio(), p.fim(), o.inicio(), o.fim(), b.libertacao())) {
				return false;
			}
		}
		return true;
	}

	/** R14/CA9: pares (i < j) com o mesmo balneario, a mesma data, eventos diferentes e janelas que se cruzam. */
	static List<int[]> conflitos(List<Ocupacao> o, Map<Integer, Integer> libertacao) {
		List<int[]> pares = new ArrayList<>();
		for (int i = 0; i < o.size(); i++) {
			for (int j = i + 1; j < o.size(); j++) {
				Ocupacao a = o.get(i);
				Ocupacao b = o.get(j);
				if (a.idBalneario() == b.idBalneario() && a.data() == b.data() && !a.chave().equals(b.chave())
						&& cruzam(a.inicio(), a.fim(), b.inicio(), b.fim(), libertacao.getOrDefault(a.idBalneario(), 0))) {
					pares.add(new int[] { i, j });
				}
			}
		}
		return pares;
	}

	// ==================== auto-verificacao (§6.3): java -cp target/classes sm.core.helper.CalculoBalnearios ====================

	private static final Balneario A = new Balneario(1, 1, 0);
	private static final Balneario B = new Balneario(2, 2, 0);
	private static final Balneario C = new Balneario(3, 3, 0);
	private static final int DIA = 20261202;

	private static Regras regras(Map<Integer, List<Integer>> parmPref, List<Integer> parmAdv, List<Integer> parmPrioridade,
			Map<Integer, String> parmNomes) {
		return regras(parmPref, parmAdv, parmPrioridade, parmNomes, Map.of());
	}

	private static Regras regras(Map<Integer, List<Integer>> parmPref, List<Integer> parmAdv, List<Integer> parmPrioridade,
			Map<Integer, String> parmNomes, Map<Integer, Restricao> parmRestricoes) {
		return new Regras(new TreeMap<>(parmPref), parmAdv, parmPrioridade, new TreeMap<>(parmNomes),
				new TreeMap<>(parmRestricoes));
	}

	private static Restricao restricao(String parmUso, boolean parmAdversarios, Integer... parmEscaloes) {
		return new Restricao(parmUso, new TreeSet<>(List.of(parmEscaloes)), parmAdversarios);
	}

	private static void confirma(String parmCaso, boolean parmOk) {
		if (!parmOk) {
			throw new AssertionError(parmCaso);
		}
	}

	private static Pedido treino(int parmId, int parmEscalao, int parmIni, int parmFim) {
		return new Pedido(TREINO, parmId, null, DIA, parmIni, parmFim, parmEscalao);
	}

	private static Pedido jogo(int parmId, String parmLado, int parmEscalao, int parmIni, int parmFim) {
		return new Pedido(JOGO, parmId, parmLado, DIA, parmIni, parmFim, parmEscalao);
	}

	private static Atribuicao de(List<Atribuicao> parmR, String parmChave) {
		for (Atribuicao a : parmR) {
			if (a.pedido().chave().equals(parmChave)) {
				return a;
			}
		}
		throw new AssertionError("sem atribuicao para " + parmChave);
	}

	private static void confere(String parmCaso, List<Atribuicao> parmR, String parmChave, Integer parmId, String parmOrigem) {
		Atribuicao a = de(parmR, parmChave);
		if (!java.util.Objects.equals(a.idBalneario(), parmId) || !java.util.Objects.equals(a.origem(), parmOrigem)) {
			throw new AssertionError(parmCaso + ": " + parmChave + " esperado " + parmId + "/" + parmOrigem + ", obtido "
					+ a.idBalneario() + "/" + a.origem());
		}
	}

	private static <T> List<T> baralha(List<T> parmLista) {
		List<T> l = new ArrayList<>(parmLista);
		Collections.reverse(l);
		if (l.size() > 2) {
			Collections.swap(l, 0, l.size() / 2);
		}
		return l;
	}

	public static void main(String[] args) {
		Regras vazias = regras(Map.of(), List.of(), List.of(), Map.of(13, "Sub-13", 15, "Sub-15", 11, "Sub-11"));
		List<Entrada> casos = new ArrayList<>();

		// 1 Preferencia
		Entrada e1 = new Entrada(List.of(treino(1, 15, 1050, 1200)), List.of(), List.of(A, B, C),
				regras(Map.of(15, List.of(2)), List.of(), List.of(), Map.of(15, "Sub-15")));
		confere("1 Preferência", calcula(e1), "TREINO:1", 2, PREFERENCIA);
		casos.add(e1);

		// 2 Ordem
		Entrada e2 = new Entrada(List.of(treino(2, 13, 1050, 1200)), List.of(), List.of(A, B, C), vazias);
		confere("2 Ordem", calcula(e2), "TREINO:2", 1, ORDEM_CLUBE);
		casos.add(e2);

		// 3 Jogos primeiro
		Entrada e3 = new Entrada(List.of(treino(3, 13, 1050, 1200), jogo(30, ADVERSARIO, 11, 900, 1110),
				jogo(30, EQUIPA, 11, 900, 1110)), List.of(), List.of(A, B), vazias);
		List<Atribuicao> r3 = calcula(e3);
		confere("3 Jogos primeiro", r3, "JOGO:30:EQUIPA", 1, ORDEM_CLUBE);
		confere("3 Jogos primeiro", r3, "JOGO:30:ADVERSARIO", 2, ORDEM_CLUBE);
		confere("3 Jogos primeiro", r3, "TREINO:3", null, null);
		casos.add(e3);

		// 4 Prioridade
		Entrada e4 = new Entrada(List.of(treino(4, 13, 1050, 1200), treino(5, 15, 1080, 1200)), List.of(), List.of(A),
				regras(Map.of(), List.of(), List.of(15, 13), Map.of(13, "Sub-13", 15, "Sub-15")));
		List<Atribuicao> r4 = calcula(e4);
		confere("4 Prioridade", r4, "TREINO:5", 1, ORDEM_CLUBE);
		confere("4 Prioridade", r4, "TREINO:4", null, null);
		casos.add(e4);

		// 5 Alfabetica
		Entrada e5 = new Entrada(List.of(treino(9, 9, 1000, 1100), treino(7, 7, 1050, 1150)), List.of(), List.of(A),
				regras(Map.of(), List.of(), List.of(), Map.of(9, "Juniores", 7, "Infantis")));
		List<Atribuicao> r5 = calcula(e5);
		confere("5 Alfabética", r5, "TREINO:7", 1, ORDEM_CLUBE);
		confere("5 Alfabética", r5, "TREINO:9", null, null);
		casos.add(e5);

		// 6 Libertacao
		Balneario a15 = new Balneario(1, 1, 15);
		Entrada e6 = new Entrada(List.of(treino(61, 13, 600, 700), treino(62, 13, 710, 800)), List.of(), List.of(a15, B),
				vazias);
		List<Atribuicao> r6 = calcula(e6);
		confere("6 Libertação", r6, "TREINO:61", 1, ORDEM_CLUBE);
		confere("6 Libertação", r6, "TREINO:62", 2, ORDEM_CLUBE);
		Entrada e6b = new Entrada(List.of(treino(61, 13, 600, 700), treino(62, 13, 715, 800)), List.of(), List.of(a15, B),
				vazias);
		confere("6 Libertação (715)", calcula(e6b), "TREINO:62", 1, ORDEM_CLUBE);
		casos.add(e6);
		casos.add(e6b);

		// 7 Manual
		Entrada e7 = new Entrada(List.of(treino(71, 13, 650, 750)), List.of(new Ocupacao("TREINO:70", 1, DIA, 600, 700)),
				List.of(A, B, C), vazias);
		confere("7 Manual", calcula(e7), "TREINO:71", 2, ORDEM_CLUBE);
		casos.add(e7);

		// 8 Lados diferentes
		Entrada e8 = new Entrada(List.of(jogo(80, ADVERSARIO, 15, 900, 1110), jogo(80, EQUIPA, 15, 900, 1110)), List.of(),
				List.of(A, B, C), regras(Map.of(), List.of(1), List.of(), Map.of(15, "Sub-15")));
		List<Atribuicao> r8 = calcula(e8);
		confere("8 Lados diferentes", r8, "JOGO:80:EQUIPA", 1, ORDEM_CLUBE);
		confere("8 Lados diferentes", r8, "JOGO:80:ADVERSARIO", 2, ORDEM_CLUBE);
		casos.add(e8);

		Map<Integer, String> nomes = Map.of(13, "Sub-13", 15, "Sub-15");

		// 10 Exclusao
		Entrada e10 = new Entrada(List.of(treino(101, 13, 1050, 1200)), List.of(), List.of(A, B, C),
				regras(Map.of(), List.of(), List.of(), nomes, Map.of(1, restricao(EXCLUSAO, false, 13))));
		confere("10 Exclusão", calcula(e10), "TREINO:101", 2, ORDEM_CLUBE);
		casos.add(e10);

		// 11 Dedicacao
		Entrada e11 = new Entrada(List.of(treino(111, 13, 600, 700), treino(112, 15, 900, 1000)), List.of(),
				List.of(A, B, C), regras(Map.of(), List.of(), List.of(), nomes, Map.of(1, restricao(DEDICADO, false, 15))));
		List<Atribuicao> r11 = calcula(e11);
		confere("11 Dedicação", r11, "TREINO:111", 2, ORDEM_CLUBE);
		confere("11 Dedicação", r11, "TREINO:112", 1, ORDEM_CLUBE);
		casos.add(e11);

		// 12 Adversarios: o escalao da casa nao conta para o lado ADVERSARIO
		Entrada e12 = new Entrada(List.of(jogo(120, EQUIPA, 15, 900, 1110), jogo(120, ADVERSARIO, 15, 900, 1110)),
				List.of(), List.of(A, B, C),
				regras(Map.of(), List.of(), List.of(), nomes, Map.of(1, restricao(EXCLUSAO, false, 15))));
		List<Atribuicao> r12 = calcula(e12);
		confere("12 Adversários", r12, "JOGO:120:EQUIPA", 2, ORDEM_CLUBE);
		confere("12 Adversários", r12, "JOGO:120:ADVERSARIO", 1, ORDEM_CLUBE);
		casos.add(e12);
		Entrada e12b = new Entrada(List.of(jogo(121, EQUIPA, 15, 900, 1110), jogo(121, ADVERSARIO, 15, 900, 1110)),
				List.of(), List.of(A, C), regras(Map.of(), List.of(), List.of(), nomes,
						Map.of(1, restricao(EXCLUSAO, true), 3, restricao(DEDICADO, true))));
		List<Atribuicao> r12b = calcula(e12b);
		confere("12 Adversários (b)", r12b, "JOGO:121:EQUIPA", 1, ORDEM_CLUBE);
		confere("12 Adversários (b)", r12b, "JOGO:121:ADVERSARIO", 3, ORDEM_CLUBE);
		casos.add(e12b);

		// 13 Preferencia incompativel ignorada
		Entrada e13 = new Entrada(List.of(treino(131, 13, 1050, 1200)), List.of(), List.of(A, B, C),
				regras(Map.of(13, List.of(1, 3)), List.of(), List.of(), nomes, Map.of(1, restricao(EXCLUSAO, false, 13))));
		confere("13 Preferência ignorada", calcula(e13), "TREINO:131", 3, PREFERENCIA);
		casos.add(e13);
		Entrada e13b = new Entrada(List.of(treino(131, 13, 1050, 1200)), List.of(), List.of(A, B, C),
				regras(Map.of(13, List.of(1)), List.of(), List.of(), nomes, Map.of(1, restricao(EXCLUSAO, false, 13))));
		confere("13 Preferência ignorada (só A)", calcula(e13b), "TREINO:131", 2, ORDEM_CLUBE);
		casos.add(e13b);

		// 14 Sem permitido / sem livre
		Regras r14regras = regras(Map.of(), List.of(), List.of(), nomes, Map.of(1, restricao(DEDICADO, false, 15)));
		Pedido p141 = treino(141, 13, 600, 700);
		Pedido p143 = treino(143, 15, 900, 1000);
		Entrada e14 = new Entrada(List.of(p141, treino(142, 15, 900, 1000), p143), List.of(), List.of(A), r14regras);
		List<Atribuicao> r14 = calcula(e14);
		confere("14 Sem permitido", r14, "TREINO:141", null, null);
		confirma("14 Sem permitido: permitidos devia ser vazio", permitidos(List.of(A), r14regras, p141).isEmpty());
		confere("14 Sem livre", r14, "TREINO:142", 1, ORDEM_CLUBE);
		confere("14 Sem livre", r14, "TREINO:143", null, null);
		confirma("14 Sem livre: permitidos nao devia ser vazio", !permitidos(List.of(A), r14regras, p143).isEmpty());
		casos.add(e14);

		// 15 permite
		confirma("15 DEDICADO{15} sem equipa", !permite(restricao(DEDICADO, false, 15), null, null));
		confirma("15 EXCLUSAO{13} sem equipa", permite(restricao(EXCLUSAO, false, 13), null, null));
		confirma("15 EXCLUSAO{adv} adversario", !permite(restricao(EXCLUSAO, true), ADVERSARIO, 13));
		confirma("15 null", permite(null, EQUIPA, 13));
		confirma("15 TODOS", permite(new Restricao(TODOS, new TreeSet<>(), false), null, 13));

		// 9 Determinismo (tambem os casos 10-14): as mesmas entradas com as listas baralhadas dao o mesmo resultado
		for (Entrada e : casos) {
			Entrada baralhada = new Entrada(baralha(e.pedidos()), baralha(e.ocupadas()), baralha(e.balnearios()), e.regras());
			if (!calcula(e).toString().equals(calcula(baralhada).toString())) {
				throw new AssertionError("9 Determinismo: " + e);
			}
		}

		// conflitos (CA9): libertacao 15 no balneario 1
		Map<Integer, Integer> lib = Map.of(1, 15);
		if (conflitos(List.of(new Ocupacao("TREINO:1", 1, DIA, 600, 700), new Ocupacao("TREINO:2", 1, DIA, 710, 760)), lib)
				.size() != 1) {
			throw new AssertionError("conflitos: 710 devia dar 1 par");
		}
		if (!conflitos(List.of(new Ocupacao("TREINO:1", 1, DIA, 600, 700), new Ocupacao("TREINO:2", 1, DIA, 715, 760)), lib)
				.isEmpty()) {
			throw new AssertionError("conflitos: 715 devia dar 0 pares");
		}
		System.out.println("CalculoBalnearios: 15 casos OK");
	}
}
