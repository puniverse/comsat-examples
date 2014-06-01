package co.paralleluniverse.examples.db;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.jdbc.FiberDataSource;
import co.paralleluniverse.strands.SuspendableRunnable;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

public class DbExample {
    public static void main(String[] args) throws SQLException, ExecutionException, InterruptedException {
        final JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:./build/h2testdb");
        final DataSource fiberDataSource = new FiberDataSource(ds, 10);

        new Fiber<Void>(new SuspendableRunnable() {

            @Override
            public void run() throws SuspendExecution, InterruptedException {
                String res = BlockingCallsExample.executeSomeSql(fiberDataSource);
                System.out.println(res);
            }
        }).start().join();
    }
}
