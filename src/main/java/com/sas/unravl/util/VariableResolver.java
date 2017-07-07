package com.sas.unravl.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expand <code>{varName}</code>, <code>{varName|alt text}</code>, <code>{{@literal @}varName@}</code>, <code>{U+nnnn}</code> in strings.
 * <ol>
 * <li>Variables (bound with <code>"env"</code> elements or <code>"groovy"</code> elements) may be
 * referenced in strings via <code>{varName}</code> and replaced with their corresponding
 * string value.
 * <li>Variables  may be referenced in JSON via <code>"{{@literal @}varName@}"</code> and replaced with their corresponding
 * JSON value.
 * <li>
 * If a variable is not bound, alternate text is substituted instead.
 * <li>Any Unicode code point may be inserted by referencing it using U+nnnn
 * where nnnn is four hex digits naming a Unicode code point. For example,
 * <code>{U+002D}</code> will be replaced with the right curly (close) brace, '}', and
 * <code>{U+03C0}</code> will be replaced with the Unicode GREEK SMALL LETTER PI &#x3c0;
 * </ol>
 *
 * @author David.Biesack@sas.com
 */
public class VariableResolver {

    private static final char OPENING_BRACE = '{';
    private static final char DELIMITER = '|';
    private static final char CLOSING_BRACE = '}';

    public final static Pattern VAR_NAME_PATTERN = Pattern
            .compile("^[-\\w.\\$]+$");
    public final static Pattern UNICODE_CHARACTER_NAME_PATTERN = Pattern
            .compile("^[Uu]\\+[0-9A-Fa-f]{4}$");

    /** defines a pattern for variable substitution <code>{{@literal @}varName@}</code> **/
    public static final String IS_VAR_VALUE_PATTERN = "^\\{@[-\\w.\\$]+@\\}$";
    /** defines a pattern for a variable name within a variable value pattern **/
    public final static Pattern VAR_NAME_IN_VALUE_PATTERN = Pattern
            .compile("[^\\{@][-\\w.\\$]+[^@\\}]");

    private String input; // the input string that we will expand
    private final Map<String, Object> env;
    private int len;
    private StringBuilder result;
    private int index; // position in the input string

    /**
     * Construct a reusable resolver that uses an environment. After creating,
     * call {@link #expand(String)}.
     *
     * @param environment
     *            Non-null mapping of variable names to values
     */
    public VariableResolver(Map<String, Object> environment) {
        this.env = environment;
    }

    /**
     * Expand variable references <code>{varname}</code> or <code>{undefinedVarName|alt value}</code> in
     * the input string source
     *
     * @param input
     *            the input source string
     * @return the result of expanding variables in the input
     */
    public String expand(String input) {
        this.input = input;
        return expand();
    }

    /**
     * Expand variable references in the input
     *
     * @return the expanded input string
     */
    private synchronized String expand() {
        if (input.indexOf(OPENING_BRACE) == -1
                || input.indexOf(CLOSING_BRACE) == -1)
            return input;
        result = new StringBuilder();
        index = 0;
        len = input.length();
        while (index < len) {
            char c = input.charAt(index);
            if (c == OPENING_BRACE) {
                resolveVar();
            } else {
                result.append(c);
                index++;
            }
        }
        return result.toString();
    }

    /**
     * Resolve a variable of the form <code>{varName}</code> or <code>{varName|alt text}</code>. If
     * <var>varName</var>. is bound in the environment, append the <code>toString()</code> value of the
     * variable to the result (dropping the braces around the <var>varName</var>). If
     * <var>varName</var> is not defined, the braces and <var>varName</var> are appended to the
     * result. If the form is <code>{varName|alt text}</code> and the <var>varName</var> is not bound,
     * the <em>alt text</em> is appended to the result (recursively expanding it.) If the
     * first portion is not a valid variable name, then the remainder is
     * parsed/expanded recursively.
     * <p>
     * The input is on a <code>'{'</code>. This will consume characters until to the matching
     * <code>'}'</code> and leave index pointing after the matching <code>'}'</code>. If there is no
     * matching <code>'}'</code>, simply append the <code>'{'</code> to the result and return.
     */
    private void resolveVar() {
        index++; // skip opening {
        if (hasMatchingCloseBrace()) {
            int varPos = index;
            while (index < len) {
                char c = input.charAt(index);
                switch (c) {
                case OPENING_BRACE: {
                    result.append(input, varPos - 1, index);
                    resolveVar();
                    scanToCloseBrace(true);
                    result.append(CLOSING_BRACE);
                    return;
                }
                case CLOSING_BRACE: {
                    String candidateVarName = input.substring(varPos, index);
                    if (isValidVarName(candidateVarName)
                            && env.containsKey(candidateVarName)) {
                        Object val = env.get(candidateVarName);
                        result.append(val == null ? "null" : val.toString());
                    } else if (isUnicodeCodePointName(candidateVarName)) {
                        result.append(unicodeCharacter(candidateVarName));
                    } else {
                        result.append(OPENING_BRACE) //
                                .append(candidateVarName) //
                                .append(CLOSING_BRACE);
                    }
                    index++;
                    return;
                }
                case DELIMITER: {
                    String candidateVarName = input.substring(varPos, index);
                    index++;
                    if (isValidVarName(candidateVarName)) {
                        if (env.containsKey(candidateVarName)) {
                            Object val = env.get(candidateVarName);
                            result.append(val == null ? "null" : val.toString());
                            scanToCloseBrace(false);
                        } else {
                            scanToCloseBrace(true);
                        }
                    } else {
                        result.append(OPENING_BRACE) //
                                .append(candidateVarName) //
                                .append(DELIMITER);
                        scanToCloseBrace(true);
                        result.append(CLOSING_BRACE);
                    }
                    return;
                }
                default:
                    index++;
                }
            }
        } else
            // no matching close
            result.append(OPENING_BRACE);
    }

