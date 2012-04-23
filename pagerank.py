#/usr/bin/python2.4
#
#

import psycopg2
from time import time

default_pr = 1.0
pr_total=170215.0 *default_pr

rounds=0


# Try to connect
conn=psycopg2.connect("dbname='' user='' password='' host='localhost' port=5433")
cur = conn.cursor()

#Get all unique papers
cur.execute("SELECT * from pagerank_tmp")
papers = cur.fetchall()

#In-memory tables
pagerank_dict = {}
tmp_dict={}
citations_dict = {}

print "Compiling papers"
start = time()

#Store calculated pagerank within a round (node+message sim)
for paper in papers:
	tmp_dict[(paper[0],paper[1])]=0.0

#Building valid citations table. Default pagerank table with default_pr value. Store # of citations for easy of later computation
for paper in papers:
	cur.execute("select cited_paper_id, cited_proc_id from citations where citer_proc_id='"+paper[0]+"' and citer_paper_id='"+paper[1]+"' and cited_proc_id is not null and cited_paper_id is not null")
	citations_tmp = cur.fetchall()
	citations=[]
	for citation_tmp in citations_tmp:

		if citation_tmp[0]==paper[0] and citation_tmp[1]==paper[1]: continue
		if tmp_dict.has_key((citation_tmp[0],citation_tmp[1])): citations.append(citation_tmp)
	citations_dict[(paper[0],paper[1])]=citations
	pagerank_dict[(paper[0],paper[1])]=(default_pr,len(citations))


#test:
#print pagerank_dict[('371920','372150')];
#print citations_dict[('371920','372150')]
print "finish compiling at ", (time()-start)/60
print "Starting rounds"

#Pagerank iterations
while rounds<1000000:

	#Pagerank transfer (according to paper eqns)
	for paper in papers:
		(pr, num)=pagerank_dict[(paper[0],paper[1])]
		if num!=0: 
			new_pr=pr/num
			citations=citations_dict[(paper[0],paper[1])]
			for citation in citations:
				tmp_dict[(citation[0],citation[1])]+=new_pr

	tmp_sum=0
	max_diff =0

	#Assumes uniform E vector (from paper) to handle sinks.
	for paper in papers:
		tmp_dict[(paper[0],paper[1])]+=0.33
		tmp_sum+=tmp_dict[(paper[0],paper[1])]
	#Normalizing factor
	c=pr_total/tmp_sum

	#Normalize pageranks, tracking the maximum difference, and update pageranks. Reset tmp pagerank storage.
	for paper in papers:
		tmp_dict[(paper[0],paper[1])]*=c
		diff = tmp_dict[(paper[0],paper[1])]-pagerank_dict[(paper[0],paper[1])][0]
		if diff>max_diff: 
			max_diff=diff
		pagerank_dict[(paper[0],paper[1])]=(tmp_dict[(paper[0],paper[1])],pagerank_dict[(paper[0],paper[1])][1])
		tmp_dict[(paper[0],paper[1])]=0.0

	print"Finish round ",rounds," c: ",c," diffs:", max_diff, "current runtime: ", (time()-start)/60
	rounds+=1
	#Consider convergence when difference is less than 10% of default pagerank
	if max_diff<0.1: break;


print "Iterations over, updating pageranks at ", (time()-start)/60

#Update pagerank in postgres database
for paper in papers:
	cur.execute("update pagerank_tmp set pr="+str(pagerank_dict[(paper[0],paper[1])][0])+" where citer_proc_id='"+paper[0]+"' and citer_paper_id='"+paper[1]+"'")

#Commit transaction
conn.commit()
print "Pagerank complete in ",rounds," rounds in ",(time()-start)/60
