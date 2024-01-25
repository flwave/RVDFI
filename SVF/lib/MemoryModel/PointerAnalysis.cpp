//===- PointerAnalysis.cpp -- Base class of pointer analyses------------------//
//
//                     SVF: Static Value-Flow Analysis
//
// Copyright (C) <2013-2017>  <Yulei Sui>
//

// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//===----------------------------------------------------------------------===//

/*
 * PointerAnalysis.cpp
 *
 *  Created on: May 14, 2013
 *      Author: rocky
 */

#include "MemoryModel/PointerAnalysis.h"
#include "MemoryModel/PAGBuilder.h"
#include "Util/GraphUtil.h"
#include "Util/AnalysisUtil.h"
#include "Util/PTAStat.h"
#include "Util/ThreadCallGraph.h"
#include "Util/CPPUtil.h"
#include "Util/SVFModule.h"
#include "Util/InstrumentUtil.h"
#include "MemoryModel/CHA.h"
#include "MemoryModel/PTAType.h"

#include <llvm/IR/InstIterator.h>	// for inst iteration

#include <fstream>
#include <sstream>

using namespace llvm;
using namespace analysisUtil;
using namespace cppUtil;

static cl::opt<bool> TYPEPrint("print-type", cl::init(false),
                               cl::desc("Print type"));

static cl::opt<bool> FuncPointerPrint("print-fp", cl::init(false),
                                      cl::desc("Print targets of indirect call site"));

static cl::opt<bool> PTSPrint("print-pts", cl::init(false),
                              cl::desc("Print points-to set of top-level pointers"));

static cl::opt<bool> PTSAllPrint("print-all-pts", cl::init(false),
                                 cl::desc("Print all points-to set of both top-level and address-taken variables"));

static cl::opt<bool> PStat("stat", cl::init(true),
                           cl::desc("Statistic for Pointer analysis"));

static cl::opt<unsigned> statBudget("statlimit",  cl::init(20),
                                    cl::desc("Iteration budget for On-the-fly statistics"));

static cl::opt<bool> PAGDotGraph("dump-pag", cl::init(false),
                                 cl::desc("Dump dot graph of PAG"));

static cl::opt<bool> PAGPrint("print-pag", cl::init(false),
                              cl::desc("Print PAG to command line"));

static cl::opt<std::string> Graphtxt("graphtxt", cl::value_desc("filename"),
                                     cl::desc("graph txt file to build PAG"));

static cl::opt<unsigned> IndirectCallLimit("indCallLimit",  cl::init(50000),
        cl::desc("Indirect solved call edge limit"));

static cl::opt<bool> UsePreCompFieldSensitive("preFieldSensitive", cl::init(true),
        cl::desc("Use pre-computed field-sensitivity for later analysis"));

static cl::opt<bool> EnableAliasCheck("alias-check", cl::init(true),
                                      cl::desc("Enable alias check functions"));

static cl::opt<bool> EnableThreadCallGraph("enable-tcg", cl::init(true),
        cl::desc("Enable pointer analysis to use thread call graph"));

static cl::opt<bool> INCDFPTData("incdata", cl::init(true),
                                 cl::desc("Enable incremental DFPTData for flow-sensitive analysis"));

static cl::opt<bool> connectVCallOnCHA("vcall-cha", cl::init(false),
                                       cl::desc("connect virtual calls using cha"));

static cl::opt<bool> PrintProgramWithID("print-prog-id", cl::init(false),
                                        cl::desc("Print program with ID"));

static cl::opt<bool> GenDefSet("gen-def-set", cl::init(false),
                               cl::desc("Generate memory objects' definition set"));

static cl::opt<bool> PrintDefSet("print-def-set", cl::init(false),
                                 cl::desc("Generate memory objects' definition set"));

static cl::opt<bool> LogFunction("log-function", cl::init(false),
                                 cl::desc("instrument IR to log function calls"));

static cl::opt<bool> DFIInstrument("dfi-instrument", cl::init(false),
                                   cl::desc("instrument IR with Data-flow Integrity"));

typedef SymbolTableInfo::IDToMemMapTy IDToMemMapTy;

CHGraph* PointerAnalysis::chgraph = NULL;
PAG* PointerAnalysis::pag = NULL;

/*!
 * Constructor
 */
PointerAnalysis::PointerAnalysis(PTATY ty) :
    ptaTy(ty),stat(NULL),ptaCallGraph(NULL),callGraphSCC(NULL),typeSystem(NULL) {
    OnTheFlyIterBudgetForStat = statBudget;
    print_stat = PStat;
    numOfIteration = 0;
}

/*!
 * Destructor
 */
PointerAnalysis::~PointerAnalysis() {
    destroy();
    // do not delete the PAG for now
    //delete pag;
}


void PointerAnalysis::destroy()
{
    delete ptaCallGraph;
    ptaCallGraph = NULL;

    delete callGraphSCC;
    callGraphSCC = NULL;

    delete stat;
    stat = NULL;

    delete typeSystem;
    typeSystem = NULL;
}

/*!
 * Initialization of pointer analysis
 */
void PointerAnalysis::initialize(SVFModule svfModule) {

    /// whether we have already built PAG
    if(pag == NULL) {

        DBOUT(DGENERAL, outs() << pasMsg("Building Symbol table ...\n"));
        SymbolTableInfo* symTable = SymbolTableInfo::Symbolnfo();
        symTable->buildMemModel(svfModule);

        DBOUT(DGENERAL, outs() << pasMsg("Building PAG ...\n"));
        if (!Graphtxt.getValue().empty()) {
            PAGBuilderFromFile fileBuilder(Graphtxt.getValue());
            pag = fileBuilder.build();

        } else {
            PAGBuilder builder;
            pag = builder.build(svfModule);
        }

        chgraph = new CHGraph();
        chgraph->buildCHG(svfModule);

        // dump the PAG graph
        if (dumpGraph())
            PAG::getPAG()->dump("pag_initial");

        // print to command line of the PAG graph
        if (PAGPrint)
            pag->print();
    }

    typeSystem = new TypeSystem(pag);

    svfMod = svfModule;

    /// initialise pta call graph
    if(EnableThreadCallGraph)
        ptaCallGraph = new ThreadCallGraph(svfModule);
    else
        ptaCallGraph = new PTACallGraph(svfModule);
    callGraphSCCDetection();
}


