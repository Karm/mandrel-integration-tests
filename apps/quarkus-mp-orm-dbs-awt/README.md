## Podman, databases

Having trouble with TestContainers and Podman? Take a look: https://quarkus.io/blog/quarkus-devservices-testcontainers-podman/

You might have to enable:
```
$ systemctl --user enable podman.socket
$ systemctl --user start podman.socket
```

This must return OK:
```
$ curl -H "Content-Type: application/json" --unix-socket /var/run/user/$UID/podman/podman.sock  http://localhost/_ping
```

## What is this?

A demo app comprising various dependencies to intentionally
add slightly more work for the compiler. Each component is
covered with a rudimentary test and each dependency has a simple example so as it doesn't get optimized away.

## Testing

```
$ mvn clean verify -Dquarkus.version=2.13.3.Final -Pnative -Dquarkus.profile=test

```

## Databases

Databases are started and stopped automatically via Quarkus devservices using testcontainers. If you need to work with those manually, you can start them e.g. as:

```
podman run --network=host --ulimit memlock=-1:-1 -it -d --rm=true --name quarkus_test_db -e POSTGRES_USER=quarkus -e POSTGRES_PASSWORD=quarkus -e POSTGRES_DB=db1 quay.io/debezium/postgres:15

podman run -it -d --name mariadb -p 49157:3306  --env MARIADB_USER=quarkus --env MARIADB_PASSWORD=quarkus --env MARIADB_ROOT_PASSWORD=quarkus --env MARIADB_DATABASE=db2 mariadb:10.3
```

## Sources

Various demo endpoints and ideas inspired by:

 * https://github.com/Karm/fuzz
 * https://github.com/quarkusio/quarkus-quickstarts
 * https://github.com/quarkusio/quarkus/tree/main/integration-tests/awt
 * https://github.com/quarkusio/quarkus-quickstarts/pull/1154
 * https://github.com/eclipse/microprofile-starter/tree/main/src/it
