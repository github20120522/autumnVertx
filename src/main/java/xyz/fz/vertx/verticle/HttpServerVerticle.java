package xyz.fz.vertx.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.fz.vertx.model.Result;
import xyz.fz.vertx.util.BaseProperties;
import xyz.fz.vertx.util.BaseUtil;
import xyz.fz.vertx.util.EventBusUtil;

import static xyz.fz.vertx.verticle.AbcVerticle.ABC_ADDRESS;
import static xyz.fz.vertx.verticle.AbcVerticle.ABC_ADDRESS_JSON;
import static xyz.fz.vertx.verticle.MongoVerticle.MONGO_ADDRESS_FIND;
import static xyz.fz.vertx.verticle.MongoVerticle.MONGO_ADDRESS_SAVE;
import static xyz.fz.vertx.verticle.RxSocketJsVerticle.SOCKET_MESSAGE_ADDRESS;

public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(HttpServerVerticle.class);

    @Override
    public void start() {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // We need a cookie handler first
        router.route().handler(CookieHandler.create());

        // Create a clustered session store using defaults
        SessionStore store = ClusteredSessionStore.create(vertx);

        SessionHandler sessionHandler = SessionHandler.create(store);

        // Make sure all requests are routed through the session handler too
        router.route().handler(sessionHandler);

        router.route("/pubs/*").handler(StaticHandler.create());

        router.route().failureHandler(routingContext -> {
            Throwable failure = routingContext.failure();
            logger.error(BaseUtil.getExceptionStackTrace(failure));
            routingContext.response().end(Result.ofMessage(failure.getMessage()));
        });

        // filter
        router.route("/*").handler(routingContext -> {
            logger.debug("/* filter");
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "application/json");
            routingContext.next();
        });

        router.route("/abc/*").handler(routingContext -> {
            logger.debug("/abc/* filter");
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "text/html");
            routingContext.next();
        });

        router.route("/abc").handler(routingContext -> {
            String name = routingContext.request().getParam("name");
            HttpServerResponse response = routingContext.response();
            EventBusUtil.eventBusSend(vertx, ABC_ADDRESS, name, response);
        });

        router.route("/abc/json").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            routingContext.request().bodyHandler(event -> {
                EventBusUtil.eventBusSend(vertx, ABC_ADDRESS_JSON, event, response);
            });
        });

        router.route("/mongo/save").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            routingContext.request().bodyHandler(event -> {
                EventBusUtil.eventBusSend(vertx, MONGO_ADDRESS_SAVE, event, response);
            });
        });

        router.route("/mongo/find").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            EventBusUtil.eventBusSend(vertx, MONGO_ADDRESS_FIND, "", response);
        });

        router.route("/pushSocketMessage").handler(routingContext -> {
            routingContext.request().bodyHandler(buffer -> {
                String requestJson = buffer.toString();
                try {
                    JsonObject requestMap = new JsonObject(requestJson);
                    vertx.eventBus().publish(SOCKET_MESSAGE_ADDRESS, requestMap.getString("message"));
                } catch (Exception e) {
                    routingContext.response().end(Result.ofMessage(null));
                    return;
                }
                routingContext.response().end(Result.ofSuccess());
            });
        });

        String serverPort = BaseProperties.get("server.port");

        httpServer.requestHandler(router::accept).listen(Integer.parseInt(serverPort));

        logger.info("vertx httpServer started at port:{}", serverPort);
    }

}
