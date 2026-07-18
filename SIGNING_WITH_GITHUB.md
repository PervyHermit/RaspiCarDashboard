# Persistent APK signing with GitHub

Do not commit a private keystore or its passwords to the public repository.

Open the GitHub repository and go to:

**Settings → Secrets and variables → Actions → New repository secret**

Create these four secrets:

## `RASPI_KEYSTORE_BASE64`

The complete single-line Base64 contents of the private JKS keystore.

## `RASPI_KEYSTORE_PASSWORD`

The keystore password.

## `RASPI_KEY_ALIAS`

The key alias.

## `RASPI_KEY_PASSWORD`

The private-key password.

The workflow decodes the keystore only inside the temporary GitHub runner. The keystore is not uploaded as an artifact.

Keep an offline backup of the keystore and credentials. Losing the key means future APKs cannot update installations signed by that key.

If no signing secrets are configured, the workflow falls back to a normal debug build.
