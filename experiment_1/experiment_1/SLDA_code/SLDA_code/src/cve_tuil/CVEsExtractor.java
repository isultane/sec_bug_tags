package cve_tuil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

public class CVEsExtractor {

	public static Map<String, Integer> unique = new HashMap<String, Integer>();
	public static void counter(List<String> cveYear){
		for (String s : cveYear) {
			Integer cnt = unique.get(s);
			if (cnt == null) {
				unique.put(s, 1);
			} else {
				unique.put(s, cnt + 1);
			}
		}
	}
	public static void numOfCVEsPerYear(){
		try {
			CsvReader CVEsData = new CsvReader("CVEsSummaries.csv");
			CVEsData.readHeaders();
			List<String> year = new ArrayList<String>();
			while (CVEsData.readRecord())
			{
				String CVEid = CVEsData.get("Vulnerability").replaceAll("http://encs.concordia.ca/ontologies/2015/Secont#", "");
				String[] splits = CVEid.split("-");
				year.add(splits[0]+"-"+splits[1]);
			}
			counter(year);
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void extractCVEsDetails(){
		try {
			CsvReader CVEsData = new CsvReader("CVEsSummaries.csv");
			CVEsData.readHeaders();
			StringBuffer content = new StringBuffer();
			while (CVEsData.readRecord())
			{
				String CVEid = CVEsData.get("Vulnerability").replaceAll("http://encs.concordia.ca/ontologies/2015/Secont#", "");
				String[] splits = CVEid.split("-");
				String year = splits[1];
				
				content.append(CVEid+","+year);
				content.append("\n");
			}
			System.out.println("Writing to File...");
			writeDetailsTofile("CVEs.csv",content);

			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void extractCVEsSummaries(){
		try {
			CsvReader CVEsData = new CsvReader("CVEsSummaries.csv");
			CVEsData.readHeaders();
			while (CVEsData.readRecord())
			{
				String CVEid = CVEsData.get("Vulnerability").replaceAll("http://encs.concordia.ca/ontologies/2015/Secont#", "");
				StringBuffer content = new StringBuffer();
				content.append(CVEsData.get("Summary"));
				
				writeTofile(CVEid,content);
			}
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void main(String[] args) {
		System.out.println("Reading CVEs data...");
		extractCVEsDetails();
//	//	extractCVEsSummaries();
//		numOfCVEsPerYear();
//		for (Map.Entry<String, Integer> entry : unique.entrySet()) {
//			String key = entry.getKey();
//			int cnt = entry.getValue();
//			System.out.println("Year: " +key + " , count: "+cnt);
//		}
	}
	
	public static void writeTofile(String filename, StringBuffer content) throws IOException{
		String path = "cve_files/";
		// before we open the file check to see if it already exists
		File file = new File(path+filename);
		
		if(!file.exists()){
			file.createNewFile();
		}
		
		PrintWriter bwr = new PrintWriter(new FileWriter(file, true));

		// write contents of StringBuffer to a file
		bwr.println(content);

		// flush the stream
		bwr.flush();

		// close the stream
		bwr.close();

		System.out.println("Content of the document "+filename+" written to File.");
		
	}
	public static void writeDetailsTofile(String filename, StringBuffer content) throws IOException{
		// before we open the file check to see if it already exists
		File file = new File(filename);
		
		if(!file.exists()){
			file.createNewFile();
		}
		
		PrintWriter bwr = new PrintWriter(new FileWriter(file, true));

		// write contents of StringBuffer to a file
		bwr.println(content);

		// flush the stream
		bwr.flush();

		// close the stream
		bwr.close();
		
	}

}
