package sm.core.data;

import java.util.ArrayList;

public class ConvocatoriaData {
    
    private int id;
    private ArrayList<JogadorConvocado> jogadoresConvocatoria;

    public ConvocatoriaData(int id, ArrayList<JogadorConvocado> jogadoresConvocatoria) {
        super();
        this.id = id;
        this.jogadoresConvocatoria = jogadoresConvocatoria;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
 
    public ArrayList<JogadorConvocado> getJogadoresConvocatoria() {
        return jogadoresConvocatoria;
    }
    public void setJogadoresConvocatorias(ArrayList<JogadorConvocado> jogadoresConvocatoria) {
        this.jogadoresConvocatoria = jogadoresConvocatoria;
    }
}
