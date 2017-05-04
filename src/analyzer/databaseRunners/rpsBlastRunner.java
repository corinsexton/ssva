package analyzer.databaseRunners;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.text.DecimalFormat;

import analyzer.Utilities.Utilities;
import analyzer.variantInfo.Variant;

public class rpsBlastRunner {

    String tempfaaPath;
    String tempoutPath;

    
    public rpsBlastRunner(String outputPath) throws IOException{
        this.tempfaaPath = outputPath + "temp.faa";
        this.tempoutPath = outputPath + "temp.out";

        }
    
    public void runRPSBlast(Variant var) throws IOException {
    	
    	buildRPSQuery(var); 	
    	try {
			runRPSBlastCommand();
		} catch (Exception e) {
			e.printStackTrace();
		}
    	extractRPSBlastResults(var);
    	
    }

    public void buildRPSQuery(Variant var) throws IOException {
    
    	FileWriter tempFAA = new FileWriter(tempfaaPath);
    	StringBuilder fasta = new StringBuilder('>' + var.getChr() + ':' + var.getPos() + '\n' +
    											var.getCDSList().get(0).getOriginalProtein().substring(0, var.getCDSList().get(0).getOriginalProtein().length() - 1) //take off *
    											+ '\n');
    	tempFAA.write(fasta.toString());
    	tempFAA.close();
    }
    
    public void runRPSBlastCommand() throws Exception {
    	System.out.println(Utilities.GREEN+"Running rpsblast to find Conserved Domains"+ Utilities.RESET);
    	try {
    		String[] call = new String[]{"rpsblast", "-query", tempfaaPath, "-db", "Cdd",
    									 "-out", tempoutPath, "-evalue", ".01", "-outfmt", "6 sseqid qstart qend length evalue stitle"};
    		
    		ProcessBuilder pb = new ProcessBuilder(call);

    		Process p = pb.start();
    		p.waitFor();
    		//System.out.println(Utilities.getProcessOutput(p));
    		String error = Utilities.getProcessError(p);
    		p.destroy();
    		
    		Files.deleteIfExists(new File(tempfaaPath).toPath());
    		
    		if(!error.isEmpty()){
                throw new Exception("rpsblast threw the following error: " + error);
            }
    	} catch (IOException e) {
    		e.printStackTrace();
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    	}
    }
    
    public void extractRPSBlastResults(Variant var) throws IOException {
    	String thisLine = null;
    	
    	BufferedReader rpsResults = new BufferedReader(new FileReader(new File(tempoutPath)));
    	while ((thisLine = rpsResults.readLine()) != null) {
    		
    		//gnl|CDD|306940	419  	526 	108 	2e-07	pfam00567, TUDOR

    		String[] splitLine = thisLine.split("\t");
    		Integer cddStart = Integer.parseInt(splitLine[1]);
    		Integer cddEnd = Integer.parseInt(splitLine[2]);
    		Double percentLost;
    		
    		DecimalFormat df = new DecimalFormat("#.##");
			df.setRoundingMode(RoundingMode.CEILING);
			
    		if (cddStart >= var.WithinGenePos) { // starts after the lost splice site
    			percentLost = 100.0;
    		} else if (cddEnd >= var.WithinGenePos) { // variant within this domain
    			Double totalDomainLength = (double) (cddEnd - cddStart + 1);
    			Double lostAmount = (double) (cddEnd - var.WithinGenePos + 1);
    			percentLost = lostAmount / totalDomainLength;
    		} else {
    			percentLost = 0.0;
    		}
    		
            System.out.println(thisLine);
            System.out.println("PercentLost: " + df.format(percentLost));

         }  
    	
    	/* ROUNDING
    	 * DecimalFormat df = new DecimalFormat("#.####");
			df.setRoundingMode(RoundingMode.CEILING);
			for (Number n : Arrays.asList(12, 123.12345, 0.23, 0.1, 2341234.212431324)) {
    		Double d = n.doubleValue();
    		System.out.println(df.format(d));
			}
    	 */
		Files.deleteIfExists(new File(tempoutPath).toPath());
    	
    	rpsResults.close();
    	
    }
    
}