/*!
 * Return TRUE if this node is a local variable of recursive function.
 */
bool PointerAnalysis::isLocalVarInRecursiveFun(NodeID id) const
{
    const MemObj* obj = this->pag->getObject(id);
    assert(obj && "object not found!!");
    if(obj->isStack()) {
        if(const AllocaInst* local = dyn_cast<AllocaInst>(obj->getRefVal())) {
            const Function* fun = local->getParent()->getParent();
            return callGraphSCC->isInCycle(getPTACallGraph()->getCallGraphNode(fun)->getId());
        }
    }
    return false;
}

/*!
 * Reset field sensitivity
 */
void PointerAnalysis::resetObjFieldSensitive()
{
    for (PAG::iterator nIter = pag->begin(); nIter != pag->end(); ++nIter) {
        if(ObjPN* node = dyn_cast<ObjPN>(nIter->second))
            const_cast<MemObj*>(node->getMemObj())->setFieldSensitive();
    }
}

/*!
 * Flag in order to dump graph
 */
bool PointerAnalysis::dumpGraph() {
    return PAGDotGraph;
}

/*
 * Dump statistics
 */

void PointerAnalysis::dumpStat() {

    if(print_stat && stat)
        stat->performStat();
}


/*!
 * Finalize the analysis after solving
 * Given the alias results, verify whether it is correct or not using alias check functions
 */
void PointerAnalysis::finalize() {

    /// Print statistics
    dumpStat();

    PAG* pag = PAG::getPAG();
    // dump the PAG graph
    if (dumpGraph())
        pag->dump("pag_final");

    /// Dump results
    if (PTSPrint) {
        dumpTopLevelPtsTo();
        //dumpAllPts();
        //dumpCPts();
    }

    if (TYPEPrint)
        dumpAllTypes();

    if(PTSAllPrint)
        dumpAllPts();

    if (FuncPointerPrint)
        printIndCSTargets();

    if (PrintProgramWithID)
        dumpProgram();

    if (GenDefSet) {
        genDefSet();
        if (PrintDefSet)
            dumpDefSet();
        if (DFIInstrument)
            instrument();
    }

    if (LogFunction) {
        logFunctionCall();
    }

    getPTACallGraph()->vefityCallGraph();

    getPTACallGraph()->dump("callgraph_final");

    if(!pag->isBuiltFromFile() && EnableAliasCheck)
        validateTests();

    if (!UsePreCompFieldSensitive)
        resetObjFieldSensitive();
}

/*!
 * Validate test cases
 */
void PointerAnalysis::validateTests() {
    validateSuccessTests("MAYALIAS");
    validateSuccessTests("NOALIAS");
    validateSuccessTests("MUSTALIAS");
    validateSuccessTests("PARTIALALIAS");
    validateSuccessTests("_Z8MAYALIASPvS_");
    validateSuccessTests("_Z7NOALIASPvS_");
    validateSuccessTests("_Z9MUSTALIASPvS_");
    validateSuccessTests("_Z12PARTIALALIASPvS_");
    validateExpectedFailureTests("EXPECTEDFAIL_MAYALIAS");
    validateExpectedFailureTests("EXPECTEDFAIL_NOALIAS");
}


void PointerAnalysis::dumpAllTypes()
{
    for (NodeBS::iterator nIter = this->getAllValidPtrs().begin();
            nIter != this->getAllValidPtrs().end(); ++nIter) {
        const PAGNode* node = getPAG()->getPAGNode(*nIter);
        if (isa<DummyObjPN>(node) || isa<DummyValPN>(node))
            continue;

        outs() << "##<" << node->getValue()->getName() << "> ";
        outs() << "Source Loc: " << getSourceLoc(node->getValue());
        outs() << "\nNodeID " << node->getId() << "\n";

        llvm::Type* type = node->getValue()->getType();
        SymbolTableInfo::Symbolnfo()->printFlattenFields(type);
        if (PointerType* ptType = dyn_cast<PointerType>(type))
            SymbolTableInfo::Symbolnfo()->printFlattenFields(ptType->getElementType());
    }
}


/*!
 * Dump whole program with ID for instructions
 */
void PointerAnalysis::dumpProgram() {

    outs() << "\n============= Program with IDs =============\n";
    SymbolTableInfo *symTable = SymbolTableInfo::Symbolnfo();
    for (SVFModule::iterator F = svfMod.begin(), E = svfMod.end(); F != E; ++F) {
        Function *fun = *F;
        outs() << "\n" << symTable->valSyms()[fun] << "\t" << fun->getName() << "(";
        for (Function::arg_iterator I = fun->arg_begin(), E = fun->arg_end(); I != E; ++I) {
            if (I == fun->arg_begin())
                outs() << *I;
            else
                outs() << ", " << *I;
        }
        outs() << ")\n";
        for (inst_iterator II = inst_begin(*fun), E = inst_end(*fun); II != E; ++II) {
            Instruction *inst = &*II;
            outs() << symTable->valSyms()[inst] << "\t" << *inst << "\n";
        }
    }
}


/*!
 * Add definition to a node's set, if not available, create a new one
 */
void PointerAnalysis::genDefSet(NodeID node, NodeID def) {
    IDToDefSetMap::iterator iter = defSet.find(node);
    if (iter == defSet.end()) {
        defSet.insert(make_pair(node, DefSet()));
    }
    defSet[node].set(def);
    DBOUT(DDFI, outs() << "add def " << def << " to set " << node << "\n");
}


/*!
 * Add definition to a node's set, if not available, create one
 */
void PointerAnalysis::genDefSet(NodeID node, const DefSet &defs) {
    IDToDefSetMap::iterator iter = defSet.find(node);
    if (iter == defSet.end()) {
        defSet[node] = defs;
    } else {
        defSet[node] |= defs;
    }
    DBOUT(DDFI, outs() << "add defs { ");
    for (DefSet::iterator it = defs.begin(), eit = defs.end(); it != eit; ++it) {
        DBOUT(DDFI, outs() << *it << " ");
    }
    DBOUT(DDFI, outs() << "} to set " << node << "\n");
}


