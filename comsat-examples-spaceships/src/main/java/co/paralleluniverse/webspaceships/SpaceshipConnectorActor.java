package co.paralleluniverse.webspaceships;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.ExitMessage;
import co.paralleluniverse.actors.LifecycleMessage;
import co.paralleluniverse.common.monitoring.Metrics;
import co.paralleluniverse.comsat.webactors.HttpRequest;
import co.paralleluniverse.comsat.webactors.HttpResponse;
import static co.paralleluniverse.comsat.webactors.HttpResponse.ok;
import co.paralleluniverse.comsat.webactors.WebActor;
import co.paralleluniverse.comsat.webactors.WebDataMessage;
import co.paralleluniverse.comsat.webactors.WebSocketOpened;
import co.paralleluniverse.fibers.SuspendExecution;
import static co.paralleluniverse.spaceships.Spaceships.spaceships;
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
                        msg.getFrom().send(new HttpResponse(self(), ok(msg, nameFormHtml())));
                    else {
                        loginName = truncate(loginName.replaceAll("[\"\'<>/\\\\]", ""), 10); // protect from js injection attacks
                        if (spaceships.getControlledAmmount().get() / spaceships.players > 0.9)
                            msg.getFrom().send(new HttpResponse(self(), ok(msg, noMoreSpaceshipsHtml())));
                        else
                            msg.getFrom().send(new HttpResponse(self(), ok(msg, gameHtml())));
                    }
                } else if (message instanceof WebSocketOpened) {
                    openMetric.mark();

                    watch(((WebSocketOpened)message).getFrom()); // watch client
                } else if (message instanceof WebDataMessage) {
                    rcvMetric.mark();
                    WebDataMessage msg = (WebDataMessage) message;
                    if (spaceship == null) { // 
                        spaceship = spaceships.spawnControlledSpaceship(client, "c." + loginName);
                        if (spaceship == null)
                            break;

                        watch(spaceship);
                    }
                    if (msg.getFrom() == client)
                        spaceship.send(msg);
                } else if (message instanceof ExitMessage) {
                    ActorRef actor = ((ExitMessage) message).getActor();
                    if (actor == spaceship) { // the spaceship is dead
                        spaceships.notifyControlledSpaceshipDied();
                        spaceship = null;
                    } else { // the client is dead
                        if (spaceship != null)
                            spaceship.send(new WebDataMessage(self(), "exit"));
                    }
                    break;
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
