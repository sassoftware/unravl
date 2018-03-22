## Uniform REST API Validation Language (UnRAVL) - a JSON domain-specific language for validating REST APIs

UnRAVL is a domain-specific language, coded in JSON, for validating REST APIs.
UnRAVL scripts consist of a JSON description of a REST API call:

1. HTTP method
1. URI
1. HTTP headers (optional)
1. Request body (optional)
1. Authentication (optional)

For each API call, an UnRAVL script may also contain
*assertions* which validate the results. You can assert:

1. The result body matches expected JSON, text or other data
1. Specific headers exist with specific values
1. HTTP status code is a specific value or is in a specific set

UnRAVL also supports extracting data from a REST API call's results
and using those values for assertions or in future API calls.

A template facility provides reusable API call and validation constructs.

Although initially conceived as a REST validation tool, UnRAVL
is also well-suited for use as a *REST scripting language*, for
stringing together a set of interrelated REST calls, extracting
results and headers from early calls to be used when making
subsequent calls. Users may find it useful to associate `.unravl`
files with the UnRAVL jar file via the batch scripts in the
[`src/main/bin`](https://github.com/sassoftware/unravl/tree/master/src/main/bin) directory
so you can run scripts from your file explorer.

UnRAVL was designed and initially implemented by David Biesack [@DavidBiesack](https://github.com/DavidBiesack) (GitHub)
[@DavidBiesack](https://twitter.com/DavidBiesack) (Twitter).
unRAVL is an implementation of [*API validation system*, U.S. Patent No. 9,552,237](http://patft.uspto.gov/netacgi/nph-Parser?Sect1=PTO1&Sect2=HITOFF&d=PALL&p=1&u=%2Fnetahtml%2FPTO%2Fsrchnum.htm&r=1&f=G&l=50&s1=9552237.PN.&OS=PN/9552237&RS=PN/9552237).

## A basic REST validation

The most fundamental form of validation is to invoke an API and
validate the response code and response body.

Let's consider the REST call
```
 GET  http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false
```
This can be captured in JSON using the UnRAVL syntax
```JSON
{ "GET" : "http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false" }
```
This is a fully functioning UnRAVL script. Besides executing the call, UnRAVL will also validate
that the call returns a 2xx level HTTP status code, indicating success in one form or another.

However, that is not highly useful.
This particular REST call should return the JSON body:
```JSON
{
   "results" : [
      {
         "elevation" : 8815.7158203125,
         "location" : {
            "lat" : 27.988056,
            "lng" : 86.92527800000001
         },
         "resolution" : 152.7032318115234
      }
   ],
   "status" : "OK"
}
```

UnRAVL scripts are JSON documents that encode the call, response, and assertions.
This is useful since much of the test data (request bodies, responses)
will be JSON.

Below is an UnRAVL script that performs the above REST call, asserts
the result matches the expected JSON and that the HTTP response is 200:

```JSON
{
  "name" : "GoogleEverestElevation",
  "GET" : "http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false",
  "assert": [
    { "status" : 200 },
    { "json" :

{ "results" : [ { "elevation" : 8815.7158203125,
        "location" : { "lat" : 27.988056,
            "lng" : 86.92527800000001
          },
        "resolution" : 152.7032318115234
      } ],
  "status" : "OK"
}
     }
  ]
}
```

This shows invoking an API using the GET method. `"GET", "HEAD", "POST", "PUT", "DELETE", "PATCH"` are the allowed HTTP verbs.
For `POST`, `PUT` and `PATCH`, you can also pass a request body.

Next, we verify the result with a set of assertions:

1. the expected HTTP status code, 200 OK ; the value could also be an array of allowed response types, such as [200, 204] or a string value which is a regular expression, such as "2.." If a `"status"` assertion is omitted, the status code must match "2.."; that is, be a 200-level status code.
1. assert the JSON body matches an expected JSON structure.

The simplest response body assertion is a literal assertion that the body matches the expected JSON,
although admittedly this is somewhat fragile.
The implementation of such benchmark comparison assertions performs a structural comparison
and allows for different order of items in JSON objects, as well as whitespace differences.

Another form of response assertion allows comparing the received body to the contents of a benchmark file
rather than literal JSON in the file.
(In the JSON based DSL, this form is required for XML benchamrks.)

```
{
  "name" : "GoogleEverestElevation",
  "GET" : "http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false",
  "assert": [
    { "status" : 200 },
    { "json" :  "@{benchmarks}/{name}.json" }
    ]
}
```

The `@` notation indicates the JSON is located at a file or URL, not in line.
This example expects two variables to be bound in the UnRAVL environment:
`benchmarks` is the name of a directory containing JSON files.
name is the current test name (set by the `"name"` element of the script),
with the value `"GoogleEverestElevation"` in this case. The
`{varName}` notation is used to evaluate the value of an environment
variable. More on this later.

However, because representation assertions are fragile,
an alternate approach would be to perform more specific assertions for data elements in the body.

```
{
  "name" : "GoogleEverestElevation",
  "GET" : "http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false",
  "bind" : { "json" : "response" },
  "assert": [
     "response.results[0].elevation.doubleValue() == 8815.7158203125",
     "response.results[0].location.lat.doubleValue() == 27.988056",
     "response.results[0].location.lng.doubleValue() == 86.925278",
     "response.status.textValue().equals('OK')"
    ]
}
```

UnRAVL scripts also have an *environment*, which is a set of name/value pairs or variables.
You can set variables explicitly, or bind them based on the result of API calls.
For example, the above binds the JSON response body to a JsonNode (using the
[Jackson](https://github.com/FasterXML/jackson)
library for JSON) named `response`. (See [`"json"`](doc/Bind.md#json)
for details.) This variable may be used to compare nested
values, as seen in the `assert` array. Each assertion string is a Groovy
expression that must evaluate to true for the test to pass.
(You can also use JavaScript.)
Groovy expressions can walk JSON structures naturally by accessing object values
by name, or array items by index. The `doubleValue()` method extracts
the numeric value of a Jackson [NumericNode](https://fasterxml.github.io/jackson-databind/javadoc/2.2.0/com/fasterxml/jackson/databind/node/NumericNode.html) item.

Most string values in UnRAVL scripts are subject to environment substitution
which replaces substrings of the form `{varName}` with the value
of the named variable from the environment.

## Syntax and reference

To see the full syntax for UnRAVL scripts, refer to [Reference](doc/Reference.md).

## Interactive mode

UnRAVL provides a simple interactive interface for running scripts.
See [Interactive mode](doc/ui/UI.md) for details.

## Releases
See [Releases](doc/Releases.md)

## Running UnRAVL

You can download the source from this repo and run
```
    git clone git@github.com:sassoftware/unravl.git
    cd unravl
    ./gradlew clean build copyDeps
```
Run UnRAVL as:
```bash
    src/main/bin/unravl.sh src/test/scripts/hello.json  # from Linux or Mac OS X
    src\main\bin\unravl.bat src\test\scripts\hello.json # from Windows
```

Alternatively, you can download the binary release.
Create a directory `unravl` for your local copy of UnRAVL, `cd unravl` to that directory,
then download [a release](https://github.com/sassoftware/unravl/releases).
Unzip the release file in the `unravl` directory.
Run UnRAVL using the scripts in the `bin` directory, as described above.
(In the binary distribution the scripts are in `bin` not `src/main/bin`.)

When integrated into a Java application, you may run UnRAVL
scripts by instantiating an `UnRAVLRuntime` object,
then creating an `UnRAVL` object based on a Jackson `ObjectNode`
or ArrayNode, then invoking the run() method.
```Java
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.ApiCall;
import com.sas.unravl.util.Json;
...
  UnRAVLRuntime runtime = new UnRAVLRuntime();
  UnRAVL unravl = new UnRAVL(runtime, objectNode);
  ApiCall call = unravl.run();
```
The `UnRAVLRuntime` contains the environment variables
and may be reused to run multiple `UnRAVL` instances.

## Contributing

Contributors are welcome to join the project.
See [ContributorAgreement.txt](ContributorAgreement.txt).

To contribute, submit issues for bugs or enhancments.
[Fork this repo](https://help.github.com/articles/fork-a-repo),
then clone your GitHub fork:

```
$ git clone git@github.com:userid/unravl.git
```

(Change *`userid`* to your GutHub user id). Next, set the Git upstream
which will allow you to merge from master:
```
$ cd unravl
$ git remote add upstream git@github.com:sassoftware/unravl.git
```

Before doing local development, be sure to sync your local code:

To [sync your fork with master](https://help.github.com/articles/syncing-a-fork/)
after changes have been merged at https://github.com/sassoftware/unravl :
```
$ cd unravl # change to your local clone
$ git fetch upstream # get all branches
$ git checkout master
$ git merge upstream/master
```

Create a local branch for your changes, then push to your personal unravl repo
and [create a GitHub pull request](https://help.github.com/articles/using-pull-requests/)
for submitting new contributions.

Contributions should use the Eclipse format configuration in `eclipse-java-format.xml`
and organize imports in com, java, javax, org order (alphabetical, with grouping)

Contributors are listed in [CONTRIBUTORS.md](CONTRIBUTORS.md).

## License

UnRAVL is released under the [Apache 2.0 License](LICENSE).
