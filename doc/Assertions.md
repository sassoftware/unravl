This page describes the *assertions* supported in [UnRAVL](Reference.md) scripts.

Assertions are what test the API.
There are two sets of supported assertions:
`"preconditions"` and `"assert"`.

`"preconditions"`
are evaluated *before* invoking the API and must
all pass before calling the API. This can be used to
validate environment variables that may have been set
from other APIs calls in earlier tests.

```
 "preconditions" : assertions
```

`"assert"` assertions run *after* the API call and validate the result.

```
 "assert" : assertions
```

`"preconditions"` and `"assert"` blocks are very similar, differing
only in when they are executed. In the description below,
descriptions of `"assert"` also applies to `"preconditions"`.

Assertions can take following form:

```
  "assert" : json-object
  "assert" : json-array
  "assert" : "expression-string"
```

The first form is converted to the general array form,
`"assert" : [ json-object ]`

The second form is the most general case: an array of
assertions, using any combination of the forms described below.

Each assertion in the array is evaluated in order. Validation stops
on the first failed assertion in the array.

The third form `"expession-string"` is a convenient
abbreviation for

```JSON
"assert" : [ { "groovy" : "expression-string" } ]`
```
(See the [`"groovy"`](#groovy) assertion element below.)

Groovy is the default expression evaluation language.
You may use the `unravl.script.language` system property
to set the script language that UnRAVL
uses tp evaluate such expression strings.
See [Script language](Reference.md#script-language) for
to learn how to change the languages UnRAVL
uses to evaluate such expressions.

Below is the set of assertions supported by UnRAVL.
Reminder: all the precondition/assertion forms described below are embedded within a
```
"preconditions" : assertion
"precondition" : [ array-of-assertions ]
```
```
"assert" : assertion
"assert" : [ array-of-assertions ]
```
element. Note however that some only make sense
inside `"assert"` since they make assertions about
the *result* of the API call.

## status

Checks that the HTTP status code matches the expected response.

```
 { "status" : integer }
 { "status" : integer-array }
 { "status" : pattern }
```

Examples:
```JSON
 { "status" : 200 }
 { "status" : [200, 204] }
 { "status" : "2.." }
```

The third form is a regular expression; `"2.."`
will match any status between 200 and 299.

*Warning*: Unless there is an explicit { "status" : status-code } assertion,
UnRAVL will execute an implicit `{ "status" : "2.." }` assertion.
Thus, if a test expects a non-2xx status code, use an explicit `"status"`
assertion and not a `"groovy"` assertion such as `"status == 404"`.

## json

Asserts that the response body matches the JSON value

```
 { "json" : json-object-or-array }
 { "json" : "@file-or-url" }
```

String values in the JSON are subject to environment substitution,
and anywhere when using a @file-or-url.

**TODO**: augment to allow environment substitution for numbers, booleans, etc.
in literal JSON.  We can't use naked env references in the
JSON literal, such as
```
 { "longitude" : {longitude} }
```
to expand into
```JSON
 { "longitude" : 89.392 }
```
because the entire UnRAVL script is parsed as JSON *before* any environment variables are defined,
and the JSON parser will balk at `{longitude}` as invalid JSON.

**TODO**: add an option to test or ignore only certain fields or JSON Path expressions.

## text

Asserts that the response body matches the (usually) plain/text body.

```
 { "text" : "expected text" }
 { "text" : "@file-or-url" }
 { "text" : array-of-strings }