/*!
 * Generate memory definition set from store/call instructions recursively
 */
void PointerAnalysis::genDefSet(NodeID def, const Value* val) {
    NodeID id = pag->getValueNode(val);
    const PointsTo &pts = getPts(id);
    for (PointsTo::iterator it = pts.begin(), eit = pts.end(); it != eit;
            ++it) {
        NodeID ptr = *it;
        PAGNode *node = pag->getPAGNode(ptr);
        if (isa<FIObjPN>(node)) {
            genDefSet(ptr, def);
        } else if (isa<ValPN>(node)) {
            genDefSet(def, node->getValue());
        } else if (const GetElementPtrInst *gep = dyn_cast<GetElementPtrInst>(val)) {
            genDefSet(def, gep->getPointerOperand());
        } else {
            DBOUT(DDFI, outs() << "no need to add def " << def << " to "
                    << ptr << " [" << node->getNodeKindStr() << "]\n");
        }
    }
}


/*!
 * Generate reaching definition set for load instruction recursively
 */
void PointerAnalysis::genReachDefSet(NodeID tgt, const Value* val) {
    NodeID id = pag->getValueNode(val);
    const PointsTo &pts = getPts(id);
    for (PointsTo::iterator it = pts.begin(), eit = pts.end(); it != eit;
            ++it) {
        NodeID ptr = *it;
        PAGNode *node = pag->getPAGNode(ptr);
        if (isa<FIObjPN>(node)) {
            DefSet &defs = getDefSet(ptr);
            genDefSet(tgt, defs);
        } else if (isa<ValPN>(node)) {
            genReachDefSet(tgt, node->getValue());
        } else if (const GetElementPtrInst *gep = dyn_cast<GetElementPtrInst>(val)) {
            genReachDefSet(tgt, gep->getPointerOperand());
        } else {
            DBOUT(DDFI, outs() << "no need to collect def set from " << ptr
                    << " [" << node->getNodeKindStr() << "]\n");
        }
    }
}


/*!
 * Generate memory object's definition set (store ins id for the mem obj)
 */
void PointerAnalysis::genDefSet() {

    DBOUT(DDFI, outs() << "\n============= DFI Generate Definition Set ===============\n");

    /// generate the definition set for memory objects
    // visit globals
    for (SVFModule::global_iterator I = svfMod.global_begin(),
            E = svfMod.global_end(); I != E; ++I) {
        GlobalVariable *gvar = *I;
        NodeID idx = pag->getValueNode(gvar);
        NodeID obj = pag->getObjectNode(gvar);
        genDefSet(obj, idx);
        DBOUT(DDFI, outs() << "\ngenerating def of global variable " << idx << " : " << *gvar
                << " to its object node (" << obj << ")\n");
    }
    // visit instructions
    for (SVFModule::iterator F = svfMod.begin(), E = svfMod.end(); F != E; ++F) {
        Function *fun = *F;
        for (inst_iterator I = inst_begin(*fun), E = inst_end(*fun); I != E; ++I) {
            const Instruction *inst = &*I;
            NodeID def = pag->getValueNode(inst);
            if (const StoreInst *st = dyn_cast<StoreInst>(inst)) {
                const Value *ptrOp = st->getPointerOperand();
                DBOUT(DDFI, outs() << "\ngenerating def of store " << def << " : " << *inst << "\n");
                genDefSet(def, ptrOp);
            } else if (inst->mayWriteToMemory()) {
                DBOUT(DDFI, outs() << "\ngenerating def of inst " << def << " : " << *inst << " and is ");
                genDefSet(def, inst);
                if (isCallSite(inst)) {
                    DBOUT(DDFI, outs() << "function call, arguments:\n");
                    const CallSite cs = getLLVMCallSite(inst);
                    for (CallSite::arg_iterator ait = cs.arg_begin(); ait != cs.arg_end(); ++ait) {
                        DBOUT(DDFI, outs() << "\t" << pag->getValueNode(*ait) << ": " << *(*ait) << "\n");
                        genDefSet(def, *ait);
                    }
                } else {
                    DBOUT(DDFI, outs() << "not call\n");
                }
            }
            if (isCallSite(inst)) {
                genDefSet(def, inst); // handle call assign, _cbyte_b_loc()
            }
        }
    }

    /// generate the definition set for load instructions
    for (SVFModule::iterator F = svfMod.begin(), E = svfMod.end(); F != E; ++F) {
        Function *fun = *F;
        for (inst_iterator I = inst_begin(*fun), E = inst_end(*fun); I != E; ++I) {
            const Instruction *inst = &*I;
            if (const LoadInst *ld = dyn_cast<LoadInst>(inst)) {
                /// direct point-to
                NodeID id = pag->getValueNode(inst);
                DBOUT(DDFI, outs() << "\ngenerating reaching def-set for load " << id << "\t" << *inst << "\n");
                if (id == 648) {
                    outs() << "step in\n";
                }
                genReachDefSet(id, ld->getPointerOperand());
                DBOUT(DDFI, outs() << "\tgenerated reachDefSet { ");
                if (defSet.find(id) != defSet.end()) {
                    DefSet &defs = defSet[id];
                    for (DefSet::iterator it = defs.begin(), eit = defs.end(); it != eit; ++it) {
                        DBOUT(DDFI, outs() << *it << " ");
                    }
                } else {
                    DBOUT(DDFI, outs() << "empty ");
                }
                DBOUT(DDFI, outs() << "}\n");
            }
        }
    }
}


/*!
 * Get reaching definition set of a given node ID (mem obj or load)
 */
string PointerAnalysis::getDefSetString(NodeID node) {
    stringstream ss;
    ss << "{ ";
    if (defSet.find(node) != defSet.end()) {
        DefSet &defs = defSet[node];
        for (DefSet::iterator it = defs.begin(), eit = defs.end(); it != eit; ++it) {
            ss << *it << " ";
        }
    } else {
        ss << "empty ";
    }
    ss << "}";

    return ss.str();
}


