package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.util.Json;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * Extract links from a JSON body response and bind them to variables.
 * <p>
 * There are several forms for extracting links, allowing for various link
 * representations.
 * <p>
 * A JSON response may contain <a
 * href='http://tools.ietf.org/html/rfc4287#section-4.2.7'>atom:link</a>
 * representations. This is also compatible with <a
 * href='http://amundsen.com/media-types/collection/'>Collection+JSON</a>
 * format.
 *
 * <pre>
 *  {  ...,
 *     "links" : [
 *         { "rel" : "self",
 *           "method" : "GET",
 *           "href" : "http://www.example.com/orders",
 *           "type" : "application/json"
 *         },
 *         { "rel" : "search",
 *           "method" : "GET",
 *           "href" : "/orders?id={order_id}",
 *           "type" : "application/json"
 *         }
 *      ],
 *     ...
 *  }
 * </pre>
 *
 * We will refer to the above as the atom:link response. With Collection+JSON,
 * the "links" are embedded in the top level "collection" object.
 * <p>
 * Common atom:link members are rel, type, href.
 * <p>
 * The second format is the the <a
 * href='http://stateless.co/hal_specification.html'>Hypertext Application
 * Language</a> (HAL) representation by Mike Kelly which uses a "_links" member:
 *
 * <pre>
 *  { ...,
 *    "_links": {
 *         "self": { "href": "/orders" },
 *         "search": { "href": "/orders?id={order_id}" }
 *    }
 *  }
 * </pre>
 *
 * We will refer to this as the HAL response.
 * <p>
 * This UnRAVL "bind" element can extract links from only one set of links in a
 * JSON object. By default, this extractor will bind variables from links in the
 * environment variable named "responseBody"; this is normally defined using the
 * {@link JsonExtractor "json" extractor}. See the use of "from" below if you
 * want to extract links from another environment variable or expression.
 * <p>
 * The syntax for the links extractor is
 *
 * <pre>
 *  { "links" : { "var1" : selector1,
 *                "var2" : selector2,
 *                ...
 *                "varn" : selectorn } }
 * </pre>
 *
 * "var1" through "varn" are environment variable names which will be bound to
 * the links as per their corresponding selectors. Each selector may be:
 * <dl>
 * <dt>a string</dt>
 * <dd>the string is the link relation (for atom:link or HAL responses)</dd>
 * <dt>a JSON object</dt>
 * <dd>the link which matches all the members of this object (as regular
 * expressions) will be selected. The values in the selector must be strings.
 * This allows you to match on the link relation, method, type, and/or uri
 * instead of just the link relation. (For HAL responses, only href maty be
 * used.)</dd>
 * </dl>
 * <p>
 * Instead of an object of name/spec pairs, the value of the links extractor may
 * be an array of strings, in which case each string is used as both the
 * variable name and the link relation (selector) name. Thus,
 *
 * <pre>
 *  { "links" : [ "self", "update", "delete" ] }
 * </pre>
 *
 * is equivalent to
 *
 * <pre>
 *  { "links" : { "self" : "self", "update" : "update", "delete" : "delete" ] }
 * </pre>
 *
 * Finally, a single value may be used:
 *
 * <pre>
 *  { "link" : "self" }
 * </pre>
 *
 * which is equivalent to
 *
 * <pre>
 *  { "link" : { "self" : "self" } }
 * </pre>
 *
 * Note that "link" may be used instead of "links"; this is clearer for
 * extracting a single link.
 * <p>
 * An extra option, <code>"unwrap" : true</code> may be used to unwrap the
 * Jackson <code>ObjectNode</code> values from the links into a
 * <code>java.util.Map</code>. For example:
 *
 * <pre>
 *  { "link" : { "self" : "self" }, "unwrap" : true }
 * </pre>
 *
 * <h3>Extracting just the href value from links</h3>
 * <p>
 * If the extractor is used with the key "hrefs" or "href" instead of "links",
 * then the extracted value will be just the string value of the "href" member
 * of the corresponding link representation. not the entire link object. For
 * example,
 * </p>
 *
 * <pre>
 *  { "links" : [ "self", "update", "delete" ] }
 * </pre>
 * <p>
 * will bind "self", "update", and "delete" to the corresponding <strong>href
 * string values</strong> of the "self", "update, and "delete" links links.
 * </p>
 * <h2>Extracting from JSON other than responseBody</h2>
 * <p>
 * The links/href extractors also has an additional option
 *
 * <pre>
 *              "from" : "var-or-path"
 * </pre>
 * <p>
 * If "from" is present, its value should be the name of an UnRAVL variable that
 * contains the links collection. By default, this is the JSON response object.
 * <p>
 * An extra option, <code>"prefix" : "<em>prefix</em>"</code> may be used to to
 * specify a prefix string to be prepended to the href values. This may be a URL
 * such as "http://www.example.com/myApi". The prefix is applied to the href if
 * and only if the href value is not a full URL.
 * </p>
 * <p>
 * If the variable <code>unravl.hrefs.prefix </code>is defined, its value will
 * be used if no "prefix" is defined
 * </p>
 * <p>
 * Examples:
 * </p>
 *
 * <pre>
 *  {
 *    "GET" : "{site}/apiPath",
 *    "bind" : { "href" : "self", "prefix" : "https://www.example.com/myApi"  }
 *  }
 *
 *  { "env" : { "site" : "https://www.example.com/myApi" },
 *    "GET" : "{site}/apiPath",
 *    "bind" : { "href" : "self", "prefix" : "{site}" }
 *  }
 *
 *  { "env" : { "unravl.hrefs.prefix " : "https://www.example.com/myApi" },
 *    "GET" : "{site}/apiPath",
 *    "bind" : { "href" : "self" }
 *  }
 * </pre>
 *
 * <p>
 * All three of these forms will convert a href from the <code>"self"</code>
 * link such as <code>"/myResources/ab54d8bc4f"</code> to
 * <code>"https://www.example.com/myApi/myResources/ab54d8bc4f"</code>.
 *
 * <h2>Example: Extracting multiple links</h2>
 *
 * <p>
 * Consider two different JSON responses, the atom:link response and the HAL
 * response, as described above. The UnRAVL "bind" element
 *
 * <pre>
 *  { "links" : { "selfLink" : "self",
 *                "searchLink" : "search" }
 *  }
 * </pre>
 *
 * will select links based on their "rel" names. This will bind the variable
 * "selfLink" to the object
 *
 * <pre>
 *  { "rel" : "self",
 *    "method" : "GET",
 *    "href" : "http://www.example.com/orders",
 *    "type" : "application/json"
 *  }
 * </pre>
 *
 * when used with the the atom:link response, or to the object
 *
 * <pre>
 *  { "href": "/orders" }
 * </pre>
 * <p>
 * when used with the HAL response. The variable named "searchLink" will be
 * bound to the link with the link relation "search".
 * </p>
 *
 * <pre>
 *  { "href" : [ "self", "search" ] }
 * </pre>
 *
 * will bind "self" to the string "http://www.example.com/orders" and bind
 * "search" to the string "http://www.example.com/orders?id={order_id}" (because
 * we use the "href" form instead of the "links" form.)
 *
 * <h2>Example: Extracting from other sources</h2>
 *
 * By default, this extractor works on the variable named "responseBody" which
 * is bound when using the "json" extractor. However, you can use the optional
 * "from" member to name another variable that is bound, or you can use a Groovy
 * expression that returns a JsonNode. This is useful if you want to extract the
 * links of nested objects. It is required for Collection+JSON nodes to select
 * from the "collection" element inside the response, for example.
 *
 * <pre>
 *  "bind" : [
 *             { "href" : { "coll" : "self" },
 *               "from" : "responseBody.collection" } },
 *
 *             { "href" : { "self0" : "self",
 *                          "delete0" : "delete" },
 *               "from" : "responseBody.collection.items[0]" } },
 *
 *             { "href" : { "selfLast" : "self",
 *                          "deleteLast" : "delete" },
 *               "from" : "responseBody.collection.items[responseBody.collection.items.size()-1]" } }
 *           ]
 * </pre>
 *
 * this will extract the href from the link to the collection as well as the the
 * href values from the "self" and "delete" links in the first and last element
 * of the nested items array, respectively. Environment variable substitution is
 * performed on the string before evaluating it as a Groovy expression.
 *
 * <h2>Example: Complex matching</h2>
 *
 * By default, if the selector is a string, this extractor only matches the link
 * relation. This is also the only option for HAL. For atom:link, the "links"
 * array may contain multiple links with the same link relation. Thus, you may
 * specify multiple matching criteria, using regular expression matches for one
 * or more members of the link. For example, to match a link that has a "rel"
 * value of "update" and a "method" value of "PUT" and a "href" label that
 * contains "models", use
 *
 * <pre>
 *  "bind" : { "link" : { "updateLink" : { "rel" : "update",
 *                                         "method" : "PUT",
 *                                         "href" : ".*models.*"
 *                                        }
 *                       }
 *            }
 * </pre>
 * <p>
 * It is easy to see that
 *
 * <pre>
 *  "bind" : { "link" : { "updateLink" : "update" } }
 * </pre>
 *
 * is shorthand for
 *
 * <pre>
 *  "bind" : { "link" : { "updateLink" : { "rel" : "update" } } }
 * </pre>
 * <p>
 * (Note that this element may be specified with either "links" or "link",
 * depending on your preference - use "links" when binding more than one link,
 * and "link" when binding only one.)
 *
 * @author David.Biesack@sas.com
 */

