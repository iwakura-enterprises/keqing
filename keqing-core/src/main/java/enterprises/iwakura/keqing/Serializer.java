package enterprises.iwakura.keqing;

import java.io.IOException;
import java.util.*;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Abstract serializer class to be extended by specific serialization implementations. Adds a serialization support for {@link Keqing}
 * @param <S> The type of the serialized content (e.g., Properties, JSON object, YAML object, etc.)
 */
public abstract class Serializer<S> {

    /**
     * Holds the serialized file contents mapped by their postfixes.
     */
    @Getter
    protected final Map<String, S> serializedFileContents = new HashMap<>();

    /**
     * Cache for read properties to optimize repeated reads.
     */
    protected final Map<CacheKey, Optional<Object>> readCache = new HashMap<>();

    /**
     * Indicates whether read results should be cached.
     */
    protected final boolean shouldCacheReads;

    /**
     * Constructs a Serializer with the specified caching behavior.
     *
     * @param shouldCacheReads indicates whether read results should be cached
     */
    protected Serializer(boolean shouldCacheReads) {
        this.shouldCacheReads = shouldCacheReads;
    }

    /**
     * Determines if the given file extension is supported by this serializer.
     *
     * @param extension the file extension to check
     *
     * @return true if the extension is supported, false otherwise
     */
    public abstract boolean supportsFileExtension(String extension);

    /**
     * Serializes the given content and associates it with the specified postfix.
     *
     * @param postfix the postfix to associate with the serialized content
     * @param content the content to serialize
     *
     * @throws IOException if an I/O error occurs during serialization
     */
    public abstract void serialize(String postfix, String content) throws IOException;

    /**
     * Reads a property from the serialized content associated with the given postfix and path, Default implementation invokes {@link #readProperty(String, String, Class)}
     *
     * @param postfix the postfix associated with the serialized content
     * @param postfixPriorities the list of postfix priorities to consider if the specified postfix is not found
     * @param path the property path to read
     * @param clazz the class type to which the property value should be converted
     *
     * @return an Optional containing the property value if found and successfully converted, or an empty Optional otherwise
     * @param <T> the type of the property value
     */
    public <T> Optional<T> readProperty(String postfix, List<String> postfixPriorities, String path, Class<T> clazz) {
        CacheKey cacheKey = new CacheKey(postfix, postfixPriorities, path, clazz);
        Object cachedRead = readFromCache(cacheKey);

        // If cached read is present, e.g. non-null Optional, return it (be it empty or non-empty)
        if (cachedRead instanceof Optional) {
            return (Optional<T>)cachedRead;
        }

        String currentPostfix = postfix == null ? (!postfixPriorities.isEmpty() ? postfixPriorities.get(0) : null) : postfix;
        int currentIndex = -1;

        while (currentPostfix != null) {
            Optional<T> property = readProperty(currentPostfix, path, clazz);

            if (property.isPresent()) {
                return writeToCache(cacheKey, property);
            }

            currentIndex++;
            currentPostfix = currentIndex < postfixPriorities.size() ? postfixPriorities.get(currentIndex) : null;
        }

        return writeToCache(cacheKey, Optional.empty());
    }

    /**
     * Reads a property from the serialized content associated with the given postfix and path.
     *
     * @param postfix non-null postfix associated with the serialized content
     * @param path the property path to read
     * @param clazz the class type to which the property value should be converted
     *
     * @return an Optional containing the property value if found and successfully converted, or an empty Optional otherwise
     * @param <T> the type of the property value
     */
    public abstract <T> Optional<T> readProperty(String postfix, String path, Class<T> clazz);

    protected Object readFromCache(CacheKey cacheKey) {
        if (shouldCacheReads) {
            Object cachedRead = readCache.get(cacheKey);
            if (cachedRead != null) {
                try {
                    return cachedRead;
                } catch (ClassCastException e) {
                    readCache.remove(cacheKey);
                }
            }
        }
        return null;
    }

    protected <T> Optional<T> writeToCache(CacheKey cacheKey, Optional<T> value) {
        if (shouldCacheReads) {
            readCache.put(cacheKey, (Optional<Object>) value);
        }
        return value;
    }

    @Data
    @RequiredArgsConstructor
    public static class CacheKey {

        private final String postfix;
        private final List<String> postfixPriorities;
        private final String path;
        private final Class<?> clazz;
    }
}
