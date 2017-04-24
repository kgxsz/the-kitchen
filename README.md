# The Playground 

##### A repository to experiment and mess around with Swagger.

| SnapCI | Waffle | Swagger |
| :----: | :----: | :-----: |
| [![Build Status](https://snap-ci.com/kgxsz/the-playground/branch/master/build_image)](https://snap-ci.com/kgxsz/the-playground/branch/master) | [![Stories in Ready](https://badge.waffle.io/kgxsz/the-playground.svg?label=ready&title=Ready)](http://waffle.io/kgxsz/the-playground) |

To see it in action, first hit the [API](https://kgxsz-the-playground.herokuapp.com) to get the dyno up and running, then you can hit the [Swagger Docs](http://petstore.swagger.io/?url=https://kgxsz-the-playground.herokuapp.com/api-docs)

## Local development setup

- You will need to install [Boot](https://github.com/boot-clj/boot#install).
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
- To explore your API running on localhost, hit [local Swagger](http://petstore.swagger.io/?url=http://localhost:8080/api-docs).