    // return true if there is a matching } for the current {
    private boolean hasMatchingCloseBrace() {
        int matchDepth = 1;
        for (int i = index; i < len; i++) {
            char ch = input.charAt(i);
            if (ch == OPENING_BRACE)
                matchDepth++;
            else if (ch == CLOSING_BRACE) {
                matchDepth--;
                if (matchDepth == 0)
                    return true;
            }
        }
        return false;
    }

    // process characters until we find the match }
    // If copy is true, those characters and any nested
    // variable references are copied/expanded,
    // else we simply skip over them.
    // This method assumes a matching } exists
    private void scanToCloseBrace(boolean copy) {
        while (index < len) {
            char c = input.charAt(index);
            switch (c) {
            case OPENING_BRACE: {
                if (copy)
                    resolveVar();
                else {
                    index++;
                    scanToCloseBrace(false);
                }
                break;
            }
            case CLOSING_BRACE: {
                index++;
                return;
            }
            default:
                if (copy)
                    result.append(c);
                index++;
            }
        }
    }

    // Return true iff candidateVarName matches a valid variable name syntax:
    // [alphanumeric, _, ., #, -]+
    private static boolean isValidVarName(String candidateVarName) {
        return VAR_NAME_PATTERN.matcher(candidateVarName).matches();
    }

    /**
     * Test if a string is a Unicode code point that matches the pattern
     * "U+hhhh".
     *
     * @param string
     *            the input string
     * @return True if string matches "U+hhhh" where hhhh is four hex digits.
     *         Case is ignored.
     */
    public static boolean isUnicodeCodePointName(String string) {
        return UNICODE_CHARACTER_NAME_PATTERN.matcher(string).matches();
    }

    // Convert "U+hhhh to a Unicode character, where hhhh is four hex digits
    private static char unicodeCharacter(String spec) {
        assert spec.matches(UNICODE_CHARACTER_NAME_PATTERN.pattern());
        int codePoint = Integer.parseInt(spec.substring(2), 16);
        return (char) codePoint;
    }

    /**
     * Checks if a node is a value node. If a node is of pattern <code>{{@literal @}varName@}</code>
     * it is a value node; that is return the actual value for that
     * node instead of embedding the value to a string.
     *
     * @param node
     *            a textual node
     * @return if the node is a value node
     */
    public boolean isValueNode(String node) {
        if ((node == null) || (node.isEmpty())) {
            return false;
        } else {
            return node.matches(IS_VAR_VALUE_PATTERN);
        }
    }

    /**
     * This method resolves the variable value given the encoded variable name.
     * It will match the variable name in the pattern and will return the value
     * of the variable as an object from the Environment key-values map. The var
     * name is of form <code>{{@literal @}varName@}</code>. If the <var>varName</var> is invalid or
     * cannot be found in the list of vars the var value won't be resolved. This
     * method does not resolve and append the var value into a string.
     *
     * @param varName
     *            the encoded name of a variable
     * @return the variable value
     */
    public Object resolveVarValue(String varName) {
        Matcher matcher = VAR_NAME_IN_VALUE_PATTERN.matcher(varName);
        if (matcher.find()) {
            String candidateVarName = matcher.group();
            if (env.containsKey(candidateVarName)) {
                return env.get(candidateVarName);
            }
        }
        return varName;
    }
}
