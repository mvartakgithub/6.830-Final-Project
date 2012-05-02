#/usr/bin/python2.4
#
#

import psycopg2
from time import time
from sets import Set


rounds=0


# Try to connect
conn=psycopg2.connect("dbname='' user='' password='' host='localhost' port=5433")
cur = conn.cursor()

#Get all unique papers
cur.execute("SELECT * from pagerank")
papers = cur.fetchall()

num_papers = len(papers)
print "Num papers: ", num_papers
#default_pr = 1.0
#pr_total=num_papers *default_pr


#In-memory tables
pagerank_dict = {}
tmp_dict={}
citations_dict = {}

undirected_graph_dict = {}

print "Compiling papers"
start = time()

#List of sets of componeents, each element in the list should be a set representing all papers of a distinct componeent
comp_list=[]

#Set of all papers
paper_set = Set([])

#Store calculated pagerank within a round (node+message sim)
for paper in papers:
	tmp_dict[(paper[0],paper[1])]=0.0
	paper_set.add((paper[0],paper[1]))

#Building valid citations table. Default pagerank table with default_pr value. Store # of citations for easy of later computation
for paper in papers:
	cur.execute("select cited_proc_id, cited_paper_id from citations where citer_proc_id='"+paper[0]+"' and citer_paper_id='"+paper[1]+"' and cited_proc_id is not null and cited_paper_id is not null")
	citations_tmp = cur.fetchall()
	citations=[]
	for citation_tmp in citations_tmp:

		if citation_tmp[0]==paper[0] and citation_tmp[1]==paper[1]: continue
		if tmp_dict.has_key((citation_tmp[0],citation_tmp[1])): 
			citations.append(citation_tmp)
	citations_dict[(paper[0],paper[1])]=citations
	if undirected_graph_dict.has_key((paper[0],paper[1])):
		for citation in citations:
			undirected_graph_dict[(paper[0],paper[1])].append(citation)
	else: undirected_graph_dict[(paper[0],paper[1])]=citations

	for citation in citations:
		if undirected_graph_dict.has_key((citation[0],citation[1])):
			undirected_graph_dict[(citation[0],citation[1])].append((paper[0],paper[1]))
		else: undirected_graph_dict[(citation[0],citation[1])]=[(paper[0],paper[1])]

print "Done building citation graph and undirected graph at ", (time()-start)/60


def find_comp(paper):
	rtn_set=Set([paper])
	queue = [paper]
	while len(queue)>0:
		last = queue.pop()
		nhbr_list = undirected_graph_dict[(last[0],last[1])]
		for nhbr in nhbr_list:
			if nhbr not in rtn_set: 
				queue.append(nhbr)
				rtn_set.add(nhbr)
	return rtn_set


ctr=0
while len(paper_set)>0:
	ctr+=1
	search_root = paper_set.pop()
	paper_set.add(search_root)
	rtn_set = find_comp(search_root)
	if ctr%1000==0: print ctr, len(paper_set), len(rtn_set)

	comp_list.append(rtn_set)
	paper_set=paper_set.difference(rtn_set)

print "Citations componentized at ", (time()-start)/60

print "Beginning component check"
			
#Check

pr_total = 0.0
ctr = 0
for i in range(len(comp_list)):
	tmp_pr = float(len(comp_list[i]))/float(num_papers)
	for paper in comp_list[i]:
		ctr+=1
		pagerank_dict[(paper[0],paper[1])]=(tmp_pr,len(citations_dict[(paper[0],paper[1])]))
		pr_total+=tmp_pr

if ctr!=num_papers: print "Mismatch in componentization!", ctr, num_papers
#for paper in papers:
#	found=False
#	only_one=True
#	for i in range(len(comp_list)):
#		if found==True and (paper[0],paper[1]) in comp_list[i]: 
#			only_one=False
#			break
#		if (paper[0],paper[1]) in comp_list[i]: 
#			found=True
#			pagerank_dict[(paper[0],paper[1])]=(float(len(comp_list))/float(pr_total),len(citations_dict[(paper[0],paper[1])]))
#
#	if found!=True: print "Didn't comp ", paper
#	if only_one!=True: print "Multiple comp ", paper


#test:
#print pagerank_dict[('371920','372150')];
#print citations_dict[('371920','372150')]
print "finish compiling at ", (time()-start)/60
print "Starting rounds"

#Pagerank iterations
while rounds<100:

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
		tmp_dict[(paper[0],paper[1])]+=0.1
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
	if max_diff<1: break;


print "Iterations over, updating pageranks at ", (time()-start)/60

max_pr=0
for paper in papers:
	tmp_pr=pagerank_dict[(paper[0],paper[1])][0]
	if tmp_pr>max_pr:
		max_pr=tmp_pr

#Update pagerank in postgres database
for paper in papers:
	cur.execute("update pagerank set pr="+str(pagerank_dict[(paper[0],paper[1])][0]/max_pr)+" where citer_proc_id='"+paper[0]+"' and citer_paper_id='"+paper[1]+"'")

#Commit transaction
conn.commit()
print "Pagerank complete in ",rounds," rounds in ",(time()-start)/60
