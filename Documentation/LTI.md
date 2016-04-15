Karuta is LTI 1.x compatible

LTI interface is at:
- /karuta-backend/lti

Changes needed in configKaruta.properties:
- basiclti.provider.{SECRET}.secret={KEY}

And if you deployed the UI under a different name
- lti\_redirect\_location=/{CHANGED NAME}/karuta/htm/list.htm