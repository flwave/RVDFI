import re
import sys
import math
import copy
import numpy
import random
import pickle

sfunc={'dfi_rocc_stlddebug':'','init_ori_dfi':'','dfi_rocc_debug':'','init_roccinstr_dfi':'','dfi_func_signal':'','init_soft_dfi':'','init_noinstrument_dfi':'','dfi_debug':''}

nopinst='call void asm "add x0,x0,x0", "~{dirflag},~{fpsr},~{flags}"() #2'

nopinst2='call void asm "nop", "~{dirflag},~{fpsr},~{flags}"() #2'

nonexcluded=['dfi_o3_flush']

valid_intrinsic_func=['memcpy','memset','memmove']

ts_insert_line_ratio=0.15

exfuncs=[]
sfuncfile=''

debugfunc=0

usr_rds=0
usr_rds_file=''

timestampfunc='main'
endtimestamp=0

exp_mode='noinstr'

sp_len=32
bf_len=32
max_hist=3
analysis=0
rand_del=0
rand_del_ptg=0
rand_del_file_suffix=''
show_del=0
show_del_range=0
load_del=0
load_del_name=''

hdfi_mode=0

runtoend=0

anyfunc=0
nofunc=0
noload=0
nostore=0

stldroccdebug=0

#the index is the index of the input, if we need the output, the index is the # of inputs
#d=func(a,b,c), if we need b, index=1, if we need d, index=3
#if len is a list [k], it means the usr define the length is k
#pos means the position of the instrumentation, 0 means before, 1 means after
sp_inst_funcs=[
{'name':'NA','s':[],'l':[],'len':[0],'pos':0},
{'name':'memcpy','s':0,'l':1,'len':2,'pos':0},
{'name':'memset','s':0,'l':[],'len':2,'pos':0},
{'name':'memmove','s':0,'l':1,'len':2,'pos':0},
{'name':'BIO_write','s':0,'l':1,'len':2,'pos':0},
{'name':'read_wrapper','s':1,'l':[],'len':3,'pos':0},
{'name':'longjmp','s':[],'l':0,'len':[1],'pos':0},
{'name':'strncpy','s':0,'l':1,'len':2,'pos':0},
{'name':'strncat','s':0,'l':1,'len':2,'pos':0},
]

#regular expressions of where to insert debug functions
#default: function return
insert_debug_rules=[
'^\s*ret',
'call void @mylongjmp\(',
'%\d+ = call signext i\d+ %\d+\(.*\)',
]

ret_id=0

spatt_dop=0

#user change this in this text file manually
signal_delay=0

def read_file(filename):
	f=open(filename,'r')
	text=f.readlines()
	f.close()
	return text

def parse_analysis(text):
	state=0
	idinst=[]
	rds=[]
	nid=-1
	func=0
	func_name=""
	for t in text:
		if state==0:
			if t=="============= Program with IDs =============\n":
				print "Start Parsing Program with ID"
				state=1
		elif state==1:
			if t=="============= Reaching Definition Set ===============\n":
				print "Start Parsing reaching definition set"
				state=2
		if state==1:
			if t=="\n":#next valid line is the first line of a function
				func=1
			else:
				exp="(\d+)\s+(.*)"
				r=re.findall(exp,t)
				if len(r)>0:
					if func:#this line is a function name
						exp="\d+\s+(\S+)\(.*\)"
						r2=re.findall(exp,t)
						func_name=r2[0]
						func=0
					else:
						exp2="^\s*\S+ = load .*"
						r2=re.findall(exp2,r[0][1])
						if len(r2)>0:
							idinst.append({"type":'l',"id":int(r[0][0]),"inst":r[0][1],"func":func_name,"rds":[],"addr":[],"del":0,'matched':0})
							continue
						exp2="^\s*store .*"
						r2=re.findall(exp2,r[0][1])
						if len(r2)>0:
							idinst.append({"type":'s',"id":int(r[0][0]),"inst":r[0][1],"func":func_name,"rds":[],"addr":[],"del":0,'matched':0})
							continue
						exp2="^\s*br .*"
						r2=re.findall(exp2,r[0][1])
						if len(r2)>0:
							idinst.append({"type":'br',"id":int(r[0][0]),"inst":r[0][1],"func":func_name,"rds":[],"addr":[],"del":0,'matched':0})
							continue
						exp2="^\s*\S+ = call .*|\s*call .*|\S+ = tail call .*|\s*tail call .*"
						r2=re.findall(exp2,r[0][1])
						if len(r2)>0:
							idinst.append({"type":'bc',"id":int(r[0][0]),"inst":r[0][1],"func":func_name,"rds":[],"addr":[],"del":0,'matched':0})
							continue
						exp2="^\s*switch .*"
						r2=re.findall(exp2,r[0][1])
						if len(r2)>0:
							idinst.append({"type":'bs',"id":int(r[0][0]),"inst":r[0][1],"func":func_name,"rds":[],"addr":[],"del":0,'matched':0})
							continue
						exp2="^\s*ret .*"
						r2=re.findall(exp2,r[0][1])
						if len(r2)>0:
							idinst.append({"type":'bt',"id":int(r[0][0]),"inst":r[0][1],"func":func_name,"rds":[],"addr":[],"del":0,'matched':0})
							continue
		elif state==2:
			exp="NodeID (\d+)"
			r=re.findall(exp,t)
			if len(r)>0:
				nid=int(r[0])
				continue
			if nid>=0:
				exp=": ReachDefSet \{ (.*) \}"
				r=re.findall(exp,t)
				if len(r)>0 and r[0]!="empty":
					rds_temp=r[0].split(" ")
					for i in range(len(rds_temp)):
						rds_temp[i]=int(rds_temp[i])
					rds.append({"nid":nid,"rds":rds_temp})
	
	print "Start writing reaching definition set into parsed program"
	i_i=0
	i_r=0
	
	while i_i<len(idinst) and i_r<len(rds):
		if idinst[i_i]["id"]<rds[i_r]["nid"]:
			i_i+=1
		elif idinst[i_i]["id"]<rds[i_r]["nid"]:
			i_r+=1
		else:
			idinst[i_i]["rds"]=rds[i_r]["rds"]
			i_i+=1
			i_r+=1
	"""
	i=0
	for ii in idinst:
		for r in rds:
			if ii["id"]==r["nid"]:
				idinst[i]["rds"]=r["rds"]
				break
		i+=1
	"""
	return idinst

def check_if_special_inst_func(inst):
	if nofunc:
		return -1
	r=re.findall('@(\S+)\(',inst)
	if len(r)>0:
		i=0
		for sf in sp_inst_funcs:
			r2=re.findall(sf['name'],r[0])
			if len(r2)>0:
				return i
			i+=1
	if anyfunc:
		return 0
	else:
		return -1

def check_if_special_inst_func_lite(name):
	if nofunc:
		return -1
	i=0
	for sf in sp_inst_funcs:
		r2=re.findall(sf['name'],name)
		if len(r2)>0:
			return i
		i+=1
	if anyfunc:
		return 0
	else:
		return -1

def inst_debug(text):
	i=0
	state=0
	line_inst=[]
	while i<len(text):
		exp='define .* @\S*'+timestampfunc+'\S*\(.*\) .* \{'
		if state==0:
			if len(re.findall(exp,text[i]))>0:
				state=1
		elif state==1:
			if len(re.findall('^\}',text[i]))>0:
				break
			for exp in insert_debug_rules:
				if len(re.findall(exp,text[i]))>0:
					line_inst.append(i)
		i+=1
	line_inst.reverse()
	for l in line_inst:
		text.insert(l,'call void @'+sfunc['dfi_debug']+'()\n')

def inst_rocc_debug(text):
	i=0
	state=0
	line_inst=[]
	while i<len(text):
		exp='define .* @\S*'+timestampfunc+'\S*\(.*\) .* \{'
		if state==0:
			if len(re.findall(exp,text[i]))>0:
				state=1
		elif state==1:
			if len(re.findall('^\}',text[i]))>0:
				break
			for exp in insert_debug_rules:
				if len(re.findall(exp,text[i]))>0:
					j=i
					"""
					found=0
					while i-j<5:#instrument before the cooresponding .word
						r=re.findall('call void asm sideeffect ".word (\S+)"',text[j])
						if len(r)>0:
							data=int(r[0],16)
							if (data>>30)==0 and ((data>>27)&0x7)>0:
								found=1
								break
						j-=1
					if found==0:
						j=i
					"""
					line_inst.append(j)
		i+=1
	line_inst.reverse()
	for l in line_inst:
		text.insert(l,'call void @'+sfunc['dfi_rocc_debug']+'()\n')

def inst_init(fake,base_id):
	if fake:
		if exp_mode=="noinstr":
			return 0
		elif exp_mode=="soft":
			return 11
		elif exp_mode=="roccinstr":
			return 0
		elif exp_mode=="ori":
			return 0
	string=[]
	if exp_mode=="ori":
		string=[]
		string.append('call void asm sideeffect ".word '+hex(0xc800000b)+'", ""()\n')
		#string.append('call void @'+sfunc['dfi_func_signal']+'()\n')
	elif exp_mode=="noinstr":
		string=[]
		string.append('call void asm sideeffect ".word '+hex(0xc800000b)+'", ""()\n')
		#string.append('call void @'+sfunc['dfi_func_signal']+'()\n')
	elif exp_mode=="roccinstr":
		string=[]
		string.append('call void asm sideeffect ".word '+hex(0xc800000b)+'", ""()\n')
		#string.append('call void @'+sfunc['dfi_func_signal']+'()\n')
	elif exp_mode=="soft":
		string.append('call void asm sideeffect ".word '+hex(0xc800000b)+'", ""()\n')
		#string.append('call void @'+sfunc['dfi_func_signal']+'()\n')
		string.append('%'+str(base_id)+' = load i16*, i16** @dfi_reg_s, align 4\n')
		string.append('%'+str(base_id+1)+' = call i8* @llvm.frameaddress(i32 0)\n')
		string.append('%'+str(base_id+2)+' = ptrtoint i8* %'+str(base_id+1)+' to i32\n')
		#string.append('%'+str(base_id+1)+' = load i'+str(sp_len)+'*, i'+str(sp_len)+'** @dfi_p, align 4\n')
		
		string.append('%'+str(base_id+3)+' = icmp eq i32 %'+str(base_id+2)+', 4294967295\n')
		string.append('br i1 %'+str(base_id+3)+', label %'+str(base_id+4)+', label %'+str(base_id+5)+'\n')
		string.append('; <label>:'+str(base_id+4)+':\n')
		string.append('br label %'+str(base_id+10)+'\n')
		string.append('; <label>:'+str(base_id+5)+':\n')
		string.append('%'+str(base_id+6)+' = mul nsw i32 32, %'+str(base_id+2)+'\n')
		string.append('%'+str(base_id+7)+' = lshr i32 %'+str(base_id+6)+', 7\n')
		string.append('%'+str(base_id+8)+' = add nsw i32 10, %'+str(base_id+7)+'\n')
		string.append('%'+str(base_id+9)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(base_id)+', i32 %'+str(base_id+8)+'\n')
		string.append('store i'+str(bf_len)+' '+str(ret_id)+', i'+str(bf_len)+'* %'+str(base_id+9)+', align '+str(bf_len/8)+'\n')
		string.append('br label %'+str(base_id+10)+'\n')
		string.append('; <label>:'+str(base_id+10)+':\n')
	return string

