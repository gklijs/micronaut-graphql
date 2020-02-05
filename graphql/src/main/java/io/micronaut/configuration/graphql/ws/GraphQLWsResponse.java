/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.graphql.ws;

import io.micronaut.configuration.graphql.GraphQLResponseBody;

import javax.annotation.Nullable;

/**
 * Class to handle the message to and from the websocket.
 *
 * @author Gerard Klijs
 * @since 1.3
 */
public class GraphQLWsResponse {

    private String type;
    @Nullable
    private String id;
    @Nullable
    private GraphQLResponseBody payload;

    /**
     * Constructor for messages with just the type, like errors.
     *
     * @param serverType type as serverType
     */
    public GraphQLWsResponse(ServerType serverType) {
        type = serverType.getType();
    }

    /**
     * Constructor for messages with only serverType and id, like the stop message.
     *
     * @param serverType serverType as serverType
     * @param id         id as string
     */
    public GraphQLWsResponse(ServerType serverType, String id) {
        type = serverType.getType();
        this.id = id;
    }

    /**
     * Constructor having all the types, like a graphql query response.
     *
     * @param serverType serverType as string
     * @param id         id as string
     * @param payload    payload as string
     */
    public GraphQLWsResponse(ServerType serverType, String id, GraphQLResponseBody payload) {
        type = serverType.getType();
        this.id = id;
        this.payload = payload;
    }

    /**
     * Get the type.
     *
     * @return the type of message as string
     */
    public String getType() {
        return type;
    }

    /**
     * Get the id.
     *
     * @return id as string
     */
    public String getId() {
        return id;
    }

    /**
     * Get the payload.
     *
     * @return result of the query
     */
    public GraphQLResponseBody getPayload() {
        return payload;
    }

    /**
     * Types of messages send to the client.
     */
    public enum ServerType {
        GQL_CONNECTION_ACK("connection_ack"),
        GQL_CONNECTION_ERROR("connection_error"),
        GQL_DATA("data"),
        GQL_ERROR("error"),
        GQL_COMPLETE("complete"),
        GQL_CONNECTION_KEEP_ALIVE("ka");

        private String type;

        /**
         * Default constructor.
         *
         * @param type string
         */
        ServerType(String type) {
            this.type = type;
        }

        /**
         * Get the type.
         *
         * @return type as string
         */
        public String getType() {
            return type;
        }
    }
}
