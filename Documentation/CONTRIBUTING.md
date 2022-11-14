# <a name="organize"></a>Code organization
- [com.eportfolium.karuta.data.attachment](../src/com/eportfolium/karuta/data/attachment)

Other services:<br/>
**ConvertCSV.java**: Send CSV, Receive JSon<br/>
**FileServlet.java**: File server communication<br/>
**XSLService.java**: Specific xml pre-processing and PDF generation

- [com.eportfolium.karuta.data.provider](../src/com/eportfolium/karuta/data/provider)

Database manipulation.

_Need code un-merging, some data manipulation need to be made "one level" above, in the Jersey layer or something in-between._

- [com.eportfolium.karuta.data.utils](../src/com/eportfolium/karuta/data/utils)

Various helpers

- [com.eportfolium.karuta.eventbus](../src/com/eportfolium/karuta/eventbus)

Vague attempt at process configuration.

Say, when we may need to activate or not sending notification to a specific place.
Or sending or not an email for specific events.<br/>
The pipeline will be configured to add blocks, and those are configured to react on certain events.

_Definition incomplete_

- [com.eportfolium.karuta.rest](../src/com/eportfolium/karuta/rest)

Jersey REST address definitions and request processing

- [com.eportfolium.karuta.security](../src/com/eportfolium/karuta/security)

Helpers related to rights query and
LTI stuff

- [com.eportfolium.karuta.socialnetwork](../src/com/eportfolium/karuta/socialnetwork)

ELGG and NING interface