/*!
 * Print definition instructions and definition set for loads
 */
void PointerAnalysis::dumpDefSet() {

    SymbolTableInfo *symTable = SymbolTableInfo::Symbolnfo();
    outs() << "\n============= Reaching Definition Set ===============\n";

    /// dump the definition set for memory objects
    for (IDToMemMapTy::iterator iter = symTable->idToObjMap().begin(),
        eiter = symTable->idToObjMap().end(); iter != eiter; ++iter) {
        SymID sym = iter->first;
        outs() << "Memory object ID " << sym << "\t: ReachDefSet { ";
        if (defSet.find(sym) != defSet.end()) {
            DefSet &defs = defSet[sym];
            for (DefSet::iterator it = defs.begin(), eit = defs.end(); it != eit; ++it) {
                outs() << *it << " ";
            }
        } else {
            outs() << "empty ";
            if (iter->second->isFunction()) {
                outs() << "[" << iter->second->getRefVal()->getName() << "] ";
            } else {
                string memType = iter->second->getMemTypeStr();
                outs() << "[" << memType << "] ";
            }
        }
        outs() << "}\n";
    }

    /// dump the definition set for loads
    for (SVFModule::iterator F = svfMod.begin(), E = svfMod.end(); F != E; ++F) {
        Function *fun = *F;
        for (inst_iterator I = inst_begin(*fun), E = inst_end(*fun); I != E; ++I) {
            const Instruction *inst = &*I;
            if (const LoadInst *ld = dyn_cast<LoadInst>(inst)) {
                NodeID ptr = pag->getValueNode(inst);
                outs() << "NodeID " << ptr << "\t" << *inst << "\n";
                outs() << "\t: ReachDefSet { ";
                if (defSet.find(ptr) != defSet.end()) {
                    DefSet &defs = defSet[ptr];
                    for (DefSet::iterator it = defs.begin(), eit = defs.end(); it != eit; ++it) {
                        outs() << *it << " ";
                    }
                } else {
                    outs() << "empty ";
                }
                outs() << "}\n";
            }
        }
    }
}


/*!
 * Instrument definition instructions and definition set for load/stores
 */
void PointerAnalysis::instrument() {

    Module *module = svfMod.getMainLLVMModule();
    LLVMContext &ctx = module->getContext();

    // insert function declarations
    Constant *printfFunc = instrumentUtil::printfDecl(ctx, module);

    // add global string
    Constant *storeFormat = ConstantDataArray::getString(ctx, "memory %p is defined by instruction %d\n");
    Constant *loadFormat = ConstantDataArray::getString(ctx, "memory %p is read by instruction %d, reachDefSet: %s\n");
    GlobalVariable *storeVar = new GlobalVariable(*module, storeFormat->getType(),
                                                 true, GlobalValue::PrivateLinkage,
                                                 storeFormat, ".str");
    GlobalVariable *loadVar = new GlobalVariable(*module, loadFormat->getType(),
                                                 true, GlobalValue::PrivateLinkage,
                                                 loadFormat, ".str");

    // get poionters to strings for parameters to printf calls
    Constant *zero = Constant::getNullValue(IntegerType::getInt32Ty(ctx));
    std::vector<Constant *> indices(2, zero);
    Constant *storeVarRef = ConstantExpr::getGetElementPtr(storeFormat->getType(),
                                                           storeVar, indices, true);
    Constant *loadVarRef = ConstantExpr::getGetElementPtr(loadFormat->getType(),
                                                          loadVar, indices, true);

    // get call insertion point and insert call instruction
    map<string, Constant *> reachDefSetToConstMap;
    for (SVFModule::iterator F = svfMod.begin(), E = svfMod.end(); F != E; ++F) {
        Function *fun = *F;
        for (inst_iterator II = inst_begin(*fun), IE = inst_end(*fun); II != IE; ++II) {
            Instruction *inst = &*II;
            if (StoreInst *st = dyn_cast<StoreInst>(inst)) {
                Value *ptrOp = st->getPointerOperand();
                NodeID sym = pag->getValueNode(inst);
                Constant *instID = ConstantExpr::getIntegerValue(IntegerType::getInt32Ty(ctx), APInt(32, sym));
                CallInst::Create(printfFunc, {storeVarRef, ptrOp, instID}, "printfcall", inst);
            } else if (LoadInst *ld = dyn_cast<LoadInst>(inst)) {
                Value *ptrOp = ld->getPointerOperand();
                NodeID sym = pag->getValueNode(inst);
                string reachDefSetStr = getDefSetString(sym);
                Constant *instID = ConstantExpr::getIntegerValue(IntegerType::getInt32Ty(ctx), APInt(32, sym));
                Constant *reachDefSetRef = nullptr;
                if (reachDefSetToConstMap.find(reachDefSetStr) == reachDefSetToConstMap.end()) {
                    Constant *reachDefSet = ConstantDataArray::getString(ctx, reachDefSetStr.c_str());
                    GlobalValue *reachDefSetVar = new GlobalVariable(*module, reachDefSet->getType(),
                                                                     true, GlobalValue::PrivateLinkage,
                                                                     reachDefSet, ".str");
                    reachDefSetRef = ConstantExpr::getGetElementPtr(reachDefSet->getType(),
                                                                    reachDefSetVar, indices, true);
                    reachDefSetToConstMap[reachDefSetStr] = reachDefSetRef;
                } else {
                    reachDefSetRef = reachDefSetToConstMap[reachDefSetStr];
                }
                CallInst::Create(printfFunc, {loadVarRef, ptrOp, instID, reachDefSetRef}, "printfcall", inst);
            }
        }
    }
}


/*!
 * Instrument to log function calls
 */
