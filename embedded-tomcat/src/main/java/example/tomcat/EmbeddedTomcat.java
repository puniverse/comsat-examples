package example.tomcat;

import java.io.File;
import java.sql.SQLException;
import org.apache.catalina.startup.Tomcat;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.apache.tomcat.util.descriptor.web.ContextResource;

public class EmbeddedTomcat extends Tomcat {
    public static void main(String[] args) throws Exception {
        EmbeddedTomcat tomcat = new EmbeddedTomcat();
        String buildWebAppDir = new File(".").getAbsolutePath() + "/build/webapp";
        tomcat.setBaseDir(buildWebAppDir);

        for (final File fileEntry : new File(buildWebAppDir).listFiles()) {
            if (fileEntry.getName().endsWith(".war")) {
                String war = fileEntry.getName().substring(0, fileEntry.getName().length() - ".war".length());
                System.out.println("Loading WAR: " + war);
                tomcat.addWebapp("/" + war, fileEntry.getAbsolutePath());
            }
        }
        registerDB(tomcat);

        tomcat.start();
        try {
            // block forever for service mode
            while (args.length > 0)
                Thread.sleep(3600000);
        } catch (InterruptedException ignored) {
        }
        System.out.println("Hit enter to exit...");
        //noinspection ResultOfMethodCallIgnored
        System.in.read();
        tomcat.stop();
    }

    private static void registerDB(EmbeddedTomcat tomcat) throws SQLException {
        // create db if doesn't exist
        EmbeddedDataSource jdb = new EmbeddedDataSource();
        jdb.setDatabaseName("build/sample");
        jdb.setCreateDatabase("create");
        jdb.getConnection().close();

        ContextResource dbDsRes = new ContextResource();
        dbDsRes.setName("jdbc/gdb");
        dbDsRes.setAuth("Container");
        dbDsRes.setType("javax.sql.DataSource");
        dbDsRes.setProperty("maxActive", "100");
        dbDsRes.setProperty("maxIdle", "30");
        dbDsRes.setProperty("maxWait", "10000");
        dbDsRes.setScope("Sharable");
        dbDsRes.setProperty("driverClassName", "org.apache.derby.jdbc.EmbeddedDriver");
        dbDsRes.setProperty("url", "jdbc:derby:build/sample");
        dbDsRes.setProperty("username", "");
        dbDsRes.setProperty("password", "");

        tomcat.enableNaming();
        tomcat.getServer().getGlobalNamingResources().addResource(dbDsRes);
    }
}
