package co.paralleluniverse.examples.db;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.jdbc.FiberDataSource;
import co.paralleluniverse.fibers.jdbi.FiberDBI;
import co.paralleluniverse.strands.SuspendableRunnable;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.util.StringMapper;

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

        final FiberDBI jdbi = new FiberDBI(fiberDataSource, Executors.newFixedThreadPool(10,new ThreadFactoryBuilder().setDaemon(true).build()));
        final MyDAO dao = jdbi.onDemand(MyDAO.class);
        new Fiber<Void>(new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                try (Handle h = jdbi.open()) {
                    try {
                        h.execute("create table something (id int primary key, name varchar(100))");
                    } catch (UnableToExecuteStatementException t) {
                        h.execute("truncate table something"); // in case the table exists
                    }
                    for (int i = 0; i < 100; i++)
                        h.execute("insert into something (id, name) values (?, ?)", i, "stranger " + i);
                    System.out.println("name37 is: " + h.createQuery("select name from something where id = :id")
                            .bind("id", 37)
                            .map(StringMapper.FIRST)
                            .first());
                    System.out.println("dao name37: "+dao.findNameById(37));
                    h.execute("drop table something");
                }
            }
        }).start().join();

    }

    @Suspendable
    public interface MyDAO {
        @SqlUpdate("create table something (id int primary key, name varchar(100))")
        void createSomethingTable();

        @SqlUpdate("insert into something (id, name) values (:id, :name)")
        void insert(@Bind("id") int id, @Bind("name") String name);

        @SqlQuery("select name from something where id = :id")
        String findNameById(@Bind("id") int id);
    }
}
