package co.paralleluniverse.examples.comsatservlet;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.ws.rs.client.Client;

public class BlockingCallsExample {
    public static String callSomeRS(Client httpClient) throws SuspendExecution {
        final String TEST_URL = "http://localhost:8080/test-servlet/test";
        return new StringBuilder()
                .append("** RS-Client Example **\n")
                .append("Calling: ").append(TEST_URL).append("\n")
                .append("Response: ")
                .append(httpClient.target(TEST_URL).request().get().readEntity(String.class))
                .append("\n").toString();
    }

    public static String doSleep() throws SuspendExecution {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("** Blocking Sleep Example **\n");
            sb.append("Current nanos: ").append(System.nanoTime()).append("\n");
            sb.append("Call sleep(100)\n");
            Strand.sleep(100);
            sb.append("Current nanos: ").append(System.nanoTime()).append("\n\n");
            return sb.toString();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String executeSomeSql(final DataSource ds) throws SuspendExecution {
        try (Connection conn = ds.getConnection()) {
            return executeSomeSql(conn);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String executeSomeSql(final Connection conn) throws SQLException, SuspendExecution {
        final String ALREADY_EXISTS_ERROR = "X0Y32";

        Statement stmt = conn.createStatement();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        out.println("** Blocking JDBC Example **");
        out.println("Creating table...");
        try {
            stmt.executeUpdate("CREATE TABLE PERSONS(name CHAR(32), age INT, PRIMARY KEY (name))");
        } catch (SQLException e) {
            if (e.getSQLState().equals(ALREADY_EXISTS_ERROR))
                stmt.executeUpdate("TRUNCATE TABLE PERSONS");
            else
                throw e;
        }
        StringBuilder sb = new StringBuilder("INSERT INTO PERSONS VALUES");
        for (int i = 0; i < 100; i++)
            sb.append(i == 0 ? "" : ",").append("('name").append(i).append("',").append(i).append(")");
        out.println("Inserting 100 people...");
        stmt.executeUpdate(sb.toString());

        try (final PreparedStatement pstmt = conn.prepareStatement("select * from PERSONS where age >= ?")) {
            int c = 0;
            final int QUERY_AGE = 40;
            pstmt.setInt(1, QUERY_AGE);
            out.println("Query for persons above age of " + QUERY_AGE);
            for (ResultSet res = pstmt.executeQuery(); res.next();)
                c++;
            out.println("Results: " + c + " rows");
        }
        out.println("Drop table...\n");
        stmt.executeUpdate("DROP TABLE PERSONS");
        return baos.toString();
    }

    public static DataSource lookupDataSourceJDBC() {
        try {
            Context envCtx = (Context) new InitialContext().lookup("java:comp/env");
            return (DataSource) envCtx.lookup("jdbc/fiberdb");
        } catch (NamingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