def inst_sl_old_4(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos):
	#added the memcpt etc. functions
	#changed l/r shift bits
	load_from=copy.deepcopy(load_from)
	if fake:#only return the instrumentation length
		if sl=='s':
			if pos==0:
				return 0
			return 9
		elif sl=='l':
			if pos==0:
				return 0
			now_id=6
			parts=inst_sl_old_find_cont(rds)
			if len(parts)==0:
				now_id+=1
			for p in parts:
				if len(p)<=2:
					for d in p:
						now_id+=2
				elif p[0]==0:
					now_id+=2
				else:
					now_id+=4
			now_id+=2
			return now_id
		elif len(sl)>=3 and sl[0:2]=='bc':
			lib_p=int(sl[2:])
			lib_s=sp_inst_funcs[lib_p]['s']
			lib_l=sp_inst_funcs[lib_p]['l']
			lib_len=sp_inst_funcs[lib_p]['len']
			if type(lib_len)==list:
				if lib_len[0]<(1<<32):
					load_from.append(' i32 '+str(lib_len[0])+' ')
				else:
					load_from.append(' i64 '+str(lib_len[0])+' ')
				lib_len=len(load_from)-1
			r=re.sub("align \d+","",load_from[lib_len])
			r=re.sub("nonnull","",r)
			r=re.findall("^\s*i(\d+)(.*)",r)
			rlen=int(r[0][0])
			rfrom=r[0][1]
			target=[]
			if lib_s!=[]:
				target=re.sub("align \d+","",load_from[lib_s])
				target=re.sub("nonnull","",target)
			source=[]
			if lib_l!=[]:
				source=re.sub("align \d+","",load_from[lib_l])
				source=re.sub("nonnull","",source)
			now_id=0
			if source!=[]:
				now_id+=13
				parts=inst_sl_old_find_cont(rds)
				if len(parts)==0:
					now_id+=1
				for p in parts:
					if len(p)<=2:
						for d in p:
							now_id+=2
					elif p[0]==0:
						now_id+=2
					else:
						now_id+=4
				now_id+=7
			if target!=[]:
				now_id+=21
			return now_id
		elif sl=='bt':
			now_id=5
			now_id+=2
			now_id+=2
			return now_id
		else:
			return 0
	if sl=='s':
		if pos==0:
			return []
		string=[]
		string.append('%'+str(base_id)+' = ptrtoint '+load_from+' to i32\n')
		string.append('%'+str(base_id+1)+' = icmp eq i32 %'+str(base_id)+', 4294967295\n')
		string.append('br i1 %'+str(base_id+1)+', label %'+str(base_id+2)+', label %'+str(base_id+3)+'\n')
		string.append('; <label>:'+str(base_id+2)+':\n')
		string.append('br label %'+str(base_id+8)+'\n')
		string.append('; <label>:'+str(base_id+3)+':\n')
		#string.append('%'+str(base_id+4)+' = lshr i32 %'+str(base_id)+', 2\n')
		#string.append('%'+str(base_id+5)+' = mul nsw i32 2, %'+str(base_id+4)+'\n')
		string.append('%'+str(base_id+4)+' = mul nsw i32 32, %'+str(base_id)+'\n')
		string.append('%'+str(base_id+5)+' = lshr i32 %'+str(base_id+4)+', 7\n')
		string.append('%'+str(base_id+6)+' = add nsw i32 10, %'+str(base_id+5)+'\n')
		#string.append('call void @'+sfunc['pim_dfi_print']+'(i32 %'+str(base_id)+')\n')
		#string.append('call void @'+sfunc['pim_dfi_print']+'(i32 %'+str(base_id+4)+')\n')
		#string.append('call void @'+sfunc['pim_dfi_print']+'(i32 %'+str(base_id+5)+')\n')
		#string.append('call void @'+sfunc['pim_dfi_print']+'(i32 %'+str(base_id+6)+')\n')
		#string.append('%'+str(base_id+7)+' = and i32 %'+str(base_id+6)+', 1048575\n')
		string.append('%'+str(base_id+7)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 %'+str(base_id+6)+'\n')
		#string.append('%'+str(base_id+7)+' = inttoptr i32 %'+str(base_id+6)+' to i32*\n')
		string.append('store i'+str(bf_len)+' '+str(node_id)+', i'+str(bf_len)+'* %'+str(base_id+7)+', align '+str(bf_len/8)+'\n')
		string.append('br label %'+str(base_id+8)+'\n')
		string.append('; <label>:'+str(base_id+8)+':\n')
	elif sl=='l':
		if pos==0:
			return []
		string=[]
		string.append('%'+str(base_id)+' = ptrtoint '+load_from+' to i32\n')
		#string.append('%'+str(base_id+1)+' = lshr i32 %'+str(base_id)+', 2\n')
		#string.append('%'+str(base_id+2)+' = mul nsw i32 2, %'+str(base_id+1)+'\n')
		#string.append('%'+str(base_id+3)+' = add nsw i32 10, %'+str(base_id+2)+'\n')
		#string.append('%'+str(base_id+4)+' = and i32 %'+str(base_id+3)+', 1048575\n')
		string.append('%'+str(base_id+1)+' = mul nsw i32 32, %'+str(base_id)+'\n')
		string.append('%'+str(base_id+2)+' = lshr i32 %'+str(base_id+1)+', 7\n')
		string.append('%'+str(base_id+3)+' = add nsw i32 10, %'+str(base_id+2)+'\n')
		string.append('%'+str(base_id+4)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 %'+str(base_id+3)+'\n')
		#string.append('store i32 '+str(node_id)+', i32* %'+str(base_id+8)+', align 4\n')
		string.append('%'+str(base_id+5)+' = load i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(base_id+4)+', align '+str(bf_len/8)+'\n')
		now_id=base_id+5
		parts=inst_sl_old_find_cont(rds)
		final_id=now_id
		if len(parts)==0:
			final_id+=1
		for p in parts:
			if len(p)<=2:
				for d in p:
					final_id+=2
			elif p[0]==0:
				final_id+=2
			else:
				final_id+=4
		if len(parts)==0:
			string.append('br label %'+str(final_id+2)+'\n')
			string.append('; <label>:'+str(now_id+1)+':\n')
			now_id+=1
		for p in parts:
			if len(p)<=2:#no need to do d1<x<d2 comparison
				for d in p:
					string.append('%'+str(now_id+1)+' = icmp eq i'+str(bf_len)+' %'+str(base_id+5)+', '+str(d)+'\n')
					string.append('br i1 %'+str(now_id+1)+', label %'+str(final_id+2)+', label %'+str(now_id+2)+'\n')
					string.append('; <label>:'+str(now_id+2)+':\n')
					now_id+=2
			elif p[0]==0:#do one comparision
				string.append('%'+str(now_id+1)+' = icmp ule i'+str(bf_len)+' %'+str(base_id+5)+', '+str(p[-1])+'\n')
				string.append('br i1 %'+str(now_id+1)+', label %'+str(final_id+2)+', label %'+str(now_id+2)+'\n')
				string.append('; <label>:'+str(now_id+2)+':\n')
				now_id+=2
			else:#do two comparisions
				string.append('%'+str(now_id+1)+' = icmp uge i'+str(bf_len)+' %'+str(base_id+5)+', '+str(p[0])+'\n')
				string.append('br i1 %'+str(now_id+1)+', label %'+str(now_id+2)+', label %'+str(now_id+4)+'\n')
				string.append('; <label>:'+str(now_id+2)+':\n')
				string.append('%'+str(now_id+3)+' = icmp ule i'+str(bf_len)+' %'+str(base_id+5)+', '+str(p[-1])+'\n')
				string.append('br i1 %'+str(now_id+3)+', label %'+str(final_id+2)+', label %'+str(now_id+4)+'\n')
				string.append('; <label>:'+str(now_id+4)+':\n')
				now_id+=4
		string.append('%'+str(final_id+1)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 0\n')
		string.append('store i'+str(bf_len)+' 1, i'+str(bf_len)+'* %'+str(final_id+1)+', align '+str(bf_len/8)+'\n')
		string.append('br label %'+str(final_id+2)+'\n')
		string.append('; <label>:'+str(final_id+2)+':\n')
	elif len(sl)>=3 and sl[0:2]=='bc':
		lib_p=int(sl[2:])
		lib_s=sp_inst_funcs[lib_p]['s']
		lib_l=sp_inst_funcs[lib_p]['l']
		lib_len=sp_inst_funcs[lib_p]['len']
		if type(lib_len)==list:
			if lib_len[0]<(1<<32):
				load_from.append(' i32 '+str(lib_len[0])+' ')
			else:
				load_from.append(' i64 '+str(lib_len[0])+' ')
			lib_len=len(load_from)-1
		r=re.sub("align \d+","",load_from[lib_len])
		r=re.sub("nonnull","",r)
		r=re.findall("^\s*i(\d+)(.*)",r)
		rlen=int(r[0][0])
		rfrom=r[0][1]
		target=[]
		if lib_s!=[]:
			target=re.sub("align \d+","",load_from[lib_s])
			target=re.sub("nonnull","",target)
		source=[]
		if lib_l!=[]:
			source=re.sub("align \d+","",load_from[lib_l])
			source=re.sub("nonnull","",source)
		
		string=[]
		final_id=base_id
		if source!=[]:
			parts=inst_sl_old_find_cont(rds)
			final_id=base_id+12
			if len(parts)==0:
				final_id+=1
			for p in parts:
				if len(p)<=2:
					for d in p:
						final_id+=2
				elif p[0]==0:
					final_id+=2
				else:
					final_id+=4
			string.append('%'+str(base_id)+' = alloca i'+str(rlen)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(base_id+1)+' = alloca i32, align 4\n')
			string.append('store i'+str(rlen)+' 0, i'+str(rlen)+'* %'+str(base_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(base_id+2)+' = ptrtoint '+source+' to i32\n')
			string.append('store i32 '+str(base_id+2)+', i32* %'+str(base_id+1)+', align 4\n')
			string.append('br label %'+str(base_id+3)+'\n')
			string.append('; <label>:'+str(base_id+3)+':\n')
			string.append('%'+str(base_id+4)+' = load i'+str(rlen)+', i'+str(rlen)+'* %'+str(base_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(base_id+5)+' = icmp sge i'+str(rlen)+' %'+str(base_id+4)+', '+rfrom+'\n')
			string.append('br i1 %'+str(base_id+5)+', label %'+str(final_id+7)+', label %'+str(base_id+6)+'\n')
			string.append('; <label>:'+str(base_id+6)+':\n')
			
			string.append('%'+str(base_id+7)+' = load i32, i32* %'+str(base_id+1)+', align 4\n')
			string.append('%'+str(base_id+8)+' = mul nsw i32 32, %'+str(base_id+7)+'\n')
			string.append('%'+str(base_id+9)+' = lshr i32 %'+str(base_id+8)+', 7\n')
			string.append('%'+str(base_id+10)+' = add nsw i32 10, %'+str(base_id+9)+'\n')
			string.append('%'+str(base_id+11)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 %'+str(base_id+10)+'\n')
			string.append('%'+str(base_id+12)+' = load i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(base_id+11)+', align '+str(bf_len/8)+'\n')
			now_id=base_id+12
			if len(parts)==0:
				string.append('br label %'+str(final_id+2)+'\n')
				string.append('; <label>:'+str(now_id+1)+':\n')
				now_id+=1
			for p in parts:
				if len(p)<=2:#no need to do d1<x<d2 comparison
					for d in p:
						string.append('%'+str(now_id+1)+' = icmp eq i'+str(bf_len)+' %'+str(base_id+12)+', '+str(d)+'\n')
						string.append('br i1 %'+str(now_id+1)+', label %'+str(final_id+2)+', label %'+str(now_id+2)+'\n')
						string.append('; <label>:'+str(now_id+2)+':\n')
						now_id+=2
				elif p[0]==0:#do one comparision
					string.append('%'+str(now_id+1)+' = icmp ule i'+str(bf_len)+' %'+str(base_id+12)+', '+str(p[-1])+'\n')
					string.append('br i1 %'+str(now_id+1)+', label %'+str(final_id+2)+', label %'+str(now_id+2)+'\n')
					string.append('; <label>:'+str(now_id+2)+':\n')
					now_id+=2
				else:#do two comparisions
					string.append('%'+str(now_id+1)+' = icmp uge i'+str(bf_len)+' %'+str(base_id+12)+', '+str(p[0])+'\n')
					string.append('br i1 %'+str(now_id+1)+', label %'+str(now_id+2)+', label %'+str(now_id+4)+'\n')
					string.append('; <label>:'+str(now_id+2)+':\n')
					string.append('%'+str(now_id+3)+' = icmp ule i'+str(bf_len)+' %'+str(base_id+12)+', '+str(p[-1])+'\n')
					string.append('br i1 %'+str(now_id+3)+', label %'+str(final_id+2)+', label %'+str(now_id+4)+'\n')
					string.append('; <label>:'+str(now_id+4)+':\n')
					now_id+=4
			string.append('%'+str(final_id+1)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 0\n')
			string.append('store i'+str(bf_len)+' 1, i'+str(bf_len)+'* %'+str(final_id+1)+', align '+str(bf_len/8)+'\n')
			string.append('br label %'+str(final_id+2)+'\n')
			string.append('; <label>:'+str(final_id+2)+':\n')
			
			string.append('%'+str(final_id+3)+' = load i'+str(rlen)+', i'+str(rlen)+'* %'+str(base_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(final_id+4)+' = add nsw i'+str(rlen)+' %'+str(final_id+3)+', 4\n')
			string.append('store i'+str(rlen)+' %'+str(final_id+4)+', i'+str(rlen)+'* %'+str(base_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(final_id+5)+' = load i32, i32* %'+str(base_id+1)+', align 4\n')
			string.append('%'+str(final_id+6)+' = add nsw i32 %'+str(final_id+5)+', 4\n')
			string.append('store i32 %'+str(final_id+6)+', i32* %'+str(base_id+1)+', align 4\n')
			string.append('br label %'+str(base_id+3)+'\n')
			string.append('; <label>:'+str(final_id+7)+':\n')
			
			final_id=final_id+8
		if target!=[]:
			string.append('%'+str(final_id)+' = alloca i'+str(rlen)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(final_id+1)+' = alloca i32, align 4\n')
			string.append('store i'+str(rlen)+' 0, i'+str(rlen)+'* %'+str(final_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(final_id+2)+' = ptrtoint '+target+' to i32\n')
			string.append('store i32 '+str(final_id+2)+', i32* %'+str(final_id+1)+', align 4\n')
			string.append('br label %'+str(final_id+3)+'\n')
			string.append('; <label>:'+str(final_id+3)+':\n')
			string.append('%'+str(final_id+4)+' = load i'+str(rlen)+', i'+str(rlen)+'* %'+str(final_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(final_id+5)+' = icmp sge i'+str(rlen)+' %'+str(final_id+4)+', '+rfrom+'\n')
			string.append('br i1 %'+str(final_id+5)+', label %'+str(final_id+20)+', label %'+str(final_id+6)+'\n')
			string.append('; <label>:'+str(final_id+6)+':\n')
			
			string.append('%'+str(final_id+7)+' = load i32, i32* %'+str(final_id+1)+', align 4\n')
			string.append('%'+str(final_id+8)+' = icmp eq i32 %'+str(final_id+7)+', 4294967295\n')
			string.append('br i1 %'+str(final_id+8)+', label %'+str(final_id+9)+', label %'+str(final_id+10)+'\n')
			string.append('; <label>:'+str(final_id+9)+':\n')
			string.append('br label %'+str(final_id+15)+'\n')
			string.append('; <label>:'+str(final_id+10)+':\n')
			string.append('%'+str(final_id+11)+' = mul nsw i32 32, %'+str(final_id+7)+'\n')
			string.append('%'+str(final_id+12)+' = lshr i32 %'+str(final_id+11)+', 7\n')
			#string.append('%'+str(final_id+11)+' = lshr i32 %'+str(final_id+7)+', 2\n')
			#string.append('%'+str(final_id+12)+' = mul nsw i32 2, %'+str(final_id+11)+'\n')
			string.append('%'+str(final_id+13)+' = add nsw i32 10, %'+str(final_id+12)+'\n')
			#string.append('%'+str(final_id+14)+' = and i32 %'+str(final_id+13)+', 1048575\n')
			string.append('%'+str(final_id+14)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 %'+str(final_id+13)+'\n')
			string.append('store i'+str(bf_len)+' '+str(node_id)+', i'+str(bf_len)+'* %'+str(final_id+14)+', align '+str(bf_len/8)+'\n')
			string.append('br label %'+str(final_id+15)+'\n')
			string.append('; <label>:'+str(final_id+15)+':\n')
		
			string.append('%'+str(final_id+16)+' = load i'+str(rlen)+', i'+str(rlen)+'* %'+str(final_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(final_id+17)+' = add nsw i'+str(rlen)+' %'+str(final_id+16)+', 4\n')
			string.append('store i'+str(rlen)+' %'+str(final_id+17)+', i'+str(rlen)+'* %'+str(final_id)+', align '+str(rlen/8)+'\n')
			string.append('%'+str(final_id+18)+' = load i32, i32* %'+str(final_id+1)+', align 4\n')
			string.append('%'+str(final_id+19)+' = add nsw i32 %'+str(final_id+18)+', 4\n')
			string.append('store i32 %'+str(final_id+19)+', i32* %'+str(final_id+1)+', align 4\n')
			string.append('br label %'+str(final_id+3)+'\n')
			string.append('; <label>:'+str(final_id+20)+':\n')
	elif sl=='bt':
		string=[]
		string.append('%'+str(base_id)+' = mul nsw i32 32, %'+str(init_base_id+2)+'\n')
		string.append('%'+str(base_id+1)+' = lshr i32 %'+str(base_id)+', 7\n')
		string.append('%'+str(base_id+2)+' = add nsw i32 10, %'+str(base_id+1)+'\n')
		string.append('%'+str(base_id+3)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 %'+str(base_id+2)+'\n')
		string.append('%'+str(base_id+4)+' = load i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(base_id+3)+', align '+str(bf_len/8)+'\n')
		now_id=base_id+4
		final_id=now_id+2
		string.append('%'+str(now_id+1)+' = icmp eq i'+str(bf_len)+' %'+str(base_id+4)+', '+str(ret_id)+'\n')
		string.append('br i1 %'+str(now_id+1)+', label %'+str(final_id+2)+', label %'+str(now_id+2)+'\n')
		string.append('; <label>:'+str(now_id+2)+':\n')
		now_id+=2
		string.append('%'+str(final_id+1)+' = getelementptr inbounds i'+str(bf_len)+', i'+str(bf_len)+'* %'+str(init_base_id)+', i32 0\n')
		string.append('store i'+str(bf_len)+' 1, i'+str(bf_len)+'* %'+str(final_id+1)+', align '+str(bf_len/8)+'\n')
		string.append('br label %'+str(final_id+2)+'\n')
		string.append('; <label>:'+str(final_id+2)+':\n')
	else:
		string=[]
	return string

def rocc_trans_info(data):
	datal=data&0x1f
	datah=data-(datal)
	return (datah<<3)|datal

def inst_sl_none(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos):
	if fake:#only return the instrumentation length
		return 0
	return []

def inst_sl_roccinstr(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos):
	#this is for gem5, only instrument on store for reference
	#added the memcpy etc. functions
	load_from=copy.deepcopy(load_from)
	if fake:#only return the instrumentation length
		if sl=='s':
			return 0
		elif sl=='l':
			return 0
		elif len(sl)>=3 and sl[0:2]=='bc':
			return 0
			lib_p=int(sl[2:])
			lib_s=sp_inst_funcs[lib_p]['s']
			lib_l=sp_inst_funcs[lib_p]['l']
			lib_len=sp_inst_funcs[lib_p]['len']
			#if lib_p==0:
			#	print '-++++++++++++'
			#	print lib_s,lib_l,lib_len
			#	print load_from
			if type(lib_len)==list:
				if lib_len[0]<(1<<32):
					load_from.append(' i32 '+str(lib_len[0])+' ')
				else:
					load_from.append(' i64 '+str(lib_len[0])+' ')
				lib_len=len(load_from)-1
			r=re.sub("align \d+","",load_from[lib_len])
			r=re.sub("nonnull","",r)
			r=re.findall("^\s*i(\d+)(.*)",r)
			rlen=int(r[0][0])
			rfrom=r[0][1]
			target=[]
			if lib_s!=[]:
				target=re.sub("align \d+","",load_from[lib_s])
				target=re.sub("nonnull","",target)
			source=[]
			if lib_l!=[]:
				source=re.sub("align \d+","",load_from[lib_l])
				source=re.sub("nonnull","",source)
			now_id=0
			if rlen>32:
				now_id+=3
			if source!=[]:
				now_id+=1
			if target!=[]:
				now_id+=1
			return now_id
		elif sl=='bt':
			return 0
		else:
			return 0
	string=[]
	if sl=='s':
		if pos==0:
			return []
		else:
			#return []
			if stldroccdebug:
				string.append('call void @'+sfunc['dfi_rocc_stlddebug']+'(i32 '+str((rocc_trans_info(0x00000000+node_id)<<7)|0x0b)+')\n')
			string.append('call void asm sideeffect ".word '+hex((rocc_trans_info(0x00000000+node_id)<<7)|0x0b)+'", ""()\n')
			#string.append('.word '+hex(0x00000000+node_id)+' \n')
	elif sl=='l':
		if pos==0:
			return []
		else:
			#return []
			if stldroccdebug:
				string.append('call void @'+sfunc['dfi_rocc_stlddebug']+'(i32 '+str((rocc_trans_info(0x00010000+node_id)<<7)|0x0b)+')\n')
			string.append('call void asm sideeffect ".word '+hex((rocc_trans_info(0x00010000+node_id)<<7)|0x0b)+'", ""()\n')
			#string.append('store i32 '+str(0x00010000+node_id)+', i32* @dfi_reg_signal, align 4\n')
	elif len(sl)>=3 and sl[0:2]=='bc':
		lib_p=int(sl[2:])
		lib_s=sp_inst_funcs[lib_p]['s']
		lib_l=sp_inst_funcs[lib_p]['l']
		lib_len=sp_inst_funcs[lib_p]['len']
		if type(lib_len)==list:
			if lib_len[0]<(1<<32):
				load_from.append(' i32 '+str(lib_len[0])+' ')
			else:
				load_from.append(' i64 '+str(lib_len[0])+' ')
			lib_len=len(load_from)-1
		r=re.sub("align \d+","",load_from[lib_len])
		r=re.sub("nonnull","",r)
		r=re.findall("^\s*i(\d+)(.*)",r)
		rlen=int(r[0][0])
		rfrom=r[0][1]
		target=[]
		if lib_s!=[]:
			target=re.sub("align \d+","",load_from[lib_s])
			target=re.sub("nonnull","",target)
		source=[]
		if lib_l!=[]:
			source=re.sub("align \d+","",load_from[lib_l])
			source=re.sub("nonnull","",source)
		mark=0
		if source==[] and target!=[]: #write but not read
			mark=1
		elif source!=[] and target==[]: #read but not write
			mark=2
		elif source!=[] and target!=[]: #write and read
			mark=3
		string.append('call void asm sideeffect ".word '+hex((rocc_trans_info((1<<19)+(mark<<17)+node_id)<<7)|0x0b)+'", ""()\n')
	elif sl=='bt':
		string=[]
		if anyfunc:
			#mark=4
			string.append('call void asm sideeffect ".word '+hex((rocc_trans_info((1<<19)+(1<<16)+ret_id)<<7)|0x0b)+'", ""()\n')
	else:
		string=[]
	return string

def inst_sl_aux(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos):
	#pos:0: front, 1: rear
	load_from=copy.deepcopy(load_from)
	if fake:#only return the instrumentation length
		if sl=='s':
			return 0
		elif sl=='l':
			return 0
		else:
			return 0
	string=[]
	if sl=='s':
		if pos==0:
			string.append(nopinst+'\n')
			string.append(nopinst+'\n')
		else:
			string.append(nopinst2+'\n')
			string.append(nopinst2+'\n')
	elif sl=='l':
		if pos==0:
			string.append(nopinst+'\n')
			string.append(nopinst+'\n')
		else:
			string.append(nopinst2+'\n')
			string.append(nopinst2+'\n')
	else:
		string=[]
	return string

def inst_sl_old_find_cont(rds):
	#find out the continous parts in the reaching definition set
	parts=[]
	now_id=-2
	for r in rds:
		if r!=now_id+1:
			parts.append([r])
		else:
			parts[-1].append(r)
		now_id=r
	return parts#parts is sorted

def inst_sl(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos):
	if exp_mode=='noinstr':
		return inst_sl_aux(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos)
	elif exp_mode=='roccinstr':
		return inst_sl_roccinstr(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos)
	elif exp_mode=='soft':
		return inst_sl_old_4(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos)
	elif exp_mode=='ori':
		return inst_sl_none(fake,base_id,init_base_id,load_from,node_id,sl,rds,pos)

def inst_cmp_rd_ll(tp,rd,ll):
	if tp=='s' or tp=='l':
		exp="\S+.*align \S+"
		r_rd=re.findall(exp,rd)
		r_ll=re.findall(exp,ll)
		if r_rd==r_ll:
			return 1
		else:
			return 0
	elif tp[0]=='b':
		exp="(\S+.*)[#!]\S+"
		r_rd=re.findall(exp,rd)
		if len(r_rd)==0:
			exp="(\S+.*\S+)\s*"
			r_rd=re.findall(exp,rd)
		r_ll=re.findall(exp,ll)
		#print r_rd,r_ll
		if r_rd==r_ll:
			return 1
		else:
			return 0

def inst_find_id_func(line,now_id):
	exp="%(\d+) = .*"
	r=re.findall(exp,line)
	if len(r)>0:#this is a line with id
		if now_id>=int(r[0]):
			now_id=[int(r[0]),now_id-int(r[0])+1]#if we found this id in IR is >= the previous, there is an error
		else:
			now_id=int(r[0])
		return now_id,[],0
	exp="<label>:(\d+):"
	r=re.findall(exp,line)
	if len(r)>0:#this is a line with id
		if now_id>=int(r[0]):
			now_id=[int(r[0]),now_id-int(r[0])+1]
		else:
			now_id=int(r[0])
		return now_id,[],0
	exp="define .* @(\S+)\((.*)\) .* \{"
	r=re.findall(exp,line)
	if len(r)>0:#this is a line of function begin
		#find out the start up id
		param=r[0][1]
		param=re.sub('\{.*?\}','REP',param)
		param=re.sub('\(.*?\)','',param)
		param=re.sub(' ','',param)
		param=param.split(',')
		count=0
		for p in param:
			if len(p)>0 and p!='...':
				count+=1
		return count,r[0][0],0
	exp="^\s*\}\s*"
	r=re.findall(exp,line)
	if len(r)>0:#this is a line of function begin
		return now_id,[],1
	return now_id,[],0

def inst_add_rule_find(eid,rule):
	for r in rule:
		if eid>=r[0][0] and (r[0][1]==[] or eid<=r[0][1]):
			return r[1]
	return -1

def inst_match_target(sl,text):
	if sl=='s':
		#exp="store .*,(?![^(]*\\)) (.*),(?![^(]*\\)) align \S+"
		exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
		r=re.findall(exp,text)
		return r
	elif sl=='l':
		#exp="%\S+ = load .*,(?![^(]*\\)) (.*),(?![^(]*\\)) align \S+"
		exp="%\S+ = load .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
		r=re.findall(exp,text)
		return r
	elif len(sl)>=2 and sl[0:2]=='bc':
		#print text
		exp="(%\S+) =.*call (.*) @"
		r=re.findall(exp,text)
		if len(r)>0:
			output=r[0][1]+' '+r[0][0]
		else:
			output=[]
		exp="\((.*)\)"
		r=re.findall(exp,text)
		r[0]+=','
		result=[[]]
		search=[6,5,4,3,2,1]
		for s in search:
			exp=""
			for i in range(s):
				exp+="(.*),(?![^(]*\\))(?![^{]*\\}) "
			exp=exp[0:-1]
			r2=re.findall(exp,r[0])
			if len(r2)>0:
				result=[[rr for rr in r2[0]]]
				break
		"""
		exp="(.*),(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\})"
		r2=re.findall(exp,r[0])
		if len(r2)==0:
			exp="(.*),(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\})"
			r2=re.findall(exp,r[0])
		r=[[rr for rr in r2[0]]]
		"""
		if output!=[]:
			result[0].append(output)
		#print result
		return result
	return ['']

def rds_rename(rds):
	#the format of input: array of [load node id, [store node ids]]
	drs=[]
	i=0
	for r in rds:
		i_dn=0
		for d in r[1]:
			while 1:
				if i_dn>=len(drs):
					drs.append([d,[r[0]]])
					i_dn+=1
					break
				elif d>drs[i_dn][0]:
					i_dn+=1
				elif d<drs[i_dn][0]:
					drs.insert(i_dn,[d,[r[0]]])
					break
				else:
					drs[i_dn][1].append(r[0])
					i_dn+=1
					break
		i+=1
	
	#rename
	now_name=1
	names=[]#old_name, new_name
	for r in rds:
		if r[0]!=[] and r[1]!=[]:
			now_name+=1
			names.append([r[0],now_name])
	
	drs=sorted(drs,key=lambda x: x[1])
	
	now_d=[]
	for d in drs:
		if d[1]!=now_d:
			now_d=d[1]
			now_name+=1
			names.append([d[0],now_name])
		else:
			names.append([d[0],now_name])
	names=sorted(names,key=lambda x: x[0])
	"""
	for n in names:
		print n
	"""
	for r in rds:
		i_n=0
		while 1:
			if r[0]==[]:
				break
			elif i_n>=len(names):
				now_name+=1
				names.append([r[0],now_name])
				i_n+=1
				break
			elif r[0]<names[i_n][0]:
				now_name+=1
				names.insert(i_n,[r[0],now_name])
				break
			elif r[0]>names[i_n][0]:
				i_n+=1
			else:
				break
	names=sorted(names,key=lambda x: x[0])
	return names

def rds_membership_rename(rds):
	i=0
	while i<len(rds):
		rds[i].extend([len(rds[i][1]),i])
		i+=1
	rds=sorted(rds,key=lambda x: x[2])
	rds.reverse()
	
	names=[]
	now_id=1
	#rename the identifier in reaching definition set first
	for r in rds:
		for d in r[1]:
			found=0
			i=0
			while i<len(names):
				if names[i][0]==d:
					found=1
					break
				i+=1
			if found==0:
				names.append([d,now_id])
				now_id+=1
	#rename the load id then
	for r in rds:
		found=0
		i=0
		if r[0]!=[]:
			while i<len(names):
				if names[i][0]==r[0]:
					found=1
					break
				i+=1
			if found==0:
				names.append([r[0],now_id])
				now_id+=1
	names=sorted(names,key=lambda x: x[0])
	return names

def rds_rename_perform(renames,rds):
	#rename the node id
	renames_ids=[r[0] for r in renames]
	i_r=0
	while i_r<len(rds):
		if rds[i_r][0]!=[]:
			rds[i_r][0]=renames[renames_ids.index(rds[i_r][0])][1]
		i_r+=1
	"""
	i_rn=0
	i_r=0
	while i_r<len(rds) and i_rn<len(renames):
		if rds[i_r][0]!=[]:
			if rds[i_r][0]<renames[i_rn][0]:
				i_r+=1
			elif rds[i_r][0]>renames[i_rn][0]:
				i_rn+=1
			else:
				rds[i_r][0]=renames[i_rn][1]
				i_r+=1
				i_rn+=1
		else:
			i_r+=1
	"""
	#rename ids in reaching definition set
	i=0
	while i<len(rds):
		i_rn=0
		i_d=0
		while i_d<len(rds[i][1]) and i_rn<len(renames):
			if rds[i][1][i_d]<renames[i_rn][0]:
				i_d+=1
			elif rds[i][1][i_d]>renames[i_rn][0]:
				i_rn+=1
			else:
				rds[i][1][i_d]=renames[i_rn][1]
				i_d+=1
				i_rn+=1
		rds[i][1]=sorted(list(set(rds[i][1])))
		i+=1
	return rds

def rds_delete(rds):
	#this is for delete the identifier(stores) that is not in the list
	count_d=0
	all_rds0=[r[0] for r in rds]
	delete=[]
	i=0
	while i<len(all_rds0):
		if all_rds0[i]==[]:
			delete.append(i)
		i+=1
	delete.reverse()
	for d in delete:
		del all_rds0[d]
	all_rds0=sorted(list(set(all_rds0)))
	i=0
	while i<len(rds):
		i_d=0
		i_r=0
		delete=[]
		while i_r<=len(all_rds0) and i_d<len(rds[i][1]):
			if i_r<len(all_rds0):
				if all_rds0[i_r]<rds[i][1][i_d]:
					i_r+=1
				elif all_rds0[i_r]>rds[i][1][i_d]:
					delete.append(i_d)
					i_d+=1
				else:
					i_r+=1
					i_d+=1
			else:
				delete.append(i_d)
				i_d+=1
		delete.reverse()
		for d in delete:
			del rds[i][1][d]
		i+=1

def inst_mark_cp_bb_fcycles(p,graph,flag,crecord,cycles):
	#init:flag=all 0; cycles=[]
	if flag[p]==0:
		ncrecord=copy.deepcopy(crecord)
		ncrecord.append(p)
		flag[p]=1
		for i in range(len(graph[p])):
			if graph[p][i]:
				inst_mark_cp_bb_fcycles(i,graph,flag,ncrecord,cycles)
		flag[p]=2
	elif flag[p]==1:
		for i in range(len(crecord)):
			if crecord[i]==p:
				break
		cycles.append(crecord[i:])

def inst_mark_cp_bb_fcoverset(p,agraph,length,cp,cover,margin):
	#initial length=0
	if length<max_hist:
		cover.append(p)
		length+=1
		for i in range(len(agraph[p])):
			if agraph[p][i] and cp[i]==0:
				inst_mark_cp_bb_fcoverset(i,agraph,length,cp,cover,margin)
	else:
		margin.append(p)

def inst_mark_cp_bb_fhist(p,agraph,cp,record,hist):
	nrecord=copy.deepcopy(record)
	nrecord.append(p)
	need_record=0
	if sum(agraph[p])==0:
		need_record=1
	for i in range(len(agraph[p])):
		if agraph[p][i]:
			if cp[i]==0:
				inst_mark_cp_bb_fhist(i,agraph,cp,nrecord,hist)
			else:
				need_record=1
	if need_record:
		hist.append(nrecord)

def inst_mark_cp_bb_func(bb):
	graph=[[0 for i in range(len(bb))] for j in range(len(bb))]
	for i in range(len(bb)):
		for j in range(len(bb)):
			for np in bb[j]['npred']:
				if bb[i]['id']==np:
					graph[i][j]=1
					break
	cp=[0 for i in range(len(bb))]
	for i in range(len(bb)):
		if bb[i]['sp']:
			cp[i]=1
	
	# mark the node without outgoing edge
	for i in range(len(bb)):
		if sum(graph[i])==0:
			cp[i]=1
	
	#mark the node in a cycle
	cycles=[]
	crecord=[]
	flag=[0 for i in range(len(bb))]
	inst_mark_cp_bb_fcycles(0,graph,flag,crecord,cycles)
	for c in cycles:
		found=0
		for i in c:
			if cp[i]:
				found=1
				break
		if found==0:
			cp[c[0]]=1
	
	#mark the remaining nodes
	agraph=numpy.array(graph).T.tolist()
	flag=copy.deepcopy(cp)
	while sum(flag)<len(flag):
		for i in range(len(bb)):
			if cp[i]==1:
				cover=[]
				margin=[]
				inst_mark_cp_bb_fcoverset(i,agraph,0,cp,cover,margin)
				for m in margin:
					cp[m]=1
				for c in cover:
					flag[c]=1
	
	#find out all possible histories for each checkpoint
	history=[]
	for i in range(len(bb)):
		if cp[i]:
			record=[]
			hist=[]
			inst_mark_cp_bb_fhist(i,agraph,cp,record,hist)
			#print '-'*5
			#for h in hist:
			#	print h
			history.append([1,hist])
		else:
			history.append([0,[]])
	#print cp
	return history

def inst_mark_cp_bb(inst_records,text):
	#this function will find out the basic block, and mark the checkpoint basic block
	#text is the .ll text
	inst_bb=[]
	now_id=[-1,-1]#first one is the normal id of the basic block, the second incurred by function call
	pred=[]
	p=[0,0]
	for ir in inst_records:
		if ir[1]=='f':
			inst_bb.append([])
			now_id=[ir[3],0]
			pred=[]
			p=[p[1]+1,p[1]+1]
		elif ir[1]=='br' or ir[1]=='bs':
			inst_bb[-1].append({'id':now_id,'p':p,'pred':pred,'sp':0})#p is the pointer to inst_records
			
			now_id=[ir[3]+1,0]
			p=[p[1]+1,p[1]+1]
			
			line=ir[0]+1
			exp='; preds = (.*)'
			r=re.findall(exp,text[line])
			while len(r)==0:
				line+=1
				r=re.findall(exp,text[line])
			exp='%(\d+)'
			r=re.findall(exp,r[0])
			for i in range(len(r)):
				r[i]=int(r[i])
			pred=r
			
		elif ir[1]=='bc':
			inst_bb[-1].append({'id':now_id,'p':p,'pred':pred,'sp':1})
			pred=[now_id[0]]
			now_id=[now_id[0],now_id[1]+1]
			p=[p[1]+1,p[1]+1]
		elif ir[1]=='bt':
			inst_bb[-1].append({'id':now_id,'p':p,'pred':pred,'sp':1})
			pred=[now_id[0]]
			now_id=[now_id[0],now_id[1]+1]
			p=[p[1]+1,p[1]+1]
		else:
			p[1]+=1
	
	#revise the pred
	i_f=0
	while i_f<len(inst_bb):
		i_b=0
		while i_b<len(inst_bb[i_f]):
			b=inst_bb[i_f][i_b]
			new_pred=[]
			for p in b['pred']:
				if p==b['id'][0]:
					aux=b['id'][1]-1
					if aux<0:
						j=i_b
						max_aux=0
						while j<len(inst_bb[i_f]):
							if inst_bb[i_f][j]['id'][0]!=p:
								break
							else:
								max_aux=inst_bb[i_f][j]['id'][1]
							j+=1
						aux=max_aux
					new_pred.append([p,aux])
				else:
					j=0
					aux=0
					max_aux=-1
					while j<len(inst_bb[i_f]):
						if inst_bb[i_f][j]['id'][0]!=p and max_aux>=0:
							break
						elif inst_bb[i_f][j]['id'][0]==p:
							max_aux=inst_bb[i_f][j]['id'][1]
						j+=1
					aux=max_aux
					new_pred.append([p,aux])
			inst_bb[i_f][i_b]['npred']=new_pred
			i_b+=1
		i_f+=1
	
	#mark the checkpoints, history=[function][bb][possible history]
	history=[]
	for f in inst_bb:
		history.append(inst_mark_cp_bb_func(f))
	
	#conclude the info
	inst_info=[[] for i in inst_records]
	i=0
	while i<len(inst_bb):
		j=0
		while j<len(inst_bb[i]):
			
			j+=1
		i+=1
	
	i=0
	while i<len(inst_bb):
		j=0
		while j<len(inst_bb[i]):
			print inst_bb[i][j]
			print history[i][j]
			hist_info=[]
			for hist in history[i][j][1]:
				hist.reverse()
				data=[]
			j+=1
		i+=1
	
	return inst_bb,history

def inst_ll_random_delete(inst_records,ptg):
	delete=[]
	sl_count=0
	delete_old=[]#this is for in case it will be used by inst_ll_old
	i=0
	while i<len(inst_records):
		if inst_records[i][1] in ['s','l']:
			value=random.randint(0,99)
			if value<ptg:
				delete.append(i)
				delete_old.append(sl_count)
		if inst_records[i][1] in ['s','l','f','e']:
			sl_count+=1
		i+=1
	
	with open('random_delete_record'+rand_del_file_suffix, 'wb') as f:
		pickle.dump([delete,delete_old],f)
	f=open('random_delete_record_disp'+rand_del_file_suffix,'wb')
	for d in delete:
		f.write(str(d)+'\n')
	f.close()
	return delete

def inst_initialize_func(text,mode):
	i=0
	linemain=-1
	while i<len(text):
		exp='define .* @main\(.*\) .* \{'
		if len(re.findall(exp,text[i]))>0:
			linemain=i
			break
		i+=1
	if mode=='noinstr':
		text.insert(linemain+1,'call void @'+sfunc['init_noinstrument_dfi']+'()\n')
	if mode=='roccinstr':
		text.insert(linemain+1,'call void @'+sfunc['init_roccinstr_dfi']+'()\n')
	elif mode=='soft':
		text.insert(linemain+1,'call void @'+sfunc['init_soft_dfi']+'()\n')
	elif mode=='ori':
		text.insert(linemain+1,'call void @'+sfunc['init_ori_dfi']+'()\n')

def remove_initialize_func(text,mode):
	i=0
	linemain=-1
	while i<len(text):
		exp='define .* @main\(.*\) .* \{'
		if len(re.findall(exp,text[i]))>0:
			linemain=i
			break
		i+=1
	if mode=='noinstr':
		rexp='call void @'+sfunc['init_noinstrument_dfi']+'()\n'
	if mode=='roccinstr':
		rexp='call void @'+sfunc['init_roccinstr_dfi']+'()\n'
	while i<len(text):
		if text[i]==rexp:
			del text[i]
			return
		i+=1

def inst_ll(idinst,ll_name,ll_out_name):
	
	text=read_file(ll_name)
	replace_intrinsic_func(text)
	#make_align4(text)
	if endtimestamp:
		inst_timestamp(text)
	
	#delete llvm.lifetime function call's instrumentation
	i_i=0
	llvmlifetimecount=0
	while i_i<len(idinst):
		if idinst[i_i]['type']=='bc' and "llvm.lifetime" in idinst[i_i]['inst']:
			idinst[i_i]['del']=1
			llvmlifetimecount+=1
		i_i+=1
	print 'delete llvm.liftime',llvmlifetimecount
	
	#delete unnecessary check
	emptyrds=0
	i_i=0
	while i_i<len(idinst):
		if idinst[i_i]['type']=='l' and len(idinst[i_i]['rds'])==0:
			idinst[i_i]['del']=1
			emptyrds+=1
		i_i+=1
	print 'delete empty rds',emptyrds
	
	#find out after which lines we should add instrumented code
	inst_records=[]#text pos,type,node id(rd analysis),last element id,target/func name
	rds=[]
	i_i=0
	i_t=0
	now_id=-1
	last_f=-1
	invalid=[]
	idinst.append({'type':'s','inst':'dummy'})
	while i_i<len(idinst) and i_t<len(text):
		if idinst[i_i]['type'] in ['s','l','bc','bt']:
			record_type=idinst[i_i]['type']
			if idinst[i_i]['type']=='bc':
				sp_inst_func=check_if_special_inst_func(idinst[i_i]['inst'])
				if sp_inst_func==-1:
					i_i+=1
					continue
				#print idinst[i_i]
				record_type+=str(sp_inst_func)
			old_id=now_id
			now_id,fname,isend=inst_find_id_func(text[i_t],old_id)
			if type(now_id)==list:
				print 'variable id extraction error, now try to automatically fix'
				i_fix=len(inst_records)-1
				while i_fix>=0:
					inst_records[-1][3]-=now_id[1]
					if inst_records[-1][1]=='f':
						break
					i_fix-=1
				now_id=now_id[0]
			if fname!=[]:
				inst_records.append([i_t,'f',[],now_id,fname])
				rds.append([[],[]])
			if isend:
				inst_records.append([i_t,'e',[],now_id,''])
				rds.append([[],[]])
			if i_i!=len(idinst)-1 and inst_cmp_rd_ll(idinst[i_i]['type'],idinst[i_i]['inst'],text[i_t]):
				#print idinst[i_i]['inst'],text[i_t]
				#print record_type,'|',idinst[i_i]['type'],'|',idinst[i_i]['inst'],'|',text[i_t]
				r=inst_match_target(record_type,text[i_t])
				#print i_i,len(idinst),idinst[i_i]['inst'],text[i_t],r
				if(idinst[i_i]['del']):
					invalid.append(len(inst_records))
				if idinst[i_i]['type'] in ['bc','bt']:#if we instrument bc and bt in front of them, we should set the "id to this instr" the old_id
					if idinst[i_i]['type']=='bt' or sp_inst_funcs[sp_inst_func]['pos']==0:
						inst_records.append([i_t,record_type,idinst[i_i]['id'],old_id,r[0]])
					else:
						inst_records.append([i_t,record_type,idinst[i_i]['id'],now_id,r[0]])
				else:
					inst_records.append([i_t,record_type,idinst[i_i]['id'],now_id,r[0]])
				rds.append([idinst[i_i]['id'],idinst[i_i]['rds']])
				i_i+=1
			i_t+=1
		else:
			i_i+=1
	
	if i_i<len(idinst)-1:
		print "ERROR, .rd not match .ll",idinst[i_i],i_i,len(idinst)
		exit(1)
	print '1.extract basic information'
	"""
	for ii in idinst:
		print ii
	"""
	#delete the invalid instructions
	invalid.reverse()
	for d in invalid:
		del inst_records[d]
		del rds[d]
	
	#exit(1)
	#delete special functions
	delete=[]
	i=0
	while i<len(inst_records):
		if inst_records[i][1]=='f':
			found=0
			for ef in exfuncs:
				if len(re.findall(ef,inst_records[i][4]))>0:
					found=1
					break
			if found:
				j=i
				while j<len(inst_records):
					if inst_records[j][1]=='e':
						break
					j+=1
				delete.append([i,j])
				i=j
			else:
				i+=1
		else:
			i+=1
	delete.reverse()
	for d in delete:
		i=d[1]
		while i>=d[0]:
			del inst_records[i]
			del rds[i]
			i-=1
	#exit(1)
	print '2.delete extra information complete'
	
	delete_list=[0 for ir in inst_records]
	delete=[]
	
	if rand_del and rand_del_ptg>0:
		delete=inst_ll_random_delete(inst_records,rand_del_ptg)
		delete.reverse()
		for d in delete:
			del inst_records[d]
			del rds[d]
		print 'sp1.random delete complete'
	
	if load_del:
		with open(load_del_name, 'r') as f:
			packet=pickle.load(f)
		delete=packet[1]
		delete.reverse()
		for d in delete:
			del inst_records[d]
			del rds[d]
		print 'sp2.load delete complete'
	
	"""
	for d in delete:
		delete_list[d]=1
	"""
	
	#find out how much should be added on id, and the base id of each instrumented part
	
	now_add=0
	i=0
	for ir in inst_records:
		if ir[1]=='f':
			inst_records[i].append(0)
			now_add=inst_init(1,[])
		elif ir[1]=='s':
			inst_records[i].append(now_add)
			now_add+=inst_sl(1,[],[],[],[],'s',rds[i][1],0)#before
			now_add+=inst_sl(1,[],[],[],[],'s',rds[i][1],1)#after
		elif ir[1]=='l':
			inst_records[i].append(now_add)
			now_add+=inst_sl(1,[],[],[],[],'l',rds[i][1],0)
			now_add+=inst_sl(1,[],[],[],[],'l',rds[i][1],1)
		elif len(ir[1])>=2 and ir[1][0:2]=='bc':
			inst_records[i].append(now_add)
			now_add+=inst_sl(1,[],[],ir[4],[],ir[1],rds[i][1],0)
		elif ir[1]=='bt':
			inst_records[i].append(now_add)
			now_add+=inst_sl(1,[],[],ir[4],[],ir[1],rds[i][1],0)
		else:
			inst_records[i].append(now_add)
		i+=1
	
	add_records=[]
	now_line=0
	last_id=-1
	now_id=0
	for ir in inst_records:
		if ir[1]=='f':
			last_id=-1
			now_line=ir[0]
		now_id=ir[3]
		if last_id!=now_id:
			#print now_line, last_id,now_id,ir
			add_records.append([[now_line,[]],[last_id+1,now_id],ir[-1]])
			last_id=now_id
		if ir[1]=='e':
			i=len(add_records)-1
			while i>=0:
				if add_records[i][0][1]==[]:
					add_records[i][0][1]=ir[0]
				else:
					break
				i-=1
	"""
	for a in add_records:
		print '--)))',a
	"""
	#exit(1)
	
	add_rule=[]
	now_line=[]
	i=0
	while i<len(add_records):
		if add_records[i][0]!=now_line:
			now_line=add_records[i][0]
			add_rule.append([now_line,[]])
		add_rule[-1][1].append([add_records[i][1],add_records[i][2]])
		i+=1
	del add_records
	"""
	print 'NO.2','-'*10
	for i in inst_records:
		print i
	"""
	"""
	print '-'*10
	for a in add_records:
		print a
	"""
	"""
	print '-'*10
	for a in add_rule:
		print a
	"""
	#exit(1)
	
	#add the original element ids
	
	i=0
	now_rule=0
	process=0
	process_end=0
	exp='%(\d+)'
	exp2='<label>:(\d+):'
	while i<len(text):
		while 1:
			if now_rule>=len(add_rule):
				process_end=1
				break
			if i<add_rule[now_rule][0][0]:
				process=0
				i+=1
				break
			if i>=add_rule[now_rule][0][0] and (add_rule[now_rule][0][1]==[] or i<=add_rule[now_rule][0][1]):
				process=1
				break
			if i>=add_rule[now_rule][0][0] and add_rule[now_rule][0][1]!=[] and i>add_rule[now_rule][0][1]:
				now_rule+=1
				process=0
				break
		if process_end:
			break
		if process==0:
			continue
		if now_rule>=0:
			results=re.finditer(exp,text[i])
			need_add=[]
			for r in results:
				se=[r.start(),r.end()]
				eid=int(text[i][se[0]+1:se[1]])
				add=inst_add_rule_find(eid,add_rule[now_rule][1])
				need_add.append([se[0]+1,se[1],str(eid+add)])
			results=re.finditer(exp2,text[i])
			for r in results:
				se=[r.start(),r.end()]
				eid=int(text[i][se[0]+8:se[1]-1])
				add=inst_add_rule_find(eid,add_rule[now_rule][1])
				need_add.append([se[0]+8,se[1]-1,str(eid+add)])
			need_add=sorted(need_add,key=lambda x: x[0])
			need_add.reverse()
			for n in need_add:
				text[i]=text[i][0:n[0]]+n[2]+text[i][n[1]:]
		i+=1
	
	#revise the gadgets in inst_record
	"""
	print 'NO.3','-'*10
	for i in inst_records:
		print i
	"""
	i=0
	now_rule=-1
	while i<len(inst_records):
		while now_rule+1<len(add_rule) and inst_records[i][0]>=add_rule[now_rule+1][0][0] and (add_rule[now_rule+1][0][1]==[] or inst_records[i][0]<=add_rule[now_rule+1][0][1]):
			now_rule+=1
		if now_rule>=0:
			if type(inst_records[i][4])==str:
				results=re.finditer(exp,inst_records[i][4])
				need_add=[]
				for r in results:
					se=[r.start(),r.end()]
					eid=int(inst_records[i][4][se[0]+1:se[1]])
					add=inst_add_rule_find(eid,add_rule[now_rule][1])
					need_add.append([se[0]+1,se[1],str(eid+add)])
				need_add=sorted(need_add,key=lambda x: x[0])
				need_add.reverse()
				for n in need_add:
					inst_records[i][4]=inst_records[i][4][0:n[0]]+n[2]+inst_records[i][4][n[1]:]
				#print add_rule[now_rule],'|||',inst_records[i]
				#inst_records[i][3]+=inst_add_rule_find(inst_records[i][3],add_rule[now_rule][1])
			else:
				for i_4 in range(len(inst_records[i][4])):
					results=re.finditer(exp,inst_records[i][4][i_4])
					need_add=[]
					for r in results:
						se=[r.start(),r.end()]
						eid=int(inst_records[i][4][i_4][se[0]+1:se[1]])
						add=inst_add_rule_find(eid,add_rule[now_rule][1])
						need_add.append([se[0]+1,se[1],str(eid+add)])
					need_add=sorted(need_add,key=lambda x: x[0])
					need_add.reverse()
					for n in need_add:
						inst_records[i][4][i_4]=inst_records[i][4][i_4][0:n[0]]+n[2]+inst_records[i][4][i_4][n[1]:]
					#print add_rule[now_rule],'|||',inst_records[i]
					#inst_records[i][3]+=inst_add_rule_find(inst_records[i][3],add_rule[now_rule][1])
		i+=1
	"""
	for a in add_rule:
		print '========++++',a[0]
		for r in a[1]:
			print r
	"""
	#revise the last element id in inst_record, and add the init_base_id
	i=0
	line_count=0
	while i<len(inst_records):
		if inst_records[i][1]=='f':
			line_count=0
			j=i
			while j<len(inst_records):
				if inst_records[j][1]=='e':
					break
				j+=1
			diff=[0 for k in range(j-i)]
			k=i+1
			#print i,j,k,len(diff)
			while k<=j:
				diff[k-i-1]=inst_records[k][3]-inst_records[k-1][3]
				k+=1
		else:
			if line_count==0:
				inst_records[i][3]=inst_records[i-1][3]+diff[line_count]+inst_init(1,[])
			elif inst_records[i-1][1]=='s':
				inst_records[i][3]=inst_records[i-1][3]+diff[line_count]+inst_sl(1,[],[],[],[],'s',rds[i-1][1],0)+inst_sl(1,[],[],[],[],'s',rds[i-1][1],1)
			elif inst_records[i-1][1]=='l':
				inst_records[i][3]=inst_records[i-1][3]+diff[line_count]+inst_sl(1,[],[],[],[],'l',rds[i-1][1],0)+inst_sl(1,[],[],[],[],'l',rds[i-1][1],1)
			elif len(inst_records[i-1][1])>=2 and inst_records[i-1][1][0:2]=='bc':
				inst_records[i][3]=inst_records[i-1][3]+diff[line_count]+inst_sl(1,[],[],inst_records[i-1][4],[],inst_records[i-1][1],rds[i-1][1],0)
			elif inst_records[i-1][1]=='bt':
				inst_records[i][3]=inst_records[i-1][3]+diff[line_count]+inst_sl(1,[],[],inst_records[i-1][4],[],inst_records[i-1][1],rds[i-1][1],0)
			line_count+=1
		i+=1
	
	now_init_base_id=0
	i=0
	for ir in inst_records:
		if ir[1]=='f':
			now_init_base_id=ir[3]+1
			inst_records[i].append(now_init_base_id)
		elif ir[1]=='s' or ir[1]=='l' or (len(ir[1])>2 and ir[1][0:2]=='bc') or ir[1]=='bt':
			inst_records[i].append(now_init_base_id)
		i+=1
	"""
	print 'NO.4','-'*10
	for i in inst_records:
		print i
	"""
	#exit(1)
	
	#instrument
	i=0
	rds.reverse()
	inst_records.reverse()
	for ir in inst_records:
		if ir[1]=='f':
			add=inst_init(0,ir[-1]);
			add.reverse()
			text.insert(ir[0]+1,'<<<<<E<<<<<\n')
			#text.insert(ir[0]+1,'\n')
			for t in add:
				text.insert(ir[0]+1,t)
			text.insert(ir[0]+1,'>>>>>S>>>>>\n')
			#text.insert(ir[0]+1,'\n')
		elif ir[1]=='s' or ir[1]=='l':#instrument after them
			add=inst_sl(0,ir[3]+1,ir[-1],ir[4],ir[2],ir[1],rds[i][1],1);
			add.reverse()
			text.insert(ir[0]+1,'<<<<<E<<<<<'+ir[1]+'\n')
			#text.insert(ir[0]+1,'\n')
			for t in add:
				text.insert(ir[0]+1,t)
			text.insert(ir[0]+1,'>>>>>S>>>>>'+ir[1]+'\n')
			#text.insert(ir[0]+1,'\n')
			add=inst_sl(0,ir[3]+1,ir[-1],ir[4],ir[2],ir[1],rds[i][1],0);
			add.reverse()
			text.insert(ir[0],'<<<<<E<<<<<'+ir[1]+'\n')
			#text.insert(ir[0]+1,'\n')
			for t in add:
				text.insert(ir[0],t)
			text.insert(ir[0],'>>>>>S>>>>>'+ir[1]+'\n')
			#text.insert(ir[0]+1,'\n')
		elif (len(ir[1])>2 and ir[1][0:2]=='bc') or ir[1]=='bt':#instrument before them
			if ir[1]=='bt' or sp_inst_funcs[int(ir[1][2:])]['pos']==0:
				add=inst_sl(0,ir[3]+1,ir[-1],ir[4],ir[2],ir[1],rds[i][1],0);
				add.reverse()
				text.insert(ir[0],'<<<<<E<<<<<'+ir[1]+'\n')
				for t in add:
					text.insert(ir[0],t)
				text.insert(ir[0],'>>>>>S>>>>>'+ir[1]+'\n')
			else:
				add=inst_sl(0,ir[3]+1,ir[-1],ir[4],ir[2],ir[1],rds[i][1],1);
				add.reverse()
				text.insert(ir[0]+1,'<<<<<E<<<<<'+ir[1]+'\n')
				for t in add:
					text.insert(ir[0]+1,t)
				text.insert(ir[0]+1,'>>>>>S>>>>>'+ir[1]+'\n')
		i+=1
	
	# extract the phi node
	phi_nodes=[]
	i=0
	for t in text:
		exp="%\S+ = phi .*"
		if len(re.findall(exp,t))>0:
			#exp="\[ (\S+), (\S+) \]"
			exp="\[ (.*?), (\S+) \]"
			r=re.findall(exp,t)
			temp=[]
			for branch in r:
				temp.append([i,branch[0],branch[1]])
			phi_nodes[-1][1].extend(temp)
			i+=1
			continue
		exp="define .* @(\S+)\((.*)\) .* \{"
		r=re.findall(exp,t)
		if len(r)>0:
			phi_nodes.append([r[0][0],[]])
			i+=1
			continue
		i+=1
	"""
	for p in phi_nodes:
		print p
	"""
	print '-1.extract phi nodes'
	
	#revise the phi node
	label_change=[]
	in_inst=0
	now_label=[-1,-1]
	exp="; <label>:(\d+):"
	exp2="UnifiedReturnBlock:"
	expf="define .* @(\S+)\((.*)\) .* \{"
	expfe="^\s*\}\s*"
	exps=">>>>>S>>>>>"
	expe="<<<<<E<<<<<"
	for t in text:
		#print t
		#print now_label
		#print in_inst
		if in_inst==0:
			r=re.findall(exps,t)
			if len(r)>0:
				in_inst=1
				continue
			r=re.findall(exp,t)
			if len(r)>0:
				label_change[-1][1].append(now_label)
				now_label=[int(r[0]),int(r[0])]
				continue
			r=re.findall(exp2,t)
			if len(r)>0:
				label_change[-1][1].append(now_label)
				now_label=[-1,-1]
				continue
			r=re.findall(expf,t)
			if len(r)>0:
				label_change.append([r[0][0],[]])
				param=r[0][1]
				param=re.sub('\{.*?\}','REP',param)
				param=re.sub('\(.*?\)','',param)
				param=re.sub(' ','',param)
				param=param.split(',')
				count=0
				for p in param:
					if len(p)>0 and p!='...':
						count+=1
				now_label=[count,count]
				continue
			r=re.findall(expfe,t)
			if len(r)>0:
				label_change[-1][1].append(now_label)
				continue
		else:
			r=re.findall(exp,t)
			if len(r)>0:
				now_label[1]=int(r[0])
				continue
			r=re.findall(expe,t)
			if len(r)>0:
				in_inst=0
				continue
	"""
	for l in label_change:
		print l
	"""
	#exit(1)
	if len(label_change)!=len(phi_nodes):
		print 'ERROR, label_change or phi_nodes reads error'
		exit(1)
	i=0
	i_l=-1
	while i<len(label_change):
		#print phi_nodes[i][1]
		i_p=0
		while i_p<len(phi_nodes[i][1]):
			line=phi_nodes[i][1][i_p][0]
			prev=int(phi_nodes[i][1][i_p][2][1:])
			#print '='*10,prev
			#print label_change[i][1]
			i_l=0
			while i_l<len(label_change[i][1]):
				if prev==label_change[i][1][i_l][0]:
					#print label_change[i][1][i_l]
					text[line]=re.sub('%'+str(prev)+' \]','%'+str(label_change[i][1][i_l][1])+' ]',text[line])
					break
				i_l+=1
			i_p+=1
		i+=1
	
	#delete the mark
	exps=">>>>>S>>>>>"
	expe="<<<<<E<<<<<"
	i=0
	while i<len(text):
		if len(text[i])>=11 and (text[i][0:11]==exps or text[i][0:11]==expe):
			text[i]='\n'
		i+=1
	
	#exchange initialize function to the first line of main
	inst_initialize_func(text,exp_mode)
	if exp_mode=='soft' or exp_mode=='roccinstr' or exp_mode=='ori':
		remove_initialize_func(text,'noinstr')
	
	if debugfunc == 1:
		inst_debug(text)
	elif debugfunc == 2:
		inst_rocc_debug(text)
	
	#exit(1)
	f=open(ll_out_name,'wb')
	for t in text:
		f.write(t)
	f.close()

def write_verilogmem_word(f,word):
	s=hex(word)[2:]
	i=len(s)
	while i<8:
		s='0'+s
		i+=1
	f.write(s+',\n')

def write_file_word(f,word,nbyte):
	for i in range(nbyte):
		f.write(chr(word>>(i*8)&0x000000ff))

def inst_write_rds_noinstr_old(asm):
	hash_bits=16
	
	filename='dfi_rds_file'
	
	addr2id=[]
	numaddr2id=0
	max_id=-1
	num_rds=0
	
	hash_values=[]
	hash_poses=[]
	i_a=0
	for a in asm:
		if a['func'] not in exfuncs:
			i_i=0
			for i in a['insts']:
				#print i
				if i['id']!=[]:
					asm[i_a]['insts'][i_i]['hash']=int(i['addr'],16)&((1<<hash_bits)-1)
					if asm[i_a]['insts'][i_i]['hash'] in hash_values:
						i_h=hash_values.index(asm[i_a]['insts'][i_i]['hash'])
						hash_poses[i_h].append([i_a,i_i])
					else:
						hash_values.append(asm[i_a]['insts'][i_i]['hash'])
						hash_poses.append([[i_a,i_i]])
					if i['id']>max_id:
						max_id=i['id']
					numaddr2id+=1
					num_rds+=len(i['rds'])
					#addr2id.append({'addr'int(i['addr'],16),i['id']})
				i_i+=1
		i_a+=1
	max_id+=1
	
	max_hash=0
	for h in hash_poses:
		if len(h)>max_hash:
			max_hash=len(h)
	print 'max hash entry size is ',max_hash
	
	size_word=3+2*numaddr2id+2*max_id+num_rds
	
	#size
	# #of the address to id map entry
	#max id + 1 
	f=open(filename,'wb')
	word=0
	write_file_word(f,size_word,4)
	word+=1
	write_file_word(f,numaddr2id,4)
	write_file_word(f,max_id,4)
	for a in asm:
		if a['func'] not in exfuncs:
			for i in a['insts']:
				if i['id']!=[]:
					write_file_word(f,int(i['addr'],16),4)
					if i['type']=='s':
						ty=0
					elif i['type']=='l':
						ty=1
					elif i['type']=='call':
						ty=2
						if i['libinfo']!=[]:
							if i['libinfo']['s']!=[]:
								ty=ty|(1<<(2*i['libinfo']['s']+2))
							if i['libinfo']['l']!=[]:
								ty=ty|(2<<(2*i['libinfo']['l']+2))
							if i['libinfo']['len']!=[]:
								if type(i['libinfo']['len'])==list:
									ty=ty|(i['libinfo']['len'][0]<<8)
								else:
									ty=ty|(3<<(2*i['libinfo']['len']+2))
					elif i['type']=='ret':
						ty=3
					write_file_word(f,i['id']|(ty<<16),4)
	id2rds=[[] for i in range(max_id)]
	for a in asm:
		if a['func'] not in exfuncs:
			for i in a['insts']:
				if i['id']!=[]:
					id2rds[i['id']]=i['rds']
	off=0
	for i in range(max_id):
		write_file_word(f,off,4)
		off+=len(id2rds[i])
		write_file_word(f,off,4)
	for i in range(max_id):
		for r in id2rds[i]:
			write_file_word(f,r,4)
	f.close()

def inst_write_rds_noinstr(asm):
	hash_bits=16
	hash_shift=0
	
	filename='dfi_rds_file'
	
	verilog_filename='mem.coe'
	
	addr2id=[]
	numaddr2id=0
	max_id=-1
	num_rds=0
	
	all_id=[]#this is for count the different ids
	
	hash_values=[]
	hash_poses=[]
	i_a=0
	for a in asm:
		if a['func'] not in exfuncs:
			i_i=0
			for i in a['insts']:
				#print i
				if i['id']!=[]:
					all_id.append(i['id'])
					asm[i_a]['insts'][i_i]['hash']=(int(i['addr'],16)>>hash_shift)&((1<<hash_bits)-1)
					if asm[i_a]['insts'][i_i]['hash'] in hash_values:
						i_h=hash_values.index(asm[i_a]['insts'][i_i]['hash'])
						hash_poses[i_h].append([i_a,i_i])
					else:
						hash_values.append(asm[i_a]['insts'][i_i]['hash'])
						hash_poses.append([[i_a,i_i]])
					if i['id']>max_id:
						max_id=i['id']
					numaddr2id+=1
					num_rds+=len(i['rds'])
					#addr2id.append({'addr'int(i['addr'],16),i['id']})
				i_i+=1
		i_a+=1
	max_id+=1
	
	all_id=list(set(all_id))
	print '++the number of id is',len(all_id)
	
	max_hash=0
	for h in hash_poses:
		if len(h)>max_hash:
			max_hash=len(h)
	print 'max hash entry size is ',max_hash
	
	size_word=4+(1<<hash_bits)*(3*max_hash)+2*max_id+num_rds
	
	#layout:
	#indicators
	#hash table
	#id 2 rds map
	#rds
	
	#hash table: (e.g. max_hash=3)
	#key=0: [1st addr(64 bit)][1st info|id(32 bit)][2nd addr][2nd info|id(32 bit)][3rd addr][3rd info|id(32 bit)], key=1:...
	#id to rds map:
	#[id=0's begin offset(offset to the beginning of rds)(32 bit)][id=0's end offset][id=1's...]
	
	#size
	#max hash table elements per entry
	#max id + 1 
	f=open(filename,'wb')
	write_file_word(f,size_word,4)
	write_file_word(f,(hash_shift<<16)|hash_bits,4)
	write_file_word(f,max_hash,4)
	write_file_word(f,max_id,4)
	
	fv=open(verilog_filename,'wb')
	fv.write('memory_initialization_radix=16;\n')
	fv.write('memory_initialization_vector=;\n')
	write_verilogmem_word(fv,size_word)
	write_verilogmem_word(fv,(hash_shift<<16)|hash_bits)
	write_verilogmem_word(fv,max_hash)
	write_verilogmem_word(fv,max_id)
	
	hashtable=[0 for i in range((1<<hash_bits)*(3*max_hash))]
	i_a=0
	for a in asm:
		if a['func'] not in exfuncs:
			i_i=0
			for i in a['insts']:
				if i['id']!=[]:
					i_h=hash_values.index(asm[i_a]['insts'][i_i]['hash'])
					i_pos=0
					for hp in hash_poses[i_h]:
						if hp==[i_a,i_i]:
							break
						i_pos+=1
					if i_pos>=len(hash_poses[i_h]):
						print 'error, hash table search error'
						exit(1)
					
					hashtable[3*max_hash*asm[i_a]['insts'][i_i]['hash']+3*i_pos]=(int(i['addr'],16)&0xffffffff)
					hashtable[3*max_hash*asm[i_a]['insts'][i_i]['hash']+3*i_pos+1]=(int(i['addr'],16)>>32)
					
					if i['type']=='s':
						ty=0
					elif i['type']=='l':
						ty=1
					elif i['type']=='call':
						ty=2
						if i['libinfo']!=[]:
							if i['libinfo']['s']!=[]:
								ty=ty|(1<<(2*i['libinfo']['s']+2))
							if i['libinfo']['l']!=[]:
								ty=ty|(2<<(2*i['libinfo']['l']+2))
							if i['libinfo']['len']!=[]:
								if type(i['libinfo']['len'])==list:
									ty=ty|(i['libinfo']['len'][0]<<8)
								else:
									ty=ty|(3<<(2*i['libinfo']['len']+2))
					elif i['type']=='ret':
						ty=3
					hashtable[3*max_hash*asm[i_a]['insts'][i_i]['hash']+3*i_pos+2]=i['id']|(ty<<16)
				i_i+=1
		i_a+=1
	for h in hashtable:
		write_file_word(f,h,4)
		write_verilogmem_word(fv,h)
	
	id2rds=[[] for i in range(max_id)]
	for a in asm:
		if a['func'] not in exfuncs:
			for i in a['insts']:
				if i['id']!=[]:
					id2rds[i['id']]=i['rds']
	off=0
	for i in range(max_id):
		write_file_word(f,off,4)
		write_verilogmem_word(fv,off)
		off+=len(id2rds[i])
		write_file_word(f,off,4)
		write_verilogmem_word(fv,off)
	for i in range(max_id):
		for r in id2rds[i]:
			write_file_word(f,r,2)
			write_verilogmem_word(fv,r)
	fv.write('00000000;\n')
	f.close()
	fv.close()

def inst_write_rds_2core(idinst):
	filename='dfi_rds_file'
	
	#add a dummy entry for function return checking
	idinst.append({'type':'l','func':'ret','id':ret_id,'rds':[ret_id]})
	
	n_addr=0
	for ii in idinst:
		if (ii['type']=='l' or ii['type']=='bc') and ii['func'] not in exfuncs:
			if ii['id']>n_addr:
				n_addr=ii['id']
	n_addr+=1
	print n_addr,'table end addr'
	data=[[0,[]] for i in range(n_addr)]
	for ii in idinst:
		if (ii['type']=='l' or ii['type']=='bc') and ii['func'] not in exfuncs:
			for r in ii['rds']:
				data[ii['id']][1].append(r)
	now_addr=n_addr
	for i in range(n_addr):
		data[i][0]=now_addr|((now_addr+len(data[i][1]))<<16)
		now_addr=now_addr+len(data[i][1])
	mem=[d[0] for d in data]
	for d in data:
		mem.extend(d[1])
	print 'total',len(mem),'words(32bits)'
	f=open(filename,'wb')
	for m in mem:
		for i in range(4):
			f.write(chr(m>>(i*8)&0x000000ff))
	f.close()

def inst_write_rds_roccinstr(idinst):
	filename='dfi_rds_file'
	
	#add a dummy entry for function return checking
	idinst.append({'type':'l','func':'ret','id':ret_id,'rds':[ret_id]})
	
	n_addr=0
	for ii in idinst:
		if (ii['type']=='l' or ii['type']=='bc') and ii['func'] not in exfuncs:
			if ii['id']>n_addr:
				n_addr=ii['id']
	n_addr+=1
	print n_addr,'table end addr'
	data=[[0,[]] for i in range(n_addr)]
	for ii in idinst:
		if (ii['type']=='l' or ii['type']=='bc') and ii['func'] not in exfuncs:
			for r in ii['rds']:
				data[ii['id']][1].append(r)
	now_addr=n_addr*4
	for i in range(n_addr):
		data[i][0]=now_addr|((now_addr+len(data[i][1]))<<32)
		now_addr=now_addr+len(data[i][1])
	mem_hword=[]
	for d in data:
		mem_hword.append(d[0]&0xffff)
		mem_hword.append((d[0]>>16)&0xffff)
		mem_hword.append((d[0]>>32)&0xffff)
		mem_hword.append((d[0]>>48)&0xffff)
	for d in data:
		mem_hword.extend(d[1])
	print 'total',len(mem_hword),'half words(16bits)'
	f=open(filename,'wb')
	for m in mem_hword:
		for i in range(2):
			f.write(chr(m>>(i*8)&0x000000ff))
	f.close()
	
	#for i in range(len(data)):
	#	print i,data[i]

def inst_write_rds(inp):
	if exp_mode=='noinstr':
		inst_write_rds_noinstr(inp)
	elif exp_mode=='roccinstr':
		inst_write_rds_roccinstr(inp)

def inst_test_read_rds():
	filename='dfi_rds_file'
	f=open(filename,'r')
	text=f.read()
	data=[]
	i=0
	temp=0
	for t in text:
		temp|=(ord(t)<<(i*8))
		if i<3:
			i+=1
		else:
			data.append(temp)
			temp=0
			i=0
	if i>0:
		data.append(temp)
	length=data[0]&0xffff
	for i in range(length):
		s=str(i)+': '
		for j in range(data[i]&0xffff,data[i]>>16):
			s+=str(data[j])+', '
		print s
	f.close()

def idinst_opt_rename(idinst):
	
	rds=[[ii['id'],[]] for ii in idinst]
	#we only care about store and load
	i=0
	while i<len(idinst):
		if idinst[i]['type']!='s' and idinst[i]['type']!='l' and idinst[i]['type']!='bc':
			rds[i][0]=[]
		i+=1
	#generate the reaching definition sets
	i_r=0
	i_i=0
	while i_r<len(rds) and i_i<len(idinst) and idinst[i_i]['inst']!='dummy':
		if rds[i_r][0]==[] or rds[i_r][0]<idinst[i_i]['id']:
			i_r+=1
		elif rds[i_r][0]>idinst[i_i]['id']:
			i_i+=1
		else:
			rds[i_r][1]=idinst[i_i]['rds']
			i_r+=1
			i_i+=1
	#get the new name
	renames=rds_rename(rds)
	rds=rds_rename_perform(renames,rds)
	
	#delete the out of range definition identifier
	rds_delete(rds)
	
	#do membership check opt
	renames=rds_membership_rename(rds)
	rds=rds_rename_perform(renames,rds)
	
	#rewrite the result to idinst. NOTE, this will delete all the node id except store and load
	i=0
	while i<len(idinst):
		idinst[i]['id']=rds[i][0]
		idinst[i]['rds']=rds[i][1]
		i+=1

def idinst_opt_setcheckdef(idinst):
	#this is for idinst optimization, two intrumentations should be in the same basic block, related to same data.
	#opt1, same identifier
	i=0
	delete=[]
	while i<len(idinst):
		if idinst[i]['type']=='s':
			exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
			r=re.findall(exp,idinst[i]['inst'])
			data=r[0]
			j=i+1
			while j<len(idinst):
				if idinst[j]['type']=='s':
					exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0]:
						if idinst[i]['id']==idinst[j]['id']:
							if not (j in delete):
								idinst[j]['del']=1
								#delete.append(j)
						break
				elif idinst[j]['type'][0]=='b':
					break
				j+=1
		i+=1
	#print delete
	delete=sorted(list(set(delete)))
	delete.reverse()
	for d in delete:
		#print idinst[d]['inst']
		del idinst[d]
	#opt2, no checkdef
	i=0
	delete=[]
	while i<len(idinst):
		if idinst[i]['del']:
			i+=1
			continue
		if idinst[i]['type']=='s':
			exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
			r=re.findall(exp,idinst[i]['inst'])
			data=r[0]
			j=i+1
			while j<len(idinst):
				if idinst[j]['del']:
					j+=1
					continue
				if idinst[j]['type']=='s':
					exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0]:
						if not (i in delete):
							idinst[i]['del']=1
							#delete.append(i)
						break
				elif idinst[j]['type']=='l':
					exp="%\S+ = load .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0]:
						break
				elif idinst[j]['type'][0]=='b':
					break
				j+=1
		i+=1
	#print delete
	delete=sorted(list(set(delete)))
	delete.reverse()
	for d in delete:
		del idinst[d]
	#opt3, checkdef right after setdef
	i=0
	delete=[]
	while i<len(idinst):
		if idinst[i]['del']:
			i+=1
			continue
		if idinst[i]['type']=='s':
			exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
			r=re.findall(exp,idinst[i]['inst'])
			data=r[0]
			j=i+1
			while j<len(idinst):
				if idinst[j]['del']:
					j+=1
					continue
				if idinst[j]['type']=='s':
					exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0]:
						break
				elif idinst[j]['type']=='l':
					exp="%\S+ = load .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0] and (idinst[i]['id'] in idinst[j]['rds']):
						if not (j in delete):
							idinst[j]['del']=1
							#delete.append(j)
				elif idinst[j]['type'][0]=='b':
					break
				j+=1
		i+=1
	delete=sorted(list(set(delete)))
	delete.reverse()
	for d in delete:
		del idinst[d]
	#opt4, later checkdef can only check former checkdef
	i=0
	delete=[]
	while i<len(idinst):
		if idinst[i]['del']:
			i+=1
			continue
		if idinst[i]['type']=='l':
			exp="%\S+ = load .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
			r=re.findall(exp,idinst[i]['inst'])
			data=r[0]
			j=i+1
			while j<len(idinst):
				if idinst[j]['del']:
					j+=1
					continue
				if idinst[j]['type']=='s':
					exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0]:
						break
				elif idinst[j]['type']=='l':
					exp="%\S+ = load .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0]:
						if idinst[i]['rds']==idinst[j]['rds']:
							if not (j in delete):
								idinst[j]['del']=1
								#delete.append(j)
							break
						else:
							delete2=[]
							i_r=0
							while i_r<len(idinst[j]['rds']):
								if idinst[j]['rds'][i_r] not in idinst[i]['rds']:
									delete2.append(i_r)
								i_r+=1
							delete2.reverse()
							for d in delete2:
								del idinst[j]['rds'][d]
							break
				elif idinst[j]['type'][0]=='b':
					break
				j+=1
		i+=1
	#print delete
	delete=sorted(list(set(delete)))
	delete.reverse()
	for d in delete:
		del idinst[d]
	#opt5, delete setdef if it is just checked
	i=0
	delete=[]
	while i<len(idinst):
		if idinst[i]['del']:
			i+=1
			continue
		if idinst[i]['type']=='l':
			exp="%\S+ = load .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
			r=re.findall(exp,idinst[i]['inst'])
			data=r[0]
			j=i+1
			while j<len(idinst):
				if idinst[j]['del']:
					j+=1
					continue
				if idinst[j]['type']=='s':
					exp="store .*,(?![^(]*\\))(?![^{]*\\}) (.*),(?![^(]*\\))(?![^{]*\\}) align \S+"
					r=re.findall(exp,idinst[j]['inst'])
					if data==r[0]:
						if idinst[i]['rds']==[idinst[j]['id']]:
							if j not in delete:
								idinst[j]['del']=1
								#delete.append(j)
						break
				elif idinst[j]['type'][0]=='b':
					break
				j+=1
		i+=1
	#print delete
	delete=sorted(list(set(delete)))
	delete.reverse()
	for d in delete:
		del idinst[d]
	
def analysis_code(idinst):
	n_s=0
	n_l=0
	n_br=0
	n_bc=0
	n_bs=0
	n_bt=0
	for ii in idinst:
		if ii['type']=='br':
			n_br+=1
		elif ii['type']=='bc':
			n_bc+=1
		elif ii['type']=='bs':
			n_bs+=1
		elif ii['type']=='bt':
			n_bt+=1
		elif ii['type']=='l':
			n_l+=1
		elif ii['type']=='s':
			n_s+=1
	
	print 'store count '+str(n_s)+'. '+str(float(n_s)/len(idinst)*100)+'%'
	print 'load count '+str(n_l)+'. '+str(float(n_l)/len(idinst)*100)+'%'
	print 'branch count '+str(n_br)+'. '+str(float(n_br)/len(idinst)*100)+'%'
	print 'call count '+str(n_bc)+'. '+str(float(n_bc)/len(idinst)*100)+'%'
	print 'switch count '+str(n_bs)+'. '+str(float(n_bs)/len(idinst)*100)+'%'
	print 'ret count '+str(n_bt)+'. '+str(float(n_bt)/len(idinst)*100)+'%'
	

def show_delete_inst(inst_records,show_del_range):
	for i in range(show_del_range):
		with open('random_delete_record_'+str(i), 'r') as f:
			[delete,delete_old]=pickle.load(f)
		print '====='+str(i)+'====='
		for d in delete:
			print d,inst_records[d]

def get_all_special_exclusive_funcs():
	text=read_file(sfuncfile+'.ll')
	exp="define .* @(\S+)\((.*)\) .* \{"
	for t in text:
		r=re.findall(exp,t)
		if len(r)>0:
			nonexcludedfound=0
			for ne in nonexcluded:
				if len(re.findall(ne,r[0][0]))>0:
					nonexcludedfound=1
					break
			if nonexcludedfound:
				continue
			exfuncs.append(r[0][0])
			for sfk in sfunc.keys():
				if len(re.findall(sfk,r[0][0]))>0:
					sfunc[sfk]=r[0][0]
					break
	
def disp_statistic(idinst):
	n_s=0
	max_sid=0
	n_l=0
	max_lid=0
	for ii in idinst:
		if 'func' in ii.keys() and ii['func'] not in exfuncs:
			if ii['type']=='l':
				if ii['id']>max_lid:
					max_lid=ii['id']
				n_l+=1
			elif ii['type']=='s':
				if ii['id']>max_sid:
					max_sid=ii['id']
				n_s+=1
	print '# of store',n_s,'max id is',max_sid
	print '# of load',n_l,'max id is',max_lid

def usr_specify_rds(idinst,filename):
	text=read_file(filename)
	state=0
	for t in text:
		if state==0:
			uid=int(t)
			state=1
		else:
			urds=re.findall('(\S+)',t)
			mode=0
			if urds[0]=='+':
				mode=1#add mode
				del urds[0]
			elif urds[0]=='-':
				mode=2#delete mode
				del urds[0]
			elif urds[0]=='--':
				mode=3#delete all that have
				del urds[0]
			if mode==0:
				i=0
				while i<len(idinst):
					if idinst[i]['id']==uid:
						idinst[i]['rds']=[int(r) for r in urds]
						print 'user specified rds:',idinst[i]
						break
					i+=1
			elif mode==1:
				i=0
				while i<len(idinst):
					if idinst[i]['id']==uid:
						idinst[i]['rds'].extend([int(r) for r in urds])
						print 'user specified rds:',idinst[i]
						break
					i+=1
			elif mode==2:
				i=0
				while i<len(idinst):
					if idinst[i]['id']==uid:
						delete=[]
						for r in urds:
							if int(r) in idinst[i]['rds']:
								delete.append(idinst[i]['rds'].index(int(r)))
						delete=sorted(delete)
						delete.reverse()
						for d in delete:
							del idinst[i]['rds'][d]
						print 'user specified rds:',idinst[i]
						break
					i+=1
			else:
				i=0
				while i<len(idinst):
					found=0
					delete=[]
					for r in urds:
						if int(r) in idinst[i]['rds']:
							delete.append(idinst[i]['rds'].index(int(r)))
					delete=sorted(delete)
					delete.reverse()
					for d in delete:
						found=1
						del idinst[i]['rds'][d]
					if found:
						print 'user specified rds:',idinst[i]
					i+=1
			state=0

def backward_slices_analysis_extract(text):
	#parse the cfg inside each function
	funcs=[]
	state=0
	i=0
	for t in text:
		if state==0:
			exp="define .* @(\S+)\((.*)\) .* \{"
			r=re.findall(exp,t)
			if len(r)>0:
				funcs.append({'name':r[0][0],'var':r[0][1],'s':i,'e':[],'block':[]})
				funcs[-1]['block'].append({'label':[],'s':[],'e':[],'target':[],'to':[],'from':[],'instr':[]})
				state=1
		elif state==1:
			exp="^\s*\}"
			r=re.findall(exp,t)
			if len(r)>0:
				if len(funcs[-1]['block'])>0 and funcs[-1]['block'][-1]['s']==[]:
					del funcs[-1]['block'][-1]
				funcs[-1]['e']=i
				state=0
				continue
			if funcs[-1]['block'][-1]['s']==[]:
				funcs[-1]['block'][-1]['s']=i
			funcs[-1]['block'][-1]['e']=i
			exp="; <label>:(\d+):"
			r=re.findall(exp,t)
			if len(r)>0:
				funcs[-1]['block'][-1]['label']=int(r[0])
				continue
			exp="^\s*br label %(\d+)"
			r=re.findall(exp,t)
			if len(r)>0:
				funcs[-1]['block'][-1]['target'].append(int(r[0]))
				funcs[-1]['block'].append({'label':[],'s':[],'e':[],'target':[],'to':[],'from':[],'instr':[]})
				continue
			exp="^\s*br .* label %(\d+), label %(\d+)"
			r=re.findall(exp,t)
			if len(r)>0:
				funcs[-1]['block'][-1]['target'].append(int(r[0][0]))
				funcs[-1]['block'][-1]['target'].append(int(r[0][1]))
				funcs[-1]['block'].append({'label':[],'s':[],'e':[],'target':[],'to':[],'from':[],'instr':[]})
				continue
			exp="^\s*unreachable"
			r=re.findall(exp,t)
			if len(r)>0:
				funcs[-1]['block'].append({'label':[],'s':[],'e':[],'target':[],'to':[],'from':[],'instr':[]})
				continue
			exp="^\s*switch .* label %(\d+) \["
			r=re.findall(exp,t)
			if len(r)>0:
				temp=[int(r[0])]
				j=i+1
				while 1:
					exp="^.* label %(\d+)"
					r=re.findall(exp,text[j])
					if len(r)>0:
						temp.append(int(r[0]))
					exp="^\s*\]"
					r=re.findall(exp,text[j])
					if len(r)>0:
						funcs[-1]['block'][-1]['target']=temp
						funcs[-1]['block'].append({'label':[],'s':[],'e':[],'target':[],'to':[],'from':[],'instr':[]})
						break
					j+=1
		i+=1
	i=0
	while i<len(funcs[-1]['block']):
		i2=0
		while i2<len(funcs[-1]['block']):
			for j in range(len(funcs[-1]['block'][i2]['target'])):
				if funcs[-1]['block'][i2]['target'][j]==funcs[-1]['block'][i]['label']:
					funcs[-1]['block'][i2]['to'].append(i)
			i2+=1
		i+=1
	i=0
	while i<len(funcs[-1]['block']):
		for j in range(len(funcs[-1]['block'][i]['to'])):
			funcs[-1]['block'][funcs[-1]['block'][i]['to'][j]]['from'].append(i)
		i+=1
	
	#parse the read/write data of each instruction
	for f in funcs:
		for b in f['block']:
			#print b
			i=b['s']
			while i<b['e']+1:
				exp="^\s*%(\S+)\s*=(.*)"
				r=re.findall(exp,text[i])
				if len(r)>0:
					target=r[0][0]
					exp="%(\d+)"
					source=[]
					r=re.findall(exp,r[0][1])
					for s in r:
						if s[-1]==',':
							s=s[0:-1]
						source.append(s)
					exp="^\s*%\d+\s*=\s*load"
					r=re.findall(exp,text[i])
					exp="^\s*%\d+\s*=\s*phi (.*)"
					r2=re.findall(exp,text[i])
					if len(r)>0:
						ty="l"
					elif len(r2)>0:
						ty="p"
						exp="\[ (\S+), (\S+) \]"
						r3=re.findall(exp,r2[0])
						fr=[]
						source=[]
						for group in r3:
							if group[0][0]=='%':
								source.append(group[0][1:])
							fr.append(group[1])
					else:
						ty="?"
					if ty=='p':
						b['instr'].append({'p':i,'target':[target],'source':source,'from':fr,'type':ty})
					else:	
						b['instr'].append({'p':i,'target':[target],'source':source,'type':ty})
					i+=1
					continue
				exp="%(\d+)"
				r=re.findall(exp,text[i])
				source=[]
				for s in r:
					if s[-1]==',':
						s=s[0:-1]
					source.append(s)
				exp="^\s*store"
				r=re.findall(exp,text[i])
				exp="^\s*br .*%(\d+) label %(\d+), label %(\d+)"
				r2=re.findall(exp,text[i])
				if len(r)>0:
					ty="s"
				elif len(r2)>0:
					ty="bcond"
				else:
					ty="?"
				b['instr'].append({'p':i,'target':[],'source':source,'type':ty})
				i+=1
	"""
	for f in funcs:
		print '=======',f['name']
		for b in f['block']:
			print '---',b['label']
			for i in b['instr']:
				print i
	"""
	return funcs

def backward_slices_analysis_gadgets_def(nowt,ib,ii,func,flag):
	#find the definition of variable nowt, start from block ib's ii instruction
	if flag[ib]:
		return []
	flag[ib]=1
	i=ii
	while i>=0:
		if nowt in func['block'][ib]['instr'][i]['target']:
			return [ib,i]
		i-=1
	for fm in func['block'][ib]['from']:
		r=backward_slices_analysis_gadgets_def(nowt,fm,len(func['block'][fm]['instr'])-1,func,flag)
		if r!=[]:
			return r
	return []

def backward_slices_analysis_gadgets_def_phi(ib,func,flag):
	#find the definition of variable nowt, start from block ib's ii instruction
	if flag[ib]:
		return []
	flag[ib]=1
	if func['block'][ib]['instr'][-1]['type']=='bcond':
		r=backward_slices_analysis_gadgets_def(func['block'][ib]['instr'][-1]['source'][0],ib,len(func['block'][ib]['instr'])-1,func,flag)
		return r
	else:
		for fm in func['block'][ib]['from']:
			r=backward_slices_analysis_gadgets_def_phi(fm,func,flag)
			return r
	return []

def backward_slices_analysis_gadgets_search(ib,ii,func,gadgets):
	source=func['block'][ib]['instr'][ii]['source']
	is_input=0
	if func['block'][ib]['instr'][ii]['type']=='p':
		for s in source:
			flag=[0 for b in func['block']]
			r=backward_slices_analysis_gadgets_def(s,ib,ii,func,flag)
			if r!=[]:
				gadgets.append([r[0],r[1],0])
				backward_slices_analysis_gadgets_search(r[0],r[1],func,gadgets)
			else:
				is_input=1
		for fr in func['block'][ib]['instr'][ii]['from']:
			for fm in func['block'][ib]['from']:
				if func['block'][fm]['label']==fr:
					flag=[0 for b in func['block']]
					r=backward_slices_analysis_gadgets_def_phi(fm,func,flag)
					if r!=[]:
						gadgets.append([r[0],r[1],0])
						backward_slices_analysis_gadgets_search(r[0],r[1],func,gadgets)
					else:
						is_input=1
		if is_input:
			if [ib,ii,0] in gadgets:
				gadgets[gadgets.index([ib,ii,0])][2]=1
	else:
		for s in source:
			flag=[0 for b in func['block']]
			r=backward_slices_analysis_gadgets_def(s,ib,ii,func,flag)
			if r!=[]:
				gadgets.append([r[0],r[1],0])
				backward_slices_analysis_gadgets_search(r[0],r[1],func,gadgets)
			else:
				is_input=1
		if is_input:
			if [ib,ii,0] in gadgets:
				gadgets[gadgets.index([ib,ii,0])][2]=1
	

def backward_slices_analysis_gadgets(text,func):
	"""
	ib=0
	print '========'
	for b in func['block']:
		ii=0
		for it in b['instr']:
			print ib,ii,text[it['p']]
			ii+=1
		ib+=1
	"""
	results=[]
	ib=0
	for b in func['block']:
		ii=0
		for it in b['instr']:
			if it['type']=='s':
				#print 'store at',ib,ii
				gadgets=[[ib,ii,0]]
				backward_slices_analysis_gadgets_search(ib,ii,func,gadgets)
				gadgets.reverse()
				for g in gadgets:
					if g[2]:
						if func['block'][g[0]]['instr'][g[1]]['type']=='l':
							results.append(gadgets)
			ii+=1
		ib+=1
	"""
	print '===results==='
	for r in results:
		print r
	"""
	return results

def backward_slices_analysis(text):
	funcs=backward_slices_analysis_extract(text)
	delete=[]
	i=0
	for f in funcs:
		#print f['name']
		found=0
		for ef in exfuncs:
			if len(re.findall(ef,f['name']))>0:
				found=1
				break
		if found:
			delete.append(i)
		i+=1
	delete.reverse()
	for d in delete:
		del funcs[d]
	gadgets=[]
	for f in funcs:
		results=backward_slices_analysis_gadgets(text,f)
		temp=[[[f['block'][g[0]]['instr'][g[1]]['p'],g[2]] for g in r] for r in results]
		gadgets.extend(temp)
	print 'total',len(gadgets),'gadgets'
	"""
	for g in gadgets:
		print '-------'
		for i in g:
			print i[1],text[i[0]][0:-1]
	"""
	return gadgets

def delete_checks_by_dop(gadgets,idinst,text):
	textptr=[-1 for ii in idinst]
	i_i=0
	i_t=0
	while i_i<len(idinst) and i_t<len(text):
		if idinst[i_i]['type'] in ['s','l','bc']:
			if i_i!=len(idinst)-1 and inst_cmp_rd_ll(idinst[i_i]['type'],idinst[i_i]['inst'],text[i_t]):
				textptr[i_i]=i_t
				i_i+=1
			i_t+=1
		else:
			i_i+=1
	
	if i_i<len(idinst)-1:
		print "ERROR, .rd not match .ll",idinst[i_i],i_i,len(idinst)
		exit(1)
	
	valid=[]
	for g in gadgets:
		#print g
		for i in g:
			if i[1]:
				if i[0] in textptr:
					idx=textptr.index(i[0])
					if idinst[idx]['type']=='l':
						valid.append(idx)
	
	#we don't case about library this time
	for ii in idinst:
		if ii['type']=='bc':
			ii['del']=1
	
	for ii in idinst:
		if ii['type']=='l':
			ii['del']=1
	for v in valid:
		idinst[v]['del']=0
	
	#delete those stores with no valid load defined by them
	ids=[ii['id'] for ii in idinst]
	drs=[[ii['id'],[]] for ii in idinst]
	i=0
	for ii in idinst:
		for r in ii['rds']:
			i2=0
			while i2<len(drs):
				if ids[i2]==r:
					drs[i2][1].append(ii['id'])
				i2+=1
		i+=1
	i=0
	for d in drs:
		if idinst[i]['type']=='s':
			#print d
			found=0
			for dd in d[1]:
				if idinst[ids.index(dd)]['del']==0:
					found=1
					break
			if found==0:
				idinst[i]['del']=1
		i+=1

def make_align4(text):
	i=0
	while i<len(text):
		text[i]=text[i].replace("align 1","align 4")
		text[i]=text[i].replace("align 2","align 4")
		i+=1	

def get_ret_id(idinst):
	#function return id is the largest id+1
	global ret_id
	for ii in idinst:
		if ii['id']!=[] and ii['id']>ret_id:
			ret_id=ii['id']
		if len(ii['rds'])>0 and max(ii['rds'])>ret_id:
			ret_id=max(ii['rds'])
	ret_id+=1

def do_hdfi_change(idinst,filename):
	global ret_id
	
	ret_id=9999
	
	text=read_file(filename)
	changed=[]
	state=0
	for t in text:
		if state==0:
			uid=int(t)
			state=1
		else:
			urds=re.findall('(\S+)',t)
			mode=0
			if urds[0]=='s':
				mode=1
			del urds[0]
			if mode==0:
				i=0
				while i<len(idinst):
					if idinst[i]['id']==uid:
						idinst[i]['rds']=[int(r) for r in urds]
						print 'user specified rds:',idinst[i]
						changed.append(i)
						break
					i+=1
			elif mode==1:
				i=0
				while i<len(idinst):
					if idinst[i]['id']==uid:
						idinst[i]['id']=int(urds[0])
						print 'user specified rds:',idinst[i]
						changed.append(i)
						break
					i+=1
			state=0
	i=0
	while i<len(idinst):
		if i not in changed:
			if type(idinst[i]['id'])==int:
				idinst[i]['id']=9999
			if len(idinst[i]['rds'])>0:
				idinst[i]['rds']=[]
		i+=1

def extract_sl_old(i,insts,mark,mark2,num):
	#mark: special words
	#num: # of the special words for a group
	j=i
	while j<len(insts) and insts[j]==mark:
		j+=1
	size=(j-i)/num
	if size==0:
		return [[],0,j]
	temp=[]
	while j<len(insts) and insts[j]!=mark2:
		temp=insts[j]
		j+=1
	return temp,size,j+size*num

def extract_sl(i,insts,mark,mark2,num):
	#mark: special words
	#num: # of the special words for a group
	j=i
	size=0
	count=0
	state=0
	temp=[]
	while j<len(insts):
		if state==0:
			if insts[j]==mark:
				count+=1
			if count==num:
				count=0
				state=1
		elif state==1:
			if insts[j]==mark:
				state=0
				count=0
				temp=[]
				continue
			elif insts[j]==mark2:
				state=2
				count=0
				continue
			elif asm_test_sl_valid(insts[j]):
				temp=insts[j]
		elif state==2:
			if insts[j]==mark2:
				count+=1
			if count==num:
				count=0
				size+=1
				if temp==[] and j+1<len(insts) and insts[j+1]==mark:
					state=0
				else:
					return temp,size,j
		j+=1
	return [],0,j

def parse_asm(text):
	asm=[]
	nowsec=''
	state=0
	temp=[]
	count=-1
	off=0
	for string in text:
		sec=re.findall("Disassembly of section (\S+):",string)
		if len(sec)>0:
			nowsec=sec[0]
		head=re.findall("^([\d+,a-f]+) <(\S+)>",string)
		if len(head)!=0:
			temp={'func':head[0][1],'sec':nowsec,'insts':[],'sls':[]}
			asm.append(temp)
			state=0
			count=-1
			off=0
		else:
			r=re.findall("^\s*[\d+,a-f]+:.......................(.*)",string)
			if len(r)>0:
				asm[-1]['insts'].append(r[0])
	
	mark='add	zero,zero,zero'
	mark2='nop'
	i_a=0
	while i_a<len(asm):
		i_i=0
		while i_i<len(asm[i_a]['insts']):
			if asm[i_a]['insts'][i_i]==mark:
				temp,size,i_i=extract_sl(i_i,asm[i_a]['insts'],mark,mark2,2)
				if temp!=[]:
					asm[i_a]['sls'].append([temp,size])
			else:
				i_i+=1
		del asm[i_a]['insts']
		i_a+=1
	#exit(1)
	return asm

def asm_test_sl_valid(s0):
	s0ori=s0
	r=re.findall('(\S+)',s0)
	if len(r)>1:
		op0=r[0]
		s0=r[1]
	#print '----',s0
	s0=re.sub('\(\S+\)','(reg)',s0)
	r=re.findall('([^,]+)',s0)
	found=0
	case=0
	for e in r:
		if '(' in e:
			found=1
			case=0
			s0=re.sub('\(\S+\)','',e)
	if found==0:
		for e in r:
			if len(re.findall('(^[0-9,a-f,x]+)',e))>0:
				if len(e)>2 and e[0:2]=='0x':
					found=1
					break
	if found==0:
		return 0
	return 1

def asm_match(s0,s1,debug):
	s0ori=s0
	s1ori=s1
	r=re.findall('(\S+)',s0)
	if len(r)>1:
		op0=r[0]
		s0=r[1]
	#print '----',s0
	s0=re.sub('\(\S+\)','(reg)',s0)
	r=re.findall('([^,]+)',s0)
	#r=re.findall('(?<!\()[^,]+(?![\w\s]*[\)])',s0)
	#print r
	found=0
	case=0
	for e in r:
		if '(' in e:
			found=1
			case=0
			s0=re.sub('\(\S+\)','',e)
	if found==0:
		for e in r:
			if len(re.findall('(^[0-9,a-f,x]+)',e))>0:
				if len(e)>2 and e[0:2]=='0x':
					found=1
					case=1
					s0=e
					break
	if found==0:
		print 'error: cannot parse store/load instruction'
		print s0ori
		exit(1)
	
	r=re.findall('(\S+)',s1)
	if len(r)>1:
		if r[0]=='lea':
			return 0
		op1=r[0]
		s1=r[1]
	else:
		return 0
	if debug:
		print s1
	
	#r=re.findall('(?<!\()[^,]+(?![\w\s]*[\)])',s1)
	
	s1=re.sub('\(\S+\)','(reg)',s1)
	s1=re.sub('\*\(reg\)','(reg)',s1)
	r=re.findall('([^,]+)',s1)
	#print r
	found=0
	case1=0
	for e in r:
		if '(' in e:
			found=1
			case1=0
			s1=re.sub('\(\S+\)','',e)
	if found==0:
		for e in r:
			if len(re.findall('(^[0-9,a-f,x]+)',e))>0:
				if len(e)>2 and e[0:2]=='0x':
					found=1
					case1=1
					s1=e
					break
	if found==0:
		return 0
	
	if case!=case1:
		return 0
	
	if s0=='':
		s0='0'
	if s1=='':
		s1='0'
	try:
		n0=int(s0,16)
	except:
		try:
			n0=int(s0[1:],16)
		except:
			return 0
	try:
		n1=int(s1,16)
	except:
		try:
			n1=int(s1[1:],16)
		except:
			return 0
	if debug:
		print n0,n1,abs(n0-n1),min(abs(n0),abs(n1))*3,abs(n0-n1)<=(min(abs(n0),abs(n1))*3)
	if (abs(n0)>=0x100000 and abs(n1)>=0x100000) or (op0=='movsd' and len(op1)>2 and op1[-2:]=='sd') or (op1=='movsd' and len(op0)>2 and op0[-2:]=='sd'):
		if abs(n0-n1)<=abs(n0)/2 or abs(n0-n1)<=abs(n1)/2 or abs(n0-n1)<=0x30 or abs(n0-n1)<=(min(abs(n0),abs(n1))*3):
			return 1
		else:
			return 0
	elif abs(n0)<0x100000 and abs(n1)<0x100000:
		if abs(n0-n1)<=0x10:
			return 1
		else:
			return 0
	else:
		return 0
	return 0
		
def extract_asm_address(text,parsedsl):
	asm=[]
	nowsec=''
	state=0
	temp=[]
	i_psd=-1
	i_sls=0
	debug=0
	for string in text:
		sec=re.findall("Disassembly of section (\S+):",string)
		if len(sec)>0:
			nowsec=sec[0]
		head=re.findall("^([\d+,a-f]+) <(\S+)>",string)
		if len(head)!=0:
			temp={'func':head[0][1],'sec':nowsec,'insts':[]}
			asm.append(temp)
			i_psd=0
			while i_psd<len(parsedsl):
				if parsedsl[i_psd]['func']==head[0][1]:
					break
				i_psd+=1
			i_sls=0
			#print i_psd,len(parsedsl),head[0][1]
		else:
			r=re.findall("^\s*([\d+,a-f]+):.......................(.*)",string)
			if len(r)>0:
				
				if 0 and i_sls<len(parsedsl[i_psd]['sls']) and asm[-1]['func']=='XNU':
					debug=1
					print parsedsl[i_psd]['sls'][i_sls],r[0][1]
					print i_sls,len(parsedsl[i_psd]['sls'])
					print asm_match(parsedsl[i_psd]['sls'][i_sls][0],r[0][1],0)
				else:
					debug=0
				
				if i_sls<len(parsedsl[i_psd]['sls']) and asm_match(parsedsl[i_psd]['sls'][i_sls][0],r[0][1],debug):
					for i_num in range(parsedsl[i_psd]['sls'][i_sls][1]):
						asm[-1]['insts'].append({'type':'sl','addr':r[0][0],'id':[],'rds':[],'del':[],'hash':[]})
					i_sls+=1
			r=re.findall("^\s*([\d+,a-f]+):.......................callq  \S+ <(\S+)>",string)
			if len(r)>0:
				asm[-1]['insts'].append({'type':'call','addr':r[0][0],'func':r[0][1],'id':[],'rds':[],'del':[],'libinfo':[],'hash':[]})
				continue
			r=re.findall("^\s*([\d+,a-f]+):.......................callq  \S+",string)
			if len(r)>0:
				asm[-1]['insts'].append({'type':'call','addr':r[0],'func':[],'id':[],'rds':[],'del':[],'libinfo':[],'hash':[]})
				continue
			r=re.findall("^\s*([\d+,a-f]+):.......................retq",string)
			if len(r)>0:
				asm[-1]['insts'].append({'type':'ret','addr':r[0],'id':[],'rds':[],'del':[],'hash':[]})
				continue
	for a in asm:
		sl_count=0
		for i in a['insts']:
			if i['type']=='sl':
				sl_count+=1
		for p in parsedsl:
			if a['func']==p['func']:
				sl_count2=0
				for sl in p['sls']:
					sl_count2+=sl[1]
				if sl_count!=sl_count2:
					print 'WARNING, aux asm not match asm,',a['func'],sl_count,sl_count2
					#exit(1)
				break
	#exit(0)
	return asm

def test_asm_extension_additionalbits(text):
	asm=[]
	nowsec=''
	state=0
	temp=[]
	i_psd=-1
	i_sls=0
	debug=0
	for string in text:
		sec=re.findall("Disassembly of section (\S+):",string)
		if len(sec)>0:
			nowsec=sec[0]
		head=re.findall("^([\d+,a-f]+) <(\S+)>",string)
		if len(head)!=0:
			temp={'func':head[0][1],'sec':nowsec,'insts':[]}
			asm.append(temp)
			i_sls=0
			#print i_psd,len(parsedsl),head[0][1]
		else:
			r=re.findall("^\s*([\d+,a-f]+):(.{0,23})(.*)",string)
			if len(r)>0:
				#print '----',string
				#print '----',r[0][1]
				#print '----',r[0][2]
				r2=re.findall("([\d+,a-f])",r[0][1])
				#print '++++',len(r2)
				asm[-1]['insts'].append({'type':'normal','addr':r[0][0],'func':[],'ins':r[0][2],'code':r[0][1],'codebits':4*len(r2)})
				continue
			r=re.findall("^\s*([\d+,a-f]+):(.{0,23})callq  \S+ <(\S+)>",string)
			if len(r)>0:
				#print '----',string
				#print '----',r[0][1]
				#print '----',r[0][2]
				r2=re.findall("([\d+,a-f])",r[0][1])
				#print '++++',len(r2)
				asm[-1]['insts'].append({'type':'call','addr':r[0][0],'func':r[0][2],'ins':r[0][2],'code':r[0][1],'codebits':4*len(r2)})
				continue
			r=re.findall("^\s*([\d+,a-f]+):(.{0,23})callq  \S+",string)
			if len(r)>0:
				#print '----',string
				#print '----',r[0][1]
				#print '----',r[0][2]
				r2=re.findall("([\d+,a-f])",r[0][1])
				#print '++++',len(r2)
				asm[-1]['insts'].append({'type':'call','addr':r[0][0],'func':[],'ins':r[0][2],'code':r[0][1],'codebits':4*len(r2)})
				continue
			r=re.findall("^\s*([\d+,a-f]+):(.{0,23})retq",string)
			if len(r)>0:
				#print '----',string
				#print '----',r[0][1]
				#print '----',r[0][2]
				r2=re.findall("([\d+,a-f])",r[0][1])
				#print '++++',len(r2)
				asm[-1]['insts'].append({'type':'ret','addr':r[0][0],'id':[],'code':r[0][1],'codebits':4*len(r2)})
				continue
	addbits=0
	totalbits=0
	"""
	norbits=2
	slbits=18
	cbits=23
	clbits=39
	"""
	norbits=0
	slbits=16
	cbits=16
	clbits=16
	rbits=16
	for a in asm:
		for it in a['insts']:
			totalbits+=it['codebits']
			if len(re.findall("(\S+)",it['ins']))>0:
				if it['type'] in ['normal']:
					r=re.findall("(\(.+\))",it['ins'])
					if len(r)>0:
						#print it['ins']
						addbits+=slbits
					else:
						addbits+=norbits
				elif it['type'] in ['ret']:
					addbits+=rbits
				else:
					r=findall("(\(.+\))",it['ins'])
					if len(r)>0:
						#print it['ins']
						addbits+=clbits
					else:
						addbits+=cbits
	print 'Percentage of additional bits:',float(addbits)/totalbits

def map_rds(idinst,ll_name,asm):
	text=read_file(ll_name)
	replace_intrinsic_func(text)
	#find out after which lines we should add instrumented code
	inst_records=[]#text pos,type,node id(rd analysis),last element id,target/func name
	rds=[]
	i_i=0
	i_t=0
	now_id=-1
	last_f=-1
	invalid=[]
	idinst.append({'type':'s','inst':'dummy'})
	while i_i<len(idinst) and i_t<len(text):
		if idinst[i_i]['type'] in ['s','l','bc','bt']:
			record_type=idinst[i_i]['type']
			if idinst[i_i]['type']=='bc':
				sp_inst_func=check_if_special_inst_func(idinst[i_i]['inst'])
				if sp_inst_func==-1:
					i_i+=1
					continue
				#print idinst[i_i]
				record_type+=str(sp_inst_func)
			old_id=now_id
			now_id,fname,isend=inst_find_id_func(text[i_t],old_id)
			if type(now_id)==list:
				print 'variable id extraction error, now try to automatically fix'
				i_fix=len(inst_records)-1
				while i_fix>=0:
					inst_records[-1][3]-=now_id[1]
					if inst_records[-1][1]=='f':
						break
					i_fix-=1
				now_id=now_id[0]
			if fname!=[]:
				inst_records.append([i_t,'f',[],now_id,fname])
				rds.append([[],[]])
			if isend:
				inst_records.append([i_t,'e',[],now_id,''])
				rds.append([[],[]])
			if i_i!=len(idinst)-1 and inst_cmp_rd_ll(idinst[i_i]['type'],idinst[i_i]['inst'],text[i_t]):
				r=inst_match_target(record_type,text[i_t])
				#print i_i,len(idinst),idinst[i_i]['inst'],text[i_t],r
				if(idinst[i_i]['del']):
					invalid.append(len(inst_records))
				if idinst[i_i]['type'] in ['bc','bt']:#if we instrument bc and bt in front of them, we should set the "id to this instr" the old_id
					if idinst[i_i]['type']=='bt' or sp_inst_funcs[sp_inst_func]['pos']==0:
						inst_records.append([i_t,record_type,idinst[i_i]['id'],old_id,r[0]])
					else:
						inst_records.append([i_t,record_type,idinst[i_i]['id'],now_id,r[0]])
				else:
					inst_records.append([i_t,record_type,idinst[i_i]['id'],now_id,r[0]])
				rds.append([idinst[i_i]['id'],idinst[i_i]['rds']])
				i_i+=1
			i_t+=1
		else:
			i_i+=1
	
	if i_i<len(idinst)-1:
		print "ERROR, .rd not match .ll",idinst[i_i],i_i,len(idinst)
		exit(1)
	print '1.extract basic information'
	
	del idinst[-1]
	"""
	for ii in idinst:
		print ii
	"""
	i_i=0
	func=[]
	i_a=0
	i_inst=0
	while i_i<len(idinst):
		if idinst[i_i]['func']!=func:
			func=idinst[i_i]['func']
			i_a=0
			while i_a<len(asm):
				if asm[i_a]['func']==func:
					break
				i_a+=1
			if i_a==len(asm):
				print "WARNING: func not found in asm",func
				while i_i<len(idinst) and func==idinst[i_i]['func']:
					i_i+=1
				continue
			i_inst=0
			count_sl=0
			for it in asm[i_a]['insts']:
				if it['type'] in ['sl']:
					count_sl+=1
			count_sl2=0
			ii=i_i
			while ii<len(idinst) and idinst[ii]['func']==func:
				if idinst[ii]['type'] in ['s','l']:
					count_sl2+=1
				ii+=1
			if not(count_sl==0 or count_sl==count_sl2):
				"""
				print 'debug here-------------'
				for it in asm[i_a]['insts']:
					print it
				print '==========='
				ii=i_i
				while ii<len(idinst) and idinst[ii]['func']==func:
					if idinst[ii]['type'] in ['s','l','bc','bt']:
						print idinst[ii]
					ii+=1
				"""
				print 'WARNING: asm and ll not match in',func,count_sl,count_sl2
				#exit(1)
			#print count_sl,count_sl2
		#print func
		"""
		for ii in asm[i_a]['insts']:
			print ii
		print '----------'
		for i in range(20):
			print idinst[i_i+i]['func'],idinst[i_i+i]['type']
		"""
		if func=='main':
			i_inst+=1
		while i_i<len(idinst) and func==idinst[i_i]['func']:
			if i_inst<len(asm[i_a]['insts']):
				#if func=='main':
				#	print asm[i_a]['insts'][i_inst],idinst[i_i]
				#print i_inst,len(asm[i_a]['insts']),idinst[i_i]
				#print asm[i_a]['insts'][i_inst]
				#print sp_inst_funcs
				if asm[i_a]['insts'][i_inst]['type']=='sl':
					if idinst[i_i]['type']=='s' or idinst[i_i]['type']=='l':
						#print '--',idinst[i_i],asm[i_a]['insts'][i_inst]
						asm[i_a]['insts'][i_inst]['type']=idinst[i_i]['type']
						asm[i_a]['insts'][i_inst]['id']=idinst[i_i]['id']
						asm[i_a]['insts'][i_inst]['rds']=idinst[i_i]['rds']
						asm[i_a]['insts'][i_inst]['del']=idinst[i_i]['del']
						idinst[i_i]['matched']=1
						i_inst+=1
						#i_i+=1
					elif idinst[i_i]['type']=='bc':
						i_inst+=1
						#print idinst[i_i]
					i_i+=1
				elif asm[i_a]['insts'][i_inst]['type']=='call':
					if idinst[i_i]['type']=='bc':
						if asm[i_a]['insts'][i_inst]['func']!=[]:
							sp_inst_funcasm=check_if_special_inst_func_lite(asm[i_a]['insts'][i_inst]['func'])
						else:
							sp_inst_funcasm=-1
						sp_inst_func=check_if_special_inst_func(idinst[i_i]['inst'])
						if sp_inst_func!=-1 and sp_inst_funcasm==sp_inst_func:
							libs=sp_inst_funcs[sp_inst_func]['s']
							libl=sp_inst_funcs[sp_inst_func]['l']
							liblen=sp_inst_funcs[sp_inst_func]['len']
							asm[i_a]['insts'][i_inst]['libinfo']={'s':libs,'l':libl,'len':liblen}
						#print '--',idinst[i_i],asm[i_a]['insts'][i_inst]
						asm[i_a]['insts'][i_inst]['id']=idinst[i_i]['id']
						asm[i_a]['insts'][i_inst]['rds']=idinst[i_i]['rds']
						asm[i_a]['insts'][i_inst]['del']=idinst[i_i]['del']
						idinst[i_i]['matched']=1
						i_inst+=1
					i_i+=1
				elif asm[i_a]['insts'][i_inst]['type']=='ret':
					if idinst[i_i]['type']=='bt':
						#print '--',idinst[i_i],asm[i_a]['insts'][i_inst]
						asm[i_a]['insts'][i_inst]['id']=idinst[i_i]['id']
						asm[i_a]['insts'][i_inst]['rds']=idinst[i_i]['rds']
						asm[i_a]['insts'][i_inst]['del']=idinst[i_i]['del']
						idinst[i_i]['matched']=1
						i_inst+=1
					i_i+=1
			else:
				i_i+=1
	#if idinst not match (for s, l, bc, bt), then delete
	i_i=0
	unmatched_count={'s':0,'l':0,'bc':0,'bt':0}
	while i_i<len(idinst):
		if idinst[i_i]['type'] in ['s','l','bc','bt']:
			if idinst[i_i]['matched']==0:
				#idinst[i_i]['del']=1
				unmatched_count[idinst[i_i]['type']]+=1
		i_i+=1
	print 'unmatched statistics',unmatched_count
	#return
	#change rds's id to address
	"""
	maxid=0
	for a in asm:
		for ins in a['insts']:
			if ins['id']!=[] and ins['id']>maxid:
				maxid=ins['id']
	id2addr=[[] for i in range(maxid+1)]
	for a in asm:
		for ins in a['insts']:
			if ins['id']!=[]:
				id2addr[ins['id']].append(ins['addr'])
	i_a=0
	while i_a<len(asm):
		i_i=0
		while i_i<len(asm[i_a]['insts']):
			rds=copy.deepcopy(asm[i_a]['insts'][i_i]['rds'])
			asm[i_a]['insts'][i_i]['rds']=[]
			for i_r in range(len(rds)):
				asm[i_a]['insts'][i_i]['rds'].extend(id2addr[rds[i_r]])
			i_i+=1
		i_a+=1
	"""

def replace_intrinsic_func(text):
	exp="declare .* @(\S+)\(.*\)"
	exp2="\S+ = call [^@]* @([^\(]*)\(|\s*call [^@]* @([^\(]*)\(|\S+ = tail call [^@]* @([^\(]*)\(|\s*tail call [^@]* @([^\(]*)\("
	expl="llvm\.([^\.]+)\..*"
	i=0
	while i<len(text):
		t=text[i]
		r=re.findall(exp,t)
		if len(r)>0:
			#print r
			found=0
			for v in valid_intrinsic_func:
				if v in r[0]:
					found=1
					break
			if found:
				r2=re.findall(expl,r[0])
				if len(r2)>0:
					text[i]=text[i].replace(r[0],r2[0])
			i+=1
			continue
		r=re.findall(exp2,t)
		if len(r)>0:
			name=''
			for e in r[0]:
				if len(e)>0:
					name=e
					break
			found=0
			for v in valid_intrinsic_func:
				if v in name:
					found=1
					break
			if found:
				r2=re.findall(expl,name)
				if len(r2)>0:
					text[i]=text[i].replace(name,r2[0])
		i+=1

def revise_rocc_lib_instr(text):
	#this is to move the library instrumentation of rocc right in front of the call	
	
	matched=[]#the start, end, and call position of matched lib instrumenataion
	temp=[-1,-1,-1,-1,-1] #app start, app end, call/ret, is call?, .word content
	state=0
	i=0
	while i<len(text):
		if state==0:
			if len(re.findall("call\s+\S*dfi_rocc_debug\S*",text[i]))>0:
				temp[0]=i
				state=1
		elif state==1:
			if len(re.findall("^\s*call\s+\S+",text[i])) or len(re.findall("^\s*ret",text[i])):
				temp[1]=i
				state=0
				matched.append(copy.deepcopy(temp))
		i+=1
	matched.reverse()
	for m in matched:
		text=text[0:m[0]]+text[m[0]+1:m[1]]+text[m[0]:m[0]+1]+text[m[1]:]
	
	matched=[]#the start, end, and call position of matched lib instrumenataion
	temp=[-1,-1,-1,-1,-1] #app start, app end, call/ret, is call?, .word content
	state=0
	i=0
	while i<len(text):
		if state==0:
			temp=[-1,-1,-1,-1,-1]
			if len(re.findall("^\s*#APP",text[i]))>0:
				temp[0]=i
				state=1
		elif state==1:
			r=re.findall("^\s*\.word\s+(\d+)",text[i])
			if len(r)>0:
				data=int(r[0])
				temp[4]=data
				if (temp[4]>>30)==0 and ((temp[4]>>27)&0x7)>0:
					state=2
				else:
					state=0
			else:
				state=0
		elif state==2:
			if len(re.findall("^\s*#NO_APP",text[i]))>0:
				temp[1]=i
				state=3
		elif state==3:
			if len(re.findall("^\s*call\s+\S+",text[i])):
				temp[2]=i
				if (temp[4]>>30)==0 and ((temp[4]>>27)&0x3)>0:
					temp[3]=1
					matched.append(copy.deepcopy(temp))
				else:
					temp[3]=2
					matched.append(copy.deepcopy(temp))
				state=0
			elif len(re.findall("^\s*ret",text[i])):#return
				temp[2]=i
				temp[3]=0
				matched.append(copy.deepcopy(temp))
				state=0
			elif len(re.findall("^\s*#APP",text[i]))>0:
				matched.append(copy.deepcopy(temp))
				temp=[-1,-1,-1,-1,-1]
				temp[0]=i
				state=1
		i+=1
	custom1=0x2b|(3<<12)|(10<<15)|(11<<20)
	custom2=0x5b|(2<<12)|(12<<15)
	addtext=[
	"	add	a0, zero, a0\n",
	"	add	a1, zero, a1\n",
	"	add	a2, zero, a2\n",
	#"	#APP\n",
	#"	.word "+str(custom1)+"\n",
	#"	#NO_APP\n",
	#"	#APP\n",
	#"	.word "+str(custom2)+"\n",
	#"	#NO_APP\n",
	]
	addtextret=[
	"	add s0,zero,s0\n"
	]
	addtextnop="	nop\n"
	matched.reverse()
	for m in matched:
	#	#print m,text[m[0]:m[1]+1],text[m[2]]
	#	text=text[0:m[0]]+text[m[1]+1:m[2]]+text[m[0]:m[1]+1]+text[m[2]:]
		fill=4-(m[2]-m[1])
		if fill<0:
			fill=0
		if m[3]==1:#call
			text=text[0:m[0]]+text[m[1]+1:m[2]]+addtext+text[m[0]:m[1]+1]+text[m[2]:]
			#text=text[0:m[2]]+addtext+[addtextnop*fill]+text[m[2]:]
			#text=text[0:m[2]]+addtext+text[m[2]:]
		elif m[3]==0:#ret
			text=text[0:m[0]]+text[m[1]+1:m[2]]+text[m[0]:m[1]+1]+text[m[2]:]
			#text=text[0:m[0]]+text[m[1]+1:m[2]]+addtextret+text[m[0]:m[1]+1]+text[m[2]:]
			#text=text[0:m[2]]+addtextret+[addtextnop*fill]+text[m[2]:]
			#text=text[0:m[2]]+addtextret+text[m[2]:]
		elif m[3]==2:#not lib call
		#	text=text[0:m[2]]+[addtextnop*fill]+text[m[2]:]
		#	if m[2]==m[1]+1:
		#		text=text[0:m[2]]+addtextnop+text[m[2]:]
			text=text[0:m[0]]+text[m[1]+1:m[2]]+text[m[0]:m[1]+1]+text[m[2]:]
		#else: #not a real call indicator, delete
		#	text=text[0:m[0]]+text[m[1]+1:]
		"""
		if m[3]==1:
			text=text[0:m[2]]+[addtext[0]]+[addtext[2]]+text[m[2]:]
		elif m[3]==2:
			text=text[0:m[2]]+[addtext[1]]+[addtext[2]]+text[m[2]:]
		elif m[3]==3:
			text=text[0:m[2]]+addtext+text[m[2]:]
		"""
	print 'processed branch in asm:',len(matched)
	return text

if len(sys.argv)<2:
	print("Please input the program prefix")
	exit(1)

p=[]
for i in range(2,len(sys.argv)):
	if sys.argv[i][0]=='-':
		p.append(i)

for i in p:
	if sys.argv[i]=='-init':
		mode=0
	elif sys.argv[i]=='-map':
		mode=1
	elif sys.argv[i]=='-rds':
		mode=2
	elif sys.argv[i]=='-sfunc':
		sfuncfile=sys.argv[i+1]
	elif sys.argv[i]=='-usrrds':
		usr_rds=1
		usr_rds_file=sys.argv[i+1]
	elif sys.argv[i]=='-roccinstr':
		exp_mode='roccinstr'
	elif sys.argv[i]=='-soft':
		exp_mode='soft'
		bf_len=16
	elif sys.argv[i]=='-ori':
		exp_mode='ori'
	elif sys.argv[i]=='-revasm':
		exp_mode='revasm'
	elif sys.argv[i]=='-hdfi':
		hdfi_mode=1
	elif sys.argv[i]=='-debugfunc':
		debugfunc=1
		print 'debugfunc is inserted'
	elif sys.argv[i]=='-debugrocc':
		debugfunc=2
		print 'debug rocc func is inserted'
	elif sys.argv[i]=='-timestampfunc':
		timestampfunc=sys.argv[i+1]
	elif sys.argv[i]=='-allfunc':
		anyfunc=1
	elif sys.argv[i]=='-nofunc':
		nofunc=1
	elif sys.argv[i]=='-noload':
		noload=1
	elif sys.argv[i]=='-nostore':
		nostore=1
	elif sys.argv[i]=='-stldroccdebug':
		stldroccdebug=1

if sfuncfile!='':
	get_all_special_exclusive_funcs()
	print sfunc
	print exfuncs

if exp_mode=='noinstr' and mode==0:
	text=read_file(sys.argv[1]+'.ll')
	
	replace_intrinsic_func(text)
	
	inst_initialize_func(text,'noinstr')
	
	f=open(sys.argv[1]+'.ll','wb')
	for t in text:
		f.write(t)
	f.close()
elif exp_mode=='noinstr' and mode==1:
	
	text=read_file(sys.argv[1]+'.rd')
	#make_align4(text)
	replace_intrinsic_func(text)
	
	idinst=parse_analysis(text)
	del text
	print "Parse reaching definition analysis complete"
	if usr_rds and hdfi_mode==0:
		usr_specify_rds(idinst,usr_rds_file)
	"""
	print '=======RDS BEFORE PROCESSING========'
	i=0
	for ii in idinst:
		print i,ii
		i+=1
		if i>=3000:
			print '..............'
			break
	"""
	"""
	i=0
	for ii in idinst:
		if ii['type']=='bc':
			print i,ii['id'],ii['rds'],ii
		i+=1
	print '============='
	"""
	"""
	if hdfi_mode==0:
		idinst_opt_rename(idinst)
		print 'Rename optimization complete'
		
		idinst_opt_setcheckdef(idinst)
		print 'Setdef/Checkdef optimization complete'
		
		get_ret_id(idinst)
		print 'Ret id is',ret_id

	if usr_rds and hdfi_mode:
		do_hdfi_change(idinst,usr_rds_file)

	if spatt_dop:
		text=read_file(sys.argv[1]+'.ll')
		gadgets=backward_slices_analysis(text)
		delete_checks_by_dop(gadgets,idinst,text)
		del text
		print 'Special attack DOP optimization complete'

	if analysis:
		analysis_code(idinst)
		exit(0)
	"""
	countdel=0
	countsldel=0
	countsl=0
	for ii in idinst:
		if ii['type'] in ['s','l']:
			countsl+=1
			if ii['del']:
				countsldel+=1
		else:
			if ii['del']:
				#print ii
				countdel+=1
	print 's/l count',countsl,'deleted s/l',countsldel,'deleted other',countdel
	"""
	for ii in idinst:
		print ii
	"""
	inst_ll(idinst,sys.argv[1]+'.ll',sys.argv[1]+'.ll.aux')
	print "Instrument bitcode complete"
	"""
	print '=======RDS AFTER PROCESSING========'
	i=0
	for ii in idinst:
		print i,ii
		i+=1
		if i>=3000:
			print '..............'
			break
	"""
	#the id in rds starts from 1, not 0
	#inst_write_rds(idinst)
	#print "Write dfi_data file complete"
	#inst_test_read_rds()
	
elif (exp_mode=='noinstr' and mode==2) or exp_mode=='roccinstr' or exp_mode=='soft' or exp_mode=='ori':
	text=read_file(sys.argv[1]+'.od.aux')
	parsedsl=parse_asm(text)
	del text
	"""
	count=0
	for p in parsedsl:
		if 1 or p['func']=='perform_attack':
			for it in p['sls']:
				count+=it[1]
				print it
	print count
	"""
	#exit(0)
	text=read_file(sys.argv[1]+'.od')
	asm=extract_asm_address(text,parsedsl)
	test_asm_extension_additionalbits(text)
	del text
	"""
	for p in asm:
		#print p
		if p['func']=='perform_attack':
			for it in p['insts']:
				print it
	"""
	#exit(1)
	text=read_file(sys.argv[1]+'.rd')
	replace_intrinsic_func(text)
	idinst=parse_analysis(text)
	del text
	print "Parse reaching definition analysis complete"
	if usr_rds and hdfi_mode==0:
		usr_specify_rds(idinst,usr_rds_file)
	
	if hdfi_mode==0:
		idinst_opt_rename(idinst)
		print 'Rename optimization complete'
		
		idinst_opt_setcheckdef(idinst)
		print 'Setdef/Checkdef optimization complete'
		
		get_ret_id(idinst)
		print 'Ret id is',ret_id

	if usr_rds and hdfi_mode:
		do_hdfi_change(idinst,usr_rds_file)

	if spatt_dop:
		text=read_file(sys.argv[1]+'.ll')
		gadgets=backward_slices_analysis(text)
		delete_checks_by_dop(gadgets,idinst,text)
		del text
		print 'Special attack DOP optimization complete'

	if analysis:
		analysis_code(idinst)
		exit(0)

	countdel=0
	countsldel=0
	countsl=0
	for ii in idinst:
		if ii['type'] in ['s','l']:
			countsl+=1
			if ii['del']:
				countsldel+=1
		else:
			if ii['del']:
				#print ii
				countdel+=1
	print 's/l count',countsl,'deleted s/l',countsldel,'deleted other',countdel
	"""
	for ii in idinst:
		print ii
	"""
	map_rds(idinst,sys.argv[1]+'.ll',asm)
	print "Map rds complete"
	
	if nostore:
		i_i=0
		while i_i<len(idinst):
			if idinst[i_i]['type']=='s':
				idinst[i_i]['del']=1
			i_i+=1
	if noload:
		i_i=0
		while i_i<len(idinst):
			if idinst[i_i]['type']=='l':
				idinst[i_i]['del']=1
			i_i+=1
	
	if or exp_mode=='soft' or exp_mode=='roccinstr' or exp_mode=='ori':
		inst_ll(idinst,sys.argv[1]+'.ll',sys.argv[1]+'.ll')
		print "Instrument bitcode complete"
	
	count_del=0
	for a in asm:
		for e in a['insts']:
			if e['del']:
				count_del+=1
		#if a['func']=='update_tree':
		#	print a
	print 'deleted inst:',count_del
	"""
	print '==================='
	for ii in idinst:
		if 'func' in ii.keys() and ii['func']=='main':
			print ii
	"""
	if exp_mode=='noinstr':
		inst_write_rds(asm)
	elif exp_mode=='roccinstr':
		inst_write_rds(idinst)
	print "write rds to file complete"
	"""
	print '==================='
	for a in asm:
		if a['func']=='main':
			for i in a['insts']:
				print i
	"""
	"""
	print '==================='
	for a in asm:
		if a['func']=='perform_attack':
			for i in a['insts']:
				print i
	"""

elif exp_mode == 'revasm':
	text=read_file(sys.argv[1]+'.s')
	text=revise_rocc_lib_instr(text)
	f=open(sys.argv[1]+'.s','wb')
	for t in text:
		f.write(t)
	f.close()
	
	
	
