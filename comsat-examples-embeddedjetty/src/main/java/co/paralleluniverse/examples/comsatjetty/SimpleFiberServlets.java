package co.paralleluniverse.examples.comsatjetty;

import co.paralleluniverse.examples.comsatservlet.BlockingCallsExample;
import co.paralleluniverse.examples.comsatservlet.MyFiberServlet;
import co.paralleluniverse.examples.test.TestServlet;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.jdbc.FiberDataSource;
import co.paralleluniverse.fibers.servlet.FiberHttpServlet;
import co.paralleluniverse.fibers.ws.rs.client.AsyncClientBuilder;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.ws.rs.client.Client;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class SimpleFiberServlets {
    public static void main(String[] args) throws Exception {
        final Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        server.setHandler(context);
        final DataSource jdb = createDB();
        final Client httpClient = AsyncClientBuilder.newClient();

        context.addServlet(new ServletHolder(new FiberHttpServlet() {
            @Override
            @Suspendable
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try (PrintWriter out = resp.getWriter()) {
                    out.println(new Date() + ": welcome to " + MyFiberServlet.class.getSimpleName() + "\n");
                    try {
                        out.println(BlockingCallsExample.doSleep());
                        out.println(BlockingCallsExample.callSomeRS(httpClient));
                        out.println(BlockingCallsExample.executeSomeSql(jdb));
                    } catch (SuspendExecution suspendExecution) {
                        new AssertionError(suspendExecution);
                    }
                }
            }
        }), "/myFiberServlet");
        context.addServlet(TestServlet.class, "/test-servlet/test");
        context.addServlet(new ServletHolder(new FiberHttpServlet() {
            @Override
            @Suspendable
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                // FiberServlet is allowed to forward the call
                getServletConfig().getServletContext().getRequestDispatcher("/myFiberServlet").forward(req, resp);
            }
        }), "/forward");
        server.start();
        System.out.println("http://localhost:8080/myFiberServlet");
        System.out.println("http://localhost:8080/forward");
        System.out.println("Jetty started. Hit enter to stop it...");
        System.in.read();
        server.stop();
    }

    static DataSource createDB() throws SQLException {
        EmbeddedDataSource jdb = new EmbeddedDataSource();
        jdb.setDatabaseName("build/sample");
        jdb.setCreateDatabase("create");
        jdb.getConnection().close();
        
        return FiberDataSource.wrap(jdb, 2);
    }
}
