package com.sas.unravl.util;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Expand {varName} or {varName|alt text} or {U+nnnn} in strings.
 * <ol>
 * <li>Variables (bound with "env" elements or "groovy" elements) may be
 * referenced in strings via {varName} and replaced with their corresponding
 * string value.
 * <li>
 * If a variable is not bound, alternate text is substituted instead.
 * <li>Any Unicode code point may be inserted by referencing it using U+nnnn
 * where nnnn is four hex digits naming a Unicode code point. For example,
 * {U+002D} will be replaced with the right curly (close) brace, '}', and
 * {U+03C0} will be replaced with the Unicode GREEK SMALL LETTER PI &#x3c0;
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
     * Expand variable references {varname} or {undefinedVarName|alt value} in
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
     * Resolve a variable of the form {varName} or {varName|alt text}. If
     * varName is bound in the environment, append the toString() value of the
     * variable to the result (dropping the braces around the varName). If
     * varName is not defined, the braces and varName are appended to the
     * result. If the form is {varName|alt text} and the varName is not bound,
     * the alt text is appended to the result (recursively expanding it.) if the
     * first portion is not a valid variable name, then the remainder is
     * parsed/expanded recursively.
     * <p>
     * The input is on a '{'. This will consume characters until to the matching
     * '}' and leave index pointing after the matching '}'. If there is no
     * matching '}', simply append the '{' to the result and return.
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
     * This method resolves the variable value given the variable name. This
     * will return the value of the variable as an object from the Environment
     * key-values map. The var name is of form {varName} and is a substring of
     * the var value notation "{@varName@}". If the varName is
     * invalid or cannot be found in the list of vars the var value won't be
     * resolved. This method does not resolve and append the var value into a
     * string.
     *
     * @param varName
     *            the name of a variable
     * @return the variable value
     */
    public Object resolveVarValue(String varName) {
        String candidateVarName = varName.substring(
                varName.indexOf(OPENING_BRACE) + 1,
                varName.indexOf(CLOSING_BRACE));
        if (isValidVarName(candidateVarName)
                && env.containsKey(candidateVarName)) {
            return env.get(candidateVarName);
        } else {
            return varName.toString();
        }
    }
}
