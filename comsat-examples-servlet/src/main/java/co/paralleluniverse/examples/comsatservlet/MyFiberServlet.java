package co.paralleluniverse.examples.comsatservlet;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.servlet.FiberHttpServlet;
import co.paralleluniverse.fibers.ws.rs.client.AsyncClientBuilder;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.ws.rs.client.Client;

@WebServlet(urlPatterns = "/fiberservlet")
public class MyFiberServlet extends FiberHttpServlet {
    final static Client httpClient = AsyncClientBuilder.newClient();
    final static DataSource ds = BlockingCallsExample.lookupDataSourceJDBC();

    @Override
    @Suspendable
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try (PrintWriter out = resp.getWriter()) {
            out.println(new Date() + ": welcome to " + MyFiberServlet.class.getSimpleName() + "\n");
            try {
                out.println(BlockingCallsExample.doSleep());
                out.println(BlockingCallsExample.callSomeRS(httpClient));
                out.println(BlockingCallsExample.executeSomeSql(ds));
            } catch (SuspendExecution suspendExecution) {
                throw new AssertionError(suspendExecution);
            }
        }
    }
}
