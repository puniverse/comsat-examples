var source;
function SSEconnect() {
    if (typeof source == 'undefined' || source === null) {
        var mypath = window.location.pathname.substring(0, window.location.pathname.lastIndexOf("/"));
        var wsUrl = "http://" + window.location.host + mypath + "/sse";
        if (typeof(EventSource) !== "undefined") {
            source = new EventSource(wsUrl);
            document.getElementById("SSEoutput").innerHTML = "Connecting to " + wsUrl + "...";
            source.onmessage = SSEonMessage;
            source.onopen = SSEonOpen;
            source.onclose = SSEonClose;
        } else
            alert("SSE not supported on your browser.");
    } else {
        source.close();
        source = null;
        SSEonClose();
    }
}
function SSEprintAndScroll(id, text) {
    document.getElementById(id).innerHTML += text;
    document.getElementById(id).scrollTop = document.getElementById(id).scrollHeight;
}
function SSEonOpen(evt) {
    SSEprintAndScroll("SSEoutput", "\nConnected");
    document.getElementById("SSEconnButton").innerHTML = "disconnect";
}
function SSEonClose(evt) {
    SSEprintAndScroll("SSEoutput", "\nDisconnected\n");
    document.getElementById("SSEconnButton").innerHTML = "connect";
}
function SSEonMessage(evt) {
    SSEprintAndScroll("SSEoutput", "\nResponse: " + evt.data);
}
//function onError(evt) {
//    printAndScroll("output", "\nError: " + evt.data);
//}
//function sendMessage() {
//    printAndScroll("output", "\nMessage sent");
//    websocket.send(document.getElementById("txtMessage").value);
//}
