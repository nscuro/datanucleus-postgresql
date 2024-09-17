/*
 * This file is part of versatile.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Niklas Düster. All Rights Reserved.
 */
package io.github.nscuro.datanucleus.postgresql.method;

import io.github.nscuro.datanucleus.postgresql.test.model.Person;
import org.datanucleus.PropertyNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.jdo.JDOException;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import java.net.URL;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class JsonbContainsMethodTest {

    private static PostgreSQLContainer<?> postgresContainer;
    private PersistenceManagerFactory pmf;
    private PersistenceManager pm;

    @BeforeAll
    static void beforeAll() {
        postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine");
        postgresContainer.start();
    }

    @BeforeEach
    void beforeEach() {
        pmf = createPmf(postgresContainer);
        pm = pmf.getPersistenceManager();
    }

    @AfterEach
    void afterEach() {
        if (pm != null) {
            pm.close();
        }
        if (pmf != null) {
            pmf.close();
        }
    }

    @AfterAll
    static void afterAll() {
        if (postgresContainer != null) {
            postgresContainer.stop();
        }
    }

    @Test
    void shouldMatchWithStringParameter() {
        final var person = new Person();
        person.setProperties(/* language=JSON */ """
                [
                  {
                    "foo": "bar",
                    "baz": 111
                  }
                ]
                """);
        pm.makePersistent(person);

        final Query<Person> query = pm.newQuery(Person.class);
        query.setFilter("properties.jsonbContains(:foo)");
        query.setParameters("[{\"baz\":111}]");

        final Person queryResult = query.executeUnique();
        assertThat(queryResult).isNotNull();
    }

    @Test
    void shouldMatchWithStringLiteral() {
        final var person = new Person();
        person.setProperties(/* language=JSON */ """
                [
                  {
                    "foo": "bar",
                    "baz": 111
                  }
                ]
                """);
        pm.makePersistent(person);

        final Query<Person> query = pm.newQuery(Person.class);
        query.setFilter("properties.jsonbContains('[{\"baz\":111}]')");

        final Person queryResult = query.executeUnique();
        assertThat(queryResult).isNotNull();
    }

    @Test
    void shouldThrowForNonJsonStringArgument() {
        final var person = new Person();
        person.setProperties(/* language=JSON */ """
                [
                  {
                    "foo": "bar",
                    "baz": 111
                  }
                ]
                """);
        pm.makePersistent(person);

        final Query<Person> query = pm.newQuery(Person.class);
        query.setFilter("properties.jsonbContains('not-json')");
        query.setParameters(123);

        assertThatExceptionOfType(JDOException.class)
                .isThrownBy(query::executeUnique)
                .havingCause()
                .isInstanceOf(PSQLException.class)
                .withMessage("""
                        ERROR: invalid input syntax for type json
                          Detail: Token "not" is invalid.
                          Position: 172
                          Where: JSON data, line 1: not...""");
    }

    @Test
    void shouldThrowForNonStringArgument() {
        final var person = new Person();
        person.setProperties(/* language=JSON */ """
                [
                  {
                    "foo": "bar",
                    "baz": 111
                  }
                ]
                """);
        pm.makePersistent(person);

        final Query<Person> query = pm.newQuery(Person.class);
        query.setFilter("properties.jsonbContains(:foo)");
        query.setParameters(123);

        assertThatExceptionOfType(JDOException.class)
                .isThrownBy(query::executeUnique)
                .withMessage("""
                        Cannot invoke jsonbContains with argument of type \
                        org.datanucleus.store.rdbms.sql.expression.IntegerLiteral""");
    }

    private static PersistenceManagerFactory createPmf(final PostgreSQLContainer<?> postgresContainer) {
        final URL schemaUrl = JsonbContainsMethod.class.getResource("/schema.sql");
        assertThat(schemaUrl).isNotNull();

        return JDOHelper.getPersistenceManagerFactory(
                Map.ofEntries(
                        entry(PropertyNames.PROPERTY_PERSISTENCE_UNIT_NAME, "test"),
                        entry(PropertyNames.PROPERTY_SCHEMA_GENERATE_DATABASE_MODE, "drop-and-create"),
                        entry(PropertyNames.PROPERTY_SCHEMA_GENERATE_DATABASE_CREATE_SCRIPT, schemaUrl.toString()),
                        entry(PropertyNames.PROPERTY_CONNECTION_URL, postgresContainer.getJdbcUrl()),
                        entry(PropertyNames.PROPERTY_CONNECTION_DRIVER_NAME, postgresContainer.getDriverClassName()),
                        entry(PropertyNames.PROPERTY_CONNECTION_USER_NAME, postgresContainer.getUsername()),
                        entry(PropertyNames.PROPERTY_CONNECTION_PASSWORD, postgresContainer.getPassword())),
                "test");
    }

}