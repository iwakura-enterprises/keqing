package enterprises.iwakura.keqing.impl;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;

import enterprises.iwakura.keqing.Keqing;
import enterprises.iwakura.keqing.Serializer;

/**
 * Java {@link Properties} serializer for {@link Keqing}. Supports file types with ".properties" extension.
 */
public class PropertiesSerializer extends Serializer<Properties> {

    /**
     * The file extension supported by this serializer.
     */
    public static final String FILE_EXTENSION = "properties";

    /**
     * Constructs a PropertiesSerializer with caching enabled by default.
     */
    public PropertiesSerializer() {
        this(true);
    }

    /**
     * Constructs a PropertiesSerializer with the specified caching behavior.
     *
     * @param shouldCacheReads indicates whether read results should be cached
     */
    public PropertiesSerializer(boolean shouldCacheReads) {
        super(shouldCacheReads);
    }

    /**
     * Determines if the given file extension is supported by this serializer.
     *
     * @param extension the file extension to check
     * @return true if the extension is "properties" (case-insensitive), false otherwise
     */
    @Override
    public boolean supportsFileExtension(String extension) {
        return FILE_EXTENSION.equalsIgnoreCase(extension);
    }

    /**
     * Serializes the given content into a Properties object and associates it with the specified postfix.
     *
     * @param postfix the postfix to associate with the serialized content
     * @param content the content to serialize
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void serialize(String postfix, String content) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(content));
        serializedFileContents.put(postfix, properties);
    }

    /**
     * Reads a property from the serialized Properties object associated with the given postfix and path,
     *
     * @param postfix the postfix associated with the Properties object
     * @param path    the property key to read
     * @param clazz   the class type to which the property value should be converted. Properties support only primitive
     *                types and String.
     * @param <T>     the type of the property value
     * @return an Optional containing the property value if found and successfully converted, or an empty Optional
     * otherwise
     */
    @Override
    public <T> Optional<T> readProperty(String postfix, String path, Class<T> clazz) {
        Properties properties = serializedFileContents.get(postfix);

        if (properties != null) {
            T property = parseValue(properties.getProperty(path), clazz);

            if (property != null) {
                return Optional.of(property);
            }
        }

        return Optional.empty();
    }

    /**
     * Parses a string property value into the specified class type.
     *
     * @param property the string property value to parse
     * @param clazz    the class type to which the property value should be converted
     * @param <T>      the type of the property value
     * @return the parsed property value, or null if the input property is null
     * @throws IllegalArgumentException if the specified class type is unsupported (not a primitive type or String)
     */
    private <T> T parseValue(String property, Class<T> clazz) {
        if (property == null) {
            return null;
        }

        if (clazz == String.class) {
            return clazz.cast(property);
        } else if (clazz == Integer.class || clazz == int.class) {
            return clazz.cast(Integer.parseInt(property));
        } else if (clazz == Long.class || clazz == long.class) {
            return clazz.cast(Long.parseLong(property));
        } else if (clazz == Double.class || clazz == double.class) {
            return clazz.cast(Double.parseDouble(property));
        } else if (clazz == Float.class || clazz == float.class) {
            return clazz.cast(Float.parseFloat(property));
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return clazz.cast(Boolean.parseBoolean(property));
        } else if (clazz == Byte.class || clazz == byte.class) {
            return clazz.cast(Byte.parseByte(property));
        } else if (clazz == Short.class || clazz == short.class) {
            return clazz.cast(Short.parseShort(property));
        } else if (clazz == Character.class || clazz == char.class) {
            return clazz.cast(property.charAt(0));
        } else {
            throw new IllegalArgumentException("Unsupported class type: " + clazz.getName());
        }
    }
}