void PointerAnalysis::logFunctionCall() {

    Module *module = svfMod.getMainLLVMModule();
    LLVMContext &ctx = module->getContext();

    // insert function declarations
    Constant *printfFunc = instrumentUtil::printfDecl(ctx, module);

    // add global string
    Constant *callFormat = ConstantDataArray::getString(ctx, "start function %s ...\n");
    GlobalVariable *callVar = new GlobalVariable(*module, callFormat->getType(),
                                                 true, GlobalValue::PrivateLinkage,
                                                 callFormat, ".str");
    Constant *retFormat = ConstantDataArray::getString(ctx, "return from function %s!\n");
    //GlobalVariable *retVar = new GlobalVariable(*module, ArrayType::get(IntegerType::get(ctx, 8), 26),
    GlobalVariable *retVar = new GlobalVariable(*module, retFormat->getType(),
                                             true, GlobalValue::PrivateLinkage,
                                             retFormat, ".str");

    // get pointers to strings for parameters to printf calls
    Constant *zero = Constant::getNullValue(IntegerType::getInt32Ty(ctx));
    std::vector<Constant *> indices;
    indices.push_back(zero);
    indices.push_back(zero);
    Constant *callVarRef = ConstantExpr::getGetElementPtr(callFormat->getType(),
                                                          callVar, indices, true);
    //Constant *retVarRef = ConstantExpr::getGetElementPtr(ArrayType::get(IntegerType::get(ctx, 8), 26),
    //Constant *retVarRef = ConstantExpr::getGetElementPtr(nullptr,
    Constant *retVarRef = ConstantExpr::getGetElementPtr(retFormat->getType(),
                                                       retVar, indices, true);

    // get call insertion point and insert call instruction
    for (SVFModule::iterator F = svfMod.begin(), E = svfMod.end(); F != E; ++F) {
        Function *fun = *F;
        Constant *funcName = ConstantDataArray::getString(ctx, fun->getName().data());
        GlobalValue *varName = new GlobalVariable(*module, funcName->getType(),
                                                  true, GlobalValue::PrivateLinkage,
                                                  funcName, ".str");
        Constant *varNameRef = ConstantExpr::getGetElementPtr(funcName->getType(),
                                                              varName, indices, true);
        inst_iterator II = inst_begin(*fun), IE = inst_end(*fun);
        if (II != IE) {
            Instruction *inst = &*II;
            CallInst::Create(printfFunc, {callVarRef, varName}, "printcall", inst);
        }
        for (; II != IE; ++II) {
            Instruction *inst = &*II;
            if (isa<ReturnInst>(inst)) {
                CallInst::Create(printfFunc, {retVarRef, varNameRef}, "printfcall", inst);
            }
        }
    }
}


/*!
 * Constructor
 */
BVDataPTAImpl::BVDataPTAImpl(PointerAnalysis::PTATY type) : PointerAnalysis(type) {
    if(type == Andersen_WPA || type == AndersenWave_WPA || type == AndersenLCD_WPA) {
        ptD = new PTDataTy();
    }
    else if (type == AndersenWaveDiff_WPA || type == AndersenWaveDiffWithType_WPA) {
        ptD = new DiffPTDataTy();
    }
    else if (type == FSSPARSE_WPA) {
        if(INCDFPTData)
            ptD = new IncDFPTDataTy();
        else
            ptD = new DFPTDataTy();
    }
    else if (type == FlowS_DDA) {
        ptD = new PTDataTy();
    }
    else
        assert(false && "no points-to data available");
}

/*!
 * Expand all fields of an aggregate in all points-to sets
 */
void BVDataPTAImpl::expandFIObjs(const PointsTo& pts, PointsTo& expandedPts) {
    expandedPts = pts;;
    for(PointsTo::iterator pit = pts.begin(), epit = pts.end(); pit!=epit; ++pit) {
        if(pag->getBaseObjNode(*pit)==*pit) {
            expandedPts |= pag->getAllFieldsObjNode(*pit);
        }
    }
}

/*!
 * Store pointer analysis result into a file.
 * It includes the points-to data, and the PAG offset nodes, which
 * are created when solving Andersen's constraints.
 */
void BVDataPTAImpl::writeToFile(const string& filename) {
    outs() << "Storing pointer analysis results to '" << filename << "'...";

    error_code err;
    ToolOutputFile F(filename.c_str(), err, sys::fs::F_None);
    if (err) {
        outs() << "  error opening file for writing!\n";
        F.os().clear_error();
        return;
    }

    // Write analysis results to file
    PTDataTy *ptD = getPTDataTy();
    auto &ptsMap = ptD->getPtsMap();
    for (auto it = ptsMap.begin(), ie = ptsMap.end(); it != ie; ++it) {
        NodeID var = it->first;
        const PointsTo &pts = getPts(var);

        F.os() << var << " -> { ";
        if (pts.empty()) {
            F.os() << " ";
        } else {
            for (auto it = pts.begin(), ie = pts.end(); it != ie; ++it) {
                F.os() << *it << " ";
            }
        }
        F.os() << "}\n";
    }

    // Write PAG offset nodes to file
    NodeID firstGepObjNode = 0;
    for (auto it = pag->begin(), ie = pag->end(); it != ie; ++it) {
        PAGNode* pagNode = it->second;
        if (GepObjPN *gepObjPN = dyn_cast<GepObjPN>(pagNode)) {
            if (firstGepObjNode > gepObjPN->getId()) {
                firstGepObjNode = gepObjPN->getId();
            }
        }
    }
    for (NodeID i = firstGepObjNode, e = pag->getTotalNodeNum(); i != e; ++i) {
        GepObjPN *gepObjPN = dyn_cast<GepObjPN>(pag->getPAGNode(i));
        if (gepObjPN) {
            F.os() << i << " ";
            F.os() << pag->getBaseObjNode(i) << " ";
            F.os() << gepObjPN->getLocationSet().getOffset() << "\n";
        }
    }

    // Job finish and close file
    F.os().close();
    if (!F.os().has_error()) {
        outs() << "\n";
        F.keep();
        return;
    }
}

/*!
 * Load pointer analysis result form a file.
 * It populates BVDataPTAImpl with the points-to data, and updates PAG with
 * the PAG offset nodes created during Andersen's solving stage.
 */
