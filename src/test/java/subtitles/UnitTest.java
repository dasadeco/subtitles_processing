package subtitles;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import es.uned.dsaenzdec1.tfm.RttmCommand.FormasHablar;
import es.uned.dsaenzdec1.tfm.helper.LabelerHelper;

public class UnitTest {
			
	
	Pattern speakerPattern = Pattern.compile("\\([A-Za-z0-9\\s\\-_\\,\\.\\:]+\\).*");
	
	@Test
	public void testSpeakerMatcher (){				
        String line = null;
        FileReader reader = null;
		try {
			reader = new FileReader("E:\\Universidad\\UNED_MasterCienciaDatos_2022\\TFM\\Propuesta_TFM_Subtitulado\\Datasets\\subtitles\\canal.uned\\Test.ES.txt");				
		} catch (FileNotFoundException e1) {
			assert Boolean.TRUE; // que no falle el test por no encontrar el archivo
			return;
		}
		BufferedReader buffer = new BufferedReader(reader, 1000);				
        do { 
            try {
				line = buffer.readLine();
			} catch (IOException e) {
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
        try {
			buffer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	@Test
	public void testFileWalker (){
        Path start = Paths.get("./data/subtitles");
        
        try {
            Files.walk(start)
                 .forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

	
	@Test
	public void testLabel() throws IOException {
		File subtitleIn = new File("E:\\Universidad\\UNED_MasterCienciaDatos_2022\\TFM\\Propuesta_TFM_Subtitulado\\Datasets\\subtitles\\TEST\\4ºESO+Empresa en la Escuela, 2023.vtt");
		File rttmIn = new File("E:\\Universidad\\UNED_MasterCienciaDatos_2022\\TFM\\Propuesta_TFM_Subtitulado\\Datasets\\backup_rttms\\4ºESO+Empresa_en_la_Escuela,_2023.rttm");
		LabelerHelper labelerHelper = new LabelerHelper();
		String filenameWithoutExtension = subtitleIn.getName().substring(0, subtitleIn.getName().lastIndexOf(".")).replace(" ", "_");
		String extension = subtitleIn.getName().substring(subtitleIn.getName().lastIndexOf(".") + 1);
		File filenameOut = new File(subtitleIn.getParent(), filenameWithoutExtension + "_labeled." + extension );
		filenameOut.createNewFile();				
		FileWriter labeledSubtitleWriter = new FileWriter(filenameOut);					
		BufferedReader bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
		BufferedReader bufferRttmIn = new BufferedReader(new FileReader(rttmIn), 1000);
		BufferedWriter bufferLabelWriter = new BufferedWriter(labeledSubtitleWriter, 1000);
		int result = labelerHelper.doLabelSubtitle(bufferSubtitleIn, bufferRttmIn, bufferLabelWriter, Boolean.TRUE);		
		changeLabelUnlabelFile(result, subtitleIn, bufferSubtitleIn, bufferLabelWriter, filenameOut, "");
	}
	
	
	@Test
	public void testUnlabel() throws IOException {
		File subtitleIn = new File("E:\\Universidad\\UNED_MasterCienciaDatos_2022\\TFM\\Propuesta_TFM_Subtitulado\\Datasets\\subtitles\\TEST\\4ºESO+Empresa en la Escuela, 2023.vtt");
		LabelerHelper labelerHelper = new LabelerHelper();
		String filenameWithoutExtension = subtitleIn.getName().substring(0, subtitleIn.getName().lastIndexOf(".")).replace(" ", "_");
		String extension = subtitleIn.getName().substring(subtitleIn.getName().lastIndexOf(".") + 1);
		File filenameOut = new File(subtitleIn.getParent(), filenameWithoutExtension + "_unlabeled." + extension );
		filenameOut.createNewFile();				
		FileWriter labeledSubtitleWriter = new FileWriter(filenameOut);					
		BufferedReader bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
		BufferedWriter bufferLabelWriter = new BufferedWriter(labeledSubtitleWriter, 1000);
		int result = labelerHelper.doUnlabelSubtitle(bufferSubtitleIn, bufferLabelWriter);
		System.out.println(result);		
		changeLabelUnlabelFile(result, subtitleIn, bufferSubtitleIn, bufferLabelWriter, filenameOut, "(des)");	
	}	
	
	private void changeLabelUnlabelFile(int result, File subtitleIn, BufferedReader bufferSubtitleIn, BufferedWriter bufferLabelWriter, File filenameOut, String unlabel) throws IOException {
		if (result == 0) {
			System.out.println(String.format("Se ha realizado el %setiquetado correcto de %s", unlabel, subtitleIn.toString()));
			bufferSubtitleIn.close();
			Files.delete(subtitleIn.toPath());						
			bufferLabelWriter.close();
			Files.move(filenameOut.toPath(), subtitleIn.toPath());
		}else {       //result == 1--> KO
			System.out.println(String.format("ALGO HA FALLADO en el %setiquetado de %s",  unlabel, subtitleIn.toString()));
		}		
	}
	
	
	@Test
	public void walkingDead() throws IOException{
		Path rttmsPath = Paths.get("./media/rttm");
		Files.walk(rttmsPath, 1).filter(path -> Files.isDirectory(path) && !path.getFileName().toString().equals("rttm"))
		.forEach(datasetPath -> {
			try {
				Files.walk(datasetPath, 2).filter(rttmFile -> rttmFile.toString().endsWith(".rttm"))
						.forEach(rttmFile -> {
							System.out.println("with dataset..." + rttmFile.toAbsolutePath());
						});
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		
	}
		
}