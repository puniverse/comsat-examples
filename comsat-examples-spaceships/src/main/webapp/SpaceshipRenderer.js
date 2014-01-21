//var output;
var websocket;
function init() {
    //output = document.getElementById("output");
} // end init
function connect() {
//open socket
    var mypath = window.location.pathname.substring(0, window.location.pathname.lastIndexOf("/"));
    var wsUrl = "ws://" + window.location.host + mypath + "/game";
    if ("WebSocket" in window) {
        websocket = new WebSocket(wsUrl);
        websocket.onopen = onOpen;
        websocket.onclose = onClose;
        websocket.onmessage = onMessage;
        websocket.onerror = onError;
        statusData.msg = "Connecting...";
        updateStatus();
    } else {
        alert("WebSockets not supported on your browser.");
    } // end if
} // end connect
var pingInterval;
function onOpen(evt) {
    statusData.msg = "Connected";
    updateStatus();
    websocket.send("subscribe");
    pingInterval = setInterval(function() {
        websocket.send("ping " + Date.now());
    }, 1000);
} // end onOpen
function onClose(evt) {
    clearInterval(pingInterval);
    statusData.msg = "Connection is closed";
    updateStatus();
} // end onClose;
function onError(evt) {
    clearInterval(pingInterval);
    statusData.msg = "Websocket error";
    updateStatus();
} // end onClose;
function handleShip(ship, ans, sertverTime, clientTime) {
    var d = 200.0;
    var label;
    ship.x = ans.x / d;
    ship.y = ans.y / d;
    ship.t = clientTime;
    ship.h = Math.atan2(ans.vy, ans.vx) + Math.PI / 2;
    ship.vx = ans.vx / d;
    ship.vy = ans.vy / d;
    ship.exVx = ans.exVx / d;
    ship.exVy = ans.exVy / d;
    ship.exVelocityUpdated = clientTime - (sertverTime - ans.exVelocityUpdated);
    ship.ax = ans.vx / d;
    ship.ay = ans.vy / d;
    if (ans.name.indexOf("c.") == 0) {
        if (ans.name in textTexturesCache) {
            label = textTexturesCache[ans.name];
        } else {
            label = makeTextTexture(ans.name.substring(2), {fontsize: 24, fontface: "Georgia"});
            textTexturesCache[ans.name] = label;
        }
        ship.text.material.map = label;
        ship.text.material.visible = true;
    } else {
        ship.text.material.visible = false;
    }

// drow explostion
    if (ans.blowTime > 0) {
        ship.mesh.material.map = expTexture;
        ship.mesh.material.opacity = Math.max(0, 1 - (sertverTime - ans.blowTime) / 1000);
        return;
    }
    ship.mesh.material.opacity = 1;
    ship.mesh.material.map = shipTexture;
    if (sertverTime - ans.timeFired < 150) {
        ship.shoot.position.x = ship.x; //+ (0.5) * Math.sin(star.h);
        ship.shoot.position.y = ship.y; //- (0.5) * Math.cos(star.h);
        ship.shoot.geometry.vertices[1].x = ans.shotLength / d;
        ship.shoot.geometry.verticesNeedUpdate = true;
        ship.shoot.rotation.z = ship.h - Math.PI / 2;
        ship.shoot.material.visible = true;
    } else {
        ship.shoot.material.visible = false;
    }
}
var text2;
function hh() {
    text2 = document.createElement('div');
    text2.style.position = 'absolute';
//text2.style.zIndex = 1;
    text2.style.width = 200;
    text2.style.height = 120;
    text2.style.backgroundColor = "#696969";
    text2.style.color = "white";
    text2.style.opacity = 0.5;
    text2.innerHTML = "hi there!";
    text2.style.top = 10 + 'px';
    text2.style.left = 10 + 'px';
    document.body.appendChild(text2);
}
var statusData= new Object();
statusData.latancy = "unknown";
statusData.controlled = 0;
statusData.total = 0;
statusData.life = 0;
statusData.velocity = 0;
statusData.msg = "";

function updateStatus() {
            text2.innerHTML =
                "Status: " + statusData.msg + "<br>"
                +"Latency: " + statusData.latancy + " ms<br>"
                + "Players: " + statusData.controlled + "<br>"
                + "Spceships: " + statusData.total + "<br>"
                + "Velocity: " + statusData.velocity + "<br>"
                + "Lives: " + statusData.life;
}