@UnRAVLExtractorPlugin({ "link", "links", "href", "hrefs" })
public class LinksExtractor extends BaseUnRAVLExtractor {

    private static final String PREFIX_KEY = "prefix";
    private static final String UNRAVL_HREF_PREFIX = "unravl.hrefs.prefix";
    private static final String REL_KEY = "rel";
    private static final String COLLECTION_KEY = "collection";
    private static final String HREF_KEY = "href";
    private static final String LINKS_KEY = "links";
    private static final String HAL_LINKS_KEY = "_links";
    private static final Logger logger = Logger.getLogger(LinksExtractor.class);

    @Override
    public void extract(UnRAVL current, ObjectNode extractor, ApiCall call)
            throws UnRAVLException {
        super.extract(current, extractor, call);
        try {
            boolean href = isHref(extractor);
            JsonNode fromNode = extractor.get("from");
            ObjectNode from = jsonObjectSource(extractor, fromNode, call,
                    call.getScript());
            JsonNode spec = Json.firstFieldValue(extractor);
            ObjectNode effectiveSpec = effectiveLinksSpec(extractor, spec);
            extractLinks(extractor, from, effectiveSpec, href, call);
        } catch (ClassCastException e) {
            throw new UnRAVLException(String.format(
                    "%s extractor invalid or corresponding links are not well formed",
                    key(extractor)));
        }
    }