bool BVDataPTAImpl::readFromFile(const string& filename) {
    outs() << "Loading pointer analysis results from '" << filename << "'...";

    ifstream F(filename.c_str());
    if (!F.is_open()) {
        outs() << "  error opening file for reading!\n";
        return false;
    }

    // Read analysis results from file
    PTDataTy *ptD = getPTDataTy();
    string line;

    // Read points-to sets
    string delimiter1 = " -> { ";
    string delimiter2 = " }";
    while (F.good()) {
        // Parse a single line in the form of "var -> { obj1 obj2 obj3 }"
        getline(F, line);
        size_t pos = line.find(delimiter1);
        if (pos == string::npos)    break;
        if (line.back() != '}')     break;

        // var
        NodeID var = atoi(line.substr(0, pos).c_str());
        PointsTo &pts = ptD->getPts(var);

        // objs
        pos = pos + delimiter1.length();
        size_t len = line.length() - pos - delimiter2.length();
        string objs = line.substr(pos, len);
        if (!objs.empty()) {
            istringstream ss(objs);
            NodeID obj;
            while (ss.good()) {
                ss >> obj;
                pts.set(obj);
            }
        }
    }

    // Read PAG offset nodes
    while (F.good()) {
        // Parse a single line in the form of "ID baseNodeID offset"
        istringstream ss(line);
        NodeID id;
        NodeID base;
        size_t offset;
        ss >> id >> base >> offset;

        NodeID n = pag->getGepObjNode(pag->getObject(base), LocationSet(offset));
        assert(id == n && "Error adding GepObjNode into PAG!");

        getline(F, line);
    }

    // Update callgraph
    updateCallGraph(pag->getIndirectCallsites());

    F.close();
    outs() << "\n";

    return true;
}

/*!
 * Dump points-to of each pag node
 */
void BVDataPTAImpl::dumpTopLevelPtsTo() {
    for (NodeBS::iterator nIter = this->getAllValidPtrs().begin();
            nIter != this->getAllValidPtrs().end(); ++nIter) {
        const PAGNode* node = getPAG()->getPAGNode(*nIter);
        if (getPAG()->isValidTopLevelPtr(node)) {
            PointsTo& pts = this->getPts(node->getId());
            outs() << "\nNodeID " << node->getId() << " ";

            if (pts.empty()) {
                outs() << "\t\tPointsTo: {empty}\n";
            } else {
                outs() << "\t\tPointsTo: { ";
                for (PointsTo::iterator it = pts.begin(), eit = pts.end();
                        it != eit; ++it)
                    outs() << *it << " ";
                outs() << "}\n";
            }
        }
    }
}

/*!
 * Dump points-to of top-level pointers (ValPN)
 */
void PointerAnalysis::dumpPts(NodeID ptr, const PointsTo& pts) {

    const PAGNode* node = pag->getPAGNode(ptr);
    /// print the points-to set of node which has the maximum pts size.
    if (isa<DummyObjPN> (node)) {
        outs() << "##<Dummy Obj > id:" << node->getId();
    } else if (!isa<DummyValPN>(node)) {
        outs() << "##<" << node->getValue()->getName() << "> ";
        outs() << "Source Loc: " << getSourceLoc(node->getValue());
    }
    outs() << "\nPtr " << node->getId() << " ";

    if (pts.empty()) {
        outs() << "\t\tPointsTo: {empty}\n\n";
    } else {
        outs() << "\t\tPointsTo: { ";
        for (PointsTo::iterator it = pts.begin(), eit = pts.end(); it != eit;
                ++it)
            outs() << *it << " ";
        outs() << "}\n\n";
    }

    outs() << "";

    for (NodeBS::iterator it = pts.begin(), eit = pts.end(); it != eit; ++it) {
        const PAGNode* node = pag->getPAGNode(*it);
        if(isa<ObjPN>(node) == false)
            continue;
        NodeID ptd = node->getId();
        outs() << "!!Target NodeID " << ptd << "\t [";
        const PAGNode* pagNode = pag->getPAGNode(ptd);
        if (isa<DummyValPN>(node))
            outs() << "DummyVal\n";
        else if (isa<DummyObjPN>(node))
            outs() << "Dummy Obj id: " << node->getId() << "]\n";
        else {
            outs() << "<" << pagNode->getValue()->getName() << "> ";
            outs() << "Source Loc: " << getSourceLoc(pagNode->getValue()) << "] \n";
        }
    }
}


/*!
 * Dump all points-to including top-level (ValPN) and address-taken (ObjPN) variables
 */
void BVDataPTAImpl::dumpAllPts() {
    for(PAG::iterator it = pag->begin(), eit = pag->end(); it!=eit; it++) {
        outs() << "----------------------------------------------\n";
        dumpPts(it->first, this->getPts(it->first));
        outs() << "----------------------------------------------\n";
    }
}


/*!
 * Print indirect call targets at an indirect callsite
 */
void PointerAnalysis::printIndCSTargets(const llvm::CallSite cs, const FunctionSet& targets)
{
    llvm::outs() << "\nNodeID: " << getFunPtr(cs);
    llvm::outs() << "\nCallSite: ";
    cs.getInstruction()->print(llvm::outs());
    llvm::outs() << "\tLocation: " << analysisUtil::getSourceLoc(cs.getInstruction());
    llvm::outs() << "\t with Targets: ";

    if (!targets.empty()) {
        FunctionSet::const_iterator fit = targets.begin();
        FunctionSet::const_iterator feit = targets.end();
        for (; fit != feit; ++fit) {
            const llvm::Function* callee = *fit;
            llvm::outs() << "\n\t" << callee->getName();
        }
    }
    else {
        llvm::outs() << "\n\tNo Targets!";
    }

    llvm::outs() << "\n";
}

/*!
 * Print all indirect callsites
 */
