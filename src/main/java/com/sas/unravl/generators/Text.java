package com.sas.unravl.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.util.Json;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;

/**
 * This class produces text from a JSON text specification. This may be used for
 * a text ResponseBodyGenerator or a TextAssertion.
 * 
 * <pre>
 * { "key" : "text-value" } 
 * { "key" : "@fileOrUrl" } 
 * { "key" : array-of-text }
 * </pre>
 * 
 * <p>
 * In the first form, the text is expressed as a single JSON string.
 * </p>
 * <p>
 * In the second form, the text is read from a text file or a URL. The text is
 * assumed to be in UTF-8 encoding.
 * </p>
 * <p>
 * In the third form, Text will combine texts in an array. Each element of the
 * array may be a text-value or a <code>{@literal @}file-or-url</code> or a
 * nested array. The values are separated by a newline. If you want a trailing
 * newline, the last value should be the empty string, "".
 * </p>
 * <p>
 * TODO: add an <code>"encoding" : <em>encoding-name</em></code>
 * </p>
 * 
 * @author David.Biesack@sas.com
 * 
 */
public class Text implements CharSequence {

    private static final int BUFSIZE = 256;
    public static final Charset UTF_8 = Charset.forName("UTF-8");
    StringBuilder text = new StringBuilder();
    private final UnRAVL script;

    public Text(UnRAVL script) {
        this.script = script;
    }

    public Text(UnRAVL script, JsonNode node, String key)
            throws IOException, UnRAVLException {
        this(script, node.get(key));
    }

    public Text(UnRAVL script, JsonNode node)
            throws IOException, UnRAVLException {
        this(script);
        build(node);
    }

    public Text(UnRAVL script, String text)
            throws IOException, UnRAVLException {
        this(script);
        build(text);
    }

    private void build(JsonNode node) throws IOException, UnRAVLException {
        if (node == null) {
        } else if (node.isTextual()) {
            build(node.textValue());
        } else if (node.isArray()) {
            String delimiter = "";
            for (JsonNode each : Json.array(node)) {
                text.append(delimiter);
                build(each);
                delimiter = "\n";
            }
        }
    }

    private void build(String textValue) throws IOException {
        if (textValue.startsWith(UnRAVL.REDIRECT_PREFIX)) {
            String expanded = script.expand(
                    textValue.substring(UnRAVL.REDIRECT_PREFIX.length()));
            buildFromStream(expanded);
        } else {
            text.append(textValue);
        }

    }

    private void buildFromStream(String fileOrURL) throws IOException {
        InputStream is = null;
        try {
            URL url = new URL(fileOrURL);
            is = url.openStream();
        } catch (MalformedURLException e) {
            File f = new File(fileOrURL);
            if (f.exists()) {
                is = new FileInputStream(f);
            } else {
                is = getClass().getResourceAsStream(fileOrURL);
            }
        }
        if (is == null) {
            throw new IOException("No such file or URL " + fileOrURL);
        }
        Reader r = new InputStreamReader(is, UTF_8);
        char buffer[] = new char[BUFSIZE];
        BufferedReader br = new BufferedReader(r);
        for (int n = br.read(buffer, 0, BUFSIZE); n > 0; n = br.read(buffer, 0,
                BUFSIZE))
            text.append(buffer, 0, n);
        br.close();
    }

    public Reader reader() {
        return new StringReader(text());
    }

    @Override
    public char charAt(int index) {
        return text.charAt(index);
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public CharSequence subSequence(int start, int length) {
        return text.subSequence(start, length);
    }

    public String text() {
        return text.toString();
    }

    @Override
    public String toString() {
        return text();
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        else if (other instanceof Text) {
            return text.equals(((Text) other).text());
        } else
            return false;
    }

    /**
     * Utility method to extract a byte array from a string, using UTF-8
     * encoding. This method ignores the UnsupportedEncodingException because
     * all JRE's are required to support the UTF-8 encoding.
     * 
     * @param s
     *            a string
     * @return the UTF-8 encoding of the string, as a byte array
     */
    public static byte[] utf8(String s) {
        return s.getBytes(UTF_8);
    }

    /**
     * Utility method to crate a string from a UTF-8 encoded byte array This
     * method ignores the UnsupportedEncodingException because all JRE's are
     * required to support the UTF-8 encoding.
     * 
     * @param byteArray
     *            input bytes which must be a UTF-8 encoding
     * @return the string
     * @throws IllegalArgumentException
     *             if the bytes could not be converted to a string with UTF-8
     */
    public static String utf8ToString(byte[] byteArray) {
        return new String(byteArray, UTF_8);
    }

    /**
     * Encode text using URL UTF-8 encoding.
     * 
     * @param text
     *            input text string
     * @return the encoded text
     * @throws IllegalStateException
     *             if the string could not be encoded with UTF-8
     */
    public static String urlEncode(String text) {
        String encoded;
        try {
            encoded = URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(
                    "Could not encode string to URL UTF-8 encoding");
        }
        return encoded;
    }

}
