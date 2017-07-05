UnRAVL is a domain-specific language, coded in JSON, for validating REST APIs.
UnRAVL scripts consist of a JSON description of an HTTP REST API call:

1. HTTP headers (optional)
1. Request body (optional)
1. HTTP method (**`GET, POST, PUT, DELETE, HEAD, PATCH`**)
1. URI
1. Authentication (optional)

For each API call, an UnRAVL script may contain one or more
assertions which validate the results. Some assertions may be expressed as preconditions, which must be true before
making the API call. You can assert:

1. The result body matches expected JSON, text or other data
1. Specific headers exist with specific values
1. HTTP status code is a specific value or is in a specific set
1. A Groovy expression, testing elements of the response or environment, is true
1. others

UnRAVL also supports extracting data from a REST API call's results,
binding those values to variables in the environment,
and using those values for future API call validations.
For example, you can save the **`Location`** header
from a **`POST`** call that creates a resource
and use that URL in a future **`GET`**, **`PUT`**, or **`DELETE`**.

A template facility provides reusable API validation constructs.

UnRAVL was designed and implemented by [David Biesack](https://github.com/DavidBiesack)

## UnRAVL script syntax

The JSON syntax for an UnRAVL test script is as follows. Follow links in the Description to read more
about each test element.

Syntax element.                                  | Description
-------------------------------------------------|------------
{                                                | An UnRAVL test script is a JSON object which begins with an open brace
  `"name" : "test name",`                        | The [name](#name) of this test
  `"doc" : "a comment",`                         | More detailed [comments](#Comments)/description
  `"template" : "template-name",`                | Inheritance of tests via [templates](#template)
  `"env" : {env-bindings},`                      | Assign variables in the [environment](#env)
  `"preconditions" : [assertions],`              | Assert [preconditions](#preconditions) are true before calling the API
  `"if" : condition,`                            | Conditionally execute the test [if](#if) `condition` is true
  `"headers" : {request-headers}`                | Names/values of request [headers](#headers) to pass to the API
  `"auth" : {authentication},`                   | Use [authentication](#auth) to call the API
  `method : "URL",`                              | The HTTP method **`GET, POST, PUT, DELETE, HEAD, PATCH`** and target URL
  `"body" : {body-specification}`                | The request [body](#body) (text, JSON, binary)
  `"bind" : [api-bindings]`                      | [Bind](#bind) (extract) values from the response
  `"assert: [assertions]`                        | Validate the response with [assertions](#assert)
}                                                | End of the JSON object

This defines a *test*.

In the first column, `{ }` indicates that the value is a JSON object; `[ ]` indicates the value is a JSON array.

The order of items in a test does not matter, as UnRAVL processes each element by keys
(but in the order shown above.) All elements are optional.

An UnRAVL script may also be a JSON array
of test objects, test names, or test resource names (file or URLs):
```
[
  { "name" : "test1", ... },
  { "name" : "test2", ... },
  "test1",
  "@file-or-url",
  ...
]
```

If the element of the array is a simple string, it should be the name of a
test that has already been executed. If multiple tests have the same
name, sequential execution order will replace the previous name-to-test mapping
(thus, "last one wins".)

If a `"@file-or-URL"` element names a file (not a URL) without an absolute file location,
it should reside relative to the current directory
(not the directory where the test was found.)

UnRAVL test also execute with an environment: a set of variable bindings
which can contain data to pass to API calls, data parsed from API responses,
and which may be used to perform validations/assertions.

Below are the structural elements of an UnRAVL test,
with syntax and descriptions. Some elements can have
complex or varied bodies which are explained in separate
documentaton.

### name

Use the optional `"name"` element to provide a meaningful, descriptive name for the test.
The name does not have to be an identifier; it may be a phrase or sentence.
You may use the name to invoke the test again.

```JSON
{
  "name" : "Simple test",
  "GET" : "http://www.example.com/api"
}
```

If the `"name"` element is omitted, the test
is given a name based on the current local date and time.

If a test name ends with `.template`, it becomes a
**[template](Templates.md)**. It is not executed at the point it occurs,
but defines reusable test structures that can be included
in other tests via `"template"`; see below.

### template

You can reuse an existing template by naming it:

```JSON
{
   "name" : "A more complex test",
   "template" : "myTemplate",
   "GET" : "http://www.example.com/api"
}
```

The operations (preconditions, assertions, environment, method/URI, etc.)
defined by the template (and any templates that they include)
are inherited by the current test.

See [template](Templates.md) for more details.

### env

`"env"` is a set of variables which runs before a test.

```JSON
"env" : collection-of-bindings
```

This is a collection of `"name"` `:` *`value`* pairs (also known as a *map*).
Each *`value`* is bound to an variable named "name" in the current
environment. These values may later be used by referencing
them as `{name}` inside String values, or as values bound
when Groovy or JavaScript expressions are run.

Example:

```JSON
  "env" : { "lat" : 27.988056,
            "longitude" : 86.925278,
            "API_ROOT" : "http://maps.googleapis.com/maps/api/elevation",
            "obj" : { "x" : 0, "y" : 10, "z" : -1 },
            "a" : [ 0, 1, 4, 9 ]
          }
```

Values may be any valid JSON type. For scalar types (integers, long, doubles, strings, booleans, null),
the corresponding `java.lanag.{Integer, Long, Double, String, Boolean}` or `null` value
is assigned to the variable. For JSON Objects and Arrays, the values are Jackson `com.fasterxml.jackson.databind.node.{ObjectNode, ArrayNode}`.

UnRAVL will also bind the values in the test's template `"env"` block, if one is named.

Variables bound in the environment may be used elsewhere in UnRAVL
tests: for building inputs or request headers or request bodies, or for validating the API
with assertions. See [Environment](#environment) below for details on how variables
may be used.

### preconditions

Preconditions are Boolean expressions which are evaluated *before*
the API call. Preconditions must be true in order for the API call to
occur; if false, they result in assertion errors.

Syntax:

```JSON
  "preconditions" : precondition
  "preconditions" : array-of-precondition
```

See [Assertions](Assertions.md) for details on the syntax of preconditions
and assertions.

### if

The `"if"` condition is an element which allows you to control
conditional execution of the test. If the condition is true,
the test executes; if false, the test is skipped.

The condition is evaluated after the "env" and "preconditions" elements are evaluated (if present), but
before the `"body"`,  `"headers"`,
`"GET"`...`"DELETE"`, `"bind"` or `"assert"` elements.
Thus, the condition expression can use values bound in the `"env"` element.

If there is no "if" element , the implicit condition is "failedAssertionCount == 0" - that is, the test will not run if any previous assertions/preconditions have failed.

Unlike preconditions, a false condition does not
result in an assertion error or test failure;
the test is simply skipped instead.

Format:
```JSON
  "if" : condition
```

Condition may be:
* true : (the JSON true literal). The test will continue.
* false : (the JSON false literal). The test will stop executing.
* string: the value can be the name of UnRAVL environment variable which can be a Boolean object or a JSON BooleanNode value; if true, the test executes.
* If none of the above match, the value is evaluated as a Groovy ( or JavaScript) expression and if true, the test executes.


Execute the test unconditionally.
You can use this to force execution of a test.
For example, an UnRAVL test can be structured as an array of tests, `[ test1, test2, ... ]`.
test1 may create a resource that should be deleted.
A test can use conditional execution to ensure the resource is deleted even if other assertion
or preconditions fail in earlier scripts. For example, test1 might create a resource
and set the value `"exists"` to true in the environment;
test2 can then conditionally delete that resource to clean up:

```
[
   {
     "name" : "create resource",
     "env" : { "resourceLocation" : "" },
     "POST" : "http://www.example.com/api/resources",
     "body" : { ... },
     "bind" : { "headers" : { "resourceLocation" : "Location" },
     "assert" : [ .... ]
   } ,

   { "doc" : "...other tests here which may have failures ... " },

   {
     "test" : "cleanup",
     "doc" : "Delete the resource if the POST above created it. If the location is empty, the POST failed.",
     "if" : "!resourceLocation.isEmpty()",
     "DELETE" : "{resourceLocation}"
   }
]
```

### headers

Use the `headers` element to specify one or more request headers.

```
  "headers" : { request-headers }
```

The `{ request-headers }` is a JSON object consisting one more more header name/value pairs.
The values may use [environment](*Environment) substitution.

```JSON
  "headers" : { "Content-Type" : "application/json",
                "If-Unmodified-Since" : "{lastMod}" }
              }
```

This shows passing two headers, **`Content-Type`** and **`If-Unmodified-Since`**.
The value of the latter is taken from the environment.

Header names are case-insensitive but using *Hyphenated-Upper-Camel-Case*
is the convention.

### auth

Authentication is optionally specified with an `"auth"` element within a script.
UnRAVL supports Basic authentication:
```
  "auth" : { "basic" : true }
```
and Central Authentication Service authentication:
```JSON
  "auth" : { "cas" : "logon-URL" }
```
Credentials may be supplied in a credentials file (recommended), passed
in the environment, in stored directly the script (discouraged).

See [Authentication](Authentication.md) for details and a list
of the different forms of authentication.

### body

For `"PUT"`, "`POST"` and `"PATCH"` methods, the request body can be expressed in multiple ways.

```
 "body" : *body-specification*
```
The *body-specification* may take one of several forms:

```
 "body" : json-request-body
 "body" : { "json" : json-request-body }
 "body" : { "text" : text-request-body }
 "body" : { "binary" : binary-request-body }
```

The different forms are described in [Body](Body.md).

### Method and URL

The UnRAVL script specifies the API call with the method name and URL.

```JSON
  method : URL
```

The *`method`* and *`URL`* are both strings. The *method* must be one of
`"GET", "HEAD", "POST", "PUT", "DELETE", "PATCH"`. (Spaces are *not*
allowed in the method names, and the methods **must** be ALLCAPS.)
The *`URL`* is the REST API being tested.
Only one *`method`* `:` *`URL`* pair is allowed per test object.
Use an array of test objects to perform multiple API calls.

Examples:

```JSON
[
 { "GET" : "http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278" },
 { "DELETE" : "http://www.example.com/rest/myService/myCollection/xk4783" }
]
```

The *`URL`* is subject to [environment substitution](#environment):

For example,
```JSON
  "DELETE" : "{BASE_URL}/rest/myService/myCollection/{itemId}"
```

will replace `{BASE_URL}` and `{itemId}` with the values of those
variables in the current environment. Variables are assigned by
the [`"env"`](#env),
`"groovy"`, or
`"javascript"` or other [`"bind"`](#bind)
elements described below.

### bind

The `"bind"` elements extract data from an API response body and headers
and store the values in variables that may be used to test the response.
Some bind elements also validate data, acting as implicit assertions.

The general form is

  "bind" : [
      extractor_0,
      extractor_1,
      ...
      extractor_n
      ]

If you only have one extractor, you do not need to embed it in an array:

  "bind" : extractor_0

See [Bind](Bind.md) for details of the various ways to extract
and bind values from an API response.

### assert

The "assert" element names one or more assertions to run
after the API call. Assertions are used to test the result of the API call:
the status code, the response headers, and the response body.

Syntax:

```
  "assert" : assertion
  "assert" : [ assertions ]
```

See [Assertions](Assertions.md) for full details.

## Environment

Tests run within an *environment*, which is a mapping of name/value pairs,
or *bindings*. When executing an UnRAVL script, values can be captured
from the responses and stored in that environment. These environment values can be used
for specific assertion values. This is done by referring to bindings
to the script.

Variable names should be simple identifiers, so that they can be referenced
in Groovy (or JavaScript) code. However, UnRAVL also imports all system variables, so some environment variables
may exist with names such as `os.name`, `user.name` and `user.dir`. However,
such variables are not available in Groovy (or JavaScript) scripts (but Groovy can directly access Java system properties via `System.getProperty(name)`.

#### Variable value substitution

An environment binding is *referenced* by using the `{varName}`
notation within string values in the JSON. (Do not use leading or trailing whitespace.)
The value of that binding is substituted, replacing `{varName}` with the value bound
to *`varName`*. 

For example: 
'''JSON
{
  "env" : { "name" : "UnRAVL",
            "suggestion" : `"Use {name} to test your APIs"`
           }
}

The variable `name` is bound to tje JSON string `"UnRAVL"`.
The value of the variable named `"suggestion"` will be
`"Use {name} to test your APIs"`.

If a variable is not bound, this notation passes through. That is,
the value of `{undefinedVariable}` is `{undefinedVariable}`.

Note that [Groovy or JavaScript scripts](Assertions.md) do not need the braces as the environment bindings
are made directly available as script variables.

#### Automatically bound variables

* system properties
  * at startup, all Java system properties (including values passed via `-Dprop=value`) are bound
* `name`
  * the name of the currently executing script (from the `"name"` element of the script)
* `unravlScript`
  * the UnRAVL script object currently executing
* `status`
  * is always bound to the HTTP status of the latest API call.
* `responseBody`
  * is bound to the response body for the `"json"`, `"text"`, and `"binary"` extractors (the JSON value, text response as a single `String`, or the bytes of the response as a `byte[]`, respectively)
* Unicode characters
  * The special notation {U+nnnn} may be used to insert Unicode characters into text anywhere variable expansion is allowed.  You must supply four hex digits. For example,
  * `{U+002D}` will be replaced with the right curly (close) brace, `}`,
  * `{U+03C0}` will be replaced with the Unicode GREEK SMALL LETTER PI, &#x3c0;
  * UnRAVL does not allow rebinding these values.

#### Alternate text for unbound variables

You may also provide alternate text to use if a variable is
not defined. Add a '|' vertical bar (pipe) character and the alternative text
before the closing brace:

`{varName|alt text}`

If `varName` is bound, the result will be the value of that variable and the *`alt text`* is discarded,
else the result will be *`alt text`*. The *`alt text`* can also
contain nested variable references. (Left and right curly braces must match inside the *`alt text`*.)

Processing of *`alt text`* is only supported for variable names
that consist of the following characters
* alphanumeric characters `a-Z A-Z 0-9`,
* '`.`' (period)
* '`_`' (underscore)
* '`$`' (dollar sign)
* '`-`' (dash)

This syntax restriction on names is so that other syntax using braces and vertical bars can pass
through directly. For example, an UnRAVL script may contain text such as
the following

```
   if (pending)
      { next = state|ACTIVE ? active : inactive; }
```

UnRAVL should not process this like a `{varName|alt text}`
expression. If it did, it would parse the second line of text as
a variable named `" next = state"`
and *`alt text`* of `"ACTIVE ? active : inactive; "`,

Since there is no UnRAVL variable binding for a variable with the name ` next = state`,
the result would be the alternate text `"ACTIVE ? active : inactive; "`
The full result would be the unexpected

```
    if (pending)
       ACTIVE ? active : inactive;
```
**Warning**: because of this *`alt text`* processing, some text may
be replaced even if you do not intend it to be interpreted as
variable references. For example, if A and D are not bound, the input text

```
    {A|B|C} | {D|E} is the same as {A|B|C|D|E}
```

will result in the text

```
    B|C | E is the same as B|C|D|E
```

If you wish to prevent varibable expansion or include unbalanced left and right braces in *`alt text`*,
you may use Unicode character replacement.
```
    {U+007b}A|B|C} | {U+007b}D|E} is the same as {U+007b}A|B|C|D|E}
```

Here is another example. If you want the value
of the variable `end`, but use `%}` if end is not defined, you cannot use
    `{end|}}`
because the first `}` will be used as the end of the variable reference,
and the *`alt text`* will be empty.
If `end` is bound to the string `$` then `{end|}}` will result in `$}`.
However, if `end` is *not* bound, the result will be `}`.
Neither of these results is desired.

Instead, use
```
    {end|%{U+007D}}
```
Here, the *`alt text`* is `%{U+007D}` which will expand to the desired `%}`.

#### Variable value substitution

The `{varName}` variable substitution notation described above only allows for the variable bound value to be embedded in a string. 
In certain cases,  non-string values for variables is needed instead. In order to get the actual JSON type value, use a the `{@varName@}` notation instead. The entire string has to be of the form `"{@varName@}"` for the actual value replacement. That is, the following string will not get resolved with this notation: "prefix text `{@varName@}` other text". However, it can be successfully expanded with the string variable replacement: "prefix text `{varName}` other text".

The variable name in this case is limited to the use of the following:

*  alphanumeric characters `a-Z A-Z 0-9`,
* '`.`' (period)
* '`_`' (underscore)
* '`$`' (dollar sign)
* '`-`' (dash)

Note that the alternate text binding for the actual value substitution is not yet supported. 

Below is an example of both notations used for variable substitution.

```JSON
{ "name" : "GamePlayers",
  "env" : { "API_ROOT": "http://www.httpbin.org",
            "ucode": "495",
            "maxScore": 1000.5,
            "minScore": 10,
            "streetName": "Mango Circle Dr.",
            "unitNumber": 451,
            "cityName": "Live Oak",
            "stateName": "FL",
            "countryName": "USA",
            "zipCode": 32064,
            "address": {"street" : "{streetName}", "unit": "{@unitNumber@}", "city": "{cityName}", "state": "{stateName}", "country": "{countryName}", "zip": "{@zipCode@}"},
            "phone": "3866549000",
            "email": "stranger@somemail.com",
            "gameOver": true,
            "scores": [10, 99, 100.5, 900, 1000.5]         
   },
  "POST" : "{API_ROOT}/post",
  "headers" : { "Content-Type" : "text/plain",
                "Accept" : "text/plain",
                "Agent" : "UnRAVL"
              },
  "body" : {"id": "{ucode}0008",
            "name": "Bob",
            "score": "{@maxScore@}",
            "homeAddress": "{@address@}",
            "contact": "{address} {phone} {email}",
            "isFinished": "{@gameOver@}",
            "gameScores": "{@scores@}"
   },
  "bind" : [ { "json" : "@-" },
             { "groovy" : {
                 "actual" : "responseBody.json",
                 "sentHeaders" : "responseBody.headers"
                 }
             }
             ],
  "assert" : [
      "sentHeaders['Accept'].textValue() == 'text/plain'",
      "sentHeaders['Content-Type'].textValue() == 'text/plain'",
      "sentHeaders['Agent'].textValue() == 'UnRAVL'",
      "actual.id.isTextual()", 
      "actual.score.isDouble()",  
      "actual.isFinished.isBoolean()", 
      "actual.homeAddress.isObject()", 
      "actual.contact.isTextual()", 
      "actual.gameScores.isArray()",
      "actual.id.textValue().equals('{ucode}0008')",
      "actual.name.textValue().equals('Bob')",
      "actual.score.doubleValue() == maxScore",
      "actual.isFinished.booleanValue() == gameOver",
      "actual.homeAddress.get('street').textValue().equals(streetName)",
      "actual.homeAddress.get('unit').intValue() == unitNumber",
      "actual.homeAddress.get('city').textValue().equals(cityName)",
      "actual.homeAddress.get('state').textValue().equals(stateName)",
      "actual.homeAddress.get('country').textValue().equals(countryName)",
      "actual.homeAddress.get('zip').intValue() == zipCode",
      "actual.contact.textValue().equals(address.toString() + ' ' + phone + ' ' + email)",
      "actual.gameScores == scores"
   ]
}
```

#### Examples

Here is an example that shows setting values in an environment,
using them to invoke an API call, and binding values from the API results.

```JSON
{
  "name" : "GoogleEverestElevation",
  "env" : { "latitude" : 27.988056,
            "longitude" : 86.925278,
            "expectedElevation" :  8815.7158203125,
            "API_ROOT" : "http://maps.googleapis.com/maps/api/elevation",
            "outputDir" : "{java.io.tmpdir}"
          },
  "GET" : "{API_ROOT}/json?locations={latitude},{longitude}",
  "bind" : [
     { "json" : "@{outputDir}/{name}.json" },
     { "jsonPath" : { "actualElevation" : "$.results[0].elevation",
                      "actualLat"       : "$.results[0].location.lat",
                      "actualLong"      : "$.results[0].location.lng",
                      "actualStatus"    : "$.status"
                    }
     }
  ],
  "assert": [
    "actualElevation == expectedElevation",
    "actualLat == latitude",
    "actualLong == longitude",
    "actualStatus == 'OK'"
  ]
}
```

Here, the response body is saved in a file in the output directory with a file name based on the test name,
for later analysis/use, or for creating a benchmark for later validation.
The specific values from the body are bound to environment variables, `actualElevation`, `actualLat`, `actualLong`,
and `actualStatus` which are used in later assertions.

The values in the current environment are passed
to the Groovy (or JavaScript) scripts as script language bindings, so the `{varName}` notation is not needed
in the assertion expressions. If you use `{varName}` in a groovy expression, it will be substituted
before the script is interpreted, so it is useful to *generate* the script source,
or to inject content into string literals. That is, the Groovy
interpreter will evaluate the  expression
`"actualLat == latitude"` by comparing the values of the variables
`actualLat` and `latitude`. However, for the expression
`"{actualLat} == {latitude}"`, UnRAVL will perform veriable substitution
*before* passing the expression to Groovy  to evaluate. Thus,
the expression evaluated by Groovy is will be `27.988056 == 27.988056`.
Both will be true, but derived in different ways. The former lets Groovy
resolve the variable; the latter lets UnRAVL expand the variables as
text first.

Note how the literal value `"OK"` may be quoted `'OK'`
in the Groovy assertion `"actualStatus 'OK'"`.

Values captured in the environment by `"bind"` elements may be used in subsequent tests.

## Miscellaneous

### Script language

UnRAVL interprets some quoted expressions as Groovy expressions by default.
For example, the "if" guard for conditional execution of a script is defined as

```
   "if" : "expression"
```
If an element of the "assert" or "preconditions" elements
is a naked string, it is evaluated using Groovy:
```
  "assert" : [ "response != null",
               "response.size() > 10"
             ]
```
Also, the "links" and "hrefs" extractors in a "bind" element can use
a Groovy path expression to extract link objects.

You can override the default language (Groovy) by setting the system
property `unravl.script.language` to `javascript`.
Java 7 and higher comes with a JavaScript engine. UnRAVL also includes
[`groovy-all`](http://www.groovy-lang.org/download.html) which includes a Groovy script engine (the default).
Thus, the valid values for
`unravl.script.language` are `groovy` and `javascript`.

The script language must have a corresponding assertion
class so that `"assert"` elements can be converted into
explicit assert objects. The above is converted to the equivalent
```
  "assert" : [ { "groovy" : "response != null" },
               { "groovy" : "response.size() > 10" }
             ]
```

If you want all such expressions to use JavaScript instead of
Groovy, use the setting

```
  export UNRAVL_OPT=-Dunravl.script.language=javascript
```
when you launch UnRAVL with the `bin/unravl.sh` script.

If you are running UnRVL from Java (or Groovy....) and instantiating an `UnRAVLRuntime` instance,
you can set the script language with `runtime.setScriptLanguage("javascript");`

### Comments

Unfortunately, JSON does not provide any syntax for enclosing comments.

Although the Jackson parser has an option to enable Java-style `/* ... */` and `//`
comments, not all JSON tools support this, so UnRAVL does not allow them.
Instead, each test (or template) may have `"doc"` elements which may be a string
or an array of strings, or actually any valid JSON. (This also allows
you to comment out a block of JSON UnRAVL script by wrapping it in a
`{ "doc" : argitrary-JSON-to-be-commented-out }` block

The `"assert"` and `"bind"` elements also allow a nested `"doc"` or `"ignore"` element , such as

```JSON
{
  "GET" : "http://www.example.com/api",
  "doc" : "assert that the response code is 200 OK",
  "bind" : [
        { "json" : "jsonValue" },
        { "ignore" : { "text" : "textValue" } }
  ],
  "assert" : [
    { "status" : 200 },
    { "ignore" : { "status" : [201, 204] } }
  ]
}
```

### Redirection

The default operation is to apply redirection if a 3xx responses is received.
For 300, 301, and 302, UnRAVL will retry `GET` and `HEAD` requests,
but not other methods (via Apache HTTP Components.)

### JUnit integration

It is quite easy to run UnRAVL scripts from JUnit.
Place your scripts in a directory and use the
`com.sas.unravl.assertions.JUnitWrapper` class
to run all the tests in that directory.

For builds, you can add a dependency to UnRAVL as described in the Logistics section.
Then in a JUnit test method, run the tests in your test scripts directory.
See `src/test/java/com/sas/unravl/test/TestScripts.java` which runs the testsin
the UnRAVL source folder, `src/test/scripts` (but not in subdirectories)

### Plugins

The UnRAVL implementation can support custom assertions, body generators,
and extractors by defining Java classes which implement the appropriate
interfaces or inherit from the base classes.
These are loaded via Spring component-scan (of the `com.sas` packages) and
dependency injection of an `UnRALPlugins` instance
through which the plug-in can register the plug-in
class.

Each such class use an annotation to define it's
shortcut code (such as `"json"` or `"headers"`
as used in the other assert objects above.)

For example, the `"bound"` assertion is defined as:

```Java
import com.sas.unravl.annotations.Assertion;
@Assertion("bound")
public class BoundAssertion extends BaseAssertion {
```

By convention, tag names should use *lowerCamelCase*.

Be careful to not use names that are already in use
or which could be confused with attributes of other assertions.

Plugins register themselves via the following method in the inherited
base class, such as from com.sas.unravl.assertions.BaseAssertion:
```Java

    @Autowired
    public void setPluginManager(UnRAVLPlugins plugins) {
        plugins.addAssertion(this.getClass());
    }
```
Spring creates the UnRAVLPlugins instance and autowires
to all the discoved plugin components.

If you implement a plugin, inherit from the base class,
or else use the `@Autowired` annotation, implement this setter,
and call the appropriate `runtime.add*Plugin*` method.

### Logging

By default, UnRAVL will log the REST API calls' HTTP method and URI, request and response bodies,
request and response headers, and HTTP status codes.

You can control the level of logging by with command line argument used before
the scripts:
 unravl -q &lt;<em>scripts</em>&gt;
 unravl --quiet &lt;<em>scripts</em>&gt;
 unravl -v &lt;<em>scripts</em>&gt;
 unravl --verbose &lt;<em>scripts</em>&gt;

These options select an alternate Log4j configuration file, respectively:
--quiet
 The quiet configuration logs just the script name and the passed/failed assertion.
 (source in `src/main/resources/log4j-quiet.properties`)
--verbose
 the verbose configuration (TRACE) lists all the API call inputs, headers, and output, as well as the results : `log4j-trace.properties` (source in `src/main/resources/log4j-trace.properties`)
of the assertions
The default
([log4j.properties](http://gitlab.sas.com/sasdjb/unravl/blob/master/src/main/resources/log4j.properties))
lists each scripts and the results of the assertions

You can configure more fine-grained logging other Log4J configuration files:

  `java -Dlog4j.configuration=your-log4j.properties ...`

## Logistics

UnRAVL is built with either [Gradle](http://gradle.org/) or [Maven](https://maven.apache.org/).
The Gradle wrapper is included in the project,
so no extra setup is required (unlike Maven)
to create a jar file `sas.unravl-<em>version</em>.jar`

* Gradle build puts the jar in ./build/libs
* Windows:
  * `.\gradlew.bat build`
* Linux
  * `./gradlew build`
* Maven build puts the jar in ./target
  * `mvn clean compile package`

## Running UnRAVL from the command line

First, copy all dependencies

`gradlew copyDeps`

to download the jars necessary to run UnRAVL with a classpath pointing to the dependcy directory

Run UnRAVL as:
```bash
    src/main/bin/unravl.sh src/test/scripts/hello.json  # from Linux or Mac OS X
    src\main\bin\unravl.bat src\test\scripts\hello.json # from Windows
```

You may set system properties to configure UnRAVL or initialize the UnRAVL
[environment](#Environment) variables by setting the `UNRAVL_OPT` environment variable
before running the scripts. For example, for Linux:
```bash
export UNRAVL_OPT='-Dapi.host=www.example.com -Dapi.port=8080 -Dkeys="a b c"'
```
or from Windows:
```winbatch
set UNRAVL_OPT=-Dapi.host=www.example.com -Dapi.port=8080 -Dkeys="a b c"
```

If you wish to deploy UnRAVL outside the build
directory, You may deploy all the jar files from
`build/lib` and `build/output/lib`
into a single directory
named by `UNRAVL_LIB` and then run

 `java $UNRAVL_OPT -classpath "$UNRAVL_LIB/*" com.sas.unravl.Main file.json file.json`

If you embed UnRAVL in a Spring Java application,
you should augment your Spring configuration to include
```
 classpath:/META-INF/spring/unravlApplicationContext.xml
```
