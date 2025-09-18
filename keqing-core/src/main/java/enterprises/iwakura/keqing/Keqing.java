package enterprises.iwakura.keqing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import enterprises.iwakura.keqing.util.ResourceUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;

/**
 * Main class for handling file bundles using the {@link Keqing} library.
 * <p>
 * Usage:
 * <pre>
 *         {@code
 *         Keqing keqing = new Keqing();
 *         // 1. Path to the language files without postfix and extension
 *         // 2. Postfix separator character
 *         // 3. Serializer instance (e.g., GsonSerializer, SnakeYamlSerializer, PropertiesSerializer)
 *         keqing.loadFromFileSystem("./data/lang", '_', new SnakeYamlSerializer());
 *         keqing.setDefaultPostfix("en"); // Defaults to English
 *         String greeting = keqing.readProperty("greeting", String.class);
 *         System.out.println(greeting); // Outputs the greeting in English
 *         }
 *     </pre>
 * The default implementation of {@link Keqing} is thread-safe.
 */
@Getter
@Setter
public class Keqing {

    /**
     * List of effective postfix priorities, including the default postfix if set, and always ending with an empty
     * string.
     */
    protected final List<String> effectivePostfixPriorities = new ArrayList<>(Collections.singletonList(""));

    /**
     * List of user-defined postfix priorities.
     */
    protected final List<String> postfixPriorities = new ArrayList<>();

    /**
     * Character used to separate the postfix from the file name (e.g., '_' in 'lang_en.yaml').
     */
    protected char postfixSeparator = '_';

    /**
     * Serializer instance used for reading properties.
     */
    protected Serializer<?> serializer;

    /**
     * Loads files from the file system based on the provided file path template, postfix separator, and serializer.
     *
     * @param filePathTemplate the file path template without postfix and extension (e.g., "./data/lang" for files like
     *                         "./data/lang_en.yaml")
     * @param postfixSeparator the character used to separate the postfix from the file name (e.g., '_' in
     *                         'lang_en.yaml')
     * @param serializer       the serializer instance to use for reading properties
     * @return a new instance of {@link Keqing} with loaded files
     * @throws IOException if an I/O error occurs during file loading
     */
    public static Keqing loadFromFileSystem(String filePathTemplate, char postfixSeparator, Serializer<?> serializer)
        throws IOException {
        Keqing keqing = new Keqing();
        keqing.reloadFromFileSystem(filePathTemplate, postfixSeparator, serializer);
        return keqing;
    }

    /**
     * Loads files from the resources based on the provided file path template, postfix separator, serializer, and class
     * loader.
     *
     * @param filePathTemplate the file path template without postfix and extension (e.g., "lang/lang" for files like
     *                         "lang/lang_en.yaml")
     * @param postfixSeparator the character used to separate the postfix from the file name (e.g., '_' in
     *                         'lang_en.yaml')
     * @param serializer       the serializer instance to use for reading properties
     * @param classLoader      the class loader to use for loading resources
     * @return a new instance of {@link Keqing} with loaded files
     * @throws IOException if an I/O error occurs during resource loading
     */
    public static Keqing loadFromResources(String filePathTemplate, char postfixSeparator, Serializer<?> serializer,
        ClassLoader classLoader) throws IOException {
        Keqing keqing = new Keqing();
        keqing.reloadFromResources(filePathTemplate, postfixSeparator, serializer, classLoader);
        return keqing;
    }

    /**
     * Loads files from the resources based on the provided file path template, postfix separator, and serializer. Uses
     * the context class loader of the current thread.
     *
     * @param filePathTemplate the file path template without postfix and extension (e.g., "lang/lang" for files like
     *                         "lang/lang_en.yaml")
     * @param postfixSeparator the character used to separate the postfix from the file name (e.g., '_' in
     *                         'lang_en.yaml')
     * @param serializer       the serializer instance to use for reading properties
     * @return a new instance of {@link Keqing} with loaded files
     * @throws IOException if an I/O error occurs during resource loading
     */
    public static Keqing loadFromResources(String filePathTemplate, char postfixSeparator, Serializer<?> serializer)
        throws IOException {
        Keqing keqing = new Keqing();
        keqing.reloadFromResources(filePathTemplate, postfixSeparator, serializer);
        return keqing;
    }

