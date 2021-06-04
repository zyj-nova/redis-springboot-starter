package bjtu.zyj.redisspringbootstarter.bean;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class Item implements Serializable {
    private static final long serialVersionUID = 10L;
    private int id;
    private int total;
}
