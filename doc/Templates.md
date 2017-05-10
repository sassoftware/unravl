## Templates ##

If a test name ends with `.template` then it is assumed to be a *template*.
Templates are not executed but saved. Other tests can reuse saved templates
by specifying a `"template" : "template-name"` attribute.
The template name is subject to [environment substitution](Reference.md#Environment)
so you can choose preconditions and assertions by dynamically
loading templates based on a variable binding.

The "`.template`" suffix is assumed if omitted if omitted from *`template-name`*.

All the environment settings, if conditions, request body and headers, API calls, bindings, and assertions
of the template are added to a test.  A test or template will evaluate these template
elements before its own. Note that "env" elements in templates may
overwrite variable bindings.

Templates can also refer to other templates, so you can chain capabilities.

The special template `implicit.template`, if defined, is applied to all
tests or templates which do not have a `"template"` element.
It can be used to define global assertions or defaults for the
entire test suite without having to repeatedly name the template
in each test.

TODO: Allow the "templates" value to be an array of template names

For example, to test that the Google elevation API is idempotent,
one could create a template for it, then run that multiple times
to assert the same response each time:

```JSON
[

  {
    "name" : "Google Everest Elevation setup",
    "env" : { "url" : "http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false" },
    "doc" : "Save the response to GoogleEverestElevation.json",
    "GET" : "{url}",
    "bind" : { "json" : "@GoogleEverestElevation.json" }
  },

  {
    "name" : "GoogleEverestElevation.template",
    "doc" : "Template which invokes the GET and asserts the response equals the contents of GoogleEverestElevation.json",
    "GET" : "{url}",
    "assert" : { "json" :  "@GoogleEverestElevation.json" }
  },

  {
    "name" : "GoogleEverestElevation GET 1",
    "template" : "GoogleEverestElevation.template"
  },

  {
    "name" : "GoogleEverestElevation GET 2",
    "template" : "GoogleEverestElevation.template"
  },

  {
    "name" : "GoogleEverestElevation GET 3",
    "template" : "GoogleEverestElevation.template"
  },

]
```

The first test binds `url` in the environment; performs a GET
on the target API and stores the JSON result in a file.
the remaining tests use that result.

The second test is a *template* which performs a GET and asserts the
response body matches the JSON in the file `"@GoogleEverestElevation.json"`
which was created by the previous test.

The third through fifth test calls the GET API as defined
in the template and asserts the response matches the saved response.
Each of these calls will also verify the HTTP status code is
200 because of the implicit status assertion.

Templates can also include other templates, creating a chain.
It is invalid to define a cycle.
