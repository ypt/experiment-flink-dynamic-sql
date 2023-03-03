# experiment-flink-dynamic-sql

<!-- TOC -->
* [Overview](#overview)
* [Rationale](#rationale)
* [Run the examples](#run-the-examples)
  * [Pre-requisites](#pre-requisites)
  * [Getting started](#getting-started)
  * [Example 1) streaming aggregation](#example-1--streaming-aggregation)
  * [Example 2) streaming joins](#example-2--streaming-joins)
* [Other exercises to try](#other-exercises-to-try)
* [Troubleshooting](#troubleshooting)
* [TODO](#todo)
<!-- TOC -->

# Overview

This is an experiment that explores the question: 

> How easy can we make stateful stream processing?

Here we'll build upon the [Apache Flink](https://flink.apache.org/) framework, a market leader in the stateful stream
processing space. 

Using Flink, there are two main areas of complexity to examine:

1. Creating a Flink program - This is the main area of exploration in this experiment
2. Running a Flink program - Aka, the Flink runtime. **This is out of scope** in this particular experiment. In
   practice, There are various solutions available - ranging from [Kubernetes operators](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-stable/)
   to fully managed Flink runtimes [[1](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-flink.html),
   [2](https://www.ververica.com/)].

This experiment will focus on (1) - exploring how we might simplify the process of creating a Flink program for
developers.

Ultimately, the goals explored here are:
1. How might we increase development velocity or decrease iteration cycle time?
2. How might we enable organizationally scalable development?

# Rationale

## Flink is great, but how practical is it to expect engineers across an organization to become experts at Flink?
In other words, how might we reduce the level of Flink expertise required?

## Angle of attack 1) Abstract Flink details away from engineers
Anything can be solved by [another level of indirection](https://en.wikipedia.org/wiki/Fundamental_theorem_of_software_engineering).

In other words, limit how much Flink code an engineer building a streaming process needs to work with.

In practice, this may take the form of libraries or platforms that wrap Flink.

In this experiment, we're implementing a **single reusable Flink job jar** that can be passed args upon execution to
dynamically build a stream processing pipeline.

## Angle of attack 2) Flink offers a range of programming API's. Some easier to use than others.
These Flink API's offer different levels of expressiveness vs conciseness

| API                                                                                                                            | Expressiveness | Conciseness  | 
|--------------------------------------------------------------------------------------------------------------------------------|----------------|--------------|
| [SQL / Table API](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/table/overview/)                         | +              | +++          |
| [DataStream API](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/datastream/overview/)                     | ++             | ++           |
| [ProcessFunction](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/python/datastream/operators/process_function/) | +++            | +            |

This experiment will focus on the SQL, the "easiest" among the Flink's programmatic
API's.

### Why SQL?

#### 1) Skill availability - SQL is _the_ standard universal data API
For better or for worse, SQL is the closest thing the industry has to a _universal_ data manipulation language. It is a
standard that is not only used across diverse tech stacks (e.g. Ruby, Java, etc.), but also across functions (e.g.
application development, data analytics, etc.)

In other words, it's easier to train or find someone with SQL expertise vs Flink expertise.

#### 2) Ease of use - SQL is a declarative API
Users state what end result they want, not how to do it. What you see is what you get.

#### 3) Development velocity
Given (1) and (2), also buys you development velocity.

#### 4) Performance
Flink is able to automatically [optimize](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/table/tuning/#:~:text=Flink%20Table%20API%20and%20SQL%20is%20effectively%20optimized%2C%20it%20integrates%20a%20lot%20of%20query%20optimizations%20and%20tuned%20operator%20implementations.)
the operations that happen underneath a SQL query.

### SQL tradeoffs

#### 1) A SQL query is just a string
This means:

1. You don't get as much compile time type safety
2. Dynamically manipulating SQL queries is not easy

#### 2) Less expressive (vs other Flink API's)
For example...

The [Process Function API](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/python/datastream/operators/process_function/)
provides very fine programmatic control of a Flink program. Giving developers direct control to manipulate state and
outputs.

One level of abstraction up, the [DataStream API](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/datastream/overview/)
provides a level of control akin to Java's Stream API (e.g. filter, map, reduce).

And there may be use cases where the these API's are a better fit vs the SQL API.

#### 3) If your organization's engineers are the most effective with Java, Scala, or Python (and not SQL)
In those cases, tools and API's in those languages may prove more effective in your organization 

# Run the examples

## Pre-requisites
- Java 11
- Docker

## Getting started

Start Pulsar
```
docker run -it --rm --name pulsar -p 6650:6650 -p 8080:8080 apachepulsar/pulsar:2.9.1 bin/pulsar standalone
```

Note: For the following examples, the Flink Web UI has been enabled. After you start the Flink job, you can access it
here to examine what Flink is doing under the hood.
```
http://localhost:8081/
```

## Example 1) streaming aggregation

e.g.

`users`

| accountId | uid   |
|-----------|-------|
| 100       | 100-1 |
| 100       | 100-2 |
| 101       | 101-1 |

→

`user_count_by_account`

| accountId | userCount |
|-----------|-----------|
| 100       | 2         |
| 101       | 1         |


Run the Flink job, passing it a few queries for it to execute as a stream processing job.

```bash
./gradlew run --args="
\"CREATE TABLE users (
  uid STRING,
  accountId STRING,
  id INT,
  fullName STRING,
  op STRING,
  eventTime TIMESTAMP(3) METADATA,
  properties MAP<STRING, STRING> METADATA ,
  topic STRING METADATA VIRTUAL,
  sequenceId BIGINT METADATA VIRTUAL,
  PRIMARY KEY (uid) NOT ENFORCED
) WITH (
  'connector' = 'upsert-pulsar',
  'topic' = 'persistent://public/default/users',
  'key.format' = 'raw',
  'value.format' = 'json',
  'service-url' = 'pulsar://localhost:6650',
  'admin-url' = 'http://localhost:8080'
)\" 

\"CREATE TABLE user_count_by_account (
  accountId STRING,
  userCount BIGINT,
  PRIMARY KEY (accountId) NOT ENFORCED
) WITH (
  'connector' = 'upsert-pulsar',
  'topic' = 'persistent://public/default/user_count_by_account',
  'key.format' = 'raw',
  'value.format' = 'json',
  'service-url' = 'pulsar://localhost:6650',
  'admin-url' = 'http://localhost:8080'
)\" 
 
\"INSERT INTO user_count_by_account (accountId, userCount) 
SELECT accountId, COUNT(1) AS userCount 
FROM users 
GROUP BY accountId\"
"
```

Here's what the above SQL queries do...

1. `CREATE TABLE users` → tell Flink to [interpret](https://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying#:~:text=There%20is%20a%20facinating%20duality%20between%20a%20log%20of%20changes%20and%20a%20table)
   the Pulsar topic `persistent://public/default/users` as a table called `users`.
2. `CREATE TABLE user_count_by_account` → tell Flink to interpret the Pulsar topic
   `persistent://public/default/user_count_by_account` as a table called `user_count_by_account`
3. `INSERT INTO user_count_by_account` → tell Flink to aggregate the number of users per account and keep the
   `user_count_by_account` "table" updated with the results.

Now, let's get some data flowing through this pipeline. 

Exec into the running Pulsar container
```
docker exec -it pulsar bash
```

Consume from sink topic which the aggregated data is being written to.
```
./bin/pulsar-client consume -n 0 -s my-sub persistent://public/default/user_count_by_account
```

Write to source table
```bash
# Simulate a few create operations
./bin/pulsar-client produce persistent://public/default/users -k 100-1 -f <(echo '{"uid":"100-1","accountId":"100","id":1,"fullName":"bob","op":"c"}')
./bin/pulsar-client produce persistent://public/default/users -k 100-2 -f <(echo '{"uid":"100-2","accountId":"100","id":2,"fullName":"bob","op":"c"}')
./bin/pulsar-client produce persistent://public/default/users -k 101-1 -f <(echo '{"uid":"101-1","accountId":"101","id":1,"fullName":"bob","op":"c"}')
./bin/pulsar-client produce persistent://public/default/users -k 101-2 -f <(echo '{"uid":"101-2","accountId":"101","id":2,"fullName":"bob","op":"c"}')

# Simulate an update operation
./bin/pulsar-client produce persistent://public/default/users -k 100-1 -f <(echo '{"uid":"100-1","accountId":"100","id":1,"fullName":"bob","op":"u"}')
```

Examine the Pulsar consumer's output. You should see something similar to this:

```
----- got message -----
key:[MTAw], properties:[], content:{"accountId":"100","userCount":1}
----- got message -----
key:[MTAw], properties:[], content:{"accountId":"100","userCount":2}
----- got message -----
key:[MTAx], properties:[], content:{"accountId":"101","userCount":1}
----- got message -----
key:[MTAx], properties:[], content:{"accountId":"101","userCount":2}
```

## Example 2) streaming joins

e.g.

`users`

| accountId | uid   |
|-----------|-------|
| 100       | 100-1 |
| 100       | 100-2 |
| 101       | 101-1 |

`user_favorite_color`

| userUid | favoriteColor |
|---------|---------------|
| 100-1   | blue          |

`user_favorite_cheese`

| userUid | favoriteCheese |
|---------|----------------|
| 100-1   | cheddar        |

→

`user_favorite_color_and_cheese`

| userUid | favoriteColor | favoriteCheese |
|---------|---------------|----------------|
| 100-1   | blue          | cheddar        |
| 100-2   |               |                |
| 101-1   |               |                |


Run the Flink job, passing it some other queries for it to execute as a stream processing job.
```bash
./gradlew run --args="
\"CREATE TABLE users (
  uid STRING,
  accountId STRING,
  id INT,
  fullName STRING,
  op STRING,
  eventTime TIMESTAMP(3) METADATA,
  properties MAP<STRING, STRING> METADATA ,
  topic STRING METADATA VIRTUAL,
  sequenceId BIGINT METADATA VIRTUAL,
  PRIMARY KEY (uid) NOT ENFORCED
) WITH (
  'connector' = 'upsert-pulsar',
  'topic' = 'persistent://public/default/users',
  'key.format' = 'raw',
  'value.format' = 'json',
  'service-url' = 'pulsar://localhost:6650',
  'admin-url' = 'http://localhost:8080'
)\" 

\"CREATE TABLE user_favorite_color (
  userUid STRING,
  favoriteColor STRING,
  PRIMARY KEY (userUid) NOT ENFORCED
) WITH (
  'connector' = 'upsert-pulsar',
  'topic' = 'persistent://public/default/user_favorite_color',
  'key.format' = 'raw',
  'value.format' = 'json',
  'service-url' = 'pulsar://localhost:6650',
  'admin-url' = 'http://localhost:8080'
)\" 

\"CREATE TABLE user_favorite_cheese (
  userUid STRING,
  favoriteCheese STRING,
  PRIMARY KEY (userUid) NOT ENFORCED
) WITH (
  'connector' = 'upsert-pulsar',
  'topic' = 'persistent://public/default/user_favorite_cheese',
  'key.format' = 'raw',
  'value.format' = 'json',
  'service-url' = 'pulsar://localhost:6650',
  'admin-url' = 'http://localhost:8080'
)\" 

\"CREATE TABLE user_favorite_color_and_cheese (
  accountId STRING,
  userUid STRING,
  favoriteColor STRING,
  favoriteCheese STRING,
  PRIMARY KEY (userUid) NOT ENFORCED
) WITH (
  'connector' = 'upsert-pulsar',
  'topic' = 'persistent://public/default/user_favorite_color_and_cheese',
  'key.format' = 'raw',
  'value.format' = 'json',
  'service-url' = 'pulsar://localhost:6650',
  'admin-url' = 'http://localhost:8080'
)\" 
 
\"INSERT INTO user_favorite_color_and_cheese (accountId, userUid, favoriteColor, favoriteCheese) 
SELECT 
  u.accountId, 
  u.uid,
  ufcol.favoriteColor,
  ufchz.favoriteCheese
FROM users u
  LEFT JOIN user_favorite_color ufcol
  ON u.uid = ufcol.userUid
  LEFT JOIN user_favorite_cheese ufchz
  ON u.uid = ufchz.userUid\"
"
```

Here's what the above SQL queries do...

1. `CREATE TABLE users` → tell Flink to [interpret](https://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying#:~:text=There%20is%20a%20facinating%20duality%20between%20a%20log%20of%20changes%20and%20a%20table)
   the Pulsar topic `persistent://public/default/users` as a table called `users`.
2. `CREATE TABLE user_favorite_color` → tell Flink to interpret the Pulsar topic
   `persistent://public/default/user_favorite_color` as a table called `user_favorite_color`.
3. `CREATE TABLE user_favorite_cheese` → tell Flink to interpret the Pulsar topic
   `persistent://public/default/user_favorite_cheese` as a table called `user_favorite_cheese`.
4. `CREATE TABLE user_favorite_color_and_cheese` → tell Flink to interpret the Pulsar topic
   `persistent://public/default/user_favorite_color_and_cheese` as a table called `user_favorite_color_and_cheese`.
5. `INSERT INTO user_favorite_color_and_cheese` → tell Flink to join `users`, `user_favorite_color`, and
   `user_favorite_cheese` on `userUid` and keep the `user_favorite_color_and_cheese` "table" updated with the results.

Now, let's get some data flowing through this pipeline.

Exec into the running Pulsar container
```bash
docker exec -it pulsar bash
```

Consume from the sink topic which the joined data is being written to.
```bash
./bin/pulsar-client consume -n 0 -s my-sub persistent://public/default/user_favorite_color_and_cheese
```

Write to source tables
```bash
# Simulate a creating a user
./bin/pulsar-client produce persistent://public/default/users -k 103-1 -f <(echo '{"uid":"103-1","accountId":"103","id":1,"fullName":"bob","op":"c"}')

# Simulate adding a favorite color
./bin/pulsar-client produce persistent://public/default/user_favorite_color -k 103-1 -f <(echo '{"userUid":"103-1","favoriteColor":"blue","op":"c"}')

# Simulate adding a favorite cheese
./bin/pulsar-client produce persistent://public/default/user_favorite_cheese -k 103-1 -f <(echo '{"userUid":"103-1","favoriteCheese":"cheddar","op":"c"}')

# Simulate updating a favorite cheese
./bin/pulsar-client produce persistent://public/default/user_favorite_cheese -k 103-1 -f <(echo '{"userUid":"103-1","favoriteCheese":"brie","op":"u"}')
```

Examine the Pulsar consumer's output. You should see something similar to this:

```
----- got message -----
key:[MTAzLTE=], properties:[], content:{"accountId":"103","userUid":"103-1","favoriteColor":null,"favoriteCheese":null}
----- got message -----
key:[MTAzLTE=], properties:[], content:{"accountId":"103","userUid":"103-1","favoriteColor":"blue","favoriteCheese":null}
----- got message -----
key:[MTAzLTE=], properties:[], content:{"accountId":"103","userUid":"103-1","favoriteColor":"blue","favoriteCheese":"cheddar"}
----- got message -----
key:[MTAzLTE=], properties:[], content:{"accountId":"103","userUid":"103-1","favoriteColor":"blue","favoriteCheese":"brie"}
````

# Other exercises to try
- [Windowing functions](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/table/sql/queries/window-tvf/)
- [Pattern recognition](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/table/sql/queries/match_recognize/)
- [Window Top-N](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/table/sql/queries/window-topn/)
- [EXPLAIN statements](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/dev/table/sql/explain/)

# Troubleshooting

## `java.lang.reflect.InaccessibleObjectException: Unable to make field private final byte[] java.lang.String.value accessible...`

```
Exception in thread "main" java.lang.reflect.InaccessibleObjectException: Unable to make field private final byte[] java.lang.String.value accessible: module java.base does not "opens java.lang" to unnamed module @41a962cf
```

If you see a runtime error like this with Java 17, try using Java 11 instead.

## `org.apache.flink.table.api.SqlParserException: SQL parse failed. Encountered "CREATE" at line 14, column 1. Was expecting one of: <EOF>...`

```
Exception in thread "main" org.apache.flink.table.api.SqlParserException: SQL parse failed. Encountered "CREATE" at line 14, column 1.
Was expecting one of:
    <EOF> 
    "LIKE" ...
    
        at org.apache.flink.table.planner.parse.CalciteParser.parse(CalciteParser.java:56)
        at org.apache.flink.table.planner.delegation.ParserImpl.parse(ParserImpl.java:98)
        at org.apache.flink.table.api.internal.TableEnvironmentImpl.executeSql(TableEnvironmentImpl.java:736)
        at com.example.flinkjobpulsartopostgresexample.StreamingJob.main(StreamingJob.java:91)
```

If you see runtime error like the following, ensure that your SQL queries are delimited by spaces. (Yes, this is silly
and should be fixed ASAP).

## `Failed to create producer: {"errorMsg":"org.apache.pulsar.broker.service.schema.exceptions.IncompatibleSchemaException...`

```
ERROR org.apache.pulsar.client.impl.ProducerImpl - [persistent://public/default/user_favorite_color_and_cheese] [null] Failed to create producer: {"errorMsg":"org.apache.pulsar.broker.service.schema.exceptions.IncompatibleSchemaException: org.apache.avro.SchemaValidationException: Unable to read schema: 
{
  "type" : "record",
  "name" : "record",
  "fields" : [ {
    "name" : "accountId",
...
```

The prior schema registered with the Pulsar schema registry is not compatible with the current schema you're trying to
connect with.

In production, this behavior is a feature, not a bug - important for graceful schema evolution and the protection of
downstream data consumers.

For this experiment though, the simplest fix it to stop Pulsar and restart it, to clear out the data it had previously
persisted.

## `org.apache.flink.streaming.connectors.pulsar.internal.ReaderThread - Error while closing Pulsar reader org.apache.pulsar.client.api.PulsarClientException: java.lang.InterruptedException`

```
ERROR org.apache.flink.streaming.connectors.pulsar.internal.ReaderThread - Error while closing Pulsar reader org.apache.pulsar.client.api.PulsarClientException: java.lang.InterruptedException
```

When writing some data to Pulsar, if you see this error along with a crashed Flink job runner and an inaccessible web UI,
most likely you wrote some incorrect data to Pulsar - e.g. Using a string when an int was expected.

Double check your columns in `CREATE TABLE` or the data you're writing with `pulsar-client produce`.


# TODO
- Update project dependencies
- Explore other ways of passing in SQL queries
  - The current space based delimiter is not great. :(
  - Secrets exposure :(
- Runtime observability story
- Instead of registering tables directly in our queries, external catalogs can be used. See:
  - Flink & Catalogs: https://ci.apache.org/projects/flink/flink-docs-release-1.12/dev/table/common.html#create-tables-in-the-catalog
  - Pulsar Catalog: https://github.com/streamnative/pulsar-flink#catalog
  - Postgres Catalog: https://ci.apache.org/projects/flink/flink-docs-release-1.12/dev/table/connectors/jdbc.html#postgres-database-as-a-catalog
- Get this running in Kubernetes using the [Flink Kubernetes Operator](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/)
- An example with a delete operation
- An example with a [Postgres sink](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/table/jdbc/)
---
