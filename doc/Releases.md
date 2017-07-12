## UnRAVL releases

* [Release v1.3.1](https://github.com/sassoftware/unravl/releases/tag/v1.3.1)
  * Add a prefix option to the href links extractor to resolve relative links
  * Make Unix and Windows unravl CLI scripts more robust (running from a dev)
  * Minor doc cleanup
* [Release v1.2.1](https://github.com/sassoftware/unravl/releases/tag/v1.2.1)
  * Improved Cut/Copy/Paste, layout, threading, and other
    interactive mode improvements
* [Release v1.2.0](https://github.com/sassoftware/unravl/releases/tag/v1.2.0)
  * Added a [user interface](ui/UI.md) for running UnRAVL scripts
    interactively.
  * Added a `"wrap"` option to the [`"jsonPath"`](Bind.md#jsonpath) extractor
* [Release v1.1.0](https://github.com/sassoftware/unravl/releases/tag/v1.1.0)
  * Added [OAuth2](doc/Authentication.md#oauth2) authentication
  * A script can disable inherited authentication
  * UnRAVL variables are now expanded in JSON object keys, not just in
    string values
  * Added a [`"form"`](doc/Body.md#form) body generator
  * Expand UnRAVL variables in `"basic"` authentication parameters
  * UnRAVL now follows 3xx redirects for HEAD calls as well as GET calls
  * Added an `"unwrap"` option for the [`"json"`](doc/Bind.md#json)
    response extractor and the [`"links"` and `"hrefs"`](doc/Bind.md#links-and-hrefs )
    extractors
  * Added the[`"jsonPath"`](doc/Bind.md#jsonPath) body extractor
  * Various bug fixes
* [Release v1.0.0](https://github.com/sassoftware/unravl/releases/tag/v1.0.0)
  * Removed deprecated features (equals assertion)
  * Reduced dependency on JUnit. All behaviors, assertions, etc. do not depend on JUnit.
* [Release v0.2.1](https://github.com/sassoftware/unravl/releases/tag/v0.2.1) is the current release of UnRAVL.
  * Added UnRVAL.cancel() support for test scripts and runtime
  * Added UnRAVL.execute() method to allow running multiple JsonObject scripts
* [Release v0.2.0](https://github.com/sassoftware/unravl/releases/tag/v0.2.0) is the first public release of UnRAVL.
