# JWT runtime configuration

All runtime profiles require `JWT_SIGNING_SECRET`. There is no default signing key,
including in `dev`. Supply at least 64 UTF-8 bytes from a cryptographically random
source. For an intentional local development session:

```sh
export JWT_SIGNING_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Keep a deployment's key in its secret-management environment, not in Git, command
logs or a frontend variable. Use the same key on instances of the same deployment.
The `.env.example` is documentation, not an automatically loaded Spring file.
Never deploy with the `dev` profile or development account seeders.

S1 preserves HS512 and the eight-hour token lifetime. Rotate away from the former
embedded key when deploying S1; existing tokens then require a fresh login. The
former key and obvious placeholder/weak values are rejected at startup. Length
and simple weakness checks cannot establish randomness; generate the key securely.

Every bearer request loads the current account. Deleted/inactive/unrecognized-role
accounts, or tokens whose role no longer matches the current role, receive 401.
Users whose roles change must log in again. No authentication state is retained in
an HTTP session. The frontend already clears stored authentication and returns to
login on 401, including operational calls that suppress 403 redirects.

Automated tests use random keys from `src/test/resources/config/application.properties`.
That resource is test-only and is not packaged into the runtime JAR. IDE tests must
include test resources on their classpath. No production database records need to
be changed to run the focused security tests.