void PointerAnalysis::printIndCSTargets()
{
    llvm::outs() << "==================Function Pointer Targets==================\n";
    const CallEdgeMap& callEdges = getIndCallMap();
    CallEdgeMap::const_iterator it = callEdges.begin();
    CallEdgeMap::const_iterator eit = callEdges.end();
    for (; it != eit; ++it) {
        const llvm::CallSite cs = it->first;
        const FunctionSet& targets = it->second;
        printIndCSTargets(cs, targets);
    }

    const CallSiteToFunPtrMap& indCS = getIndirectCallsites();
    CallSiteToFunPtrMap::const_iterator csIt = indCS.begin();
    CallSiteToFunPtrMap::const_iterator csEit = indCS.end();
    for (; csIt != csEit; ++csIt) {
        const llvm::CallSite& cs = csIt->first;
        if (hasIndCSCallees(cs) == false) {
            llvm::outs() << "\nNodeID: " << csIt->second;
            llvm::outs() << "\nCallSite: ";
            cs.getInstruction()->print(llvm::outs());
            llvm::outs() << "\tLocation: " << analysisUtil::getSourceLoc(cs.getInstruction());
            llvm::outs() << "\n\t!!!has no targets!!!\n";
        }
    }
}

/*!
 * On the fly call graph construction
 * callsites is candidate indirect callsites need to be analyzed based on points-to results
 * newEdges is the new indirect call edges discovered
 */
void BVDataPTAImpl::onTheFlyCallGraphSolve(const CallSiteToFunPtrMap& callsites, CallEdgeMap& newEdges,CallGraph* callgraph) {
    for(CallSiteToFunPtrMap::const_iterator iter = callsites.begin(), eiter = callsites.end(); iter!=eiter; ++iter) {
        CallSite cs = iter->first;
        if (isVirtualCallSite(cs)) {
            const Value *vtbl = getVCallVtblPtr(cs);
            assert(pag->hasValueNode(vtbl));
            NodeID vtblId = pag->getValueNode(vtbl);
            resolveCPPIndCalls(cs, getPts(vtblId), newEdges,callgraph);
        } else
            resolveIndCalls(iter->first,getPts(iter->second),newEdges,callgraph);
    }
}

/*!
 * Resolve indirect calls
 */
void PointerAnalysis::resolveIndCalls(CallSite cs, const PointsTo& target, CallEdgeMap& newEdges,llvm::CallGraph* callgraph) {

    assert(pag->isIndirectCallSites(cs) && "not an indirect callsite?");
    /// discover indirect pointer target
    for (PointsTo::iterator ii = target.begin(), ie = target.end();
            ii != ie; ii++) {

        if(getNumOfResolvedIndCallEdge() > IndirectCallLimit) {
            errMsg("Resolved Indirect Call Edges are Out-Of-Budget, please increase the limit");
            return;
        }

        if(ObjPN* objPN = dyn_cast<ObjPN>(pag->getPAGNode(*ii))) {
            const MemObj* obj = pag->getObject(objPN);

            if(obj->isFunction()) {
                const Function* callee = cast<Function>(obj->getRefVal());
                callee = getDefFunForMultipleModule(callee);

                /// if the arg size does not match then we do not need to connect this parameter
                /// even if the callee is a variadic function (the first parameter of variadic function is its paramter number)
                if(matchArgs(cs, callee) == false)
                    continue;

                if(0 == getIndCallMap()[cs].count(callee)) {
                    newEdges[cs].insert(callee);
                    getIndCallMap()[cs].insert(callee);

                    ptaCallGraph->addIndirectCallGraphEdge(cs.getInstruction(), callee);
                    // FIXME: do we need to update llvm call graph here?
                    // The indirect call is maintained by ourself, We may update llvm's when we need to
                    //CallGraphNode* callgraphNode = callgraph->getOrInsertFunction(cs.getCaller());
                    //callgraphNode->addCalledFunction(cs,callgraph->getOrInsertFunction(callee));
                }
            }
        }
    }
}

/*
 * Get virtual functions "vfns" based on CHA
 */
void PointerAnalysis::getVFnsFromCHA(CallSite cs,
                                     std::set<const Function*> &vfns) {
    if (chgraph->csHasVFnsBasedonCHA(cs))
        vfns = chgraph->getCSVFsBasedonCHA(cs);
}

/*
 * Get virtual functions "vfns" from PoninsTo set "target" for callsite "cs"
 */
void PointerAnalysis::getVFnsFromPts(CallSite cs,
                                     const PointsTo &target,
                                     std::set<const Function*> &vfns) {

    if (chgraph->csHasVtblsBasedonCHA(cs)) {
        std::set<const Value*> vtbls;
        const std::set<const Value*> &chaVtbls =
            chgraph->getCSVtblsBasedonCHA(cs);
        for (PointsTo::iterator it = target.begin(), eit = target.end();
                it != eit; ++it) {
            const PAGNode *ptdnode = pag->getPAGNode(*it);
            if (ptdnode->hasValue()) {
                const Value *vtbl = ptdnode->getValue();
                if (chaVtbls.find(vtbl) != chaVtbls.end())
                    vtbls.insert(vtbl);
            }
        }
        chgraph->getVFnsFromVtbls(cs, vtbls, vfns);
    }
}

/*
 * Connect callsite "cs" to virtual functions in "vfns"
 */
void PointerAnalysis::connectVCallToVFns(CallSite cs,
        const std::set<const Function*> &vfns,
        CallEdgeMap& newEdges,
        llvm::CallGraph* callgraph) {
    //// connect all valid functions
    for (set<const Function*>::const_iterator fit = vfns.begin(),
            feit = vfns.end(); fit != feit; ++fit) {
        const Function* callee = *fit;
        if (callee->isDeclaration() && svfMod.hasDefinition(callee))
            callee = svfMod.getDefinition(callee);
        if (getIndCallMap()[cs].count(callee) > 0)
            continue;
        if(cs.arg_size() == callee->arg_size() ||
                (cs.getFunctionType()->isVarArg() && callee->isVarArg())) {
            newEdges[cs].insert(callee);
            getIndCallMap()[cs].insert(callee);
            ptaCallGraph->addIndirectCallGraphEdge(cs.getInstruction(), callee);
        }
    }
}

/// Resolve cpp indirect call edges
void PointerAnalysis::resolveCPPIndCalls(CallSite cs,
        const PointsTo& target,
        CallEdgeMap& newEdges,
        CallGraph* callgraph) {
    assert(pag->isIndirectCallSites(cs) && "not an indirect callsite?");
    assert(isVirtualCallSite(cs) && "not cpp virtual call");

    std::set<const Function*> vfns;
    if (connectVCallOnCHA)
        getVFnsFromCHA(cs, vfns);
    else
        getVFnsFromPts(cs, target, vfns);
    connectVCallToVFns(cs, vfns, newEdges, callgraph);
}

