package io.micronaut.configuration.graphql;

import graphql.ExecutionResult;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.websocket.WebSocketSession;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

import java.util.Collection;
import java.util.function.Function;

import static io.micronaut.configuration.graphql.GraphQLWsResponse.ServerType.*;

/**
 * Sends the GraphQL response(s) to the client.
 *
 * @author Gerard Klijs
 * @since 1.3
 */
@Singleton
public class GraphQLWsSender {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLWsSender.class);

    private final GraphQLWsState state;
    private final GraphQLJsonSerializer graphQLJsonSerializer;

    /**
     * Default constructor.
     *
     * @param state                 the {@link GraphQLWsState} instance
     * @param graphQLJsonSerializer the {@link GraphQLJsonSerializer} instance
     */
    public GraphQLWsSender(GraphQLWsState state,
            GraphQLJsonSerializer graphQLJsonSerializer) {
        this.state = state;
        this.graphQLJsonSerializer = graphQLJsonSerializer;
    }

    /**
     * Transform the result from the a websocket request to a message that can be send to the client.
     *
     * @param operationId  Sting value of the operation id
     * @param responseBody GraphQLResponseBody of the executed operation
     * @param session      the websocket session by which the operation was executed
     * @return GraphQLWsOperationMessage
     */
    @SuppressWarnings("unchecked")
    Flowable<GraphQLWsResponse> send(String operationId, GraphQLResponseBody responseBody,
            WebSocketSession session) {
        Object dataObject = responseBody.getSpecification().get("data");
        if (dataObject instanceof Publisher) {
            return startSubscription(operationId, (Publisher<ExecutionResult>) dataObject, session);
        } else {
            return Flowable.just(
                    toGraphQLWsResponse(operationId, responseBody),
                    new GraphQLWsResponse(GQL_COMPLETE, operationId));
        }
    }

    private GraphQLWsResponse toGraphQLWsResponse(String operationId, GraphQLResponseBody responseBody) {
        if (hasErrors(responseBody)) {
            return new GraphQLWsResponse(GQL_ERROR, operationId, responseBody);
        } else {
            return new GraphQLWsResponse(GQL_DATA, operationId, responseBody);
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean hasErrors(GraphQLResponseBody responseBody) {
        Object errorObject = responseBody.getSpecification().get("errors");
        if (errorObject instanceof Collection) {
            return !((Collection) errorObject).isEmpty();
        } else {
            return false;
        }
    }

    private Function<String, Subscription> starter(Publisher<ExecutionResult> publisher, WebSocketSession session) {
        return operationId -> {
            SendSubscriber subscriber = new SendSubscriber(operationId, session);
            publisher.subscribe(subscriber);
            return subscriber.getSubscription();
        };
    }

    private Flowable<GraphQLWsResponse> startSubscription(String operationId, Publisher<ExecutionResult> publisher,
            WebSocketSession session) {
        state.saveOperation(operationId, session, starter(publisher, session));
        return Flowable.empty();
    }

    /**
     * Subscriber to handle the messages, might be cancelled when the client calls stop or when the connection is
     * broken.
     */
    private final class SendSubscriber extends CompletionAwareSubscriber<ExecutionResult> {

        private final String operationId;
        private final WebSocketSession session;

        private SendSubscriber(String operationId, WebSocketSession session) {
            this.operationId = operationId;
            this.session = session;
        }

        Subscription getSubscription() {
            return subscription;
        }

        @Override
        protected void doOnSubscribe(Subscription subscription) {
            LOG.info("Subscribed to results for to operation {} in session {}", operationId, session.getId());
            subscription.request(1L);
        }

        @Override
        protected void doOnNext(ExecutionResult message) {
            convertAndSend(message);
            subscription.request(1L);
        }

        @Override
        protected void doOnError(Throwable t) {
            LOG.warn("Error in SendSubscriber", t);
            send(new GraphQLWsResponse(GQL_ERROR, operationId));
        }

        @Override
        protected void doOnComplete() {
            LOG.info("Completed results for operation {} in session {}", operationId, session.getId());
            send(new GraphQLWsResponse(GQL_COMPLETE, operationId));
            state.removeCompleted(operationId, session);
        }

        private void convertAndSend(ExecutionResult executionResult) {
            GraphQLWsResponse response = toGraphQLWsResponse(
                    operationId, new GraphQLResponseBody(executionResult.toSpecification()));
            send(response);
        }

        private void send(GraphQLWsResponse response) {
            if (session.isOpen()) {
                session.sendSync(graphQLJsonSerializer.serialize(response));
            }
        }
    }
}