    private boolean isHref(ObjectNode extractor) {
        String key = key(extractor);
        return key.equals("href") || key.equals("hrefs");
    }

    private void extractLinks(ObjectNode root, ObjectNode from,
            ObjectNode effectiveSpec, boolean href, ApiCall call)
            throws UnRAVLException {

        boolean unwrap = unwrapOption(root);
        ArrayNode linksArray = null;
        ObjectNode linksObject = null;
        if (from.get(HAL_LINKS_KEY) != null) {
            linksObject = Json.object(from.get(HAL_LINKS_KEY));
            logger.info("Extracting HAL style links");
        } else if (from.get(LINKS_KEY) != null) {
            linksArray = Json.array(from.get(LINKS_KEY));
            logger.info("Extracting Atom style links");
        } else { // Collection+JSON
            JsonNode coll = from.get(COLLECTION_KEY);
            if (coll != null && coll.isObject()) {
                linksArray = Json.array(coll.get(LINKS_KEY));
                logger.info(
                        "Extracting Collection+JSON style links from \"collection\" member");
            } else {
                String msg = String.format("Cannot infer links in %s extractor",
                        key(root));
                logger.error(msg);
                throw new UnRAVLException(msg);
            }
        }
        for (Map.Entry<String, JsonNode> e : Json.fields(effectiveSpec)) {
            String varName = e.getKey();
            JsonNode spec = e.getValue();
            JsonNode link = matchLink(varName, spec, linksArray, linksObject,
                    root);
            Object value = link;
            if (link != null) {
                if (href) {
                    value = link.get(HREF_KEY).textValue();
                    value = applyPrefix(root, (String) value, varName);
                } else if (unwrap)
                    value = Json.unwrap(link);
                logger.info(String.format("Bound link name %s to %s", varName, value));
                call.getScript().bind(varName, value);
            } else {
                logger.warn(String.format("No such link '%s' in %s extractor", varName, key(root)));
            }

        }
    }

    private String applyPrefix(ObjectNode root, String value, String name)
            throws UnRAVLException {
        if (isUrl(value))
            return value;
        JsonNode prefixSpec = root.get(PREFIX_KEY);
        String prefix = null;
        String why = "";
        if (prefixSpec == null) {
            Object implicitPrefix = getScript().binding(UNRAVL_HREF_PREFIX);
            if (implicitPrefix == null) {
                return value;
            } else if (implicitPrefix instanceof String) {
                prefix = (String) implicitPrefix;
                why = "(implicit " + UNRAVL_HREF_PREFIX + ")";
            }
            else {
                throw new UnRAVLException(
                        "href prefix value must be a string, found "
                                + implicitPrefix.getClass().getName()
                                + ", value = " + prefixSpec);
            }
        } else {
            if (!prefixSpec.isTextual()) {
                throw new UnRAVLException(
                        "href prefix value must be a string, found "
                                + prefixSpec.getClass().getName()
                                + ", value = " + prefixSpec);
            } else {
                prefix = getScript().expand(prefixSpec.textValue());
                why = "(explicit \"prefix\")";
                }
        }
        logger.info(String.format("Prepended '%s' to the '%s' link href '%s' %s.", prefix, name, value, why));
        return prefix + value;
    }

