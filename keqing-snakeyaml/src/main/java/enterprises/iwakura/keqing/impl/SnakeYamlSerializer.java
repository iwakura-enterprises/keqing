package enterprises.iwakura.keqing.impl;

import enterprises.iwakura.keqing.Keqing;
import enterprises.iwakura.keqing.Serializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.util.*;

/**
 * SnakeYAML serializer for {@link Keqing}. Supports file types with ".yaml" and ".yml" extensions.
 */
@Getter
@Setter
@RequiredArgsConstructor
public class SnakeYamlSerializer extends Serializer<Object> {

    /**
     * The file extensions supported by this serializer.
     */
    public static final List<String> FILE_EXTENSIONS;

    static {
        FILE_EXTENSIONS = new ArrayList<>();
        FILE_EXTENSIONS.add("yaml");
        FILE_EXTENSIONS.add("yml");
    }

    /**
     * The SnakeYAML instance used for parsing YAML content.
     */
    private final Yaml yaml;

    /**
     * Constructs a new SnakeYamlSerializer with a default Yaml instance.
     */
    public SnakeYamlSerializer() {
        this.yaml = new Yaml();
    }

    /**
     * Determines if the given file extension is supported by this serializer.
     *
     * @param extension the file extension to check
     *
     * @return true if the extension is "yaml" or "yml" (case-insensitive), false otherwise
     */
    @Override
    public boolean supportsFileExtension(String extension) {
        return FILE_EXTENSIONS.stream().anyMatch(ext -> ext.equalsIgnoreCase(extension));
    }

    /**
     * Serializes the given content into a YAML object and associates it with the specified postfix.
     *
     * @param postfix the postfix to associate with the serialized content
     * @param content the content to serialize
     *
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void serialize(String postfix, String content) throws IOException {
        try {
            Object data = yaml.load(content);
            serializedFileContents.put(postfix, data);
        } catch (Exception exception) {
            throw new IOException(String.format("Failed to parse YAML content for postfix %s: %s", postfix, content), exception);
        }
    }

    /**
     * Reads a property from the serialized YAML object associated with the given postfix and path,
     * merging properties from multiple postfixes based on their priorities.
     *
     * @param postfix the postfix associated with the YAML object
     * @param postfixPriorities the list of postfix priorities to consider if the specified postfix is not found
     * @param path the property path to read
     * @param clazz the class type to which the property value should be converted
     *
     * @return an Optional containing the property value if found and successfully converted, or an empty Optional otherwise
     * @param <T> the type of the property value
     */
    @Override
    public <T> Optional<T> readProperty(String postfix, List<String> postfixPriorities, String path, Class<T> clazz) {
        List<Object> elements = new ArrayList<>();
        String currentPostfix = postfix == null ? (!postfixPriorities.isEmpty() ? postfixPriorities.get(0) : null) : postfix;
        int currentIndex = postfix != null ? -1 : 0;

        while (currentPostfix != null) {
            Object yamlObject = serializedFileContents.get(currentPostfix);

            if (yamlObject != null) {
                Object elementByPath = getElementByPath(yamlObject, path);
                if (elementByPath != null) {
                    elements.add(elementByPath);
                }
            }

            currentIndex++;
            currentPostfix = currentIndex < postfixPriorities.size() ? postfixPriorities.get(currentIndex) : null;
        }

        if (elements.isEmpty()) {
            return Optional.empty();
        }

        Object firstElement = elements.get(0);

        if (firstElement == null) {
            return Optional.empty();
        }

        if (firstElement instanceof Map) {
            Map<String, Object> mergedMap = mergeObjects(elements);
            return Optional.ofNullable(convertToType(mergedMap, clazz));
        }

        if (firstElement instanceof List) {
            List<Object> mergedList = mergeLists(elements);
            return Optional.ofNullable(convertToType(mergedList, clazz));
        }

        // For primitives, just return the first one
        return Optional.ofNullable(convertToType(firstElement, clazz));
    }

    /**
     * Merges a list of objects, treating them as maps. Higher priority maps overwrite lower priority ones.
     *
     * @param elements the list of objects to merge
     * @return the merged map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeObjects(List<Object> elements) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Reverse order so that higher priority objects overwrite lower priority ones
        Collections.reverse(elements);

        for (Object element : elements) {
            if (element instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) element;
                deepMerge(map, result);
            }
        }

        return result;
    }

    /**
     * Merges a list of objects, treating them as lists. Higher priority lists are appended after lower priority ones.
     *
     * @param elements the list of objects to merge
     * @return the merged list
     */
    @SuppressWarnings("unchecked")
    private List<Object> mergeLists(List<Object> elements) {
        List<Object> result = new ArrayList<>();

        Collections.reverse(elements);
        for (Object element : elements) {
            if (element instanceof List) {
                result.addAll((List<Object>) element);
            }
        }

        return result;
    }

