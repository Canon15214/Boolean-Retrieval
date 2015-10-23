/** 
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.HashMap;
import java.util.Set;

/**
 *  The root class of all query operators that use a retrieval model
 *  to determine whether a query matches a document and to calculate a
 *  score for the document.  This class has two main purposes.  First, it
 *  allows query operators to easily recognize any nested query
 *  operator that returns a document scores (e.g., #AND (a #OR(b c)).
 *  Second, it is a place to store data structures and methods that are
 *  common to all query operators that calculate document scores.
 */
public abstract class QrySop extends Qry {
	
  /**
   * This stores the query term frequencies to help in generate the query model
   */
  HashMap<Qry, Integer>  queryTermFrequencies = new HashMap<Qry, Integer>();
  protected double defaultScore = Double.MIN_VALUE;

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public abstract double getScore (RetrievalModel r)
    throws IOException;

  /**
   * A default score for the probabilistic Indri ranked retrieval model
   */
  public abstract double getDefaultScore(RetrievalModel r, int docid)
  	throws IOException;
  
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize(RetrievalModel r) throws IOException {
    for (Qry q_i: this.args) {
      q_i.initialize (r);
    }
  }
  
	
	/**
	 * For use in a HashMap
	 */
  public boolean equals(Object obj){
      if(!(obj instanceof QrySop))   return false; 
      QrySop that = (QrySop) obj;
      return this.args.equals(that.args);
   }
   
   public int hashCode(){
       return this.args.hashCode();
   }
   
	/**
	 * Methods for updating and retrieving query term frequencies 
	 */
	
	  public void addQuery(Qry q){
		  if(this.queryTermFrequencies.containsKey(q)){
			  this.queryTermFrequencies.put(q, this.queryTermFrequencies.get(q) + 1);
		  }
		  
		  else{
			  this.queryTermFrequencies.put(q, 1);
		  }
	  }

	  public int getQueryFrequency(Qry q){
		  if(this.queryTermFrequencies.containsKey(q))
			  return this.queryTermFrequencies.get(q);
		  
		  else
			  return -1;
	  }
	  
	  public Set<Qry> getQueries(){
		  return this.queryTermFrequencies.keySet();
	  }
	  
	  public void clearQueries(){
		  this.queryTermFrequencies.clear();
	  }
}
