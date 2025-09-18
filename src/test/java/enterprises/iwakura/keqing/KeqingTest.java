package enterprises.iwakura.keqing;

import com.google.gson.Gson;
import enterprises.iwakura.keqing.impl.GsonSerializer;
import enterprises.iwakura.keqing.impl.PropertiesSerializer;
import enterprises.iwakura.keqing.impl.SnakeYamlSerializer;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class KeqingTest {

    @Test
    @SneakyThrows
    public void testKeqing_properties() {
        var keqing = Keqing.loadFromResources("/lang/lang", '_', new PropertiesSerializer());

        var englishGreeting = keqing.readProperty("", "greeting", String.class);
        var czechGreeting = keqing.readProperty("cs", "greeting", String.class);
        var englishGoodbye = keqing.readProperty("", "goodbye", String.class);
        var missingCzechGoodbye = keqing.readProperty("cs", "goodbye", String.class);

        keqing.setPostfixPriorities(List.of("cs"));

        var defaultGreeting = keqing.readProperty("greeting", String.class);
        var missingDefaultGoodbye = keqing.readProperty("goodbye", String.class);
        var beepBoop = keqing.readProperty("robot.greeting", String.class);

        Assertions.assertEquals("Hello", englishGreeting);
        Assertions.assertEquals("Ahoj", czechGreeting);
        Assertions.assertEquals("Goodbye", englishGoodbye);
        Assertions.assertEquals("Goodbye", missingCzechGoodbye);
        Assertions.assertEquals("Ahoj", defaultGreeting);
        Assertions.assertEquals("Goodbye", missingDefaultGoodbye);
        Assertions.assertEquals("Pip piip", beepBoop);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            keqing.readProperty("greeting", Lang.class); // PropertiesSerializer does not support class mapping
        });
    }

    @Test
    @SneakyThrows
    public void testKeqing_json_test() {
        var keqing = Keqing.loadFromResources("config", '-', new GsonSerializer(new Gson()));
        keqing.setPostfixPriorities(List.of("test"));

        var appName = keqing.readProperty("appName", String.class);
        var appVersion = keqing.readProperty("appVersion", String.class);
        var appDescription = keqing.readProperty("appDescription", String.class);
        var strings = keqing.readPropertyList("strings", String.class);
        var configClass = keqing.readProperty("", Config.class);

        Assertions.assertEquals("Keqing-Test", appName); // from config-test.json
        Assertions.assertEquals("1.0.0-test", appVersion); // from config-test.json
        Assertions.assertEquals("A sample application configuration file", appDescription); // from config.json
        Assertions.assertEquals(5, strings.size()); // merged list from config.json and config-test.json

        Assertions.assertNotNull(configClass);
        Assertions.assertEquals("Keqing-Test", configClass.getAppName());
        Assertions.assertEquals("1.0.0-test", configClass.getAppVersion());
        Assertions.assertEquals("A sample application configuration file", configClass.getAppDescription());
        Assertions.assertEquals(5, configClass.getStrings().size());
    }

    @Test
    @SneakyThrows
    public void testKeqing_json_testDevPriorities() {
        var keqing = Keqing.loadFromResources("config", '-', new GsonSerializer(new Gson()));
        keqing.setPostfixPriorities(List.of("test", "dev"));

        var appName = keqing.readProperty("appName", String.class);
        var appVersion = keqing.readProperty("appVersion", String.class);
        var appDescription = keqing.readProperty("appDescription", String.class);
        var strings = keqing.readPropertyList("strings", String.class);
        var configClass = keqing.readProperty("", Config.class);

        Assertions.assertEquals("Keqing-Test", appName); // from config-test.json
        Assertions.assertEquals("1.0.0-test", appVersion); // from config-test.json
        Assertions.assertEquals("Development build", appDescription); // from config-dev.json
        Assertions.assertEquals(6, strings.size()); // merged list from config.json, config-dev.json and config-test.json

        Assertions.assertNotNull(configClass);
        Assertions.assertEquals("Keqing-Test", configClass.getAppName());
        Assertions.assertEquals("1.0.0-test", configClass.getAppVersion());
        Assertions.assertEquals("Development build", configClass.getAppDescription());
        Assertions.assertEquals(6, configClass.getStrings().size());
    }

    @Test
    @SneakyThrows
    public void testKeqing_yaml_dev() {
        var keqing = Keqing.loadFromResources("data/data", '-', new SnakeYamlSerializer());
        keqing.setPostfixPriorities(List.of("dev"));

        var appName = keqing.readProperty("app.name", String.class);
        var appVersion = keqing.readProperty("app.version", String.class);
        var appDescription = keqing.readProperty("app.description", String.class);
        var strings = keqing.readPropertyList("app.strings", String.class);
        var configClass = keqing.readProperty("app", AppConfig.class);

        Assertions.assertEquals("Keqing-Dev", appName); // from data-dev.yaml
        Assertions.assertEquals("1.0.0-dev", appVersion); // from data-dev.yaml
        Assertions.assertEquals("Production build", appDescription); // from data.yaml
        Assertions.assertEquals(4, strings.size()); // merged list from data.yaml and data-dev.yaml

        Assertions.assertNotNull(configClass);
        Assertions.assertEquals("Keqing-Dev", configClass.getName());
        Assertions.assertEquals("1.0.0-dev", configClass.getVersion());
        Assertions.assertEquals("Production build", configClass.getDescription());
        Assertions.assertEquals(4, configClass.getStrings().size());
    }
}