    /**
     * Sets the list of postfix priorities.
     *
     * @param postfixPriorities the list of postfix priorities to set
     */
    @Synchronized
    public void setPostfixPriorities(Collection<String> postfixPriorities) {
        this.postfixPriorities.clear();
        this.postfixPriorities.addAll(postfixPriorities);
        updateEffectivePostfixes();
    }

    /**
     * Uses all found postfixes from the loaded files as postfix priorities.
     */
    public void useAllFoundPostfixes() {
        setPostfixPriorities(serializer.getSerializedFileContents().keySet());
    }

    /**
     * Updates the effective postfix priorities list based on the current default postfix and user-defined postfix
     * priorities.
     */
    protected void updateEffectivePostfixes() {
        this.effectivePostfixPriorities.clear();
        this.effectivePostfixPriorities.addAll(this.postfixPriorities);
        this.effectivePostfixPriorities.add("");
    }

    /**
     * Reads a property from the serialized content using the specified postfix, path, and class type.
     *
     * @param postfix the postfix to use for reading the property; if it exists in the effective priorities, it will be
     *                prioritized
     * @param path    the property path to read
     * @param clazz   the class type to which the property value should be converted
     * @param <T>     the type of the property value
     * @return the property value if found and successfully converted, or null otherwise
     */
    @Synchronized
    public <T> T readProperty(String postfix, String path, Class<T> clazz) {
        if (serializer == null) {
            throw new IllegalStateException("Serializer is not loaded");
        }
        boolean effectivePostfixExists = effectivePostfixPriorities.stream().anyMatch(p -> p.equals(postfix));

        return serializer.readProperty(effectivePostfixExists ? null : postfix, effectivePostfixPriorities, path, clazz)
            .orElse(null);
    }

    /**
     * Reads a list of properties from the serialized content using the specified postfix, path, and class type.
     *
     * @param postfix the postfix to use for reading the property; if it exists in the effective priorities, it will be
     *                prioritized
     * @param path    the property path to read
     * @param clazz   the class type to which the property value should be converted
     * @param <T>     the type of the property value inside the list
     * @return the list of properties if found and successfully converted, or null otherwise
     */
    public <T> List<T> readPropertyList(String postfix, String path, Class<T> clazz) {
        T property = readProperty(postfix, path, clazz);
        if (property instanceof List<?>) {
            //noinspection unchecked
            return (List<T>) property;
        } else {
            return null;
        }
    }

    /**
     * Reads a property from the serialized content using the default postfix, path, and class type. Uses the default
     * postfix.
     *
     * @param path  the property path to read
     * @param clazz the class type to which the property value should be converted
     * @param <T>   the type of the property value
     * @return the property value if found and successfully converted, or null otherwise
     * @throws IllegalStateException if the default postfix is not set
     */
    @Synchronized
    public <T> T readProperty(String path, Class<T> clazz) {
        return readProperty(effectivePostfixPriorities.get(0), path, clazz);
    }

    /**
     * Reads a list of properties from the serialized content using the default postfix, path, and class type. Uses the
     * default postfix.
     *
     * @param path  the property path to read
     * @param clazz the class type to which the property value should be converted
     * @param <T>   the type of the property value inside the list
     * @return the list of properties if found and successfully converted, or null otherwise
     * @throws IllegalStateException if the default postfix is not set
     */
    public <T> List<T> readPropertyList(String path, Class<T> clazz) {
        return readPropertyList(effectivePostfixPriorities.get(0), path, clazz);
    }

    /**
     * Loads files from the file system based on the provided file path template, postfix separator, and serializer.
     *
     * @param filePathTemplate the file path template without postfix and extension (e.g., "./data/lang" for files like
     *                         "./data/lang_en.yaml")
     * @param postfixSeparator the character used to separate the postfix from the file name (e.g., '_' in
     *                         'lang_en.yaml')
     * @param serializer       the serializer instance to use for reading properties
     * @throws IOException if an I/O error occurs during file loading
     */
    @Synchronized
    public void reloadFromFileSystem(String filePathTemplate, char postfixSeparator, Serializer<?> serializer)
        throws IOException {
        this.postfixSeparator = postfixSeparator;
        this.serializer = serializer;

        // File path will be for example ./data/lang, where the files will be ./data/lang_en.yaml, ./data/lang_cs
        // .properties, etc.
        File fileTemplate = new File(filePathTemplate);
        Path directory = fileTemplate.getParentFile().toPath();
        String filePrefix = fileTemplate.getName();

        try (Stream<Path> files = Files.list(directory).filter(Files::isRegularFile)) {
            files.forEach(file -> {
                String fileName = file.getFileName().toString();
                String fileExtension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";
                if (fileName.startsWith(filePrefix) && serializer.supportsFileExtension(fileExtension)) {
                    String postfix = "";
                    int separatorIndex = fileName.indexOf(postfixSeparator, filePrefix.length());
                    if (separatorIndex != -1) {
                        postfix = fileName.substring(separatorIndex + 1,
                            fileName.length() - fileExtension.length() - 1);
                    }
                    try {
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        serializer.serialize(postfix, content);
                    } catch (IOException exception) {
                        throw new RuntimeException("Failed to read file: " + file, exception);
                    }
                }
            });
        }
    }

