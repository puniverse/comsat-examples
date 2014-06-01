package co.paralleluniverse.examples.db;

import co.paralleluniverse.fibers.SuspendExecution;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

public class BlockingCallsExample {
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
}
