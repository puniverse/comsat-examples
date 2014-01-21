package co.paralleluniverse.webspaceships;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.ExitMessage;
import co.paralleluniverse.actors.LifecycleMessage;
import co.paralleluniverse.comsat.webactors.HttpRequest;
import co.paralleluniverse.comsat.webactors.HttpResponse;
import static co.paralleluniverse.comsat.webactors.HttpResponse.ok;
import co.paralleluniverse.comsat.webactors.WebActor;
import co.paralleluniverse.comsat.webactors.WebDataMessage;
import co.paralleluniverse.comsat.webactors.WebSocketOpened;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.spaceships.Spaceships;
import static co.paralleluniverse.spaceships.Spaceships.spaceships;

@WebActor(httpUrlPatterns = "/login", webSocketUrlPatterns = "/game")
public class SpaceshipConnectorActor extends BasicActor<Object, Void> {
    private ActorRef<Object> spaceship = null;

    @Override
    protected Void doRun() throws InterruptedException, SuspendExecution {
        ActorRef<WebDataMessage> client = null;
        String loginName = "empty";
        try {
            for (;;) {
                Object message = receive();
                if (message instanceof HttpRequest) {
                    HttpRequest msg = (HttpRequest) message;
                    loginName = msg.getParameter("name");
                    if (loginName == null)
                        msg.getFrom().send(new HttpResponse(self(), ok(msg, nameFormHtml())));
                    else {
                        if (spaceships.getControlledAmmount().get() / Spaceships.MAX_PLAYERS > 0.9) {
                            msg.getFrom().send(new HttpResponse(self(), ok(msg, noMoreSpaceshipsHtml())));
                        } else {
                            msg.getFrom().send(new HttpResponse(self(), ok(msg, gameHtml())));
                        }
                    }
                } else if (message instanceof WebSocketOpened) {
                    WebSocketOpened msg = (WebSocketOpened) message;
                    client = msg.getFrom();
                    watch(client);
                } else if (message instanceof WebDataMessage) {
                    WebDataMessage msg = (WebDataMessage) message;
                    if (spaceship == null) { // 
                        spaceship = spaceships.spawnControlledSpaceship(client, "c." + loginName);
                        if (spaceship == null) {
                            return null;
                        }
                        watch(spaceship);
                    }
                    if (msg.getFrom() == client)
                        spaceship.send(msg);
                } else if (message instanceof ExitMessage) {
                    ActorRef actor = ((ExitMessage) message).getActor();
                    if (actor == spaceship) {
                        spaceships.notifyControlledSpaceshipDied();
                        spaceship = null;
                    } else {
                        if (spaceship==null)
                            spaceship.send(new WebDataMessage(self(), "exit"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();           
//            throw new RuntimeException();
        }
        return null;
    }

    @Override
    protected Object handleLifecycleMessage(LifecycleMessage m) {
        if (m instanceof ExitMessage)
            return m;
        return super.handleLifecycleMessage(m);
    }

    String getResource(String resourceName) {
        try {
            return com.google.common.io.Resources.toString(com.google.common.io.Resources.getResource(resourceName),
                    com.google.common.base.Charsets.UTF_8);
        } catch (java.io.IOException ex) {
            return "error " + ex;
        }
    }

    String nameFormHtml() {
        return "<h1>Parallel Universe Spaceships webactors demo</h1>\n"
                + "<h3>Please enter your name:</h3>"
                + "<form action=\"login\" method=\"post\">\n"
                + "    <p>Name : <input type=\"text\" name=\"name\" value=\"myName\" /></p>\n"
                + "    <input type=\"submit\" value=\"Start Playing\" />\n"
                + "</form>\n";
    }

    String noMoreSpaceshipsHtml() {
        return "<h1>Sorry. No free spaceship. Please try again later</h1>\n";
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

    void verifySpaceshipNotDead(ActorRef<Object> spaceship) throws InterruptedException, SuspendExecution {
    }
}
