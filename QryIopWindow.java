/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The WINDOW operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {

	/**
	 *  This query operator has a parameter (eg. 2 in #WINDOW/2), that 
	 *  is not considered an argument.
	 */
	protected int maxDistance;

	/**
	 *  Custom constructor
	 */
	public QryIopWindow(int distance){
		this.maxDistance = distance; 
	}

	/**
	 *  Evaluate the query operator; the result is an internal inverted
	 *  list that may be accessed via the internal iterators.
	 *  @throws IOException Error accessing the Lucene index.
	 */
	protected void evaluate () throws IOException {

		//  Create an empty inverted list.  If there are no query arguments,
		//  that's the final result.

		this.invertedList = new InvList (this.getField());

		if (args.size () == 0)	return;

		//  Each pass of the loop adds 1 document to result inverted list
		//  until all of the argument inverted lists are depleted.

		while (true) {
			//  Find the minimum next document id that contains all arguments.
			// This is the condition for a document to be in the result set.
			int minDocid = Qry.INVALID_DOCID;
			boolean allMatch = false;
			while(!allMatch){
				allMatch = true;
				for (int i=0; i<this.args.size(); i++) {
					Qry q_i = this.args.get(i);
					if(i > 0 && minDocid != Qry.INVALID_DOCID)
						q_i.docIteratorAdvanceTo(minDocid);
					if (q_i.docIteratorHasMatch (null)) {

						int q_iDocid = q_i.docIteratorGetMatch ();
						if(i == 0)
							minDocid = q_iDocid;
						if(i > 0 && minDocid != q_iDocid)	{
							this.args.get(0).docIteratorAdvancePast(minDocid);
							allMatch = false;
							break;
						}
					}
					else{
						allMatch = false;
						minDocid = Qry.INVALID_DOCID;
						break;
					}
				}

				if (minDocid == Qry.INVALID_DOCID)
					break;				// All docids have been processed.  Done.
			}

			if (minDocid == Qry.INVALID_DOCID)
				break;				// All docids have been processed.  Done.

			
			// Now build the inverted list for this document
			List<Integer> positions = new ArrayList<Integer>();
			// postings for all the arguments
			List<Vector<Integer>> postingsLists = new ArrayList<Vector<Integer>>();
			for (Qry q_i: this.args) {
				assert q_i.docIteratorHasMatch (null) 
					&& q_i.docIteratorGetMatch () == minDocid ;
				Vector<Integer> locations_i =
						((QryIop) q_i).docIteratorGetMatchPosting().positions;
				postingsLists.add(locations_i);
				q_i.docIteratorAdvancePast (minDocid);
			}
			getValidWindowPositions(positions, postingsLists);
			Collections.sort (positions);
			if(positions.size() > 0)	this.invertedList.appendPosting (minDocid, positions);
		}
	}

	private void getValidWindowPositions(List<Integer> positions,
			List<Vector<Integer>> postingsLists) {
		
		List<ListIterator<Integer>> postingsIterators = new ArrayList<ListIterator<Integer>>();
		for(Vector<Integer> postings : postingsLists)
			postingsIterators.add(postings.listIterator());
		int numPostings = postingsLists.size();

		int minIndex, position;
		boolean postingEmpty = false;
		boolean matchFound = true;
		minIndex = position = 0;		// initial values don't matter
		List<Integer> locations = new ArrayList<Integer>();
		
		while(!postingEmpty){
			// advance all iterators or only the one with minimum index based on matchFound
			for(int i=0; i<numPostings; i++){
				
				if(!matchFound && i != minIndex)	continue;
				ListIterator<Integer> it = postingsIterators.get(i);
				if(!it.hasNext()){
					postingEmpty = true;
					break;
				}
				position = it.next();
				if(matchFound)
					locations.add(position);
				
				else
					locations.set(minIndex, position);
				
			}
			
			if(locations.size() < numPostings)	break;
			
			int maxLoc = Collections.max(locations);
			int minLoc = Collections.min(locations);
			minIndex = locations.indexOf(minLoc);
			int windowSize = maxLoc - minLoc + 1;
			matchFound = windowSize <= this.maxDistance ? true : false;
			
			if(matchFound){
				positions.add(maxLoc);
				locations.clear();
			}
		}
	}
}
