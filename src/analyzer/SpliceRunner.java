package analyzer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import analyzer.RefSeq.PullRegionsFromRef;
import analyzer.RefSeq.RefSeqParser;
import analyzer.Utilities.Utilities;
import analyzer.annovarParsers.GeneParser;
import analyzer.annovarParsers.GeneralAnnotationParser;
import analyzer.databaseRunners.AnnovarRunner;
import analyzer.databaseRunners.MESRunner;
import analyzer.databaseRunners.rpsBlastRunner;
import analyzer.fileWriters.TSVWriter;
//import analyzer.fileWriters.VCFWriter;
import analyzer.fileWriters.annovarWriter;
import analyzer.variantInfo.Variant;
//import htsjdk.variant.variantcontext.VariantContextBuilder;
//import analyzer.transcriptInfo.CDS;
import net.sourceforge.argparse4j.inf.Namespace;

/*
 * This class is used to run the splice detection algorithm. 
 */
public class SpliceRunner {

    private String annovar;                // Path to annovar 
    private String input;                  // Input file in vcf file 
    private String human;                  // Path to the humandb to be used by annovar 
    private String outputFolder;           // Directory to save outputfiles to
    private String ref;                    // Path to the directory containing the UCSC reference genome
    private String refSeq;                 // Path to the file that contains the refseq data 
    private TreeMap<String,Variant> vars;  // A map of the splicing variants. keys = chromsome:position, values = splicing variant. 
    private String SamtoolsPath;           // Path to Samtools 
    private String algorithm;              // The algorithm variable passed from splice engine. 
    private String algorithmPath;          // Path to the algorithm directory 


    //-------------------------------------------------------------------------------------
    // Constructor method 
    //-------------------------------------------------------------------------------------
    public SpliceRunner(Namespace res, String algorithm){

        this.annovar = res.getString("Annovar");
        this.input = res.getString("Input");
        this.human = res.getString("human");
        this.outputFolder = res.getString("Output");
        if (!this.outputFolder.endsWith("/")){
            StringBuilder sb = new StringBuilder(this.outputFolder+"/");
            this.outputFolder = sb.toString();
        }
        this.ref = res.getString("Ref");
        this.refSeq = res.getString("analyzer/RefSeq");
        this.SamtoolsPath = res.getString("Samtools");
        this.algorithm = algorithm;
        this.algorithmPath = res.getString("AlgorithmPath");

    }

    //-------------------------------------------------------------------------------------
    // New toString method 
    //-------------------------------------------------------------------------------------
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("analyzer.SpliceRunner{" +
                "annovar='" + annovar + '\'' +
                ", input='" + input + '\'' +
                ", human='" + human + '\'' +
                ", outputFolder='" + outputFolder + '\'' +
                ", ref='" + ref + '\'' +
                ", refSeq='" + refSeq + "\'vars=");
//        for(Map.Entry<String, Variant> entry : vars.entrySet()){
//            sb.append("\n\tkey= "+entry.getKey()+"\t"+entry.getValue().toString());
//        }

