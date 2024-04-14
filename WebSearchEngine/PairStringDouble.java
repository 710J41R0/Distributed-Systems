/*
 * Proyecto Final
 * Jairo Soto Yañez
 * 7CM3
 */

package com.mycompany.app;

public class PairStringDouble {
    private String word;
    private double value;
    
    public PairStringDouble(String word, double value){
        this.value = value;
        this.word = word;
    }

    public String getWord(){
        return this.word;
    }

    public double getValue(){
        return this.value;
    }

    @Override
    public String toString() {
        return String.format("(%s,%f)", this.word, this.value);
    }
}
