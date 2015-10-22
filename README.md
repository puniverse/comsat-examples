# COMSAT Examples

This project includes some examples that makes use of an embedded Jetty servers and several others that are packaged into WAR files and can be deployed to servlet containers.

## Running the Jetty examples

In the shell:

```sh
gradle :comsat-examples-embeddedjetty:run
```

A Jetty web actors example can be run with:

```sh
gradle :comsat-examples-jetty-webactor:run
```

A Dropwizard example can be run with:

```sh
gradle :comsat-examples-dropwizard:runSimple
```

## Non-web examples

```sh
gradle :comsat-examples-db:runSimple
gradle :comsat-examples-retrofit:runSimple
```

## Running the WAR examples

For convenience, we've included a tiny program (`embedded-tomcat`) that runs a Tomcat server, creates a database, and compiles and deploys all WAR examples.

In the shell, type:

```sh
gradle :embedded-tomcat:run
```

Then, in a web browser, access any of the following links:

* [http://localhost:8080/comsat-examples-servlet/fiberservlet](http://localhost:8080/comsat-examples-servlet/fiberservlet) - a servlet that runs in a fiber.
* [http://localhost:8080/comsat-examples-jaxrs/rest/myresource](http://localhost:8080/comsat-examples-jaxrs/rest/myresource) - a JAX-RS REST service that runs in a fiber (the first time this is accessed, it will take a while as Jersey is initialized).
* [http://localhost:8080/comsat-examples-webactors/webactor](http://localhost:8080/comsat-examples-webactors/webactor) - a WebActors example

### Hot Code Swapping

1. Open an new shell and build the upgrade module:

```sh
gradle :comsat-examples-webactors-codeswap:jar
```

2. Run and access [http://localhost:8080/comsat-examples-webactors/webactor](http://localhost:8080/comsat-examples-webactors/webactor) as explained above. 

3. Copy the upgrade module jar file into the `modules/` directory:

```sh
cp comsat-examples-webactors-codeswap/build/libs/comsat-examples-webactors-codeswap.jar modules
```

You will now see the new actor behave differently in the test web page.

## Running Spaceships

Stop the server if running and uncomment the following line in `embedded-tomcat/build.gradle`:

```
from project(":comsat-examples-spaceships").war
```

Restart the server with:

```sh
gradle :embedded-tomcat:run
```

Then, open [http://localhost:8080/comsat-examples-spaceships/login](http://localhost:8080/comsat-examples-spaceships/login) in your browser.

## License

These examples are released under the [MIT license](http://opensource.org/licenses/MIT).

Copyright (c) 2014-2015 Parallel Universe
