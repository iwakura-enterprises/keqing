package enterprises.iwakura.keqing.util;

import enterprises.iwakura.keqing.Keqing;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Utility class for {@link Keqing}
 */
@UtilityClass
public class ResourceUtils {

    /**
     * Lists all resource files in a given directory, whether they are in the file system or inside a JAR.
     *
     * @param directory   The directory to search in (e.g., "lang").
     * @param classLoader The class loader to use for loading resources.
     * @return A list of filenames found in the specified directory.
     * @throws IOException If an I/O error occurs.
     */
    public static List<String> listResourceFiles(String directory, ClassLoader classLoader) throws IOException {
        List<String> filenames = new ArrayList<>();

        Enumeration<URL> resources = classLoader.getResources(directory);
        while (resources.hasMoreElements()) {
            URL resourceUrl = resources.nextElement();

            if (resourceUrl.getProtocol().equals("file")) {
                // File system resource
                try {
                    java.nio.file.Path directoryPath = Paths.get(resourceUrl.toURI());
                    try (Stream<Path> files = Files.walk(directoryPath, 1)) {  // Only direct children
                        files.filter(path -> !path.equals(directoryPath))  // Exclude the directory itself
                                .filter(Files::isRegularFile)
                                .map(path -> path.getFileName().toString())
                                .forEach(filenames::add);
                    }
                } catch (URISyntaxException e) {
                    throw new IOException("Failed to convert URL to URI", e);
                }
            } else if (resourceUrl.getProtocol().equals("jar")) {
                // JAR resource
                String jarPath = resourceUrl.getPath();
                String jarFilePath = jarPath.substring(5, jarPath.indexOf("!"));
                String directoryInJar = directory.replace('.', '/');

                try (JarFile jarFile = new JarFile(URLDecoder.decode(jarFilePath, StandardCharsets.UTF_8.name()))) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();

                        if (!entry.isDirectory() && entryName.startsWith(directoryInJar + "/")) {
                            String relativeName = entryName.substring(directoryInJar.length() + 1);
                            if (!relativeName.contains("/")) {  // Only direct children
                                filenames.add(relativeName);
                            }
                        }
                    }
                }
            }
        }

        return filenames;
    }
}
