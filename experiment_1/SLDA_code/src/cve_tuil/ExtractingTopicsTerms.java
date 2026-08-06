package cve_tuil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import com.csvreader.CsvReader;

public class ExtractingTopicsTerms {

	public static void main(String[] args) throws IOException {
		StringBuffer Topic0 = new StringBuffer();
		StringBuffer Topic1 = new StringBuffer();
		StringBuffer Topic2 = new StringBuffer();
		StringBuffer Topic3 = new StringBuffer();
		StringBuffer Topic4 = new StringBuffer();
		StringBuffer Topic5 = new StringBuffer();
		StringBuffer Topic6 = new StringBuffer();
		StringBuffer Topic7 = new StringBuffer();
		StringBuffer Topic8 = new StringBuffer();
		StringBuffer Topic9 = new StringBuffer();
		StringBuffer Topic10 = new StringBuffer();
		StringBuffer Topic11 = new StringBuffer();
		StringBuffer Topic12 = new StringBuffer();
		StringBuffer Topic13 = new StringBuffer();
		StringBuffer Topic14 = new StringBuffer();
		StringBuffer Topic15 = new StringBuffer();
		StringBuffer Topic16 = new StringBuffer();
		StringBuffer Topic17 = new StringBuffer();
		StringBuffer Topic18 = new StringBuffer();
		StringBuffer Topic19 = new StringBuffer();
		BufferedReader br = new BufferedReader(new FileReader("SLDA-T20-A0.01-B0.01-I1000-ProbWords.csv"));
		String line;
		while ((line = br.readLine()) != null) {
			if(line.contains("Topic"))
				continue;
		    // use comma as separator
		    String[] cols = line.split(",");
		    Topic0.append(Parsing(cols[0])+",");
		    Topic1.append(Parsing(cols[1])+",");
		    Topic2.append(Parsing(cols[2])+",");
		    Topic3.append(Parsing(cols[3])+",");
		    Topic4.append(Parsing(cols[4])+",");
		    Topic5.append(Parsing(cols[5])+",");
		    Topic6.append(Parsing(cols[6])+",");
		    Topic7.append(Parsing(cols[7])+",");
		    Topic8.append(Parsing(cols[8])+",");
		    Topic9.append(Parsing(cols[9])+",");
		    Topic10.append(Parsing(cols[10])+",");
		    Topic11.append(Parsing(cols[11])+",");
		    Topic12.append(Parsing(cols[12])+",");
		    Topic13.append(Parsing(cols[13])+",");
		    Topic14.append(Parsing(cols[14])+",");
		    Topic15.append(Parsing(cols[15])+",");
		    Topic16.append(Parsing(cols[16])+",");
		    Topic17.append(Parsing(cols[17])+",");
		    Topic18.append(Parsing(cols[18])+",");
		    Topic19.append(Parsing(cols[19])+",");
		}
		writeTofile(Topic0, "Topic 0");
		writeTofile(Topic1, "Topic 1");
		writeTofile(Topic2, "Topic 2");
		writeTofile(Topic3, "Topic 3");
		writeTofile(Topic4, "Topic 4");
		writeTofile(Topic5, "Topic 5");
		writeTofile(Topic6, "Topic 6");
		writeTofile(Topic7, "Topic 7");
		writeTofile(Topic8, "Topic 8");
		writeTofile(Topic9, "Topic 9");
		writeTofile(Topic10, "Topic 10");
		writeTofile(Topic11, "Topic 11");
		writeTofile(Topic12, "Topic 12");
		writeTofile(Topic13, "Topic 13");
		writeTofile(Topic14, "Topic 14");
		writeTofile(Topic15, "Topic 15");
		writeTofile(Topic16, "Topic 16");
		writeTofile(Topic17, "Topic 17");
		writeTofile(Topic18, "Topic 18");
		writeTofile(Topic19, "Topic 19");



	}
	public static String Parsing(String data){
		String[] splits = data.split(" ");
		return splits[0];
	}
	public static void writeTofile(StringBuffer content, String topicIndex) throws IOException{
		// before we open the file check to see if it already exists
		File file = new File("out.csv");
		
		if(!file.exists()){
			file.createNewFile();
		}
		
		PrintWriter bwr = new PrintWriter(new FileWriter(file, true));

		// write contents of StringBuffer to a file
		bwr.println(topicIndex+","+content);

		// flush the stream
		bwr.flush();

		// close the stream
		bwr.close();

		System.out.println("Content of written to File.");
		
	}

}
