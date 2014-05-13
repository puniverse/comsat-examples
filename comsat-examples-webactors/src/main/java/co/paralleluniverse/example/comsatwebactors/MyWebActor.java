package co.paralleluniverse.example.comsatwebactors;

import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.ExitMessage;
import co.paralleluniverse.actors.LifecycleMessage;
import static co.paralleluniverse.comsat.webactors.Cookie.*;
import co.paralleluniverse.comsat.webactors.HttpRequest;
import static co.paralleluniverse.comsat.webactors.HttpResponse.*;
import co.paralleluniverse.comsat.webactors.HttpStreamOpened;
import co.paralleluniverse.comsat.webactors.SSE;
import co.paralleluniverse.comsat.webactors.WebActor;
import co.paralleluniverse.comsat.webactors.WebDataMessage;
import co.paralleluniverse.comsat.webactors.WebStreamOpened;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.ws.rs.client.AsyncClientBuilder;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.SendPort;
import com.google.common.base.Function;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import javax.ws.rs.client.Client;

@WebActor(httpUrlPatterns = {"/webactor", "/send1", "/send2", "/sse"}, webSocketUrlPatterns = {"/ws"})
public class MyWebActor extends BasicActor<Object, Void> {
    static final Client httpClient = AsyncClientBuilder.newClient();

    private boolean initialized;
    private final List<SendPort<WebDataMessage>> listeners = new ArrayList<>();
    private int i;
    private DataSource ds;

    @Override
    protected Void doRun() throws InterruptedException, SuspendExecution {
        if (!initialized) { // necessary for hot code swapping, as this method might be called again after swap
            this.i = 1;
            this.ds = BlockingCallsExample.lookupDataSourceJDBC();
            this.initialized = true;
        }

        for (;;) {
            Object message = receive(5000, TimeUnit.MILLISECONDS);
            if (message instanceof HttpRequest) {
                HttpRequest msg = (HttpRequest) message;
                // -------- plain HTTP request -------- 
                if (!msg.getRequestURI().endsWith("/sse")) {
                    String blockingCallsOuput = BlockingCallsExample.executeSomeSql(ds)
                            + BlockingCallsExample.callSomeRS(httpClient)
                            + BlockingCallsExample.doSleep();
                    msg.getFrom().send(ok(self(), msg, buildHtml(blockingCallsOuput, msg, i))
                            .setContentType("text/html")
                            .addCookie(cookie("userCookie", "value").build()).build());
                } // -------- request for SSE -------- 
                else {
                    msg.getFrom().send(SSE.startSSE(self(), msg).build());

                    // We could use the selective receive in the next line to handle the response 
                    // (instead we let it be handled by the next if block)
//                        HttpStreamOpened streamOpened = receive(MessageSelector.select().withId(response).ofType(HttpStreamOpened.class));
//                        ActorRef<WebDataMessage> sseActor = streamOpened.getFrom();
//                        watch(sseActor);
//                        sseActor.send(new WebDataMessage(self(), SSE.event("Welcome. " + listeners.size() + " listeners")));
//                        listeners.add(wrapAsSSE(sseActor));
                }
            } // -------- WebSocket/SSE opened -------- 
            else if (message instanceof WebStreamOpened) {
                WebStreamOpened msg = (WebStreamOpened) message;
                watch(msg.getFrom()); // will call handleLifecycleMessage with ExitMessage when the session ends

                SendPort<WebDataMessage> p = msg.getFrom();
                if (msg instanceof HttpStreamOpened)
                    p = wrapAsSSE(p);
                listeners.add(p);
                p.send(new WebDataMessage(self(), "Welcome. " + listeners.size() + " listeners"));
            } // -------- WebSocket message received -------- 
            else if (message instanceof WebDataMessage) {
                WebDataMessage msg = (WebDataMessage) message;
                if (!msg.isBinary()) {
                    for (SendPort listener : listeners)
                        listener.send(new WebDataMessage(self(), "local counter:" + i + " data:" + msg.getStringBody().toUpperCase()));
                }
            } // -------- Timeout -------- 
            else if (message == null) {
                for (SendPort listener : listeners)
                    listener.send(new WebDataMessage(self(), "local counter:" + i + " no data. "));
            }
            i++;

            checkCodeSwap();
        }
    }

    private SendPort<WebDataMessage> wrapAsSSE(SendPort<WebDataMessage> actor) {
        return Channels.mapSend(actor, new Function<WebDataMessage, WebDataMessage>() {
            @Override
            public WebDataMessage apply(WebDataMessage f) {
                return new WebDataMessage(f.getFrom(), SSE.event(f.getStringBody()));
            }
        });
    }

    @Override
    protected Object handleLifecycleMessage(LifecycleMessage m) {
        if (m instanceof ExitMessage) {
            // while listeners might contain an SSE actor wrapped with Channels.map, the wrapped SendPort maintains the original actors hashCode and equals behavior
            ExitMessage em = (ExitMessage) m;
            System.out.println("Actor " + em.getActor() + " has died.");
            boolean res = listeners.remove(em.getActor());
            System.out.println((res ? "Successfuly" : "Unsuccessfuly") + " removed listener for actor " + em.getActor());
        }
        return super.handleLifecycleMessage(m);
    }

    private static String buildHtml(String blockingCallsOutput, HttpRequest msg, int i) {
        return "<h1>ParllelUniverse Webactor example</h1>\n"
                + "<h3>Blocking calls results from the webactor (JDBC, JAX-RS, Sleep):</h3>\n"
                + "<textarea cols=80 rows=10>" + blockingCallsOutput + "</textarea>\n"
                + "<table><tbody>\n"
                + " <tr><td>local counter</td><td><input size=100 value=" + i + " /></td></tr>\n"
                + " <tr><td>uri:</td><td><input size=100 value=" + msg.getRequestURI() + " /></td></tr>\n"
                + " <tr><td>cookies:</td><td><textarea cols=70 rows=3>" + msg.getCookies().toString() + "</textarea></td></tr>\n"
                + "</tbody></table>\n"
                + "<p>You can try access this webactor via <a href=send1>send1</a> or <a href=send2>send2</a> or through websocket in the next section.\n"
                + "<h3>WebSocket push</h3>\n"
                + " <script type=\"text/javascript\" src=\"myWebSocket.js\" ></script>\n"
                + " <form>\n"
                + "   <button id=\"connButton\" type=\"button\" onclick=\"connect()\">connect</button>\n"
                + "   <input type=text id=txtMessage value=\"message text\" />\n"
                + "   <button type=\"button\" onclick=\"sendMessage()\"> send message</button>\n"
                + "   <p><textarea id=\"output\" cols=60 rows=10>Click 'connect' button to connect</textarea></p>\n"
                + " </form>\n"
                + "<h3>SSE push</h3>\n"
                + " <script type=\"text/javascript\" src=\"mySSE.js\" ></script>\n"
                + " <form>\n"
                + "   <button id=\"SSEconnButton\" type=\"button\" onclick=\"SSEconnect()\">connect</button>\n"
                //                + "   <input type=text id=txtMessage value=\"message text\" />\n"
                //                + "   <button type=\"button\" onclick=\"sendMessage()\"> send message</button>\n"
                + "   <p><textarea id=\"SSEoutput\" cols=60 rows=10>Click 'connect' button to connect</textarea></p>\n"
                + " </form>\n";
    }
}
