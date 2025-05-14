package subtitles;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import es.uned.dsaenzdec1.tfm.RttmCommand.FormasHablar;

public class UnitTest {
	
	Pattern speakerPattern = Pattern.compile("\\([A-Z\\s\\-,.\\:]+\\).*");
	
	@Test
	public void testHola (){				
        String line = null;
        FileReader reader = null;
		try {
			reader = new FileReader("E:\\Universidad\\UNED_MasterCienciaDatos_2022\\TFM\\Propuesta_TFM_Subtitulado\\Datasets\\subtitles\\canal.uned\\Test.ES.txt");				
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		BufferedReader buffer = new BufferedReader(reader, 1000);
		
		
        do { 
            try {
				line = buffer.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            if (line!=null && StringUtils.isNotBlank(line)){
            	
        		Matcher speakerMatcher = speakerPattern.matcher(line);
        		if (speakerMatcher.matches()) {
        	    	String speakerLine = speakerMatcher.group();
        	    	String speaker = speakerLine.substring(1, speakerLine.indexOf(")"));
        	    	Pattern formasHablarPattern = Pattern.compile("("+FormasHablar.getValues() +")");
        	    	Matcher formasHablarMatcher = formasHablarPattern.matcher(speaker);
        	    	StringBuffer sb = new StringBuffer();
        	    	while (formasHablarMatcher.find()) {
        	    		formasHablarMatcher.appendReplacement(sb, "");
        	    	}
        	    	formasHablarMatcher.appendTail(sb);
        	    	speaker = sb.toString();
        	    	speaker = speaker.replaceAll("[\\,|\\;|\\:|\\-]", "").trim();
        	    	
        	    	System.out.println(speaker);
        		}	
            }
        }while (line!=null);  

		
	}
}
