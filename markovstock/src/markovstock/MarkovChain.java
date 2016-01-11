package markovstock;

import java.io.IOException;
import java.sql.*;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;


public class MarkovChain {
	private double[][] transitions;
	private double averageChange;
	private String symbol;
	
	public MarkovChain(String sym, double averageStockChange, double[][] transitionMatrix){
		transitions = transitionMatrix;
		averageChange = averageStockChange;
		symbol = sym.toUpperCase();
	}
	
	public double getCurentPrice() throws IOException{
		Document page = Jsoup.connect("http://finance.yahoo.com/q?s=" + symbol).get();
		Element newsHeadlines = page.getElementById("yfs_l84_" + symbol);
		double price = Double.valueOf(newsHeadlines.text());
		return price;
	}
	
	public void doSimulations(int numSims, int numDays, double startingPrice) throws ClassNotFoundException, SQLException{
		 Class.forName("org.sqlite.JDBC");
		 Connection con = DriverManager.getConnection("jdbc:sqlite:test.db");
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
		 con.commit();
		 con.close();
	}
	
	public void eraseSimulations() throws ClassNotFoundException, SQLException{
		Class.forName("org.sqlite.JDBC");
		Connection con = DriverManager.getConnection("jdbc:sqlite:test.db");
		Statement statement = con.createStatement();
		String query = "DROP TABLE IF EXISTS SIMULATION" + symbol + ";";
		statement.executeUpdate(query);
		con.commit();
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
		return currPrice * (1 + multiplier);
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
