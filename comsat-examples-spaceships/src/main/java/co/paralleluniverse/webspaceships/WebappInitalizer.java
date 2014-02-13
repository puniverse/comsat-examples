package co.paralleluniverse.webspaceships;

import static co.paralleluniverse.webspaceships.Spaceships.spaceships;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class WebappInitalizer implements ServletContextListener {
    static final String LINKS_HTML = "<a href='send1'>send1</a> or <a href='send2'>send2</a>";

    private static void startSpaceships() throws Exception, IOException {
        Properties props = new Properties();
        URL url = Thread.currentThread().getContextClassLoader().getResource("spaceships.properties");

        System.out.println("loading properties from " + url);
        props.load(new InputStreamReader(url.openStream()));

        System.out.println("Initializing...");
        spaceships = new Spaceships(props);

        System.out.println("Running...");
        spaceships.run();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    spaceships.mrun();
                } catch (Exception ex) {
                    Logger.getLogger(WebappInitalizer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            startSpaceships();
        } catch (Exception ex) {
            sce.getServletContext().log("error inititalizing sapceships", ex);
        }
        ServletContext sc = sce.getServletContext();
        sc.log("Webapp " + sc.getContextPath().substring(1) + " started ...");
        sc.log("enter http://localhost:8080" + sc.getContextPath() + "/login");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
