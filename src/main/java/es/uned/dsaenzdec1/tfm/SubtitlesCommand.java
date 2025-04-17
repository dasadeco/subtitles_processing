package es.uned.dsaenzdec1.tfm;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
@Command(name="DelayOrAdvanceSubtitles", version="1.0", mixinStandardHelpOptions = true, description="Pequeño programa que inicialmente sirve para avanzar o retrasar subtitulos")
public class SubtitlesCommand implements Callable<Integer> {

    @Option(names = { "-i", "--filenameIn" }, paramLabel = "ARCHIVO DE SUBTITULOS DE ENTRADA", description = "Archivo con subtitulos de entrada")
    File filenameIn;
    
    @Option(names = { "-o", "--filenameOut" }, paramLabel = "ARCHIVO DE SUBTITULOS DE SALIDA", description = "Archivo de salida con subtitulos procesados")
    File filenameOut;
    
    @Option(names={"-s", "--seconds"}, paramLabel="SEGUNDOS", description="Cantidad de segundos de adelanto o de retraso")
    Optional<Long> seconds;
    
    @Option(names={"-d", "--direction"}, paramLabel="DIRECCION", description="Si los subtitulos se avanzan o se retrasan") 
    Optional<Direction> advanceDelay;
    
	private enum Direction {
		advance,
		delay;		
	}	      	    
	    
    private String calculateDelayOrAdvanceTime(String timeStr, long seconds, Direction delayOrAdvance) {
    	DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");
    	LocalTime localTime = LocalTime.parse(timeStr, dtFormatter);	    	
    	if (delayOrAdvance.equals(Direction.delay)) { 	//retrasar el subtitulo implica sumar "n" segundos al tiempo	    		
    		localTime = localTime.plusSeconds(seconds);
    	}else{											//adelantar el subtitulo implica restar "n" segundos al tiempo
    		localTime = localTime.minusSeconds(seconds);
    	}
    	return localTime.format(dtFormatter);	    		
	}
		
	/**
	 * Crea una linea de rango temporal del subtítulo la cual tiene el formato:
	 *      hh:mm:ss,zzz --> hh:mm:ss,zzz  
	 *(Horas_pelicula:minutos_pelicula:segundos,milisegundos INICIO --> Horas_pelicula:minutos_pelicula:segundos,milisegundos FINAL) 
	 * @param matcher Patrón con  expresiones regulares de una linea con la anterior
	 * @param seconds
	 * @param delayOrAdvance
	 * @return
	 */		
    private String createTimeIntervalLine(Matcher matcher, long seconds, Direction delayOrAdvance) {
    	return new StringBuffer( calculateDelayOrAdvanceTime(matcher.group(1), seconds, delayOrAdvance))
    			.append(matcher.group(2))
    			.append(calculateDelayOrAdvanceTime(matcher.group(3), seconds, delayOrAdvance)).toString();	    			
    }	 		

	    
	/** TODO: Que admita segundos negativos en args[2], con lo que args[3] se ignorarán		
		TODO: Nuevos parámetros args[4] y args[5] para delimitar un rango de tiempo en formato HH:MM:SS 
		en el que se aplicará la modificación.
	 */ 
	@Override
	public Integer call() throws Exception {
        String line = null;
        FileReader reader = null;
        FileWriter writer = null;
		try {
			reader = new FileReader(filenameIn);
			filenameOut.createNewFile();				
			writer = new FileWriter(filenameOut);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*
		 * It depends on the srt file's size
		 */
        BufferedReader buffer = new BufferedReader(reader, 1000);
        BufferedWriter bufferWriter = new BufferedWriter(writer, 1000);
        Pattern pattern = Pattern.compile("(\\d{2}:\\d{2}:\\d{2},\\d{3})(\\s-->\\s)(\\d{2}:\\d{2}:\\d{2},\\d{3})");
        int linesModified = 0, linesNotModified = 0;
        if (seconds.isPresent() && advanceDelay.isPresent())
	        try{
	            do { 
	                    line = buffer.readLine();
	                    if (line!=null){
		                    Matcher matcher = pattern.matcher(line);
		                    if (matcher.matches()){
		                    	System.out.println("line is matched:"+line);
		                    	line = createTimeIntervalLine(matcher, seconds.get(), advanceDelay.get());
		                    	linesModified++;
		                    }else{
		                    	linesNotModified++;
		                    }
	                    	bufferWriter.write(line);
	                    	bufferWriter.newLine();
	                    }

	             }while (line!=null);
	        } catch (IOException ioe) {
	            System.out.println(ioe.getMessage());
	        }  finally {
	            try {
	            	bufferWriter.close();
					buffer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
	        
	            System.out.println("Lineas modificadas: "+linesModified);
	            System.out.println("Lineas no modificadas: "+linesNotModified);
	        }	    
		return 0;
	}
		
}