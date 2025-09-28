package sm.core.data;

import java.util.ArrayList;

public class ConvocatoriaData {
    
    private int id;
    private ArrayList<Integer> jogadoresConvocados;

    public ConvocatoriaData(int id, ArrayList<Integer> jogadoresConvocados) {
        super();
        this.id = id;
        this.jogadoresConvocados = jogadoresConvocados;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public ArrayList<Integer> getJogadoresConvocados() {
        return jogadoresConvocados;
    }
    public void setJogadoresConvocados(ArrayList<Integer> jogadoresConvocados) {
        this.jogadoresConvocados = jogadoresConvocados;
    }
}
