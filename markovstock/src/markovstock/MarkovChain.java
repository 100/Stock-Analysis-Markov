package markovstock;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.jfree.data.time.Day;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;


public class MarkovChain {
	private double[][] transitions;
	private double averageChange;
	private String symbol;
	
	public MarkovChain(String sym, double averageStockChange, double[][] transitionMatrix){
		this.transitions = transitionMatrix;
		this.averageChange = averageStockChange;
		this.symbol = sym.toUpperCase();
	}
	
	public MarkovChain(String sym) throws IOException{
		CSVReader reader = new CSVReader(new FileReader("matrices.csv"), '\t');
		String[] line;
		while ((line = reader.readNext()) != null){
			if (line[0].equals(sym)){
				break;
			}
		}
		this.symbol = sym.toUpperCase();
		this.averageChange = Double.valueOf(line[1]);
		this.transitions = new double[5][5];
		for (int i = 2; i < line.length; i++){
			this.transitions[(i-2)/5][(i-2)%5] = Double.valueOf(line[i]);
		}
		reader.close();
	}
	
	public double getCurentPrice() throws IOException{
		Document page = Jsoup.connect("http://finance.yahoo.com/q?s=" + symbol).get();
		Element priceEle = page.getElementById("yfs_l84_" + symbol.toLowerCase());
		double price = Double.valueOf(priceEle.text());
		return price;
	}
	
	public void doSimulations(int numSims, int numDays, double startingPrice) throws ClassNotFoundException, SQLException{
		 Class.forName("org.sqlite.JDBC");
		 Connection con = DriverManager.getConnection("jdbc:sqlite:sims.db");
		 con.setAutoCommit(true);
		 Statement statement = con.createStatement();
		 String query = "CREATE TABLE IF NOT EXISTS SIMULATION" + symbol + " (" +
				 		"ITERATION INT PRIMARY KEY	NOT NULL, ";
		 for (int i = 0; i < numDays; i++){
			 query += "DAY" + (i+1) + " DOUBLE PRECISION	NOT NULL, ";
		 }
		 query = query.substring(0, query.length() - 2) + ");";
		 statement.executeUpdate(query);
		 query = "INSERT INTO SIMULATION" + symbol + " VALUES (?, ";
		 for (int i = 0; i < numDays; i++){
			 query += "?, ";
		 }
		 query = query.substring(0, query.length() - 2) + ");";
		 PreparedStatement insertion = con.prepareStatement(query);
		 for (int i = 0; i < numSims; i++){
			 insertion.setInt(1, i+1);
			 double[] predictions = predict(numDays, startingPrice);
			 for (int j = 0; j < predictions.length; j++){
				 insertion.setDouble(j+2, predictions[j]);
			 }
			 insertion.executeUpdate();
		 }
		 con.close();
	}
	
	public void extractSimulations(double currentPrice, TimeSeriesCollection collection) throws ClassNotFoundException, SQLException{
		Class.forName("org.sqlite.JDBC");
		Connection con = DriverManager.getConnection("jdbc:sqlite:sims.db");
		Statement stmt = con.createStatement();
		ResultSet results = stmt.executeQuery("SELECT * FROM SIMULATION" + symbol + ";");
		collection.removeAllSeries();
		addSimulationsToSeries(currentPrice, results, collection);
		con.close();
	}
	
	public void addSimulationsToSeries(double currentPrice, ResultSet sims, TimeSeriesCollection collection) throws SQLException{
		int columns = sims.getMetaData().getColumnCount();
		while (sims.next()){
			String simName = Integer.toString(sims.getInt(1));
			TimeSeries series = new TimeSeries("Sim" + simName);
			RegularTimePeriod day = new Day();
			series.add(day, currentPrice);
			day = day.next();
			for (int i = 2; i < columns+1; i++){
				double price = sims.getDouble(i);
				series.add(day, price);
	            day = day.next();
			}
			collection.addSeries(series);
			day = new Day();
		}
	}
	
	public void eraseSimulations() throws ClassNotFoundException, SQLException{
		Class.forName("org.sqlite.JDBC");
		Connection con = DriverManager.getConnection("jdbc:sqlite:sims.db");
		Statement statement = con.createStatement();
		String query = "DROP TABLE IF EXISTS SIMULATION" + symbol + ";";
		statement.executeUpdate(query);
		query = "VACUUM;";
		statement.executeUpdate(query);
		con.close();
	}
	
	public void exportSimulations() throws IOException, ClassNotFoundException, SQLException{
		File resultsDir = new File("./results");
		if (!resultsDir.exists()){
			resultsDir.mkdir();
		}
		String filePath;
		if (new File("./results/table" + symbol + ".csv").exists()){
			File directory = new File("./results/");
			String[] filesInDir = directory.list();
			int currNumbering = 0;
			for (int i = 0; i < filesInDir.length; i++){
				if (filesInDir[i].startsWith("table" + symbol)){
					currNumbering++;
				}
			}
			filePath = "./results/table" + symbol + String.valueOf(currNumbering);
		}
		else{
			filePath = "./results/table" + symbol;
		}
		CSVWriter writer = new CSVWriter(new FileWriter(filePath + ".csv"), '\t', CSVWriter.NO_QUOTE_CHARACTER);
		Class.forName("org.sqlite.JDBC");
	    Connection con = DriverManager.getConnection("jdbc:sqlite:sims.db");
	    Statement stmt = con.createStatement();
	    ResultSet results = stmt.executeQuery("SELECT * FROM SIMULATION" + symbol + ";");
		writer.writeAll(results, true, true);
		writer.close();
		con.close();
	}
	
	public double predictXDays(int initialState, int finalState, int daysLater){
		RealMatrix transitionMatrix = MatrixUtils.createRealMatrix(transitions);
		RealMatrix result = MatrixUtils.createRealMatrix(transitions);
		for (int i = 0; i < daysLater - 1; i++){
			result = transitionMatrix.multiply(result);
		}
		double chance = result.getEntry(initialState, finalState);
		return chance;
	}
	
	public double[] predict(int days, double startingPrice){
		double[] predictions = new double[days];
		int begin = (int)(Math.random() * 5);
		predictions[0] = makeStep(startingPrice, begin);
		int currentState;
		int pastState = begin;
		for (int i = 1; i < days; i++, pastState = currentState){
			currentState = makeSelection(pastState);
			predictions[i] = makeStep(predictions[i-1], currentState);
		}
		return predictions;
	}
	
	//0: small up, 1:large up, 2:small down, 3:large down, 4:no difference
	public double makeStep(double currPrice, int transition){
		double multiplier = 0;
		if (transition == 0){
			multiplier = (Math.random() * averageChange);
		}
		else if (transition == 1){
			multiplier = averageChange + (Math.random() * averageChange);
		}
		else if (transition == 2){
			multiplier = -(Math.random() * averageChange);
		}
		else if (transition == 3){
			multiplier = -(averageChange + (Math.random() * averageChange));
		}
		return currPrice * (1 + multiplier/100);
	}
	
	public int makeSelection(int currState){
		double[] values = transitions[currState];
		double[] rouletteWheel = new double[5];
		rouletteWheel[0] = values[0];
		for (int i = 1; i < values.length; i++){
			rouletteWheel[i] = rouletteWheel[i-1] + values[i];
		}
		double choice = Math.random();
		int selection = 0;
		for (; selection < values.length; selection++){
			if (choice <= rouletteWheel[selection]){
				break;
			}
		}
		return selection;
	}
}
