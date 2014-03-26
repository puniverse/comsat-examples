package co.paralleluniverse.webspaceships;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.ExitMessage;
import co.paralleluniverse.actors.LifecycleMessage;
import co.paralleluniverse.actors.ShutdownMessage;
import co.paralleluniverse.common.monitoring.Metrics;
import co.paralleluniverse.comsat.webactors.HttpRequest;
import co.paralleluniverse.comsat.webactors.HttpResponse;
import static co.paralleluniverse.comsat.webactors.HttpResponse.ok;
import co.paralleluniverse.comsat.webactors.WebActor;
import co.paralleluniverse.comsat.webactors.WebDataMessage;
import co.paralleluniverse.comsat.webactors.WebSocketOpened;
import co.paralleluniverse.fibers.SuspendExecution;
import static co.paralleluniverse.webspaceships.Spaceships.spaceships;
import com.codahale.metrics.Meter;

@WebActor(httpUrlPatterns = "/login", webSocketUrlPatterns = "/game")
public class SpaceshipConnectorActor extends BasicActor<Object, Void> {
    private static final Meter rcvMetric = Metrics.meter("msgsRecieved");
    private static final Meter openMetric = Metrics.meter("openWsRecieved");
    private static final Meter httpMetric = Metrics.meter("httpRecieved");
    private ActorRef<Object> spaceship = null;

    @Override
    protected Void doRun() throws InterruptedException, SuspendExecution {
        ActorRef<WebDataMessage> client = null;
        String loginName = "empty";
        try {
            for (;;) {
                Object message = receive();
                if (message instanceof HttpRequest) {
                    httpMetric.mark();
                    HttpRequest msg = (HttpRequest) message;
                    loginName = msg.getParameter("name");
                    if (loginName == null)
                        msg.getFrom().send(ok(self(), msg, nameFormHtml()).build());
                    else {
                        loginName = truncate(loginName.replaceAll("[\"\'<>/\\\\]", ""), 10); // protect against js injection
                        if (spaceships.getControlledCount().get() / spaceships.players > 0.9)
                            msg.getFrom().send(ok(self(), msg, noMoreSpaceshipsHtml()).build());
                        else
                            msg.getFrom().send(ok(self(), msg, gameHtml()).build());
                    }
                } else if (message instanceof WebSocketOpened) {
                    openMetric.mark();

                    client = ((WebSocketOpened) message).getFrom();

                    System.out.println("NEW CLIENT " + client + ": " + loginName);

                    watch(client);
                } else if (message instanceof WebDataMessage) {
                    rcvMetric.mark();
                    WebDataMessage msg = (WebDataMessage) message;
                    if (spaceship == null) { // too many players
                        spaceship = spaceships.spawnControlledSpaceship(client, "c." + loginName);
                        if (spaceship == null) {
                            System.out.println("TOO MANY PLAYERS");
                            break;
                        }

                        watch(spaceship);
                    }
                    if (msg.getFrom() == client)
                        spaceship.send(msg);
                } else if (message instanceof ExitMessage) {
                    if (spaceship != null)
                        spaceships.notifyControlledSpaceshipDied();
                    ActorRef actor = ((ExitMessage) message).getActor();
                    if (actor == spaceship) { // the spaceship is dead
                        spaceship = null; // create a new spaceship
                    } else { // the client is dead
                        if (spaceship != null)
                            spaceship.send(new WebDataMessage(self(), "exit"));
                        System.out.println("CLIENT " + client + " IS DEAD: " + loginName);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // throw new RuntimeException();
        }
        return null;
    }

    @Override
    protected Object handleLifecycleMessage(LifecycleMessage m) {
        if (m instanceof ExitMessage)
            return m; // handle this message in the main loop
        return super.handleLifecycleMessage(m);
    }

    String nameFormHtml() {
        return "<html><h1>Parallel Universe Spaceships webactors demo</h1>\n"
                + "<h3>Please enter your name:</h3>"
                + "<form action=\"login\" method=\"post\">\n"
                + "    <p>Name : <input type=\"text\" name=\"name\" value=\"myName\" /></p>\n"
                + "    <input type=\"submit\" value=\"Start Playing\" />\n"
                + "</form>\n"
                + "Left/Right: turn; up/down: accelarate/decelerate; space: shoot</html>";
    }

    String noMoreSpaceshipsHtml() {
        return "<html><h1>Sorry. No free spaceship. Please try again later</h1>\n</html>";
    }

    String gameHtml() {
        return "<html>\n"
                + "    <head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">\n"
                + "        <title>ParallelUniverse Web Spaceships Demo</title>\n"
                + "        <meta name=\"viewport\" content=\"width=device-width, height=device-height, initial-scale=1\">\n"
                + "        <style type=\"text/css\">\n"
                + "            body {\n"
                + "                background-color: #000000;\n"
                + "                overflow: hidden;\n"
                + "            }\n"
                + "        </style>\n"
                + "        <script src=\"js/Three.r51.js\"></script>\n"
                + "        <script src=\"js/Detector.js\"></script>\n"
                + "    </head>\n"
                + "    <body>\n"
                + "\n"
                + "        <div id=\"WebGLCanvas\">\n"
                + "            <script src=\"SpaceshipRenderer.js\"></script>\n"
                + "            <canvas width=\"1270\" height=\"685\"></canvas>\n"
                + "\n"
                + "        </div>\n"
                + "    </body>\n"
                + "</html>\n"
                + "";
    }

    private static String truncate(String s, int len) {
        return s.substring(0, Math.min(len, s.length()));
    }
}
