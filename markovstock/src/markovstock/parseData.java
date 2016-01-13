package markovstock;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

public class ParseData {
	
	public static ArrayList<String[]> obtainRecords(String symbol) throws MalformedURLException, IOException{
		String url = "http://ichart.finance.yahoo.com/table.csv?s=" + symbol;
		InputStream csv = new URL(url).openStream();
		Reader reader = new InputStreamReader(csv, "UTF-8");
		CSVReader csvReader = new CSVReader(reader);
		List<String[]> records = csvReader.readAll();
		csvReader.close();
		return new ArrayList<String[]>(records.subList(1, records.size()));
	}
	
	public static MarkovChain createParameters(String sym, ArrayList<String[]> records) throws IOException{
		ArrayList<Double> closingPrices = new ArrayList<Double>();
		for (String[] day: records){
			closingPrices.add(Double.valueOf(day[4]));
		}
		int numTrans = closingPrices.size() - 1;
		double sumDiffs = 0;
		for (int i = 0; i < numTrans; i++){
			double start = closingPrices.get(i);
			double finish = closingPrices.get(i + 1);
			double diff = 100.0 * (Math.abs(finish - start) / start);
			sumDiffs += diff;
		}
		int[][] transitionCounts = new int[5][5];
		double averageChange = Math.abs(sumDiffs / numTrans);
		int currentState;
		int pastState = classifyDifference(closingPrices.get(1) - closingPrices.get(0), averageChange);
		for (int i = 1; i < numTrans; i++, pastState = currentState){
			double start = closingPrices.get(i);
			double finish = closingPrices.get(i + 1);
			double diff = 100.0 * ((finish - start) / start);
			currentState = classifyDifference(diff, averageChange);
			transitionCounts[pastState][currentState]++;
		}
		double[][] transitionMatrix = new double[5][5];
		for (int i = 0; i < transitionCounts.length; i++){
			int sum = 0;
			for (int j = 0; j < transitionCounts[i].length; j++){
				sum += transitionCounts[i][j];
			}
			for (int k = 0; k < transitionCounts[i].length; k++){
				transitionMatrix[i][k] = (double)(transitionCounts[i][k]) / sum;
			}
		}
		CSVWriter writer = new CSVWriter(new FileWriter("matrices.csv", true), '\t', CSVWriter.NO_QUOTE_CHARACTER, System.getProperty("line.separator"));
		String[] concatTrans = new String[transitionMatrix.length*transitionMatrix.length + 2];
		concatTrans[0] = sym;
		concatTrans[1] = Double.toString(averageChange);
		for (int i = 0; i < transitionMatrix.length; i++){
			for (int j = 0; j < transitionMatrix.length; j++){
				concatTrans[transitionMatrix.length*i + j + 2] = Double.toString(transitionMatrix[i][j]);
			}
		}
		writer.writeNext(concatTrans);
		writer.close();
		return new MarkovChain(sym, averageChange, transitionMatrix);
	}
	
	//0: small up, 1:large up, 2:small down, 3:large down, 4:no difference
	public static int classifyDifference(double diff, double avgDiff){
		if (diff == 0){
			return 4;
		}
		if (Math.abs(diff) >= avgDiff){
			if (diff > 0){
				return 1;
			}
			return 3;
		}
		else{
			if (diff > 0){
				return 0;
			}
			return 2;
		}
	}
}
