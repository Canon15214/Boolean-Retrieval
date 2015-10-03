import java.util.HashMap;
import java.util.Set;

/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the BM25 ranked
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {

  /**
   * Parameters for the model
   */
	
  public double k_1;
  public double b;
  public double k_3;
  private HashMap<Qry, Integer>  queryFrequencies;
	
  /**
   * Custom constructor
   */
  public RetrievalModelBM25(double k_1, double b, double k_3){
	  this.k_1 = k_1;
	  this.b = b;
	  this.k_3 = k_3;
	  this.queryFrequencies = new HashMap<Qry, Integer>();
  }
  
  public String defaultQrySopName () {
    return new String ("#sum");
  }
  
  public void addQuery(Qry q){
	  if(this.queryFrequencies.containsKey(q))
		  this.queryFrequencies.put(q, this.queryFrequencies.get(q) + 1);
	  else
		  this.queryFrequencies.put(q, 1);
  }

  public int getQueryFrequency(Qry q){
	  if(this.queryFrequencies.containsKey(q))
		  return this.queryFrequencies.get(q);
	  else
		  return 0;
  }
  
  public Set<Qry> getQueries(){
	  return this.queryFrequencies.keySet();
  }
  
}
