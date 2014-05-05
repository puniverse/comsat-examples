package com.example.helloworld;

import co.paralleluniverse.fibers.dropwizard.FiberApplication;
import co.paralleluniverse.fibers.dropwizard.FiberHttpClientBuilder;
import co.paralleluniverse.fibers.dropwizard.FiberDBIFactory;
import com.example.helloworld.resources.HelloWorldResource;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.HttpClient;
import org.skife.jdbi.v2.IDBI;

public class HelloWorldApplication extends FiberApplication<HelloWorldConfiguration> {
    public static void main(String[] args) throws Exception {
        new HelloWorldApplication().run(args);
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    @Override
    public void initialize(Bootstrap<HelloWorldConfiguration> bootstrap) {
    }

    @Override
    public void fiberRun(HelloWorldConfiguration configuration,
            final Environment environment) throws ClassNotFoundException {

        final HttpClient fhc = new FiberHttpClientBuilder(environment).
                using(configuration.getHttpClientConfiguration()).
                build("FiberHttpClient");

        final IDBI jdbi = new FiberDBIFactory().build(environment, configuration.getDatabase(), "postgresql");
        final MyDAO dao = jdbi.onDemand(MyDAO.class);

        final HelloWorldResource helloWorldResource = new HelloWorldResource(
                configuration.getTemplate(),
                configuration.getDefaultName(),
                fhc,
                jdbi,
                dao
        );
        environment.jersey().register(helloWorldResource);       
    }
}
