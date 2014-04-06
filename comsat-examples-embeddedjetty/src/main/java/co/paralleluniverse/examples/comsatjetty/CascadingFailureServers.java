package co.paralleluniverse.examples.comsatjetty;

import co.paralleluniverse.examples.comsatservlet.MyFiberServlet;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.servlet.FiberHttpServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class CascadingFailureServers {
    public static void main(String[] args) throws Exception {
        QueuedThreadPool queuedThreadPool = new QueuedThreadPool();
        queuedThreadPool.setMaxThreads(5000);
        final Server server = new Server(queuedThreadPool);
        ServerConnector http = new ServerConnector(server);
        http.setPort(8080);
        http.setIdleTimeout(30000);
        http.setAcceptQueueSize(5000);
        server.addConnector(http);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        server.setHandler(context);

        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try (PrintWriter out = resp.getWriter()) {
                    out.println(new Date() + ": welcome to my servlet \n");
                    Thread.sleep(10);
                } catch (InterruptedException  ex) {
                }
            }
        }), "/regular");
        context.addServlet(new ServletHolder(new FiberHttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try (PrintWriter out = resp.getWriter()) {
                    out.println(new Date() + ": welcome to my servlet \n");
                    Fiber.sleep(10);
                } catch (InterruptedException | SuspendExecution ex) {
                }
            }
        }), "/fiber");

        server.start();
        System.out.println("http://localhost:8080/regular");
        System.out.println("http://localhost:8080/fiber");
        System.out.println("Jetty started. Hit enter to stop it...");
        System.in.read();
        server.stop();
    }
}
