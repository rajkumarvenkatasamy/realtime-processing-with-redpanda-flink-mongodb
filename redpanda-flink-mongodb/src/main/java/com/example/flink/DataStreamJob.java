/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.flink;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;

/**
 * Flink DataStream Job.
 *
 * <p>For a tutorial how to write a Flink application, check the
 * tutorials and examples on the <a href="https://flink.apache.org">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class DataStreamJob {
	private static final Logger LOG = LoggerFactory.getLogger(DataStreamJob.class);

	public static void main(String[] args) throws Exception {
		// Sets up the execution environment, which is the main entry point
		// to building Flink applications.
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		// Configure Redpanda source
		Properties kafkaProps = new Properties();
		kafkaProps.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "redpanda-0:9092");
		kafkaProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "flink-consumer-group");

		// Create the Redpanda source connector
		FlinkKafkaConsumer<String> kafkaConsumer = new FlinkKafkaConsumer<>("financial-transactions",
				new SimpleStringSchema(), kafkaProps);

		// Add Redpanda source to the execution environment
		DataStream<String> transactionStream = env.addSource(kafkaConsumer);

		// Configure MongoDB sink
		// Add MongoDB sink to the transaction stream
		transactionStream.addSink(new MongoDBSink());

		// Execute the Flink job
		env.execute("Fraud Detection App");

	}

	public static class MongoDBSink extends RichSinkFunction<String> {
		private transient MongoCollection<Document> collection;

		@Override
		public void open(Configuration parameters) throws Exception {
			// Create the MongoDB client to establish connection
			MongoClientSettings settings = MongoClientSettings.builder()
					.applyToClusterSettings(builder ->
							builder.hosts(Arrays.asList(new ServerAddress("mongo", 27017))))
					.codecRegistry(createCodecRegistry())
					.build();

			com.mongodb.client.MongoClient mongoClient = MongoClients.create(settings);

			// Access the MongoDB database and collection
			// At this stage, if the Mongo DB and collection does not exist, they would get auto-created
			MongoDatabase database = mongoClient.getDatabase("fraud_detection");
			collection = database.getCollection("fraud_transactions");
		}

		@Override
		public void invoke(String value, Context context) throws Exception {
			// Consume the event from redpanda topic
			LOG.info("Consumed event : " + value);
			Document transactionDocument = Document.parse(value);
			LOG.info("transactionDocument is : "+ transactionDocument);
			// Optionally you can add fraud detection logic as an additional exercise task from your end here
			// ...

			// Insert into MongoDB collection
			collection.insertOne(transactionDocument);
		}

		@Override
		public void close() throws Exception {
			// Clean up resources, if needed
		}

		private CodecRegistry createCodecRegistry() {
		// The method createCodecRegistry is a helper method that is used to create a CodecRegistry object for MongoDB.
		// In MongoDB, a CodecRegistry is responsible for encoding and decoding Java objects to
		// BSON (Binary JSON) format, which is the native format used by MongoDB to store and retrieve data.
			return CodecRegistries.fromRegistries(
					MongoClientSettings.getDefaultCodecRegistry(),
					CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
			);
		}
	}
}
