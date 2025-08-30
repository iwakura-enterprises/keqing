package enterprises.iwakura.keqing;

import lombok.Data;

import java.util.List;

@Data
public class Config {

    private String appName;
    private String appVersion;
    private String appDescription;
    private InnerConfig innerConfig;
    private List<String> strings;
    @Data
    private class InnerConfig {
        private String someValue;
    }
}
