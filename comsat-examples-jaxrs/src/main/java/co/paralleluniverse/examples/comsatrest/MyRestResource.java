package co.paralleluniverse.examples.comsatrest;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.ws.rs.client.AsyncClientBuilder;
import co.paralleluniverse.strands.Strand;
import java.util.Date;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("myresource")
public class MyRestResource {
    private static final Client httpClient = AsyncClientBuilder.newClient();
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getIt() throws SuspendExecution, InterruptedException {
        String blockingOutput = BlockingCallsExample.doSleep()+ BlockingCallsExample.callSomeRS(httpClient);
        return "<h1>Parallel Universe JAX-RS Post Example</h1>\n"
                + "<p>Blocking calls allowed in this context, for example:</p>\n"
                + "<textarea cols=80 rows=10>" + blockingOutput + "</textarea>\n"
                + "<form action=\"myresource/post\" method=\"post\">\n"
                + "    <p>Name : <input type=\"text\" name=\"name\" value=\"myName\" /></p>\n"
                + "    <p>Age : <input type=\"number\" name=\"age\" value=\"23\" /></p>\n"
                + "    <input type=\"submit\" value=\"Send Post\" />\n"
                + "</form>\n"
                + "<h3>Accessed using rest GET call</h3>";
    }

    @POST
    @Path("/post")
    public Response addUser(@FormParam("name") String name, @FormParam("age") int age) throws SuspendExecution, InterruptedException {
        Strand.sleep(100);
        return Response.status(200).entity(new Date() + " post response: name:" + name + ", age:" + age).build();
    }
}
