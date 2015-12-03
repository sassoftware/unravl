// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Function;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.generators.Text;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * JSON utility methods.
 *
 * @author David.Biesack@sas.com
 */
public class Json {
    private static ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(Json.class);

    /**
     * Convenience method for parsing a string as JSON.
     *
     * @param json
     *            text JSON; this must be valid
     * @return the root JsonNode
     * @throws UnRAVLException
     *             if the json is not valid.
     */
    public static JsonNode parse(String json) throws UnRAVLException {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            logger.error(e);
            throw new UnRAVLException(e.getMessage(), e);
        } catch (IOException e) {
            logger.error(e);
            throw new UnRAVLException(e.getMessage(), e);
        }
    }

    /**
     * Process a JsonNode and its subtree and perform environment expansion on
     * all text.
     *
     * @param actual
     *            an input Json
     * @param script
     *            the Unravl script
     * @return a new JsonNode with the text mapped
     */
    public static JsonNode expand(JsonNode actual, final UnRAVL script) {
        final JsonNodeFactory jnf = jsonNodeFactory();

        /**
         * A recursive JsonNode transformation mapping function which replaces
         * each string with its environment expansion, That is, replace
         * {varName} with the current binding for "varName" in the script's
         * environment.
         *
         * @param node
         *            the input JSON
         * @return the node or a replacement which the text expanded.
         */
        Function<JsonNode, JsonNode> expandText = new Function<JsonNode, JsonNode>() {
            @Override
            public JsonNode apply(JsonNode node) {
                if (node.isTextual()) {
                    return new TextNode(script.expand(node.textValue()));
                } else if (node.isArray()) {
                    ArrayNode from = (ArrayNode) node;
                    ArrayNode to = new ArrayNode(jnf);
                    for (int i = 0; i < from.size(); i++) {
                        to.add(this.apply(from.get(i)));
                    }
                    return to;
                } else if (node.isObject()) {
                    ObjectNode from = (ObjectNode) node;
                    ObjectNode to = new ObjectNode(jnf);
                    for (Map.Entry<String, JsonNode> f : Json.fields(from)) {
                        to.set(f.getKey(), this.apply(f.getValue()));
                    }
                    return to;
                } else
                    return node;
            }

        };
        return map(actual, expandText);
    }

    /**
     * Transform a JsonNode tree by applying a mapping function to the nodes in
     * it.
     *
     * @param node
     *            the input JSON
     * @param mappingFunction
     *            a Function that maps a node to a node
     * @return the transformed map which may be the input JsonNode or a new
     *         node.
     */
    public static JsonNode map(JsonNode node,
            Function<JsonNode, JsonNode> mappingFunction) {
        return mappingFunction.apply(node);
    }

    /**
     * Return the input node as an ArrayNode when an UnRAVL script expects an
     * array node.
     *
     * @param node
     *            input node
     * @return the input node, cast as an ArrayNode
     * @throws UnRAVLException
     *             if the node is not an ArrayNode. This is used instead of
     *             ClassCastException because it indicates a user error (an
     *             improperly formed UnRAVL JSON script), not an implementation
     *             error in the UnRAVL execution.
     */
    public static ArrayNode array(JsonNode node) throws UnRAVLException {
        if (node.isArray())
            return (ArrayNode) node;
        else
            throw new UnRAVLException("expected array when found " + node);
    }

    /**
     * Return the input node as an ObjectNode when an UnRAVL script expects an
     * array node.
     *
     * @param node
     *            input node
     * @return the input node, cast as an ObjectNode
     * @throws UnRAVLException
     *             if the node is not an ObjectNode. This is used instead of
     *             ClassCastException because it indicates a user error (an
     *             improperly formed UnRAVL JSON script), not an implementation
     *             error in the UnRAVL execution.
     */
    public static ObjectNode object(JsonNode node) throws UnRAVLException {
        if (node.isObject())
            return (ObjectNode) node;
        else
            throw new UnRAVLException("expected object when found " + node);
    }

    /**
     * Return the input node as an TextNode when an UnRAVL script expects an
     * text node.
     *
     * @param node
     *            input node
     * @return the input node, cast as an TextNode
     * @throws UnRAVLException
     *             if the node is not an TextNode. This is used instead of
     *             ClassCastException because it indicates a user error (an
     *             improperly formed UnRAVL JSON script), not an implementation
     *             error in the UnRAVL execution.
     */
    public static TextNode text(JsonNode node) throws UnRAVLException {
        if (!node.isTextual())
            throw new UnRAVLException(
                    "Text node required when " + node + " found.");
        TextNode text = (TextNode) node;
        return text;
    }

    /**
     * Access the first field of a JsonNode, which must be an ObjectNode
     *
     * @param node
     *            a JsonNode which will be used as an ObjectNode
     * @return the first field in the ObjectNode as a Map.Entry
     * @throws UnRAVLException
     *             if the node is not an ObjectNode or if the node is empty.
     */
    public static Map.Entry<String, JsonNode> firstField(JsonNode node)
            throws UnRAVLException {
        ObjectNode o = object(node);
        Iterator<Map.Entry<String, JsonNode>> iter = o.fields();
        if (iter.hasNext()) {
            Map.Entry<String, JsonNode> first = iter.next();
            return first;
        } else
            throw new UnRAVLException(
                    "Non-empty object required when " + node + " found.");
    }

    /**
     * Convert an ArrayNode into an iterable list of JsonNode
     *
     * @param node
     *            a JsonNode which will be used as an ObjectNode
     * @return a List containing the elements
     * @throws UnRAVLException
     *             if the node is not an ArrayNode
     */
    public static List<JsonNode> toArray(JsonNode node) throws UnRAVLException {
        ArrayNode a = array(node);
        List<JsonNode> list = new ArrayList<JsonNode>(a.size());
        Iterator<JsonNode> iter = a.elements();
        while (iter.hasNext()) {
            list.add(iter.next());
        }
        return list;
    }

    /**
     * Return the name of the first field in a JsonNode
     *
     * @param node
     *            a node which must be a non-empty ObjectNode
     * @return the name of the first field
     * @throws UnRAVLException
     *             if the node is not an ObjectNode or if the node is empty.
     */
    public static String firstFieldName(JsonNode node) throws UnRAVLException {
        return firstField(node).getKey();
    }

    /**
     * Return the name of the first field in a JsonNode
     *
     * @param node
     *            a node which must be a non-empty ObjectNode
     * @return the value of the first field
     * @throws UnRAVLException
     *             if the node is not an ObjectNode or if the node is empty.
     */
    public static JsonNode firstFieldValue(JsonNode node)
            throws UnRAVLException {
        return firstField(node).getValue();
    }

    public static List<Map.Entry<String, JsonNode>> fields(JsonNode node)
            throws UnRAVLException {
        ObjectNode object = object(node);
        return fields(object);
    }

    /**
     * Convert an ObjectNode to a List of Map.Entry objects
     *
     * @param object
     *            an JSON object
     * @return a List of Map.Entry objects. These are active and updating them
     *         with setKey or setValue will update the input object.
     */
    public static List<Map.Entry<String, JsonNode>> fields(ObjectNode object) {
        List<Map.Entry<String, JsonNode>> l = new ArrayList<Map.Entry<String, JsonNode>>();
        Iterator<Map.Entry<String, JsonNode>> iter = object.fields();
        while (iter.hasNext()) {
            Map.Entry<String, JsonNode> next = iter.next();
            l.add(next);
        }
        return l;
    }

    /**
     * Write a JSON tree to a stream
     *
     * @param json
     *            the JSON node
     * @param fileName
     *            file name to write the JSON to
     * @throws UnRAVLException
     *             if the file is not writable
     */
    public static void extractToStream(JsonNode json, String fileName)
            throws UnRAVLException {
        try {
            boolean stdout = fileName.equals("-");
            Writer w = stdout ? new PrintWriter(System.out)
                    : new OutputStreamWriter(new FileOutputStream(fileName),
                            Text.UTF_8);
            JsonFactory jf = jsonFactory();
            JsonGenerator g = jf.createGenerator(w);
            g.setCodec(new ObjectMapper());
            g.useDefaultPrettyPrinter();
            g.writeTree(json);
            if (stdout)
                w.write("\n");
            else
                w.close();
        } catch (FileNotFoundException e) {
            throw new UnRAVLException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UnRAVLException(e.getMessage(), e);
        }
    }

    public static JsonFactory jsonFactory() {
        return new JsonFactory();
    }

    public static JsonNodeFactory jsonNodeFactory() {
        return JsonNodeFactory.instance;
    }

    public static ArrayNode wrapInArray(JsonNode node) {
        ArrayNode a = new ArrayNode(jsonNodeFactory());
        a.add(node);
        return a;
    }

    /**
     * If the JSON node contains a field with the given name and it is text,
     * return that text, else return the default value
     *
     * @param node
     *            a JSON node
     * @param fieldName
     *            the name of a field to get from the object
     * @param defaultValue
     *            the value to return if the named field is not found.
     * @return the string value of node.fieldname, if it exists, else
     *         defaultValue
     */
    public static String stringFieldOr(ObjectNode node, String fieldName,
            String defaultValue) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual())
            return defaultValue;
        return value.textValue();
    }

    public static Object unwrap(Object val) { // Can Jackson do this via
                                              // ObjectMapper.treeToValue()? The
                                              // spec is unclear
        Object result = val;
        ObjectMapper mapper = new ObjectMapper();
        if (val instanceof ObjectNode) {
            result = mapper.convertValue(val, Map.class);
        } else if (val instanceof ArrayNode) {
            result = mapper.convertValue(val, List.class);
        } else if (val instanceof NullNode) {
            result = null;
        } else if (val instanceof BooleanNode) {
            result = ((BooleanNode) val).booleanValue();
        } else if (val instanceof ShortNode) {
            result = ((ShortNode) val).shortValue();
        } else if (val instanceof IntNode) {
            result = ((IntNode) val).intValue();
        } else if (val instanceof LongNode) {
            result = ((LongNode) val).longValue();
        } else if (val instanceof DoubleNode) {
            result = ((DoubleNode) val).doubleValue();
        } else if (val instanceof FloatNode) {
            result = ((FloatNode) val).floatValue();
        } else if (val instanceof BigIntegerNode) {
            result = ((BigIntegerNode) val).bigIntegerValue();
        } else if (val instanceof DecimalNode) {
            result = ((DecimalNode) val).decimalValue();
        }
        return result;
    }

}
