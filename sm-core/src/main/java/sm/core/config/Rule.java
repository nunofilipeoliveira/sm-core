package sm.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Regra de limitacao de trafego aplicada a um conjunto de caminhos.
 *
 * <ul>
 *   <li>{@code capacity} - tokens disponiveis em rajada (burst);</li>
 *   <li>{@code refillPerSecond} - tokens repostos por segundo (ritmo sustentado);</li>
 *   <li>{@code maxConcurrentRequests} - pedidos simultaneos por cliente (0 = sem limite);</li>
 *   <li>{@code paths} - prefixos de caminho aos quais a regra se aplica.</li>
 * </ul>
 */
public class Rule {

    private String name = "rule";
    private long capacity = 60;
    private double refillPerSecond = 10;
    private int maxConcurrentRequests = 0;
    private List<String> paths = new ArrayList<>();

    public Rule() {
        // construtor vazio para binding do Spring
    }

    public static Rule of(String name, long capacity, double refillPerSecond,
            int maxConcurrentRequests, String... paths) {
        Rule rule = new Rule();
        rule.name = name;
        rule.capacity = capacity;
        rule.refillPerSecond = refillPerSecond;
        rule.maxConcurrentRequests = maxConcurrentRequests;
        rule.paths = new ArrayList<>(Arrays.asList(paths));
        return rule;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public double getRefillPerSecond() {
        return refillPerSecond;
    }

    public void setRefillPerSecond(double refillPerSecond) {
        this.refillPerSecond = refillPerSecond;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    /** Indica se o caminho pertence a esta regra (comparacao por prefixo). */
    public boolean matches(String path) {
        if (path == null || paths == null) {
            return false;
        }
        for (String prefix : paths) {
            if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
