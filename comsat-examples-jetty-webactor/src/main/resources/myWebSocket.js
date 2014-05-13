var websocket;
function connect() {
    if (typeof websocket == 'undefined' || websocket === null) {
        var mypath = window.location.pathname.substring(0, window.location.pathname.lastIndexOf("/"));
        var wsUrl = "ws://" + window.location.host + mypath + "/ws";
        if ("WebSocket" in window) {
            websocket = new WebSocket(wsUrl);
            document.getElementById("output").innerHTML = "Connecting to " + wsUrl + "...";
            websocket.onopen = onOpen;
            websocket.onclose = onClose;
            websocket.onmessage = onMessage;
            websocket.onerror = onError;
        } else {
            alert("WebSockets not supported on your browser.");
        }
    } else {
        websocket.close();
        websocket = null;
    }
}
function printAndScroll(id, text) {
    document.getElementById(id).innerHTML += text;
    document.getElementById(id).scrollTop = document.getElementById(id).scrollHeight;
}
function onOpen(evt) {
    printAndScroll("output","\nConnected");
    document.getElementById("connButton").innerHTML = "disconnect";
}
function onClose(evt) {
    printAndScroll("output","\nDisconnected\n");
    document.getElementById("connButton").innerHTML = "connect";
}
var count = 0;
function onMessage(evt) {
//    if (evt.data.indexOf("no data")== -1)
        printAndScroll("output","\nResponse: " + evt.data);
//    else {
//        if ((++count % 500) ==0)
//        printAndScroll("output","\nResponse500: " + evt.data.substring(20)+ "...");    
//    }
}
function onError(evt) {
    printAndScroll("output","\nError: " + evt.data);
}
function sendMessage() {
    printAndScroll("output","\nMessage sent");
    websocket.send(document.getElementById("txtMessage").value);
}
