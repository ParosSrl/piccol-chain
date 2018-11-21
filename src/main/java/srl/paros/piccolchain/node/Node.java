package srl.paros.piccolchain.node;

import com.google.gson.reflect.TypeToken;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import srl.paros.piccolchain.Json;
import srl.paros.piccolchain.node.consumer.BroadcastTransactions;
import srl.paros.piccolchain.node.domain.*;
import srl.paros.piccolchain.node.api.*;
import srl.paros.piccolchain.node.p2p.PeerConnection;
import srl.paros.piccolchain.node.task.Initialize;
import srl.paros.piccolchain.node.task.UpdatePeerList;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static srl.paros.piccolchain.Hostname.HOSTNAME;
import static srl.paros.piccolchain.Json.toJson;
import static srl.paros.piccolchain.node.domain.Transactions.transactions;

public class Node extends AbstractVerticle {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final Type SET_STRING = new TypeToken<Set<String>>() {}.getType();

    private final Transactions transactions = transactions();
    private final Blockchain blockchain = Blockchain.blockchain();
    private final String name;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private Set<String> peers;

    public Node() {
        this.name = HOSTNAME.get();
    }

    @Override
    public void start() {
        final var router = Router.router(vertx);

        router.get("/").handler(new Index(name, transactions, blockchain));
        router.get("/mine").handler(new Mine(name, blockchain, transactions));
        router.get("/blocks").handler(new GetBlocks(blockchain));

        router.post("/transaction")
                .consumes("application/json")
                .handler(new CreateTransactionJson(transactions));

        router.post("/transactions")
                .consumes("application/x-www-form-urlencoded")
                .handler(BodyHandler.create())
                .handler(new CreateTransactionForm(transactions));

        router.exceptionHandler(error -> log.error("Error", error));

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(4567);

        vertx.createNetServer()
                .connectHandler(new PeerConnection(blockchain, transactions))
                .listen(4568);

        vertx.eventBus().consumer("transaction", new BroadcastTransactions(peers, vertx.createNetClient()));

        var httpClient = vertx.createHttpClient();
        vertx.setPeriodic(5000, new Initialize(name, connected, httpClient));
        vertx.setPeriodic(2000, new UpdatePeerList(name, connected, peers, httpClient));

        vertx.exceptionHandler(error -> log.error("Error", error));
    }

}
