package co.paralleluniverse.examples;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.retrofit.FiberRestAdapterBuilder;
import co.paralleluniverse.strands.SuspendableRunnable;
import java.util.List;
import java.util.concurrent.ExecutionException;
import retrofit.RestAdapter;
import retrofit.http.GET;
import retrofit.http.Path;

public class RetrofitExample {
    public static void main(String... args) throws ExecutionException, InterruptedException {
        // Create a very simple REST adapter which points the GitHub API endpoint.
        RestAdapter restAdapter = new FiberRestAdapterBuilder().setEndpoint("https://api.github.com").build();

        // Create an instance of our GitHub API interface.
        final GitHub github = restAdapter.create(GitHub.class);

        // Fetch and print a list of the contributors to this library.
        new Fiber<Void>(new SuspendableRunnable() {

            @Override
            public void run() throws SuspendExecution, InterruptedException {
                // Now you can call your API from fiber context
                List<Contributor> contributors = github.contributors("puniverse", "comsat");
                for (Contributor contributor : contributors)
                    System.out.println(contributor.login + " (" + contributor.contributions + ")");
            }
        }).start().join();
    }

    @Suspendable
    public static interface GitHub {

        @GET(value = "/repos/{owner}/{repo}/contributors")
        List<Contributor> contributors(@Path(value = "owner") String owner, @Path(value = "repo") String repo);

    }

    public static class Contributor {
        String login;
        int contributions;
    }
}