```

The `"text"` literals are encoded with Unicode and escape characters,
such as `\n` for newlines. An array of strings will
be concatenated, separated by a newline. Any string that begins with the '@' character
is assumed to be a file or URL reference and that content
streamed in.

Environment substitution is applied to strings and external text.

Only UTF-8 text is allowed.

```JSON
 { "text" : "This is\na multiline\nresponse\n" }
 { "text" : "@file-or-url" }
 { "text" : [ "This is",
              "a multiline",
              "response",
              "" ]
```

**TODO**: add diagnostic to indicate where the text differs.

**TODO**: add a `"literal" : true` or other option to suppress environment substitution.

**TODO**: add a `"charset" : "charset-name"` option to use another character set
in external resources.

## binary

Asserts that the response body matches the (usually) binary body.

```
 { "binary" : [ array of expected binary byte values ] }
 { "binary" : "@file-or-url" }
```

For example to assert that a response matches the content of a file, use

```JSON
{ "binary" : "@Un.png" }
```

## bound

Asserts that one or more variables are bound in the environment.
This is a "safety valve" for a script, especially those which
expect variables to be defined via system properties.

```
    { "bound" : "var-name" }
    { "bound" : [ "var-name1", ..., "var-namen" ] }
```

## headers

Assert that one or more headers exist and have the
specified value matching a regular expression

```
  { "headers" :
     {
       "Header-Name" : "pattern",
       ...
       "Header-Name" : "pattern"
      }
 }
```
Header names are case-insensitive but Hyphenated-Upper-Camel-Case
is the convention.

Examples:

```JSON
    { "headers" :
      {
        "Content-Type" : "application/json;\\s*charset=UTF-8",
        "Transfer-Encoding": "chunked",
        "Cache-Control" : "public.*"
      }
    }
```

## schema

Asserts that one or more JSON structures conform to a JSON schema. There are
several possible forms for this assertion:

  1. `{ "schema" : `*schema*` }`
  1. `{ "schema" : `*schema*`, "values" : `*values*` }`

*schema* may be:
  1. a JSON object which represents an embedded JSON schema
  1. the name of a variable that contains a JSON object
  1. a string in the form of `"@location"` where *location* is the URI of the JSON schema. (Environment variables are expanded within the *location* string.)

*values* may be
  1. a string containing a single variable (the key `"value"` may be used instead of the plural `"values"`)
  1. an array of variable names
  1. For forms 1 and 2, each such variable must be bound to a JSON object or array. The JSON value of the variable is validated against the above referenced JSON schema.

If `"values"` is omitted, the current JSON response
body will be validated against the schema.

The assertion fails if any value does not
conform to the JSON schema, if
the elements do not have the forms described above
or if the referenced JSON schema is not a valid schema.

### Examples

This example will invoke GET to fetch a Swagger document and validate it against
the Swagger 2.0 schema.

```JSON
{
  "name" : "Fetch a Swagger document and validate the JSON body against the Swagger schema.",
  "GET" : "http://www.example.com/swagger/apis/factoryMiner/v1/swagger.json",
  "assert" : { "schema" : "@https://raw.githubusercontent.com/swagger-api/swagger-spec/master/schemas/v2.0/schema.json" }
}
```

----

This example creates an UnRAVL template for fetching a resource (via the
variable `resId`) and validating the response body against a JSON schema.
The next three scripts use the template, setting the `resId` variable
to different values each time.
(Templates are described in [Templates](Temlates.md).)

Note: At present, the UnRAVL `"schema"` assertion does **not**
cache JSON schema objects, so this will reread, parse, and process
the JSON schema object for each API call.

```JSON
[
  {
     "name" : "imlicit.template",
     "preconditions" : { "bound" : "resId"},
     "env"  : {
	        "schema" : "http://www.example.com/api/schemas/resourceX.json"
	       },
     "GET" : "http://www.example.com/api/resources/{resId}",
     "assert" : { "schema" : "@{schema}" }
   },
   {
     "env" : { "restId" : "r123" }
   },
   {
     "env" : { "restId" : "r456" }
   },
   {
     "env" : { "restId" : "r789" }
   }
]
```

----

This example is another way to do the previous test, to validate
that the result of three GET calls all conform to the JSON schema.
This example binds the response bodies (as JSON object) to three
different variables, then validates that the bound JSON objects
conform to the schema. It has the benefit of parsing and
processing the JSON schema only once.

```JSON
[
  {
     "GET" : "http://www.example.com/api/resources/r123",
     "bind" : { "json" : "r123" }
   },
  {
     "GET" : "http://www.example.com/api/resources/r456",
     "bind" : { "json" : "r456" }
   },
  {
     "GET" : "http://www.example.com/api/resources/r789",
     "bind" : { "json" : "r789" }
   },
   {
     "assert" : { "schema" : "http://www.example.com/api/schemas/resourceX.json",
                  "values" : [ "r123", "r456", "r789" ] }
   }
]
```

**TODO**: add a member to the "schema" element to save the parsed/loaded
JSON schema object in the environment, for later use.

## groovy

This assertion allows you to execute Groovy script code
to perform more complex validation and assertions.

```
    { "groovy" : groovy-script }
    { "groovy" : [ script-line,
                   ...
                   script-line,
                   ] }
```

groovy-script and each script-line script must be a string.
In the first form, the groovy-script is evaluated as
a Boolean expression. In the second form, all the lines
are combined with a newline separator and the resulting
string is evaluated as a Groovy script; the result
must also be a Boolean value.

Note that double quote characters in the script must be escaped
as \", but you can use single quotes to quote strings:
```
  "lastName == 'Biesack'"
```
Also, non-ASCII Unicode characters can be
expressed as \uxxxx (four hex digits), as per the JSON syntax rules.
(The entire unRAVL script must be UTF-8.)

If the string or one of the lines in the array form
starts with `@` then the value is assumed to be the name
of a (relative) file resource or a URL and the Groovy script or fragment
is downloaded from there.

Tip: Avoid absolute path names. Use relative path names, and use
portable path notation, namely forward slashes which work on
both Linux **and** Microsoft Windows. Make sure the filename case is correct;
Windows will ignore case differences, but Linux will not.

The values in the current environment are passed to Groovy scripts.
Groovy assertions are often used in conjunction with binding
the JSON result of the API call to a variable using the
"json" extractor, which will parse the JSON response
and put it in a
[`org.fasterxml.jackson.databins.JsonNode`](http://fasterxml.github.io/jackson-databind/javadoc/2.2.0/com/fasterxml/jackson/databind/JsonNode.html) (using the Jackson JSON parser).

The Groovy is evaluated and
the assertion is true iff
the result is a Boolean and the Boolean value is true.
Warning: Other types are ignored.

The Groovy script may also throw a `java.lang.AssertionError`
to indicate a failed assertion.
A `java.lang.RuntimeException` results in a failed
assertion but also results in test errors.

Note that JSON results are represented with Jackson `ObjectNode` or `ArrayNode` values.
Thus, if you wish to use Groovy to select and operate on *values* in a JSON object,
you must extract values with `.textValue() , .doubleValue(),
.longValue(), .intValue(), .booleanValue(),` etc.

Tip: You can use the `"unwrap"` option in the `"json"` extractor
to unwrap JSON objects into Java core types. See
also the "Wrapped vs. unwrapped values" section in
[Bind](Bind.md).

For example, assuming the (wrapped) response body has been saved in a variable
named `result` in the current environment, an assertion such as
 "result[0].type == 'Folder'"
will always be false, even if the *type* field of the 0<sup>th</sup>
element of the `JsonNode` *result* has the value `"Folder"`, because
the type of `JsonNode` is a Jackson `TextNode`, not a `String`.
Thus, the comparison is always false.

Instead, use

 "result[0].type.textValue() == 'Folder'"

In addition, if any element of the `"assert"` or `"preconditions"` arrays
are simple text strings, they are interpreted as Groovy assertions.
Thus,
```JSON
 "assert" : [
      "projectId > 0",
      "projectId != lastProjectId"
 ]
```
is shorthand for embedding each of the expression in `{ "groovy" : expression}` element:

```JSON
 "assert" : [
      { "groovy" : "projectId > 0" },
      { "groovy" : "projectId != lastProjectId" }
 ]
```
Warning: Unless there is an explicit `{ "status" : status-code }` assertion,
UnRAVL will execute and implicit `{ "status" : "2.." }` assertion.
Thus, if a test expects a non-2xx status code, use an explicit `"status"`
assertion and not a `"groovy"` assertion such as `"status == 404"`.

## javascript

The `"javascript"` assertion works just like the `"groovy"` assertion
described above, except that the expression is interpreted by
the JVM's JavaScript (Rhino) interpreter.

```
    { "javascript" : javascript-script }
    { "javascript" : [
                       script-line,
                       ...
                       script-line
                     ] }
```

Note that there are several differences between
the `"groovy"` and `"javascript"` assertions.
Groovy is a scripting
language designed to interoperate with Java, whereas JavaScript
is actually a different language. UnRAVL runs in the JVM
and interprets Java through the Rhino JavaScript engine.

For example, JavaScript strings are not the same as Java strings.
Thus, while a Groovy assertion

```JSON
  { "groovy" : "text.endsWith('.html')" }
```
may work (because Groovy uses Java's String objects),
the assertion

```JSON
  { "javascript" : "text.endsWith('.html')" }
```
will not work because the JavaScript String class
does not have the `endsWith` method that Java's String class has.

## ignore and doc

The `"ignore"` and `"doc"` assertions are
useful to "comment out" an existing assertion in an UnRAVL, since JSON does not support comment syntax.
For example, if you have the assertion
```JSON
 "assert" : [
     { "json" : "@benchmark.json" },
     { "status" : 201 }
     ]
```
and you wish to ignore the "json" assertion, but retain it for future use, wrap it in an `"ignore"`:
```JSON
 "assert" : [
     { "ignore" : { "json" : "@benchmark.json" } },
     { "status" : 201 }
     ]
```

`"doc"` elements allow arbitrary test documentation inside an `"assert"` or `"preconditions"`:
```JSON
"assert" : [
     { "doc" : "Verify that the POST returns 201 Created status to indicate it successfully created a new resource." },
     { "status" : 201 }
     ]
```

## To do

See [Assertions to do](Assertions-to-do.md) for some possible
new assertion elements.