function onMessage(evt) {
    if (evt.data === "killed") {
        alert("game over");
        statusData.msg = "Starting a new game";
        updateStatus();
        websocket.send("subscribe");
        return;
    }
    if (evt.data.indexOf("pong") == 0) {
        var arr = evt.data.split(",");
        statusData.latancy = (Date.now() - arr[1]);
        statusData.controlled = arr[3];
        statusData.total = (parseInt(arr[3]) + parseInt(arr[2]));
        updateStatus();
        return;
    }

    var ans = eval("(" + evt.data + ")");
    var serverTime = ans.now;
    var d = 200.0;
    var clientTime = Date.now();
    statusData.msg = "Playing";
    statusData.life = ans.life;
    statusData.velocity = Math.round(Math.sqrt(ans.self.vx*ans.self.vx + ans.self.vy*ans.self.vy));
    updateStatus();
    handleShip(ship[0], ans.self, serverTime, clientTime);
    for (i = 0; i < num - 1; i++) {
        if (i < Math.min(ans.others.length)) {
            ship[i + 1].mesh.material.visible = true;
            handleShip(ship[i + 1], ans.others[i], serverTime, clientTime);
        } else {
            ship[i + 1].mesh.material.visible = false;
            ship[i + 1].shoot.material.visible = false;
            ship[i + 1].text.material.visible = false;
        }
    }
} // end onMessage

var scene;
var camera;
var shipTexture;
var cubeMesh;
var num = 20;
var ship = new Array(num);
var spin = 0;
var textTexturesCache = {};
initializeScene();
animateScene();
connect();
function initializeScene() {
    hh();
    webGLAvailable = false;
    if (Detector.webgl) {
        renderer = new THREE.WebGLRenderer({antialias: true});
        webGLAvailable = true;
    } else {
        renderer = new THREE.CanvasRenderer();
    }

    renderer.setClearColorHex(0x000000, 1);
    canvasWidth = window.innerWidth - 10;
    canvasHeight = window.innerHeight - 20;
    renderer.setSize(canvasWidth, canvasHeight);
    document.getElementById("WebGLCanvas").appendChild(renderer.domElement);
    scene = new THREE.Scene();
    camera = new THREE.PerspectiveCamera(45, canvasWidth / canvasHeight, 1, 100);
    camera.position.set(0, 0, 6);
    camera.lookAt(scene.position);
    scene.add(camera);

    shipTexture = new THREE.ImageUtils.loadTexture("spaceship.png");
    expTexture = new THREE.ImageUtils.loadTexture("explosion.png");
    shootTexture = new THREE.ImageUtils.loadTexture("shoot.png");
    defaultTextTexture = makeTextTexture("default", {fontsize: 16, fontface: "Georgia"});
    // create stars
    for (var zpos = -2; zpos < 5; zpos += 0.001) {
        var geom = new THREE.CircleGeometry(0.01);
        var mat = new THREE.MeshBasicMaterial({
            color: 0xFAFA66
        });
        var mesh = new THREE.Mesh(geom, mat);
        mesh.position.set((Math.random() - 0.5) * 15000.0 / 200.0, (Math.random() - 0.5) * 15000.0 * 0.7 / 200.0, zpos);
        scene.add(mesh);
    }
    for (i = 0; i < num; i++) {
        var squareGeometry = new THREE.Geometry();
        var s = 0.15;
        squareGeometry.vertices.push(new THREE.Vector3(-s, -s, 0.0));
        squareGeometry.vertices.push(new THREE.Vector3(s, -s, 0.0));
        squareGeometry.vertices.push(new THREE.Vector3(s, s, 0.0));
        squareGeometry.vertices.push(new THREE.Vector3(-s, s, 0.0));
        squareGeometry.faces.push(new THREE.Face4(0, 1, 2, 3));
        squareGeometry.faceVertexUvs[0].push([
            new THREE.UV(0.0, 0.0),
            new THREE.UV(1.0, 0.0),
            new THREE.UV(1.0, 1.0),
            new THREE.UV(0.0, 1.0)
        ]);
        var squareMaterial = new THREE.MeshBasicMaterial({
            map: shipTexture
        });
        var squareMesh = new THREE.Mesh(squareGeometry, squareMaterial);
        squareMesh.position.set(0.0, 0.0, 0.0);
        scene.add(squareMesh);

        var textMesh = createSquareMesh(150, 80, expTexture);
        textMesh.position.set(i, i, 0.0);
        scene.add(textMesh);
        textMesh.material.map = defaultTextTexture;
        ship[i] = new Object();
        ship[i].angle = 0.0;
        ship[i].dist = (i / num) * 1.0;
        ship[i].r = 255; 
        ship[i].x = 0;
        ship[i].y = 0;
        ship[i].g = ship[i].r;
        ship[i].b = ship[i].r;
        ship[i].mesh = squareMesh;
        ship[i].text = textMesh;
        //shoot ray
        var material = new THREE.LineBasicMaterial({
            color: 0xFFFFFF,
            linewidth: 3
        });
        var geometry = new THREE.Geometry();
        geometry.vertices.push(new THREE.Vector3(0, 0, 0));
        geometry.vertices.push(new THREE.Vector3(0.9, 0, 0));
        var sm = new THREE.Line(geometry, material);
        scene.add(sm);
        sm.position.set(0, 0, 0);
        ship[i].shoot = sm;
    }
    ship[0].mesh.scale.x = ship[0].mesh.scale.y = 1.5;
    var pointLight = new THREE.PointLight(0xFFFF00);
    pointLight.position.x = 4;
    pointLight.position.y = 4;
    pointLight.position.z = 4;
    scene.add(pointLight);

    document.addEventListener("keydown", onDocumentKeyDown, false);
}

