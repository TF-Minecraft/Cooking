# Documentation impact waiver

This file is a bypass only when the same diff that skips system docs also changes this file.

Leave the marker below unset so a normal behavior change cannot pass by accident.

```text
docs-impact: none
reason: Paper marks the existing legacy text and serialization APIs deprecated. These source edits add documented method-scoped compatibility annotations only; gameplay, stored data, and configuration remain unchanged. Build changes explicitly retain annotation processing and provide an SLF4J logger for tests.
```
