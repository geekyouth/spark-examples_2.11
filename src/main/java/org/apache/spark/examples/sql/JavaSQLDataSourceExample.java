/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.examples.sql;

// $example on:schema_merging$

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

// $example off:schema_merging$
// $example on:basic_parquet_example$
// $example on:schema_merging$
// $example on:json_dataset$
// $example off:json_dataset$
// $example off:schema_merging$
// $example off:basic_parquet_example$

public class JavaSQLDataSourceExample {
	
	// $example on:schema_merging$
	public static class Square implements Serializable {
		private int value;
		private int square;
		
		// Getters and setters...
		// $example off:schema_merging$
		public int getValue() {
			return value;
		}
		
		public void setValue(int value) {
			this.value = value;
		}
		
		public int getSquare() {
			return square;
		}
		
		public void setSquare(int square) {
			this.square = square;
		}
		// $example on:schema_merging$
	}
	// $example off:schema_merging$
	
	// $example on:schema_merging$
	public static class Cube implements Serializable {
		private int value;
		private int cube;
		
		// Getters and setters...
		// $example off:schema_merging$
		public int getValue() {
			return value;
		}
		
		public void setValue(int value) {
			this.value = value;
		}
		
		public int getCube() {
			return cube;
		}
		
		public void setCube(int cube) {
			this.cube = cube;
		}
		// $example on:schema_merging$
	}
	// $example off:schema_merging$
	
	public static void main(String[] args) {
		SparkSession spark = SparkSession
			.builder()
			.appName("Java Spark SQL data sources example")
			.config("spark.some.config.option", "some-value")
			.getOrCreate();
		
		runBasicDataSourceExample(spark);
		runBasicParquetExample(spark);
		runParquetSchemaMergingExample(spark);
		runJsonDatasetExample(spark);
		runJdbcDatasetExample(spark);
		
		spark.stop();
	}
	
	private static void runBasicDataSourceExample(SparkSession spark) {
		// $example on:generic_load_save_functions$
		Dataset<Row> usersDF = spark.read().load("examples/src/main/resources/users.parquet");
		usersDF.select("name", "favorite_color").write().save("namesAndFavColors.parquet");
		// $example off:generic_load_save_functions$
		// $example on:manual_load_options$
		Dataset<Row> peopleDF =
			spark.read().format("json").load("examples/src/main/resources/people.json");
		peopleDF.select("name", "age").write().format("parquet").save("namesAndAges.parquet");
		// $example off:manual_load_options$
		// $example on:direct_sql$
		Dataset<Row> sqlDF =
			spark.sql("SELECT * FROM parquet.`examples/src/main/resources/users.parquet`");
		// $example off:direct_sql$
	}
	
	private static void runBasicParquetExample(SparkSession spark) {
		// $example on:basic_parquet_example$
		Dataset<Row> peopleDF = spark.read().json("examples/src/main/resources/people.json");
		
		// DataFrames can be saved as Parquet files, maintaining the schema information
		peopleDF.write().parquet("people.parquet");
		
		// Read in the Parquet file created above.
		// Parquet files are self-describing so the schema is preserved
		// The result of loading a parquet file is also a DataFrame
		Dataset<Row> parquetFileDF = spark.read().parquet("people.parquet");
		
		// Parquet files can also be used to create a temporary view and then used in SQL statements
		parquetFileDF.createOrReplaceTempView("parquetFile");
		Dataset<Row> namesDF = spark.sql("SELECT name FROM parquetFile WHERE age BETWEEN 13 AND 19");
		Dataset<String> namesDS = namesDF.map(new MapFunction<Row, String>() {
			public String call(Row row) {
				return "Name: " + row.getString(0);
			}
		}, Encoders.STRING());
		namesDS.show();
		// +------------+
		// |       value|
		// +------------+
		// |Name: Justin|
		// +------------+
		// $example off:basic_parquet_example$
	}
	
