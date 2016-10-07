This page describes the "auth" element of [UnRAVL scripts](Reference.md).

The scriptlet form is
```
  "auth" : { name : value, options... }
```
the *`name`* determines what type of authentication,
and the *`value`* and *`options`* are used to configure that authentication.
Credentials can be included in the options, or stored
separately from the script; see [Credentials](#credentials) below.

UnRAVL supports three authentication models,
* [basic](#basic) Basic Authentication
* [oauth2](#oauth2) OAuth2 Authentication
* [cas](#cas) Central Authentication Service
* [none](#none) No authentication

Tip: You can put the `"auth"` member in a template (including
`implicit.template`) and all scripts which inherit from that template will use that authentication method.

## basic

Basic Authentication locates credentials for the REST API call host
via the `.netrc` file (see [Credentials](#credentials) below) and adds an

`Authentication: Basic *encoded-credentials*`

header to the request.

The basic auth syntax is one of:
```JSON
  "auth" : "basic"
  "auth" : { "basic" : true }
  "auth" : { "basic" : true, "login" : "testuserid" }
  "auth" : { "basic" : true, "login" : "testuserid", "password" : "testSecret" }
```

If the *`"password"`* or *`"login"`* id are omitted, they are obtained
from the credentials file, described below, based on the host
name of the API call. You may use *`"user"`* instead of *`"login"`*.

## oauth2

OAuth2 is a fairly complex authentication and authorization protocol,
with many variations. UnRAVL supports a simple OAuth2 model;
more variations such as three-legged authentication may be added
in the future by extending the configuration.

OAUth2 authentication will authenticate using a separate login or
authentication service via a REST API call, obtaining
an *access token* which is then used on the target API call.
OAUth2 uses two sets of credentials:
1. client ID and client secret
2. user ID and password

The form is one of
```JSON
  "auth" : { "oauth2" : "oauth-server-URL" }
  "auth" : { "oauth2" : "oauth-server-URL", options }
```

You may use the key `"oauth2"`, `"oauth"`, `"OAuth2"`, or `"OAuth"`.

If the credentials are not provided in the *`"oauth2"`* object,
they are found via the credentials provider, such as the
`.netrc` file.

The OAuth2 object will POST request to the authentication
server named by the *`oauth-server-URL`* value. The POST body
is a `application/x-www-form-urlencoded`
body constructed by passing corresponding values for the  form parameters
`grant_type`, `username`, and `password`

UnRAVL will pass the user ID and user password from
the credentials provider (such as from the line in the `.netrc` file
that matches the *`oauth-server-URL`* host/machine).

The `POST` call will also use [Basic authentication](#basic) based on
the client ID and client secret.

When using `.netrc` credential provider, the `.netrc` file
may include the two additional fields, `clientId` and `clientSecret`
(these keys are not case sensitive).

The OAuth2 token server should return a JSON response, which
must be a valid JSON object. The `access_token` value is
read from the respnse. The value will be bound in the current
UnRAVL environment using the name `access_token` (Warning: this
may replace an existing binding for that variable.)

Next, the OAuth2 object will add a
```
Authentication: Bearer {access_token}
```
header to the current REST API call.

### OAuth2 authentication options

The `"oauth2"` object allows some optional parameters.

#### Credentials

If you want to include credentials directly in the script
instead of using credentials in the credential provider
(such as the `.netrc` file), you can pass them:

```
    "user" : "userid-string", "password" : "password-string"
```
or
```
    "user" : "testuserid", "password" : "testSecret",
    "clientId" : "client-id-string", "clientSecret" : "client-secret-string"
```
#### Static access token

If you have a static access token, you can store that in the `.netrc`
file or include it in the options:

```
    "accessToken" : "access-token-string"
```
In this case, the above `POST` to the *`oauth2-server-URL`* is skipped.

#### Alternate access_token query parameter

By default the OAuth2 authentication will also add the `access_token`
as a query parameter `?access_token=access-token-string`.
If the service requires a different query parameter, use
the `"parameter"` option

```
    "parameter" : "access-token-query-param-name"
```

For example, for

```
        "GET" : "https://my-api.com/api/resources/res-22939",
        "auth" : { "oauth2" : "https://my-api.com/oauth/token",
                   "parameter" : "auth-token" }
```

UnRAVL will add the access token to the URL as `auth-token` instead of `access_token`, such as:
```
https://my-api.com/api/resources/res-22939&auth-token=6f8dbc338af9
```

Use
```
   "parameter" : false
```
to suppress the access token parameter.

#### Alternate access_token environment parameter

By default, oauth2 will bind the access token string
as the variable `access_token` in the current environment
in case you want to use it in other ways via `{access_token}`.
The caller can choose to bind to a different variable name
using the `"bindAccessToken" : "varName"` option.
This is useful if a script is dealing with multiple
OAUth2 servers or different access tokens for different APIs.

For example, for
```
"bindAccessToken" : "bitlyAccessToken"
```
option, the oauth2 object will bind the variable
`bitlyAccessToken` in the environment instead of
the variable `access_token`.

## cas

Central Authentication Service authentication will
use a login URL to autheticate the user credentials
and acquire a *Ticket Granting Ticket* (TGT). For each API call
in an UnRAVL script, UnRAVL will use the TGT to request a *Service Ticket*
for that API URL and append the Service Ticket to
the request URL.

The form is
```JSON
  "auth" : { "cas" : "login-URL" }
  "auth" : { "cas" : "login-URL", "login" : "testuserid" }
  "auth" : { "cas" : "login-URL", "login" : "testuserid", "password" : "testSecret" }
```

Example:

```JSON
  "auth" : { "cas" : "http://{sas.logon.host}:7980/SASLogon/rest/v1/tickets" },
  "GET" : "http://{my.app.host}/SASMyApi/rest/myEndpoint/myResource"
```

UnRAVL will lookup the
The credentials for the {sas.logon.host} URL
in the file `.netrc` as [described below](#credentials), then use the full URL
"http://{sas.logon.host}:7980/SASLogon/rest/v1/tickets" to obtain a TGT.
Then using that TGT, UnRAVL will request a service ticket for the URL
"http://{my.app.host}/SASMyApi/rest/myEndpoint/myResource" (after expanding the
environment variables), then append the ticket. The net result will look something
like
 GET http://www.example.com/SASMyApi/rest/myEndpoint/myResource?ticket=ST-188763-kEcYVdVfAVYdmEyyfZWg-cas

The TGT is stored in the environment using `&lt;<em>hostname</em>&gt;.TGT`,
*where `&lt;<em>hostname</em>&gt;` is taken from the `login-URL`. The TGT
will be resused in other scripts that call the same host.

## None

You may also disable authentication with
```
   "auth" : false
```
This is useful if you define authentication in a [template](Templates.md) (including `implicit.template`)
which is used in multiple UnRAVL scripts, but you wish to make an unauthenticated call.
Use `"auth" : false` to disable the inherited authentication.

Example:
```JSON
[
  {
    "name" : "implicit.template",
    "auth" : { "OAuth2" : "https://my-api.com/auth/tokens" }
  },
  {
    "name" : "GET a resource using authentication defined in the implicit template",
    "GET" : "https://my-api.com/api/some/resource"
  },
  {
    "name" : "DELETE a resource using authentication defined in the implicit template",
    "DELETE" : "https://my-api.com/api/some/resource"
  },
  {
    "name" : "GET a static resource without using authentication, overrideing the auth in the implicit template",
    "GET" : "https://my-api.com/api/static-resources/logo.png",
    "auth" : false
  }
]
```
## Credentials

For best security, the credentials for authentication are stored in
the file `.netrc` in the current directory (if it exists),
or in the user's home directory (`~/.netrc` on Linux,
or `%USERPROFILE%\\_netrc` on Windows, for example).
Users can protect these files using file system security.
For example, on Linux:
```
 chmod go-rwx .netrc
```
will not allow others users to read or write the `.netrc` file.

The format of the file is a simplified version of the standard
[Unix netrc file format](http://linux.die.net/man/5/netrc).

Warning: The *`default`* entry and
*`macdef`* in the [`.netrc`](http://linux.die.net/man/5/netrc) spec are not supported.

Credentials must be specified entirely on one line:

`machine` *`hostname`* `login` *`userid`* `password` *`password`*

`machine` *`hostname`* `login` *`userid`* `password` *`password`* `port` *`port`*

such as

```
 machine rdcesx51019.race.sas.com login testuser password testuserSecret123
 machine rdcesx51019.race.sas.com user testuser password testuserSecret123 port 8080
```

You may use either the .netrc standard `login` key, or
the key `user` to identify the login/user ID.

The port is optional; use this if you want to match
a test API on a non-default port such as port 8080.

The *hostname* field must *exactly* match the hostname in UnRAVL API
calls (ignoring case).

When using `.netrc` credential provider for OAuth2 authentication,
the `.netrc` file may include the additional fields, `clientId`, `clientSecret`
and accessToken (these keys are not case sensitive).
For example:

```
 host machine my.api.auth.com user my-api-userid password my-api-passwd clientId my-api-client-id clientSecret my-api-client-secret
```

(Use double quotes around values that contain embedded spaces).

You may also embed the credentials directly inside the authentication element in the script.
These may be the login id and password (if there are no security issues with directly embedding
the plain text credentials in the script, such as when using test users).

```JSON
  "auth" : { "basic" : true,
             "login" : "testuser",
             "password" : "testuserSecret123" }
```
Your script `"auth"` elements may also use UnRAVL environment variable
substitution. For example, you can pass the credentials at startup:

```
$ export UNRAVL_OPT='-Dhostname.login=testuser -Dhostnme.password=testuserSecret123'
$ unravl script1.json scipt2.json
```

Warning: Doing this may leave the credentials readable in the process tables or to other tools that can read the currently running commands and all their parameters.

You may set credentials in Java system properties before creating the UnRAVL runtime
(for example, when running UnRAVL scripts in JUnit tests).
The scripts can then access the credentials from the environment, such as

```JSON
  "auth" : { "basic" : true,
             "login" : "{hostname.login}",
             "password" : "{hostname.password}" }
```

or

```JSON
  "auth" : { "oauth2" : "{oauth2Url}",
             "login" : "{hostname.login}",
             "password" : "{hostname.password}" }
```

If the `"login"` is embedded but no `"password"`,
UnRAVL will look up the password for that host/login pair in the `.netrc` file.