    /**
     * Loads files from the resources based on the provided file path template, postfix separator, serializer, and class
     * loader.
     *
     * @param filePathTemplate the file path template without postfix and extension (e.g., "lang/lang" for files like
     *                         "lang/lang_en.yaml")
     * @param postfixSeparator the character used to separate the postfix from the file name (e.g., '_' in
     *                         'lang_en.yaml')
     * @param serializer       the serializer instance to use for reading properties
     * @param classLoader      the class loader to use for loading resources
     * @throws IOException if an I/O error occurs during resource loading
     */
    @Synchronized
    public void reloadFromResources(String filePathTemplate, char postfixSeparator, Serializer<?> serializer,
        ClassLoader classLoader) throws IOException {
        this.postfixSeparator = postfixSeparator;
        this.serializer = serializer;

        String directorySymbolUsed = filePathTemplate.contains("/") ? "/" : "\\"; // Windows style path support

        if (filePathTemplate.startsWith(directorySymbolUsed)) {
            filePathTemplate = filePathTemplate.substring(1);
        }

        // Resource path will be for example /lang/lang, where the files will be /lang/lang_en.yaml, /lang/lang_cs
        // .properties, etc.
        String resourceDirectory = filePathTemplate.contains(directorySymbolUsed) ? filePathTemplate.substring(0,
            filePathTemplate.lastIndexOf(directorySymbolUsed)) : "";
        String filePrefix = filePathTemplate.contains(directorySymbolUsed) ? filePathTemplate.substring(
            filePathTemplate.lastIndexOf(directorySymbolUsed) + 1) : filePathTemplate;

        ResourceUtils.listResourceFiles(resourceDirectory, classLoader).forEach(resourceFile -> {
            String fileExtension =
                resourceFile.contains(".") ? resourceFile.substring(resourceFile.lastIndexOf('.') + 1) : "";
            if (resourceFile.startsWith(filePrefix) && serializer.supportsFileExtension(fileExtension)) {
                String postfix = "";
                int separatorIndex = resourceFile.indexOf(postfixSeparator, filePrefix.length());
                if (separatorIndex != -1) {
                    postfix = resourceFile.substring(separatorIndex + 1,
                        resourceFile.length() - fileExtension.length() - 1);
                }
                try {
                    String resourcePath = (resourceDirectory.isEmpty() ? "" : (resourceDirectory + "/")) + resourceFile;
                    InputStream resourceStream = classLoader.getResourceAsStream(resourcePath);
                    if (resourceStream == null) {
                        throw new RuntimeException("Resource not found: " + resourcePath);
                    }
                    String content;
                    try (ByteArrayOutputStream result = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = resourceStream.read(buffer)) != -1) {
                            result.write(buffer, 0, length);
                        }
                        content = result.toString(StandardCharsets.UTF_8.name());
                    }
                    serializer.serialize(postfix, content);
                } catch (IOException exception) {
                    throw new RuntimeException("Failed to read resource: " + resourceFile, exception);
                }
            }
        });
    }

    /**
     * Loads files from the resources based on the provided file path template, postfix separator, and serializer. Uses
     * the context class loader of the current thread.
     *
     * @param filePathTemplate the file path template without postfix and extension (e.g., "lang/lang" for files like
     *                         "lang/lang_en.yaml")
     * @param postfixSeparator the character used to separate the postfix from the file name (e.g., '_' in
     *                         'lang_en.yaml')
     * @param serializer       the serializer instance to use for reading properties
     * @throws IOException if an I/O error occurs during resource loading
     */
    @Synchronized
    public void reloadFromResources(String filePathTemplate, char postfixSeparator, Serializer<?> serializer)
        throws IOException {
        reloadFromResources(filePathTemplate, postfixSeparator, serializer,
            Thread.currentThread().getContextClassLoader());
    }
}
