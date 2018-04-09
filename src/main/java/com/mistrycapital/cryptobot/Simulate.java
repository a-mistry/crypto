package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Simulate {
	public static void main(String[] args)
		throws Exception
	{
		Path file = Paths.get("C:\\Users\\mistr\\Documents\\gdaxtmp\\samples-2018-01-14.csv");
		try(
			Reader in = Files.newBufferedReader(file);
			CSVParser parser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in);
		) {
			for(CSVRecord record : parser) {
				ProductSnapshot snapshot = new ProductSnapshot(record);
				System.out.println("Price " + snapshot.lastPrice);
			}
		}
	}
}
