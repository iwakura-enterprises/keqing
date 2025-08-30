package enterprises.iwakura.keqing;

import lombok.Data;

import java.util.List;

@Data
public class AppConfig {

    private String name;
    private String version;
    private String description;
    private List<String> strings;

}
