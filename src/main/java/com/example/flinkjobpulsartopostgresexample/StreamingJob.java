package com.example.flinkjobpulsartopostgresexample;

import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.util.Arrays;
import java.util.stream.Stream;

public class StreamingJob {
    public static void main(String[] args) {
        // Enable the Flink web ui for local debugging. It will be accessible at http://localhost:8081
        // TODO: decide what's appropriate for prod deployments
        Configuration config = new Configuration();
        config.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, true);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);
        //  StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        EnvironmentSettings envSettings = EnvironmentSettings.newInstance()
                .inStreamingMode()
                .build();

        // TODO: let other config options be configurable externally. e.g.
        // env.setParallelism(3);
        // Enable checkpointing for incremental synchronization.
        // env.enableCheckpointing(10000);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, envSettings);


        // Execute SQL statements sourced externally from the source code itself

        // TODO: in practice, perform better arg parsing and validation
        //  Flink has a simple arg parser built in (https://nightlies.apache.org/flink/flink-docs-release-1.14/docs/dev/datastream/application_parameters/).
        //  Or, others in the Java ecosystem can also be used.

        // TODO: source queries another way?
        // TODO: Also, something better than the current space based delimiter.
        Stream<String> queries = Arrays.stream(args)
                .map(String::trim)
                .filter(q -> q.length() > 0);

        queries.forEach(q -> {
            System.out.println("");
            System.out.println("EXECUTING SQL:");
            System.out.println("");
            System.out.println(q);
            tableEnv.executeSql(q);

            // TODO: DML operations are executed asynchronously. Should we wait for the results of one before executing the next?
            //  https://nightlies.apache.org/flink/flink-docs-master/api/java/org/apache/flink/table/api/TableEnvironment.html#executeSql-java.lang.String-
        });
    }
}