    private boolean isUrl(String value) {
        try {
            new URL(value);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private JsonNode matchLink(String varName, JsonNode linkSpec, ArrayNode linksArray,
            ObjectNode linksObject, ObjectNode root) throws UnRAVLException {
        if (linksArray != null) {// Collection+JSON mode
            for (JsonNode link : Json.toArray(linksArray)) {
                if (matches(root, linkSpec, link))
                    return link;
            }
        } else { // HAL mode
            JsonNode link = linksObject.get(varName);
            return link;
        }
        throw new UnRAVLException(String.format(
                "No such link matching %s found in %s %s", linkSpec, key(root),
                linksArray == null ? linksObject : linksArray));
    }

    private boolean matches(ObjectNode root, JsonNode linkSpec,
                            JsonNode link) throws UnRAVLException {
        if (linkSpec.isTextual()) {
            return link.get(REL_KEY).textValue().equals(linkSpec.textValue());
        } else { // ensure all items in spec match, but using regular
            // expression matching,
            for (Map.Entry<String, JsonNode> e : Json.fields(linkSpec)) {
                String key = e.getKey();
                JsonNode val = e.getValue();
                JsonNode actual = link.get(key);
                if (val.equals(actual))
                    continue; // skip pattern match if exact match
                if (!nodesMatch(root, actual, val))
                    return false;
            }
        }
        return true;
    }

    private boolean nodesMatch(ObjectNode root, JsonNode actual, JsonNode expected)
            throws UnRAVLException {
        if (actual.isTextual() && expected.isTextual()) {
            Pattern p = Pattern.compile(expected.textValue());
            return p.matcher(actual.textValue()).matches();
        }
        throw new UnRAVLException(String.format(
                "%s extractor selector requires string values", key(root)));
    }

    private ObjectNode effectiveLinksSpec(ObjectNode root, JsonNode spec)
            throws UnRAVLException {
        ObjectNode effectiveSpec = new ObjectNode(JsonNodeFactory.instance);
        if (spec.isTextual()) { // convert "self" into { "self" : "self" }
            spec = Json.wrapInArray(spec);
        }
        if (spec.isArray()) { // convert { "links" : ["self","delete"] } into {
            // "links" : { "self" : "self" } }
            for (JsonNode e : Json.toArray(spec)) {
                if (e.isTextual()) {
                    String name = e.textValue();
                    effectiveSpec.set(name, e);
                } else
                    throw new UnRAVLException(String.format(
                            "Array elements must be strings in %s extractor: %s",
                            key(root), e));
            }
        } else if (spec.isObject()) {
            effectiveSpec = (ObjectNode) spec;
        } else {
            throw new UnRAVLException(String.format(
                    "Invalid value in %s extractor: %s", key(root), spec));
        }
        return effectiveSpec;
    }

    @SuppressWarnings("rawtypes")
    private ObjectNode jsonObjectSource(ObjectNode root, JsonNode fromNode,
            ApiCall call, UnRAVL script) throws UnRAVLException {
        ObjectNode from = null;
        if (fromNode == null) {
            if (!script.bound("responseBody")) {
                throw new UnRAVLException(String.format(
                        "resonseBody is not bound in %s extractor", key(root)));
            }
            Object f = call.getScript().binding("responseBody");
            if (f == null) {
                throw new UnRAVLException(String.format(
                        "No responseBody binding in %s extractor.", key(root)));
            } else if (f instanceof Map) {
                from = Json.wrap((Map) f);
            } else if (f instanceof ObjectNode) {
                from = (ObjectNode) f;
            } else {
                throw new UnRAVLException(String.format(
                        "responseBody is not bound to a JSON object in %s extractor: %s",
                        key(root), f));
            }
        } else {
            if (fromNode.isTextual()) {
                String where = fromNode.textValue();
                if (call.getScript().bound(where)) {
                    Object val = call.getScript().binding(where);
                    if (val instanceof ObjectNode) {
                        from = (ObjectNode) val;
                    } else if (val instanceof Map) {
                        from = Json.wrap((Map) val);
                    } else {
                        throw new UnRAVLException(String.format(
                                "Value of \"from\": \"%s\" in %s extractor is not a JSON object:\n%s",
                                fromNode.textValue(), key(root), val));
                    }
                } else {
                    Object o = call.getScript().eval(where);
                    if (o instanceof ObjectNode) {
                        from = (ObjectNode) o;
                    } else
                        throw new UnRAVLException(String.format(
                                "expression %s did not yield a JSON object in %s extractor",
                                where, key(root)));
                }
            }
        }
        return from;
    }

}
