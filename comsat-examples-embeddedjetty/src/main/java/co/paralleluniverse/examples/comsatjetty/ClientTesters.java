package co.paralleluniverse.examples.comsatjetty;

import co.paralleluniverse.concurrent.util.ThreadUtil;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.ws.rs.client.AsyncClientBuilder;
import co.paralleluniverse.strands.SuspendableRunnable;
import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

public class ClientTesters {

    public static void main(String[] args) throws InterruptedException {
        final String URL = "http://localhost:8080/regular";
        final int REQ_PER_SEC = 100;
        final int DURATION = 20;

        final Client newClient = AsyncClientBuilder.newClient();
        final AtomicInteger ai = new AtomicInteger();
        final RateLimiter rl = RateLimiter.create(REQ_PER_SEC, 10, TimeUnit.SECONDS);

        final CountDownLatch cdl = new CountDownLatch(REQ_PER_SEC);
        System.out.println("starting");
        for (int i = 0; i < DURATION * REQ_PER_SEC; i++) {
            rl.acquire();
            new Fiber<Void>(new SuspendableRunnable() {
                @Override
                public void run() throws SuspendExecution, InterruptedException {
                    try {
                        Response resp = newClient.target(URL).request().buildGet().submit().get(5, TimeUnit.SECONDS);
                        if (resp.getStatus() == 200)
                            ai.incrementAndGet();
                    } catch (ExecutionException | TimeoutException ex) {
                        System.out.println(ex);
                    } finally {
                        cdl.countDown();
                    }
                }
            }).start();
        }
        cdl.await();
        ThreadUtil.dumpThreads();
        System.out.println("finished " + ai);
//        System.exit(0);
    }
}
