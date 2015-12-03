package com.sas.unravl.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.processors.syntax.SyntaxValidator;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLAssertionPlugin;
import com.sas.unravl.generators.Text;
import com.sas.unravl.util.Json;

import java.io.IOException;
import java.util.Iterator;

/**
 * Asserts that one or more JSON structures conform to a JSON schema. There are
 * several possible forms for this assertion:
 * 
 * <pre>
 * 
 * { "schema" : <var>schema</var>  }
 * { "schema" : <var>schema</var>,"values" : <var>values</var> }
 * </pre>
 * 
 * <var>schema</var> may be:
 * <ol>
 * <li>a JSON object which represents an embedded JSON schema</li>
 * <li>the name of a variable that contains a JSON object</li>
 * <li>a string in the form of "@location" where <var>location</var> is the URI
 * of the JSON schema. (Environment variables are expanded within the
 * <var>location</var> location string.)</li>
 * </ol>
 * <p>
 * <var>values</var> may be
 * <ol>
 * <li>a string containing a single variable (the key <code>"value"</code> may
 * be used instead of <code>"values"</code>)
 * <li>an array of variable names
 * </ol>
 * For forms 1 and 2, each such variable must be bound to a JSON object or
 * array. The JSON value of the variable is validated against the above
 * referenced JSON schema.
 * <p>
 * If <code>"values"</code> is omitted, the default value is the current
 * response body which is assumed to be JSON.
 * <p>
 * TThe assertion fails if any value does not conform to the JSON schema, or if
 * the elements do not have the forms described above or if the referenced JSON
 * schema is not a valid schema.
 * 
 * @author David.Biesack@sas.com
 */
@UnRAVLAssertionPlugin("schema")
public class SchemaAssertion extends BaseUnRAVLAssertion {

    @Override
    public void check(UnRAVL current, ObjectNode assertion, Stage when,
            ApiCall call) throws UnRAVLAssertionException, UnRAVLException {
        super.check(current, assertion, when, call);
        JsonNode schemaRef = Json.firstFieldValue(assertion);
        JsonNode jsonSchema = resolveSchema(current, schemaRef);
        JsonSchema validatingSchema = validateSchema(jsonSchema);
        JsonNode values = assertion.get("values");
        if (values == null) {
            values = assertion.get("value");
        }
        if (values == null) {
            JsonNode responseBody = Json
                    .parse(Text.utf8ToString(call.getResponseBody()));
            validateValueAgainstSchema(responseBody, validatingSchema);
        } else if (values.isArray()) {
            Iterator<JsonNode> iter = values.elements();
            while (iter.hasNext()) {
                assertSchema(call, validatingSchema, iter.next());
            }
        } else if (values.isTextual()) {
            assertSchema(call, validatingSchema, values);
        } else { // Should we allow an object or array and validate it?
            throw new UnRAVLException(String.format(
                    "Value '%s' is not a variable name in schema assertion",
                    values));
        }
        return;
    }

    private void assertSchema(ApiCall call, JsonSchema validatingSchema,
            JsonNode item) throws UnRAVLException {
        if (!item.isTextual())
            throw new UnRAVLException(String.format(
                    "Value '%s' is not a variable name in schema assertion",
                    item));
        String varName = item.textValue();
        validateVarAgainstSchema(call, varName, validatingSchema);
    }

    private JsonNode resolveSchema(UnRAVL current, JsonNode schemaRef)
            throws UnRAVLException {

        JsonNode jsonSchema = schemaRef; // assume default - a schema object
        if (schemaRef.isTextual()) {
            String request = schemaRef.textValue();
            if (request.startsWith(UnRAVL.REDIRECT_PREFIX)) {
                Text text;
                try {
                    TextNode expanded = new TextNode(
                            current.expand(schemaRef.textValue()));
                    text = new Text(current, expanded);
                    jsonSchema = Json.parse(text.text());
                } catch (IOException e) {
                    throw new UnRAVLException(String.format(
                            "Unable to load schema from @ reference %s",
                            schemaRef.textValue()), e);
                }
            } else {
                Object val = current.binding(request);
                if (val instanceof JsonNode) {
                    jsonSchema = (JsonNode) val;
                }
            }
        }

        if (!jsonSchema.isObject()) {
            throw new UnRAVLException(String.format(
                    "schema value %s in schema assertion is not a \"@location\", the name of a variable holding a JSON obejct, or a JSON object.",
                    schemaRef));
        }

        return jsonSchema;
    }

    private JsonSchema validateSchema(JsonNode jsonSchema)
            throws UnRAVLException {

        try {
            final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            final JsonSchema schema = factory.getJsonSchema(jsonSchema);
            SyntaxValidator syntaxValidator = factory.getSyntaxValidator();
            if (!syntaxValidator.schemaIsValid(jsonSchema)) {
                throw new UnRAVLException("JSON schema is invalid");
            }
            ProcessingReport report = syntaxValidator
                    .validateSchema(jsonSchema);
            boolean success = report.isSuccess();
            if (!success) {
                throw new UnRAVLAssertionException(report.toString());
            }
            return schema;
        } catch (ProcessingException e) {
            throw new UnRAVLException(e);
        }
    }

    private void validateVarAgainstSchema(ApiCall call, String varName,
            JsonSchema jsonSchema) throws UnRAVLException {
        Object value = call.getScript().binding(varName);
        if (value == null || !(value instanceof JsonNode)) {
            throw new UnRAVLException(
                    "responseBody is not a JSON value in schema assertion");
        }
        validateValueAgainstSchema((JsonNode) value, jsonSchema);
    }

    private void validateValueAgainstSchema(JsonNode node, JsonSchema schema)
            throws UnRAVLException {
        try {
            ProcessingReport report = schema.validate(node, true);
            boolean success = report.isSuccess();
            if (!success) {
                throw new UnRAVLAssertionException(report.toString());
            }
        } catch (ProcessingException e) {
            throw new UnRAVLException(e);
        }
    }

}