    /**
     * Deeply merges the source map into the target map. For overlapping keys:
     * - If both values are maps, they are merged recursively.
     * - If both values are lists, they are concatenated.
     * - Otherwise, the source value overwrites the target value.
     *
     * @param source the source map to merge from
     * @param target the target map to merge into
     */
    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> source, Map<String, Object> target) {
        for (String key : source.keySet()) {
            Object sourceValue = source.get(key);
            if (target.containsKey(key)) {
                Object targetValue = target.get(key);
                if (sourceValue instanceof Map && targetValue instanceof Map) {
                    deepMerge((Map<String, Object>) sourceValue, (Map<String, Object>) targetValue);
                } else if (sourceValue instanceof List && targetValue instanceof List) {
                    List<Object> mergedList = new ArrayList<>();
                    mergedList.addAll((List<Object>) targetValue);
                    mergedList.addAll((List<Object>) sourceValue);
                    target.put(key, mergedList);
                } else {
                    target.put(key, sourceValue);
                }
            } else {
                target.put(key, sourceValue);
            }
        }
    }

    /**
     * Converts the given object to the specified class type.
     *
     * @param object the object to convert
     * @param clazz the target class type
     * @return the converted object, or null if the input object is null
     * @param <T> the type of the target class
     */
    @SuppressWarnings("unchecked")
    private <T> T convertToType(Object object, Class<T> clazz) {
        if (object == null) {
            return null;
        }

        if (clazz.isInstance(object)) {
            return (T) object;
        }

        // For maps and lists, we need to convert them to the target type
        if (object instanceof Map && !Map.class.isAssignableFrom(clazz)) {
            // Serialize back to YAML and then load with a constructor for the target class
            String yamlStr = yaml.dump(object);
            Yaml customYaml = new Yaml(new Constructor(clazz, new LoaderOptions()));
            return customYaml.load(yamlStr);
        }

        if (object instanceof List) {
            return (T) object;
        }

        // For primitives or simple conversions
        if (clazz == String.class) {
            return (T) String.valueOf(object);
        }

        if (clazz == Integer.class || clazz == int.class) {
            return (T) (Integer) (object instanceof Number ? ((Number) object).intValue() : Integer.parseInt(object.toString()));
        }

        if (clazz == Boolean.class || clazz == boolean.class) {
            return (T) (Boolean) (object instanceof Boolean ? object : Boolean.parseBoolean(object.toString()));
        }

        if (clazz == Double.class || clazz == double.class) {
            return (T) (Double) (object instanceof Number ? ((Number) object).doubleValue() : Double.parseDouble(object.toString()));
        }

        if (clazz == Long.class || clazz == long.class) {
            return (T) (Long) (object instanceof Number ? ((Number) object).longValue() : Long.parseLong(object.toString()));
        }

        if (clazz == Float.class || clazz == float.class) {
            return (T) (Float) (object instanceof Number ? ((Number) object).floatValue() : Float.parseFloat(object.toString()));
        }

        if (clazz == Short.class || clazz == short.class) {
            return (T) (Short) (object instanceof Number ? ((Number) object).shortValue() : Short.parseShort(object.toString()));
        }

        if (clazz == Byte.class || clazz == byte.class) {
            return (T) (Byte) (object instanceof Number ? ((Number) object).byteValue() : Byte.parseByte(object.toString()));
        }

        if (clazz == Character.class || clazz == char.class) {
            String str = object.toString();
            if (str.length() == 1) {
                return (T) (Character) str.charAt(0);
            } else {
                throw new IllegalArgumentException("Cannot convert to character: " + object);
            }
        }

        // Last resort: try to use YAML's built-in conversion
        return yaml.loadAs(yaml.dump(object), clazz);
    }

    /**
     * Retrieves an element from a nested YAML object using a dot-separated path.
     *
     * @param yamlObject the root YAML object (typically a Map)
     * @param path the dot-separated path to the desired element (e.g., "parent.child.key")
     * @return the element at the specified path, or null if not found
     */
    protected Object getElementByPath(Object yamlObject, String path) {
        if (path == null || path.isEmpty()) {
            return yamlObject;
        }

        String[] keys = path.split("\\.");
        Object current = yamlObject;

        for (String key : keys) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(key);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * This method is unsupported in SnakeYamlSerializer. Use {@link #readProperty(String, List, String, Class)} instead.
     *
     * @param postfix the postfix associated with the YAML object
     * @param path the property path to read
     * @param clazz the class type to which the property value should be converted
     * @return nothing, always throws UnsupportedOperationException
     * @param <T> the type of the property value
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> Optional<T> readProperty(String postfix, String path, Class<T> clazz) {
        throw new UnsupportedOperationException("Use readProperty with postfixPriorities for correct YAML merging behavior.");
    }
}