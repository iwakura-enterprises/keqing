package enterprises.iwakura.keqing.impl;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import enterprises.iwakura.keqing.Keqing;
import enterprises.iwakura.keqing.Serializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Gson serializer for {@link Keqing}. Supports file types with ".json" extension.
 */
@Getter
@Setter
@RequiredArgsConstructor
public class GsonSerializer extends Serializer<JsonElement> {

    /**
     * The file extension supported by this serializer.
     */
    public static final String FILE_EXTENSION = "json";

    /**
     * The Gson instance used for serialization and deserialization.
     */
    protected final Gson gson;

    /**
     * Creates a new instance of GsonSerializer with a default Gson configuration.
     */
    public GsonSerializer() {
        this.gson = new Gson();
    }

    /**
     * Determines if the given file extension is supported by this serializer.
     */
    @Override
    public boolean supportsFileExtension(String extension) {
        return FILE_EXTENSION.equalsIgnoreCase(extension);
    }

    /**
     * Serializes the given content into a JsonElement using {@link JsonParser} and associates it with the specified postfix.
     *
     * @param postfix the postfix to associate with the serialized content
     * @param content the content to serialize
     *
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void serialize(String postfix, String content) throws IOException {
        try {
            serializedFileContents.put(postfix, JsonParser.parseString(content));
        } catch (Exception exception) {
            throw new IOException(String.format("Failed to parse JSON content for postfix %s: %s", postfix, content), exception);
        }
    }

    /**
     * Reads a property from the serialized JsonElement associated with the given postfix and path, merging multiple JsonElements if necessary. This will
     * merge JsonObjects by deep merging and JsonArrays by concatenation.
     *
     * @param postfix the postfix associated with the serialized content
     * @param postfixPriorities the list of postfix priorities to consider if the specified postfix is not found
     * @param path the property path to read
     * @param clazz the class type to which the property value should be converted
     *
     * @return an Optional containing the property value if found and successfully converted, or an empty Optional otherwise
     * @param <T> the type of the property value
     */
    @Override
    public <T> Optional<T> readProperty(String postfix, List<String> postfixPriorities, String path, Class<T> clazz) {
        List<JsonElement> jsonElements = new ArrayList<>();
        String currentPostfix = postfix == null ? (!postfixPriorities.isEmpty() ? postfixPriorities.get(0) : null) : postfix;
        int currentIndex = postfix != null ? -1 : 0;

        while (currentPostfix != null) {
            JsonElement jsonElement = serializedFileContents.get(currentPostfix);

            if (jsonElement != null) {
                JsonElement elementByPath = getElementByPath(jsonElement, path);
                if (elementByPath != null) {
                    jsonElements.add(elementByPath);
                }
            }

            currentIndex++;
            currentPostfix = currentIndex < postfixPriorities.size() ? postfixPriorities.get(currentIndex) : null;
        }

        if (jsonElements.isEmpty()) {
            return Optional.empty();
        }

        JsonElement firstNonNullElement = jsonElements.get(0);

        if (firstNonNullElement == null || firstNonNullElement.isJsonNull() || firstNonNullElement.isJsonPrimitive()) {
            return Optional.ofNullable(parseElement(firstNonNullElement, clazz));
        }

        if (firstNonNullElement.isJsonObject()) {
            JsonObject mergedJsonObject = mergeObjects(jsonElements);
            return Optional.ofNullable(parseElement(mergedJsonObject, clazz));
        }

        if (firstNonNullElement.isJsonArray()) {
            JsonArray mergedJsonArray = mergeArrays(jsonElements);
            return Optional.ofNullable(parseElement(mergedJsonArray, clazz));
        }

        return Optional.empty();
    }

    /**
     * Merges a list of JsonElements into a single JsonObject by deep merging.
     * Higher priority objects in the list will overwrite lower priority ones.
     *
     * @param jsonElements the list of JsonElements to merge
     * @return the merged JsonObject
     */
    protected JsonObject mergeObjects(List<JsonElement> jsonElements) {
        JsonObject jsonObject = new JsonObject();

        // Reverse order so that higher priority objects overwrite lower priority ones
        Collections.reverse(jsonElements);

        jsonElements.forEach(jsonElement -> {
            if (jsonElement.isJsonObject()) {
                deepMerge(jsonElement.getAsJsonObject(), jsonObject);
            }
        });

        return jsonObject;
    }

