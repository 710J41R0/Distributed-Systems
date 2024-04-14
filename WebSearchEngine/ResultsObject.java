/*
 * Proyecto Final
 * Jairo Soto Yañez
 * 7CM3
 */

package com.mycompany.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

class SortbyTfIdf implements Comparator<ResultsObject> {
    final double TOP = 1000000000;
    public int compare(ResultsObject r1, ResultsObject r2) {
        double tmp1 = r1.getTfIdf(), tmp2 = r2.getTfIdf();
        return (int) (tmp2*TOP - tmp1*TOP);
    }
}

public class ResultsObject {
    private String filename;
    private ArrayList<PairStringDouble> searchingWordResults;
    private double tf_idf;
    
    public ResultsObject(String filename, String wordResults){
        this.filename = filename;
        this.tf_idf = 0;
        this.searchingWordResults = fillSearchingWordResults(wordResults);
    }

    public void calculateTfIdf(HashMap<String, Long> reference, int noDocs){
        double result = 0;
        System.out.println("Calculado TF-IDF de " + this.filename + "\n");
        for (PairStringDouble currPair : searchingWordResults) {
            System.out.print("\nResult = " + currPair.getValue() + " * Log10(" + (double)noDocs + "/" + reference.get(currPair.getWord()) + ") = ");
            result += currPair.getValue() * Math.log10((double)noDocs/reference.get(currPair.getWord()));
            System.out.print(result + "\t");
        }
        System.out.println("End");
        this.tf_idf = result;
    }
    
    public ArrayList<PairStringDouble> fillSearchingWordResults(String wordResults){
        final int WORD_POS = 0, TF_POS = 1;
        ArrayList<PairStringDouble> res = new ArrayList<>();
        String[] currRow;
        
        for (String data : wordResults.split(" ")) {
            currRow = data.split(":");
            PairStringDouble currPair = new PairStringDouble(currRow[WORD_POS], Double.valueOf(currRow[TF_POS]));
            res.add(currPair);
        }
        return res;
    }

    // Getters
    public String getFilename(){
        return this.filename;
    }
    
    public double getTfIdf(){
        return this.tf_idf;
    }
    
    public ArrayList<PairStringDouble> getSearchingWordResults(){
        return this.searchingWordResults;
    }
    
    // Setters
    public void setTf_Idf(double tf_idf){
        this.tf_idf = tf_idf;
    }
    
    @Override
    public String toString() {
        return "FileName: "+ filename + "\nTf-Idf: " + tf_idf + "\n\n";
    }
}