function onDocumentKeyDown(event) {
    var keyCode = event.which;
    if (keyCode === 32) {
// space
        websocket.send("fire");
    } else if (keyCode === 38) {
// Cursor up
        websocket.send("up");
    } else if (keyCode === 40) {
// Cursor down
        websocket.send("down");
    } else if (keyCode === 37) {
// Cursor left
        websocket.send("left");
    } else if (keyCode === 39) {
// Cursor right
        websocket.send("right");
    }
}

/**
 * Animate the scene and call rendering.
 */
function animateScene() {
    requestAnimationFrame(animateScene);
    var ct = Date.now();
    var x, y;
    var vx, vy, exVx = 0, exVy = 0;
    var dt, dt2, dext;
    for (i = 0; i < num; i++) {
        dt = Math.min(1, (ct - ship[i].t) / 1000.0);
        dt2 = Math.min(1, dt * dt * 0.5);
        dext = Math.min(2, (ct - ship[i].exVelocityUpdated) / 1000.0);
        if (dext < 2 && dext > 0) {
            exVx = ship[i].exVx / (1 + 8 * dext);
            exVy = ship[i].exVy / (1 + 8 * dext);
        }
        x = ship[i].x + (exVx + ship[i].vx) * dt + ship[i].ax * dt2;
        y = ship[i].y + (exVy + ship[i].vy) * dt + ship[i].ay * dt2;
        vx = ship[i].vx + ship[i].ax * dt;
        vy = ship[i].vy + ship[i].ay * dt;


        if (i == 0)
            camera.position.set(x, y, 6);

        ship[i].mesh.position.set(x, y, 0.0);
        ship[i].text.position.set(x - 0.75 / 200.0, y - 0.75 / 200.0, 0.0);
        ship[i].mesh.rotation.z = Math.atan2(vy, vx) + Math.PI / 2;//ship[i].h;
    }
    renderScene();
}

function renderScene() {
    renderer.render(scene, camera);
}

function makeTextTexture(message, parameters) {
    if (message.length < 11)
        message = message + Array(11 - message.length).join(' ');
    if (parameters === undefined)
        parameters = {};
    var fontface = parameters.hasOwnProperty("fontface") ?
            parameters["fontface"] : "Arial";
    var fontsize = parameters.hasOwnProperty("fontsize") ?
            parameters["fontsize"] : 18;
    var borderThickness = parameters.hasOwnProperty("borderThickness") ?
            parameters["borderThickness"] : 4;
    var borderColor = parameters.hasOwnProperty("borderColor") ?
            parameters["borderColor"] : {r: 0, g: 0, b: 0, a: 0.0};
    var backgroundColor = parameters.hasOwnProperty("backgroundColor") ?
            parameters["backgroundColor"] : {r: 0, g: 0, b: 0, a: 0.0};
    var canvas = document.createElement('canvas');
    var context = canvas.getContext('2d');
    context.font = /*"Bold " +*/ fontsize + "px " + fontface;
    // get size data (height depends only on font size)
//    var metrics = context.measureText(message);
//    var textWidth = metrics.width;
    // background color
    context.fillStyle = "rgba(" + backgroundColor.r + "," + backgroundColor.g + ","
            + backgroundColor.b + "," + backgroundColor.a + ")";
    // border color
    context.strokeStyle = "rgba(" + borderColor.r + "," + borderColor.g + ","
            + borderColor.b + "," + borderColor.a + ")";
    context.lineWidth = borderThickness;
    // 1.4 is extra height factor for text below baseline: g,j,p,q.

    // text color
    context.fillStyle = "rgba(255, 255, 255, 1.0)";
    context.fillText(message, borderThickness, fontsize + borderThickness);
    // canvas contents will be used for a texture
    var texture = new THREE.Texture(canvas);
    texture.needsUpdate = true;
    return texture;
}

function createSquareMesh(w, h, texture) {
    var halfH = h / 300.0;
    var halfW = w / 300.0;
    var squareGeometry = new THREE.Geometry();
    squareGeometry.vertices.push(new THREE.Vector3(-halfW, -halfH, 0.0));
    squareGeometry.vertices.push(new THREE.Vector3(halfW, -halfH, 0.0));
    squareGeometry.vertices.push(new THREE.Vector3(halfW, halfH, 0.0));
    squareGeometry.vertices.push(new THREE.Vector3(-halfW, halfH, 0.0));
    squareGeometry.faces.push(new THREE.Face4(0, 1, 2, 3));
    squareGeometry.faceVertexUvs[0].push([
        new THREE.UV(0.0, 0.0),
        new THREE.UV(1.0, 0.0),
        new THREE.UV(1.0, 1.0),
        new THREE.UV(0.0, 1.0)
    ]);
    var squareMaterial = new THREE.MeshBasicMaterial({
        map: texture
    });
    return new THREE.Mesh(squareGeometry, squareMaterial);
}