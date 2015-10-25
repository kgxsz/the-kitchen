#The Playground 

#####A respository to experiment and mess around.
[![Stories in Ready](https://badge.waffle.io/kgxsz/the-playground.svg?label=ready&title=Ready)](http://waffle.io/kgxsz/the-playground)

## Local development setup

- You will need to install [Boot](https://github.com/boot-clj/boot#install).
- In order to decrypt the private variables in the config, you will need to setup your nomad credentials in `~/.nomad/the-playground.edn`.
- For a no frills build: `boot build`. This will produce an uberjar.
- You can run it with `java -jar target/the-playground-x.y.z.jar`.
- Run `boot dev` to get a development environment going.
- You can connect to the nREPL server on port 8088.

## Workflow

- When you first connect the nREPL server, bootstrap yourself with `(boot.core/load-data-readers!)`.
- After any changes you make, you can reload the system with `(yoyo/reload!)`.
- To run tests from the repl, do `(boot (test))`.
- To run tests automatically on file changes, do `boot auto-test` in a terminal session.


## Exploring your API with Swagger
- Each handler can be wrapped with documentation middleware.
- Hit [http://petstore.swagger.io](http://petstore.swagger.io/) and point Swagger to `http://localhost:8080/api-docs` to explore the API.