/*!
 * Find the alias check functions annotated in the C files
 * check whether the alias analysis results consistent with the alias check function itself
 */
void PointerAnalysis::validateSuccessTests(const char* fun) {

    // check for must alias cases, whether our alias analysis produce the correct results
    for (u32_t i = 0; i < svfMod.getModuleNum(); ++i) {
        Module *module = svfMod.getModule(i);
        if (Function* checkFun = module->getFunction(fun)) {
            if(!checkFun->use_empty())
                outs() << "[" << this->PTAName() << "] Checking " << fun << "\n";

            for (Value::user_iterator i = checkFun->user_begin(), e =
                        checkFun->user_end(); i != e; ++i)
                if (isa<CallInst>(*i) || isa<InvokeInst>(*i)) {

                    CallSite cs(*i);
                    assert(cs.getNumArgOperands() == 2
                           && "arguments should be two pointers!!");
                    Value* V1 = cs.getArgOperand(0);
                    Value* V2 = cs.getArgOperand(1);
                    AliasResult aliasRes = alias(V1, V2);

                    bool checkSuccessful = false;
                    if (strcmp(fun, "MAYALIAS") == 0 || strcmp(fun, "_Z8MAYALIASPvS_") == 0) {
                        if (aliasRes == MayAlias || aliasRes == MustAlias)
                            checkSuccessful = true;
                    } else if (strcmp(fun, "NOALIAS") == 0 || strcmp(fun, "_Z7NOALIASPvS_") == 0) {
                        if (aliasRes == NoAlias)
                            checkSuccessful = true;
                    } else if (strcmp(fun, "MUSTALIAS") == 0 || strcmp(fun, "_Z9MUSTALIASPvS_") == 0) {
                        // change to must alias when our analysis support it
                        if (aliasRes == MayAlias || aliasRes == MustAlias)
                            checkSuccessful = true;
                    } else if (strcmp(fun, "PARTIALALIAS") == 0 || strcmp(fun, "_Z12PARTIALALIASPvS_") == 0) {
                        // change to partial alias when our analysis support it
                        if (aliasRes == MayAlias)
                            checkSuccessful = true;
                    } else
                        assert(false && "not supported alias check!!");

                    NodeID id1 = pag->getValueNode(V1);
                    NodeID id2 = pag->getValueNode(V2);

                    if (checkSuccessful)
                        outs() << sucMsg("\t SUCCESS :") << fun << " check <id:" << id1 << ", id:" << id2 << "> at ("
                               << getSourceLoc(*i) << ")\n";
                    else
                        errs() << errMsg("\t FAIL :") << fun << " check <id:" << id1 << ", id:" << id2 << "> at ("
                               << getSourceLoc(*i) << ")\n";
                } else
                    assert(false && "alias check functions not only used at callsite??");

        }
    }
}

/*!
 * Pointer analysis validator
 */
void PointerAnalysis::validateExpectedFailureTests(const char* fun) {

    if (Function* checkFun = getModule().getFunction(fun)) {
        if(!checkFun->use_empty())
            outs() << "[" << this->PTAName() << "] Checking " << fun << "\n";

        for (Value::user_iterator i = checkFun->user_begin(), e =
                    checkFun->user_end(); i != e; ++i)
            if (CallInst *call = dyn_cast<CallInst>(*i)) {
                assert(call->getNumArgOperands() == 2
                       && "arguments should be two pointers!!");
                Value* V1 = call->getArgOperand(0);
                Value* V2 = call->getArgOperand(1);
                AliasResult aliasRes = alias(V1, V2);

                bool expectedFailure = false;
                if (strcmp(fun, "EXPECTEDFAIL_MAYALIAS") == 0) {
                    // change to must alias when our analysis support it
                    if (aliasRes == NoAlias)
                        expectedFailure = true;
                } else if (strcmp(fun, "EXPECTEDFAIL_NOALIAS") == 0) {
                    // change to partial alias when our analysis support it
                    if (aliasRes == MayAlias || aliasRes == PartialAlias || aliasRes == MustAlias)
                        expectedFailure = true;
                } else
                    assert(false && "not supported alias check!!");

                NodeID id1 = pag->getValueNode(V1);
                NodeID id2 = pag->getValueNode(V2);

                if (expectedFailure)
                    outs() << sucMsg("\t EXPECTED FAIL :") << fun << " check <id:" << id1 << ", id:" << id2 << "> at ("
                           << getSourceLoc(call) << ")\n";
                else
                    errs() << errMsg("\t UNEXPECTED FAIL :") << fun << " check <id:" << id1 << ", id:" << id2 << "> at ("
                           << getSourceLoc(call) << ")\n";
            }
            else
                assert(false && "alias check functions not only used at callsite??");
    }
}

/*!
 * Return alias results based on our points-to/alias analysis
 */
llvm::AliasResult BVDataPTAImpl::alias(const llvm::MemoryLocation &LocA,
                                       const llvm::MemoryLocation &LocB) {
    return alias(LocA.Ptr, LocB.Ptr);
}

/*!
 * Return alias results based on our points-to/alias analysis
 */
llvm::AliasResult BVDataPTAImpl::alias(const Value* V1,
                                       const Value* V2) {
    return alias(pag->getValueNode(V1),pag->getValueNode(V2));
}

/*!
 * Return alias results based on our points-to/alias analysis
 */
llvm::AliasResult BVDataPTAImpl::alias(NodeID node1, NodeID node2) {
    return alias(getPts(node1),getPts(node2));
}

/*!
 * Return alias results based on our points-to/alias analysis
 */
llvm::AliasResult BVDataPTAImpl::alias(const PointsTo& p1, const PointsTo& p2) {

    PointsTo pts1;
    expandFIObjs(p1,pts1);
    PointsTo pts2;
    expandFIObjs(p2,pts2);

    if (containBlackHoleNode(pts1) || containBlackHoleNode(pts2) || pts1.intersects(pts2))
        return MayAlias;
    else
        return NoAlias;
}
