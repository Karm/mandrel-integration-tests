/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.acme.vertx;

import io.quarkus.runtime.Startup;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FruitApp {

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    @Inject
    PgPool client;

    @Startup
    void initdb() {
        if (schemaCreate) {
            client.query("DROP TABLE IF EXISTS fruits").execute()
                    .flatMap(r -> client.query("CREATE TABLE fruits (id SERIAL PRIMARY KEY, name TEXT NOT NULL)").execute())
                    .flatMap(r -> client.query("INSERT INTO fruits (name) VALUES ('Orange')").execute())
                    .flatMap(r -> client.query("INSERT INTO fruits (name) VALUES ('Pear')").execute())
                    .flatMap(r -> client.query("INSERT INTO fruits (name) VALUES ('Apple')").execute()).await().indefinitely();
        }
    }
}