        sb.append(", SamtoolsPath='" + SamtoolsPath + '\'' +
                ", algorithm='" + algorithm + '\'' +
                '}');
        return sb.toString();
    }

    //-------------------------------------------------------------------------------------
    // Method used to run the splice site algorithm
    //-------------------------------------------------------------------------------------
    public void run() throws Exception{
        String varfunc = convertAnnovar(); // Convert input VCF to annovar format and run annovar on it. (Returns the file name of the file, containing annotated variance, created by annovar)
        String newFile = geneBasedAnnotation(varfunc); // SETS VARS / Parse the annotated file. (Returns the file name of a "splice file" containing splice variants) 
        runAnnotations(newFile); // Runs annovar 3 times to get 3 different conserved scores. New information is added to the "vars" map.
        RefSeqParser rsp = new RefSeqParser(this.refSeq); //Parses the refseq data file. (2 files created) 
        PullRegionsFromRef prfr = new PullRegionsFromRef(ref,SamtoolsPath);  //hg19 / Sam tools
        Iterator<Map.Entry<String,Variant>> iter = this.vars.entrySet().iterator();
        rpsBlastRunner rpsRunner = new rpsBlastRunner(outputFolder);

        // Create new files
        //VCFWriter vw = new VCFWriter(new File(this.outputFolder+"MaxEntScan_Filtered.vcf"),new File(this.ref+"hg19.fa"));
        //VCFWriter sig_vw = new VCFWriter(new File(this.outputFolder+"MaxEntScan_Significant.vcf"),new File(this.ref+"hg19.fa"));
        //VCFWriter possiblySig_vw = new VCFWriter(new File(this.outputFolder+"MaxEntScan_PossiblySignificant.vcf"),new File(this.ref+"hg19.fa"));
        //VCFWriter notSig_vw = new VCFWriter(new File(this.outputFolder+"MaxEntScan_NonSignificant.vcf"),new File(this.ref+"hg19.fa"));

        TSVWriter sig_tsv = new TSVWriter(this.outputFolder+"SpliceVariantResults.tsv");
        
        System.out.println(Utilities.GREEN+"Going through the variants\n"+ Utilities.RESET);

        while(iter.hasNext()){ //iterate over keys in the vars map
            Map.Entry<String,Variant> entry = iter.next();
            Variant var = entry.getValue();
            var.parseSpliceInfo(rsp, prfr);

          //Run MES and set scores for each variant
            MESRunner mr = new MESRunner(var,this.outputFolder, this.algorithmPath);  
            if (!mr.IsEmpty()){ // ONLY IF A VALID MES RUN
                //populate percentDiffList
            	var.checkMesSignificance(); 
            //}
            
            //Run NNSplice and Human Splicing Finder
            
            //Run PDB site losses
            
            //Run rpsblast through Cdd Database and find all conserved domains
            rpsRunner.runRPSBlast(var);
            
            // WRITE OUT RESULTS
            sig_tsv.writeVariant(var);
            } // inside of valid Max Ent Scan only for now!
            iter.remove();
        }

        sig_tsv.close();
        
        //sig_vw.close();
        //notSig_vw.close();
        //possiblySig_vw.close();
        //vw.close();
    }

    private void runAnnotations(String newFile) {
        AnnovarRunner AR = new AnnovarRunner(this.annovar, this.outputFolder);

        String gerp = AR.Gerp2(newFile, this.human);
        GeneralAnnotationParser parser = new GeneralAnnotationParser(this.outputFolder+gerp, true);
        this.vars = parser.parse(this.vars);

//        String phastCons = AR.PhastCons(newFile, this.human);
//        parser = new GeneralAnnotationParser(this.outputFolder+phastCons, false);
//        this.vars = parser.parsePhastCons(this.vars);

        String oneKGenomes = AR.onekGenomes(newFile,this.human);
        parser = new GeneralAnnotationParser(this.outputFolder+oneKGenomes,true);
        this.vars = parser.parse(this.vars);

        String exac = AR.Exac(newFile,this.human);
        parser = new GeneralAnnotationParser(this.outputFolder+exac, true);
        this.vars = parser.parse(this.vars);
        
    }

    private String convertAnnovar(){
        AnnovarRunner AR = new AnnovarRunner(this.annovar,this.outputFolder);
        String avinput = AR.convert2Annovar(this.input);
        String varfunc = AR.Gene(avinput,this.human);
        return varfunc;
    }

    private String writeNewAvinput() {
        try {
            String filename = "SpliceOnly.avinput";
            File newavinput = new File(this.outputFolder + filename);
            FileWriter writer = new FileWriter(newavinput);
            for (Map.Entry<String, Variant> entry : this.vars.entrySet()) {
                String key = entry.getKey();
                String[] chrpos = key.split(":");
                Variant v = entry.getValue();

                writer.write(chrpos[0] + "\t" + chrpos[1] + "\t" + chrpos[1] + "\t" + v.getRef() + "\t" + v.getAlt() + "\n");
            }
            writer.close();

            return filename;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String geneBasedAnnotation(String varfunc){
        annovarWriter nonSplice = new annovarWriter(this.outputFolder+"NonSplice.txt");
        GeneParser gp = new GeneParser(this.outputFolder+varfunc);
        this.vars = gp.parse(nonSplice);
        return writeNewAvinput();
    }
}


//USED TO BE IN mr.IsEmpty() if statement:
//DEPRECATED
/*
int sig = var.checkMesSignificance();
if(sig == 2){
    System.out.println(Utilities.GREEN+"significant difference\n"+ Utilities.RESET);
    VariantContextBuilder vcb = var.createVariantContext();
    vcb.attribute("MesScore",mr.getScores());
    sig_vw.writeVar(vcb.make());
}
else if(sig == 1){
    System.out.println(Utilities.GREEN+"possibly significant difference\n"+ Utilities.RESET);
    VariantContextBuilder vcb = var.createVariantContext();
    vcb.attribute("MesScore",mr.getScores());
    possiblySig_vw.writeVar(vcb.make());
}
else if(sig == 0){
    System.out.println(Utilities.GREEN+"not significant difference\n"+ Utilities.RESET);
    VariantContextBuilder vcb = var.createVariantContext();
    vcb.attribute("MesScore",mr.getScores());
    notSig_vw.writeVar(vcb.make());
}
*/