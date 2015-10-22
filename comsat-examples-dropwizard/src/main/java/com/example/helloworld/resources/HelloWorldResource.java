package com.example.helloworld.resources;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import com.codahale.metrics.annotation.Timed;
import com.example.helloworld.MyDAO;
import com.example.helloworld.core.Saying;
import com.google.common.base.Optional;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.util.StringMapper;

@Path("/hello-world")
@Produces(MediaType.APPLICATION_JSON)
public class HelloWorldResource {
    final String ALREADY_EXISTS_ERROR = "X0Y32";
    private final String template;
    private final String defaultName;
    private final AtomicLong counter;
    private final HttpClient httpClient;
    private final MyDAO dao;
    private final IDBI jdbi;

    public HelloWorldResource(String template, String defaultName, HttpClient httpClient, IDBI jdbi, MyDAO dao) {
        this.template = template;
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
        this.httpClient = httpClient;
        this.dao = dao;
        this.jdbi = jdbi;
    }

    @GET
    @Timed
    public Saying sayHello(@QueryParam("name") Optional<String> name,
            @QueryParam("sleep") Optional<Integer> sleepParameter) throws InterruptedException, SuspendExecution {
        final String value = String.format(template, name.or(defaultName));
        Fiber.sleep(sleepParameter.or(1000));
        return new Saying(counter.incrementAndGet(), value);
    }

    @GET
    @Path("/http")
    @Timed
    public String sayGoodye(@QueryParam("name") Optional<String> name) throws InterruptedException, SuspendExecution {
        final String value = String.format(template, name.or(defaultName));
        String resp;
        try {
            resp = EntityUtils.toString(httpClient.execute(new HttpGet("http://localhost:8080/hello-world?sleep=100")).getEntity());
        } catch (IOException ex) {
            resp = ex.toString();
        }
        return resp;
    }

    @GET
    @Path("/db/create")
    @Timed
    public String createdb(@QueryParam("name") Optional<String> name) throws InterruptedException, SuspendExecution {
        try (Handle h = jdbi.open()) {
            try {
                h.execute("create table something (id int primary key, name varchar(100))");
                return "table created...";
            } catch (UnableToExecuteStatementException t) {
                h.execute("truncate table something"); // in case the table exists
                return "table truncated...";
            }
        }
    }

    @GET
    @Path("/db/insert")
    @Timed
    public String insertdb(@QueryParam("name") Optional<String> name) throws InterruptedException, SuspendExecution {
        try (Handle h = jdbi.open()) {
            for (int i = 0; i < 100; i++)
                h.execute("insert into something (id, name) values (?, ?)", i, name.or("stranger ") + i);
            return "inserted";
        } catch (UnableToExecuteStatementException t) {
            return "ignored";
        }
    }

    @GET
    @Path("/db/query")
    @Timed
    public String query(@QueryParam("id") Optional<Integer> id) throws InterruptedException, SuspendExecution {
        try (Handle h = jdbi.open()) {
            String first = h.createQuery("select name from something where id = :id")
                    .bind("id", id.or(1))
                    .map(StringMapper.FIRST)
                    .first();
            return first != null ? first : null;
        }
    }

    @GET
    @Path("/db/dao")
    @Timed
    public String daoQuery(@QueryParam("id") Optional<Integer> id) throws InterruptedException, SuspendExecution {
        String first = dao.findNameById(id.or(1));
        return first != null ? first : null;
    }
}