    /**
     * Merges a list of JsonElements into a single JsonArray by concatenation.
     * Higher priority arrays in the list will appear first in the merged array.
     *
     * @param jsonElements the list of JsonElements to merge
     * @return the merged JsonArray
     */
    protected JsonArray mergeArrays(List<JsonElement> jsonElements) {
        JsonArray jsonArray = new JsonArray();

        Collections.reverse(jsonElements);
        jsonElements.forEach(jsonElement -> {
            if (jsonElement.isJsonArray()) {
                jsonElement.getAsJsonArray().forEach(jsonArray::add);
            }
        });

        return jsonArray;
    }

    /**
     * Deep merges the source JsonObject into the target JsonObject.
     * If both source and target have a value for the same key:
     * - If both values are JsonObjects, they are merged recursively.
     * - If both values are JsonArrays, they are concatenated.
     * - Otherwise, the source value overwrites the target value.
     *
     * @param source the source JsonObject to merge from
     * @param target the target JsonObject to merge into
     */
    protected void deepMerge(JsonObject source, JsonObject target) {
        for (String key : source.keySet()) {
            JsonElement sourceValue = source.get(key);
            if (target.has(key)) {
                JsonElement targetValue = target.get(key);
                if (sourceValue.isJsonObject() && targetValue.isJsonObject()) {
                    deepMerge(sourceValue.getAsJsonObject(), targetValue.getAsJsonObject());
                } else if (sourceValue.isJsonArray() && targetValue.isJsonArray()) {
                    JsonArray mergedArray = new JsonArray();
                    targetValue.getAsJsonArray().forEach(mergedArray::add);
                    sourceValue.getAsJsonArray().forEach(mergedArray::add);
                    target.add(key, mergedArray);
                } else {
                    target.add(key, sourceValue);
                }
            } else {
                target.add(key, sourceValue);
            }
        }
    }

    /**
     * Parses a JsonElement into an instance of the specified class type.
     *
     * @param element the JsonElement to parse
     * @param clazz   the class type to which the JsonElement should be converted
     * @param <T>     the type of the property value
     * @return an instance of the specified class type, or null if the element is null or cannot be parsed
     */
    @SuppressWarnings("unchecked")
    protected <T> T parseElement(JsonElement element, Class<T> clazz) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            return (T) gson.fromJson(element, getWrapperClass(clazz));
        }

        if (element.isJsonObject()) {
            return gson.fromJson(element, clazz);
        }

        if (element.isJsonArray()) {
            return gson.fromJson(element, TypeToken.getParameterized(List.class, clazz).getType());
        }

        return null;
    }

    /**
     * Returns the wrapper class for a given primitive class.
     * If the provided class is not a primitive type, it returns the class itself.
     *
     * @param clazz the class to get the wrapper for
     * @return the corresponding wrapper class, or the original class if it's not primitive
     */
    protected Class<?> getWrapperClass(Class<?> clazz) {
        if (clazz == int.class) {
            return Integer.class;
        }
        if (clazz == boolean.class) {
            return Boolean.class;
        }
        if (clazz == double.class) {
            return Double.class;
        }
        if (clazz == float.class) {
            return Float.class;
        }
        if (clazz == long.class) {
            return Long.class;
        }
        if (clazz == short.class) {
            return Short.class;
        }
        if (clazz == byte.class) {
            return Byte.class;
        }
        if (clazz == char.class) {
            return Character.class;
        }
        return clazz;
    }

    /**
     * Retrieves a nested JsonElement from the given JsonElement based on the specified dot-separated path.
     *
     * @param jsonElement the root JsonElement to start from
     * @param path        the dot-separated path to the desired property (e.g., "parent.child.property")
     * @return the JsonElement at the specified path, or null if any part of the path does not exist
     */
    protected JsonElement getElementByPath(JsonElement jsonElement, String path) {
        if (path.isEmpty()) {
            return jsonElement;
        }

        String[] keys = path.split("\\.");
        int currentIndex = 0;
        JsonElement currentElement = jsonElement;

        while (currentElement != null && currentElement.isJsonObject() && currentIndex < keys.length) {
            JsonObject jsonObject = currentElement.getAsJsonObject();
            currentElement = jsonObject.get(keys[currentIndex]);
            currentIndex++;
        }

        return currentElement;
    }

    /**
     * This method is unsupported in GsonSerializer. Use {@link #readProperty(String, List, String, Class)} instead for correct JSON merging behavior.
     *
     * @param postfix the postfix associated with the serialized content
     * @param path    the property path to read
     * @param clazz   the class type to which the property value should be converted
     * @param <T>     the type of the property value
     * @return nothing, as this method always throws an exception
     * @throws UnsupportedOperationException always thrown to indicate this method is unsupported
     */
    @Override
    public <T> Optional<T> readProperty(String postfix, String path, Class<T> clazz) {
        throw new UnsupportedOperationException("Use readProperty with postfixPriorities for correct JSON merging behavior.");
    }
}
