/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The NEAR operator for all retrieval models.
 */
public class QryIopNear extends QryIop {

	/**
	 *  This query operator has a parameter (eg. 2 in #NEAR/2), that 
	 *  is not considered an argument.
	 */
	protected int maxDistance;

	/**
	 *  Custom constructor
	 */
	public QryIopNear(int distance){
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

			List<Integer> positions = new ArrayList<Integer>();
			// postings for all the arguments
			List<Vector<Integer>> postingsLists = new ArrayList<Vector<Integer>>();
			for (Qry q_i: this.args) {
				if (!q_i.docIteratorHasMatch (null) ||
						q_i.docIteratorGetMatch () != minDocid) {
					System.out.println("Unexpected: No common docid was found");  
				}
				Vector<Integer> locations_i =
						((QryIop) q_i).docIteratorGetMatchPosting().positions;
				postingsLists.add(locations_i);
				q_i.docIteratorAdvancePast (minDocid);
			}
			getValidNearPositions(positions, postingsLists);
			Collections.sort (positions);
			this.invertedList.appendPosting (minDocid, positions);
		}
	}

	private void getValidNearPositions(List<Integer> positions,
			List<Vector<Integer>> postingsLists) {

		List<ListIterator<Integer>> postingsIterators = new ArrayList<ListIterator<Integer>>();
		for(Vector<Integer> postings : postingsLists)
			postingsIterators.add(postings.listIterator());
		int numPostings = postingsLists.size();

		while(true){
			boolean postingEmpty = false; 
			boolean matchFound = true;
			Integer prevPosition = QryIop.INVALID_ITERATOR_INDEX;
			int[] stepsMoved = new int[numPostings];
			for(int i=0;i<numPostings; i++){
				ListIterator<Integer> it = postingsIterators.get(i);
				if(!it.hasNext()){
					postingEmpty = true;
					break;
				}
				int position = it.next();
				stepsMoved[i] = 1;
				if(i == 0)
					prevPosition = position;
				else{
					// order
					while(position <= prevPosition && it.hasNext()){
						position = it.next();
						stepsMoved[i]++;
					}
					// order and distance
					if(position > prevPosition && position -  prevPosition <= this.maxDistance)	{
						prevPosition = position; 
					}
					else{

						matchFound = false;
						for(int k=1;k<=i; k++){
							ListIterator<Integer> pit = postingsIterators.get(k);
							for(int m=0; m<stepsMoved[k]; m++)	pit.previous();
						}
						break;
					}
				}

			}
			if(postingEmpty)	break;
			if(matchFound)	positions.add(prevPosition);
		}
	}

}
