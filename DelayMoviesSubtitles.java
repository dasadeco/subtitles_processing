import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DelayMoviesSubtitles {

	private final static String ADVANCE = "advance";
	private final static String DELAY = "delay";
	 
    
		/**
		 * Crea una linea de rango temporal del subtítulo la cual tiene el formato:
		 *      hh:mm:ss,zzz --> hh:mm:ss,zzz  
		 *(Horas_pelicula:minutos_pelicula:segundos,milisegundos INICIO --> Horas_pelicula:minutos_pelicula:segundos,milisegundos FINAL) 
		 * @param matcher Patrón expresiones regulares de una linea con la anterior
		 * @param seconds
		 * @param delayOrAdvance
		 * @return
		 */		
	    private String createTimeIntervalLine(Matcher matcher, long seconds, String delayOrAdvance) {
	    	return new StringBuffer( calculateDelayOrAdvanceTime(matcher.group(1), seconds, delayOrAdvance))
	    			.append(matcher.group(2))
	    			.append(calculateDelayOrAdvanceTime(matcher.group(3), seconds, delayOrAdvance)).toString();	    			
	    }	    
	    
	    
	    private String calculateDelayOrAdvanceTime(String timeStr, long seconds, String delayOrAdvance) {
	    	DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");
	    	LocalTime localTime = LocalTime.parse(timeStr, dtFormatter);	    	
	    	if (delayOrAdvance.equals(DELAY)) { //retrasar el subtitulo implica sumar "n" segundos al tiempo	    		
	    		localTime = localTime.plusSeconds(seconds);
	    	}else{				//adelantar el subtitulo implica restar "n" segundos al tiempo
	    		localTime = localTime.minusSeconds(seconds);
	    	}
	    	return localTime.format(dtFormatter);	    		
		}
		
		
		/** TODO: Que admita segundos negativos en args[2], con lo que args[3] se ignorará		
			TODO: Nuevos parámetros args[5] y args[6] para delimitar un rango de tiempo en formato HH:MM:SS 
			en el que se aplicará la modificación
		 */ 
		/**		
		 * @doc args[0] : Nombre de archivo de entrada de Subtitulos STRING
		 * @doc args[1] : Nombre de archivo de salida de Subtitulos ( el de entrada ya modificado) STRING
		 * @doc args[2] : Segundos a adelantar o atrasar los subtitulos del archivo INTEGER		 
		 * @doc args[3] : adelantar o retrasar ("advance" o "delay") 
		 * @param args  
		 */
			    public DelayMoviesSubtitles(String[] args) {
			        String filenameIn = args[0];
			        String filenameOut = args[1];
			        String secondsStr = args[2];
			        String advanceDelay = args[3];
			        String line = null;
			        FileReader reader = null;
			        FileWriter writer = null;
					try {
						reader = new FileReader(filenameIn);
						File file = new File(filenameOut);
						file.createNewFile();				
						writer = new FileWriter(file);
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
			        int linesModified =0, linesNotModified=0;
			        long seconds = Long.parseLong((secondsStr==null || secondsStr.equals("")) ? "0" : secondsStr);
			        try{
			            do { 
			                    line = buffer.readLine();
			                    if (line!=null){
				                    Matcher matcher = pattern.matcher(line);
				                    if (matcher.matches()){
				                    	System.out.println("line is matched:"+line);
				                    	line = createTimeIntervalLine(matcher, seconds, advanceDelay);
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
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			            System.out.println("Lineas modificadas: "+linesModified);
			            System.out.println("Lineas no modificadas: "+linesNotModified);
			        }
			    }    


				/**
			     * @param args the command line arguments
			     */
			    public static void main(String[] args) {
			    	new DelayMoviesSubtitles(args);
			    }		
}