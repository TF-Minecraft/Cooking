# Add a config option

1. Add the key to the YAML in `src/main/resources`, not to a file already copied on the server. Say so in the change notes: existing servers keep the old file until the key is added there.
2. Read it in the loader with a default that matches the behavior you want when the key is missing.
3. Add the file and key to [../reference/configuration.md](../reference/configuration.md) if it is a new file or a new contract.
4. Map the file in `docs/manifest.json` if it is new.
5. Do not load `cookware.yml` for new heat keys. Use `config.yml`.
6. `/cooking reload` picks up YAML. It does not apply SQLite migrations.