	private static void runParquetSchemaMergingExample(SparkSession spark) {
		// $example on:schema_merging$
		List<Square> squares = new ArrayList<>();
		for (int value = 1; value <= 5; value++) {
			Square square = new Square();
			square.setValue(value);
			square.setSquare(value * value);
			squares.add(square);
		}
		
		// Create a simple DataFrame, store into a partition directory
		Dataset<Row> squaresDF = spark.createDataFrame(squares, Square.class);
		squaresDF.write().parquet("data/test_table/key=1");
		
		List<Cube> cubes = new ArrayList<>();
		for (int value = 6; value <= 10; value++) {
			Cube cube = new Cube();
			cube.setValue(value);
			cube.setCube(value * value * value);
			cubes.add(cube);
		}
		
		// Create another DataFrame in a new partition directory,
		// adding a new column and dropping an existing column
		Dataset<Row> cubesDF = spark.createDataFrame(cubes, Cube.class);
		cubesDF.write().parquet("data/test_table/key=2");
		
		// Read the partitioned table
		Dataset<Row> mergedDF = spark.read().option("mergeSchema", true).parquet("data/test_table");
		mergedDF.printSchema();
		
		// The final schema consists of all 3 columns in the Parquet files together
		// with the partitioning column appeared in the partition directory paths
		// root
		//  |-- value: int (nullable = true)
		//  |-- square: int (nullable = true)
		//  |-- cube: int (nullable = true)
		//  |-- key: int (nullable = true)
		// $example off:schema_merging$
	}
	
	private static void runJsonDatasetExample(SparkSession spark) {
		// $example on:json_dataset$
		// A JSON dataset is pointed to by path.
		// The path can be either a single text file or a directory storing text files
		Dataset<Row> people = spark.read().json("examples/src/main/resources/people.json");
		
		// The inferred schema can be visualized using the printSchema() method
		people.printSchema();
		// root
		//  |-- age: long (nullable = true)
		//  |-- name: string (nullable = true)
		
		// Creates a temporary view using the DataFrame
		people.createOrReplaceTempView("people");
		
		// SQL statements can be run by using the sql methods provided by spark
		Dataset<Row> namesDF = spark.sql("SELECT name FROM people WHERE age BETWEEN 13 AND 19");
		namesDF.show();
		// +------+
		// |  name|
		// +------+
		// |Justin|
		// +------+
		
		// Alternatively, a DataFrame can be created for a JSON dataset represented by
		// an RDD[String] storing one JSON object per string.
		List<String> jsonData = Arrays.asList(
			"{\"name\":\"Yin\",\"address\":{\"city\":\"Columbus\",\"state\":\"Ohio\"}}");
		JavaRDD<String> anotherPeopleRDD =
			new JavaSparkContext(spark.sparkContext()).parallelize(jsonData);
		Dataset anotherPeople = spark.read().json(anotherPeopleRDD);
		anotherPeople.show();
		// +---------------+----+
		// |        address|name|
		// +---------------+----+
		// |[Columbus,Ohio]| Yin|
		// +---------------+----+
		// $example off:json_dataset$
	}
	
	private static void runJdbcDatasetExample(SparkSession spark) {
		// $example on:jdbc_dataset$
		// Note: JDBC loading and saving can be achieved via either the load/save or jdbc methods
		// Loading data from a JDBC source
		Dataset<Row> jdbcDF = spark.read()
			.format("jdbc")
			.option("url", "jdbc:postgresql:dbserver")
			.option("dbtable", "schema.tablename")
			.option("user", "username")
			.option("password", "password")
			.load();
		
		Properties connectionProperties = new Properties();
		connectionProperties.put("user", "username");
		connectionProperties.put("password", "password");
		Dataset<Row> jdbcDF2 = spark.read()
			.jdbc("jdbc:postgresql:dbserver", "schema.tablename", connectionProperties);
		
		// Saving data to a JDBC source
		jdbcDF.write()
			.format("jdbc")
			.option("url", "jdbc:postgresql:dbserver")
			.option("dbtable", "schema.tablename")
			.option("user", "username")
			.option("password", "password")
			.save();
		
		jdbcDF2.write()
			.jdbc("jdbc:postgresql:dbserver", "schema.tablename", connectionProperties);
		// $example off:jdbc_dataset$
	}
}
