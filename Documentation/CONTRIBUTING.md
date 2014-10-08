# <a name="organize"></a>Code organization
- [com.portfolio.data.attachment](../src/com/portfolio/data/attachment)

Other services:<br/>
**ConvertCSV.java**: Send CSV, Receive JSon<br/>
**FileServlet.java**: File server communication<br/>
**XSLService.java**: Specific xml pre-processing and PDF generation

- [com.portfolio.data.provider](../src/com/portfolio/data/provider)

Database manipulation.

_Need code un-merging, some data manipulation need to be made "one level" above, in the Jersey layer or something in-between._

- [com.portfolio.data.utils](../src/com/portfolio/data/utils)

Various helpers

- [com.portfolio.eventbus](../src/com/portfolio/eventbus)

Vague attempt at process configuration.

Say, when we may need to activate or not sending notification to a specific place.
Or sending or not an email for specific events.<br/>
The pipeline will be configured to add blocks, and those are configured to react on certain events.

_Definition incomplete_

- [com.portfolio.rest](../src/com/portfolio/rest)

Jersey REST address definitions and request processing

- [com.portfolio.security](../src/com/portfolio/security)

Helpers related to rights query and
LTI stuff

- [com.portfolio.socialnetwork](../src/com/portfolio/socialnetwork)

ELGG and NING interface

